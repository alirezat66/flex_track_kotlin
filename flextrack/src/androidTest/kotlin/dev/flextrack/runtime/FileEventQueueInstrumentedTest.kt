package dev.flextrack.runtime

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.flextrack.event.FlexEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FileEventQueueInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val files = mutableListOf<File>()

    @After
    fun cleanUp() {
        files.forEach { file ->
            file.delete()
            File("${file.path}.bak").delete()
            File("${file.path}.new").delete()
        }
    }

    @Test
    fun survivesRecreationAndPreservesIdentityAndMetadata() = runTest {
        val name = queueName()
        val original = TestEvent("stable-id")
        FileEventQueue(context, name).enqueue(
            QueuedEvent(original.eventId, original, listOf("a", "b")),
        )

        val restored = FileEventQueue(context, name).read(10).single()

        assertEquals(original.eventId, restored.id)
        assertEquals(original.timestamp, restored.event.timestamp)
        assertEquals(original.name, restored.event.name)
        assertEquals(original.properties, restored.event.properties)
        assertEquals(listOf("a", "b"), restored.trackerIds)
    }

    @Test
    fun malformedJsonFailsWithoutDeletingPersistedBytes() = runTest {
        val name = queueName()
        val file = trackedFile(name).apply { writeText("{broken") }

        expectFailure<Throwable> { FileEventQueue(context, name).read(10) }
        assertEquals("{broken", file.readText())
    }

    @Test
    fun invalidPersistedShapeFailsVisibly() = runTest {
        val name = queueName()
        trackedFile(name).writeText("{}")

        expectFailure<Throwable> { FileEventQueue(context, name).size() }
    }

    @Test
    fun concurrentEnqueuesAreSerializedWithoutLoss() = runTest {
        val queue = FileEventQueue(context, queueName())

        (0 until 50).map { index ->
            async {
                val id = "event-$index"
                queue.enqueue(QueuedEvent(id, TestEvent(id), listOf("analytics")))
            }
        }.awaitAll()

        assertEquals(50, queue.size())
        assertEquals((0 until 50).map { "event-$it" }, queue.read(50).map { it.id })
    }

    @Test
    fun duplicateIdIsIdempotentAndReplacePreservesPositionAcrossRecreation() = runTest {
        val name = queueName()
        val queue = FileEventQueue(context, name)
        queue.enqueue(QueuedEvent("one", TestEvent("one"), listOf("a")))
        queue.enqueue(QueuedEvent("two", TestEvent("two"), listOf("a")))
        queue.enqueue(QueuedEvent("one", TestEvent("one"), listOf("b")))
        queue.replace(queue.read(10).first().copy(attempts = 1))

        val restored = FileEventQueue(context, name).read(10)

        assertEquals(listOf("one", "two"), restored.map { it.id })
        assertEquals(listOf(1, 0), restored.map { it.attempts })
        assertEquals(listOf("a"), restored.first().trackerIds)
    }

    @Test
    fun nonPositiveReadDoesNotMutateStorage() = runTest {
        val name = queueName()
        val queue = FileEventQueue(context, name)
        queue.enqueue(QueuedEvent("one", TestEvent("one"), listOf("a")))

        expectFailure<IllegalArgumentException> { queue.read(0) }

        assertEquals(1, FileEventQueue(context, name).size())
    }

    private fun queueName(): String = "flextrack-test-${UUID.randomUUID()}.json".also(::trackedFile)

    private fun trackedFile(name: String): File = File(context.filesDir, name).also {
        if (it !in files) files += it
    }

    private suspend inline fun <reified T : Throwable> expectFailure(
        crossinline block: suspend () -> Unit,
    ) {
        val failure = runCatching { block() }.exceptionOrNull()
        assertTrue("Expected ${T::class.java.name}, got $failure", failure is T)
    }

    private class TestEvent(id: String) : FlexEvent(
        id,
        Instant.parse("2026-08-17T12:30:00Z"),
    ) {
        override val name: String = "purchase"
        override val properties: Map<String, Any> = mapOf(
            "plan" to "pro",
            "nested" to mapOf("enabled" to true),
            "items" to listOf(1, "two"),
        )
        override val requiresConsent: Boolean = false
    }
}
