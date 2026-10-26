package fr.jarvis.evolution.performance

import fr.jarvis.evolution.events.Module
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.sql.DriverManager
import java.util.UUID

data class Experiment(val id: String, val createdAt: Long, val before: PerformanceRun, val after: PerformanceRun) {
    val comparison: PerformanceComparison get() = PerformanceComparator.compare(before,after)
}

/** Dedicated immutable history, bounded to 128 pairs / 4 MiB. Capacity or corruption refuses writes;
 * no reset, retention deletion, execution command, or production integration is provided. */
class ExperimentRepository(path: Path): AutoCloseable {
    private val db: java.sql.Connection
    init {
        val absolute=path.toAbsolutePath().normalize(); var cursor: Path?=absolute
        while(cursor!=null) { require(!Files.isSymbolicLink(cursor)); cursor=cursor.parent }
        require(Files.isDirectory(absolute.parent) && (!Files.exists(absolute) || Files.isRegularFile(absolute) && Files.size(absolute)<=4*1024*1024))
        Class.forName("org.sqlite.JDBC"); db=DriverManager.getConnection("jdbc:sqlite:$absolute")
        try {
            db.createStatement().use { s ->
                val version=s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
                val tables=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use { r -> buildSet { while(r.next()) add(r.getString(1)) } }
                require(version==0 && tables.isEmpty() || version==1 && tables==setOf("experiments")) { "INCOMPATIBLE_EXPERIMENT_SCHEMA" }
                if(version==1) {
                    require(s.executeQuery("PRAGMA table_info(experiments)").use { r -> buildList { while(r.next()) add(r.getString("name")) } } ==
                        listOf("id","created_at","module","workload","environment","configuration","origin","warmups","before_revision","after_revision","before_samples","after_samples","before_digest","after_digest","comparator_version"))
                    require(s.executeQuery("PRAGMA quick_check").use { it.next(); it.getString(1)=="ok" })
                }
                s.execute("PRAGMA busy_timeout=1000"); s.execute("PRAGMA journal_mode=DELETE"); s.execute("PRAGMA synchronous=FULL")
                val page=s.executeQuery("PRAGMA page_size").use { it.next(); it.getInt(1) }
                s.execute("PRAGMA max_page_count=${4*1024*1024/page}")
                s.execute("CREATE TABLE IF NOT EXISTS experiments(id TEXT PRIMARY KEY,created_at INTEGER NOT NULL,module TEXT NOT NULL,workload TEXT NOT NULL,environment TEXT NOT NULL,configuration TEXT NOT NULL,origin TEXT NOT NULL,warmups INTEGER NOT NULL,before_revision TEXT NOT NULL,after_revision TEXT NOT NULL,before_samples TEXT NOT NULL,after_samples TEXT NOT NULL,before_digest TEXT NOT NULL,after_digest TEXT NOT NULL,comparator_version INTEGER NOT NULL)")
                s.execute("PRAGMA user_version=1")
            }
            try { Files.setPosixFilePermissions(absolute,PosixFilePermissions.fromString("rw-------")) } catch(_: UnsupportedOperationException) {}
            list() // Reject invalid snapshots at opening, before accepting additional data.
        } catch(e: Exception) { db.close(); throw e }
    }
    @Synchronized fun record(id: String, now: Long, before: PerformanceRun, after: PerformanceRun): Experiment {
        require(UUID.fromString(id).toString()==id && now>=0)
        require(before.context.compatible(after.context) && before.samples.size==after.samples.size) { "INCOMPARABLE_RUNS" }
        db.autoCommit=false
        try {
            val existing=list().find { it.id==id }
            if(existing!=null) {
                require(existing.createdAt==now && existing.before.sha256==before.sha256 && existing.after.sha256==after.sha256) { "EXPERIMENT_ID_CONFLICT" }
                db.commit(); return existing
            }
            require(list().size<128) { "EXPERIMENT_CAPACITY" }
            db.prepareStatement("INSERT INTO experiments VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)").use { s ->
                val c=before.context
                s.setString(1,id); s.setLong(2,now)
                listOf(c.module.name,c.workloadSha256,c.environmentSha256,c.configurationSha256,c.origin.name).forEachIndexed { i,v -> s.setString(i+3,v) }
                s.setInt(8,c.warmups); s.setString(9,c.revision); s.setString(10,after.context.revision)
                s.setString(11,before.samples.joinToString(",") { java.lang.Double.toHexString(it) })
                s.setString(12,after.samples.joinToString(",") { java.lang.Double.toHexString(it) })
                s.setString(13,before.sha256); s.setString(14,after.sha256); s.setInt(15,PerformanceComparator.VERSION); s.executeUpdate()
            }
            db.commit(); return Experiment(id,now,before,after)
        } catch(e: Exception) { db.rollback(); throw e } finally { db.autoCommit=true }
    }
    @Synchronized fun list(): List<Experiment> = db.createStatement().use { s -> s.executeQuery("SELECT * FROM experiments ORDER BY created_at,id LIMIT 129").use { r ->
        buildList {
            while(r.next()) {
                require(size<128 && r.getInt("comparator_version")==PerformanceComparator.VERSION)
                val id=r.getString("id"); require(UUID.fromString(id).toString()==id && r.getLong("created_at")>=0)
                val c=MeasurementContext(Module.valueOf(r.getString("module")),r.getString("workload"),r.getString("environment"),
                    r.getString("configuration"),r.getString("before_revision"),r.getInt("warmups"),MeasurementOrigin.valueOf(r.getString("origin")))
                fun run(column: String, context: MeasurementContext): PerformanceRun {
                    val text=r.getString(column); require(text.length<=8192)
                    return PerformanceRun(context,text.split(',').map { java.lang.Double.valueOf(it).toDouble() })
                }
                val before=run("before_samples",c); val after=run("after_samples",c.copy(revision=r.getString("after_revision")))
                require(before.sha256==r.getString("before_digest") && after.sha256==r.getString("after_digest") && before.samples.size==after.samples.size) { "CORRUPT_EXPERIMENT" }
                add(Experiment(id,r.getLong("created_at"),before,after))
            }
        }
    } }
    @Synchronized override fun close() { db.close() }
}
