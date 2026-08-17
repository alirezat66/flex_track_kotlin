package dev.taghizadeh.flextrack.routing

import dev.taghizadeh.flextrack.event.FlexEvent

public class RoutingEngine(public val configuration: RoutingConfiguration) {
    public fun route(
        event: FlexEvent,
        consent: ConsentState = ConsentState(),
        availableTrackers: Set<String> = emptySet(),
    ): RoutingResult {
        val matchingRules = configuration.matchingRules(event)
        if (matchingRules.isEmpty()) {
            return RoutingResult(
                event = event,
                targetTrackers = emptyList(),
                appliedRules = emptyList(),
                skippedRules = emptyList(),
                warnings = listOf("No routing rules matched the event"),
            )
        }

        val applied = mutableListOf<RoutingRule>()
        val skipped = mutableListOf<SkippedRule>()
        val warnings = mutableListOf<String>()
        val targets = linkedSetOf<String>()
        var winningPriority: Int? = null

        for (rule in matchingRules) {
            if (winningPriority != null && rule.priority < winningPriority) break

            if (configuration.enableConsentChecking && !rule.passesConsent(event, consent)) {
                skipped += SkippedRule(rule, CONSENT_REJECTION)
                continue
            }
            if (configuration.enableSampling &&
                !rule.passesSampling(event, configuration.sampler)
            ) {
                skipped += SkippedRule(rule, "Event was sampled out")
                continue
            }

            val resolved = resolve(rule.targetGroup, availableTrackers)
            if (resolved.isEmpty()) {
                skipped += SkippedRule(rule, "No available trackers in target group")
                warnings += "Rule resolved to no available trackers"
                continue
            }

            if (winningPriority == null) winningPriority = rule.priority
            targets += resolved
            applied += rule
        }

        return RoutingResult(event, targets.toList(), applied, skipped, warnings)
    }

    public fun debug(
        event: FlexEvent,
        consent: ConsentState = ConsentState(),
        availableTrackers: Set<String> = emptySet(),
    ): RoutingDebugInfo {
        val result = route(event, consent, availableTrackers)
        val applied = result.appliedRules.toSet()
        val skipped = result.skippedRules.associate { it.rule to it.reason }
        val decisions = configuration.rules.map { rule ->
            val matches = rule.matches(event, configuration.isDebugMode)
            RuleDecision(
                rule = rule,
                matched = matches,
                applied = rule in applied,
                reason = when {
                    rule in skipped -> skipped[rule]
                    !matches -> "Rule conditions did not match"
                    rule !in applied -> "Lower priority tier was not evaluated"
                    else -> null
                },
            )
        }
        return RoutingDebugInfo(event, decisions, result)
    }

    private fun resolve(group: TrackerGroup, available: Set<String>): List<String> =
        if (group.includesAll) available.toList()
        else group.trackerIds.filter(available::contains)

    private companion object {
        const val CONSENT_REJECTION = "Consent requirements not met"
    }
}
