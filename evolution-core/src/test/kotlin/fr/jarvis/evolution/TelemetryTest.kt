package fr.jarvis.evolution

import fr.jarvis.evolution.events.*
import fr.jarvis.evolution.probes.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.sql.DriverManager
import java.security.MessageDigest

class TelemetryTest {
    private fun path() = Files.createTempDirectory("evolution-synthetic-").resolve("events.db")
    private fun event(code: EventCode = EventCode.SLOW_OPERATION, module: Module = Module.CORE) =
        EvolutionEvent(module,code,Severity.WARNING,mapOf("latencyMs" to 2500))
    @Test fun collectionIsOffByDefaultAndDoesNotInvokeProbes() {
        BoundedEventStore(path()).use { store ->
            val service=LocalTelemetryService(store); var calls=0
            val scheduler=ProbeScheduler(service,listOf(HealthProbe { calls++; event() }))
            assertEquals(SubmitResult.DISABLED,service.submit(event()))
            scheduler.enabled=true; assertEquals(0,scheduler.poll(0)); assertEquals(0,calls)
            assertTrue(store.query().isEmpty()); assertFalse(service.state.value.enabled)
        }
    }
    @Test fun privatePayloadIsDiscardedBeforeQueueAndSqliteWrite() {
        val path=path()
        BoundedEventStore(path).use { store ->
            val service=LocalTelemetryService(store);service.setEnabled(true)
            val secret="synthetic-user@example.invalid token=SYNTHETIC_NOT_A_SECRET conversation"
            service.submit(EvolutionEvent(Module.LLM,EventCode.CRASH_OBSERVED,Severity.ERROR,
                mapOf("message" to secret,"path" to secret,"token" to secret,"latencyMs" to "1200","usedBytes" to 8192)))
            service.drain()
            val row=store.query().single(); assertNull(row.payload.latencyMs); assertEquals(8192.0,row.payload.usedBytes)
            assertEquals(4,row.payload.discardedFields)
            assertFalse(SnapshotJson.incidents(listOf(row)).contains(secret))
        }
        assertFalse(Files.readAllBytes(path).toString(Charsets.ISO_8859_1).contains("SYNTHETIC_NOT_A_SECRET"))
    }
    @Test fun nonFiniteNegativeAndUnknownMetricsAreNotPersisted() {
        val s=Sanitizer.sanitize(EvolutionEvent(Module.CORE,EventCode.NO_DATA,Severity.INFO,
            mapOf("latencyMs" to Double.NaN,"usedBytes" to -1,"limitBytes" to Double.POSITIVE_INFINITY,"queueSize" to 1_000_001,"latitude" to 42)))
        assertNull(s.payload.latencyMs);assertNull(s.payload.usedBytes);assertNull(s.payload.limitBytes);assertNull(s.payload.queueSize)
        assertEquals(5,s.payload.discardedFields)
    }
    @Test fun saturationIsBoundedAndExplicitAndDisablingDiscardsQueuedOnly() {
        BoundedEventStore(path()).use { store ->
            val service=LocalTelemetryService(store,2); service.setEnabled(true)
            assertEquals(SubmitResult.ACCEPTED,service.submit(event()));assertEquals(SubmitResult.ACCEPTED,service.submit(event()))
            assertEquals(SubmitResult.SATURATED,service.submit(event()));assertEquals(1,service.snapshot().dropped)
            service.drain(1);assertEquals(1,store.query().single().occurrences)
            service.setEnabled(false);assertEquals(0,service.snapshot().queued);assertEquals(2,service.snapshot().dropped)
            assertEquals(1,store.query().size)
        }
    }
    @Test fun duplicateIncidentsAreCountedAndSurviveReopen() {
        val p=path(); BoundedEventStore(p).use { store -> repeat(3) { store.append(Sanitizer.sanitize(event()),100L+it) } }
        BoundedEventStore(p).use { store -> val r=store.query().single();assertEquals(3,r.occurrences);assertEquals(100,r.firstAt);assertEquals(102,r.lastAt) }
    }
    @Test fun retentionAndRowRotationPreserveNewest() {
        BoundedEventStore(path(),StoreLimits(maxRows=2,retentionMs=100)).use { store ->
            store.append(Sanitizer.sanitize(event()),1)
            store.append(Sanitizer.sanitize(event(EventCode.QUEUE_FULL)),2)
            store.append(Sanitizer.sanitize(event(EventCode.CRASH_OBSERVED)),3)
            assertEquals(setOf(EventCode.QUEUE_FULL,EventCode.CRASH_OBSERVED),store.query().map{it.code}.toSet())
            assertEquals(2,store.prune(104));assertTrue(store.query().isEmpty())
        }
    }
    @Test fun moduleAndTimeQueriesAreBounded() {
        BoundedEventStore(path()).use { store ->
            store.append(Sanitizer.sanitize(event()),10);store.append(Sanitizer.sanitize(event(module=Module.LLM)),20)
            assertEquals(Module.LLM,store.query(Module.LLM).single().module)
            assertEquals(1,store.query(since=15).size)
            assertThrows(IllegalArgumentException::class.java) { store.query(limit=257) }
            assertThrows(IllegalArgumentException::class.java) { store.query(since=-1) }
        }
    }
    @Test fun corruptAndFutureDatabasesAreRefusedWithoutReset() {
        val corrupt=path();Files.writeString(corrupt,"SYNTHETIC_CORRUPT_DB")
        val before=Files.readAllBytes(corrupt)
        assertThrows(Exception::class.java) { BoundedEventStore(corrupt) };assertArrayEquals(before,Files.readAllBytes(corrupt))
        val future=path();BoundedEventStore(future).close()
        DriverManager.getConnection("jdbc:sqlite:$future").use { it.createStatement().use { s -> s.execute("PRAGMA user_version=999") } }
        val hash=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(future))
        assertThrows(IllegalArgumentException::class.java) { BoundedEventStore(future) }
        assertArrayEquals(hash,MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(future)))
    }
    @Test fun unrelatedDatabaseIsNotAdoptedOrModified() {
        val p=path();Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:$p").use { it.createStatement().use { s ->s.execute("CREATE TABLE historical_data(value TEXT)") } }
        val before=Files.readAllBytes(p)
        assertThrows(IllegalArgumentException::class.java) { BoundedEventStore(p) }
        assertArrayEquals(before,Files.readAllBytes(p))
    }
    @Test fun failedCommitRemainsUnknownAndIsNotAutomaticallyRetried() {
        var writes=0
        val failing=object: EventStore {
            override fun append(event:SanitizedEvent,now:Long) { writes++;error("SYNTHETIC_PRIVATE_EXCEPTION") }
            override fun query(module:Module?,since:Long,limit:Int)=emptyList<StoredIncident>()
            override fun prune(now:Long)=0
            override fun close() {}
        }
        val service=LocalTelemetryService(failing);service.setEnabled(true);service.submit(event())
        assertEquals(0,service.drain());assertEquals(CollectorStatus.STORAGE_UNKNOWN,service.snapshot().status)
        assertEquals(1,service.snapshot().uncertain);assertEquals(0,service.drain());assertEquals(1,writes)
        assertFalse(service.snapshot().toString().contains("PRIVATE"))
    }
    @Test fun uncertainWriteBlocksRemainingQueueEvenAfterConsentToggle() {
        var writes=0
        val failing=object: EventStore {
            override fun append(event:SanitizedEvent,now:Long) { writes++;error("synthetic uncertain ACK") }
            override fun query(module:Module?,since:Long,limit:Int)=emptyList<StoredIncident>()
            override fun prune(now:Long)=0
            override fun close() {}
        }
        val service=LocalTelemetryService(failing);service.setEnabled(true)
        repeat(2) { service.submit(event()) };service.drain()
        assertEquals(1,writes);assertEquals(1,service.snapshot().queued)
        service.drain();assertEquals(1,writes)
        service.setEnabled(false);service.setEnabled(true);service.submit(event());service.drain()
        assertEquals(1,writes);assertEquals(CollectorStatus.STORAGE_UNKNOWN,service.snapshot().status)
    }
    @Test fun disableSerializesWithInFlightWriteAndDropsRemainingQueue() {
        val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
        var writes=0
        val store=object: EventStore {
            override fun append(event:SanitizedEvent,now:Long) { entered.countDown();check(release.await(5,java.util.concurrent.TimeUnit.SECONDS));writes++ }
            override fun query(module:Module?,since:Long,limit:Int)=emptyList<StoredIncident>()
            override fun prune(now:Long)=0
            override fun close() {}
        }
        val service=LocalTelemetryService(store);service.setEnabled(true);repeat(2) {service.submit(event())}
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val drain=pool.submit<Int> { service.drain(1) };assertTrue(entered.await(2,java.util.concurrent.TimeUnit.SECONDS))
            val disabled=pool.submit {service.setEnabled(false)}
            release.countDown();assertEquals(1,drain.get(5,java.util.concurrent.TimeUnit.SECONDS).toInt());disabled.get(5,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(0,service.drain());assertEquals(1,writes);assertEquals(0,service.snapshot().queued)
            assertFalse(service.snapshot().enabled)
        } finally {release.countDown();pool.shutdownNow()}
    }
    @Test fun schedulerHasNoBackgroundThreadAndHonorsExplicitInterval() {
        BoundedEventStore(path()).use { store ->
            var calls=0; val service=LocalTelemetryService(store);service.setEnabled(true)
            val scheduler=ProbeScheduler(service,listOf(HealthProbe { calls++;event() }),1000)
            assertEquals(0,scheduler.poll(0));scheduler.enabled=true
            assertEquals(1,scheduler.poll(0));assertEquals(0,scheduler.poll(999));assertEquals(1,scheduler.poll(1000))
            service.drain();assertEquals(2,calls);assertEquals(2,store.query().single().occurrences)
        }
    }
    @Test fun probesOnlyExposeInjectedTechnicalNumbersAndDiscardThrowableText() {
        val memory=CurrentProcessMemoryProbe { 8192L to 16384L }.sample()
        assertEquals(8192L,memory.payload["usedBytes"])
        assertEquals(3,QueueHealthProbe { 3 }.sample().payload["queueSize"])
        assertEquals(12.0,TimedHealthProbe { 12.0 }.sample().payload["latencyMs"])
        assertTrue(CrashObservation.event(Module.VOICE,Exception("PRIVATE_AUDIO_PATH")).payload.isEmpty())
    }
    @Test fun fileLimitAndConfigurationCannotBecomeUnbounded() {
        assertThrows(IllegalArgumentException::class.java) { StoreLimits(maxRows=0) }
        assertThrows(IllegalArgumentException::class.java) { StoreLimits(maxDatabaseBytes=100) }
        val p=path();BoundedEventStore(p,StoreLimits(maxDatabaseBytes=262144)).use { store ->
            repeat(100) { store.append(Sanitizer.sanitize(event()),it.toLong()) }
        }
        assertTrue(Files.size(p)<=262144)
    }
    @Test fun schemaVersionAndOnlySanitizedColumnsArePersisted() {
        val p=path();BoundedEventStore(p).close()
        DriverManager.getConnection("jdbc:sqlite:$p").use { db ->
            db.createStatement().use { s ->
                s.executeQuery("PRAGMA user_version").use { it.next();assertEquals(1,it.getInt(1)) }
                s.executeQuery("PRAGMA table_info(incidents)").use { rows ->
                    val names=buildList {while(rows.next())add(rows.getString("name"))}
                    assertFalse(names.any { it in setOf("message","payload","token","path","principal") })
                }
            }
        }
    }
}
