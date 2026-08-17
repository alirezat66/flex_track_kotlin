package dev.flextrack.runtime

import dev.flextrack.event.FlexEvent

/** A destination adapter managed by [FlexTrackClient]. */
public interface Tracker {
    public val id: String

    public suspend fun start(): Unit = Unit

    public suspend fun track(event: FlexEvent)

    public suspend fun shutdown(): Unit = Unit
}
