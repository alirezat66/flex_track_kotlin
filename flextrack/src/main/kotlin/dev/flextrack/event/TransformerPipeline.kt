package dev.flextrack.event

/** Ordered, failure-isolated event transformation. */
public class TransformerPipeline {
    private val transformers: MutableList<EventTransformer> = mutableListOf()

    public fun add(transformer: EventTransformer) {
        transformers += transformer
    }

    public fun remove(transformer: EventTransformer): Boolean =
        transformers.remove(transformer)

    public fun clear() {
        transformers.clear()
    }

    public fun transform(event: FlexEvent): FlexEvent {
        var current = event
        transformers.toList().forEach { transformer ->
            current = runCatching { transformer.transform(current) }
                .getOrDefault(current)
        }
        return current
    }

    public val size: Int get() = transformers.size
}
