package fr.jarvis.evolution.events

/** These identifiers describe components, never package names, people or caller-supplied strings. */
enum class Module { CORE, LLM, VOICE, INTERFACE, SERVICES, NEXUS, EVOLUTION, UNIFIED }
enum class Category { LATENCY, MEMORY, CRASH, QUEUE, AVAILABILITY, IO, PERMISSION, RELEASE }
enum class Severity { INFO, WARNING, ERROR }
enum class EventCode(val category: Category) {
    SLOW_OPERATION(Category.LATENCY), MEMORY_PRESSURE(Category.MEMORY), CRASH_OBSERVED(Category.CRASH),
    QUEUE_FULL(Category.QUEUE), SERVICE_UNAVAILABLE(Category.AVAILABILITY), FILE_UNAVAILABLE(Category.IO),
    PERMISSION_REFUSED(Category.PERMISSION), RELEASE_UNKNOWN(Category.RELEASE),
    PROCESS_INTERRUPTED(Category.CRASH), NO_DATA(Category.AVAILABILITY)
}
/** Input can contain private text. It is discarded BEFORE queueing, hashing or database operations. */
data class EvolutionEvent(val module: Module, val code: EventCode, val severity: Severity,
                          val payload: Map<String, Any?> = emptyMap())
data class SanitizedPayload internal constructor(val latencyMs: Double?, val usedBytes: Double?,
    val limitBytes: Double?, val queueSize: Double?, val discardedFields: Int)
data class SanitizedEvent internal constructor(val module: Module, val code: EventCode,
    val severity: Severity, val payload: SanitizedPayload)
object Sanitizer {
    private val caps = mapOf("latencyMs" to 86_400_000.0, "usedBytes" to 10_000_000_000_000.0,
        "limitBytes" to 10_000_000_000_000.0, "queueSize" to 1_000_000.0)
    fun sanitize(event: EvolutionEvent): SanitizedEvent {
        // Bound the inspected entries too: never call toString() on arbitrary payload objects.
        val safe = mutableMapOf<String, Double>(); var discarded = 0
        for ((key, value) in event.payload.entries.take(32)) {
            val cap = caps[key]
            val number = when (value) { is Int -> value.toDouble(); is Long -> value.toDouble()
                is Double -> value; is Float -> value.toDouble(); else -> null }
            if (cap != null && number != null && number.isFinite() && number in 0.0..cap) safe[key] = number
            else discarded++
        }
        return SanitizedEvent(event.module, event.code, event.severity,
            SanitizedPayload(safe["latencyMs"], safe["usedBytes"], safe["limitBytes"], safe["queueSize"], discarded))
    }
}
data class StoredIncident(val id: String, val module: Module, val code: EventCode, val severity: Severity,
    val firstAt: Long, val lastAt: Long, val occurrences: Long, val payload: SanitizedPayload)
data class StoreLimits(val maxRows: Int = 2048, val retentionMs: Long = 7 * 86_400_000L,
    val maxDatabaseBytes: Int = 4 * 1024 * 1024) {
    init { require(maxRows in 1..100_000 && retentionMs in 1..365 * 86_400_000L)
        require(maxDatabaseBytes in 262_144..16 * 1024 * 1024) }
}
