package fr.jarvis.evolution

import fr.jarvis.evolution.campaign.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class CampaignTest {
    private val A = "a".repeat(40); private val B = "b".repeat(40)
    private fun p(rest: Int, after: Int, thinkFrames: Int = 6, answerVisible: Double = 0.2) = """
        {"sphereBounds":"0 0 10 10","rest":{"ms":3000,"sphereDraws":0,"windowFrames":0,"visibleChange":0},
         "listening":{"ms":3000,"sphereDraws":0,"windowFrames":30,"visibleChange":0,"stillListening":true},
         "thinking":{"ms":2000,"sphereDraws":$rest,"windowFrames":$thinkFrames,"visibleChange":0},
         "answering":{"ms":2000,"sphereDraws":24,"windowFrames":20,"visibleChange":$answerVisible},
         "restAfterAnswer":{"ms":3000,"sphereDraws":$after,"windowFrames":$after,"visibleChange":0}}"""
    private fun dir(vararg files: Pair<String, String>): Path { val d = Files.createTempDirectory("campaign"); files.forEach { (n, c) -> Files.writeString(d.resolve(n), c) }; return d }

    @Test fun onlyVerdictsAndClosedMetricsAreRead() {
        val d = dir("api29-results.txt" to "PASS a_manifest\nFAIL c_states\n--- instrumentation (tail)\nAssertionError: secret answer text\nPASS no_crash\n",
            "api29-audit-results.txt" to "PASS q_unseen\nFAIL k_sphere\njava.lang.AssertionError: \"La lumière est allumée\"\n",
            "api29-audit-q.json" to "{\"answers\":[{\"a\":\"PRIVATE ANSWER\"}]}", "api29-audit-p.json" to p(0, 12), "notes.txt" to "FAIL injected_from_other_file")
        val s = CampaignEvidenceReader.read(d, A)
        val t = s.apis.getValue(29).tests
        assertEquals(5, t.size)
        assertEquals(Verdict.FAIL, t[TestKind.QUALIFICATION to "c_states"]); assertEquals(Verdict.PASS, t[TestKind.AUDIT to "q_unseen"])
        assertFalse(t.keys.any { it.second.contains("injected") })
        assertEquals(12, s.apis.getValue(29).sphere.getValue(Phase.REST_AFTER_ANSWER).sphereDraws)
    }

    @Test fun realCamelCaseTestNamesAreRead() {   // defect found on real evidence (run 44391288619): only 4 tests were read
        val s = CampaignEvidenceReader.read(dir("api29-results.txt" to "PASS a_manifestSinglePermissionNoNetworkInstallable\nFAIL c_realEngineStatesCardsMenuLandscapeBackground\nPASS no_crash\n",
            "api29-audit-results.txt" to "PASS e2e3e6e7_layoutStressAccessibilityAvatar\nFAIL k_sphereStillAtEveryNonAnswerTransition\n"), A)
        assertEquals(5, s.apis.getValue(29).tests.size)
        assertEquals(Verdict.FAIL, s.apis.getValue(29).tests[TestKind.AUDIT to "k_sphereStillAtEveryNonAnswerTransition"])
    }

    @Test fun budgetBreachWithoutBeforeMeasurementIsOverBudgetNotStable() {   // defect found on real evidence
        val c = CampaignComparator.compare(CampaignEvidenceReader.read(dir("api29-results.txt" to "PASS a\n"), A),
            CampaignEvidenceReader.read(dir("api29-results.txt" to "PASS a\n", "api29-audit-p.json" to p(0, 12)), B))
        assertEquals(CampaignStatus.OVER_BUDGET, c.status)
    }

    @Test fun contradictoryVerdictsForOneTestFail() {
        val s = CampaignEvidenceReader.read(dir("api35-results.txt" to "PASS x_test\nFAIL x_test\nPASS x_test\n"), A)
        assertEquals(Verdict.FAIL, s.apis.getValue(35).tests[TestKind.QUALIFICATION to "x_test"])
    }

    @Test fun beforeAfterDetectsFixAndResidualBudgets() {
        val before = CampaignEvidenceReader.read(dir("api29-audit-results.txt" to "FAIL k_sphere\nFAIL p_sphere\nPASS q_unseen\n", "api29-audit-p.json" to p(0, 12, thinkFrames = 24)), A)
        val after = CampaignEvidenceReader.read(dir("api29-audit-results.txt" to "PASS k_sphere\nPASS p_sphere\nPASS q_unseen\n", "api29-audit-p.json" to p(0, 0, thinkFrames = 6)), B)
        val c = CampaignComparator.compare(before, after)
        assertEquals(CampaignStatus.IMPROVEMENT, c.status)
        assertEquals(2, c.tests.count { it.change == TestChange.FIXED }); assertTrue(c.budgets.isEmpty())
        assertTrue(c.proposals.all { !it.mayExecute })
        val back = CampaignComparator.compare(after, before)
        assertEquals(CampaignStatus.REGRESSION, back.status)
        assertTrue(back.budgets.any { it.code == BudgetCode.SPHERE_REDRAW_OUTSIDE_ANSWER && it.phase == Phase.REST_AFTER_ANSWER && it.after == 12.0 })
        assertTrue(back.budgets.any { it.code == BudgetCode.WINDOW_FRAMES_OVER_BUDGET && it.phase == Phase.THINKING })
        assertTrue(back.proposals.any { it.code == "TEST_REGRESSED" && it.subject == "AUDIT:k_sphere" })
    }

    @Test fun missingVerdictOrPhaseIsIncompleteNeverStable() {
        val before = CampaignEvidenceReader.read(dir("api29-results.txt" to "PASS a\nPASS b\n"), A)
        val after = CampaignEvidenceReader.read(dir("api29-results.txt" to "PASS a\n"), B)
        val c = CampaignComparator.compare(before, after)
        assertEquals(CampaignStatus.INCOMPLETE, c.status); assertTrue(c.proposals.any { it.code == "TEST_NOT_RUN" })
        val noP = CampaignComparator.compare(CampaignEvidenceReader.read(dir("api29-audit-p.json" to p(0, 0)), A),
            CampaignEvidenceReader.read(dir("api29-audit-p.json" to "{\"rest\":{\"ms\":3000}}"), B))
        assertEquals(CampaignStatus.INCOMPLETE, noP.status)
    }

    @Test fun sphereStillWhileAnsweringIsAFinding() {
        val c = CampaignComparator.compare(CampaignEvidenceReader.read(dir("api29-audit-p.json" to p(0, 0)), A),
            CampaignEvidenceReader.read(dir("api29-audit-p.json" to p(0, 0, answerVisible = 0.0)), B))
        assertTrue(c.budgets.any { it.code == BudgetCode.SPHERE_STILL_WHILE_ANSWERING })
    }

    @Test fun refusals() {
        val d = dir("api29-results.txt" to "PASS a\n")
        assertThrows(IllegalArgumentException::class.java) { CampaignEvidenceReader.read(d, "main") }
        assertThrows(IllegalArgumentException::class.java) { CampaignEvidenceReader.read(d, "a".repeat(39)) }
        val link = Files.createTempDirectory("campaign-link"); Files.createSymbolicLink(link.resolve("api29-results.txt"), d.resolve("api29-results.txt"))
        assertThrows(IllegalArgumentException::class.java) { CampaignEvidenceReader.read(link, A) }
        val big = dir(); Files.write(big.resolve("api29-results.txt"), ByteArray((CampaignEvidenceReader.MAX_FILE_BYTES + 1).toInt()) { 'P'.code.toByte() })
        assertThrows(IllegalArgumentException::class.java) { CampaignEvidenceReader.read(big, A) }
        assertThrows(IllegalArgumentException::class.java) { CampaignEvidenceReader.read(dir("api29-audit-p.json" to p(0, 0).replace("\"visibleChange\":0.2", "\"visibleChange\":7")), A) }
        assertThrows(IllegalArgumentException::class.java) { CampaignEvidenceReader.read(dir("api29-audit-p.json" to p(0, 0).replace("\"sphereDraws\":24", "\"sphereDraws\":2.5")), A) }
    }

    @Test fun readerNeverWritesToTheEvidenceDirectory() {
        val d = dir("api29-results.txt" to "PASS a\n", "api29-audit-p.json" to p(0, 0))
        val before = Files.list(d).use { s -> s.map { it.fileName.toString() + ":" + Files.getLastModifiedTime(it) }.sorted().toList() }
        CampaignEvidenceReader.read(d, A)
        assertEquals(before, Files.list(d).use { s -> s.map { it.fileName.toString() + ":" + Files.getLastModifiedTime(it) }.sorted().toList() })
    }

    @Test fun reportDeclaresNoExecution() {
        val c = CampaignComparator.compare(CampaignEvidenceReader.read(dir("api29-results.txt" to "FAIL a\n"), A), CampaignEvidenceReader.read(dir("api29-results.txt" to "FAIL a\n"), B))
        val j = CampaignReport.json(c)
        assertTrue(j.contains("\"executionAuthorized\":false")); assertTrue(j.contains("\"mayExecute\":false")); assertFalse(j.contains("\"mayExecute\":true"))
        assertTrue(j.contains("\"status\":\"STABLE\""))
    }
}
