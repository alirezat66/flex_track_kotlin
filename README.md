# FlexTrack Kotlin

Native Android/Kotlin implementation of FlexTrack: consent-aware analytics
routing with deterministic cross-SDK behavior.

The Kotlin SDK targets Android API 21+ and implements
[FlexTrack Core Specification 1.0.0](contract/README.md), shared with
[FlexTrack Flutter 2.2.0](https://pub.dev/packages/flex_track).

## Features

- Immutable events with stable IDs and UTC timestamps.
- Priority routing, tracker groups, consent gates, and deterministic sampling.
- Ordered enrichment with transformer failure isolation.
- Coroutine-native tracker lifecycle and concurrent delivery.
- Per-tracker failure isolation and selective retry.
- Durable, atomic offline queue for Android plus an in-memory test queue.
- Shared conformance fixtures verified against the Flutter implementation.

## Installation

The release workflow publishes version `1.1.0` through GitHub Packages. After
the release tag is pushed, add the repository and credentials to your Gradle
settings, then add the dependency:

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/alirezat66/flex_track_kotlin")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull
            password = providers.gradleProperty("gpr.key").orNull
        }
    }
}
```

```kotlin
dependencies {
    implementation("dev.flextrack:flextrack:1.1.0")
}
```

GitHub Packages requires a GitHub username and a token with `read:packages`.

## Quick start

Implement an event and a destination adapter:

```kotlin
class PurchaseEvent : FlexEvent() {
    override val name = "purchase"
    override val properties = mapOf("plan" to "pro")
}

class AnalyticsTracker : Tracker {
    override val id = "analytics"
    override suspend fun track(event: FlexEvent) {
        // Forward the event to your analytics vendor.
    }
}
```

Configure routing and use the lifecycle-aware client:

```kotlin
val group = TrackerGroup("product", listOf("analytics"))
val client = FlexTrackClient(
    routingEngine = RoutingEngine(
        RoutingConfiguration(
            rules = listOf(RoutingRule(targetGroup = group)),
        ),
    ),
    queue = FileEventQueue(applicationContext),
    consentProvider = { ConsentState(general = true) },
)

client.register(AnalyticsTracker())
client.start()
val result = client.track(PurchaseEvent())
client.flush()
client.shutdown()
```

### Debug Logcat

Enable structured diagnostics in debug builds without adding a logging
framework dependency:

```kotlin
logger = AndroidLogcatLogger(
    context = applicationContext,
    level = FlexTrackLogLevel.BASIC,
)
```

Filter Logcat by the `FlexTrack` tag. Routing, delivery, failures, queueing,
offline skips, retries, and flush summaries are reported. `BASIC` logs only
property keys. `VERBOSE` is an explicit debug-only opt-in that also prints
property values, so it must not be used with real PII. `AndroidLogcatLogger`
produces no output when the host application is not debuggable.

All client operations are `suspend` functions. Call them from an application-
owned coroutine scope. `FileEventQueue` stores failed/offline deliveries in the
app's private files directory and retries only the destinations still pending.

## Modules

- `flextrack`: publishable Android library (`AAR`).
- `sample`: Android application that consumes `flextrack` as a project dependency.
- `contract`: shared specification and deterministic Flutter/Kotlin fixtures.

### Sample application

The `sample` module is a production-style Compose application using MVVM,
Hilt, StateFlow, Navigation Compose, and DataStore. It mirrors the Flutter
example with functional screens for:

- Home event demonstrations and batch tracking
- E-commerce cart, purchase, and abandonment flows
- User registration, profile, feature, engagement, and churn journeys
- Consent, network, runtime status, flushing, and diagnostics
- Event enrichment with a live event log
- Persistent offline delivery and selective retry

## Build

```bash
./gradlew build
./gradlew :flextrack:publishToMavenLocal
```

## Release

The tag must match the version in `flextrack/build.gradle.kts`. Pushing a tag
such as `v1.1.0` verifies the library and publishes it to GitHub Packages:

```bash
git tag v1.1.0
git push origin v1.1.0
```

## License

MIT
