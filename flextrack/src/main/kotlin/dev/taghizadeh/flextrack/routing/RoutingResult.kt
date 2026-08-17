package dev.taghizadeh.flextrack.routing

import dev.taghizadeh.flextrack.event.FlexEvent

public data class SkippedRule(
    val rule: RoutingRule,
    val reason: String,
)

public data class RoutingResult(
    val event: FlexEvent,
    val targetTrackers: List<String>,
    val appliedRules: List<RoutingRule>,
    val skippedRules: List<SkippedRule>,
    val warnings: List<String>,
) {
    val willBeTracked: Boolean get() = targetTrackers.isNotEmpty()
    val hasIssues: Boolean get() = warnings.isNotEmpty() || skippedRules.isNotEmpty()
}

public data class RuleDecision(
    val rule: RoutingRule,
    val matched: Boolean,
    val applied: Boolean,
    val reason: String?,
)

public data class RoutingDebugInfo(
    val event: FlexEvent,
    val decisions: List<RuleDecision>,
    val routingResult: RoutingResult,
)
