package fr.jarvis.evolution.diagnostic

import fr.jarvis.evolution.events.SnapshotJson

object DiagnosticReportRenderer {
    private fun quote(s:String) = "\""+buildString {for(c in s)when(c){'"'->append("\\\"");'\\'->append("\\\\");else->if(c.code<32)append("\\u%04x".format(c.code))else append(c)}}+"\""
    fun json(cases:List<DiagnosticCase>):String {
        require(cases.size<=128)
        return "{\"schemaVersion\":1,\"ruleVersion\":1,\"cases\":["+cases.joinToString(","){c ->
            "{\"id\":${quote(c.id)},\"rule\":${quote(c.rule.name)},\"owner\":${quote(c.owner.name)},\"impact\":${quote(c.impact.name)},\"status\":${quote(c.status.name)},\"confidence\":\"POSSIBLE_CAUSE\",\"recommendation\":${quote(RecommendationCatalog.recommendation(c.rule))},\"evidence\":["+
                c.evidence.joinToString(","){e ->"{\"sha256\":${quote(e.sha256)},\"snapshot\":${SnapshotJson.incidents(listOf(e.incident))}}"}+"]}"
        }+"]}"
    }
}
