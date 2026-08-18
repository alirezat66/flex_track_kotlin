package dev.flextrack.config

import dev.flextrack.routing.EventCategory

/** Public routing presets equivalent to Flutter while retaining Kotlin DSL syntax. */
public object SmartDefaults {
    public fun apply(builder: RoutingBuilder) {
        with(builder) {
            routeCategory(EventCategory.Technical) { toDevelopment(); onlyInDebug(); lightSampling(); priority(8); description("Technical events for debugging") }
            routeHighVolume { toAll(); heavySampling(); priority(5); description("High volume events with reduced sampling") }
            routeCategory(EventCategory.Security) { toAll(); essential(); priority(15); description("Security events - always tracked") }
            routeCategory(EventCategory.System) { toAll(); skipConsent(); lightSampling(); priority(7); description("System events - no consent required") }
            routeCategory(EventCategory.Sensitive) { toAll(); requireConsent(); requirePIIConsent(); priority(12); description("Sensitive events requiring full consent") }
            routePII { toAll(); requirePIIConsent(); priority(10); description("PII events requiring specific consent") }
            routeEssential { toAll(); essential(); priority(20); description("Essential events - bypass all restrictions") }
            routeDefault { toAll(); priority(0); description("Default routing for unmatched events") }
        }
    }

    public fun applyPerformanceFocused(builder: RoutingBuilder) {
        apply(builder)
        with(builder) {
            routeWithProperty("high_frequency") { toAll(); heavySampling(); priority(6); description("High frequency events with heavy sampling") }
            routeWithProperty("batchable") { toAll(); mediumSampling(); priority(4); description("Batchable events with medium sampling") }
        }
    }

    public fun applyPrivacyFocused(builder: RoutingBuilder) {
        with(builder) {
            routeCategory(EventCategory.User) { toAll(); requireConsent(); priority(9); description("User events requiring consent") }
            routeCategory(EventCategory.Marketing) { toAll(); requireConsent(); priority(11); description("Marketing events requiring consent") }
        }
        apply(builder)
    }

    public fun applyDevelopmentFriendly(builder: RoutingBuilder) {
        with(builder) {
            routeMatching(Regex("debug_.*")) { toDevelopment(); onlyInDebug(); noSampling(); priority(15); description("Debug events for development") }
            routeMatching(Regex("test_.*")) { toDevelopment(); onlyInDebug(); noSampling(); priority(14); description("Test events for development") }
            routeMatching(Regex("dev_.*")) { toDevelopment(); onlyInDebug(); noSampling(); priority(13); description("Development-specific events") }
        }
        apply(builder)
    }
}

public enum class PrivacyRegion { EU, UK, CALIFORNIA, GLOBAL }

public typealias GDPRRegion = PrivacyRegion

public object PrivacyDefaults {
    public fun apply(builder: RoutingBuilder, compliantTrackers: List<String> = emptyList()) {
        val target = prepare(builder, compliantTrackers)
        with(builder) {
            routeCategory(EventCategory.Sensitive) { toGroup(target); requirePIIConsent(); requireConsent(); noSampling(); priority(20); description("Sensitive data - GDPR compliant trackers only") }
            routePII { toGroup(target); requirePIIConsent(); noSampling(); priority(18); description("PII events requiring explicit consent") }
            routeCategory(EventCategory.User) { toAll(); requireConsent(); mediumSampling(); priority(12); description("User behavior events requiring consent") }
            routeCategory(EventCategory.Marketing) { toAll(); requireConsent(); requirePIIConsent(); lightSampling(); priority(15); description("Marketing events requiring full consent") }
            piiProperty("email", target, 19, "Events with email - PII consent required")
            piiProperty("phone", target, 19, "Events with phone - PII consent required")
            piiProperty("ip_address", target, 19, "Events with IP address - PII consent required")
            piiProperty("location", target, 17, "Location data - strict PII consent required")
            piiProperty("latitude", target, 17, "GPS coordinates - strict PII consent required")
            routeCategory(EventCategory.Security) { toAll(); skipConsent(); noSampling(); priority(25); description("Security events - legitimate interest basis") }
            routeEssential { toAll(); skipConsent(); noSampling(); priority(24); description("Essential events - legitimate interest basis") }
            routeCategory(EventCategory.System) { toAll(); skipConsent(); lightSampling(); priority(8); description("System events - no personal data") }
            routeCategory(EventCategory.Technical) { toDevelopment(); skipConsent(); lightSampling(); onlyInDebug(); priority(6); description("Technical events - anonymous debug data") }
            routeDefault { toAll(); requireConsent(); mediumSampling(); priority(0); description("Default GDPR-compliant routing") }
        }
    }

    public fun applyStrict(builder: RoutingBuilder, compliantTrackers: List<String> = emptyList()) {
        apply(builder, compliantTrackers)
        val target = targetName(compliantTrackers)
        with(builder) {
            routeWithProperty("user_id") { toGroup(target); requirePIIConsent(); noSampling(); priority(22); description("User ID events - strict PII consent") }
            routeWithProperty("session_id") { toGroup(target); requireConsent(); lightSampling(); priority(13); description("Session tracking - consent required") }
            routeMatching(Regex("(click|view|scroll|interaction)_.*")) { toAll(); requireConsent(); mediumSampling(); priority(14); description("Behavioral events - consent required") }
        }
    }

    public fun applyMinimal(builder: RoutingBuilder, compliantTrackers: List<String> = emptyList()) {
        val target = prepare(builder, compliantTrackers)
        with(builder) {
            routePII { toGroup(target); requirePIIConsent(); priority(16); description("PII events - minimal GDPR compliance") }
            routeCategory(EventCategory.Sensitive) { toGroup(target); requirePIIConsent(); priority(15); description("Sensitive events - minimal GDPR compliance") }
            routeEssential { toAll(); skipConsent(); priority(20); description("Essential events - legitimate interest") }
            routeCategory(EventCategory.Security) { toAll(); skipConsent(); priority(18); description("Security events - legitimate interest") }
            routeDefault { toAll(); priority(0); description("Default minimal GDPR routing") }
        }
    }

    public fun applyCCPA(builder: RoutingBuilder, compliantTrackers: List<String> = emptyList()) {
        val target = prepare(builder, compliantTrackers)
        with(builder) {
            routePII { toGroup(target); requireConsent(); priority(15); description("PII events - CCPA compliance") }
            routeCategory(EventCategory.Marketing) { toAll(); requireConsent(); priority(12); description("Marketing events - CCPA compliance") }
            routeDefault { toAll(); priority(0); description("Default CCPA-compliant routing") }
        }
    }

    public fun applyForRegion(builder: RoutingBuilder, region: PrivacyRegion, compliantTrackers: List<String> = emptyList()) {
        when (region) {
            PrivacyRegion.EU -> applyStrict(builder, compliantTrackers)
            PrivacyRegion.UK -> apply(builder, compliantTrackers)
            PrivacyRegion.CALIFORNIA -> applyCCPA(builder, compliantTrackers)
            PrivacyRegion.GLOBAL -> applyMinimal(builder, compliantTrackers)
        }
    }

    private fun prepare(builder: RoutingBuilder, trackers: List<String>): String {
        if (trackers.isNotEmpty()) builder.defineGroup("gdpr_compliant", *trackers.toTypedArray())
        return targetName(trackers)
    }

    private fun targetName(trackers: List<String>): String = if (trackers.isEmpty()) "all" else "gdpr_compliant"

    private fun RoutingBuilder.piiProperty(name: String, target: String, rulePriority: Int, text: String) {
        routeWithProperty(name) { toGroup(target); requirePIIConsent(); noSampling(); priority(rulePriority); description(text) }
    }
}

public typealias GDPRDefaults = PrivacyDefaults

public object PerformanceDefaults {
    public fun apply(builder: RoutingBuilder) {
        with(builder) {
            sampledHighVolume(0.01, 15, "High volume events - aggressive sampling")
            sampledRegex("(click|tap|scroll|swipe|gesture)_.*", 0.01, 12, "UI interaction events - heavy sampling")
            sampledRegex("(mouse|pointer|hover)_.*", 0.001, 14, "Mouse/pointer events - extreme sampling")
            sampledRegex("scroll_.*", 0.01, 13, "Scroll events - heavy sampling")
            sampledRegex("(heartbeat|ping|alive)_.*", 0.05, 11, "Heartbeat events - moderate sampling")
            routeCategory(EventCategory.Technical) { toDevelopment(); onlyInDebug(); lightSampling(); priority(8); description("Performance events - debug only") }
            sampledRegex("(api|network|http|request)_.*", 0.1, 9, "Network events - light sampling")
            sampledRegex("(timer|interval|periodic)_.*", 0.02, 10, "Timer events - heavy sampling")
            sampledRegex("(frame|animation|render)_.*", 0.0001, 16, "Animation events - minimal sampling")
            sampledRegex("(purchase|payment|transaction|error)_.*", 1.0, 20, "Critical events - no sampling")
            routeEssential { toAll(); noSampling(); priority(25); description("Essential events - no sampling") }
            routeCategory(EventCategory.Security) { toAll(); noSampling(); priority(22); description("Security events - no sampling") }
            routeDefault { toAll(); mediumSampling(); priority(0); description("Default performance-optimized routing") }
        }
    }

    public fun applyMobileOptimized(builder: RoutingBuilder) {
        with(builder) {
            sampledHighVolume(0.005, 15, "Mobile: Ultra-aggressive sampling for high volume")
            sampledRegex("(touch|swipe|pinch|rotate|shake)_.*", 0.01, 12, "Mobile: Touch events with heavy sampling")
            routeWithProperty("location") { toAll(); sample(0.1); priority(13); description("Mobile: Location events - battery conscious") }
        }
        apply(builder)
    }

    public fun applyWebOptimized(builder: RoutingBuilder) {
        with(builder) {
            sampledRegex("(page_view|route_change|navigation)_.*", 0.1, 14, "Web: Navigation events with light sampling")
            sampledRegex("(focus|blur|resize|load)_.*", 0.05, 11, "Web: DOM events with moderate sampling")
        }
        apply(builder)
    }

    public fun applyServerOptimized(builder: RoutingBuilder) {
        with(builder) {
            sampledRegex("(request|response|endpoint)_.*", 0.5, 12, "Server: HTTP events with medium sampling")
            sampledRegex("(query|database|sql)_.*", 0.1, 11, "Server: Database events with light sampling")
            sampledRegex("(cache|redis|memcached)_.*", 0.01, 10, "Server: Cache events with heavy sampling")
        }
        apply(builder)
    }

    public fun applyLowLatency(builder: RoutingBuilder) {
        with(builder) {
            routeEssential { toAll(); noSampling(); priority(20); description("Low-latency: Essential events only") }
            sampledRegex("(error|failure|critical)_.*", 1.0, 18, "Low-latency: Critical events only")
            routeDefault { toAll(); sample(0.01); priority(0); description("Low-latency: Minimal default tracking") }
        }
    }

    public fun applyBandwidthConscious(builder: RoutingBuilder) {
        with(builder) {
            routeEssential { toAll(); noSampling(); priority(20); description("Bandwidth: Essential events only") }
            sampledRegex("(purchase|payment|signup|login)_.*", 1.0, 18, "Bandwidth: Business-critical events")
            sampledRegex("(error|crash|exception)_.*", 0.1, 15, "Bandwidth: Error events with sampling")
            routeDefault { toAll(); sample(0.001); priority(0); description("Bandwidth: Minimal default tracking") }
        }
    }

    public fun applyHighThroughput(builder: RoutingBuilder) {
        with(builder) {
            sampledHighVolume(0.0001, 15, "High-throughput: Ultra-minimal sampling")
            routeEssential { toAll(); sample(0.1); priority(20); description("High-throughput: Sampled essential events") }
            sampledRegex("error_.*", 0.01, 18, "High-throughput: Sampled error tracking")
            routeDefault { toAll(); sample(0.00001); priority(0); description("High-throughput: Extremely minimal default") }
        }
    }

    private fun RoutingBuilder.sampledRegex(pattern: String, rate: Double, rulePriority: Int, text: String) {
        routeMatching(Regex(pattern)) { toAll(); sample(rate); priority(rulePriority); description(text) }
    }

    private fun RoutingBuilder.sampledHighVolume(rate: Double, rulePriority: Int, text: String) {
        routeHighVolume { toAll(); sample(rate); priority(rulePriority); description(text) }
    }
}
