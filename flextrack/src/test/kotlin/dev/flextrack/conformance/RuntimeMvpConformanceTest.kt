package dev.flextrack.conformance

import dev.flextrack.event.FlexEvent
import dev.flextrack.routing.RoutingConfiguration
import dev.flextrack.routing.RoutingEngine
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import dev.flextrack.runtime.EventQueue
import dev.flextrack.runtime.FlexTrackClient
import dev.flextrack.runtime.InMemoryEventQueue
import dev.flextrack.runtime.QueuedEvent
import dev.flextrack.runtime.Tracker
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.time.Instant
import java.util.stream.Stream

class RuntimeMvpConformanceTest {
    private val contractRoot: Path by lazy {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        workingDirectory.resolve("contract").takeIf { it.toFile().isDirectory }
            ?: workingDirectory.parent.resolve("contract")
    }

    private val document: JSONObject by lazy {
        JSONObject(contractRoot.resolve("runtime_mvp_cases.json").toFile().readText())
    }

    @Test
    fun `runtime fixture envelope and case identities are valid`() {
        val schema = JSONObject(contractRoot.resolve("runtime_mvp.schema.json").toFile().readText())
        val cases = document.getJSONArray("cases").objects()

        assertEquals("https://json-schema.org/draft/2020-12/schema", schema.getString("\$schema"))
        assertEquals("runtime_mvp.schema.json", document.getString("\$schema"))
        assertEquals("1.0.0", document.getString("specVersion"))
        assertTrue(document.getString("fixtureVersion").matches(Regex("^1\\.[0-9]+\\.[0-9]+$")))
        assertEquals(cases.size, cases.map { it.getString("id") }.toSet().size)
        cases.forEach { fixture ->
            assertEquals(setOf("id", "behavior", "input", "expected"), fixture.keySet())
            assertTrue(
                fixture.getString("behavior") in
                    setOf("offline", "partialFailure", "flush", "queue", "lifecycle"),
            )
        }
    }

    @TestFactory
    fun `shared Flutter and Kotlin fixtures conform to Runtime MVP`(): Stream<DynamicTest> =
        document.getJSONArray("cases").objects().map { fixture ->
            DynamicTest.dynamicTest(fixture.getString("id")) {
                runTest {
                    val actual = runFixture(fixture)
                    val expected = fixture.getJSONObject("expected")
                    assertTrue(
                        expected.similar(JSONObject(actual)),
                        "${fixture.getString("id")} expected=$expected actual=${JSONObject(actual)}",
                    )
                }
            }
        }.stream()

    private suspend fun runFixture(fixture: JSONObject): Map<String, Any> {
        val input = fixture.getJSONObject("input")
        return when (fixture.getString("behavior")) {
            "offline" -> offline(input)
            "partialFailure" -> partialFailure(input)
            "flush" -> flush(input)
            "queue" -> queue(input)
            "lifecycle" -> lifecycle(input)
            else -> error("Unsupported fixture behavior")
        }
    }

    private suspend fun offline(input: JSONObject): Map<String, Any> {
        val setup = setup(input.getJSONArray("targets").strings(), online = false)
        val result = setup.client.track(FixtureEvent("event-1"))
        return mapOf(
            "attempted" to setup.trackers.flatMap { it.events },
            "queued" to result.queuedTrackerIds,
            "queueSize" to setup.queue.size(),
        )
    }

    private suspend fun partialFailure(input: JSONObject): Map<String, Any> {
        val setup = setup(
            targets = input.getJSONArray("targets").strings(),
            failing = input.getJSONArray("failing").strings().toSet(),
        )
        val result = setup.client.track(FixtureEvent("event-1"))
        return mapOf(
            "successful" to result.successfulTrackerIds,
            "queued" to result.queuedTrackerIds,
            "queueSize" to setup.queue.size(),
        )
    }

    private suspend fun flush(input: JSONObject): Map<String, Any> {
        val pending = input.getJSONArray("pending").strings()
        val setup = setup(
            targets = pending,
            failing = input.getJSONArray("failing").strings().toSet(),
            online = input.getBoolean("online"),
        )
        setup.queue.enqueue(QueuedEvent("event-1", FixtureEvent("event-1"), pending))
        val result = setup.client.flush()
        val remaining = setup.queue.read(10)
        return buildMap {
            put("attemptedEvents", result.attemptedEvents)
            put("deliveredEvents", result.deliveredEvents)
            put("remainingEvents", result.remainingEvents)
            put("pending", remaining.singleOrNull()?.trackerIds.orEmpty())
            if (remaining.isNotEmpty() || !input.getBoolean("online")) {
                put("attempts", remaining.singleOrNull()?.attempts ?: 0)
            }
        }
    }

    private suspend fun queue(input: JSONObject): Map<String, Any> {
        val queue = InMemoryEventQueue()
        input.getJSONArray("eventIds").strings().forEach { id ->
            queue.enqueue(QueuedEvent(id, FixtureEvent(id), listOf("a")))
        }
        if (input.getString("operation") == "replace") {
            val first = queue.read(10).first()
            queue.replace(first.copy(attempts = 1))
        }
        val values = queue.read(input.optInt("limit", 10))
        return buildMap {
            put("eventIds", values.map(QueuedEvent::id))
            put("queueSize", queue.size())
            if (input.getString("operation") == "replace") {
                put("attempts", values.map(QueuedEvent::attempts))
            }
        }
    }

    private suspend fun lifecycle(input: JSONObject): Map<String, Any> {
        val tracker = FixtureTracker("analytics")
        val client = FlexTrackClient(RoutingEngine(RoutingConfiguration(emptyList())))
        client.register(tracker)
        repeat(input.getInt("initializeCalls")) { client.start() }
        return mapOf("trackerInitializeCalls" to tracker.startCalls)
    }

    private suspend fun setup(
        targets: List<String>,
        failing: Set<String> = emptySet(),
        online: Boolean = true,
    ): Setup {
        val queue = InMemoryEventQueue()
        val trackers = targets.map { FixtureTracker(it, it in failing) }
        val rules = if (targets.isEmpty()) emptyList() else listOf(
            RoutingRule(
                targetGroup = TrackerGroup("fixture", targets),
                requireConsent = false,
            ),
        )
        val client = FlexTrackClient(
            routingEngine = RoutingEngine(RoutingConfiguration(rules)),
            queue = queue,
            onlineProvider = { online },
        )
        (trackers.ifEmpty { listOf(FixtureTracker("unused")) }).forEach { client.register(it) }
        client.start()
        return Setup(client, queue, trackers)
    }

    private class FixtureEvent(id: String) : FlexEvent(id, Instant.parse("2026-08-17T00:00:00Z")) {
        override val name: String = "purchase"
        override val properties: Map<String, Any> = mapOf("plan" to "pro")
        override val requiresConsent: Boolean = false
    }

    private class FixtureTracker(override val id: String, private val failing: Boolean = false) : Tracker {
        val events = mutableListOf<String>()
        var startCalls: Int = 0

        override suspend fun start() { startCalls++ }

        override suspend fun track(event: FlexEvent) {
            events += id
            if (failing) error("failure")
        }
    }

    private data class Setup(
        val client: FlexTrackClient,
        val queue: EventQueue,
        val trackers: List<FixtureTracker>,
    )
}

private fun JSONArray.strings(): List<String> = List(length()) { getString(it) }
private fun JSONArray.objects(): List<JSONObject> = List(length()) { getJSONObject(it) }
