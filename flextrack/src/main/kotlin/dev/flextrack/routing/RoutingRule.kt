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
        if (debugOnly && !isDebugMode) return false
        if (productionOnly && isDebugMode) return false
        if (eventType != null && !eventType.isAssignableFrom(event.originalEvent().javaClass)) return false
        if (eventNameContains != null && !event.name.contains(eventNameContains)) return false
        if (eventNameRegex != null && !eventNameRegex.containsMatchIn(event.name)) return false
        if (category != null && event.category != category) return false
        if (hasProperty != null) {
            val properties = event.properties ?: return false
            if (!properties.containsKey(hasProperty)) return false
            if (propertyValue != null && properties[hasProperty] != propertyValue) return false
        }
        if (containsPII != null && event.containsPII != containsPII) return false
        if (isHighVolume != null && event.isHighVolume != isHighVolume) return false
        if (isEssential != null && event.isEssential != isEssential) return false
        return true
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
