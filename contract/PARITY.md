# Flutter–Kotlin test parity

Reference: Flutter `v2.2.0` at
`f38c0618374272ec256bc2809fa70ad86c555631`.

## Shared behavioral contract

| Area | Shared input | Kotlin coverage |
| --- | --- | --- |
| Routing, consent, sampling, enrichment, debug decisions | `core_mvp_cases.json` | `CoreMvpConformanceTest` |
| Offline queueing, partial delivery, selective retry, FIFO, idempotency, lifecycle | `runtime_mvp_cases.json` | `RuntimeMvpConformanceTest` |

Both implementations must consume the same vendored JSON cases. A behavior
change requires updating the fixture version and both conformance reports.

## Platform-equivalent Kotlin coverage

- Transformer execution occurs once across retry.
- Concurrent flush calls cannot redeliver the same queued event.
- Offline flush performs no tracker delivery.
- Queue snapshots cannot mutate internal queue state.
- Partial tracker startup is rolled back and can be retried.
- Shutdown is idempotent.
- Route logs explain consent and unavailable-destination decisions.
- Property values require explicit verbose opt-in.
- Logcat is disabled for every level in non-debuggable builds.
- File queue tests cover recreation, identity/metadata, malformed data,
  invalid shape, concurrent enqueue, duplicate IDs, replacement order, and
  invalid read limits.
- Compose delivery tests cover rendered queue state, consent/network actions,
  track/flush actions, and loading-state duplicate prevention.

## Intentional platform differences

Flutter widget tests (`FlexClickTrack`, `FlexImpressionTrack`,
`FlexMountTrack`, and `FlexRouteTrack`) map to Compose UI interaction and
navigation tests, not line-for-line ports. Flutter's HTTP/WebSocket Inspector
maps to Android's debug-only structured Logcat output. Dart exception-type and
environment-detector tests do not apply directly to the Kotlin API.

## Execution boundary

JVM unit tests run without a device. `FileEventQueueInstrumentedTest` and
`DeliveryScreenTest` compile into Android test APKs and must also run on an
emulator or physical device in CI before release.
