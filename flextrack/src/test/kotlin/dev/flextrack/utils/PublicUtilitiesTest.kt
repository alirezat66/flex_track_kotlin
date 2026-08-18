package dev.flextrack.utils

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PublicUtilitiesTest {
    @Test fun `wildcards question marks and exact patterns match case insensitively`() {
        assertTrue(PatternMatcher.matches("purchase_completed", "purchase_*"))
        assertTrue(PatternMatcher.matches("debug_1", "debug_?"))
        assertTrue(PatternMatcher.matches("LOGIN", "login")); assertFalse(PatternMatcher.matches("purchase", "login*"))
    }
    @Test fun `pattern collections prefixes suffixes and fragments use OR semantics`() {
        assertTrue(PatternMatcher.matchesAny("purchase", listOf("login", "pur*")))
        assertTrue(PatternMatcher.startsWithAny("api_request", listOf("web", "api")))
        assertTrue(PatternMatcher.endsWithAny("request_error", listOf("ok", "error")))
        assertTrue(PatternMatcher.containsAny("checkout_complete", listOf("cart", "complete")))
    }
    @Test fun `property matchers support exists equality wildcard and regex`() {
        val values = mapOf<String, Any?>("plan" to "premium", "count" to 2)
        assertTrue(PatternMatcher.matchesProperty(values, "plan"))
        assertTrue(PatternMatcher.matchesProperty(values, "count", PropertyMatcher.Equals(2)))
        assertTrue(PatternMatcher.matchesProperty(values, "plan", PropertyMatcher.Pattern("pre*")))
        assertTrue(PatternMatcher.matchesProperty(values, "plan", PropertyMatcher.RegexPattern(Regex("^prem"))))
    }
    @Test fun `all and any property composition are distinct`() {
        val values = mapOf<String, Any?>("plan" to "pro")
        val matchers = mapOf("plan" to PropertyMatcher.Equals("pro"), "region" to PropertyMatcher.Exists)
        assertFalse(PatternMatcher.matchesAllProperties(values, matchers)); assertTrue(PatternMatcher.matchesAnyProperty(values, matchers))
    }
    @Test fun `every public category has known matches`() {
        val examples = mapOf(EventPatternCategory.USER_INTERACTION to "click_button", EventPatternCategory.NAVIGATION to "page_view_home", EventPatternCategory.BUSINESS to "purchase_done", EventPatternCategory.ERROR to "crash_app", EventPatternCategory.PERFORMANCE to "latency_api", EventPatternCategory.DEBUG to "debug_state", EventPatternCategory.SYSTEM to "health_check", EventPatternCategory.NETWORK to "upload_file")
        examples.forEach { (category, value) -> assertTrue(PatternMatcher.matchesCategory(value, category)) }
    }
    @Test fun `pattern validation and bounded cache are observable`() {
        PatternMatcher.clearCache(); assertTrue(PatternMatcher.validate("event_*").isValid); assertFalse(PatternMatcher.validate("[broken").isValid)
        repeat(101) { PatternMatcher.matches("value", "pattern_${it}*") }
        assertTrue(PatternMatcher.cacheStats().getValue("size") <= 100); assertEquals(100, PatternMatcher.cacheStats()["maxSize"])
    }
    @Test fun `deterministic sampling and buckets are stable`() {
        assertEquals(SamplingUtils.deterministic("user", 0.5), SamplingUtils.deterministic("user", 0.5))
        assertEquals(SamplingUtils.bucket("user", 10), SamplingUtils.bucket("user", 10)); assertEquals(0, SamplingUtils.bucket("user", 0))
    }
    @Test fun `seed actually makes random sampling reproducible`() {
        SamplingUtils.setSeed(42); val first = List(20) { SamplingUtils.sample(0.5) }
        SamplingUtils.setSeed(42); assertEquals(first, List(20) { SamplingUtils.sample(0.5) })
    }
    @Test fun `sampling boundaries conversion and validation are exact`() {
        assertFalse(SamplingUtils.sample(0.0)); assertTrue(SamplingUtils.sample(1.0))
        assertEquals(0.25, SamplingUtils.percentageToRate(25.0)); assertEquals(25.0, SamplingUtils.rateToPercentage(0.25))
        assertFalse(SamplingUtils.validateRate(Double.NaN).isValid); assertFalse(SamplingUtils.validateRate(1.1).isValid); assertTrue(SamplingUtils.validateRate(0.5).isValid)
    }
    @Test fun `time reservoir adaptive weighted and bucket helpers cover boundaries`() {
        val clock = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC)
        assertTrue(SamplingUtils.byTime(Duration.ofMillis(100), clock)); assertTrue(SamplingUtils.byTime(Duration.ZERO, clock))
        assertTrue(SamplingUtils.includeInReservoir(3, 5)); assertEquals(0.5, SamplingUtils.adaptiveRate(200, 100))
        assertTrue(SamplingUtils.weighted(mapOf("critical" to 1.0), "critical")); assertTrue(SamplingUtils.inBuckets("user", 10, listOf(SamplingUtils.bucket("user", 10))))
    }
    @Test fun `sampling statistics expose retained and dropped rates`() {
        val stats = SamplingUtils.stats(listOf(true, false, true, false))
        assertEquals(4, stats.totalEvents); assertEquals(2, stats.sampledEvents); assertEquals(2, stats.droppedEvents)
        assertEquals(0.5, stats.actualSampleRate); assertEquals(0.5, stats.dropRate)
    }
    @Test fun `deterministic strategy respects identity and reset`() {
        val strategy = SamplingUtils.strategy(SamplingConfig.deterministic(0.5))
        assertEquals(strategy.shouldSample("event", userId = "user"), strategy.shouldSample("other", userId = "user")); strategy.reset()
    }
    @Test fun `tracker and event identifiers enforce public limits`() {
        assertTrue(ValidationUtils.trackerId("firebase_1").isValid); assertFalse(ValidationUtils.trackerId("all").isValid); assertFalse(ValidationUtils.trackerId("bad id").isValid)
        assertTrue(ValidationUtils.eventName("purchase.completed").isValid); assertFalse(ValidationUtils.eventName("1purchase").isValid)
    }
    @Test fun `property validation covers keys counts types lengths and finite numbers`() {
        assertFalse(ValidationUtils.propertyKey("timestamp").isValid); assertFalse(ValidationUtils.propertyKey("bad-key").isValid)
        assertTrue(ValidationUtils.propertyValue(Instant.now()).isValid); assertFalse(ValidationUtils.propertyValue(Double.NaN).isValid)
        assertFalse(ValidationUtils.eventProperties((0..50).associate { "key$it" to it }).isValid)
    }
    @Test fun `optional user and session identifiers retain platform rules`() {
        assertTrue(ValidationUtils.userId(null).isValid); assertFalse(ValidationUtils.userId("bad\nvalue").isValid)
        assertTrue(ValidationUtils.sessionId("session-1").isValid); assertFalse(ValidationUtils.sessionId("bad session").isValid)
    }
    @Test fun `rate priority and tracker group validation cover boundaries`() {
        assertTrue(ValidationUtils.sampleRate(0.0).isValid); assertFalse(ValidationUtils.sampleRate(Double.POSITIVE_INFINITY).isValid)
        assertTrue(ValidationUtils.priority(-1000).isValid); assertFalse(ValidationUtils.priority(1001).isValid)
        assertTrue(ValidationUtils.trackerGroup("analytics", listOf("firebase", "*")).isValid); assertFalse(ValidationUtils.trackerGroup("analytics", listOf("firebase", "firebase")).isValid)
    }
    @Test fun `routing validation distinguishes warnings duplicates and invalid rules`() {
        assertTrue(ValidationUtils.routingConfiguration(emptyList()).isWarning)
        val group = ValidationGroup("analytics", listOf("firebase")); val valid = ValidationRuleData("rule", 1.0, 0, false, false, true, group)
        assertTrue(ValidationUtils.routingConfiguration(listOf(valid)).isValid)
        assertFalse(ValidationUtils.routingConfiguration(listOf(valid, valid)).isValid)
    }
    @Test fun `consent validation prioritizes PII safety`() {
        assertFalse(ValidationUtils.consent(ConsentValidationData(true, false, false, true)).isValid)
        assertTrue(ValidationUtils.consent(ConsentValidationData(true, false, false, false)).isWarning)
        assertTrue(ValidationUtils.consent(ConsentValidationData(false, true, true, false, "1")).isValid)
    }
    @Test fun `setup validation reports missing trackers routing and consent`() {
        val results = ValidationUtils.setup(SetupValidationData(emptyList(), emptyList(), ConsentValidationData(true, false, false, false)))
        assertTrue(results.any { !it.isValid }); assertTrue(results.count { it.isWarning } == 2)
    }
}
