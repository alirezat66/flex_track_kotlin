package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

public data class QueuedEvent(
    val id: String,
    val event: FlexEvent,
    val trackerIds: List<String>,
    val attempts: Int = 0,
    val queuedAt: Instant = Instant.now(),
)

/** Storage boundary for events waiting for another delivery attempt. */
public interface EventQueue {
    public suspend fun enqueue(item: QueuedEvent)
    public suspend fun read(limit: Int): List<QueuedEvent>
    public suspend fun replace(item: QueuedEvent)
    public suspend fun remove(id: String)
    public suspend fun size(): Int
}

/** Process-local queue useful for tests and apps that do not require persistence. */
public class InMemoryEventQueue : EventQueue {
    private val mutex: Mutex = Mutex()
    private val items: LinkedHashMap<String, QueuedEvent> = linkedMapOf()

    override suspend fun enqueue(item: QueuedEvent): Unit = mutex.withLock {
        items.putIfAbsent(item.id, item)
        Unit
    }

    override suspend fun read(limit: Int): List<QueuedEvent> = mutex.withLock {
        require(limit > 0) { "limit must be positive" }
        items.values.take(limit)
    }

    override suspend fun replace(item: QueuedEvent): Unit = mutex.withLock {
        if (item.id in items) items[item.id] = item
    }

    override suspend fun remove(id: String): Unit = mutex.withLock { items.remove(id); Unit }

    override suspend fun size(): Int = mutex.withLock { items.size }
}
