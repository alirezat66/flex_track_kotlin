# Architecture

FlexTrack Kotlin follows the language-neutral Core Specification rather than a
line-by-line Dart translation.

The `flextrack` module is organized into four layers:

1. **Model** — immutable events, routing rules, groups, and result values.
2. **Policy** — enrichment, consent gates, deterministic sampling, and routing.
3. **Runtime** — tracker registry, event processor, and isolated clients.
4. **Diagnostics** — debug decisions and conformance reporting.

Android framework usage stays at the library boundary. Core decisions remain
deterministic JVM-testable Kotlin so they can be compared directly with the
shared Flutter fixtures. Offline persistence, retries, and SDK-owned identity
are deliberately deferred until a later versioned contract.

## Module dependency direction

```text
sample -> flextrack

diagnostics -> runtime -> policy -> model
```

Dependencies MUST point to the right in this diagram. The Core model never
depends on tracker vendors, application code, or the sample module.
