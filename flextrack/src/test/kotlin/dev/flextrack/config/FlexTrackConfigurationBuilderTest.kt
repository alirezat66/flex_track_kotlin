package dev.flextrack.config

import dev.flextrack.context.ConsentManager
import dev.flextrack.context.TrackingContext
import dev.flextrack.event.EnrichedEvent
import dev.flextrack.event.FlexEvent
import dev.flextrack.logging.FlexTrackLogger
import dev.flextrack.routing.ConsentState
import dev.flextrack.runtime.Tracker
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlexTrackConfigurationBuilderTest {
    @Test
    fun `builder wires trackers routing transformer and lifecycle`() = runTest {
        val tracker = RecordingTracker("analytics")
        val client = FlexTrackConfigurationBuilder().apply {
            tracker(tracker)
            inMemoryQueue()
            transformer { EnrichedEvent(it, mapOf("surface" to "builder")) }
            routing { routeNamed("purchase") { toTracker("analytics"); skipConsent() } }
        }.build()

        val result = client.track(Event())

        assertEquals(1, tracker.starts)
        assertEquals(listOf("analytics"), result.successfulTrackerIds)
        assertEquals("builder", tracker.events.single().properties?.get("surface"))
    }

    @Test
    fun `auto start can be disabled`() = runTest {
        val tracker = RecordingTracker("analytics")
        FlexTrackConfigurationBuilder().apply {
            tracker(tracker)
            inMemoryQueue()
            autoStart(false)
        }.build()

        assertEquals(0, tracker.starts)
    }

    @Test
    fun `consent and network providers control delivery and durable retry`() = runTest {
        var consent = ConsentState()
        var online = false
        val tracker = RecordingTracker("analytics")
        val client = FlexTrackConfigurationBuilder().apply {
            tracker(tracker)
            inMemoryQueue()
            consent { consent }
            network { online }
            routing { routeDefault { toTracker("analytics") } }
        }.build()

        val blocked = client.track(Event(requiresConsentValue = true))
        consent = ConsentState(general = true)
        val queued = client.track(Event(requiresConsentValue = true))
        online = true
        val flushed = client.flush()

        assertTrue(blocked.routing.targetTrackers.isEmpty())
        assertEquals(listOf("analytics"), queued.queuedTrackerIds)
        assertEquals(1, flushed.deliveredEvents)
    }

    @Test
    fun `custom logger is passed to the runtime`() = runTest {
        val messages = mutableListOf<String>()
        val client = FlexTrackConfigurationBuilder().apply {
            tracker(RecordingTracker("analytics"))
            inMemoryQueue()
            logger(FlexTrackLogger(messages::add))
            routing { routeDefault { toTracker("analytics"); skipConsent() } }
        }.build()

        client.track(Event())

        assertTrue(messages.any { "ROUTE purchase" in it })
        assertTrue(messages.any { "DELIVER purchase" in it })
    }

    @Test
    fun `tracking context wires consent and dynamic event enrichment`() = runTest {
        val consent = ConsentManager().apply { setConsents(general = true, version = "1") }
        var context = TrackingContext.create(userId = "first", sessionId = "session", consentManager = consent)
        val tracker = RecordingTracker("analytics")
        val client = FlexTrackConfigurationBuilder().apply {
            tracker(tracker)
            inMemoryQueue()
            trackingContext { context }
            routing { routeDefault { toTracker("analytics") } }
        }.build()

        client.track(Event(requiresConsentValue = true))
        context = context.withUserId("second")
        client.track(Event(requiresConsentValue = true))

        assertEquals(listOf("first", "second"), tracker.events.map { it.properties?.get("user_id") })
        assertEquals(listOf("session", "session"), tracker.events.map { it.properties?.get("session_id") })
    }

    @Test
    fun `builder requires at least one tracker`() {
        assertThrows(IllegalArgumentException::class.java) {
            runTest { FlexTrackConfigurationBuilder().apply { inMemoryQueue() }.build() }
        }
    }

    @Test
    fun `Android-only features explain missing Context`() {
        assertThrows(IllegalArgumentException::class.java) {
            FlexTrackConfigurationBuilder().persistentQueue()
        }
        assertThrows(IllegalArgumentException::class.java) {
            FlexTrackConfigurationBuilder().logging()
        }
    }

    private class Event(
        private val requiresConsentValue: Boolean = false,
    ) : FlexEvent() {
        override val name: String = "purchase"
        override val properties: Map<String, Any?> = emptyMap()
        override val requiresConsent: Boolean = requiresConsentValue
    }

    private class RecordingTracker(override val id: String) : Tracker {
        var starts: Int = 0
        val events: MutableList<FlexEvent> = mutableListOf()
        override suspend fun start() { starts++ }
        override suspend fun track(event: FlexEvent) { events += event }
    }
}
