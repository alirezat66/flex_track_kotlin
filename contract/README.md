# FlexTrack shared contract

This directory contains the language-neutral inputs used to keep the Flutter
and Kotlin SDKs behaviorally compatible.

- Core specification: `1.0.0`
- Runtime specification: `1.0.0`
- Fixture version: `1.0.0`
- Flutter reference: `flex_track` `v2.2.0`
- Flutter reference commit: `f38c0618374272ec256bc2809fa70ad86c555631`

Canonical sources:

- [Core MVP specification](https://github.com/alirezat66/flex_track/blob/v2.2.0/doc/core-mvp-specification.md)
- [Conformance runner contract](https://github.com/alirezat66/flex_track/blob/v2.2.0/doc/conformance.md)
- [Flutter fixtures](https://github.com/alirezat66/flex_track/tree/v2.2.0/test/fixtures/conformance)
- [Runtime fixtures](https://github.com/alirezat66/flex_track/tree/v2.2.0/test/fixtures/conformance)

The JSON files in this directory are vendored so Kotlin CI never depends on
network availability or a moving Flutter branch. Updates require an explicit
fixture version change and source-reference update.

Queueing and selective retry are specified separately by Runtime MVP 1.0.0.
See [PARITY.md](PARITY.md) for platform-equivalent coverage and intentional
Flutter/Android differences.
