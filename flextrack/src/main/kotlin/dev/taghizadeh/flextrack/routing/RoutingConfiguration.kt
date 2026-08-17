package dev.taghizadeh.flextrack.routing

import dev.taghizadeh.flextrack.event.FlexEvent
import dev.taghizadeh.flextrack.sampling.DeterministicSampler
import dev.taghizadeh.flextrack.sampling.EventSampler

public data class RoutingConfiguration(
    val rules: List<RoutingRule>,
    val customGroups: Map<String, TrackerGroup> = emptyMap(),
    val defaultGroup: TrackerGroup? = null,
    val enableSampling: Boolean = true,
    val enableConsentChecking: Boolean = true,
    val isDebugMode: Boolean = false,
    val sampler: EventSampler = DeterministicSampler,
) {
    public fun matchingRules(event: FlexEvent): List<RoutingRule> {
        val matches = rules
            .filter { it.matches(event, isDebugMode) }
            .sortedByDescending(RoutingRule::priority)
        if (matches.isNotEmpty()) return matches

        rules.firstOrNull(RoutingRule::isDefault)?.let { return listOf(it) }
        return defaultGroup?.let {
            listOf(
                RoutingRule(
                    isDefault = true,
                    targetGroup = it,
                    description = "Fallback default rule",
                ),
            )
        }.orEmpty()
    }
}
