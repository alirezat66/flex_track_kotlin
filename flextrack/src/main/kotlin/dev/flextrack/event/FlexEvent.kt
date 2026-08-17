package dev.flextrack.event

import dev.flextrack.routing.EventCategory
import dev.flextrack.routing.TrackerGroup
import java.time.Instant
import java.util.UUID

/** One immutable analytics event occurrence. */
public abstract class FlexEvent(
    public val eventId: String = UUID.randomUUID().toString(),
    public val timestamp: Instant = Instant.now(),
) {
    init {
        require(eventId.isNotEmpty()) { "eventId cannot be empty" }
    }

    public abstract val name: String
    public abstract val properties: Map<String, Any?>?

    public open val category: EventCategory? = null
    public open val preferredGroup: TrackerGroup? = null
    public open val containsPII: Boolean = false
    public open val requiresConsent: Boolean = true
    public open val isHighVolume: Boolean = false
    public open val isEssential: Boolean = false
    public open val userId: String? = null
    public open val sessionId: String? = null

    public fun toMap(): Map<String, Any?> = linkedMapOf(
        "eventId" to eventId,
        "name" to name,
        "properties" to properties,
        "category" to category?.name,
        "preferredGroup" to preferredGroup?.name,
        "containsPII" to containsPII,
        "requiresConsent" to requiresConsent,
        "isHighVolume" to isHighVolume,
        "isEssential" to isEssential,
        "timestamp" to timestamp.toString(),
        "userId" to userId,
        "sessionId" to sessionId,
    )
}
