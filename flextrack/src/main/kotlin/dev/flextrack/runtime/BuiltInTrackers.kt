package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A production-safe sink that intentionally discards events while exposing lifecycle diagnostics. */
public class NoOpTracker(
    override val id: String = "noop",
    override val displayName: String = "No-op tracker",
) : Tracker {
    private val mutex: Mutex = Mutex()
    private var state: TrackerLifecycleState = TrackerLifecycleState.CREATED
    private var starts: Int = 0
    private var shutdowns: Int = 0
    private var tracked: Long = 0

    init { require(id.isNotBlank()) { "tracker id cannot be blank" } }

    override suspend fun start() { mutex.withLock { starts++; state = TrackerLifecycleState.STARTED } }
    override suspend fun track(event: FlexEvent) { mutex.withLock { tracked++ } }
    override suspend fun shutdown() { mutex.withLock { shutdowns++; state = TrackerLifecycleState.SHUTDOWN } }
    override suspend fun diagnostics(): TrackerDiagnostics = mutex.withLock {
        TrackerDiagnostics(id, displayName, capabilities, state, tracked, starts, shutdowns)
    }
}

/** In-memory tracker for deterministic package tests, examples, and local integration debugging. */
public class RecordingTracker(
    override val id: String = "recording",
    override val displayName: String = "Recording tracker",
    override val capabilities: TrackerCapabilities = TrackerCapabilities(),
) : Tracker {
    private val mutex: Mutex = Mutex()
    private val captured: MutableList<FlexEvent> = mutableListOf()
    private var state: TrackerLifecycleState = TrackerLifecycleState.CREATED
    private var starts: Int = 0
    private var shutdowns: Int = 0

    init { require(id.isNotBlank()) { "tracker id cannot be blank" } }

    override suspend fun start() { mutex.withLock { starts++; state = TrackerLifecycleState.STARTED } }
    override suspend fun track(event: FlexEvent) { mutex.withLock { captured += event } }
    override suspend fun shutdown() { mutex.withLock { shutdowns++; state = TrackerLifecycleState.SHUTDOWN } }

    public suspend fun events(): List<FlexEvent> = mutex.withLock { captured.toList() }
    public suspend fun reset() { mutex.withLock { captured.clear() } }
    override suspend fun diagnostics(): TrackerDiagnostics = mutex.withLock {
        TrackerDiagnostics(id, displayName, capabilities, state, captured.size.toLong(), starts, shutdowns)
    }
}

/** Flutter-compatible discovery name while retaining the more descriptive native type. */
public typealias MockTracker = RecordingTracker
