package dev.flextrack.error

import dev.flextrack.event.FlexEvent
import dev.flextrack.runtime.Tracker
import dev.flextrack.runtime.TrackerRegistry
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FlexTrackExceptionTest {
    @Test fun `typed failures retain code cause and category metadata`() {
        val cause = IllegalStateException("SDK failed")
        val tracker = TrackerException("delivery failed", "TRACK", cause, "mixpanel", "purchase")
        val config = ConfigurationException("invalid", "CONFIG", fieldName = "sampleRate")
        val routing = RoutingException("no route", "ROUTE", eventName = "purchase", ruleName = "paid")
        assertEquals("mixpanel", tracker.trackerId)
        assertEquals("sampleRate", config.fieldName)
        assertEquals("paid", routing.ruleName)
        assertTrue(tracker.toString().contains("TrackerException(TRACK): delivery failed"))
        assertTrue(tracker.toString().contains("Caused by:"))
    }

    @Test fun `registry exposes typed invalid and duplicate tracker failures`() = runTest {
        val registry = TrackerRegistry()
        val invalid = runCatching { registry.register(TestTracker("")) }.exceptionOrNull()
        assertTrue(invalid is TrackerException)
        registry.register(TestTracker("same"))
        val duplicate = runCatching { registry.register(TestTracker("same")) }.exceptionOrNull()
            as TrackerException
        assertEquals("DUPLICATE_TRACKER", duplicate.code)
        assertEquals("same", duplicate.trackerId)
    }

    private class TestTracker(override val id: String) : Tracker {
        override suspend fun track(event: FlexEvent) = Unit
    }
}
