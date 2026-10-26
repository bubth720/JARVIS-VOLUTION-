package fr.jarvis.evolution.supervision

import fr.jarvis.evolution.diagnostic.*
import fr.jarvis.evolution.performance.*
import fr.jarvis.evolution.review.*

data class ImprovementRecommendation(val owner: String,val rationale: String,val evidenceSha256: List<String>,val action: String)
data class SupervisionSnapshot(val now: Long,val diagnosticState: String,val observed: Int,val unknown: Int,
    val cases: List<DiagnosticCase>,val experiments: List<Experiment>,val reviews: List<ReviewEntry>) {
    init {
        require(now>=0 && diagnosticState in setOf("OBSERVED","INSUFFICIENT_DATA","UNKNOWN","DISABLED","WAITING_FOR_HOST_POLL"))
        require(observed in 0..256 && unknown in 0..256 && cases.size<=128 && experiments.size<=128 && reviews.size<=128)
    }
    val recommendations: List<ImprovementRecommendation> get() =
        cases.filter { it.status in setOf(CaseStatus.OPEN,CaseStatus.ACKNOWLEDGED) }.map {
            ImprovementRecommendation(it.owner.name,"POSSIBLE_CAUSE: "+it.rule.name+" ; mesures : "+it.evidence.joinToString("; ") { e ->
                "occurrences="+e.incident.occurrences+", latencyMs="+e.incident.payload.latencyMs+
                    ", usedBytes="+e.incident.payload.usedBytes+", limitBytes="+e.incident.payload.limitBytes+", queueSize="+e.incident.payload.queueSize
            },it.evidence.map { e -> e.sha256 },
                RecommendationCatalog.recommendation(it.rule))
        } + experiments.filter { it.comparison.status==ComparisonStatus.REGRESSION }.map {
            ImprovementRecommendation(it.before.context.module.name,
                "Régression descriptive médiane/p95 ; origine déclarée : "+it.before.context.origin.name,
                listOf(it.before.sha256,it.after.sha256),
                "Reproduire la mesure dans le même contexte et demander une revue humaine ; aucun gain causal ni correctif validé.")
        }
}

/** Offline report only: no server, external asset, script, browser storage, action endpoint or execution. */
object SupervisionRenderer {
    private fun escape(value: String): String = buildString {
        for(c in value) append(when(c) { '&' -> "&amp;"; '<' -> "&lt;"; '>' -> "&gt;"; '"' -> "&quot;"; '\'' -> "&#39;"; else -> c.toString() })
    }
    fun html(snapshot: SupervisionSnapshot): String = buildString {
        append("<!doctype html><html lang=\"fr\"><meta charset=\"utf-8\">")
        append("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; base-uri 'none'; form-action 'none'\">")
        append("<title>JARVIS — supervision locale</title><body><h1>JARVIS — supervision locale</h1>")
        append("<p>Instantané local à "+snapshot.now+" ms. Actualisation explicite par l’hôte.</p>")
        append("<p>Laboratoire : DÉSACTIVÉ — SANDBOX_NOT_QUALIFIED. Aucun correctif exécuté ou appliqué.</p>")
        append("<h2>Diagnostics</h2><p>État : "+escape(snapshot.diagnosticState)+
            " ; incidents observés : "+snapshot.observed+" ; preuves inconnues : "+snapshot.unknown+".</p><ul>")
        for(c in snapshot.cases) {
            append("<li>"+escape(c.owner.name+" / "+c.rule.name+" / "+c.status.name)+
                " — cause possible ; preuves : "+c.evidence.size+"</li>")
        }
        append("</ul><h2>Performances et historique</h2>")
        append("<p>Comparaison descriptive v1 : variation ≥ 10 % ET ≥ 5 ms, médiane ou p95. Un recul prévaut. Aucune significativité statistique revendiquée.</p>")
        append("<table><caption>Expériences persistées — provenance déclarée par l’hôte</caption><tr><th>ID</th><th>Origine</th><th>Résultat</th><th>Médiane avant/après (ms)</th><th>p95 avant/après (ms)</th></tr>")
        for(e in snapshot.experiments) {
            append("<tr><td>"+escape(e.id)+"</td><td>"+e.before.context.origin.name+"</td><td>"+e.comparison.status.name+
                "</td><td>"+e.before.medianMs+" / "+e.after.medianMs+"</td><td>"+e.before.p95Ms+" / "+e.after.p95Ms+"</td></tr>")
        }
        append("</table><h2>Recommandations argumentées</h2><ul>")
        for(r in snapshot.recommendations) append("<li>"+escape(r.owner+" : "+r.rationale+" — "+r.action)+
            "<br>SHA-256 des preuves : "+escape(r.evidenceSha256.joinToString(", "))+"</li>")
        append("</ul><h2>Revue humaine</h2><p>Une approbation pour le laboratoire ne donne aucun droit de production.</p><ul>")
        for(r in snapshot.reviews) append("<li>"+escape(r.metadata.id+" / "+r.status.name+" / SHA-256 "+r.metadata.contentSha256)+"</li>")
        append("</ul></body></html>")
    }
}
