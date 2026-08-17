package dev.taghizadeh.flextrack.runtime

import dev.taghizadeh.flextrack.event.FlexEvent
import dev.taghizadeh.flextrack.routing.RoutingResult

public data class TrackerFailure(val trackerId: String, val cause: Throwable)

public data class DispatchResult(
    val event: FlexEvent,
    val routing: RoutingResult,
    val successfulTrackerIds: List<String>,
    val failures: List<TrackerFailure>,
    val queuedTrackerIds: List<String>,
) {
    public val wasQueued: Boolean get() = queuedTrackerIds.isNotEmpty()
}

public data class FlushResult(
    val attemptedEvents: Int,
    val deliveredEvents: Int,
    val remainingEvents: Int,
)
