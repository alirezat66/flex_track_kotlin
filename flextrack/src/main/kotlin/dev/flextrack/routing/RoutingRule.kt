package dev.flextrack.routing

import dev.flextrack.event.FlexEvent
import dev.flextrack.event.originalEvent
import dev.flextrack.sampling.DeterministicSampler
import dev.flextrack.sampling.EventSampler

public data class ConsentState(
    val general: Boolean = false,
    val pii: Boolean = false,
)

public data class RoutingRule(
    val id: String? = null,
    val eventType: Class<out FlexEvent>? = null,
    val eventNameContains: String? = null,
    val eventNameRegex: Regex? = null,
    val category: EventCategory? = null,
    val hasProperty: String? = null,
    val propertyValue: Any? = null,
    val containsPII: Boolean? = null,
    val isHighVolume: Boolean? = null,
    val isEssential: Boolean? = null,
    val isDefault: Boolean = false,
    val targetGroup: TrackerGroup,
    val sampleRate: Double = 1.0,
    val requireConsent: Boolean = true,
    val requirePIIConsent: Boolean = false,
    val debugOnly: Boolean = false,
    val productionOnly: Boolean = false,
    val priority: Int = 0,
    val description: String? = null,
) {
    init {
        require(sampleRate in 0.0..1.0) { "sampleRate must be between 0 and 1" }
        require(!(debugOnly && productionOnly)) {
            "a rule cannot be both debug-only and production-only"
        }
    }

    public fun matches(event: FlexEvent, isDebugMode: Boolean = false): Boolean {
        if (mismatchReasons(event, isDebugMode).isNotEmpty()) return false
        return true
    }

    /** Human-readable condition failures for inspector and Logcat diagnostics. */
    public fun mismatchReasons(event: FlexEvent, isDebugMode: Boolean = false): List<String> = buildList {
        if (debugOnly && !isDebugMode) add("Rule is debug-only but debug mode is disabled")
        if (productionOnly && isDebugMode) add("Rule is production-only but debug mode is enabled")
        if (eventType != null && !eventType.isAssignableFrom(event.originalEvent().javaClass)) {
            add("Event type mismatch: expected ${eventType.simpleName}, got ${event.originalEvent().javaClass.simpleName}")
        }
        if (eventNameContains != null && !event.name.contains(eventNameContains)) {
            add("Event name '${event.name}' does not contain '$eventNameContains'")
        }
        if (eventNameRegex != null && !eventNameRegex.containsMatchIn(event.name)) {
            add("Event name '${event.name}' does not match /${eventNameRegex.pattern}/")
        }
        if (category != null && event.category != category) {
            add("Category mismatch: expected ${category.name}, got ${event.category?.name}")
        }
        if (hasProperty != null) {
            val properties = event.properties
            if (properties == null || !properties.containsKey(hasProperty)) {
                add("Missing property '$hasProperty'")
            } else if (propertyValue != null && properties[hasProperty] != propertyValue) {
                add("Property '$hasProperty' value mismatch")
            }
        }
        if (containsPII != null && event.containsPII != containsPII) add("PII flag mismatch")
        if (isHighVolume != null && event.isHighVolume != isHighVolume) add("High-volume flag mismatch")
        if (isEssential != null && event.isEssential != isEssential) add("Essential flag mismatch")
    }

    public fun passesConsent(event: FlexEvent, consent: ConsentState): Boolean {
        if (event.isEssential) return true
        if (requireConsent && !consent.general) return false
        if (requirePIIConsent && !consent.pii) return false
        if (event.requiresConsent && !consent.general) return false
        return true
    }

    public fun passesSampling(
        event: FlexEvent,
        sampler: EventSampler = DeterministicSampler,
    ): Boolean = sampler.shouldSample(event, sampleRate)
}
