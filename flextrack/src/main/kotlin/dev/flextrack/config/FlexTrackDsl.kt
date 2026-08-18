package dev.flextrack.config

import android.content.Context
import dev.flextrack.context.TrackingContext
import dev.flextrack.event.EventTransformer
import dev.flextrack.event.EnrichedEvent
import dev.flextrack.event.FlexEvent
import dev.flextrack.event.TransformerPipeline
import dev.flextrack.logging.AndroidLogcatLogger
import dev.flextrack.logging.FlexTrackLogLevel
import dev.flextrack.logging.FlexTrackLogger
import dev.flextrack.logging.NoOpFlexTrackLogger
import dev.flextrack.routing.ConsentState
import dev.flextrack.routing.EventCategory
import dev.flextrack.routing.RoutingConfiguration
import dev.flextrack.routing.RoutingEngine
import dev.flextrack.routing.RoutingRule
import dev.flextrack.routing.TrackerGroup
import dev.flextrack.runtime.EventQueue
import dev.flextrack.runtime.FileEventQueue
import dev.flextrack.runtime.FlexTrackClient
import dev.flextrack.runtime.InMemoryEventQueue
import dev.flextrack.runtime.Tracker
import dev.flextrack.runtime.TrackerRegistry

@DslMarker
public annotation class FlexTrackDsl

/** Flutter-parity routing DSL with Kotlin-native block syntax. */
@FlexTrackDsl
public class RoutingBuilder {
    private val rules: MutableList<RoutingRule> = mutableListOf()
    private val groups: LinkedHashMap<String, TrackerGroup> = linkedMapOf()
    private val categories: LinkedHashMap<String, EventCategory> = linkedMapOf()
    private var defaultGroup: TrackerGroup? = null
    private var samplingEnabled: Boolean = true
    private var consentCheckingEnabled: Boolean = true
    private var debugMode: Boolean = false

    public fun defineGroup(name: String, vararg trackerIds: String) {
        require(name.isNotEmpty()) { "group name cannot be empty" }
        require(trackerIds.isNotEmpty()) { "group must contain at least one tracker ID" }
        require(trackerIds.none(String::isEmpty)) { "tracker ID cannot be empty" }
        groups[name] = TrackerGroup(name, trackerIds.toList())
    }

    public fun defineCategory(name: String) {
        require(name.isNotEmpty()) { "category name cannot be empty" }
        categories[name] = EventCategory(name)
    }

    public fun defaultGroup(group: TrackerGroup) { defaultGroup = group }

    public fun defaultGroup(name: String) {
        defaultGroup = requireNotNull(getGroup(name)) { "unknown group: $name" }
    }

    public fun sampling(enabled: Boolean) { samplingEnabled = enabled }
    public fun consentChecking(enabled: Boolean) { consentCheckingEnabled = enabled }
    public fun debugMode(enabled: Boolean) { debugMode = enabled }

    public inline fun <reified T : FlexEvent> route(noinline block: RuleBuilder.() -> Unit) {
        route(T::class.java, block)
    }

    public fun route(
        eventType: Class<out FlexEvent>,
        block: RuleBuilder.() -> Unit,
    ) {
        addRule(RuleCondition(eventType = eventType), block)
    }

    public fun routeNamed(pattern: String, block: RuleBuilder.() -> Unit) {
        require(pattern.isNotEmpty()) { "event name pattern cannot be empty" }
        addRule(RuleCondition(eventNameContains = pattern), block)
    }

    public fun routeMatching(pattern: Regex, block: RuleBuilder.() -> Unit) {
        addRule(RuleCondition(eventNameRegex = pattern), block)
    }

    public fun routeExact(eventName: String, block: RuleBuilder.() -> Unit) {
        require(eventName.isNotEmpty()) { "event name cannot be empty" }
        addRule(RuleCondition(eventNameRegex = Regex("^${Regex.escape(eventName)}$")), block)
    }

    public fun routeCategory(category: EventCategory, block: RuleBuilder.() -> Unit) {
        addRule(RuleCondition(category = category), block)
    }

    public fun routeCategory(name: String, block: RuleBuilder.() -> Unit) {
        val category = getCategory(name) ?: error("unknown category: $name")
        routeCategory(category, block)
    }

    public fun routeWithProperty(
        name: String,
        value: Any? = null,
        block: RuleBuilder.() -> Unit,
    ) {
        require(name.isNotEmpty()) { "property name cannot be empty" }
        addRule(RuleCondition(hasProperty = name, propertyValue = value), block)
    }

    public fun routePII(block: RuleBuilder.() -> Unit) =
        addRule(RuleCondition(containsPII = true), block)

    public fun routeHighVolume(block: RuleBuilder.() -> Unit) =
        addRule(RuleCondition(isHighVolume = true), block)

    public fun routeEssential(block: RuleBuilder.() -> Unit) =
        addRule(RuleCondition(isEssential = true), block)

    public fun routeDefault(block: RuleBuilder.() -> Unit) =
        addRule(RuleCondition(isDefault = true), block)

    public fun addRule(rule: RoutingRule) { rules += rule }
    public fun addRules(values: Iterable<RoutingRule>) { rules += values }
    public fun clearRules() { rules.clear() }
    public fun removeRulesWhere(predicate: (RoutingRule) -> Boolean) { rules.removeAll(predicate) }

    public fun applySmartDefaults() {
        routeCategory(EventCategory.Technical) {
            toDevelopment(); onlyInDebug(); lightSampling(); priority(8)
        }
        routeHighVolume { toAll(); heavySampling(); priority(5) }
        routeDefault { toAll() }
    }

    public fun build(): RoutingConfiguration {
        val builtRules = rules.toMutableList()
        if (builtRules.none(RoutingRule::isDefault) && defaultGroup == null) {
            builtRules += RoutingRule(
                id = "auto-default",
                isDefault = true,
                targetGroup = TrackerGroup.All,
                priority = -1000,
                description = "Auto-generated default rule",
            )
        }
        return RoutingConfiguration(
            rules = builtRules.sortedByDescending(RoutingRule::priority),
            customGroups = groups.toMap(),
            customCategories = categories.toMap(),
            defaultGroup = defaultGroup,
            enableSampling = samplingEnabled,
            enableConsentChecking = consentCheckingEnabled,
            isDebugMode = debugMode,
        )
    }

    public fun getGroup(name: String): TrackerGroup? = when (name) {
        "all" -> TrackerGroup.All
        "development" -> TrackerGroup.Development
        else -> groups[name]
    }

    public fun getCategory(name: String): EventCategory? = categories[name] ?: when (name) {
        "business" -> EventCategory.Business
        "user" -> EventCategory.User
        "technical" -> EventCategory.Technical
        "sensitive" -> EventCategory.Sensitive
        "marketing" -> EventCategory.Marketing
        "system" -> EventCategory.System
        "security" -> EventCategory.Security
        else -> null
    }

    public fun getAllGroups(): List<TrackerGroup> =
        listOf(TrackerGroup.All, TrackerGroup.Development) + groups.values

    public fun getAllCategories(): List<EventCategory> = listOf(
        EventCategory.Business,
        EventCategory.User,
        EventCategory.Technical,
        EventCategory.Sensitive,
        EventCategory.Marketing,
        EventCategory.System,
        EventCategory.Security,
    ) + categories.values

    public fun validate(): List<String> = build().validate()

    public fun getDebugInfo(): Map<String, Any> = mapOf(
        "rulesCount" to rules.size,
        "customGroupsCount" to groups.size,
        "customCategoriesCount" to categories.size,
        "hasDefaultGroup" to (defaultGroup != null),
        "enableSampling" to samplingEnabled,
        "enableConsentChecking" to consentCheckingEnabled,
        "isDebugMode" to debugMode,
        "rules" to rules.map(RoutingRule::toString),
        "customGroups" to groups.keys.toList(),
        "customCategories" to categories.keys.toList(),
    )

    private fun addRule(condition: RuleCondition, block: RuleBuilder.() -> Unit) {
        rules += RuleBuilder(this, condition).apply(block).build()
    }
}

internal data class RuleCondition(
    val eventType: Class<out FlexEvent>? = null,
    val eventNameContains: String? = null,
    val eventNameRegex: Regex? = null,
    val category: EventCategory? = null,
    val hasProperty: String? = null,
    val propertyValue: Any? = null,
    val containsPII: Boolean? = null,
    val isHighVolume: Boolean? = null,
    val isEssential: Boolean? = null,
    val isDefault: Boolean = false,
)

@FlexTrackDsl
public class RuleBuilder internal constructor(
    private val parent: RoutingBuilder,
    private val condition: RuleCondition,
) {
    private var target: TrackerGroup? = null
    private var sampleRate: Double = 1.0
    private var requiresConsent: Boolean = true
    private var requiresPIIConsent: Boolean = false
    private var debugOnly: Boolean = false
    private var productionOnly: Boolean = false
    private var priority: Int = 0
    private var id: String? = null
    private var description: String? = null

    public fun toAll() { target = TrackerGroup.All }
    public fun toDevelopment() { target = TrackerGroup.Development }

    public fun to(vararg trackerIds: String) {
        require(trackerIds.isNotEmpty()) { "cannot route to an empty tracker list" }
        require(trackerIds.none(String::isEmpty)) { "tracker ID cannot be empty" }
        target = TrackerGroup("custom-${trackerIds.contentHashCode()}", trackerIds.toList())
    }

    public fun toTracker(trackerId: String) {
        require(trackerId.isNotEmpty()) { "tracker ID cannot be empty" }
        to(trackerId)
    }

    public fun toGroup(group: TrackerGroup) { target = group }
    public fun toGroup(name: String) {
        target = requireNotNull(parent.getGroup(name)) { "unknown tracker group: $name" }
    }

    public fun sample(rate: Double) {
        require(rate in 0.0..1.0) { "sample rate must be between 0 and 1" }
        sampleRate = rate
    }

    public fun heavySampling() = sample(0.01)
    public fun lightSampling() = sample(0.1)
    public fun mediumSampling() = sample(0.5)
    public fun noSampling() = sample(1.0)
    public fun requireConsent() { requiresConsent = true }
    public fun skipConsent() { requiresConsent = false }
    public fun requirePIIConsent() { requiresPIIConsent = true }
    public fun onlyInDebug() { debugOnly = true; productionOnly = false }
    public fun onlyInProduction() { productionOnly = true; debugOnly = false }
    public fun priority(value: Int) { priority = value }

    public fun id(value: String) {
        require(value.isNotEmpty()) { "rule ID cannot be empty" }
        id = value
    }

    public fun description(value: String) {
        require(value.isNotEmpty()) { "description cannot be empty" }
        description = value
    }

    public fun essential() {
        skipConsent(); noSampling(); priority(10)
    }

    internal fun build(): RoutingRule {
        val destination = requireNotNull(target) { "route target is required" }
        return RoutingRule(
            id = id,
            eventType = condition.eventType,
            eventNameContains = condition.eventNameContains,
            eventNameRegex = condition.eventNameRegex,
            category = condition.category,
            hasProperty = condition.hasProperty,
            propertyValue = condition.propertyValue,
            containsPII = condition.containsPII,
            isHighVolume = condition.isHighVolume,
            isEssential = condition.isEssential,
            isDefault = condition.isDefault,
            targetGroup = destination,
            sampleRate = sampleRate,
            requireConsent = requiresConsent,
            requirePIIConsent = requiresPIIConsent,
            debugOnly = debugOnly,
            productionOnly = productionOnly,
            priority = priority,
            description = description ?: generatedDescription(destination),
        )
    }

    private fun generatedDescription(destination: TrackerGroup): String = buildString {
        append(
            when {
                condition.eventType != null -> "${condition.eventType.simpleName} events"
                condition.eventNameContains != null -> "events containing \"${condition.eventNameContains}\""
                condition.eventNameRegex != null -> "events matching /${condition.eventNameRegex.pattern}/"
                condition.category != null -> "${condition.category.name} events"
                condition.isDefault -> "default routing"
                else -> "events"
            },
        )
        append(" to ${destination.name}")
    }
}

/** One-place client configuration that preserves the manual API underneath. */
@FlexTrackDsl
public class FlexTrackConfigurationBuilder(private val context: Context? = null) {
    private val trackers: MutableList<Tracker> = mutableListOf()
    private val routingBuilder: RoutingBuilder = RoutingBuilder()
    private val transformers: TransformerPipeline = TransformerPipeline()
    private var queue: EventQueue? = null
    private var consentProvider: () -> ConsentState = { ConsentState() }
    private var onlineProvider: () -> Boolean = { true }
    private var logger: FlexTrackLogger = NoOpFlexTrackLogger
    private var autoStart: Boolean = true

    public fun tracker(tracker: Tracker) { trackers += tracker }
    public fun routing(block: RoutingBuilder.() -> Unit) { routingBuilder.apply(block) }
    public fun transformer(transformer: EventTransformer) { transformers.add(transformer) }
    public fun consent(provider: () -> ConsentState) { consentProvider = provider }
    public fun trackingContext(provider: () -> TrackingContext) {
        consentProvider = { provider().consentManager.summary.routingState() }
        transformers.add { event -> EnrichedEvent(event, provider().eventProperties()) }
    }
    public fun network(provider: () -> Boolean) { onlineProvider = provider }
    public fun inMemoryQueue() { queue = InMemoryEventQueue() }
    public fun persistentQueue(fileName: String = "flextrack-event-queue.json") {
        queue = FileEventQueue(requireNotNull(context) { "Android Context is required for a persistent queue" }, fileName)
    }
    public fun logging(level: FlexTrackLogLevel = FlexTrackLogLevel.BASIC) {
        logger = AndroidLogcatLogger(
            requireNotNull(context) { "Android Context is required for Logcat logging" },
            level,
        )
    }
    public fun logger(value: FlexTrackLogger) { logger = value }
    public fun autoStart(enabled: Boolean) { autoStart = enabled }

    public suspend fun build(): FlexTrackClient {
        val client = buildUnstarted()
        if (autoStart) client.start()
        return client
    }

    /** Builds synchronously for DI containers; the owner must call [FlexTrackClient.start]. */
    public fun buildUnstarted(): FlexTrackClient {
        require(trackers.isNotEmpty()) { "at least one tracker is required" }
        val client = FlexTrackClient(
            routingEngine = RoutingEngine(routingBuilder.build()),
            registry = TrackerRegistry(trackers),
            queue = queue ?: context?.let(::FileEventQueue) ?: InMemoryEventQueue(),
            transformers = transformers,
            consentProvider = consentProvider,
            onlineProvider = onlineProvider,
            logger = logger,
        )
        return client
    }
}

public suspend fun flexTrack(
    context: Context,
    block: FlexTrackConfigurationBuilder.() -> Unit,
): FlexTrackClient = FlexTrackConfigurationBuilder(context).apply(block).build()
