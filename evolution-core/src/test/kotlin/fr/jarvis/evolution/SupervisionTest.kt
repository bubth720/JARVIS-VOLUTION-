package fr.jarvis.evolution

import fr.jarvis.evolution.diagnostic.*
import fr.jarvis.evolution.events.*
import fr.jarvis.evolution.performance.*
import fr.jarvis.evolution.supervision.*
import org.junit.Assert.*
import org.junit.Test

class SupervisionTest {
    @Test fun offlineHtmlEscapesAndExplainsEvidence() {
        val c=MeasurementContext(Module.LLM,"a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(40),2,MeasurementOrigin.SYNTHETIC)
        val before=PerformanceRun(c,List(20) { 100.0 }); val after=PerformanceRun(c,List(20) { 120.0 })
        val experiment=Experiment("<script>bad</script>",100,before,after)
        val snapshot=SupervisionSnapshot(100,"INSUFFICIENT_DATA",0,0,emptyList(),listOf(experiment),emptyList())
        val html=SupervisionRenderer.html(snapshot)
        assertFalse(html.contains("<script>")); assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("SANDBOX_NOT_QUALIFIED")); assertTrue(html.contains("SYNTHETIC"))
        assertTrue(html.contains("default-src 'none'")); assertFalse(html.contains("https://"))
        assertEquals(listOf(before.sha256,after.sha256),snapshot.recommendations.single().evidenceSha256)
    }
    @Test fun closedCasesDoNotRecommendAnotherAction() {
        val case=DiagnosticCase("case",RuleId.FILE_UNAVAILABLE,Module.CORE,Severity.ERROR,CaseStatus.RESOLVED,0,0,emptyList())
        val snapshot=SupervisionSnapshot(100,"OBSERVED",1,0,listOf(case),emptyList(),emptyList())
        assertTrue(snapshot.recommendations.isEmpty())
    }
}
