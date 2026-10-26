package fr.jarvis.evolution.cli

import fr.jarvis.evolution.diagnostic.*
import fr.jarvis.evolution.events.*
import fr.jarvis.evolution.performance.*
import fr.jarvis.evolution.review.*
import fr.jarvis.evolution.supervision.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.Properties
import java.util.UUID

private fun safePath(value: String): Path {
    val path=Path.of(value).toAbsolutePath().normalize();var cursor: Path?=path
    while(cursor!=null) { require(!Files.isSymbolicLink(cursor));cursor=cursor.parent }
    return path
}
private fun existing(value: String): Path = safePath(value).also { require(Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS)) }
private fun readBounded(value: String): String {
    val path=existing(value)
    Files.newByteChannel(path,StandardOpenOption.READ,LinkOption.NOFOLLOW_LINKS).use { channel ->
        require(channel.size() in 1..8192)
        val bytes=java.nio.ByteBuffer.allocate(8193)
        while(bytes.hasRemaining() && channel.read(bytes)!=-1) {}
        require(bytes.position()<=8192)
        bytes.flip()
        return Charsets.UTF_8.newDecoder().decode(bytes).toString()
    }
}
private fun samples(value: String): List<Double> {
    val text=readBounded(value).trim()
    require(text.split(',').size in 5..256)
    return text.split(',').map { it.trim().toDouble() }
}
private fun exportHtml(path: Path,snapshot: SupervisionSnapshot) {
    require(Files.isDirectory(path.parent))
    Files.writeString(path,SupervisionRenderer.html(snapshot),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)
}
private fun printHistory(experiments: List<Experiment>) {
    println("{\"schemaVersion\":1,\"experiments\":["+experiments.joinToString(",") { e ->
        "{\"id\":\""+e.id+"\",\"createdAt\":"+e.createdAt+",\"origin\":\""+e.before.context.origin.name+
            "\",\"status\":\""+e.comparison.status.name+"\",\"baselineSha256\":\""+e.before.sha256+
            "\",\"candidateSha256\":\""+e.after.sha256+"\",\"medianBeforeMs\":"+e.before.medianMs+
            ",\"medianAfterMs\":"+e.after.medianMs+",\"p95BeforeMs\":"+e.before.p95Ms+",\"p95AfterMs\":"+e.after.p95Ms+"}"
    }+"]}")
}
fun evolutionMain(args: Array<String>) {
    when(args[0]) {
        "compare" -> {
            require(args.size==6)
            val context=Properties().apply { load(readBounded(args[2]).reader()) }
            require(context.stringPropertyNames()==setOf("module","workloadSha256","environmentSha256","configurationSha256","beforeRevision","afterRevision","warmups","origin"))
            val c=MeasurementContext(Module.valueOf(context.getProperty("module")),context.getProperty("workloadSha256"),
                context.getProperty("environmentSha256"),context.getProperty("configurationSha256"),context.getProperty("beforeRevision"),
                context.getProperty("warmups").toInt(),MeasurementOrigin.valueOf(context.getProperty("origin")))
            val before=PerformanceRun(c,samples(args[3]))
            val after=PerformanceRun(c.copy(revision=context.getProperty("afterRevision")),samples(args[4]))
            ExperimentRepository(safePath(args[1])).use { repo ->
                val result=repo.record(UUID.randomUUID().toString(),args[5].toLong(),before,after)
                printHistory(listOf(result))
            }
        }
        "history" -> {
            require(args.size==2)
            ExperimentRepository(existing(args[1])).use { printHistory(it.list()) }
        }
        "review-list" -> {
            require(args.size==3)
            val entries=HumanReviewQueue(existing(args[1])).list(args[2].toLong())
            println("{\"reviews\":["+entries.joinToString(",") { "{\"id\":\""+it.metadata.id+"\",\"sha256\":\""+it.metadata.contentSha256+"\",\"status\":\""+it.status.name+"\"}" }+"]}")
        }
        "review-decide" -> {
            require(args.size==6 && args[4] in setOf("APPROVE_FOR_LAB","REJECT"))
            val result=HumanReviewQueue(existing(args[1])).decide(args[2],args[3],args[4]=="APPROVE_FOR_LAB",args[5].toLong())
            println("{\"status\":\""+result.status.name+"\",\"executionAuthorized\":false,\"productionAuthorized\":false}")
        }
        "supervise" -> {
            require(args.size==7)
            val events=existing(args[1]);val casesPath=safePath(args[2]);val history=existing(args[3]);val reviewsPath=existing(args[4])
            val output=safePath(args[5]);require(!Files.exists(output))
            val now=args[6].toLong()
            BoundedEventStore(events).use { store ->
                DiagnosticCaseRepository(casesPath).use { cases ->
                    ExperimentRepository(history).use { experiments ->
                        val monitor=DiagnosticMonitor(store,cases); monitor.setEnabled(true)
                        val observed=monitor.poll(now)
                        val snapshot=SupervisionSnapshot(now,observed.state,observed.evaluation?.observed?:0,
                            observed.evaluation?.unknown?.size?:0,observed.cases,experiments.list(),HumanReviewQueue(reviewsPath).list(now))
                        exportHtml(output,snapshot)
                        println("{\"exported\":true,\"diagnosticState\":\""+snapshot.diagnosticState+"\",\"laboratory\":\"SANDBOX_NOT_QUALIFIED\"}")
                    }
                }
            }
        }
        "evolution-demo" -> {
            require(args.size==2)
            val dir=safePath(args[1]);require(!Files.exists(dir));Files.createDirectories(dir)
            val now=10000L
            BoundedEventStore(dir.resolve("events.db")).use { events ->
                repeat(3) { events.append(Sanitizer.sanitize(EvolutionEvent(Module.LLM,EventCode.SLOW_OPERATION,Severity.WARNING,mapOf("latencyMs" to 2500))),now-3+it) }
                events.append(Sanitizer.sanitize(EvolutionEvent(Module.CORE,EventCode.QUEUE_FULL,Severity.WARNING,mapOf("queueSize" to 3))),now)
                DiagnosticCaseRepository(dir.resolve("cases.db")).use { cases ->
                    val monitor=DiagnosticMonitor(events,cases); monitor.setEnabled(true);val observed=monitor.poll(now)
                    val c=MeasurementContext(Module.LLM,"a".repeat(64),"b".repeat(64),"c".repeat(64),"d".repeat(40),2,MeasurementOrigin.SYNTHETIC)
                    ExperimentRepository(dir.resolve("history.db")).use { history ->
                        history.record(UUID.randomUUID().toString(),now,PerformanceRun(c,List(20) { 100.0 }),PerformanceRun(c.copy(revision="e".repeat(40)),List(20) { 120.0 }))
                        val reviews=HumanReviewQueue(dir.resolve("reviews.log")).list(now)
                        val evaluation=requireNotNull(observed.evaluation)
                        exportHtml(dir.resolve("supervision.html"),SupervisionSnapshot(now,observed.state,evaluation.observed,
                            evaluation.unknown.size,observed.cases,history.list(),reviews))
                    }
                }
            }
            println("{\"synthetic\":true,\"benchmarkExecuted\":false,\"patchExecuted\":false,\"sandboxQualified\":false}")
        }
        else -> error("USAGE")
    }
}
