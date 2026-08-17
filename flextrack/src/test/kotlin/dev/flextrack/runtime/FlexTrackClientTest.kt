package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
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

    private fun client(
        queue: EventQueue,
        online: () -> Boolean = { true },
        consent: () -> ConsentState = { ConsentState(general = true) },
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
    )

    private class TestEvent(
        private val requiresConsentValue: Boolean = false,
    ) : FlexEvent() {
        override val name: String = "purchase"
        override val properties: Map<String, Any?> = mapOf("plan" to "pro")
        override val requiresConsent: Boolean = requiresConsentValue
    }

    private class RecordingTracker(
        override val id: String,
        var fail: Boolean = false,
    ) : Tracker {
        val events = mutableListOf<FlexEvent>()
        var starts = 0
        var shutdowns = 0

        override suspend fun start() { starts++ }
        override suspend fun track(event: FlexEvent) {
            if (fail) error("delivery failed")
            events += event
        }
        override suspend fun shutdown() { shutdowns++ }
    }
}
