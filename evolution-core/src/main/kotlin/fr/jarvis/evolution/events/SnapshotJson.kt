package fr.jarvis.evolution.events

/** Closed enum/numeric fields only. No device identifiers, arbitrary strings or application content. */
object SnapshotJson {
    fun incidents(rows: List<StoredIncident>): String {
        require(rows.size<=256)
        return "{\"schemaVersion\":1,\"incidents\":[" + rows.joinToString(",") { r ->
            val id=java.util.UUID.fromString(r.id).toString()
            val p=r.payload
            "{\"id\":\"$id\",\"module\":\"${r.module.name}\",\"category\":\"${r.code.category.name}\",\"code\":\"${r.code.name}\",\"severity\":\"${r.severity.name}\",\"firstAt\":${r.firstAt},\"lastAt\":${r.lastAt},\"occurrences\":${r.occurrences},\"latencyMs\":${p.latencyMs},\"usedBytes\":${p.usedBytes},\"limitBytes\":${p.limitBytes},\"queueSize\":${p.queueSize}}"
        } + "]}"
    }
}
