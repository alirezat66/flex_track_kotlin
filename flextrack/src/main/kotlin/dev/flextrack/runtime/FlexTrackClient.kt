package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
import dev.flextrack.event.TransformerPipeline
import dev.flextrack.logging.FlexTrackLogger
import dev.flextrack.logging.NoOpFlexTrackLogger
import dev.flextrack.logging.includesPropertyValues
import dev.flextrack.logging.safeLog
import dev.flextrack.routing.ConsentState
import dev.flextrack.routing.RoutingEngine
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
    private val logger: FlexTrackLogger = NoOpFlexTrackLogger,
) {
    public suspend fun start() {
        registry.start()
        val trackerCount = registry.snapshot().size
        logger.safeLog { "🚀 START trackers=$trackerCount" }
    }

    public suspend fun shutdown() {
        registry.shutdown()
        logger.safeLog { "⏹ SHUTDOWN" }
    }

    public suspend fun register(tracker: Tracker): Unit = registry.register(tracker)

    public suspend fun unregister(trackerId: String): Tracker? = registry.unregister(trackerId)

    public suspend fun track(event: FlexEvent): DispatchResult {
        val transformed = transformers.transform(event)
        val trackers = registry.snapshot()
        val routing = routingEngine.route(transformed, consentProvider(), trackers.keys)
        val targets = routing.targetTrackers
        val propertyKeys = transformed.properties.orEmpty().keys.sorted()
        logger.safeLog {
            "🟣 ROUTE ${transformed.name} targets=${targets.renderIds()} " +
                "properties=${propertyKeys.size} keys=${propertyKeys.renderIds()}"
        }
        if (logger.includesPropertyValues()) {
            logger.safeLog {
                "🔎 PAYLOAD ${transformed.name} eventId=${transformed.eventId} " +
                    "values=${transformed.properties.orEmpty()}"
            }
        }
        if (routing.skippedRules.isNotEmpty() || routing.warnings.isNotEmpty()) {
            val skipped = routing.skippedRules.joinToString(prefix = "[", postfix = "]") {
                "${it.rule.id ?: "unnamed"}: ${it.reason}"
            }
            logger.safeLog {
                "🟡 SKIPPED ${transformed.name} rules=$skipped warnings=${routing.warnings}"
            }
        }

        if (!onlineProvider() && targets.isNotEmpty()) {
            queue.enqueue(QueuedEvent(transformed.eventId, transformed, targets))
            val queueSize = queue.size()
            logger.safeLog {
                "🟠 QUEUED ${transformed.name} → ${targets.renderIds()} queue=$queueSize reason=offline"
            }
            return DispatchResult(transformed, routing, emptyList(), emptyList(), targets)
        }

        val outcomes = deliver(transformed, targets, trackers)
        val failures = outcomes.mapNotNull { it.failure }
        val failedIds = failures.map(TrackerFailure::trackerId)
        if (failedIds.isNotEmpty()) {
            queue.enqueue(QueuedEvent(transformed.eventId, transformed, failedIds))
            val queueSize = queue.size()
            logger.safeLog {
                "🟠 QUEUED ${transformed.name} → ${failedIds.renderIds()} queue=$queueSize reason=delivery_failure"
            }
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
        if (!onlineProvider()) {
            val remaining = queue.size()
            logger.safeLog { "⚪ OFFLINE flush skipped queue=$remaining" }
            return FlushResult(0, 0, remaining)
        }

        val items = queue.read(limit)
        val trackers = registry.snapshot()
        var delivered = 0
        for (item in items) {
            val failedIds = deliver(item.event, item.trackerIds, trackers)
                .mapNotNull { it.failure?.trackerId }
            if (failedIds.isEmpty()) {
                queue.remove(item.id)
                delivered++
                logger.safeLog { "🟢 RETRY ${item.event.name} delivered" }
            } else {
                queue.replace(item.copy(trackerIds = failedIds, attempts = item.attempts + 1))
                logger.safeLog {
                    "🔴 RETRY ${item.event.name} pending=${failedIds.renderIds()} attempt=${item.attempts + 1}"
                }
            }
        }
        val result = FlushResult(items.size, delivered, queue.size())
        logger.safeLog {
            "🔵 FLUSH attempted=${result.attemptedEvents} delivered=${result.deliveredEvents} remaining=${result.remainingEvents}"
        }
        return result
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
                    val startedAt = System.nanoTime()
                    try {
                        tracker.track(event)
                        val millis = (System.nanoTime() - startedAt) / 1_000_000
                        logger.safeLog { "🟢 DELIVER ${event.name} → $id ${millis}ms" }
                        Outcome(id)
                    } catch (failure: Throwable) {
                        if (failure is CancellationException) throw failure
                        logger.safeLog {
                            "🔴 FAILED ${event.name} → $id error=${failure::class.simpleName ?: "Throwable"}"
                        }
                        Outcome(id, TrackerFailure(id, failure))
                    }
                }
            }
        }.awaitAll()
    }

    private data class Outcome(val trackerId: String, val failure: TrackerFailure? = null)
}

private fun List<String>.renderIds(): String = joinToString(prefix = "[", postfix = "]")
