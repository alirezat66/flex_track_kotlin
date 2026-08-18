package dev.flextrack.sample.analytics

import android.util.Log
import dev.flextrack.sample.BuildConfig
import dev.flextrack.event.FlexEvent
import dev.flextrack.runtime.Tracker
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

class DeliveryLabEvent(sequence: Int) : FlexEvent() {
    override val name: String = "sample_delivery_lab"
    override val properties: Map<String, Any?> = mapOf("sequence" to sequence)
    override val requiresConsent: Boolean = false
}

class SampleEvent(
    override val name: String,
    override val properties: Map<String, Any?> = emptyMap(),
    override val requiresConsent: Boolean = true,
) : FlexEvent()

@Singleton
class FailureController @Inject constructor() {
    val retryDestinationFails = AtomicBoolean(false)
}

abstract class CountingTracker : Tracker {
    var attempts: Int = 0
        private set
    var deliveries: Int = 0
        private set

    final override suspend fun track(event: FlexEvent) {
        attempts++
        deliver(event)
        deliveries++
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.d(
                    "FlexTrackSample",
                    "🧪 RECEIVED ${event.name} tracker=$id " +
                        "eventId=${event.eventId} properties=${event.properties.orEmpty()}",
                )
            }
        }
    }

    protected open suspend fun deliver(event: FlexEvent) = Unit
}

@Singleton
class ReliableDemoTracker @Inject constructor() : CountingTracker() {
    override val id: String = "sample_reliable"
}

@Singleton
class RetryDemoTracker @Inject constructor(
    private val failureController: FailureController,
) : CountingTracker() {
    override val id: String = "sample_retry"

    override suspend fun deliver(event: FlexEvent) {
        check(!failureController.retryDestinationFails.get()) {
            "Intentional sample tracker failure"
        }
    }
}
