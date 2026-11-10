# Spec — Milestone 5: Multi-instance safety

Status: **todo (outline — refine at sprint start)** · Sprint: M5

## Goal
Run the evaluator and the outbox worker safely when the app runs on several
instances: each due check is evaluated by one instance at a time, and each
pending notification is claimed and processed by one instance at a time — i.e.
no **concurrent** duplicate work across nodes. Closes the remaining concurrency
gap left open since M2.3 (the in-process race is already guarded by optimistic
locking; this covers across nodes).

Note this is *not* exactly-once delivery. Delivery stays **at-least-once** (as
M3 promises): if an instance sends an alert and then crashes before committing
the row's SENT status, another instance can resend it. `SKIP LOCKED` prevents
simultaneous claims, not this crash-resend. The resend is made harmless with an
idempotency key, not eliminated.

## Scope
- Claim work with `SELECT ... FOR UPDATE SKIP LOCKED` (or Postgres advisory
  locks) so two instances never process the same row.
- Apply to the evaluator's due-check selection and the outbox worker's pending
  rows.

## Design (to refine)
- Evaluator: select-and-lock a batch of due checks with SKIP LOCKED, transition
  within the lock, commit. Combined with the existing `@Version` guard.
- Outbox worker: claim PENDING rows with SKIP LOCKED before sending.
- Idempotency: each notification carries a stable key (e.g. its outbox row id,
  or `check_id + kind + transition_at`) sent to the receiver — Slack/webhook
  dedupe on it, and email uses a stable `Message-ID` — so an at-least-once
  resend is collapsed rather than delivered twice.

## Acceptance criteria
- [ ] Two app instances running concurrently never *concurrently* transition
      the same check or claim the same outbox row.
- [ ] Delivery stays at-least-once; a crash-induced resend is deduplicated by
      the receiver via the idempotency key (no exactly-once claim).
- [ ] Throughput scales roughly with instances (no global serialization beyond
      per-row locks).

## Tests
- [ ] Concurrency test: two evaluator loops (threads/instances) against one DB
      produce exactly one transition per check and claim each outbox row once.
- [ ] A simulated send-then-crash resend is deduplicated by the idempotency
      key rather than delivered twice.

## Stories (to be broken out at sprint start)
1. SKIP LOCKED claim for the evaluator.
2. SKIP LOCKED claim for the outbox worker.
3. Idempotency key on notifications + receiver-side dedupe.
4. Concurrency tests.
5. Docs on running multiple instances.
