package fr.jarvis.evolution.probes

import fr.jarvis.evolution.events.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicLong

fun interface EventSink { fun submit(event: EvolutionEvent): SubmitResult }
enum class SubmitResult { DISABLED, ACCEPTED, SATURATED }
enum class CollectorStatus { DISABLED, READY, STORAGE_UNKNOWN, PROBE_UNAVAILABLE }
data class DiagnosticsSnapshot(val enabled: Boolean, val queued: Int, val dropped: Long,
    val persisted: Long, val uncertain: Long, val status: CollectorStatus)
/** No thread, timer or observer is started by construction. Hosts explicitly opt in and drain on IO. */
class LocalTelemetryService(private val store: EventStore, capacity: Int = 64,
    private val clock: () -> Long = System::currentTimeMillis) : EventSink {
    private val queue = ArrayBlockingQueue<SanitizedEvent>(capacity.also { require(it in 1..256) })
    private val dropped=AtomicLong(); private val persisted=AtomicLong(); private val uncertain=AtomicLong()
    @Volatile private var enabled=false
    @Volatile private var status=CollectorStatus.DISABLED
    private val mutable=MutableStateFlow(snapshot())
    val state: StateFlow<DiagnosticsSnapshot> = mutable.asStateFlow()
    fun snapshot() = DiagnosticsSnapshot(enabled,queue.size,dropped.get(),persisted.get(),uncertain.get(),status)
    private fun publish() { mutable.value=snapshot() }
    @Synchronized fun setEnabled(value: Boolean) {
        enabled=value
        if(!value) { dropped.addAndGet(queue.size.toLong()); queue.clear() }
        status=if(!value) CollectorStatus.DISABLED else if(uncertain.get()>0) CollectorStatus.STORAGE_UNKNOWN else CollectorStatus.READY; publish()
    }
    @Synchronized override fun submit(event: EvolutionEvent): SubmitResult {
        if(!enabled) return SubmitResult.DISABLED
        // Only the sanitized, closed-schema value may ever enter the queue.
        val accepted=queue.offer(Sanitizer.sanitize(event))
        if(!accepted) dropped.incrementAndGet()
        publish(); return if(accepted) SubmitResult.ACCEPTED else SubmitResult.SATURATED
    }
    /** A failed write is UNKNOWN, not automatically retried: commit acknowledgement can be ambiguous. */
    @Synchronized fun drain(maxBatch: Int = 64): Int {
        require(maxBatch in 1..256)
        var written=0
        repeat(maxBatch) {
            if(!enabled || uncertain.get()>0) return written
            val event=queue.poll() ?: return written
            try { store.append(event,clock()); persisted.incrementAndGet(); written++ }
            catch (_: Exception) { uncertain.incrementAndGet(); status=CollectorStatus.STORAGE_UNKNOWN; publish(); return written }
            publish()
        }
        return written
    }
    fun probeUnavailable() { status=CollectorStatus.PROBE_UNAVAILABLE; submit(EvolutionEvent(Module.EVOLUTION,EventCode.SERVICE_UNAVAILABLE,Severity.WARNING)); publish() }
}
fun interface HealthProbe { fun sample(): EvolutionEvent? }
/** Host-driven polling, opt-in false. Never reads another process, sensor or application database. */
class ProbeScheduler(private val service: LocalTelemetryService, private val probes: List<HealthProbe>,
    private val intervalMs: Long = 60_000) {
    init { require(probes.size<=16 && intervalMs in 1000..86_400_000) }
    var enabled=false
    private var nextAt=0L
    fun poll(now: Long): Int {
        require(now>=0)
        if(!enabled || now<nextAt || !service.snapshot().enabled) return 0
        nextAt=if(now>Long.MAX_VALUE-intervalMs) Long.MAX_VALUE else now+intervalMs
        var accepted=0
        for(probe in probes) try {
            probe.sample()?.let { if(service.submit(it)==SubmitResult.ACCEPTED) accepted++ }
        } catch (_: Exception) { service.probeUnavailable() }
        return accepted
    }
}
class CurrentProcessMemoryProbe(private val usage: () -> Pair<Long,Long> = {
    Runtime.getRuntime().let { (it.totalMemory()-it.freeMemory()) to it.maxMemory() }
}) : HealthProbe {
    override fun sample(): EvolutionEvent {
        val (used,limit)=usage()
        return EvolutionEvent(Module.EVOLUTION,EventCode.MEMORY_PRESSURE,Severity.INFO,
            mapOf("usedBytes" to used,"limitBytes" to limit))
    }
}
class QueueHealthProbe(private val size: () -> Int) : HealthProbe {
    override fun sample() = EvolutionEvent(Module.EVOLUTION,EventCode.QUEUE_FULL,Severity.INFO,mapOf("queueSize" to size()))
}
object CrashObservation {
    /** Throwable text, paths, stack, class names and exception causes are intentionally ignored. */
    fun event(module: Module, @Suppress("UNUSED_PARAMETER") error: Throwable) =
        EvolutionEvent(module,EventCode.CRASH_OBSERVED,Severity.ERROR)
}
class TimedHealthProbe(private val latencyMs: () -> Double) : HealthProbe {
    override fun sample() = EvolutionEvent(Module.EVOLUTION,EventCode.SLOW_OPERATION,Severity.INFO,mapOf("latencyMs" to latencyMs()))
}
