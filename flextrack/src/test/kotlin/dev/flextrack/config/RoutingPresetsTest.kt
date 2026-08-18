package dev.flextrack.config

import dev.flextrack.routing.EventCategory
import dev.flextrack.routing.RoutingRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutingPresetsTest {
    @Test
    fun `smart defaults contain all eight public Flutter rules`() {
        val config = RoutingBuilder().also(SmartDefaults::apply).build()
        assertEquals(8, config.rules.size)
        assertEquals(listOf(20, 15, 12, 10, 8, 7, 5, 0), config.rules.map(RoutingRule::priority))
        assertTrue(config.rules.last().isDefault)
    }

    @Test
    fun `smart security and essential rules bypass consent and sampling`() {
        val rules = RoutingBuilder().also(SmartDefaults::apply).build().rules
        val security = rules.first { it.category == EventCategory.Security }
        val essential = rules.first { it.isEssential == true }
        assertFalse(security.requireConsent)
        assertEquals(1.0, security.sampleRate)
        assertFalse(essential.requireConsent)
    }

    @Test
    fun `performance focused smart defaults add frequency rules`() {
        val rules = RoutingBuilder().also(SmartDefaults::applyPerformanceFocused).build().rules
        assertEquals(10, rules.size)
        assertEquals(0.01, rules.first { it.hasProperty == "high_frequency" }.sampleRate)
        assertEquals(0.5, rules.first { it.hasProperty == "batchable" }.sampleRate)
    }

    @Test
    fun `privacy focused smart defaults add user and marketing rules`() {
        val rules = RoutingBuilder().also(SmartDefaults::applyPrivacyFocused).build().rules
        assertEquals(10, rules.size)
        assertTrue(rules.first { it.category == EventCategory.User }.requireConsent)
        assertTrue(rules.first { it.category == EventCategory.Marketing }.requireConsent)
    }

    @Test
    fun `development smart defaults prepend debug test and dev regexes`() {
        val rules = RoutingBuilder().also(SmartDefaults::applyDevelopmentFriendly).build().rules
        assertEquals(11, rules.size)
        assertEquals(listOf(15, 14, 13), rules.filter { it.eventNameRegex != null }.map(RoutingRule::priority))
        assertTrue(rules.filter { it.eventNameRegex != null }.all(RoutingRule::debugOnly))
    }

    @Test
    fun `GDPR defaults match Flutter rule count and priority order`() {
        val rules = RoutingBuilder().also(PrivacyDefaults::apply).build().rules
        assertEquals(14, rules.size)
        assertEquals(25, rules.first().priority)
        assertEquals(0, rules.last().priority)
        assertEquals(0.5, rules.last().sampleRate)
    }

    @Test
    fun `GDPR compliant destinations are isolated in a named group`() {
        val config = RoutingBuilder().also { PrivacyDefaults.apply(it, listOf("safe-a", "safe-b")) }.build()
        assertEquals(listOf("safe-a", "safe-b"), config.customGroups["gdpr_compliant"]?.trackerIds)
        assertEquals("gdpr_compliant", config.rules.first { it.containsPII == true }.targetGroup.name)
        assertEquals("gdpr_compliant", config.rules.first { it.hasProperty == "email" }.targetGroup.name)
    }

    @Test
    fun `strict GDPR adds identity session and behavior rules`() {
        val rules = RoutingBuilder().also(PrivacyDefaults::applyStrict).build().rules
        assertEquals(17, rules.size)
        assertNotNull(rules.firstOrNull { it.hasProperty == "user_id" && it.priority == 22 })
        assertNotNull(rules.firstOrNull { it.hasProperty == "session_id" && it.priority == 13 })
        assertNotNull(rules.firstOrNull { it.eventNameRegex?.pattern?.contains("interaction") == true })
    }

    @Test
    fun `minimal GDPR contains only five public rules`() {
        val rules = RoutingBuilder().also(PrivacyDefaults::applyMinimal).build().rules
        assertEquals(5, rules.size)
        assertEquals(listOf(20, 18, 16, 15, 0), rules.map(RoutingRule::priority))
    }

    @Test
    fun `CCPA contains PII marketing and fallback rules`() {
        val rules = RoutingBuilder().also(PrivacyDefaults::applyCCPA).build().rules
        assertEquals(3, rules.size)
        assertEquals(listOf(15, 12, 0), rules.map(RoutingRule::priority))
    }

    @Test
    fun `privacy regions select their documented preset`() {
        val counts = PrivacyRegion.entries.associateWith { region ->
            RoutingBuilder().also { PrivacyDefaults.applyForRegion(it, region) }.build().rules.size
        }
        assertEquals(17, counts[PrivacyRegion.EU])
        assertEquals(14, counts[PrivacyRegion.UK])
        assertEquals(3, counts[PrivacyRegion.CALIFORNIA])
        assertEquals(5, counts[PrivacyRegion.GLOBAL])
    }

    @Test
    fun `base performance preset mirrors all thirteen Flutter rules`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::apply).build().rules
        assertEquals(13, rules.size)
        assertEquals(25, rules.first().priority)
        assertEquals(0.5, rules.last().sampleRate)
        assertEquals(0.0001, rules.first { it.eventNameRegex?.pattern?.contains("animation") == true }.sampleRate)
    }

    @Test
    fun `mobile performance preset adds three platform rules`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::applyMobileOptimized).build().rules
        assertEquals(16, rules.size)
        assertEquals(0.005, rules.first { it.isHighVolume == true }.sampleRate)
        assertNotNull(rules.firstOrNull { it.hasProperty == "location" })
    }

    @Test
    fun `web performance preset adds navigation and DOM rules`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::applyWebOptimized).build().rules
        assertEquals(15, rules.size)
        assertNotNull(rules.firstOrNull { it.description?.startsWith("Web: Navigation") == true })
        assertNotNull(rules.firstOrNull { it.description?.startsWith("Web: DOM") == true })
    }

    @Test
    fun `server performance preset adds HTTP database and cache rules`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::applyServerOptimized).build().rules
        assertEquals(16, rules.size)
        assertTrue(rules.count { it.description?.startsWith("Server:") == true } == 3)
    }

    @Test
    fun `low latency preset has only essential critical and fallback rules`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::applyLowLatency).build().rules
        assertEquals(3, rules.size)
        assertEquals(listOf(20, 18, 0), rules.map(RoutingRule::priority))
        assertEquals(0.01, rules.last().sampleRate)
    }

    @Test
    fun `bandwidth preset applies minimal fallback sampling`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::applyBandwidthConscious).build().rules
        assertEquals(4, rules.size)
        assertEquals(0.001, rules.last().sampleRate)
    }

    @Test
    fun `high throughput preset applies ultra minimal rates`() {
        val rules = RoutingBuilder().also(PerformanceDefaults::applyHighThroughput).build().rules
        assertEquals(4, rules.size)
        assertEquals(0.00001, rules.last().sampleRate)
        assertEquals(0.0001, rules.first { it.isHighVolume == true }.sampleRate)
    }
}
