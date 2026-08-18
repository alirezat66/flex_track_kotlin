package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent

/** A destination adapter managed by [FlexTrackClient]. */
public interface Tracker {
    public val id: String

    /** Human-readable name used by diagnostics and developer tooling. */
    public val displayName: String get() = id

    /** Declared delivery characteristics. Override only capabilities the adapter actually supports. */
    public val capabilities: TrackerCapabilities get() = TrackerCapabilities()

    public suspend fun start(): Unit = Unit

    public suspend fun track(event: FlexEvent)

    public suspend fun shutdown(): Unit = Unit

    /** A safe runtime snapshot suitable for logs, inspectors, and health checks. */
    public suspend fun diagnostics(): TrackerDiagnostics = TrackerDiagnostics(
        id = id,
        displayName = displayName,
        capabilities = capabilities,
    )
}

public data class TrackerCapabilities(
    public val supportsBatchTracking: Boolean = false,
    public val supportsRealtimeTracking: Boolean = true,
    public val maxBatchSize: Int = 1,
    public val gdprCompliant: Boolean = false,
) {
    init {
        require(maxBatchSize > 0) { "maxBatchSize must be greater than zero" }
    }
}

public enum class TrackerLifecycleState { CREATED, STARTED, SHUTDOWN }

public data class TrackerDiagnostics(
    public val id: String,
    public val displayName: String,
    public val capabilities: TrackerCapabilities,
    public val lifecycleState: TrackerLifecycleState? = null,
    public val trackedEventCount: Long? = null,
    public val startCount: Int? = null,
    public val shutdownCount: Int? = null,
)
