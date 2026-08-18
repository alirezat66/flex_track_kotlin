package dev.flextrack.sample.data

import dev.flextrack.runtime.DispatchResult
import dev.flextrack.runtime.FlexTrackClient
import dev.flextrack.runtime.FlushResult
import dev.flextrack.sample.analytics.DeliveryLabEvent
import dev.flextrack.sample.analytics.FailureController
import dev.flextrack.sample.analytics.ReliableDemoTracker
import dev.flextrack.sample.analytics.RetryDemoTracker
import dev.flextrack.sample.analytics.SampleEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeliveryRepository @Inject constructor(
    private val client: FlexTrackClient,
    private val preferences: DemoPreferences,
    val reliableTracker: ReliableDemoTracker,
    val retryTracker: RetryDemoTracker,
    private val failureController: FailureController,
) {
    private val initializationMutex = Mutex()
    private var initialized = false
    val isOnline: Flow<Boolean> = preferences.isOnline
    val hasConsent: Flow<Boolean> = preferences.hasConsent

    suspend fun initialize(): FlushResult = initializationMutex.withLock {
        if (!initialized) {
            preferences.initialize()
            client.register(reliableTracker)
            client.register(retryTracker)
            client.start()
            initialized = true
        }
        client.flush()
    }

    suspend fun setOnline(value: Boolean) = preferences.setOnline(value)
    suspend fun setConsent(value: Boolean) = preferences.setConsent(value)

    fun setRetryDestinationHealthy(value: Boolean) {
        failureController.retryDestinationFails.set(!value)
    }

    fun retryDestinationHealthy(): Boolean =
        !failureController.retryDestinationFails.get()

    suspend fun track(): DispatchResult = client.track(
        DeliveryLabEvent(reliableTracker.attempts + retryTracker.attempts + 1),
    )

    suspend fun trackSample(
        name: String,
        properties: Map<String, Any?> = emptyMap(),
        requiresConsent: Boolean = true,
    ): DispatchResult {
        // Sample screens may emit before the Delivery screen/view model exists.
        // Initialization is idempotent and guarantees trackers are registered first.
        initialize()
        return client.track(SampleEvent(name, properties, requiresConsent))
    }

    suspend fun flush(): FlushResult = client.flush()
    suspend fun queueSize(): Int = client.queue.size()
    suspend fun onlineNow(): Boolean = isOnline.first()
    suspend fun consentNow(): Boolean = hasConsent.first()
}
