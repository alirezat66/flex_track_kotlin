# FlexTrack Kotlin

Native Android/Kotlin implementation of FlexTrack: consent-aware analytics
routing with deterministic cross-SDK behavior.

The Kotlin SDK targets Android API 21+ and implements
[FlexTrack Core Specification 1.0.0](contract/README.md), shared with
[FlexTrack Flutter 2.1.0](https://pub.dev/packages/flex_track).

## Features

- Immutable events with stable IDs and UTC timestamps.
- Priority routing, tracker groups, consent gates, and deterministic sampling.
- Ordered enrichment with transformer failure isolation.
- Coroutine-native tracker lifecycle and concurrent delivery.
- Per-tracker failure isolation and selective retry.
- Durable, atomic offline queue for Android plus an in-memory test queue.
- Shared conformance fixtures verified against the Flutter implementation.

## Installation

The release workflow publishes version `1.0.1` through GitHub Packages. After
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
    implementation("dev.flextrack:flextrack:1.0.1")
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

All client operations are `suspend` functions. Call them from an application-
owned coroutine scope. `FileEventQueue` stores failed/offline deliveries in the
app's private files directory and retries only the destinations still pending.

## Modules

- `flextrack`: publishable Android library (`AAR`).
- `sample`: Android application that consumes `flextrack` as a project dependency.
- `contract`: shared specification and deterministic Flutter/Kotlin fixtures.

## Build

```bash
./gradlew build
./gradlew :flextrack:publishToMavenLocal
```

## Release

The tag must match the version in `flextrack/build.gradle.kts`. Pushing a tag
such as `v1.0.1` verifies the library and publishes it to GitHub Packages:

```bash
git tag v1.0.1
git push origin v1.0.1
```

## License

MIT
