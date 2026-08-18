package dev.flextrack.routing

import dev.flextrack.event.FlexEvent
import dev.flextrack.sampling.DeterministicSampler
import dev.flextrack.sampling.EventSampler

public data class RoutingConfiguration(
    val rules: List<RoutingRule>,
    val customGroups: Map<String, TrackerGroup> = emptyMap(),
    val customCategories: Map<String, EventCategory> = emptyMap(),
    val defaultGroup: TrackerGroup? = null,
    val enableSampling: Boolean = true,
    val enableConsentChecking: Boolean = true,
    val isDebugMode: Boolean = false,
    val sampler: EventSampler = DeterministicSampler,
) {
    public fun getAllGroups(): List<TrackerGroup> =
        listOf(TrackerGroup.All, TrackerGroup.Development) + customGroups.values

    public fun getAllCategories(): List<EventCategory> = listOf(
        EventCategory.Business,
        EventCategory.User,
        EventCategory.Technical,
        EventCategory.Sensitive,
        EventCategory.Marketing,
        EventCategory.System,
        EventCategory.Security,
    ) + customCategories.values

    public fun validate(): List<String> {
        val issues = mutableListOf<String>()
        val duplicateIds = rules.mapNotNull(RoutingRule::id)
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateIds.isNotEmpty()) {
            issues += "Duplicate rule IDs found: ${duplicateIds.joinToString()}"
        }
        if (rules.none(RoutingRule::isDefault) && defaultGroup == null) {
            issues += "No default rule or default group specified"
        }
        val referencedGroups = rules.map { it.targetGroup.name }.toSet()
        val unreferencedGroups = customGroups.keys.filterNot(referencedGroups::contains)
        if (unreferencedGroups.isNotEmpty()) {
            issues += "Unreferenced custom groups: ${unreferencedGroups.joinToString()}"
        }
        return issues
    }

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
