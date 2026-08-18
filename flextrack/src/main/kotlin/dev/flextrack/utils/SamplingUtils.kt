package dev.flextrack.utils

import dev.flextrack.event.FlexEvent
import dev.flextrack.sampling.DeterministicSampler
import java.time.Clock
import java.time.Duration
import java.util.Random

public data class RateValidation(val isValid: Boolean, val error: String? = null)
public data class SamplingStats(val totalEvents: Int, val sampledEvents: Int) {
    val droppedEvents: Int get() = totalEvents - sampledEvents
    val actualSampleRate: Double get() = if (totalEvents == 0) 0.0 else sampledEvents.toDouble() / totalEvents
    val dropRate: Double get() = 1.0 - actualSampleRate
}
public enum class SamplingType { RANDOM, DETERMINISTIC, TIME_BASED, ADAPTIVE, BACKOFF }
public data class SamplingConfig(
    val type: SamplingType, val sampleRate: Double = 1.0,
    val interval: Duration? = null, val window: Duration? = null,
    val targetEventsPerWindow: Int? = null, val backoffFactor: Double? = null,
) {
    public companion object {
        fun random(rate: Double) = SamplingConfig(SamplingType.RANDOM, rate)
        fun deterministic(rate: Double) = SamplingConfig(SamplingType.DETERMINISTIC, rate)
        fun timeBased(interval: Duration) = SamplingConfig(SamplingType.TIME_BASED, interval = interval)
        fun adaptive(window: Duration, target: Int) = SamplingConfig(SamplingType.ADAPTIVE, window = window, targetEventsPerWindow = target)
        fun backoff(rate: Double, factor: Double = 0.5) = SamplingConfig(SamplingType.BACKOFF, rate, backoffFactor = factor)
    }
}

public object SamplingUtils {
    private var seed: Long = System.currentTimeMillis()
    private var random = Random(seed)
    @Synchronized public fun sample(rate: Double): Boolean = when { rate >= 1 -> true; rate <= 0 -> false; else -> random.nextDouble() < rate }
    public fun deterministic(key: String, rate: Double): Boolean = when { rate >= 1 -> true; rate <= 0 -> false; else -> DeterministicSampler.stableHash(key) / 4_294_967_296.0 < rate }
    public fun key(event: FlexEvent): String = event.userId?.takeIf(String::isNotEmpty) ?: event.sessionId?.takeIf(String::isNotEmpty) ?: event.name
    public fun byUser(userId: String?, rate: Double): Boolean = userId?.takeIf(String::isNotEmpty)?.let { deterministic(it, rate) } ?: sample(rate)
    public fun bySession(sessionId: String?, rate: Double): Boolean = sessionId?.takeIf(String::isNotEmpty)?.let { deterministic(it, rate) } ?: sample(rate)
    public fun byEventName(name: String, rate: Double): Boolean = deterministic(name, rate)
    public fun byTime(interval: Duration, clock: Clock = Clock.systemUTC()): Boolean = interval.toMillis() <= 0 || clock.millis() % interval.toMillis() == 0L
    public fun withBackoff(count: Int, baseRate: Double, factor: Double = 0.5): Boolean = sample((baseRate * Math.pow(factor, (count - 1).coerceAtLeast(0).toDouble())).coerceIn(0.0, 1.0))
    public fun includeInReservoir(count: Int, size: Int): Boolean = count <= size || sample(size.toDouble() / count)
    public fun adaptiveRate(count: Int, target: Int): Double = if (count <= target) 1.0 else (target.toDouble() / count).coerceIn(0.001, 1.0)
    public fun weighted(weights: Map<String, Double>, eventType: String): Boolean = sample(weights[eventType] ?: 1.0)
    public fun bucket(identifier: String, count: Int): Int = if (count <= 0) 0 else (DeterministicSampler.stableHash(identifier).toLong() % count).toInt()
    public fun inBuckets(identifier: String, count: Int, targets: Collection<Int>): Boolean = bucket(identifier, count) in targets
    @Synchronized public fun setSeed(value: Long) { seed = value; random = Random(value) }
    @Synchronized public fun getSeed(): Long = seed
    @Synchronized public fun resetSeed() = setSeed(System.currentTimeMillis())
    public fun validateRate(rate: Double): RateValidation = when {
        !rate.isFinite() -> RateValidation(false, "Sample rate must be a valid number")
        rate < 0 -> RateValidation(false, "Sample rate cannot be negative: $rate")
        rate > 1 -> RateValidation(false, "Sample rate cannot exceed 1.0: $rate")
        else -> RateValidation(true)
    }
    public fun percentageToRate(value: Double): Double = (value / 100).coerceIn(0.0, 1.0)
    public fun rateToPercentage(value: Double): Double = (value * 100).coerceIn(0.0, 100.0)
    public fun stats(results: List<Boolean>): SamplingStats = SamplingStats(results.size, results.count { it })
    public fun strategy(config: SamplingConfig, clock: Clock = Clock.systemUTC()): SamplingStrategy = SamplingStrategy(config, clock)
}

public class SamplingStrategy(private val config: SamplingConfig, private val clock: Clock = Clock.systemUTC()) {
    private val counts = mutableMapOf<String, Int>(); private val last = mutableMapOf<String, Long>()
    @Synchronized public fun shouldSample(eventName: String, userId: String? = null, sessionId: String? = null): Boolean = when (config.type) {
        SamplingType.RANDOM -> SamplingUtils.sample(config.sampleRate)
        SamplingType.DETERMINISTIC -> SamplingUtils.deterministic(userId ?: sessionId ?: eventName, config.sampleRate)
        SamplingType.TIME_BASED -> config.interval?.let { SamplingUtils.byTime(it, clock) } ?: false
        SamplingType.ADAPTIVE -> { updateWindow(eventName); SamplingUtils.sample(SamplingUtils.adaptiveRate(counts[eventName] ?: 0, config.targetEventsPerWindow ?: 100)) }
        SamplingType.BACKOFF -> { val count = counts.getOrDefault(eventName, 0); counts[eventName] = count + 1; SamplingUtils.withBackoff(count, config.sampleRate, config.backoffFactor ?: 0.5) }
    }
    private fun updateWindow(name: String) { val now = clock.millis(); val window = config.window?.toMillis() ?: 60_000; if (last[name] == null || now - last.getValue(name) > window) counts[name] = 0; counts[name] = counts.getOrDefault(name, 0) + 1; last[name] = now }
    @Synchronized public fun reset() { counts.clear(); last.clear() }
}
