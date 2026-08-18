package dev.flextrack.config

import dev.flextrack.event.FlexEvent
import dev.flextrack.routing.EventCategory
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoutingBuilderTest {
    private class TypedEvent : FlexEvent() {
        override val name: String = "typed"
        override val properties: Map<String, Any?> = emptyMap()
    }

    @Test
    fun `build adds Flutter-compatible default rule when none exists`() {
        val config = RoutingBuilder().build()

        assertEquals(1, config.rules.size)
        assertTrue(config.rules.single().isDefault)
        assertEquals(TrackerGroup.All, config.rules.single().targetGroup)
        assertEquals(-1000, config.rules.single().priority)
    }

    @Test
    fun `explicit default prevents automatic fallback`() {
        val config = RoutingBuilder().apply {
            routeDefault { toTracker("archive") }
        }.build()

        assertEquals(1, config.rules.size)
        assertEquals(listOf("archive"), config.rules.single().targetGroup.trackerIds)
    }

    @Test
    fun `named groups can be route targets and defaults`() {
        val config = RoutingBuilder().apply {
            defineGroup("product", "firebase", "mixpanel")
            defaultGroup("product")
            routeNamed("purchase") { toGroup("product") }
        }.build()

        assertEquals(listOf("firebase", "mixpanel"), config.rules.single().targetGroup.trackerIds)
        assertEquals("product", config.defaultGroup?.name)
    }

    @Test
    fun `predefined groups match Flutter names`() {
        val config = RoutingBuilder().apply {
            routeNamed("all") { toGroup("all") }
            routeNamed("debug") { toGroup("development") }
        }.build()

        assertEquals(TrackerGroup.All, config.rules[0].targetGroup)
        assertEquals(TrackerGroup.Development, config.rules[1].targetGroup)
    }

    @Test
    fun `rules are sorted by descending priority with stable ties`() {
        val config = RoutingBuilder().apply {
            routeNamed("low") { toAll(); id("low"); priority(-1) }
            routeNamed("first") { toAll(); id("first"); priority(10) }
            routeNamed("second") { toAll(); id("second"); priority(10) }
        }.build()

        assertEquals(listOf("first", "second", "low", "auto-default"), config.rules.map(RoutingRule::id))
    }

    @Test
    fun `global switches are retained`() {
        val config = RoutingBuilder().apply {
            sampling(false)
            consentChecking(false)
            debugMode(true)
        }.build()

        assertFalse(config.enableSampling)
        assertFalse(config.enableConsentChecking)
        assertTrue(config.isDebugMode)
    }

    @Test
    fun `name regex exact and category conditions are mapped`() {
        val config = RoutingBuilder().apply {
            routeNamed("purchase") { toAll() }
            routeMatching(Regex("cart_[0-9]+")) { toAll() }
            routeExact("logout") { toAll() }
            routeCategory(EventCategory.Business) { toAll() }
        }.build()

        assertEquals("purchase", config.rules[0].eventNameContains)
        assertEquals("cart_[0-9]+", config.rules[1].eventNameRegex?.pattern)
        assertEquals("^\\Qlogout\\E$", config.rules[2].eventNameRegex?.pattern)
        assertEquals(EventCategory.Business, config.rules[3].category)
    }

    @Test
    fun `event type routing matches Flutter generic route`() {
        val rule = RoutingBuilder().apply {
            route<TypedEvent> { toAll() }
        }.build().rules.first()

        assertEquals(TypedEvent::class.java, rule.eventType)
        assertTrue(rule.matches(TypedEvent()))
    }

    @Test
    fun `groups categories validation and debug state are inspectable`() {
        val builder = RoutingBuilder().apply {
            defineGroup("unused", "archive")
            defineCategory("commerce")
            routeNamed("first") { toAll(); id("duplicate") }
            routeNamed("second") { toAll(); id("duplicate") }
        }

        assertEquals(3, builder.getAllGroups().size)
        assertEquals(8, builder.getAllCategories().size)
        assertTrue(builder.validate().any { it.contains("Duplicate rule IDs") })
        assertTrue(builder.validate().any { it.contains("Unreferenced custom groups") })
        assertEquals(2, builder.getDebugInfo()["rulesCount"])
        assertEquals(listOf("unused"), builder.getDebugInfo()["customGroups"])
    }

    @Test
    fun `custom and predefined category names resolve`() {
        val config = RoutingBuilder().apply {
            defineCategory("commerce")
            routeCategory("commerce") { toAll() }
            routeCategory("security") { toAll() }
        }.build()

        assertEquals(EventCategory("commerce"), config.rules[0].category)
        assertEquals(EventCategory.Security, config.rules[1].category)
    }

    @Test
    fun `property PII volume and essential conditions are mapped`() {
        val config = RoutingBuilder().apply {
            routeWithProperty("plan", "pro") { toAll() }
            routePII { toAll() }
            routeHighVolume { toAll() }
            routeEssential { toAll() }
        }.build()

        assertEquals("plan", config.rules[0].hasProperty)
        assertEquals("pro", config.rules[0].propertyValue)
        assertEquals(true, config.rules[1].containsPII)
        assertEquals(true, config.rules[2].isHighVolume)
        assertEquals(true, config.rules[3].isEssential)
    }

    @Test
    fun `sampling conveniences match Flutter percentages`() {
        val config = RoutingBuilder().apply {
            routeNamed("heavy") { toAll(); heavySampling() }
            routeNamed("light") { toAll(); lightSampling() }
            routeNamed("medium") { toAll(); mediumSampling() }
            routeNamed("none") { toAll(); noSampling() }
        }.build()

        assertEquals(listOf(0.01, 0.1, 0.5, 1.0), config.rules.take(4).map(RoutingRule::sampleRate))
    }

    @Test
    fun `environment consent PII and metadata modifiers are mapped`() {
        val config = RoutingBuilder().apply {
            routeNamed("debug") {
                toTracker("console")
                skipConsent()
                requirePIIConsent()
                onlyInDebug()
                priority(7)
                id("debug-rule")
                description("Debug diagnostics")
            }
        }.build()
        val rule = config.rules.first()

        assertFalse(rule.requireConsent)
        assertTrue(rule.requirePIIConsent)
        assertTrue(rule.debugOnly)
        assertFalse(rule.productionOnly)
        assertEquals(7, rule.priority)
        assertEquals("debug-rule", rule.id)
        assertEquals("Debug diagnostics", rule.description)
    }

    @Test
    fun `environment modifiers are mutually exclusive by last call`() {
        val config = RoutingBuilder().apply {
            routeNamed("event") { toAll(); onlyInDebug(); onlyInProduction() }
        }.build()

        assertFalse(config.rules.first().debugOnly)
        assertTrue(config.rules.first().productionOnly)
    }

    @Test
    fun `essential modifier matches Flutter shortcut`() {
        val rule = RoutingBuilder().apply {
            routeNamed("crash") { toAll(); essential() }
        }.build().rules.first()

        assertFalse(rule.requireConsent)
        assertEquals(1.0, rule.sampleRate)
        assertEquals(10, rule.priority)
    }

    @Test
    fun `smart defaults mirror Flutter baseline`() {
        val config = RoutingBuilder().apply { applySmartDefaults() }.build()

        assertEquals(3, config.rules.size)
        assertEquals(listOf(8, 5, 0), config.rules.map(RoutingRule::priority))
        assertTrue(config.rules.last().isDefault)
    }

    @Test
    fun `manual rules can be added cleared and removed`() {
        val manual = RoutingRule(id = "manual", targetGroup = TrackerGroup.All)
        val builder = RoutingBuilder()
        builder.addRule(manual)
        builder.removeRulesWhere { it.id == "missing" }
        assertTrue(builder.build().rules.any { it.id == "manual" })
        builder.clearRules()
        assertEquals(listOf("auto-default"), builder.build().rules.map(RoutingRule::id))
    }

    @Test
    fun `invalid group category and condition inputs fail immediately`() {
        assertThrows(IllegalArgumentException::class.java) { RoutingBuilder().defineGroup("") }
        assertThrows(IllegalArgumentException::class.java) { RoutingBuilder().defineGroup("empty") }
        assertThrows(IllegalArgumentException::class.java) { RoutingBuilder().defineCategory("") }
        assertThrows(IllegalArgumentException::class.java) { RoutingBuilder().routeNamed("") { toAll() } }
        assertThrows(IllegalArgumentException::class.java) { RoutingBuilder().routeExact("") { toAll() } }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeWithProperty("") { toAll() }
        }
        assertThrows(IllegalArgumentException::class.java) { RoutingBuilder().defaultGroup("missing") }
        assertThrows(IllegalStateException::class.java) {
            RoutingBuilder().routeCategory("missing") { toAll() }
        }
    }

    @Test
    fun `invalid targets modifiers and missing target fail immediately`() {
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { to() }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { toTracker("") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { toGroup("missing") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { toAll(); sample(1.1) }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { toAll(); id("") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { toAll(); description("") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            RoutingBuilder().routeNamed("event") { }
        }
    }
}
