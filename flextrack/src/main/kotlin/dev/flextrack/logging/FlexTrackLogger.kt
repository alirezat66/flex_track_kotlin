package dev.flextrack.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

public enum class FlexTrackLogLevel { OFF, BASIC, VERBOSE }

/** Logging boundary. Implementations must never throw into analytics delivery. */
public fun interface FlexTrackLogger {
    public fun log(message: String)

    /** Explicit opt-in for payload values. Keep false for production loggers. */
    public val includesPropertyValues: Boolean get() = false
}

public object NoOpFlexTrackLogger : FlexTrackLogger {
    override fun log(message: String): Unit = Unit
}

/** Privacy-safe Logcat output that is forcibly disabled for non-debuggable apps. */
public class AndroidLogcatLogger(
    context: Context,
    public val level: FlexTrackLogLevel = FlexTrackLogLevel.BASIC,
    private val tag: String = "FlexTrack",
) : FlexTrackLogger {
    private val enabled: Boolean =
        shouldEnableLogcat(
            isDebuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            level = level,
        )

    override val includesPropertyValues: Boolean
        get() = enabled && level == FlexTrackLogLevel.VERBOSE

    override fun log(message: String) {
        if (!enabled) return
        runCatching { Log.d(tag, message) }
    }
}

internal fun FlexTrackLogger.safeLog(message: () -> String) {
    runCatching { log(message()) }
}

internal fun shouldEnableLogcat(
    isDebuggable: Boolean,
    level: FlexTrackLogLevel,
): Boolean = isDebuggable && level != FlexTrackLogLevel.OFF
