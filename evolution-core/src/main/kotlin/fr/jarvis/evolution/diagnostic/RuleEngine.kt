package fr.jarvis.evolution.diagnostic

import fr.jarvis.evolution.events.*
import java.security.MessageDigest

enum class RuleId { REPEATED_LATENCY, MEMORY_PRESSURE, FILE_UNAVAILABLE, PERMISSION_REFUSED, RELEASE_UNKNOWN, SERVICE_UNAVAILABLE }
enum class CaseStatus { OPEN, ACKNOWLEDGED, RESOLVED, REJECTED }
enum class EvidenceIssue { MISSING_METRICS, INCOMPLETE_WINDOW, FUTURE_EVENT }
data class EvidenceLink(val incident: StoredIncident, val sha256: String) {
    companion object {
        fun from(incident: StoredIncident) = EvidenceLink(incident, digest(SnapshotJson.incidents(listOf(incident))))
        fun digest(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
data class DiagnosticCandidate(val rule: RuleId, val owner: Module, val impact: Severity, val evidence: List<EvidenceLink>)
data class UnknownEvidence(val incidentId: String, val rule: RuleId, val reason: EvidenceIssue)
data class DiagnosticEvaluation(val candidates: List<DiagnosticCandidate>, val unknown: List<UnknownEvidence>, val observed: Int) {
    val insufficientData get() = observed == 0 || unknown.isNotEmpty()
}
data class DiagnosticCase(val id: String, val rule: RuleId, val owner: Module, val impact: Severity,
    val status: CaseStatus, val createdAt: Long, val updatedAt: Long, val evidence: List<EvidenceLink>)
/** Immutable rules. Thresholds are diagnostic-only, never native/hardware admission limits. */
class RuleRepository(disabled: Set<RuleId> = emptySet(), val windowMs: Long = 300_000) {
    val version=1
    private val disabled=disabled.toSet()
    init { require(windowMs in 1000..86_400_000) }
    fun enabled(rule: RuleId) = rule !in disabled
}
class RuleEngine(private val rules: RuleRepository = RuleRepository()) {
    fun evaluate(rows: List<StoredIncident>, now: Long): DiagnosticEvaluation {
        require(rows.size<=256 && now>=0)
        val matches=mutableListOf<DiagnosticCandidate>(); val unknown=mutableListOf<UnknownEvidence>()
        val cutoff=(now-rules.windowMs).coerceAtLeast(0)
        for(row in rows) {
            val rule=when(row.code) {
                EventCode.SLOW_OPERATION -> RuleId.REPEATED_LATENCY
                EventCode.MEMORY_PRESSURE -> RuleId.MEMORY_PRESSURE
                EventCode.FILE_UNAVAILABLE -> RuleId.FILE_UNAVAILABLE
                EventCode.PERMISSION_REFUSED -> RuleId.PERMISSION_REFUSED
                EventCode.RELEASE_UNKNOWN -> RuleId.RELEASE_UNKNOWN
                EventCode.SERVICE_UNAVAILABLE -> RuleId.SERVICE_UNAVAILABLE
                else -> continue
            }
            if(!rules.enabled(rule)) continue
            if(row.lastAt>now) { unknown+=UnknownEvidence(row.id,rule,EvidenceIssue.FUTURE_EVENT);continue }
            // Missing required measurements are a data-quality UNKNOWN even when the
            // sample is old; they must not disappear as a healthy negative observation.
            val missing = rule==RuleId.REPEATED_LATENCY && row.payload.latencyMs==null ||
                rule==RuleId.MEMORY_PRESSURE && (row.payload.usedBytes==null || row.payload.limitBytes==null || row.payload.limitBytes<=0)
            if(missing) { unknown+=UnknownEvidence(row.id,rule,EvidenceIssue.MISSING_METRICS);continue }
            if(row.lastAt<cutoff) continue
            val match=when(rule) {
                RuleId.REPEATED_LATENCY -> when {
                    row.payload.latencyMs==null -> { unknown+=UnknownEvidence(row.id,rule,EvidenceIssue.MISSING_METRICS);false }
                    row.firstAt<cutoff -> { unknown+=UnknownEvidence(row.id,rule,EvidenceIssue.INCOMPLETE_WINDOW);false }
                    else -> row.occurrences>=3 && row.payload.latencyMs>=2000
                }
                RuleId.MEMORY_PRESSURE -> {
                    val used=row.payload.usedBytes;val limit=row.payload.limitBytes
                    if(used==null || limit==null || limit<=0) { unknown+=UnknownEvidence(row.id,rule,EvidenceIssue.MISSING_METRICS);false }
                    else used/limit>=0.85
                }
                else -> row.severity!=Severity.INFO
            }
            if(match) matches+=DiagnosticCandidate(rule,row.module,row.severity,listOf(EvidenceLink.from(row)))
        }
        return DiagnosticEvaluation(IncidentCorrelator.group(matches),unknown.toList(),rows.size)
    }
}
object IncidentCorrelator {
    fun group(candidates: List<DiagnosticCandidate>): List<DiagnosticCandidate> = candidates.groupBy { it.rule to it.owner }.values.map { group ->
        group.first().copy(impact=group.maxBy { it.impact.ordinal }.impact,
            evidence=group.flatMap { it.evidence }.distinctBy { it.incident.id to it.sha256 }.take(32))
    }
}
object RecommendationCatalog {
    fun recommendation(rule: RuleId) = when(rule) {
        RuleId.REPEATED_LATENCY -> "Lenteur répétée possible : vérifier la charge et les délais avec le propriétaire, sans modifier les budgets."
        RuleId.MEMORY_PRESSURE -> "Pression mémoire possible : vérifier les mesures et l'admission existante ; ne pas charger un second moteur."
        RuleId.FILE_UNAVAILABLE -> "Vérifier la disponibilité du fichier auprès du propriétaire ; ne pas modifier les permissions automatiquement."
        RuleId.PERMISSION_REFUSED -> "Examiner l'autorisation avec CORE et l'utilisateur ; ne pas contourner le refus."
        RuleId.RELEASE_UNKNOWN -> "Conserver le blocage et demander une preuve de fin/libération ; ne pas réinitialiser les journaux."
        RuleId.SERVICE_UNAVAILABLE -> "Examiner l'état du module propriétaire et sa reprise contrôlée ; aucune réparation appliquée."
    }
}
