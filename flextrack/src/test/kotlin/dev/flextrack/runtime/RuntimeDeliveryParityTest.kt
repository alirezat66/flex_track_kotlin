package dev.flextrack.runtime

import dev.flextrack.event.EnrichedEvent
import dev.flextrack.event.EventTransformer
import dev.flextrack.event.FlexEvent
import dev.flextrack.event.TransformerPipeline
import dev.flextrack.routing.RoutingConfiguration
import dev.flextrack.routing.RoutingEngine
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class RuntimeDeliveryParityTest {
    @Test
    fun `retry never reruns transformers or redelivers successful targets`() = runTest {
        var transforms = 0
        val transformers = TransformerPipeline().apply {
            add(EventTransformer { event ->
                transforms++
                EnrichedEvent(event, mapOf("transform" to "once"))
            })
        }
        val success = RecordingTracker("success")
        val retry = RecordingTracker("retry", failuresRemaining = 1)
        val client = client(listOf(success, retry), transformers = transformers)

        val first = client.track(TestEvent("event-1"))
        client.flush()

        assertEquals(listOf("retry"), first.queuedTrackerIds)
        assertEquals(1, transforms)
        assertEquals(1, success.events.size)
        assertEquals(2, retry.events.size)
        assertEquals("once", retry.events.last().properties?.get("transform"))
        assertEquals(0, client.queue.size())
    }

    @Test
    fun `concurrent flush calls cannot duplicate a queued delivery`() = runTest {
        val deliveryStarted = CompletableDeferred<Unit>()
        val releaseDelivery = CompletableDeferred<Unit>()
        val tracker = RecordingTracker("analytics") {
            deliveryStarted.complete(Unit)
            releaseDelivery.await()
        }
        val queue = InMemoryEventQueue().apply {
            enqueue(QueuedEvent("event-1", TestEvent("event-1"), listOf("analytics")))
        }
        val client = client(listOf(tracker), queue = queue)

        val first = async { client.flush() }
        deliveryStarted.await()
        val second = async { client.flush() }
        releaseDelivery.complete(Unit)

        assertEquals(1, first.await().deliveredEvents)
        assertEquals(0, second.await().attemptedEvents)
        assertEquals(1, tracker.events.size)
    }

    @Test
    fun `offline flush never invokes a tracker`() = runTest {
        val tracker = RecordingTracker("analytics")
        val queue = InMemoryEventQueue().apply {
            enqueue(QueuedEvent("event-1", TestEvent("event-1"), listOf("analytics")))
        }
        val client = client(listOf(tracker), queue = queue, online = false)

        val result = client.flush()

        assertEquals(0, result.attemptedEvents)
        assertEquals(1, result.remainingEvents)
        assertTrue(tracker.events.isEmpty())
    }

    @Test
    fun `queue snapshots cannot mutate internal state`() = runTest {
        val sourceTargets = mutableListOf("analytics")
        val queue = InMemoryEventQueue()
        queue.enqueue(QueuedEvent("event-1", TestEvent("event-1"), sourceTargets))
        sourceTargets.clear()
        val snapshot = queue.read(10)

        assertEquals(listOf("analytics"), snapshot.single().trackerIds)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot as MutableList).clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.single().trackerIds as MutableList).clear()
        }
        assertEquals(1, queue.size())
        assertEquals(listOf("analytics"), queue.read(10).single().trackerIds)
    }

    private suspend fun client(
        trackers: List<RecordingTracker>,
        queue: EventQueue = InMemoryEventQueue(),
        transformers: TransformerPipeline = TransformerPipeline(),
        online: Boolean = true,
    ): FlexTrackClient {
        val ids = trackers.map(Tracker::id)
        val client = FlexTrackClient(
            routingEngine = RoutingEngine(
                RoutingConfiguration(
                    listOf(
                        RoutingRule(
                            targetGroup = TrackerGroup("all", ids),
                            requireConsent = false,
                        ),
                    ),
                ),
            ),
            queue = queue,
            transformers = transformers,
            onlineProvider = { online },
        )
        trackers.forEach { client.register(it) }
        client.start()
        return client
    }

    private class TestEvent(id: String) : FlexEvent(id, Instant.parse("2026-08-17T00:00:00Z")) {
        override val name: String = "purchase"
        override val properties: Map<String, Any> = mapOf("plan" to "pro")
        override val requiresConsent: Boolean = false
    }

    private class RecordingTracker(
        override val id: String,
        var failuresRemaining: Int = 0,
        private val beforeResult: suspend () -> Unit = {},
    ) : Tracker {
        val events = mutableListOf<FlexEvent>()

        override suspend fun track(event: FlexEvent) {
            events += event
            beforeResult()
            if (failuresRemaining > 0) {
                failuresRemaining--
                error("failure")
            }
        }
    }
}
