# Spec — Milestone 5: Multi-instance safety

Status: **todo (outline — refine at sprint start)** · Sprint: M5

## Goal
Run the evaluator and the outbox worker safely when the app runs on several
instances: each due check is evaluated once, and each notification is sent
once. Closes the remaining concurrency gap left open since M2.3 (the in-process
race is already guarded by optimistic locking; this covers across nodes).

## Scope
- Claim work with `SELECT ... FOR UPDATE SKIP LOCKED` (or Postgres advisory
  locks) so two instances never process the same row.
- Apply to the evaluator's due-check selection and the outbox worker's pending
  rows.

## Design (to refine)
- Evaluator: select-and-lock a batch of due checks with SKIP LOCKED, transition
  within the lock, commit. Combined with the existing `@Version` guard.
- Outbox worker: claim PENDING rows with SKIP LOCKED before sending.

## Acceptance criteria
- [ ] Two app instances running concurrently never double-transition a check
      or double-send a notification.
- [ ] Throughput scales roughly with instances (no global serialization beyond
      per-row locks).

## Tests
- [ ] Concurrency test: two evaluator loops (threads/instances) against one DB
      produce exactly one transition per check and one notification per alert.

## Stories (to be broken out at sprint start)
1. SKIP LOCKED claim for the evaluator.
2. SKIP LOCKED claim for the outbox worker.
3. Concurrency tests.
4. Docs on running multiple instances.
