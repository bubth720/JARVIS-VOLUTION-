package fr.jarvis.evolution.diagnostic

import fr.jarvis.evolution.events.EventStore

data class DiagnosticSnapshot(val enabled: Boolean, val state: String, val evaluation: DiagnosticEvaluation?,
    val cases: List<DiagnosticCase>)

/** Host-driven bounded polling. No worker, command execution or automatic case resolution.
 * Disable clears the volatile snapshot; persisted cases and human decisions remain. */
class DiagnosticMonitor(private val source: EventStore, private val repository: DiagnosticCaseRepository,
    private val engine: RuleEngine=RuleEngine(), private val intervalMs: Long=60_000) {
    init { require(intervalMs in 1000..86_400_000) }
    private var enabled=false
    private var lastAt: Long?=null
    private var snapshot=DiagnosticSnapshot(false,"DISABLED",null,emptyList())
    @Synchronized fun setEnabled(value: Boolean) {
        enabled=value; lastAt=null
        snapshot=DiagnosticSnapshot(value,if(value) "WAITING_FOR_HOST_POLL" else "DISABLED",null,emptyList())
    }
    @Synchronized fun snapshot(): DiagnosticSnapshot = snapshot
    @Synchronized fun poll(now: Long): DiagnosticSnapshot {
        require(now>=0)
        if(!enabled) return snapshot
        val previous=lastAt
        if(previous!=null && (now<previous || now-previous<intervalMs)) return snapshot
        lastAt=now
        snapshot=try {
            val rows=source.query()
            val evaluation=engine.evaluate(rows,now)
            val cases=repository.record(evaluation,rows,now)
            // The bounded event query can truncate a source window. Never label that complete.
            val state=if(rows.size==256 || evaluation.insufficientData) "INSUFFICIENT_DATA" else "OBSERVED"
            DiagnosticSnapshot(true,state,evaluation,cases)
        } catch(_: Exception) {
            DiagnosticSnapshot(true,"UNKNOWN",null,emptyList())
        }
        return snapshot
    }
}
