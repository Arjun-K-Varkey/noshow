# Spec — Milestone 6: Spring Boot starter

Status: **todo (outline — refine at sprint start)** · Sprint: M6

## Goal
Monitor any `@Scheduled` method with a single annotation — no manual ping
calls in user code. This is NoShow's differentiator and gets its own deep-dive
write-up.

## Scope
- A `noshow-spring-boot-starter` module (multi-module Gradle build).
- An annotation (e.g. `@Monitored("backup-nightly")`) plus an aspect/advice
  that pings NoShow on method completion (success) and on exception (fail),
  with an optional start ping.
- Configuration: NoShow base URL and the check's token (or auto-registration
  of the check by name on startup).

## Design (to refine)
- AOP around annotated methods: success → `/ping/{token}`, exception →
  `/ping/{token}/fail`, optional `/ping/{token}/start` before.
- Map annotation name → token via config, or auto-provision through a small
  registration API on startup.
- Split the repo into `noshow-app` and `noshow-spring-boot-starter`.

## Acceptance criteria
- [ ] Annotating a `@Scheduled` method causes pings with no other code change.
- [ ] A thrown exception sends a fail ping.
- [ ] The starter is consumable as a normal dependency by a separate app.

## Tests
- [ ] A sample app using the starter; a stub NoShow receives the expected
      pings for success and failure.

## Stories (to be broken out at sprint start)
1. Multi-module split + starter scaffold.
2. `@Monitored` annotation + aspect.
3. Configuration properties.
4. Check auto-registration (or token mapping).
5. Sample app + integration tests.
6. Starter docs.
