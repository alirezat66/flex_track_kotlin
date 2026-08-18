package dev.flextrack.context

import dev.flextrack.routing.ConsentState
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

public enum class ConsentType { GENERAL, PII, MARKETING, ANALYTICS, PERFORMANCE }

public data class ConsentSummary(
    val general: Boolean = false,
    val pii: Boolean = false,
    val marketing: Boolean = false,
    val analytics: Boolean = false,
    val performance: Boolean = false,
    val custom: Map<String, Boolean> = emptyMap(),
    val timestamp: Instant? = null,
    val version: String? = null,
) {
    val hasAnyConsent: Boolean get() =
        general || pii || marketing || analytics || performance || custom.values.any { it }
    val hasAllStandardConsents: Boolean get() =
        general && pii && marketing && analytics && performance

    public fun toMap(): Map<String, Any?> = linkedMapOf(
        "general" to general,
        "pii" to pii,
        "marketing" to marketing,
        "analytics" to analytics,
        "performance" to performance,
        "custom" to custom.toMap(),
        "timestamp" to timestamp?.toString(),
        "version" to version,
        "hasAnyConsent" to hasAnyConsent,
        "hasAllStandardConsents" to hasAllStandardConsents,
    )

    public fun routingState(): ConsentState = ConsentState(general = general, pii = pii)
}

/** Thread-safe consent state with privacy-safe defaults and immutable snapshots. */
public class ConsentManager(private val clock: Clock = Clock.systemUTC()) {
    private val state: AtomicReference<ConsentSummary> = AtomicReference(ConsentSummary())

    public val summary: ConsentSummary get() = state.get()
    public val hasGeneralConsent: Boolean get() = summary.general
    public val hasPIIConsent: Boolean get() = summary.pii
    public val hasMarketingConsent: Boolean get() = summary.marketing
    public val hasAnalyticsConsent: Boolean get() = summary.analytics
    public val hasPerformanceConsent: Boolean get() = summary.performance
    public val hasAnyConsent: Boolean get() = summary.hasAnyConsent
    public val hasAllConsents: Boolean get() = summary.hasAllStandardConsents
    public val consentTimestamp: Instant? get() = summary.timestamp
    public val consentVersion: String? get() = summary.version

    public fun set(type: ConsentType, granted: Boolean) = update { current ->
        when (type) {
            ConsentType.GENERAL -> current.copy(general = granted)
            ConsentType.PII -> current.copy(pii = granted)
            ConsentType.MARKETING -> current.copy(marketing = granted)
            ConsentType.ANALYTICS -> current.copy(analytics = granted)
            ConsentType.PERFORMANCE -> current.copy(performance = granted)
        }
    }

    public fun setGeneralConsent(granted: Boolean) = set(ConsentType.GENERAL, granted)
    public fun setPIIConsent(granted: Boolean) = set(ConsentType.PII, granted)
    public fun setMarketingConsent(granted: Boolean) = set(ConsentType.MARKETING, granted)
    public fun setAnalyticsConsent(granted: Boolean) = set(ConsentType.ANALYTICS, granted)
    public fun setPerformanceConsent(granted: Boolean) = set(ConsentType.PERFORMANCE, granted)

    public fun setConsents(
        general: Boolean? = null,
        pii: Boolean? = null,
        marketing: Boolean? = null,
        analytics: Boolean? = null,
        performance: Boolean? = null,
        version: String? = null,
    ) = update { current ->
        current.copy(
            general = general ?: current.general,
            pii = pii ?: current.pii,
            marketing = marketing ?: current.marketing,
            analytics = analytics ?: current.analytics,
            performance = performance ?: current.performance,
            version = version ?: current.version,
        )
    }

    public fun grantAllConsents(version: String? = null) = setConsents(
        general = true, pii = true, marketing = true, analytics = true,
        performance = true, version = version,
    )

    public fun revokeAllConsents() = update {
        it.copy(
            general = false, pii = false, marketing = false,
            analytics = false, performance = false, custom = emptyMap(),
        )
    }

    public fun setCustomConsent(purpose: String, granted: Boolean) {
        require(purpose.isNotBlank()) { "consent purpose cannot be blank" }
        update { it.copy(custom = it.custom + (purpose to granted)) }
    }

    public fun getCustomConsent(purpose: String): Boolean = summary.custom[purpose] ?: false
    public fun getCustomConsents(): Map<String, Boolean> = summary.custom.toMap()
    public fun removeCustomConsent(purpose: String) = update { it.copy(custom = it.custom - purpose) }
    public fun setConsentVersion(version: String) {
        require(version.isNotBlank()) { "consent version cannot be blank" }
        update { it.copy(version = version) }
    }
    public fun isAllowedFor(type: ConsentType): Boolean = when (type) {
        ConsentType.GENERAL -> hasGeneralConsent
        ConsentType.PII -> hasPIIConsent
        ConsentType.MARKETING -> hasMarketingConsent
        ConsentType.ANALYTICS -> hasAnalyticsConsent
        ConsentType.PERFORMANCE -> hasPerformanceConsent
    }
    public fun isConsentRequiredFor(type: ConsentType): Boolean = !isAllowedFor(type)

    public fun validate(): List<String> = buildList {
        val value = summary
        if (value.general && !value.pii) add("General consent granted but PII consent denied - may cause compliance issues")
        if (value.version == null && value.hasAnyConsent) add("Consent version not set - recommended for compliance tracking")
        if (value.timestamp == null && value.hasAnyConsent) add("Consent timestamp not set - required for compliance reporting")
    }

    public fun toMap(): Map<String, Any?> = summary.toMap().filterKeys {
        it != "hasAnyConsent" && it != "hasAllStandardConsents"
    }

    public fun loadFromMap(data: Map<String, Any?>) {
        @Suppress("UNCHECKED_CAST")
        val custom = (data["custom"] as? Map<*, *>)?.entries
            ?.filter { it.key is String && it.value is Boolean }
            ?.associate { it.key as String to it.value as Boolean }
            .orEmpty()
        state.set(
            ConsentSummary(
                general = data["general"] as? Boolean ?: false,
                pii = data["pii"] as? Boolean ?: false,
                marketing = data["marketing"] as? Boolean ?: false,
                analytics = data["analytics"] as? Boolean ?: false,
                performance = data["performance"] as? Boolean ?: false,
                custom = custom,
                timestamp = (data["timestamp"] as? String)?.let { runCatching { Instant.parse(it) }.getOrNull() },
                version = data["version"] as? String,
            ),
        )
    }

    private inline fun update(transform: (ConsentSummary) -> ConsentSummary) {
        while (true) {
            val current = state.get()
            val next = transform(current).copy(timestamp = clock.instant())
            if (state.compareAndSet(current, next)) return
        }
    }

    override fun toString(): String = "ConsentManager(any=$hasAnyConsent, all=$hasAllConsents, version=$consentVersion)"
}
