# Changelog

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
