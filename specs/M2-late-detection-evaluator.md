# Spec — Milestone 2: Late-detection evaluator

Status: **todo** · Sprint: M2

## Goal
Make NoShow notice when a ping *doesn't* arrive. A background sweep moves a
check through `UP → LATE → DOWN` as its deadline passes, and back to `UP` when
a ping finally arrives. Still no external alerts (that is M3) — this milestone
makes the *status* correct and records the history.

## Background
This is the dead man's switch itself. Normal monitoring reacts to events; here
the alert condition is the *absence* of an expected ping. The evaluator is a
periodic job that compares each check's deadline to now.

## State machine
Per check, timestamps computed from the last success ping:
- `expected_at = last_success_ping + period_seconds`
- `deadline     = expected_at + grace_seconds`

Transitions (evaluated on each sweep, and on ping receipt):
- `NEW`  → stays `NEW` until the first success ping (no deadline yet).
- `UP`   → `LATE` when `now > expected_at` and `now <= deadline`.
- `LATE` → `DOWN` when `now > deadline`.
- `LATE`/`DOWN` → `UP` when a success ping arrives (deadline recomputed).
- `PAUSED` is never auto-evaluated.

Every transition is recorded in `check_event`.

## Design
- **Clock bean.** Inject `java.time.Clock` everywhere "now" is read, so tests
  drive time deterministically. No `Instant.now()` calls in services.
- **Evaluator** runs on a fixed delay (`@Scheduled`, interval configurable,
  default 30s). It selects due checks (`status in (UP, LATE)` and
  `now > expected_at`), applies transitions, and writes `check_event` rows.
- Separate `period_seconds` and `grace_seconds` in the status math (stop
  folding grace into `next_expected_at`; keep `expected_at` and `deadline` as
  derived values or stored columns).
- Multi-instance safety is **out of scope** here (M5). Single evaluator
  instance is assumed; note the race in the spec so M5 can close it.

## Data model changes (V2 migration)
- `check_event(id, check_id, from_status, to_status, at, reason)` — append-only
  transition log, newest shown on the dashboard.
- Optional: store `expected_at` / `deadline` on `checks` for easy querying, or
  derive them. Decide in the first story.

## Acceptance criteria
- [ ] A check with a past `deadline` and no new ping becomes `DOWN` within one
  sweep interval.
- [ ] A check past `expected_at` but within grace shows `LATE`.
- [ ] A success ping on a `LATE`/`DOWN` check returns it to `UP` and recomputes
  the deadline.
- [ ] Every status change writes a `check_event` row with from/to/at.
- [ ] `PAUSED` checks are never transitioned by the evaluator.
- [ ] Dashboard shows current status and the time of the last transition.

## Tests (Testcontainers + controlled Clock)
- [ ] `up → late → down` as the clock advances past expected_at then deadline.
- [ ] `down → up` on a success ping, with deadline recomputed.
- [ ] Grace of 0 goes straight `up → down` (no LATE window).
- [ ] A paused check is untouched by a sweep.
- [ ] `check_event` rows match the transitions exactly (no duplicates on
  repeated sweeps while status is unchanged).

## Stories (issues for the M2 sprint)
1. Clock bean + refactor services to read time from it.
2. V2 migration: `check_event`; split expected_at / deadline math.
3. Evaluator sweep with `UP → LATE → DOWN` transitions.
4. `down/late → up` recovery on success ping; record transitions.
5. Dashboard: show status + last transition; event history per check.
6. Tests for the full state machine with a controlled clock.
