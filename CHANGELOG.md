# Changelog

## 1.2.0 - 2026-08-18

### Added

- Kotlin-native configuration and client builders.
- Smart, GDPR, CCPA, privacy-region, and performance routing presets.
- Versioned consent management and dynamic tracking context enrichment.
- Public pattern matching, sampling, and validation utilities.
- Thread-safe `RecordingTracker`/`MockTracker` and `NoOpTracker` implementations.
- Typed tracker capability, lifecycle, registry diagnostics, and public failures.
- Expanded package-level Flutter parity coverage with 168 tests.

### Changed

- Publishes from the Central Publisher Portal under
  `io.github.alirezat66:flextrack` with signed sources and Javadoc artifacts.
- Replaces authenticated GitHub Packages installation with public Maven Central
  installation.

### Compatibility

- Kotlin source packages and the Android namespace remain `dev.flextrack`.
- Existing `1.1.0` users only need to replace the Maven dependency coordinates.
- Core and Runtime Specifications remain `1.0.0`.

## 1.1.0 - 2026-08-18

### Added

- Debug-only structured Logcat diagnostics with `OFF`, `BASIC`, and `VERBOSE`
  policies.
- Production-style Compose sample using MVVM, Hilt, StateFlow, Navigation,
  and DataStore.
- Shared Runtime MVP fixtures and Flutter/Kotlin conformance coverage.
- On-device tests for durable queue recreation, malformed storage, concurrent
  enqueue, consent/network controls, and delivery UI state.
- Flutter–Kotlin parity documentation.

### Changed

- Serializes concurrent flush operations to prevent duplicate delivery.
- Returns immutable queue snapshots and defensively copies tracker IDs.
- Explains skipped routing decisions and property keys in debug logs.
- Guarantees tracker initialization before sample event dispatch.
- Updates AndroidX Test dependencies for Android 16 compatibility.

### Compatibility

- No migration is required from `1.0.1`.
- Maven coordinates remain `dev.flextrack:flextrack`.
- Core Specification remains `1.0.0`; Runtime Specification is `1.0.0`.

## 1.0.1 - 2026-08-17

### Changed

- Rebrands the public Kotlin packages and Android namespace from
  `dev.taghizadeh.flextrack` to `dev.flextrack`.
- Changes Maven coordinates to `dev.flextrack:flextrack:1.0.1`.
- Updates the sample application ID and imports to the new namespace.

### Migration

Replace imports beginning with `dev.taghizadeh.flextrack` with `dev.flextrack`.
No runtime behavior or Core Specification semantics changed.

## 1.0.0 - 2026-08-17

### Added

- Android library and sample module foundation.
- Maven publication metadata and local publishing support.
- FlexTrack Core Specification 1.0.0 contract baseline.
- CI quality gates for build, unit tests, lint, and publication verification.
- Immutable events, enrichment, consent-aware routing, and deterministic sampling.
- Coroutine-based tracker runtime with isolated delivery failures.
- In-memory and durable atomic-file offline queues with selective retry.
- Shared Flutter/Kotlin conformance test runner.
- Runnable Android sample and GitHub Packages release workflow.
