package fr.jarvis.evolution.cli

import fr.jarvis.evolution.events.*
import fr.jarvis.evolution.diagnostic.*
import java.nio.file.Files
import java.nio.file.Path

fun diagnosticsMain(args:Array<String>) {
    when(args[0]) {
        "diagnostic-demo" -> {
            require(args.size==2)
            val directory=Path.of(args[1]);require(!Files.exists(directory));Files.createDirectories(directory)
            val now=1_000_000L
            BoundedEventStore(directory.resolve("events.db")).use{store ->
                repeat(3){store.append(Sanitizer.sanitize(EvolutionEvent(Module.LLM,EventCode.SLOW_OPERATION,Severity.WARNING,mapOf("latencyMs" to 2500))),now-3000+it)}
                store.append(Sanitizer.sanitize(EvolutionEvent(Module.CORE,EventCode.MEMORY_PRESSURE,Severity.WARNING,mapOf("usedBytes" to 900,"limitBytes" to 1000))),now)
                store.append(Sanitizer.sanitize(EvolutionEvent(Module.VOICE,EventCode.RELEASE_UNKNOWN,Severity.ERROR)),now)
                store.append(Sanitizer.sanitize(EvolutionEvent(Module.SERVICES,EventCode.PERMISSION_REFUSED,Severity.WARNING)),now)
                store.append(Sanitizer.sanitize(EvolutionEvent(Module.INTERFACE,EventCode.NO_DATA,Severity.INFO)),now)
                val source=store.query();val evaluation=RuleEngine().evaluate(source,now)
                DiagnosticCaseRepository(directory.resolve("cases.db")).use{repo ->
                    val cases=repo.record(evaluation,source,now);check(cases.size==4)
                    println(DiagnosticReportRenderer.json(cases))
                }
            }
        }
        "diagnose" -> {
            require(args.size in 3..4 && Files.isRegularFile(Path.of(args[1])))
            val now=args.getOrNull(3)?.toLong()?:System.currentTimeMillis()
            BoundedEventStore(Path.of(args[1])).use{store ->
                val rows=store.query();val result=RuleEngine().evaluate(rows,now)
                DiagnosticCaseRepository(Path.of(args[2])).use{repo ->
                    val cases=repo.record(result,rows,now)
                    println("{\"observed\":${result.observed},\"unknownEvidence\":${result.unknown.size},\"insufficientData\":${result.insufficientData},\"storedCases\":${cases.size}}")
                }
            }
        }
        "cases" -> {
            require(args.size in 2..3 && Files.isRegularFile(Path.of(args[1])))
            DiagnosticCaseRepository(Path.of(args[1])).use{println(DiagnosticReportRenderer.json(it.list(args.getOrNull(2)?.let(Module::valueOf))))}
        }
        "case-state" -> {
            require(args.size==4 && Files.isRegularFile(Path.of(args[1])))
            DiagnosticCaseRepository(Path.of(args[1])).use{repo ->
                repo.transition(args[2],CaseStatus.valueOf(args[3]),System.currentTimeMillis())
                println(DiagnosticReportRenderer.json(repo.list()))
            }
        }
        else -> error("USAGE")
    }
}
