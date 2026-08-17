package dev.taghizadeh.flextrack.routing

import dev.taghizadeh.flextrack.event.ChildTestEvent
import dev.taghizadeh.flextrack.event.EnrichedEvent
import dev.taghizadeh.flextrack.event.TestEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutingEngineTest {
    private val analytics = TrackerGroup("analytics", listOf("analytics"))
    private val archive = TrackerGroup("archive", listOf("archive"))
    private val available = linkedSetOf("analytics", "archive")

    @Test
    fun `higher priority wins over matching default`() {
        val engine = engine(
            RoutingRule(category = EventCategory.Business, priority = 10, targetGroup = analytics),
            RoutingRule(isDefault = true, priority = 0, targetGroup = archive),
        )

        val result = engine.route(TestEvent(), ConsentState(general = true), available)

        assertEquals(listOf("analytics"), result.targetTrackers)
        assertEquals(listOf(10), result.appliedRules.map(RoutingRule::priority))
    }

    @Test
    fun `same tier rules merge targets in stable order`() {
        val engine = engine(
            RoutingRule(eventNameContains = "purchase", priority = 5, targetGroup = analytics),
            RoutingRule(
                eventNameContains = "purchase",
                priority = 5,
                targetGroup = TrackerGroup("both", listOf("archive", "analytics")),
            ),
        )

        val result = engine.route(TestEvent(), ConsentState(general = true), available)

        assertEquals(listOf("analytics", "archive"), result.targetTrackers)
        assertEquals(listOf(5, 5), result.appliedRules.map(RoutingRule::priority))
    }

    @Test
    fun `blocked high tier falls through to lower tier`() {
        val engine = engine(
            RoutingRule(
                priority = 10,
                requirePIIConsent = true,
                targetGroup = analytics,
            ),
            RoutingRule(priority = 0, requireConsent = false, targetGroup = archive),
        )

        val result = engine.route(
            TestEvent(requiresConsent = false),
            ConsentState(),
            available,
        )

        assertEquals(listOf("archive"), result.targetTrackers)
        assertEquals("Consent requirements not met", result.skippedRules.single().reason)
    }

    @Test
    fun `default group provides fallback when nothing matches`() {
        val config = RoutingConfiguration(
            rules = listOf(
                RoutingRule(eventNameContains = "other", targetGroup = analytics),
            ),
            defaultGroup = archive,
        )
        val result = RoutingEngine(config).route(
            TestEvent(requiresConsent = false),
            availableTrackers = available,
            consent = ConsentState(general = true),
        )

        assertEquals(listOf("archive"), result.targetTrackers)
        assertEquals(listOf(0), result.appliedRules.map(RoutingRule::priority))
    }

    @Test
    fun `general and PII consent are default deny`() {
        val generalResult = engine(
            RoutingRule(targetGroup = analytics),
        ).route(TestEvent(), availableTrackers = available)
        val piiResult = engine(
            RoutingRule(requirePIIConsent = true, targetGroup = analytics),
        ).route(
            TestEvent(containsPII = true),
            ConsentState(general = true),
            available,
        )

        assertTrue(generalResult.targetTrackers.isEmpty())
        assertTrue(piiResult.targetTrackers.isEmpty())
    }

    @Test
    fun `essential event bypasses consent and sampling`() {
        val rule = RoutingRule(sampleRate = 0.0, targetGroup = analytics)
        val result = engine(rule).route(
            TestEvent(isEssential = true),
            availableTrackers = available,
        )

        assertEquals(listOf("analytics"), result.targetTrackers)
    }

    @Test
    fun `type condition unwraps enrichment and supports subtypes`() {
        val event = EnrichedEvent(ChildTestEvent(), mapOf("route" to "/pay"))
        val rule = RoutingRule(
            eventType = TestEvent::class.java,
            hasProperty = "route",
            propertyValue = "/pay",
            targetGroup = analytics,
        )

        val result = engine(rule).route(event, ConsentState(general = true), available)

        assertEquals(listOf("analytics"), result.targetTrackers)
    }

    private fun engine(vararg rules: RoutingRule): RoutingEngine =
        RoutingEngine(RoutingConfiguration(rules.toList()))
}
