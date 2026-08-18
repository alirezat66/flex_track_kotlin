package dev.flextrack.routing

import dev.flextrack.event.FlexEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutingConditionsParityTest {
    private val group = TrackerGroup("analytics", listOf("analytics"))

    @Test
    fun `compound name category and property conditions must all match`() {
        val rule = RoutingRule(
            eventNameContains = "purchase",
            category = EventCategory.Business,
            hasProperty = "plan",
            propertyValue = "pro",
            targetGroup = group,
            requireConsent = false,
        )

        assertTrue(rule.matches(Event(nameValue = "purchase", plan = "pro")))
        assertFalse(rule.matches(Event(nameValue = "view", plan = "pro")))
        assertFalse(rule.matches(Event(nameValue = "purchase", plan = "free")))
        assertFalse(rule.matches(Event(nameValue = "purchase", plan = null)))
    }

    @Test
    fun `regex condition uses contains semantics`() {
        val rule = RoutingRule(eventNameRegex = Regex("cart_[0-9]+"), targetGroup = group)

        assertTrue(rule.matches(Event(nameValue = "open_cart_42")))
        assertFalse(rule.matches(Event(nameValue = "open_cart_x")))
    }

    @Test
    fun `debug and production flags are mutually exclusive environments`() {
        val debug = RoutingRule(debugOnly = true, targetGroup = group)
        val production = RoutingRule(productionOnly = true, targetGroup = group)

        assertTrue(debug.matches(Event(), isDebugMode = true))
        assertFalse(debug.matches(Event(), isDebugMode = false))
        assertFalse(production.matches(Event(), isDebugMode = true))
        assertTrue(production.matches(Event(), isDebugMode = false))
    }

    @Test
    fun `disabled consent checking routes a protected event`() {
        val engine = RoutingEngine(
            RoutingConfiguration(
                rules = listOf(RoutingRule(targetGroup = group)),
                enableConsentChecking = false,
            ),
        )

        val result = engine.route(Event(requiresConsentValue = true), availableTrackers = setOf("analytics"))

        assertEquals(listOf("analytics"), result.targetTrackers)
    }

    @Test
    fun `disabled sampling routes a zero-rate event`() {
        val engine = RoutingEngine(
            RoutingConfiguration(
                rules = listOf(
                    RoutingRule(sampleRate = 0.0, targetGroup = group, requireConsent = false),
                ),
                enableSampling = false,
            ),
        )

        val result = engine.route(Event(), availableTrackers = setOf("analytics"))

        assertEquals(listOf("analytics"), result.targetTrackers)
    }

    @Test
    fun `unavailable target is skipped with an actionable warning`() {
        val engine = engine(RoutingRule(targetGroup = group, requireConsent = false))

        val result = engine.route(Event(), availableTrackers = emptySet())

        assertTrue(result.targetTrackers.isEmpty())
        assertEquals("No available trackers in target group", result.skippedRules.single().reason)
        assertEquals(listOf("Rule resolved to no available trackers"), result.warnings)
    }

    @Test
    fun `routing debug explains unmatched and lower-priority rules`() {
        val high = RoutingRule(
            id = "high", priority = 10, eventNameContains = "purchase",
            targetGroup = group, requireConsent = false,
        )
        val low = RoutingRule(
            id = "low", priority = 0, targetGroup = group, requireConsent = false,
        )
        val unmatched = RoutingRule(
            id = "other", eventNameContains = "other", targetGroup = group,
        )
        val debug = engine(high, low, unmatched).debug(
            Event(nameValue = "purchase"), availableTrackers = setOf("analytics"),
        )

        assertTrue(debug.decisions.first { it.rule.id == "high" }.applied)
        assertEquals(
            "Lower priority tier was not evaluated",
            debug.decisions.first { it.rule.id == "low" }.reason,
        )
        assertEquals(
            "Event name 'purchase' does not contain 'other'",
            debug.decisions.first { it.rule.id == "other" }.reason,
        )
    }

    @Test
    fun `matching trackers retain group order and remove duplicates`() {
        val ordered = TrackerGroup("ordered", listOf("archive", "analytics", "archive"))
        val result = engine(
            RoutingRule(targetGroup = ordered, requireConsent = false),
        ).route(Event(), availableTrackers = setOf("analytics", "archive"))

        assertEquals(listOf("archive", "analytics"), result.targetTrackers)
    }

    private fun engine(vararg rules: RoutingRule): RoutingEngine =
        RoutingEngine(RoutingConfiguration(rules.toList()))

    private class Event(
        private val nameValue: String = "purchase",
        private val plan: String? = "pro",
        private val requiresConsentValue: Boolean = false,
    ) : FlexEvent() {
        override val name: String = nameValue
        override val properties: Map<String, Any?>? = plan?.let { mapOf("plan" to it) }
        override val category: EventCategory = EventCategory.Business
        override val requiresConsent: Boolean = requiresConsentValue
    }
}
