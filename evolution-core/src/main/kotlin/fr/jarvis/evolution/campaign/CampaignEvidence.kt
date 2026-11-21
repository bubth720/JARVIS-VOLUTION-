package fr.jarvis.evolution.campaign

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * FINAL-01 / F08 — read-only adapter from JARVIS V3 campaign evidence (the review files the INTERFACE CI commits
 * under labs/v3/captures/run-<id>/) to technical observations EVOLUTION can compare.
 *
 * Privacy by construction: only three file kinds are opened (`apiNN-results.txt`, `apiNN-audit-results.txt`,
 * `apiNN-audit-p.json`); only `PASS|FAIL <identifier>` lines and a closed set of numeric sphere metrics survive.
 * Answers, questions, document text, stack traces and any other content are never read into the model.
 * Nothing is written to the evidence directory; symbolic links and files above 1 MiB are refused.
 */
enum class Verdict { PASS, FAIL }
enum class TestKind { QUALIFICATION, AUDIT }
enum class Phase { REST, LISTENING, THINKING, ANSWERING, REST_AFTER_ANSWER }

/** Sphere rendering metrics of test P for one phase (numbers only, bounded). */
data class PhaseMetrics(val windowMs: Int, val sphereDraws: Int, val windowFrames: Int, val visibleChange: Double) {
    init {
        require(windowMs in 1..60_000 && sphereDraws in 0..100_000 && windowFrames in 0..100_000)
        require(visibleChange.isFinite() && visibleChange in 0.0..1.0)
    }
    val windowFramesPerSecond: Double get() = windowFrames * 1000.0 / windowMs
}

data class ApiEvidence(val apiLevel: Int, val tests: Map<Pair<TestKind, String>, Verdict>, val sphere: Map<Phase, PhaseMetrics>)

data class CampaignSnapshot(val revision: String, val apis: Map<Int, ApiEvidence>) {
    init { require(revision.matches(Regex("[0-9a-f]{40}"))) { "FULL_REVISION_REQUIRED" } }
}

object CampaignEvidenceReader {
    const val MAX_FILE_BYTES = 1L shl 20
    private val LINE = Regex("^(PASS|FAIL) ([A-Za-z][A-Za-z0-9_]{0,99})(?:\\s|$)")   // real names are camelCase (a_manifestSingle…)
    private val API_FILE = Regex("^api(\\d{2})-(results\\.txt|audit-results\\.txt|audit-p\\.json)$")
    private val PHASE_KEYS = mapOf("rest" to Phase.REST, "listening" to Phase.LISTENING, "thinking" to Phase.THINKING,
        "answering" to Phase.ANSWERING, "restAfterAnswer" to Phase.REST_AFTER_ANSWER)

    fun read(directory: Path, revision: String): CampaignSnapshot {
        require(Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) { "EVIDENCE_DIRECTORY_REQUIRED" }
        val byApi = mutableMapOf<Int, Triple<MutableMap<Pair<TestKind, String>, Verdict>, MutableMap<Phase, PhaseMetrics>, Unit>>()
        Files.list(directory).use { stream ->
            for (file in stream.sorted().toList()) {
                val m = API_FILE.matchEntire(file.fileName.toString()) ?: continue
                require(!Files.isSymbolicLink(file) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) { "SYMLINK_OR_SPECIAL_FILE_REFUSED" }
                require(Files.size(file) <= MAX_FILE_BYTES) { "EVIDENCE_FILE_TOO_LARGE" }
                val api = m.groupValues[1].toInt(); require(api in 21..99) { "API_LEVEL_OUT_OF_RANGE" }
                val slot = byApi.getOrPut(api) { Triple(mutableMapOf(), mutableMapOf(), Unit) }
                val text = Files.readString(file)
                when (m.groupValues[2]) {
                    "results.txt" -> verdicts(text, TestKind.QUALIFICATION, slot.first)
                    "audit-results.txt" -> verdicts(text, TestKind.AUDIT, slot.first)
                    else -> slot.second.putAll(sphere(text))
                }
            }
        }
        return CampaignSnapshot(revision, byApi.mapValues { (api, s) -> ApiEvidence(api, s.first.toMap(), s.second.toMap()) })
    }

    /** One verdict per test; contradictory lines for the same test make the test FAIL (never an optimistic merge). */
    internal fun verdicts(text: String, kind: TestKind, into: MutableMap<Pair<TestKind, String>, Verdict>) {
        for (line in text.lineSequence()) {
            val m = LINE.find(line) ?: continue
            val key = kind to m.groupValues[2]; val v = Verdict.valueOf(m.groupValues[1])
            into[key] = if (into[key] == Verdict.FAIL || v == Verdict.FAIL) Verdict.FAIL else Verdict.PASS
        }
    }

    /** Closed extraction: `"<phase>": { ... "ms": n, "sphereDraws": n, "windowFrames": n, "visibleChange": x ... }`. */
    internal fun sphere(json: String): Map<Phase, PhaseMetrics> {
        val out = mutableMapOf<Phase, PhaseMetrics>()
        for ((key, phase) in PHASE_KEYS) {
            val body = Regex("\"" + key + "\"\\s*:\\s*\\{([^{}]*)\\}").find(json)?.groupValues?.get(1) ?: continue
            fun num(name: String) = Regex("\"" + name + "\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?(?:[eE][-+]?[0-9]+)?)").find(body)?.groupValues?.get(1)?.toDouble()
            val ms = num("ms"); val draws = num("sphereDraws"); val frames = num("windowFrames"); val visible = num("visibleChange")
            if (ms == null || draws == null || frames == null || visible == null) continue   // incomplete phase -> absent, never zero
            require(listOf(ms, draws, frames).all { it == Math.floor(it) }) { "INTEGER_METRIC_REQUIRED" }
            out[phase] = PhaseMetrics(ms.toInt(), draws.toInt(), frames.toInt(), visible)
        }
        return out
    }
}
