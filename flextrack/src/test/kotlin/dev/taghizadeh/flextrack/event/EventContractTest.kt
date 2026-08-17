package dev.taghizadeh.flextrack.event

import dev.taghizadeh.flextrack.routing.EventCategory
import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class EventContractTest {
    @Test
    fun `new events have stable UUID v4 identity and UTC timestamp`() {
        val first = TestEvent()
        val second = TestEvent()

        assertNotEquals(first.eventId, second.eventId)
        assertEquals(4, first.eventId.substring(14, 15).toInt())
        assertSame(first.timestamp, first.timestamp)
        assertEquals("Z", first.timestamp.toString().takeLast(1))
    }

    @Test
    fun `reconstructed event preserves supplied metadata`() {
        val timestamp = Instant.parse("2026-08-17T12:30:00Z")
        val event = TestEvent("fixture-id", timestamp)

        assertEquals("fixture-id", event.eventId)
        assertSame(timestamp, event.timestamp)
        assertThrows(IllegalArgumentException::class.java) { TestEvent("") }
    }

    @Test
    fun `nested enrichment preserves metadata and later properties win`() {
        val original = TestEvent(propertiesValue = mapOf("plan" to "free"))
        val first = EnrichedEvent(original, mapOf("plan" to "pro"))
        val second = EnrichedEvent(first, mapOf("route" to "/pay"))

        assertEquals(original.eventId, second.eventId)
        assertSame(original.timestamp, second.timestamp)
        assertEquals(mapOf("plan" to "pro", "route" to "/pay"), second.properties)
        assertEquals(EventCategory.Business, second.category)
    }

    @Test
    fun `transformers run in order and isolate failures`() {
        val pipeline = TransformerPipeline()
        pipeline.add { EnrichedEvent(it, mapOf("first" to true)) }
        pipeline.add { error("broken") }
        pipeline.add { EnrichedEvent(it, mapOf("last" to true)) }

        val transformed = pipeline.transform(TestEvent())

        assertEquals(mapOf("first" to true, "last" to true), transformed.properties)
    }
}

internal open class TestEvent(
    eventId: String = java.util.UUID.randomUUID().toString(),
    timestamp: Instant = Instant.now(),
    private val eventName: String = "purchase",
    private val propertiesValue: Map<String, Any?>? = null,
    override val requiresConsent: Boolean = true,
    override val containsPII: Boolean = false,
    override val isEssential: Boolean = false,
    override val userId: String? = null,
    override val sessionId: String? = null,
) : FlexEvent(eventId, timestamp) {
    override val name: String = eventName
    override val properties: Map<String, Any?>? = propertiesValue
    override val category: EventCategory = EventCategory.Business
}

internal class ChildTestEvent : TestEvent()
