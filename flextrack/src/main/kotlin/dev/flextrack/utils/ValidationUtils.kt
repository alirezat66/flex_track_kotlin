package dev.flextrack.utils

import dev.flextrack.routing.RoutingRule
import java.time.Instant

public data class ValidationResult(val isValid: Boolean, val isWarning: Boolean = false, val message: String? = null) {
    public companion object {
        fun valid() = ValidationResult(true)
        fun invalid(message: String) = ValidationResult(false, message = message)
        fun warning(message: String) = ValidationResult(true, true, message)
    }
}
public data class ValidationGroup(val name: String, val trackerIds: List<String>)
public data class ValidationRuleData(
    val id: String?, val sampleRate: Double, val priority: Int,
    val debugOnly: Boolean, val productionOnly: Boolean, val isDefault: Boolean,
    val targetGroup: ValidationGroup?,
) {
    public constructor(rule: RoutingRule) : this(
        rule.id, rule.sampleRate, rule.priority, rule.debugOnly, rule.productionOnly,
        rule.isDefault, ValidationGroup(rule.targetGroup.name, rule.targetGroup.trackerIds),
    )
}
public data class ConsentValidationData(
    val isProduction: Boolean, val hasAnyConsent: Boolean, val hasPIIConsent: Boolean,
    val tracksPII: Boolean, val consentVersion: String? = null,
)
public data class ValidationTracker(val id: String, val name: String)
public data class SetupValidationData(
    val trackers: List<ValidationTracker>, val routingRules: List<ValidationRuleData>,
    val consent: ConsentValidationData,
)

public object ValidationUtils {
    private val trackerPattern = Regex("^[a-zA-Z0-9_-]+$")
    private val eventPattern = Regex("^[a-zA-Z0-9_.-]+$")
    private val propertyPattern = Regex("^[a-zA-Z0-9_]+$")
    private val reservedIds = setOf("all", "none", "default", "system")
    private val reservedKeys = setOf("timestamp", "event_name", "user_id", "session_id", "device_id", "app_version", "platform", "environment")

    public fun trackerId(value: String?): ValidationResult = when {
        value.isNullOrEmpty() -> ValidationResult.invalid("Tracker ID cannot be null or empty")
        value.length > 50 -> ValidationResult.invalid("Tracker ID cannot exceed 50 characters")
        !trackerPattern.matches(value) -> ValidationResult.invalid("Tracker ID can only contain letters, numbers, underscores, and hyphens")
        value.lowercase() in reservedIds -> ValidationResult.invalid("Tracker ID '$value' is reserved")
        else -> ValidationResult.valid()
    }
    public fun eventName(value: String?): ValidationResult = when {
        value.isNullOrEmpty() -> ValidationResult.invalid("Event name cannot be null or empty")
        value.length > 100 -> ValidationResult.invalid("Event name cannot exceed 100 characters")
        !eventPattern.matches(value) -> ValidationResult.invalid("Event name can only contain letters, numbers, underscores, dots, and hyphens")
        !value.first().isLetter() -> ValidationResult.invalid("Event name must start with a letter")
        else -> ValidationResult.valid()
    }
    public fun propertyKey(value: String): ValidationResult = when {
        value.isEmpty() -> ValidationResult.invalid("Property key cannot be empty")
        value.length > 30 -> ValidationResult.invalid("Property key cannot exceed 30 characters")
        !propertyPattern.matches(value) -> ValidationResult.invalid("Property key can only contain letters, numbers, and underscores")
        value.lowercase() in reservedKeys -> ValidationResult.invalid("Property key '$value' is reserved")
        else -> ValidationResult.valid()
    }
    public fun propertyValue(value: Any?): ValidationResult = when {
        value !is String && value !is Number && value !is Boolean && value !is Instant -> ValidationResult.invalid("Property value must be String, number, bool, or Instant")
        value is String && value.length > 1000 -> ValidationResult.invalid("String property value cannot exceed 1000 characters")
        value is Double && !value.isFinite() -> ValidationResult.invalid("Numeric property value cannot be NaN or infinite")
        value is Float && !value.isFinite() -> ValidationResult.invalid("Numeric property value cannot be NaN or infinite")
        else -> ValidationResult.valid()
    }
    public fun eventProperties(values: Map<String, Any?>?): ValidationResult {
        if (values == null) return ValidationResult.valid()
        if (values.size > 50) return ValidationResult.invalid("Event cannot have more than 50 properties")
        values.forEach { (key, value) ->
            propertyKey(key).takeUnless { it.isValid }?.let { return ValidationResult.invalid("Invalid property key '$key': ${it.message}") }
            propertyValue(value).takeUnless { it.isValid }?.let { return ValidationResult.invalid("Invalid property value for '$key': ${it.message}") }
        }
        return ValidationResult.valid()
    }
    public fun userId(value: String?): ValidationResult = when {
        value.isNullOrEmpty() -> ValidationResult.valid()
        value.length > 100 -> ValidationResult.invalid("User ID cannot exceed 100 characters")
        value.any { it == '\n' || it == '\r' || it == '\t' } -> ValidationResult.invalid("User ID cannot contain newlines or tabs")
        else -> ValidationResult.valid()
    }
    public fun sessionId(value: String?): ValidationResult = when {
        value.isNullOrEmpty() -> ValidationResult.valid()
        value.length > 100 -> ValidationResult.invalid("Session ID cannot exceed 100 characters")
        !trackerPattern.matches(value) -> ValidationResult.invalid("Session ID can only contain letters, numbers, underscores, and hyphens")
        else -> ValidationResult.valid()
    }
    public fun sampleRate(value: Double): ValidationResult = when {
        !value.isFinite() -> ValidationResult.invalid("Sample rate must be a valid number")
        value < 0 -> ValidationResult.invalid("Sample rate cannot be negative")
        value > 1 -> ValidationResult.invalid("Sample rate cannot exceed 1.0")
        else -> ValidationResult.valid()
    }
    public fun priority(value: Int): ValidationResult = if (value in -1000..1000) ValidationResult.valid() else ValidationResult.invalid("Rule priority must be between -1000 and 1000")
    public fun trackerGroup(name: String, ids: List<String>): ValidationResult {
        trackerId(name).takeUnless { it.isValid }?.let { return ValidationResult.invalid("Invalid group name: ${it.message}") }
        if (ids.isEmpty()) return ValidationResult.invalid("Tracker group must contain at least one tracker ID")
        if (ids.size > 20) return ValidationResult.invalid("Tracker group cannot contain more than 20 trackers")
        ids.filterNot { it == "*" }.forEach { id -> trackerId(id).takeUnless { it.isValid }?.let { return ValidationResult.invalid("Invalid tracker ID '$id': ${it.message}") } }
        if (ids.distinct().size != ids.size) return ValidationResult.invalid("Tracker group contains duplicate tracker IDs")
        return ValidationResult.valid()
    }
    public fun routingConfiguration(rules: List<ValidationRuleData>): ValidationResult {
        if (rules.isEmpty()) return ValidationResult.warning("No routing rules defined - events may not be tracked")
        if (rules.size > 100) return ValidationResult.invalid("Too many routing rules (max 100)")
        val ids = rules.mapNotNull { it.id }; if (ids.distinct().size != ids.size) return ValidationResult.invalid("Duplicate rule IDs found")
        if (rules.none { it.isDefault }) return ValidationResult.warning("No default routing rule - unmatched events may not be tracked")
        rules.forEach { rule -> routingRule(rule).takeUnless { it.isValid }?.let { return ValidationResult.invalid("Invalid routing rule: ${it.message}") } }
        return ValidationResult.valid()
    }
    public fun routingRule(rule: ValidationRuleData): ValidationResult {
        rule.id?.let { trackerId(it).takeUnless { result -> result.isValid }?.let { return ValidationResult.invalid("Invalid rule ID: ${it.message}") } }
        sampleRate(rule.sampleRate).takeUnless { it.isValid }?.let { return it }
        priority(rule.priority).takeUnless { it.isValid }?.let { return it }
        rule.targetGroup?.let { trackerGroup(it.name, it.trackerIds).takeUnless { result -> result.isValid }?.let { return ValidationResult.invalid("Invalid target group: ${it.message}") } }
        if (rule.debugOnly && rule.productionOnly) return ValidationResult.invalid("Rule cannot be both debug-only and production-only")
        return ValidationResult.valid()
    }
    public fun consent(data: ConsentValidationData): ValidationResult = when {
        data.tracksPII && !data.hasPIIConsent -> ValidationResult.invalid("PII consent required when tracking personally identifiable information")
        data.isProduction && !data.hasAnyConsent -> ValidationResult.warning("No consent configured in production environment")
        data.hasAnyConsent && data.consentVersion == null -> ValidationResult.warning("Consent version not set - recommended for compliance tracking")
        else -> ValidationResult.valid()
    }
    public fun setup(data: SetupValidationData): List<ValidationResult> = buildList {
        if (data.trackers.isEmpty()) add(ValidationResult.invalid("At least one tracker must be registered"))
        data.trackers.forEach { tracker -> trackerId(tracker.id).takeUnless { it.isValid }?.let { add(ValidationResult.invalid("Invalid tracker '${tracker.name}': ${it.message}")) } }
        add(routingConfiguration(data.routingRules)); add(consent(data.consent))
    }.filter { !it.isValid || it.isWarning }
}
