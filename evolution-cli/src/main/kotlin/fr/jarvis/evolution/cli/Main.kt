package fr.jarvis.evolution.cli

import fr.jarvis.evolution.events.*
import fr.jarvis.evolution.probes.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

fun main(args: Array<String>) {
    try {
        if(args.firstOrNull()=="proposal-policy-demo") { proposalPolicyDemo(args); return }
        if(args.firstOrNull() in setOf("diagnostic-demo","diagnose","cases","case-state")) { diagnosticsMain(args); return }
        require(args.size in 2..3) { "USAGE" }
        val path=Path.of(args[1]).toAbsolutePath()
        when(args[0]) {
            "demo" -> {
                require(args.size==2 && !Files.exists(path)) { "NEW_DEMO_DATABASE_REQUIRED" }
                Files.createDirectories(path.parent)
                BoundedEventStore(path,StoreLimits(maxRows=128)).use { store ->
                    var now=1_000_000L
                    val service=LocalTelemetryService(store,32) { now++ }
                    check(!service.snapshot().enabled)
                    service.setEnabled(true) // Explicitly synthetic CLI demonstration, never production opt-in.
                    for(i in 0 until 100) {
                        check(service.submit(EvolutionEvent(Module.entries[(i/10)%8],EventCode.entries[i%10],
                            if(i<80) Severity.WARNING else Severity.ERROR,
                            mapOf("latencyMs" to 1000+i,"message" to "SYNTHETIC_PRIVATE_TEXT_MUST_NOT_PERSIST")))==SubmitResult.ACCEPTED)
                        if(i%24==23) service.drain()
                    }
                    service.drain(); check(service.snapshot().persisted==100L)
                    val rows=store.query(); check(rows.size==100 && rows.sumOf { it.occurrences }==100L)
                    println("{\"schemaVersion\":1,\"synthetic\":true,\"persistedIncidents\":${rows.size},\"occurrences\":100,\"dropped\":${service.snapshot().dropped}}")
                }
            }
            "list" -> { require(Files.isRegularFile(path)); BoundedEventStore(path).use { println(SnapshotJson.incidents(it.query(args.getOrNull(2)?.let(Module::valueOf)))) } }
            "export" -> {
                require(args.size==3 && Files.isRegularFile(path))
                val target=Path.of(args[2]); require(!Files.exists(target))
                BoundedEventStore(path).use { store -> Files.writeString(target,SnapshotJson.incidents(store.query())+"\n",StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE) }
                println("{\"exported\":true}")
            }
            "purge" -> {
                require(args.size==3 && Files.isRegularFile(path))
                val now=args[2].toLong(); require(now>=0)
                BoundedEventStore(path).use { println("{\"expiredRemoved\":${it.prune(now)}}") }
            }
            else -> error("USAGE")
        }
    } catch (_: Exception) {
        // Never include filesystem paths, input values or SQL/exception details in diagnostic output.
        System.err.println("REFUSED_OR_UNAVAILABLE: demo <new-db> | list <db> [MODULE] | export <db> <new-json> | purge <db> <now-ms> | diagnostic-demo <new-dir> | diagnose <events-db> <cases-db> [now-ms] | cases <cases-db> [MODULE] | case-state <cases-db> <id> <STATE>")
        kotlin.system.exitProcess(2)
    }
}
