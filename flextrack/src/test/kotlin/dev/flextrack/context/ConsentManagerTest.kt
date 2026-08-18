package dev.flextrack.context

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConsentManagerTest {
    private val now = Instant.parse("2026-08-18T00:00:00Z")
    private fun manager() = ConsentManager(Clock.fixed(now, ZoneOffset.UTC))

    @Test fun `initial state is privacy safe`() {
        val value = manager()
        assertFalse(value.hasAnyConsent); assertFalse(value.hasAllConsents)
        assertEquals(null, value.consentTimestamp); assertEquals(null, value.consentVersion)
        ConsentType.entries.forEach { assertFalse(value.isAllowedFor(it)) }
    }

    @Test fun `each standard consent can be set independently`() {
        ConsentType.entries.forEach { type ->
            val value = manager(); value.set(type, true)
            assertTrue(value.isAllowedFor(type)); assertEquals(now, value.consentTimestamp)
            ConsentType.entries.filterNot { it == type }.forEach { assertFalse(value.isAllowedFor(it)) }
        }
    }

    @Test fun `bulk updates preserve unspecified values`() {
        val value = manager()
        value.setConsents(general = true, pii = true, version = "1.0")
        value.setConsents(general = false, marketing = true, version = "1.1")
        assertFalse(value.hasGeneralConsent); assertTrue(value.hasPIIConsent)
        assertTrue(value.hasMarketingConsent); assertEquals("1.1", value.consentVersion)
    }

    @Test fun `grant and revoke all include custom consent lifecycle`() {
        val value = manager(); value.grantAllConsents("2.0"); value.setCustomConsent("location", true)
        assertTrue(value.hasAllConsents); assertTrue(value.getCustomConsent("location"))
        value.revokeAllConsents()
        assertFalse(value.hasAnyConsent); assertTrue(value.getCustomConsents().isEmpty())
    }

    @Test fun `custom consent is immutable outside manager`() {
        val value = manager(); value.setCustomConsent("camera", true)
        val snapshot = value.getCustomConsents().toMutableMap(); snapshot["camera"] = false
        assertTrue(value.getCustomConsent("camera")); assertFalse(value.getCustomConsent("missing"))
    }

    @Test fun `custom consent removal updates timestamp`() {
        val value = manager(); value.setCustomConsent("temporary", true); value.removeCustomConsent("temporary")
        assertFalse(value.getCustomConsent("temporary")); assertEquals(now, value.consentTimestamp)
    }

    @Test fun `blank custom purpose and version are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { manager().setCustomConsent(" ", true) }
        assertThrows(IllegalArgumentException::class.java) { manager().setConsentVersion("") }
    }

    @Test fun `required is the inverse of allowed for every standard type`() {
        val value = manager(); value.setAnalyticsConsent(true)
        ConsentType.entries.forEach { assertEquals(!value.isAllowedFor(it), value.isConsentRequiredFor(it)) }
    }

    @Test fun `summary is an immutable compliance snapshot`() {
        val value = manager(); value.setGeneralConsent(true); value.setCustomConsent("location", true); value.setConsentVersion("1")
        val snapshot = value.summary; value.setGeneralConsent(false)
        assertTrue(snapshot.general); assertTrue(snapshot.hasAnyConsent); assertFalse(snapshot.hasAllStandardConsents)
        assertEquals("1", snapshot.version); assertEquals(true, snapshot.custom["location"])
    }

    @Test fun `routing state exposes general and PII only`() {
        val value = manager(); value.setGeneralConsent(true); value.setPIIConsent(false); value.setMarketingConsent(true)
        assertTrue(value.summary.routingState().general); assertFalse(value.summary.routingState().pii)
    }

    @Test fun `map round trip preserves complete state`() {
        val original = manager(); original.setConsents(general = true, analytics = true, version = "v2"); original.setCustomConsent("camera", true)
        val restored = manager(); restored.loadFromMap(original.toMap())
        assertEquals(original.summary, restored.summary)
    }

    @Test fun `map loading defaults missing and malformed fields safely`() {
        val value = manager(); value.grantAllConsents(); value.loadFromMap(mapOf("timestamp" to "invalid", "custom" to mapOf(1 to "bad")))
        assertFalse(value.hasAnyConsent); assertEquals(null, value.consentTimestamp); assertTrue(value.getCustomConsents().isEmpty())
    }

    @Test fun `validation warns about general without PII and missing version`() {
        val value = manager(); value.setGeneralConsent(true)
        val issues = value.validate()
        assertTrue(issues.any { it.contains("PII consent denied") })
        assertTrue(issues.any { it.contains("Consent version not set") })
        assertFalse(issues.any { it.contains("timestamp not set") })
    }

    @Test fun `version mutation establishes compliance timestamp`() {
        val value = manager(); value.setConsentVersion("v1")
        assertEquals("v1", value.consentVersion); assertNotNull(value.consentTimestamp)
    }
}
