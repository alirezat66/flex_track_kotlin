package dev.flextrack.conformance

import dev.flextrack.event.EnrichedEvent
import dev.flextrack.event.FlexEvent
import dev.flextrack.routing.ConsentState
import dev.flextrack.routing.EventCategory
import dev.flextrack.routing.RoutingConfiguration
import dev.flextrack.routing.RoutingEngine
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import dev.flextrack.sampling.DeterministicSampler
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.nio.file.Path
import java.time.Instant
import java.util.stream.Stream

class CoreMvpConformanceTest {
    @TestFactory
    fun `shared Flutter and Kotlin fixtures conform to Core MVP`(): Stream<DynamicTest> {
        val workingDirectory = Path.of(System.getProperty("user.dir"))
        val root = workingDirectory.resolve("contract").takeIf { it.toFile().isDirectory }
            ?: workingDirectory.parent.resolve("contract")
        val document = JSONObject(root.resolve("core_mvp_cases.json").toFile().readText())
        assertEquals("1.0.0", document.getString("specVersion"))
        val cases = document.getJSONArray("cases")
        return (0 until cases.length()).map { cases.getJSONObject(it) }.map { fixture ->
            DynamicTest.dynamicTest(fixture.getString("id")) { verify(fixture) }
        }.stream()
    }

    private fun verify(fixture: JSONObject) {
        when (fixture.getString("behavior")) {
            "routing" -> verifyRouting(fixture)
            "consent" -> verifyConsent(fixture)
            "sampling" -> verifySampling(fixture)
            "enrichment" -> verifyEnrichment(fixture)
            "debug" -> verifyDebug(fixture)
            else -> error("Unsupported fixture behavior")
        }
    }

    private fun verifyRouting(fixture: JSONObject) {
        val input = fixture.getJSONObject("input")
        val expected = fixture.getJSONObject("expected")
        val event = input.getJSONObject("event").toEvent(requiresConsent = false)
        val rules = input.getJSONArray("rules").objects().map(::toRule)
        val defaultGroup = input.optJSONArray("defaultGroup")?.strings()?.let {
            TrackerGroup("default", it)
        }
        val result = RoutingEngine(RoutingConfiguration(rules, defaultGroup = defaultGroup)).route(
            event,
            consent = ConsentState(general = true),
            availableTrackers = input.getJSONArray("availableTrackers").strings().toSet(),
        )
        assertEquals(expected.getJSONArray("targets").strings(), result.targetTrackers)
        assertEquals(
            expected.getJSONArray("appliedPriorities").ints(),
            result.appliedRules.map(RoutingRule::priority),
        )
    }

    private fun verifyConsent(fixture: JSONObject) {
        val input = fixture.getJSONObject("input")
        val ruleInput = input.getJSONObject("rule")
        val rule = RoutingRule(
            targetGroup = TrackerGroup("fixture", ruleInput.getJSONArray("targets").strings()),
            requireConsent = ruleInput.optBoolean("requireConsent", true),
            requirePIIConsent = ruleInput.optBoolean("requirePIIConsent", false),
        )
        val result = RoutingEngine(RoutingConfiguration(listOf(rule))).route(
            input.getJSONObject("event").toEvent(),
            ConsentState(input.getBoolean("generalConsent"), input.getBoolean("piiConsent")),
            rule.targetGroup.trackerIds.toSet(),
        )
        assertEquals(
            fixture.getJSONObject("expected").getJSONArray("skipReasons").strings(),
            result.skippedRules.map { it.reason },
        )
    }

    private fun verifySampling(fixture: JSONObject) {
        val input = fixture.getJSONObject("input")
        val identity = input.getString("identity")
        val event = FixtureEvent(nameValue = "fixture", userIdValue = identity)
        assertEquals(
            fixture.getJSONObject("expected").getLong("hash"),
            DeterministicSampler.stableHash(identity),
        )
        assertEquals(
            fixture.getJSONObject("expected").getBoolean("accepted"),
            DeterministicSampler.shouldSample(event, input.getDouble("sampleRate")),
        )
    }

    private fun verifyEnrichment(fixture: JSONObject) {
        val input = fixture.getJSONObject("input")
        val event = FixtureEvent(
            eventIdValue = input.getString("eventId"),
            timestampValue = Instant.parse(input.getString("timestamp")),
            nameValue = input.getString("name"),
            propertiesValue = input.getJSONObject("properties").toMap(),
        )
        val enriched = EnrichedEvent(event, input.getJSONObject("extraProperties").toMap())
        val expected = fixture.getJSONObject("expected")
        assertEquals(expected.getString("eventId"), enriched.eventId)
        assertEquals(Instant.parse(expected.getString("timestamp")), enriched.timestamp)
        assertEquals(expected.getJSONObject("properties").toMap(), enriched.properties)
    }

    private fun verifyDebug(fixture: JSONObject) {
        val input = fixture.getJSONObject("input")
        val engine = RoutingEngine(
            RoutingConfiguration(input.getJSONArray("rules").objects().map(::toRule)),
        )
        val debug = engine.debug(
            input.getJSONObject("event").toEvent(requiresConsent = false),
            ConsentState(general = true),
            input.getJSONArray("availableTrackers").strings().toSet(),
        )
        assertEquals(
            fixture.getJSONObject("expected").getJSONArray("targetTrackers").strings(),
            debug.routingResult.targetTrackers,
        )
    }

    private fun toRule(value: JSONObject): RoutingRule {
        val targets = value.getJSONArray("targets").strings()
        return RoutingRule(
            eventNameContains = value.optString("nameContains").ifBlank { null },
            category = value.optString("category").ifBlank { null }?.let(::EventCategory),
            isDefault = value.optBoolean("default", false),
            targetGroup = TrackerGroup("fixture-${targets.joinToString()}", targets),
            priority = value.optInt("priority", 0),
            requireConsent = false,
        )
    }

    private fun JSONObject.toEvent(requiresConsent: Boolean? = null): FlexEvent = FixtureEvent(
        nameValue = getString("name"),
        categoryValue = optString("category").ifBlank { null }?.let(::EventCategory),
        containsPIIValue = optBoolean("containsPII", false),
        requiresConsentValue = requiresConsent ?: optBoolean("requiresConsent", true),
    )

    private class FixtureEvent(
        eventIdValue: String = "fixture-event",
        timestampValue: Instant = Instant.EPOCH,
        private val nameValue: String,
        private val propertiesValue: Map<String, Any?>? = emptyMap(),
        private val categoryValue: EventCategory? = null,
        private val containsPIIValue: Boolean = false,
        private val requiresConsentValue: Boolean = false,
        private val userIdValue: String? = null,
    ) : FlexEvent(eventIdValue, timestampValue) {
        override val name: String = nameValue
        override val properties: Map<String, Any?>? = propertiesValue
        override val category: EventCategory? = categoryValue
        override val containsPII: Boolean = containsPIIValue
        override val requiresConsent: Boolean = requiresConsentValue
        override val userId: String? = userIdValue
    }
}

private fun org.json.JSONArray.strings(): List<String> =
    List(length()) { getString(it) }

private fun org.json.JSONArray.ints(): List<Int> =
    List(length()) { getInt(it) }

private fun org.json.JSONArray.objects(): List<JSONObject> =
    List(length()) { getJSONObject(it) }

private fun JSONObject.toMap(): Map<String, Any?> = keys().asSequence().associateWith { get(it) }
