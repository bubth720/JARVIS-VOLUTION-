package fr.jarvis.evolution

import fr.jarvis.evolution.diagnostic.*
import fr.jarvis.evolution.events.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class DiagnosticMonitorTest {
    @Test fun optInPollingAndPersistentHumanDecisions() {
        val dir=Files.createTempDirectory("monitor")
        BoundedEventStore(dir.resolve("events.db")).use { events ->
            DiagnosticCaseRepository(dir.resolve("cases.db")).use { cases ->
                events.append(Sanitizer.sanitize(EvolutionEvent(Module.CORE,EventCode.CRASH_OBSERVED,Severity.ERROR)),1000)
                val monitor=DiagnosticMonitor(events,cases,intervalMs=1000)
                assertEquals("DISABLED",monitor.poll(1000).state); assertTrue(cases.list().isEmpty())
                monitor.setEnabled(true)
                val first=monitor.poll(1000); assertEquals("OBSERVED",first.state)
                assertEquals(RuleId.CRASH_OBSERVED,first.cases.single().rule)
                cases.transition(first.cases.single().id,CaseStatus.ACKNOWLEDGED,1000)
                assertSame(first,monitor.poll(1500)); assertSame(first,monitor.poll(900))
                assertEquals(CaseStatus.ACKNOWLEDGED,monitor.poll(2000).cases.single().status)
                monitor.setEnabled(false); assertTrue(monitor.snapshot().cases.isEmpty())
                assertEquals(CaseStatus.ACKNOWLEDGED,cases.list().single().status)
            }
        }
    }
    @Test fun missingQueueAndInvalidTimesAreUnknown() {
        val payload=Sanitizer.sanitize(EvolutionEvent(Module.CORE,EventCode.QUEUE_FULL,Severity.WARNING)).payload
        val row=StoredIncident(java.util.UUID.randomUUID().toString(),Module.CORE,EventCode.QUEUE_FULL,Severity.WARNING,1000,1000,1,payload)
        assertEquals(EvidenceIssue.MISSING_METRICS,RuleEngine().evaluate(listOf(row),1000).unknown.single().reason)
        assertEquals(EvidenceIssue.INVALID_METRICS,RuleEngine().evaluate(listOf(row.copy(firstAt=2000)),1000).unknown.single().reason)
        val valid=row.copy(payload=Sanitizer.sanitize(EvolutionEvent(Module.CORE,EventCode.QUEUE_FULL,Severity.WARNING,mapOf("queueSize" to 3))).payload)
        assertEquals(RuleId.QUEUE_SATURATION,RuleEngine().evaluate(listOf(valid),1000).candidates.single().rule)
    }
    @Test fun unavailableStoreIsUnknownWithoutInventedCases() {
        val dir=Files.createTempDirectory("monitor")
        DiagnosticCaseRepository(dir.resolve("cases.db")).use { cases ->
            val source=object: EventStore {
                override fun append(event: SanitizedEvent,now: Long) { error("UNAVAILABLE") }
                override fun query(module: Module?,since: Long,limit: Int): List<StoredIncident> { error("UNAVAILABLE") }
                override fun prune(now: Long)=0
                override fun close() {}
            }
            val monitor=DiagnosticMonitor(source,cases); monitor.setEnabled(true)
            assertEquals("UNKNOWN",monitor.poll(1000).state); assertTrue(cases.list().isEmpty())
        }
    }
}
