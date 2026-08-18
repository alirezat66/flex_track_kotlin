package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
import dev.flextrack.logging.FlexTrackLogger
import dev.flextrack.routing.ConsentState
import dev.flextrack.routing.RoutingConfiguration
import dev.flextrack.routing.RoutingEngine
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlexTrackClientTest {
    @Test
    fun `delivers to matching trackers and isolates failures`() = runTest {
        val successful = RecordingTracker("analytics")
        val failing = RecordingTracker("archive", fail = true)
        val queue = InMemoryEventQueue()
        val client = client(queue = queue)
        client.register(successful)
        client.register(failing)

        val result = client.track(TestEvent())

        assertEquals(listOf("analytics"), result.successfulTrackerIds)
        assertEquals(listOf("archive"), result.failures.map(TrackerFailure::trackerId))
        assertEquals(1, successful.events.size)
        assertEquals(1, queue.size())
    }

    @Test
    fun `queues all destinations while offline`() = runTest {
        val tracker = RecordingTracker("analytics")
        val queue = InMemoryEventQueue()
        val client = client(queue = queue, online = { false })
        client.register(tracker)

        val result = client.track(TestEvent())

        assertTrue(result.wasQueued)
        assertTrue(tracker.events.isEmpty())
        assertEquals(1, queue.size())
    }

    @Test
    fun `flush retries only destinations that still fail`() = runTest {
        val tracker = RecordingTracker("analytics", fail = true)
        val queue = InMemoryEventQueue()
        val client = client(queue = queue)
        client.register(tracker)
        client.track(TestEvent())
        tracker.fail = false

        val result = client.flush()

        assertEquals(1, result.deliveredEvents)
        assertEquals(0, result.remainingEvents)
    }

    @Test
    fun `registry starts late registrations and shuts down once`() = runTest {
        val registry = TrackerRegistry()
        val first = RecordingTracker("first")
        val second = RecordingTracker("second")
        registry.register(first)
        registry.start()
        registry.start()
        registry.register(second)
        registry.shutdown()

        assertEquals(1, first.starts)
        assertEquals(1, second.starts)
        assertEquals(1, first.shutdowns)
        assertEquals(1, second.shutdowns)
    }

    @Test
    fun `partial start failure rolls back and retry starts every tracker`() = runTest {
        val registry = TrackerRegistry()
        val healthy = RecordingTracker("healthy")
        val flaky = RecordingTracker("flaky", startFailures = 1)
        registry.register(healthy)
        registry.register(flaky)

        assertTrue(runCatching { registry.start() }.exceptionOrNull() is IllegalStateException)
        assertEquals(1, healthy.starts)
        assertEquals(1, healthy.shutdowns)

        registry.start()

        assertEquals(2, healthy.starts)
        assertEquals(2, flaky.starts)
    }

    @Test
    fun `shutdown is idempotent after successful start`() = runTest {
        val registry = TrackerRegistry()
        val tracker = RecordingTracker("analytics")
        registry.register(tracker)
        registry.start()

        registry.shutdown()
        registry.shutdown()

        assertEquals(1, tracker.shutdowns)
    }

    @Test
    fun `consent denial prevents delivery and queueing`() = runTest {
        val tracker = RecordingTracker("analytics")
        val queue = InMemoryEventQueue()
        val client = client(queue = queue, consent = { ConsentState() })
        client.register(tracker)

        val result = client.track(TestEvent(requiresConsentValue = true))

        assertFalse(result.wasQueued)
        assertTrue(result.routing.targetTrackers.isEmpty())
        assertTrue(tracker.events.isEmpty())
    }

    @Test
    fun `structured logs show property keys but never property values`() = runTest {
        val messages = mutableListOf<String>()
        val logger = FlexTrackLogger(messages::add)
        val tracker = RecordingTracker("analytics", fail = true)
        val queue = InMemoryEventQueue()
        val client = client(queue = queue, logger = logger)
        client.register(tracker)

        client.track(TestEvent())
        tracker.fail = false
        client.flush()

        assertTrue(
            messages.any { "ROUTE purchase targets=[analytics] properties=1 keys=[plan]" in it },
            messages.toString(),
        )
        assertTrue(messages.any { "FAILED purchase → analytics" in it })
        assertTrue(messages.any { "QUEUED purchase" in it })
        assertTrue(messages.any { "FLUSH attempted=1 delivered=1 remaining=0" in it })
        assertTrue(messages.none { "secret-value" in it })
    }

    @Test
    fun `route logs explain consent rejection`() = runTest {
        val messages = mutableListOf<String>()
        val client = client(
            queue = InMemoryEventQueue(),
            consent = { ConsentState() },
            logger = FlexTrackLogger(messages::add),
        )
        client.register(RecordingTracker("analytics"))

        client.track(TestEvent(requiresConsentValue = true))

        assertTrue(messages.any { "ROUTE purchase targets=[] properties=1 keys=[plan]" in it })
        assertTrue(messages.any { "SKIPPED purchase" in it && "Consent requirements not met" in it })
    }

    @Test
    fun `property values require explicit verbose logger opt in`() = runTest {
        val basicMessages = mutableListOf<String>()
        val verboseMessages = mutableListOf<String>()
        val basic = client(
            queue = InMemoryEventQueue(),
            logger = FlexTrackLogger(basicMessages::add),
        )
        val verboseLogger = object : FlexTrackLogger {
            override val includesPropertyValues: Boolean = true
            override fun log(message: String) { verboseMessages += message }
        }
        val verbose = client(queue = InMemoryEventQueue(), logger = verboseLogger)
        basic.register(RecordingTracker("analytics"))
        verbose.register(RecordingTracker("analytics"))

        basic.track(TestEvent())
        verbose.track(TestEvent())

        assertTrue(basicMessages.none { "secret-value" in it })
        assertTrue(verboseMessages.any { "PAYLOAD purchase" in it && "secret-value" in it })
    }

    @Test
    fun `offline flush logs skip and never delivers`() = runTest {
        val messages = mutableListOf<String>()
        val queue = InMemoryEventQueue()
        queue.enqueue(QueuedEvent("queued", TestEvent(), listOf("analytics")))
        val client = client(
            queue = queue,
            online = { false },
            logger = FlexTrackLogger(messages::add),
        )
        val tracker = RecordingTracker("analytics")
        client.register(tracker)

        val result = client.flush()

        assertEquals(0, result.attemptedEvents)
        assertTrue(tracker.events.isEmpty())
        assertTrue(messages.any { it == "⚪ OFFLINE flush skipped queue=1" })
    }

    @Test
    fun `logger failures never interrupt delivery`() = runTest {
        val tracker = RecordingTracker("analytics")
        val client = client(
            queue = InMemoryEventQueue(),
            logger = FlexTrackLogger { error("logger failed") },
        )
        client.register(tracker)

        val result = client.track(TestEvent())

        assertEquals(listOf("analytics"), result.successfulTrackerIds)
    }

    private fun client(
        queue: EventQueue,
        online: () -> Boolean = { true },
        consent: () -> ConsentState = { ConsentState(general = true) },
        logger: FlexTrackLogger = FlexTrackLogger { },
    ): FlexTrackClient = FlexTrackClient(
        routingEngine = RoutingEngine(
            RoutingConfiguration(
                rules = listOf(
                    RoutingRule(
                        targetGroup = TrackerGroup("all", listOf("analytics", "archive")),
                    ),
                ),
            ),
        ),
        queue = queue,
        onlineProvider = online,
        consentProvider = consent,
        logger = logger,
    )

    private class TestEvent(
        private val requiresConsentValue: Boolean = false,
    ) : FlexEvent() {
        override val name: String = "purchase"
        override val properties: Map<String, Any?> = mapOf("plan" to "secret-value")
        override val requiresConsent: Boolean = requiresConsentValue
    }

    private class RecordingTracker(
        override val id: String,
        var fail: Boolean = false,
        var startFailures: Int = 0,
    ) : Tracker {
        val events = mutableListOf<FlexEvent>()
        var starts = 0
        var shutdowns = 0

        override suspend fun start() {
            starts++
            if (startFailures > 0) {
                startFailures--
                error("start failed")
            }
        }
        override suspend fun track(event: FlexEvent) {
            if (fail) error("delivery failed")
            events += event
        }
        override suspend fun shutdown() { shutdowns++ }
    }
}
