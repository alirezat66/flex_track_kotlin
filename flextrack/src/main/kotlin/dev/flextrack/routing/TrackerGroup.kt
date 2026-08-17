package dev.flextrack.routing

import java.util.Collections

public class TrackerGroup(
    public val name: String,
    trackerIds: List<String>,
) {
    public val trackerIds: List<String> =
        Collections.unmodifiableList(trackerIds.distinct())

    public val includesAll: Boolean get() = ALL_TRACKERS in trackerIds

    init {
        require(name.isNotEmpty()) { "group name cannot be empty" }
    }

    override fun equals(other: Any?): Boolean =
        other is TrackerGroup && name == other.name && trackerIds == other.trackerIds

    override fun hashCode(): Int = 31 * name.hashCode() + trackerIds.hashCode()

    override fun toString(): String = "TrackerGroup($name: ${trackerIds.joinToString()})"

    public companion object {
        public const val ALL_TRACKERS: String = "*"
        public val All: TrackerGroup = TrackerGroup("all", listOf(ALL_TRACKERS))
        public val Development: TrackerGroup = TrackerGroup("development", listOf("console"))
    }
}
