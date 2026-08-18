package dev.flextrack.utils

public enum class EventPatternCategory { USER_INTERACTION, NAVIGATION, BUSINESS, ERROR, PERFORMANCE, DEBUG, SYSTEM, NETWORK }
public sealed interface PropertyMatcher {
    public data object Exists : PropertyMatcher
    public data class Equals(val value: Any?) : PropertyMatcher
    public data class Pattern(val value: String) : PropertyMatcher
    public data class RegexPattern(val value: Regex) : PropertyMatcher
}
public data class PatternValidationResult(val isValid: Boolean, val error: String? = null)

public object PatternMatcher {
    private const val MAX_CACHE_SIZE = 100
    private val cache = linkedMapOf<String, Regex>()

    public fun matches(value: String, pattern: String): Boolean {
        if (pattern == "*" || pattern == value) return true
        val regex = pattern.replace("*", ".*").replace("?", ".")
        return cached("^$regex$").containsMatchIn(value)
    }
    public fun matches(value: String, regex: Regex): Boolean = regex.containsMatchIn(value)
    public fun matchesAny(value: String, patterns: Iterable<String>): Boolean = patterns.any { matches(value, it) }
    public fun matchesAnyRegex(value: String, patterns: Iterable<Regex>): Boolean = patterns.any { it.containsMatchIn(value) }
    public fun startsWithAny(value: String, prefixes: Iterable<String>): Boolean = prefixes.any(value::startsWith)
    public fun endsWithAny(value: String, suffixes: Iterable<String>): Boolean = suffixes.any(value::endsWith)
    public fun containsAny(value: String, fragments: Iterable<String>): Boolean = fragments.any(value::contains)
    public fun matchesProperty(properties: Map<String, Any?>?, name: String, matcher: PropertyMatcher = PropertyMatcher.Exists): Boolean {
        if (properties == null || name !in properties) return false
        val actual = properties[name]
        return when (matcher) {
            PropertyMatcher.Exists -> true
            is PropertyMatcher.Equals -> actual == matcher.value
            is PropertyMatcher.Pattern -> actual is String && matches(actual, matcher.value)
            is PropertyMatcher.RegexPattern -> actual is String && matcher.value.containsMatchIn(actual)
        }
    }
    public fun matchesAllProperties(properties: Map<String, Any?>?, matchers: Map<String, PropertyMatcher>): Boolean =
        properties != null && matchers.all { matchesProperty(properties, it.key, it.value) }
    public fun matchesAnyProperty(properties: Map<String, Any?>?, matchers: Map<String, PropertyMatcher>): Boolean =
        properties != null && matchers.any { matchesProperty(properties, it.key, it.value) }
    public fun categoryPattern(category: EventPatternCategory): Regex = cached(when (category) {
        EventPatternCategory.USER_INTERACTION -> "(click|tap|touch|swipe|scroll|input|select|focus|blur)_.*"
        EventPatternCategory.NAVIGATION -> "(page_view|navigate|route|screen|tab)_.*"
        EventPatternCategory.BUSINESS -> "(purchase|payment|signup|login|subscription|conversion)_.*"
        EventPatternCategory.ERROR -> "(error|exception|crash|failure|timeout)_.*"
        EventPatternCategory.PERFORMANCE -> "(load|render|response|latency|memory|cpu)_.*"
        EventPatternCategory.DEBUG -> "(debug|test|dev|trace|log)_.*"
        EventPatternCategory.SYSTEM -> "(system|health|heartbeat|status|config)_.*"
        EventPatternCategory.NETWORK -> "(api|http|request|response|network|download|upload)_.*"
    })
    public fun matchesCategory(value: String, category: EventPatternCategory): Boolean = categoryPattern(category).containsMatchIn(value)
    public fun validate(pattern: String): PatternValidationResult = try {
        Regex(if ('*' in pattern || '?' in pattern) "^${pattern.replace("*", ".*").replace("?", ".")}$" else pattern)
        PatternValidationResult(true)
    } catch (failure: IllegalArgumentException) { PatternValidationResult(false, "Invalid pattern: ${failure.message}") }
    @Synchronized private fun cached(pattern: String): Regex {
        cache[pattern]?.let { return it }
        if (cache.size >= MAX_CACHE_SIZE) cache.clear()
        return Regex(pattern, RegexOption.IGNORE_CASE).also { cache[pattern] = it }
    }
    @Synchronized public fun clearCache() { cache.clear() }
    @Synchronized public fun cacheStats(): Map<String, Int> = mapOf("size" to cache.size, "maxSize" to MAX_CACHE_SIZE)
}
