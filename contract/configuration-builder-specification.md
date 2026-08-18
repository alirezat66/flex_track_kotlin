# FlexTrack Configuration Builder Contract 1.0

Flutter is the reference implementation for this contract. Native SDKs may use
language-idiomatic syntax, but the resulting routing configuration and runtime
behavior must remain equivalent.

## Routing builder

A conforming builder supports:

- named tracker groups and predefined `all` and `development` groups;
- name substring, regular expression, exact name, category, property, PII,
  high-volume, essential, and default route conditions;
- direct tracker targets and named-group targets;
- sampling rate and heavy (1%), light (10%), medium (50%), and none (100%)
  conveniences;
- general consent, PII consent, debug-only, production-only, priority, ID, and
  description modifiers;
- global sampling, consent checking, and debug-mode switches;
- stable descending priority ordering;
- an automatically generated all-trackers default rule at priority `-1000`
  when no explicit default rule or group exists.

Empty names, empty target lists, unknown group/category references, invalid
sampling rates, empty IDs, and empty descriptions fail synchronously during
configuration.

## Client builder

A conforming client builder configures in one place:

- tracker registration;
- routing;
- ordered transformers;
- consent and connectivity providers;
- in-memory or durable offline queue;
- debug logging;
- optional automatic lifecycle start.

The builder must reject duplicate or empty tracker IDs through the same runtime
contract as manual registration. Builder-created clients must produce the same
routing, delivery, queue, retry, and lifecycle results as manually constructed
clients.

## Package-test gate

Builder tests belong to the publishable library test target. Sample and UI tests
do not count toward contract parity. Each native SDK must test every validation,
default, condition, modifier, target, ordering, and client-wiring behavior listed
above.
