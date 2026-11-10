# Spec — Milestone 3: Email alerts + recovery (outbox)

Status: **todo** · Sprint: M3

## Goal
When a check goes DOWN, send an alert; when it recovers, send a recovery
alert. Delivery must be reliable (at-least-once), must not block the
evaluator, and must not spam — one alert per real transition, not one per
sweep. This is the first genuinely useful release.

## Scope
- `notification_outbox` table: queued alerts delivered asynchronously.
- Enqueue a notification when a check transitions **to DOWN** (evaluator) and
  when it **recovers to UP** from LATE/DOWN (ping path).
- An outbox worker (`@Scheduled`) delivers PENDING rows via SMTP, marking them
  SENT, or FAILED with retry + backoff.
- A configured recipient email (per account or global for now; per-channel
  targeting generalises in M4).
- Dedupe: enqueue is tied to an actual status transition (one `check_event`),
  never to each sweep.

## Non-goals
- Slack / webhook channels (M4). Multi-instance-safe claiming of outbox rows
  (M5) — M3 assumes a single worker.

## Design
- On the DOWN transition (in `CheckTransitioner`) and the recovery transition
  (in the ping path), insert one `notification_outbox` row in the same
  transaction as the status change, so an alert is enqueued iff the transition
  commits.
- Outbox worker: a fixed-delay job selects PENDING (and due-for-retry) rows,
  sends via `JavaMailSender`, and updates status. Failures increment
  `attempts` and set `next_attempt_at` with exponential backoff up to a cap.

## Data model (V4 migration)
- `notification_outbox(id, check_id, kind [DOWN|RECOVERY], recipient,
  subject, body, status [PENDING|SENT|FAILED], attempts, next_attempt_at,
  created_at, sent_at)`

## Acceptance criteria
- [ ] A transition to DOWN enqueues exactly one DOWN notification.
- [ ] A recovery to UP enqueues exactly one RECOVERY notification.
- [ ] The worker delivers a PENDING row and marks it SENT.
- [ ] A failing send is retried with backoff and marked SENT once it succeeds
      (or FAILED after the attempt cap).
- [ ] Repeated sweeps while a check stays DOWN do not enqueue duplicates.

## Tests (Testcontainers + GreenMail or a mocked mail sender)
- [ ] DOWN transition → one email enqueued and sent.
- [ ] Recovery → one recovery email.
- [ ] Transient send failure → retried, then SENT.
- [ ] Stable DOWN across several sweeps → no duplicate notifications.

## Stories (issues for the M3 sprint)
1. V4 migration + `NotificationOutbox` entity/repository.
2. Enqueue on DOWN (evaluator) and on recovery (ping path), in-transaction.
3. SMTP sender + outbox worker (fixed delay).
4. Retry with exponential backoff + attempt cap.
5. Recipient configuration.
6. Tests for enqueue, delivery, retry and dedupe.
