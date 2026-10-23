package fr.jarvis.evolution.events

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

interface EventStore : AutoCloseable {
    fun append(event: SanitizedEvent, now: Long)
    fun query(module: Module? = null, since: Long = 0, limit: Int = 256): List<StoredIncident>
    fun prune(now: Long): Int
}
/** Dedicated technical database only. Unknown/corrupt schemas are refused, never repaired or reset. */
class BoundedEventStore(path: Path, private val limits: StoreLimits = StoreLimits()) : EventStore {
    private val db: Connection
    init {
        val absolute = path.toAbsolutePath().normalize()
        var cursor: Path? = absolute
        while (cursor != null) { require(!Files.isSymbolicLink(cursor)) { "SYMLINK_REFUSED" }; cursor = cursor.parent }
        require(Files.isDirectory(absolute.parent))
        require(!Files.exists(absolute) || Files.size(absolute) <= limits.maxDatabaseBytes) { "EXISTING_DATABASE_TOO_LARGE" }
        Class.forName("org.sqlite.JDBC")
        db = DriverManager.getConnection("jdbc:sqlite:$absolute")
        try {
            val version = db.createStatement().use { s -> s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) } }
            val tables = db.createStatement().use { s -> s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {
                buildList { while (it.next()) add(it.getString(1)) }
            } }
            require(version == 1 && tables == listOf("incidents") || version == 0 && tables.isEmpty()) { "INCOMPATIBLE_SCHEMA" }
            if(version==1) db.createStatement().use { s ->
                val names=s.executeQuery("PRAGMA table_info(incidents)").use { r -> buildList { while(r.next()) add(r.getString("name")) } }
                require(names==listOf("id","module","code","severity","first_at","last_at","occurrences","latency_ms","used_bytes","limit_bytes","queue_size","discarded")) { "INCOMPATIBLE_COLUMNS" }
                require(s.executeQuery("PRAGMA quick_check").use { it.next(); it.getString(1)=="ok" }) { "CORRUPT_DATABASE" }
            }
            db.createStatement().use { s ->
                s.execute("PRAGMA busy_timeout=1000")
                s.execute("PRAGMA journal_mode=DELETE")
                s.execute("PRAGMA synchronous=FULL")
                val pageSize = s.executeQuery("PRAGMA page_size").use { it.next(); it.getInt(1) }
                s.execute("PRAGMA max_page_count=${limits.maxDatabaseBytes / pageSize}")
                s.execute("PRAGMA journal_size_limit=${limits.maxDatabaseBytes}")
                s.execute("CREATE TABLE IF NOT EXISTS incidents (id TEXT PRIMARY KEY,module TEXT NOT NULL,code TEXT NOT NULL,severity TEXT NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,occurrences INTEGER NOT NULL,latency_ms REAL,used_bytes REAL,limit_bytes REAL,queue_size REAL,discarded INTEGER NOT NULL,UNIQUE(module,code,severity))")
                s.execute("PRAGMA user_version=1")
            }
            try { Files.setPosixFilePermissions(absolute, PosixFilePermissions.fromString("rw-------")) }
            catch (_: UnsupportedOperationException) { /* Windows host filesystem permissions apply. */ }
        } catch (error: Exception) { db.close(); throw error }
    }
    @Synchronized override fun append(event: SanitizedEvent, now: Long) {
        require(now >= 0)
        transaction {
            pruneRows(now)
            val sql = "INSERT INTO incidents VALUES(?,?,?,?,?,?,1,?,?,?,?,?) ON CONFLICT(module,code,severity) DO UPDATE SET last_at=MAX(last_at,excluded.last_at),occurrences=MIN(1000000000,occurrences+1),latency_ms=excluded.latency_ms,used_bytes=excluded.used_bytes,limit_bytes=excluded.limit_bytes,queue_size=excluded.queue_size,discarded=excluded.discarded"
            db.prepareStatement(sql).use { s ->
                s.setString(1, UUID.randomUUID().toString()); s.setString(2,event.module.name)
                s.setString(3,event.code.name); s.setString(4,event.severity.name); s.setLong(5,now); s.setLong(6,now)
                val p=event.payload
                listOf(p.latencyMs,p.usedBytes,p.limitBytes,p.queueSize).forEachIndexed { index, value ->
                    if(value==null) s.setNull(7+index,Types.REAL) else s.setDouble(7+index,value)
                }
                s.setInt(11,p.discardedFields); s.executeUpdate()
            }
            db.prepareStatement("DELETE FROM incidents WHERE id IN (SELECT id FROM incidents ORDER BY last_at DESC,id DESC LIMIT -1 OFFSET ?)").use {
                it.setInt(1,limits.maxRows); it.executeUpdate()
            }
        }
    }
    private fun pruneRows(now: Long): Int = db.prepareStatement("DELETE FROM incidents WHERE last_at < ?").use {
        it.setLong(1, (now-limits.retentionMs).coerceAtLeast(0)); it.executeUpdate()
    }
    @Synchronized override fun prune(now: Long): Int { require(now>=0); return transaction { pruneRows(now) } }
    @Synchronized override fun query(module: Module?, since: Long, limit: Int): List<StoredIncident> {
        require(since>=0 && limit in 1..256)
        return db.prepareStatement("SELECT * FROM incidents WHERE last_at>=?" + (if(module==null) "" else " AND module=?") + " ORDER BY last_at DESC,id LIMIT ?").use { s ->
            s.setLong(1,since); var i=2
            if(module!=null) s.setString(i++,module.name)
            s.setInt(i,limit)
            s.executeQuery().use { rows -> buildList { while(rows.next()) add(rows.incident()) } }
        }
    }
    private fun ResultSet.optional(name: String): Double? { val v=getDouble(name); return if(wasNull()) null else v }
    private fun ResultSet.incident() = StoredIncident(getString("id"),Module.valueOf(getString("module")),EventCode.valueOf(getString("code")),
        Severity.valueOf(getString("severity")),getLong("first_at"),getLong("last_at"),getLong("occurrences"),
        SanitizedPayload(optional("latency_ms"),optional("used_bytes"),optional("limit_bytes"),optional("queue_size"),getInt("discarded")))
    private fun <T> transaction(action: () -> T): T {
        db.autoCommit=false
        try { val result=action(); db.commit(); return result }
        catch(error:Exception) { db.rollback(); throw error }
        finally { db.autoCommit=true }
    }
    @Synchronized override fun close() { db.close() }
}
