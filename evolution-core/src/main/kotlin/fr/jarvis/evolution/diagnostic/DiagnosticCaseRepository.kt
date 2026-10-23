package fr.jarvis.evolution.diagnostic

import fr.jarvis.evolution.events.*
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID

data class DecisionEntry(val caseId:String,val previous:CaseStatus?,val next:CaseStatus,val at:Long)
/** Separate, bounded diagnostic database. Evidence snapshots/journal are append-only through this API. */
class DiagnosticCaseRepository(path: Path) : AutoCloseable {
    private val db: Connection
    init {
        val p=path.toAbsolutePath().normalize();var cursor:Path?=p
        while(cursor!=null) {require(!Files.isSymbolicLink(cursor));cursor=cursor.parent}
        require(Files.isDirectory(p.parent) && (!Files.exists(p)||Files.size(p)<=4*1024*1024))
        Class.forName("org.sqlite.JDBC");db=DriverManager.getConnection("jdbc:sqlite:$p")
        try {
            db.createStatement().use { s ->
                val version=s.executeQuery("PRAGMA user_version").use {it.next();it.getInt(1)}
                val tables=s.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'").use {r ->buildSet {while(r.next())add(r.getString(1))}}
                require(version==0 && tables.isEmpty() || version==1 && tables==setOf("cases","evidence","journal")) {"INCOMPATIBLE_DIAGNOSTIC_SCHEMA"}
                if(version==1) require(s.executeQuery("PRAGMA quick_check").use{it.next();it.getString(1)=="ok"})
                s.execute("PRAGMA busy_timeout=1000");s.execute("PRAGMA journal_mode=DELETE");s.execute("PRAGMA synchronous=FULL")
                s.execute("PRAGMA foreign_keys=ON")
                val page=s.executeQuery("PRAGMA page_size").use{it.next();it.getInt(1)}
                s.execute("PRAGMA max_page_count=${4*1024*1024/page}")
                s.execute("CREATE TABLE IF NOT EXISTS cases(id TEXT PRIMARY KEY,rule TEXT NOT NULL,module TEXT NOT NULL,impact TEXT NOT NULL,status TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,UNIQUE(rule,module))")
                s.execute("CREATE TABLE IF NOT EXISTS evidence(case_id TEXT NOT NULL REFERENCES cases(id),incident_id TEXT NOT NULL,module TEXT NOT NULL,code TEXT NOT NULL,severity TEXT NOT NULL,first_at INTEGER NOT NULL,last_at INTEGER NOT NULL,occurrences INTEGER NOT NULL,latency_ms REAL,used_bytes REAL,limit_bytes REAL,queue_size REAL,discarded INTEGER NOT NULL,digest TEXT NOT NULL,PRIMARY KEY(case_id,incident_id,digest))")
                s.execute("CREATE TABLE IF NOT EXISTS journal(seq INTEGER PRIMARY KEY AUTOINCREMENT,case_id TEXT NOT NULL REFERENCES cases(id),previous TEXT,next TEXT NOT NULL,at INTEGER NOT NULL)")
                s.execute("PRAGMA user_version=1")
            }
            try {Files.setPosixFilePermissions(p,java.nio.file.attribute.PosixFilePermissions.fromString("rw-------"))} catch(_:UnsupportedOperationException) {}
        } catch(e:Exception) {db.close();throw e}
    }
    @Synchronized fun record(evaluation:DiagnosticEvaluation,source:List<StoredIncident>,now:Long):List<DiagnosticCase> {
        require(now>=0 && source.size<=256 && evaluation.candidates.size<=128)
        val byId=source.associateBy{it.id};require(byId.size==source.size)
        for(c in evaluation.candidates) {
            require(c.evidence.size in 1..32)
            for(e in c.evidence) {
                require(e.incident.module==c.owner && e.sha256==EvidenceLink.from(requireNotNull(byId[e.incident.id])).sha256)
                val code=when(c.rule) {
                    RuleId.REPEATED_LATENCY -> EventCode.SLOW_OPERATION;RuleId.MEMORY_PRESSURE -> EventCode.MEMORY_PRESSURE
                    RuleId.FILE_UNAVAILABLE -> EventCode.FILE_UNAVAILABLE;RuleId.PERMISSION_REFUSED -> EventCode.PERMISSION_REFUSED
                    RuleId.RELEASE_UNKNOWN -> EventCode.RELEASE_UNKNOWN;RuleId.SERVICE_UNAVAILABLE -> EventCode.SERVICE_UNAVAILABLE
                }
                require(e.incident.code==code && e.sha256==EvidenceLink.from(e.incident).sha256)
            }
        }
        transaction {
            for(c in evaluation.candidates) {
                val existing=db.prepareStatement("SELECT id FROM cases WHERE rule=? AND module=?").use {s ->s.setString(1,c.rule.name);s.setString(2,c.owner.name);s.executeQuery().use {if(it.next())it.getString(1)else null}}
                val id=existing?:UUID.randomUUID().toString()
                if(existing==null) {
                    require(count("cases")<128)
                    db.prepareStatement("INSERT INTO cases VALUES(?,?,?,?,?,?,?)").use {s ->
                        listOf(id,c.rule.name,c.owner.name,c.impact.name,CaseStatus.OPEN.name).forEachIndexed{i,v ->s.setString(i+1,v)}
                        s.setLong(6,now);s.setLong(7,now);s.executeUpdate()
                    }
                    appendDecision(id,null,CaseStatus.OPEN,now)
                }
                for(e in c.evidence) {
                    val already=db.prepareStatement("SELECT 1 FROM evidence WHERE case_id=? AND incident_id=? AND digest=?").use{s ->s.setString(1,id);s.setString(2,e.incident.id);s.setString(3,e.sha256);s.executeQuery().use{it.next()}}
                    if(already) continue
                    require(count("evidence")<4096)
                    val perCase=db.prepareStatement("SELECT count(*) FROM evidence WHERE case_id=?").use{s ->s.setString(1,id);s.executeQuery().use{it.next();it.getInt(1)}}
                    require(perCase<32){"EVIDENCE_CAPACITY"}
                    db.prepareStatement("INSERT INTO evidence VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)").use {s ->
                        val r=e.incident
                        listOf(id,r.id,r.module.name,r.code.name,r.severity.name).forEachIndexed{i,v ->s.setString(i+1,v)}
                        s.setLong(6,r.firstAt);s.setLong(7,r.lastAt);s.setLong(8,r.occurrences)
                        listOf(r.payload.latencyMs,r.payload.usedBytes,r.payload.limitBytes,r.payload.queueSize).forEachIndexed{i,v ->if(v==null)s.setNull(9+i,Types.REAL)else s.setDouble(9+i,v)}
                        s.setInt(13,r.payload.discardedFields);s.setString(14,e.sha256);s.executeUpdate()
                    }
                    db.prepareStatement("UPDATE cases SET updated_at=?,impact=? WHERE id=?").use{s ->s.setLong(1,now);s.setString(2,c.impact.name);s.setString(3,id);s.executeUpdate()}
                }
            }
        }
        return list()
    }
    private fun count(table:String)=db.createStatement().use{s ->s.executeQuery("SELECT count(*) FROM $table").use{it.next();it.getInt(1)}}
    private fun appendDecision(id:String,previous:CaseStatus?,next:CaseStatus,now:Long) {
        require(count("journal")<4096){"JOURNAL_CAPACITY"}
        db.prepareStatement("INSERT INTO journal(case_id,previous,next,at) VALUES(?,?,?,?)").use{s ->s.setString(1,id);s.setString(2,previous?.name);s.setString(3,next.name);s.setLong(4,now);s.executeUpdate()}
    }
    /** Human/host action, never called by RuleEngine. No automatic resolution or reopening. */
    @Synchronized fun transition(id:String,next:CaseStatus,now:Long) {
        UUID.fromString(id);require(now>=0)
        transaction {
            val previous=db.prepareStatement("SELECT status,updated_at FROM cases WHERE id=?").use{s ->s.setString(1,id);s.executeQuery().use{require(it.next());require(now>=it.getLong(2));CaseStatus.valueOf(it.getString(1))}}
            require(previous!=next)
            require(previous in setOf(CaseStatus.OPEN,CaseStatus.ACKNOWLEDGED) || next==CaseStatus.OPEN)
            appendDecision(id,previous,next,now)
            db.prepareStatement("UPDATE cases SET status=?,updated_at=? WHERE id=?").use{s ->s.setString(1,next.name);s.setLong(2,now);s.setString(3,id);s.executeUpdate()}
        }
    }
    @Synchronized fun list(module:Module?=null):List<DiagnosticCase> = db.prepareStatement("SELECT * FROM cases"+(if(module==null)""else" WHERE module=?")+" ORDER BY created_at,id LIMIT 128").use{s ->
        if(module!=null)s.setString(1,module.name)
        s.executeQuery().use{r -> buildList{while(r.next()) {
            val id=r.getString("id");UUID.fromString(id)
            add(DiagnosticCase(id,RuleId.valueOf(r.getString("rule")),Module.valueOf(r.getString("module")),Severity.valueOf(r.getString("impact")),
                CaseStatus.valueOf(r.getString("status")),r.getLong("created_at"),r.getLong("updated_at"),evidence(id)))
        }}}
    }
    private fun evidence(id:String):List<EvidenceLink> = db.prepareStatement("SELECT * FROM evidence WHERE case_id=? ORDER BY incident_id,digest LIMIT 32").use{s ->s.setString(1,id);s.executeQuery().use{r ->buildList{while(r.next()) {
        val row=StoredIncident(r.getString("incident_id"),Module.valueOf(r.getString("module")),EventCode.valueOf(r.getString("code")),Severity.valueOf(r.getString("severity")),
            r.getLong("first_at"),r.getLong("last_at"),r.getLong("occurrences"),SanitizedPayload(r.optional("latency_ms"),r.optional("used_bytes"),r.optional("limit_bytes"),r.optional("queue_size"),r.getInt("discarded")))
        val link=EvidenceLink.from(row);require(link.sha256==r.getString("digest")){"CORRUPT_EVIDENCE"};add(link)
    }}}}
    @Synchronized fun journal(id:String):List<DecisionEntry> {
        UUID.fromString(id)
        return db.prepareStatement("SELECT previous,next,at FROM journal WHERE case_id=? ORDER BY seq").use{s ->s.setString(1,id);s.executeQuery().use{r ->buildList{while(r.next())add(DecisionEntry(id,r.getString(1)?.let(CaseStatus::valueOf),CaseStatus.valueOf(r.getString(2)),r.getLong(3)))}}}
    }
    private fun ResultSet.optional(name:String):Double? {val v=getDouble(name);return if(wasNull())null else v}
    private fun <T> transaction(action:()->T):T {db.autoCommit=false;try{val r=action();db.commit();return r}catch(e:Exception){db.rollback();throw e}finally{db.autoCommit=true}}
    @Synchronized override fun close(){db.close()}
}
