package fr.jarvis.evolution.campaign

/**
 * F08 — deterministic before/after comparison of two V3 campaigns, and improvement PROPOSALS for human review.
 * Nothing here executes, schedules, edits or applies anything: `mayExecute` is always false (EVOL-03 sandbox is not
 * qualified) and a proposal is only a pointer to evidence plus a suggested investigation.
 */
enum class TestChange { FIXED, REGRESSED, STILL_FAILING, STABLE_PASS, NEW_PASS, NEW_FAIL, MISSING_AFTER }
enum class CampaignStatus { REGRESSION, OVER_BUDGET, IMPROVEMENT, STABLE, INCOMPLETE }
enum class BudgetCode { SPHERE_REDRAW_OUTSIDE_ANSWER, VISIBLE_MOTION_OUTSIDE_ANSWER, WINDOW_FRAMES_OVER_BUDGET, SPHERE_STILL_WHILE_ANSWERING, PHASE_NOT_MEASURED }

data class TestDelta(val apiLevel: Int, val kind: TestKind, val test: String, val change: TestChange)
data class BudgetFinding(val apiLevel: Int, val phase: Phase, val code: BudgetCode, val before: Double?, val after: Double?, val limit: Double)
data class ImprovementProposal(val code: String, val apiLevel: Int, val subject: String, val suggestion: String,
                               val evidence: String, val mayExecute: Boolean = false, val state: String = "PROPOSED_FOR_HUMAN_REVIEW")
data class CampaignComparison(val before: String, val after: String, val status: CampaignStatus, val tests: List<TestDelta>,
                              val budgets: List<BudgetFinding>, val proposals: List<ImprovementProposal>)

object CampaignComparator {
    const val VERSION = 1
    /** Owner budgets for the sphere (test P, 15/11/2026): still and cheap outside the answer, animated while answering. */
    private val FRAMES_PER_SECOND_MAX = mapOf(Phase.REST to 5.0, Phase.LISTENING to 15.0, Phase.THINKING to 5.0, Phase.REST_AFTER_ANSWER to 5.0)

    fun compare(before: CampaignSnapshot, after: CampaignSnapshot): CampaignComparison {
        val tests = mutableListOf<TestDelta>(); val budgets = mutableListOf<BudgetFinding>()
        for (api in (before.apis.keys + after.apis.keys).sorted()) {
            val b = before.apis[api]?.tests.orEmpty(); val a = after.apis[api]?.tests.orEmpty()
            for (key in (b.keys + a.keys).sortedWith(compareBy({ it.first }, { it.second }))) {
                val change = when (b[key] to a[key]) {
                    Verdict.FAIL to Verdict.PASS -> TestChange.FIXED
                    Verdict.PASS to Verdict.FAIL -> TestChange.REGRESSED
                    Verdict.FAIL to Verdict.FAIL -> TestChange.STILL_FAILING
                    Verdict.PASS to Verdict.PASS -> TestChange.STABLE_PASS
                    null to Verdict.PASS -> TestChange.NEW_PASS
                    null to Verdict.FAIL -> TestChange.NEW_FAIL
                    else -> TestChange.MISSING_AFTER
                }
                tests += TestDelta(api, key.first, key.second, change)
            }
            budgets += budgetFindings(api, before.apis[api]?.sphere.orEmpty(), after.apis[api]?.sphere.orEmpty())
        }
        val regressed = tests.any { it.change == TestChange.REGRESSED || it.change == TestChange.NEW_FAIL }
        val missing = tests.any { it.change == TestChange.MISSING_AFTER } || budgets.any { it.code == BudgetCode.PHASE_NOT_MEASURED }
        val worse = budgets.any { it.before != null && it.after != null && it.after > it.before }
        val improved = tests.any { it.change == TestChange.FIXED }
        val status = when {
            regressed || worse -> CampaignStatus.REGRESSION
            // A budget breach of the AFTER campaign is never STABLE or IMPROVEMENT, even without a BEFORE measurement.
            budgets.any { it.code != BudgetCode.PHASE_NOT_MEASURED } -> CampaignStatus.OVER_BUDGET
            missing -> CampaignStatus.INCOMPLETE
            improved -> CampaignStatus.IMPROVEMENT
            else -> CampaignStatus.STABLE
        }
        return CampaignComparison(before.revision, after.revision, status, tests, budgets, proposals(after.revision, tests, budgets))
    }

    /** Budget breaches of the AFTER campaign, with the BEFORE value when it exists (null = not measured). */
    private fun budgetFindings(api: Int, b: Map<Phase, PhaseMetrics>, a: Map<Phase, PhaseMetrics>): List<BudgetFinding> {
        if (a.isEmpty() && b.isEmpty()) return emptyList()
        val out = mutableListOf<BudgetFinding>()
        for (phase in Phase.entries) {
            val m = a[phase]
            if (m == null) { out += BudgetFinding(api, phase, BudgetCode.PHASE_NOT_MEASURED, null, null, 0.0); continue }
            val p = b[phase]
            if (phase == Phase.ANSWERING) {
                if (m.visibleChange <= 0.005) out += BudgetFinding(api, phase, BudgetCode.SPHERE_STILL_WHILE_ANSWERING, p?.visibleChange, m.visibleChange, 0.005)
                continue
            }
            if (m.sphereDraws > 1) out += BudgetFinding(api, phase, BudgetCode.SPHERE_REDRAW_OUTSIDE_ANSWER, p?.sphereDraws?.toDouble(), m.sphereDraws.toDouble(), 1.0)
            if (m.visibleChange > 0.001) out += BudgetFinding(api, phase, BudgetCode.VISIBLE_MOTION_OUTSIDE_ANSWER, p?.visibleChange, m.visibleChange, 0.001)
            val limit = FRAMES_PER_SECOND_MAX.getValue(phase)
            if (m.windowFramesPerSecond > limit) out += BudgetFinding(api, phase, BudgetCode.WINDOW_FRAMES_OVER_BUDGET, p?.windowFramesPerSecond, m.windowFramesPerSecond, limit)
        }
        return out
    }

    private fun proposals(after: String, tests: List<TestDelta>, budgets: List<BudgetFinding>): List<ImprovementProposal> {
        val ev = "campaign ${after.take(12)}"
        val out = mutableListOf<ImprovementProposal>()
        tests.filter { it.change in setOf(TestChange.REGRESSED, TestChange.NEW_FAIL, TestChange.STILL_FAILING) }.forEach {
            out += ImprovementProposal("TEST_${it.change}", it.apiLevel, "${it.kind}:${it.test}",
                "Reproduire ${it.test} seul sur API ${it.apiLevel}, comparer au dernier SHA vert, proposer un correctif ciblé à la revue humaine.", "$ev ${it.kind} ${it.test}")
        }
        tests.filter { it.change == TestChange.MISSING_AFTER }.forEach {
            out += ImprovementProposal("TEST_NOT_RUN", it.apiLevel, "${it.kind}:${it.test}", "Le test n'a pas produit de verdict : vérifier qu'il est toujours exécuté (NOT_RUN n'est jamais PASS).", "$ev ${it.test}")
        }
        budgets.filter { it.code != BudgetCode.PHASE_NOT_MEASURED }.forEach {
            val s = when (it.code) {
                BudgetCode.SPHERE_REDRAW_OUTSIDE_ANSWER -> "Chercher l'état ou l'effet qui redessine la sphère en phase ${it.phase} (boucle d'animation non arrêtée, état lu dans la portée de dessin)."
                BudgetCode.VISIBLE_MOTION_OUTSIDE_ANSWER -> "Identifier l'animation visible en phase ${it.phase} ; elle doit être instantanée hors réponse."
                BudgetCode.WINDOW_FRAMES_OVER_BUDGET -> "Trouver le composant qui rafraîchit la fenêtre en phase ${it.phase} (vu-mètre, texte de statut) et borner sa cadence."
                else -> "Vérifier que la sphère s'anime pendant la réponse (contrôle positif)."
            }
            out += ImprovementProposal(it.code.name, it.apiLevel, it.phase.name, s, "$ev P ${it.phase} after=${it.after} limit=${it.limit}")
        }
        return out
    }
}

object CampaignReport {
    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    private fun n(d: Double?) = d?.let { if (it == Math.floor(it) && kotlin.math.abs(it) < 1e9) it.toLong().toString() else "%.4f".format(java.util.Locale.ROOT, it) } ?: "null"
    fun json(c: CampaignComparison): String = buildString {
        append("{\"schema\":\"jarvis.evolution.campaign-comparison/1\",\"comparatorVersion\":${CampaignComparator.VERSION},")
        append("\"before\":${q(c.before)},\"after\":${q(c.after)},\"status\":${q(c.status.name)},\"executionAuthorized\":false,")
        append("\"summary\":{" + TestChange.entries.joinToString(",") { ch -> "${q(ch.name)}:${c.tests.count { it.change == ch }}" } + "},")
        append("\"tests\":[" + c.tests.filter { it.change != TestChange.STABLE_PASS }.joinToString(",") { "{\"api\":${it.apiLevel},\"kind\":${q(it.kind.name)},\"test\":${q(it.test)},\"change\":${q(it.change.name)}}" } + "],")
        append("\"budgets\":[" + c.budgets.joinToString(",") { "{\"api\":${it.apiLevel},\"phase\":${q(it.phase.name)},\"code\":${q(it.code.name)},\"before\":${n(it.before)},\"after\":${n(it.after)},\"limit\":${n(it.limit)}}" } + "],")
        append("\"proposals\":[" + c.proposals.joinToString(",") { "{\"code\":${q(it.code)},\"api\":${it.apiLevel},\"subject\":${q(it.subject)},\"suggestion\":${q(it.suggestion)},\"evidence\":${q(it.evidence)},\"mayExecute\":${it.mayExecute},\"state\":${q(it.state)}}" } + "]}")
    }
}
