package dev.flextrack.context

import java.time.Clock
import java.time.Instant

public enum class Environment {
    DEVELOPMENT, TESTING, STAGING, PRODUCTION;
    val enableDebug: Boolean get() = this == DEVELOPMENT || this == TESTING
    val enableSampling: Boolean get() = this == PRODUCTION || this == STAGING
    val strictConsent: Boolean get() = this == PRODUCTION
}

public class TrackingContext private constructor(
    public val userId: String?,
    public val sessionId: String?,
    public val deviceId: String?,
    userProperties: Map<String, Any?>,
    sessionProperties: Map<String, Any?>,
    public val consentManager: ConsentManager,
    public val environment: Environment,
    public val appVersion: String?,
    public val buildNumber: String?,
    public val createdAt: Instant,
) {
    public val userProperties: Map<String, Any?> = userProperties.toMap()
    public val sessionProperties: Map<String, Any?> = sessionProperties.toMap()
    public val isDebugMode: Boolean get() = environment == Environment.DEVELOPMENT
    public val isProduction: Boolean get() = environment == Environment.PRODUCTION
    public val isTesting: Boolean get() = environment == Environment.TESTING
    public val isUserIdentified: Boolean get() = !userId.isNullOrEmpty()
    public val hasActiveSession: Boolean get() = !sessionId.isNullOrEmpty()

    public fun withUserId(value: String?): TrackingContext = copy(userId = value)
    public fun withSessionId(value: String?): TrackingContext = copy(sessionId = value)
    public fun withUserProperties(values: Map<String, Any?>): TrackingContext =
        copy(userProperties = userProperties + values)
    public fun withSessionProperties(values: Map<String, Any?>): TrackingContext =
        copy(sessionProperties = sessionProperties + values)
    public fun withEnvironment(value: Environment): TrackingContext = copy(environment = value)
    public inline fun <reified T> getUserProperty(key: String): T? = userProperties[key] as? T
    public inline fun <reified T> getSessionProperty(key: String): T? = sessionProperties[key] as? T

    public fun eventProperties(): Map<String, Any?> = linkedMapOf<String, Any?>().apply {
        userId?.let { put("user_id", it) }
        sessionId?.let { put("session_id", it) }
        deviceId?.let { put("device_id", it) }
        appVersion?.let { put("app_version", it) }
        buildNumber?.let { put("build_number", it) }
        put("environment", environment.name.lowercase())
        put("context_created_at", createdAt.toString())
    }

    public fun toMap(): Map<String, Any?> = linkedMapOf(
        "userId" to userId, "sessionId" to sessionId, "deviceId" to deviceId,
        "userProperties" to userProperties, "sessionProperties" to sessionProperties,
        "environment" to environment.name.lowercase(), "appVersion" to appVersion,
        "buildNumber" to buildNumber, "createdAt" to createdAt.toString(),
        "isUserIdentified" to isUserIdentified, "hasActiveSession" to hasActiveSession,
        "consent" to consentManager.toMap(),
    )

    public fun validate(): List<String> = buildList {
        addAll(consentManager.validate())
        if (isProduction && !isUserIdentified) add("User not identified in production - anonymous tracking")
        if (isProduction && !hasActiveSession) add("No active session in production - session tracking recommended")
        if (isProduction && appVersion == null) add("App version not set - recommended for production tracking")
    }

    private fun copy(
        userId: String? = this.userId,
        sessionId: String? = this.sessionId,
        userProperties: Map<String, Any?> = this.userProperties,
        sessionProperties: Map<String, Any?> = this.sessionProperties,
        environment: Environment = this.environment,
    ): TrackingContext = TrackingContext(
        userId, sessionId, deviceId, userProperties, sessionProperties,
        consentManager, environment, appVersion, buildNumber, createdAt,
    )

    override fun equals(other: Any?): Boolean = other is TrackingContext &&
        userId == other.userId && sessionId == other.sessionId &&
        deviceId == other.deviceId && environment == other.environment
    override fun hashCode(): Int = arrayOf(userId, sessionId, deviceId, environment).contentHashCode()
    override fun toString(): String = "TrackingContext(user=${userId ?: "anonymous"}, session=${sessionId ?: "none"}, environment=${environment.name.lowercase()})"

    public companion object {
        public fun create(
            userId: String? = null, sessionId: String? = null, deviceId: String? = null,
            userProperties: Map<String, Any?> = emptyMap(), sessionProperties: Map<String, Any?> = emptyMap(),
            consentManager: ConsentManager = ConsentManager(), environment: Environment = Environment.PRODUCTION,
            appVersion: String? = null, buildNumber: String? = null, clock: Clock = Clock.systemUTC(),
        ): TrackingContext = TrackingContext(
            userId, sessionId, deviceId, userProperties, sessionProperties,
            consentManager, environment, appVersion, buildNumber, clock.instant(),
        )

        public fun development(userId: String? = null, sessionId: String? = null, consentManager: ConsentManager = ConsentManager()): TrackingContext =
            create(userId, sessionId, "dev-device", consentManager = consentManager, environment = Environment.DEVELOPMENT, appVersion = "dev", buildNumber = "debug")

        public fun testing(userId: String? = null, sessionId: String? = null): TrackingContext {
            val consent = ConsentManager().apply { grantAllConsents("test") }
            return create(userId, sessionId, "test-device", consentManager = consent, environment = Environment.TESTING, appVersion = "test", buildNumber = "0")
        }

        public fun fromMap(data: Map<String, Any?>): TrackingContext {
            val consent = ConsentManager().apply {
                @Suppress("UNCHECKED_CAST")
                loadFromMap(data["consent"] as? Map<String, Any?> ?: emptyMap())
            }
            fun map(key: String): Map<String, Any?> = (data[key] as? Map<*, *>)?.entries
                ?.filter { it.key is String }
                ?.associate { it.key as String to it.value }.orEmpty()
            val environment = (data["environment"] as? String)?.uppercase()?.let {
                runCatching { Environment.valueOf(it) }.getOrNull()
            } ?: Environment.PRODUCTION
            return create(
                userId = data["userId"] as? String, sessionId = data["sessionId"] as? String,
                deviceId = data["deviceId"] as? String, userProperties = map("userProperties"),
                sessionProperties = map("sessionProperties"), consentManager = consent,
                environment = environment, appVersion = data["appVersion"] as? String,
                buildNumber = data["buildNumber"] as? String,
            )
        }
    }
}
