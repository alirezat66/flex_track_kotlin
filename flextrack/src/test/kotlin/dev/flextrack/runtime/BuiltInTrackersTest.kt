package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Test

class BuiltInTrackersTest {
    @Test fun `recording tracker captures ordered events and returns snapshots`() = runTest {
        val tracker = RecordingTracker()
        val first = Event("first")
        val second = Event("second")
        tracker.track(first); tracker.track(second)
        val events = tracker.events()
        assertEquals(listOf(first, second), events)
        assertNotSame(events, tracker.events())
    }

    @Test fun `recording tracker reset clears captured events`() = runTest {
        val tracker = MockTracker()
        tracker.track(Event("first")); tracker.reset()
        assertEquals(emptyList<FlexEvent>(), tracker.events())
    }

    @Test fun `recording diagnostics expose lifecycle counts and capabilities`() = runTest {
        val capabilities = TrackerCapabilities(true, true, 50, true)
        val tracker = RecordingTracker("test", "Test tracker", capabilities)
        tracker.start(); tracker.track(Event("event")); tracker.shutdown()
        val value = tracker.diagnostics()
        assertEquals(TrackerLifecycleState.SHUTDOWN, value.lifecycleState)
        assertEquals(1, value.trackedEventCount)
        assertEquals(1, value.startCount)
        assertEquals(1, value.shutdownCount)
        assertEquals(capabilities, value.capabilities)
    }

    @Test fun `no-op tracker discards payloads but reports delivery count`() = runTest {
        val tracker = NoOpTracker()
        tracker.start(); tracker.track(Event("secret")); tracker.shutdown()
        val value = tracker.diagnostics()
        assertEquals(1, value.trackedEventCount)
        assertEquals(TrackerLifecycleState.SHUTDOWN, value.lifecycleState)
    }

    @Test fun `registry aggregates diagnostics by stable tracker id`() = runTest {
        val first = RecordingTracker("first")
        val second = NoOpTracker("second")
        val registry = TrackerRegistry(listOf(first, second))
        first.track(Event("event"))
        val values = registry.diagnostics()
        assertEquals(setOf("first", "second"), values.keys)
        assertEquals(1, values.getValue("first").trackedEventCount)
    }

    @Test fun `default tracker diagnostics preserve compatibility`() = runTest {
        val tracker = object : Tracker {
            override val id = "legacy"
            override suspend fun track(event: FlexEvent) = Unit
        }
        val value = tracker.diagnostics()
        assertEquals("legacy", value.displayName)
        assertEquals(null, value.lifecycleState)
    }

    private class Event(override val name: String) : FlexEvent() {
        override val properties: Map<String, Any?> = emptyMap()
    }
}
