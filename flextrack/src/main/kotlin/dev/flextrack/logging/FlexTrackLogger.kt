package dev.flextrack.logging

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log

public enum class FlexTrackLogLevel { OFF, BASIC, VERBOSE }

/** Logging boundary. Implementations must never throw into analytics delivery. */
public fun interface FlexTrackLogger {
    public fun log(message: String)
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
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
            level != FlexTrackLogLevel.OFF

    override fun log(message: String) {
        if (!enabled) return
        runCatching { Log.d(tag, message) }
    }
}

internal fun FlexTrackLogger.safeLog(message: () -> String) {
    runCatching { log(message()) }
}

/** Only the debuggable Android logger can opt into event values. */
internal fun FlexTrackLogger.includesPropertyValues(): Boolean =
    this is AndroidLogcatLogger && level == FlexTrackLogLevel.VERBOSE
