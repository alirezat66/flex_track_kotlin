package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant

class InMemoryEventQueueTest {
    @Test
    fun `duplicate event IDs are idempotent and first snapshot wins`() = runTest {
        val queue = InMemoryEventQueue()
        queue.enqueue(item("one", listOf("analytics")))
        queue.enqueue(item("one", listOf("archive")))

        assertEquals(1, queue.size())
        assertEquals(listOf("analytics"), queue.read(10).single().trackerIds)
    }

    @Test
    fun `read is FIFO and respects its limit`() = runTest {
        val queue = InMemoryEventQueue()
        listOf("one", "two", "three").forEach { queue.enqueue(item(it)) }

        assertEquals(listOf("one", "two"), queue.read(2).map(QueuedEvent::id))
        assertEquals(3, queue.size())
    }

    @Test
    fun `replace retains position and updates retry state`() = runTest {
        val queue = InMemoryEventQueue()
        queue.enqueue(item("one", listOf("analytics", "archive")))
        queue.enqueue(item("two"))

        queue.replace(item("one", listOf("archive"), attempts = 1))
        val values = queue.read(10)

        assertEquals(listOf("one", "two"), values.map(QueuedEvent::id))
        assertEquals(listOf("archive"), values.first().trackerIds)
        assertEquals(1, values.first().attempts)
    }

    @Test
    fun `replace and remove of unknown IDs are no-ops`() = runTest {
        val queue = InMemoryEventQueue()
        queue.enqueue(item("one"))
        queue.replace(item("missing"))
        queue.remove("missing")

        assertEquals(listOf("one"), queue.read(10).map(QueuedEvent::id))
    }

    @Test
    fun `read rejects zero and negative limits`() {
        val queue = InMemoryEventQueue()
        assertThrows(IllegalArgumentException::class.java) { runTest { queue.read(0) } }
        assertThrows(IllegalArgumentException::class.java) { runTest { queue.read(-1) } }
    }

    @Test
    fun `queue copies caller-owned target collections`() = runTest {
        val targets = mutableListOf("analytics")
        val queue = InMemoryEventQueue()
        queue.enqueue(item("one", targets))
        targets += "archive"

        assertEquals(listOf("analytics"), queue.read(10).single().trackerIds)
    }

    private fun item(
        id: String,
        trackers: List<String> = listOf("analytics"),
        attempts: Int = 0,
    ): QueuedEvent = QueuedEvent(
        id = id,
        event = QueueEvent(id),
        trackerIds = trackers,
        attempts = attempts,
        queuedAt = Instant.parse("2026-08-17T00:00:00Z"),
    )

    private class QueueEvent(id: String) : FlexEvent(id) {
        override val name: String = "purchase"
        override val properties: Map<String, Any?> = emptyMap()
        override val requiresConsent: Boolean = false
    }
}
