package fr.jarvis.evolution

import fr.jarvis.evolution.events.Module
import fr.jarvis.evolution.performance.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID

class PerformanceTest {
    private val context=MeasurementContext(Module.LLM,"a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(40),2,MeasurementOrigin.SYNTHETIC)
    private fun run(value: Double)=PerformanceRun(context,List(20) { value })
    private fun refused(block: () -> Unit) { try { block(); fail("Expected refusal") } catch(_: IllegalArgumentException) {} }
    @Test fun deterministicStatisticsAndThresholds() {
        val before=PerformanceRun(context,(1..20).map { it.toDouble()*10 })
        assertEquals(105.0,before.medianMs,0.0); assertEquals(190.0,before.p95Ms,0.0)
        assertEquals(ComparisonStatus.REGRESSION,PerformanceComparator.compare(run(100.0),run(120.0)).status)
        assertEquals(ComparisonStatus.IMPROVEMENT,PerformanceComparator.compare(run(100.0),run(80.0)).status)
        assertEquals(ComparisonStatus.STABLE,PerformanceComparator.compare(run(10.0),run(12.0)).status)
        assertEquals(before.sha256,PerformanceRun(context,before.samples).sha256)
    }
    @Test fun mixedTailRegressionWins() {
        val after=PerformanceRun(context,List(18) { 80.0 }+listOf(200.0,200.0))
        assertEquals(ComparisonStatus.REGRESSION,PerformanceComparator.compare(run(100.0),after).status)
    }
    @Test fun refusesInvalidAndSeparatesContextsAndSynthetic() {
        for(v in listOf(Double.NaN,Double.POSITIVE_INFINITY,-1.0,86_400_001.0)) refused { run(v) }
        refused { PerformanceRun(context,listOf(1.0)) }
        for(c in listOf(context.copy(workloadSha256="e".repeat(64)),context.copy(environmentSha256="e".repeat(64)),
            context.copy(configurationSha256="e".repeat(64)),context.copy(origin=MeasurementOrigin.HOST_OBSERVATION),context.copy(warmups=3))) {
            assertEquals(ComparisonStatus.INCOMPARABLE,PerformanceComparator.compare(run(100.0),PerformanceRun(c,List(20) { 90.0 })).status)
        }
        assertEquals(ComparisonStatus.INCOMPARABLE,PerformanceComparator.compare(run(100.0),PerformanceRun(context,List(5) { 90.0 })).status)
    }
    @Test fun durableIdempotentHistoryAndConflicts() {
        val p=Files.createTempDirectory("experiments").resolve("history.db");val id=UUID.randomUUID().toString()
        ExperimentRepository(p).use { r ->
            r.record(id,100,run(100.0),run(120.0)); r.record(id,100,run(100.0),run(120.0))
            refused { r.record(id,100,run(100.0),run(80.0)) }
            assertEquals(1,r.list().size)
        }
        ExperimentRepository(p).use { r ->
            assertEquals(ComparisonStatus.REGRESSION,r.list().single().comparison.status)
            assertEquals(run(120.0).sha256,r.list().single().after.sha256)
        }
    }
    @Test fun corruptHistoryRefusedAndPreserved() {
        val p=Files.createTempDirectory("experiments").resolve("history.db")
        ExperimentRepository(p).use { it.record(UUID.randomUUID().toString(),100,run(100.0),run(120.0)) }
        DriverManager.getConnection("jdbc:sqlite:$p").use { db -> db.createStatement().use { it.execute("UPDATE experiments SET after_samples='0x1.0p0,0x1.0p0,0x1.0p0,0x1.0p0,0x1.0p0'") } }
        val bytes=Files.readAllBytes(p)
        refused { ExperimentRepository(p).close() }
        assertArrayEquals(bytes,Files.readAllBytes(p))
    }
    @Test fun capacityIsAtomic() {
        val p=Files.createTempDirectory("experiments").resolve("history.db")
        ExperimentRepository(p).use { r ->
            repeat(128) { r.record(UUID.randomUUID().toString(),it.toLong(),run(100.0),run(120.0)) }
            refused { r.record(UUID.randomUUID().toString(),129,run(100.0),run(120.0)) }; assertEquals(128,r.list().size)
        }
    }
}
