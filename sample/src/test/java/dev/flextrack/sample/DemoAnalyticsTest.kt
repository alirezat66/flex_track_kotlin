package dev.flextrack.sample

import dev.flextrack.sample.analytics.DeliveryLabEvent
import dev.flextrack.sample.analytics.FailureController
import dev.flextrack.sample.analytics.ReliableDemoTracker
import dev.flextrack.sample.analytics.RetryDemoTracker
import dev.flextrack.sample.analytics.SampleEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DemoAnalyticsTest {
    @Test
    fun reliableTrackerCountsDelivery() = runTest {
        val tracker = ReliableDemoTracker()

        tracker.track(DeliveryLabEvent(1))

        assertEquals(1, tracker.attempts)
        assertEquals(1, tracker.deliveries)
    }

    @Test
    fun reliableTrackerReceivesRegularSampleEvents() = runTest {
        val tracker = ReliableDemoTracker()

        tracker.track(SampleEvent("add_to_cart", mapOf("product_id" to "shoe-1")))

        assertEquals(1, tracker.attempts)
        assertEquals(1, tracker.deliveries)
    }

    @Test
    fun retryTrackerCountsAttemptButNotDeliveryWhenFailing() = runTest {
        val failure = FailureController().apply { retryDestinationFails.set(true) }
        val tracker = RetryDemoTracker(failure)

        try {
            tracker.track(DeliveryLabEvent(1))
            fail("Expected intentional tracker failure")
        } catch (_: IllegalStateException) {
            // Expected.
        }

        assertEquals(1, tracker.attempts)
        assertEquals(0, tracker.deliveries)
    }
}
