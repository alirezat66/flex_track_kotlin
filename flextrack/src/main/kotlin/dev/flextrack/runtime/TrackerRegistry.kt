package dev.flextrack.runtime

import dev.flextrack.error.TrackerException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

/** Thread-safe registry that owns tracker lifecycle. */
public class TrackerRegistry(initialTrackers: Iterable<Tracker> = emptyList()) {
    private val mutex: Mutex = Mutex()
    private val trackers: LinkedHashMap<String, Tracker> = linkedMapOf()
    private var started: Boolean = false

    init {
        initialTrackers.forEach { tracker ->
            require(tracker.id.isNotBlank()) { "tracker id cannot be blank" }
            require(tracker.id !in trackers) { "tracker '${tracker.id}' is already registered" }
            trackers[tracker.id] = tracker
        }
    }

    public suspend fun register(tracker: Tracker) {
        if (tracker.id.isBlank()) throw TrackerException("tracker id cannot be blank", "INVALID_TRACKER_ID")
        mutex.withLock {
            if (tracker.id in trackers) throw TrackerException(
                "tracker '${tracker.id}' is already registered", "DUPLICATE_TRACKER", trackerId = tracker.id,
            )
            if (started) tracker.start()
            trackers[tracker.id] = tracker
        }
    }

    public suspend fun unregister(id: String): Tracker? = mutex.withLock {
        trackers.remove(id)?.also { if (started) it.shutdown() }
    }

    public suspend fun start() {
        mutex.withLock {
            if (started) return
            val initialized = mutableListOf<Tracker>()
            try {
                for (tracker in trackers.values) {
                    tracker.start()
                    initialized += tracker
                }
                started = true
            } catch (failure: Throwable) {
                initialized.asReversed().forEach { runCatching { it.shutdown() } }
                if (failure is CancellationException) throw failure
                throw failure
            }
        }
    }

    public suspend fun shutdown() {
        mutex.withLock {
            if (!started) return
            trackers.values.toList().asReversed().forEach { tracker ->
                try {
                    tracker.shutdown()
                } catch (failure: Throwable) {
                    if (failure is CancellationException) throw failure
                }
            }
            started = false
        }
    }

    internal suspend fun snapshot(): Map<String, Tracker> = mutex.withLock { trackers.toMap() }

    /** Returns diagnostics without exposing the registry's mutable storage. */
    public suspend fun diagnostics(): Map<String, TrackerDiagnostics> =
        snapshot().mapValues { (_, tracker) -> tracker.diagnostics() }
}
