package fr.jarvis.evolution

import fr.jarvis.evolution.events.*
import fr.jarvis.evolution.diagnostic.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import java.util.UUID

class DiagnosticTest {
    private fun path()=Files.createTempDirectory("evolution-diagnostic-synthetic-").resolve("cases.db")
    private fun row(code:EventCode=EventCode.SLOW_OPERATION,module:Module=Module.LLM,severity:Severity=Severity.WARNING,
        first:Long=100,last:Long=102,count:Long=3,metrics:Map<String,Any?> = mapOf("latencyMs" to 2500,"usedBytes" to 900,"limitBytes" to 1000))=
        StoredIncident(UUID.randomUUID().toString(),module,code,severity,first,last,count,Sanitizer.sanitize(EvolutionEvent(module,code,severity,metrics)).payload)
    @Test fun sixRulesUseRealSignalsAndKeepOwners() {
        val rows=listOf(row(),row(EventCode.MEMORY_PRESSURE,Module.CORE),row(EventCode.FILE_UNAVAILABLE,Module.NEXUS),
            row(EventCode.PERMISSION_REFUSED,Module.SERVICES),row(EventCode.RELEASE_UNKNOWN,Module.VOICE),row(EventCode.SERVICE_UNAVAILABLE,Module.INTERFACE))
        val result=RuleEngine().evaluate(rows,1000)
        assertEquals(6,result.candidates.size);assertTrue(result.unknown.isEmpty())
        assertEquals(rows.map{it.module}.toSet(),result.candidates.map{it.owner}.toSet())
        assertTrue(result.candidates.all{it.evidence.single().incident.id in rows.map{r -> r.id}})
    }
    @Test fun missingMetricsFutureAndIncompleteWindowsNeverBecomeCertainCases() {
        val rows=listOf(row(metrics=emptyMap()),row(first=0,last=999999),row(first=1000000,last=1000001))
        val result=RuleEngine().evaluate(rows,1000000)
        assertTrue(result.candidates.isEmpty());assertEquals(3,result.unknown.size)
        assertEquals(EvidenceIssue.entries.toSet(),result.unknown.map{it.reason}.toSet())
        assertTrue(result.insufficientData)
    }
    @Test fun oldLowSeverityAndLowRatiosDoNotGenerateFalseDetections() {
        val rows=listOf(row(last=1,first=0),row(EventCode.RELEASE_UNKNOWN,severity=Severity.INFO),
            row(EventCode.MEMORY_PRESSURE,metrics=mapOf("usedBytes" to 10,"limitBytes" to 1000)),row(count=2))
        assertTrue(RuleEngine().evaluate(rows,1000000).candidates.isEmpty())
        assertTrue(RuleEngine().evaluate(emptyList(),1000).insufficientData)
    }
    @Test fun individualRulesCanBeDisabledWithoutCreatingAlternativeActions() {
        val result=RuleEngine(RuleRepository(setOf(RuleId.REPEATED_LATENCY))).evaluate(listOf(row()),1000)
        assertTrue(result.candidates.isEmpty());assertTrue(result.unknown.isEmpty())
    }
    @Test fun groupingKeepsMultipleEvidenceLinksAndStrongestImpact() {
        val rows=listOf(row(EventCode.RELEASE_UNKNOWN,severity=Severity.WARNING),row(EventCode.RELEASE_UNKNOWN,severity=Severity.ERROR))
        val c=RuleEngine().evaluate(rows,1000).candidates.single()
        assertEquals(2,c.evidence.size);assertEquals(Severity.ERROR,c.impact)
    }
    @Test fun casesEvidenceAcknowledgementAndJournalSurviveReopening() {
        val p=path();val rows=listOf(row());val result=RuleEngine().evaluate(rows,1000)
        val id=DiagnosticCaseRepository(p).use{repo ->val c=repo.record(result,rows,1000).single();repo.transition(c.id,CaseStatus.ACKNOWLEDGED,1001);c.id}
        DiagnosticCaseRepository(p).use{repo ->
            val c=repo.list().single();assertEquals(id,c.id);assertEquals(CaseStatus.ACKNOWLEDGED,c.status)
            assertEquals(rows.single(),c.evidence.single().incident);assertEquals(2,repo.journal(id).size)
            assertEquals(rows.single().id,c.evidence.single().incident.id)
        }
    }
    @Test fun repeatedEvaluationDoesNotDuplicateCasesOrResetHumanStatus() {
        val rows=listOf(row());val result=RuleEngine().evaluate(rows,1000)
        DiagnosticCaseRepository(path()).use{repo ->
            val id=repo.record(result,rows,1000).single().id;repo.transition(id,CaseStatus.ACKNOWLEDGED,1001)
            repeat(3){repo.record(result,rows,1002)}
            assertEquals(1,repo.list().size);assertEquals(1,repo.list().single().evidence.size)
            assertEquals(CaseStatus.ACKNOWLEDGED,repo.list().single().status);assertEquals(2,repo.journal(id).size)
            repo.record(RuleEngine().evaluate(emptyList(),1003),emptyList(),1003)
            assertEquals(CaseStatus.ACKNOWLEDGED,repo.list().single().status)
        }
    }
    @Test fun inventedSourceOrChangedDigestIsRejectedBeforeWriting() {
        val rows=listOf(row());val result=RuleEngine().evaluate(rows,1000)
        DiagnosticCaseRepository(path()).use{repo ->
            assertThrows(IllegalArgumentException::class.java){repo.record(result,emptyList(),1000)}
            val forged=result.copy(candidates=result.candidates.map{it.copy(evidence=listOf(it.evidence.single().copy(sha256="0".repeat(64))))})
            assertThrows(IllegalArgumentException::class.java){repo.record(forged,rows,1000)}
            assertTrue(repo.list().isEmpty())
        }
    }
    @Test fun humanStateTransitionsAreExplicitAndDoNotEraseHistory() {
        val rows=listOf(row());DiagnosticCaseRepository(path()).use{repo ->
            val id=repo.record(RuleEngine().evaluate(rows,1000),rows,1000).single().id
            assertThrows(IllegalArgumentException::class.java){repo.transition(id,CaseStatus.OPEN,1001)}
            repo.transition(id,CaseStatus.RESOLVED,1001)
            assertThrows(IllegalArgumentException::class.java){repo.transition(id,CaseStatus.ACKNOWLEDGED,1002)}
            repo.transition(id,CaseStatus.OPEN,1002);repo.transition(id,CaseStatus.REJECTED,1003)
            assertEquals(4,repo.journal(id).size)
        }
    }
    @Test fun corruptedEvidenceIsRefusedInsteadOfPresentedAsVerified() {
        val p=path();val rows=listOf(row())
        DiagnosticCaseRepository(p).use{it.record(RuleEngine().evaluate(rows,1000),rows,1000)}
        DriverManager.getConnection("jdbc:sqlite:$p").use{it.createStatement().use{s ->s.execute("UPDATE evidence SET digest='bad'")}}
        DiagnosticCaseRepository(p).use{repo ->assertThrows(IllegalArgumentException::class.java){repo.list()}}
    }
    @Test fun snapshotsRemainVerifiableWhenSourceRetentionRemovesTheEvent() {
        val eventPath=path();val casePath=path()
        BoundedEventStore(eventPath,StoreLimits(retentionMs=10)).use{events ->
            repeat(3){events.append(Sanitizer.sanitize(EvolutionEvent(Module.LLM,EventCode.SLOW_OPERATION,Severity.WARNING,mapOf("latencyMs" to 3000))),it.toLong())}
            DiagnosticCaseRepository(casePath).use{repo ->
                val source=events.query();repo.record(RuleEngine().evaluate(source,3),source,3)
                events.prune(100);assertTrue(events.query().isEmpty())
                val proof=repo.list().single().evidence.single();assertEquals(proof.sha256,EvidenceLink.from(proof.incident).sha256)
            }
        }
    }
}
