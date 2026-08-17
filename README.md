# FlexTrack Kotlin

Native Android/Kotlin implementation of FlexTrack: consent-aware analytics
routing with deterministic cross-SDK behavior.

The Kotlin SDK targets Android API 21+ and implements
[FlexTrack Core Specification 1.0.0](contract/README.md), shared with
[FlexTrack Flutter 2.1.0](https://pub.dev/packages/flex_track).

## Project status

The SDK is under active development toward Kotlin 1.0.0. The current version is
`0.1.0-SNAPSHOT` and is not ready for production use.

## Modules

- `flextrack`: publishable Android library (`AAR`).
- `sample`: Android application that consumes `flextrack` as a project dependency.
- `contract`: shared specification and deterministic Flutter/Kotlin fixtures.

## Build

```bash
./gradlew build
./gradlew :flextrack:publishToMavenLocal
```

## Roadmap

1. Library foundation and shared contract.
2. Event, enrichment, routing, consent, and deterministic sampling.
3. Tracker runtime, client lifecycle, debug records, and conformance.
4. Sample application, documentation, and Kotlin 1.0.0 release.

## License

MIT
