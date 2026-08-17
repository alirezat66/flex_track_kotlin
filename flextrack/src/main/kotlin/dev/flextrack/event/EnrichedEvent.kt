package dev.flextrack.event

import dev.flextrack.routing.EventCategory
import dev.flextrack.routing.TrackerGroup
import java.util.Collections

/** Adds properties without changing the identity or metadata of [original]. */
public class EnrichedEvent(
    public val original: FlexEvent,
    extraProperties: Map<String, Any?>,
) : FlexEvent(original.eventId, original.timestamp) {
    public val extraProperties: Map<String, Any?> =
        Collections.unmodifiableMap(LinkedHashMap(extraProperties))

    override val name: String get() = original.name
    override val properties: Map<String, Any?> =
        Collections.unmodifiableMap(
            LinkedHashMap<String, Any?>().apply {
                original.properties?.let(::putAll)
                putAll(extraProperties)
            },
        )
    override val category: EventCategory? get() = original.category
    override val preferredGroup: TrackerGroup? get() = original.preferredGroup
    override val containsPII: Boolean get() = original.containsPII
    override val requiresConsent: Boolean get() = original.requiresConsent
    override val isHighVolume: Boolean get() = original.isHighVolume
    override val isEssential: Boolean get() = original.isEssential
    override val userId: String? get() = original.userId
    override val sessionId: String? get() = original.sessionId
}

public fun interface EventTransformer {
    public fun transform(event: FlexEvent): FlexEvent
}

internal tailrec fun FlexEvent.originalEvent(): FlexEvent =
    if (this is EnrichedEvent) original.originalEvent() else this
