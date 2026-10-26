package fr.jarvis.evolution.performance

import fr.jarvis.evolution.diagnostic.EvidenceLink
import fr.jarvis.evolution.events.Module
import kotlin.math.ceil

enum class MeasurementOrigin { HOST_OBSERVATION, SYNTHETIC }
enum class ComparisonStatus { REGRESSION, IMPROVEMENT, STABLE, INCOMPARABLE }

/** Fingerprints identify a fixed workload, machine/runtime and configuration without storing private text.
 * Revision may differ between runs. Warmups are excluded by the host before submission.
 * HOST_OBSERVATION is host-declared provenance, never a claim that EVOLUTION executed a patch. */
data class MeasurementContext(val module: Module, val workloadSha256: String, val environmentSha256: String,
    val configurationSha256: String, val revision: String, val warmups: Int, val origin: MeasurementOrigin) {
    init {
        require(listOf(workloadSha256, environmentSha256, configurationSha256).all { it.matches(Regex("[0-9a-f]{64}")) })
        require(revision.matches(Regex("[0-9a-f]{40}")) && warmups in 0..1000)
    }
    fun compatible(other: MeasurementContext) = copy(revision=other.revision)==other
}

class PerformanceRun(val context: MeasurementContext, samples: List<Double>) {
    val samples: List<Double> = java.util.List.copyOf(samples)
    init { require(this.samples.size in 5..256 && this.samples.all { it.isFinite() && it in 0.0..86_400_000.0 }) }
    private val sorted = this.samples.sorted()
    val medianMs: Double get() = if(sorted.size%2==0) (sorted[sorted.size/2-1]+sorted[sorted.size/2])/2 else sorted[sorted.size/2]
    val p95Ms: Double get() = sorted[ceil(sorted.size*0.95).toInt()-1]
    fun canonical(): String = with(context) {
        listOf("jarvis.performance/1", module.name, workloadSha256, environmentSha256, configurationSha256,
            revision, warmups.toString(), origin.name, samples.joinToString(",") { java.lang.Double.toHexString(it) }).joinToString("\n")
    }
    val sha256: String get() = EvidenceLink.digest(canonical())
}

data class PerformanceComparison(val status: ComparisonStatus, val baselineSha256: String, val candidateSha256: String,
    val medianDeltaMs: Double?, val p95DeltaMs: Double?, val reason: String)

/** Deterministic descriptive comparison, not statistical significance or causal proof.
 * A regression in either statistic takes precedence over an improvement in the other. */
object PerformanceComparator {
    const val VERSION = 1
    fun compare(before: PerformanceRun, after: PerformanceRun): PerformanceComparison {
        if(!before.context.compatible(after.context) || before.samples.size!=after.samples.size)
            return PerformanceComparison(ComparisonStatus.INCOMPARABLE,before.sha256,after.sha256,null,null,"CONTEXT_OR_SAMPLE_COUNT_MISMATCH")
        val median=after.medianMs-before.medianMs; val p95=after.p95Ms-before.p95Ms
        fun significant(delta: Double, baseline: Double) = kotlin.math.abs(delta)>=5.0 && kotlin.math.abs(delta)>=baseline*0.10
        val regressed = median>0 && significant(median,before.medianMs) || p95>0 && significant(p95,before.p95Ms)
        val improved = median<0 && significant(median,before.medianMs) || p95<0 && significant(p95,before.p95Ms)
        val status=when { regressed -> ComparisonStatus.REGRESSION; improved -> ComparisonStatus.IMPROVEMENT; else -> ComparisonStatus.STABLE }
        return PerformanceComparison(status,before.sha256,after.sha256,median,p95,"MEDIAN_P95_10_PERCENT_AND_5_MS_V1")
    }
}
