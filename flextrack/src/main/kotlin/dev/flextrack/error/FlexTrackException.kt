package dev.flextrack.error

/** Typed public failure root. It remains an [IllegalArgumentException] for Kotlin API compatibility. */
public sealed class FlexTrackException(
    message: String,
    public val code: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause) {
    override fun toString(): String = buildString {
        append(this@FlexTrackException::class.simpleName)
        code?.let { append("(").append(it).append(")") }
        append(": ").append(message)
        cause?.let { append("\nCaused by: ").append(it) }
    }
}

public class TrackerException(
    message: String,
    code: String? = null,
    cause: Throwable? = null,
    public val trackerId: String? = null,
    public val eventName: String? = null,
) : FlexTrackException(message, code, cause) {
    override fun toString(): String = super.toString() +
        (trackerId?.let { "\nTracker ID: $it" } ?: "") +
        (eventName?.let { "\nEvent: $it" } ?: "")
}

public class ConfigurationException(
    message: String,
    code: String? = null,
    cause: Throwable? = null,
    public val configurationType: String? = null,
    public val fieldName: String? = null,
) : FlexTrackException(message, code, cause)

public class RoutingException(
    message: String,
    code: String? = null,
    cause: Throwable? = null,
    public val eventName: String? = null,
    public val ruleName: String? = null,
) : FlexTrackException(message, code, cause)
