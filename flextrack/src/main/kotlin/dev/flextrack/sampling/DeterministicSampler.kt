package dev.flextrack.sampling

import dev.flextrack.event.FlexEvent

public fun interface EventSampler {
    public fun shouldSample(event: FlexEvent, sampleRate: Double): Boolean
}

/** Cross-platform FNV-1a sampler defined by FlexTrack Core Spec 1.0.0. */
public object DeterministicSampler : EventSampler {
    private const val FNV_OFFSET_BASIS: Long = 2_166_136_261L
    private const val FNV_PRIME: Long = 16_777_619L
    private const val UINT_32_MASK: Long = 0xffff_ffffL
    private const val UINT_32_RANGE: Double = 4_294_967_296.0

    override fun shouldSample(event: FlexEvent, sampleRate: Double): Boolean {
        if (event.isEssential) return true
        return shouldSample(samplingKey(event), sampleRate)
    }

    public fun shouldSample(identity: String, sampleRate: Double): Boolean = when {
        sampleRate <= 0.0 -> false
        sampleRate >= 1.0 -> true
        else -> stableHash(identity) / UINT_32_RANGE < sampleRate
    }

    public fun stableHash(input: String): Long {
        var hash = FNV_OFFSET_BASIS
        input.toByteArray(Charsets.UTF_8).forEach { byte ->
            hash = hash xor byte.toUByte().toLong()
            hash = (hash * FNV_PRIME) and UINT_32_MASK
        }
        return hash
    }

    public fun samplingKey(event: FlexEvent): String =
        event.userId?.takeIf(String::isNotEmpty)
            ?: event.sessionId?.takeIf(String::isNotEmpty)
            ?: event.name
}
