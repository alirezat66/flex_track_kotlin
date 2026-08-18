package dev.flextrack.context

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackingContextTest {
    private val instant = Instant.parse("2026-08-18T00:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test fun `basic context uses production privacy defaults`() {
        val context = TrackingContext.create(clock = clock)
        assertEquals(Environment.PRODUCTION, context.environment)
        assertFalse(context.isUserIdentified); assertFalse(context.hasActiveSession)
        assertFalse(context.consentManager.hasAnyConsent); assertEquals(instant, context.createdAt)
    }

    @Test fun `all constructor data is retained defensively`() {
        val users = mutableMapOf<String, Any?>("plan" to "pro")
        val context = TrackingContext.create("user", "session", "device", users, mapOf("source" to "direct"), environment = Environment.STAGING, appVersion = "1", buildNumber = "2", clock = clock)
        users["plan"] = "changed"
        assertEquals("pro", context.userProperties["plan"]); assertEquals("direct", context.sessionProperties["source"])
        assertEquals(Environment.STAGING, context.environment)
    }

    @Test fun `development factory has native debug metadata`() {
        val context = TrackingContext.development("user", "session")
        assertEquals("dev-device", context.deviceId); assertEquals("dev", context.appVersion)
        assertTrue(context.isDebugMode); assertFalse(context.isProduction)
    }

    @Test fun `testing factory grants all versioned consents`() {
        val context = TrackingContext.testing("user", "session")
        assertTrue(context.isTesting); assertTrue(context.consentManager.hasAllConsents)
        assertEquals("test", context.consentManager.consentVersion)
    }

    @Test fun `environment capabilities mirror Flutter semantics`() {
        assertTrue(Environment.DEVELOPMENT.enableDebug); assertTrue(Environment.TESTING.enableDebug)
        assertTrue(Environment.STAGING.enableSampling); assertTrue(Environment.PRODUCTION.enableSampling)
        assertTrue(Environment.PRODUCTION.strictConsent); assertFalse(Environment.STAGING.strictConsent)
    }

    @Test fun `identity updates create independent contexts`() {
        val original = TrackingContext.create(userId = "one", sessionId = "first", clock = clock)
        val updated = original.withUserId("two").withSessionId("second")
        assertEquals("one", original.userId); assertEquals("first", original.sessionId)
        assertEquals("two", updated.userId); assertEquals("second", updated.sessionId)
    }

    @Test fun `property updates merge without mutating original`() {
        val original = TrackingContext.create(userProperties = mapOf("age" to 25, "plan" to "basic"), sessionProperties = mapOf("page" to 1), clock = clock)
        val updated = original.withUserProperties(mapOf("age" to 26, "premium" to true)).withSessionProperties(mapOf("page" to 2))
        assertEquals(25, original.userProperties["age"]); assertEquals(26, updated.userProperties["age"])
        assertEquals(true, updated.userProperties["premium"]); assertEquals(2, updated.sessionProperties["page"])
    }

    @Test fun `typed property access rejects wrong types`() {
        val context = TrackingContext.create(userProperties = mapOf("age" to 25, "name" to "Ada"), sessionProperties = mapOf("mobile" to true), clock = clock)
        assertEquals(25, context.getUserProperty<Int>("age")); assertEquals(null, context.getUserProperty<Int>("name"))
        assertEquals(true, context.getSessionProperty<Boolean>("mobile"))
    }

    @Test fun `event properties contain only non-null routing context`() {
        val context = TrackingContext.create(userId = "user", deviceId = "device", environment = Environment.STAGING, appVersion = "1", clock = clock)
        assertEquals(mapOf("user_id" to "user", "device_id" to "device", "app_version" to "1", "environment" to "staging", "context_created_at" to instant.toString()), context.eventProperties())
    }

    @Test fun `map round trip preserves public context and consent`() {
        val consent = ConsentManager(Clock.fixed(instant, ZoneOffset.UTC)).apply { setConsents(general = true, version = "1") }
        val original = TrackingContext.create("user", "session", "device", mapOf("age" to 25), mapOf("source" to "search"), consent, Environment.STAGING, "2", "20", clock)
        val restored = TrackingContext.fromMap(original.toMap())
        assertEquals(original, restored); assertEquals(original.userProperties, restored.userProperties)
        assertTrue(restored.consentManager.hasGeneralConsent); assertEquals("1", restored.consentManager.consentVersion)
    }

    @Test fun `unknown serialized environment safely falls back to production`() {
        assertEquals(Environment.PRODUCTION, TrackingContext.fromMap(mapOf("environment" to "unknown")).environment)
    }

    @Test fun `production validation reports identity session version and consent issues`() {
        val consent = ConsentManager().apply { setGeneralConsent(true) }
        val issues = TrackingContext.create(consentManager = consent, clock = clock).validate()
        assertTrue(issues.any { it.contains("User not identified") }); assertTrue(issues.any { it.contains("No active session") })
        assertTrue(issues.any { it.contains("App version not set") }); assertTrue(issues.any { it.contains("Consent version not set") })
    }

    @Test fun `complete production context validates cleanly`() {
        val consent = ConsentManager(Clock.fixed(instant, ZoneOffset.UTC)).apply { grantAllConsents("1") }
        assertTrue(TrackingContext.create("user", "session", consentManager = consent, appVersion = "1", clock = clock).validate().isEmpty())
    }

    @Test fun `equality follows Flutter routing identity fields`() {
        val first = TrackingContext.create("user", "session", "device", userProperties = mapOf("a" to 1), environment = Environment.DEVELOPMENT, clock = clock)
        val sameIdentity = TrackingContext.create("user", "session", "device", userProperties = mapOf("a" to 2), environment = Environment.DEVELOPMENT, clock = clock)
        val other = first.withEnvironment(Environment.PRODUCTION)
        assertEquals(first, sameIdentity); assertEquals(first.hashCode(), sameIdentity.hashCode()); assertNotEquals(first, other)
    }
}
