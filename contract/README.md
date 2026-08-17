# FlexTrack shared contract

This directory contains the language-neutral inputs used to keep the Flutter
and Kotlin SDKs behaviorally compatible.

- Core specification: `1.0.0`
- Fixture version: `1.0.0`
- Flutter reference: `flex_track` `v2.1.0`
- Flutter reference commit: `78d7f46c2479b9680268ab794443375e3959d441`

Canonical sources:

- [Core MVP specification](https://github.com/alirezat66/flex_track/blob/v2.1.0/doc/core-mvp-specification.md)
- [Conformance runner contract](https://github.com/alirezat66/flex_track/blob/v2.1.0/doc/conformance.md)
- [Flutter fixtures](https://github.com/alirezat66/flex_track/tree/v2.1.0/test/fixtures/conformance)

The JSON files in this directory are vendored so Kotlin CI never depends on
network availability or a moving Flutter branch. Updates require an explicit
fixture version change and source-reference update.

Queues, persistence, retry/backoff, session management, SDK-owned identity,
and optimized batching are intentionally outside Core MVP 1.0.0.
