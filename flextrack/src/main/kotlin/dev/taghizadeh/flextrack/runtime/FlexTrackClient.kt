package dev.taghizadeh.flextrack.runtime

import dev.taghizadeh.flextrack.event.FlexEvent
import dev.taghizadeh.flextrack.event.TransformerPipeline
import dev.taghizadeh.flextrack.routing.ConsentState
import dev.taghizadeh.flextrack.routing.RoutingEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

/** Runtime entry point for transforming, routing, delivering, and retrying events. */
public class FlexTrackClient(
    public val routingEngine: RoutingEngine,
    public val registry: TrackerRegistry = TrackerRegistry(),
    public val queue: EventQueue = InMemoryEventQueue(),
    public val transformers: TransformerPipeline = TransformerPipeline(),
    private val consentProvider: () -> ConsentState = { ConsentState() },
    private val onlineProvider: () -> Boolean = { true },
) {
    public suspend fun start(): Unit = registry.start()

    public suspend fun shutdown(): Unit = registry.shutdown()

    public suspend fun register(tracker: Tracker): Unit = registry.register(tracker)

    public suspend fun unregister(trackerId: String): Tracker? = registry.unregister(trackerId)

    public suspend fun track(event: FlexEvent): DispatchResult {
        val transformed = transformers.transform(event)
        val trackers = registry.snapshot()
        val routing = routingEngine.route(transformed, consentProvider(), trackers.keys)
        val targets = routing.targetTrackers

        if (!onlineProvider() && targets.isNotEmpty()) {
            queue.enqueue(QueuedEvent(transformed.eventId, transformed, targets))
            return DispatchResult(transformed, routing, emptyList(), emptyList(), targets)
        }

        val outcomes = deliver(transformed, targets, trackers)
        val failures = outcomes.mapNotNull { it.failure }
        val failedIds = failures.map(TrackerFailure::trackerId)
        if (failedIds.isNotEmpty()) {
            queue.enqueue(QueuedEvent(transformed.eventId, transformed, failedIds))
        }
        return DispatchResult(
            transformed,
            routing,
            outcomes.filter { it.failure == null }.map(Outcome::trackerId),
            failures,
            failedIds,
        )
    }

    public suspend fun flush(limit: Int = 100): FlushResult {
        require(limit > 0) { "limit must be positive" }
        if (!onlineProvider()) return FlushResult(0, 0, queue.size())

        val items = queue.read(limit)
        val trackers = registry.snapshot()
        var delivered = 0
        for (item in items) {
            val failedIds = deliver(item.event, item.trackerIds, trackers)
                .mapNotNull { it.failure?.trackerId }
            if (failedIds.isEmpty()) {
                queue.remove(item.id)
                delivered++
            } else {
                queue.replace(item.copy(trackerIds = failedIds, attempts = item.attempts + 1))
            }
        }
        return FlushResult(items.size, delivered, queue.size())
    }

    private suspend fun deliver(
        event: FlexEvent,
        trackerIds: List<String>,
        trackers: Map<String, Tracker>,
    ): List<Outcome> = coroutineScope {
        trackerIds.map { id ->
            async {
                val tracker = trackers[id]
                if (tracker == null) {
                    Outcome(id, TrackerFailure(id, IllegalStateException("tracker '$id' is unavailable")))
                } else {
                    try {
                        tracker.track(event)
                        Outcome(id)
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        Outcome(id, TrackerFailure(id, failure))
                    }
                }
            }
        }.awaitAll()
    }

    private data class Outcome(val trackerId: String, val failure: TrackerFailure? = null)
}
