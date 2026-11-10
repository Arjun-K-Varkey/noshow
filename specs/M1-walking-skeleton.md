# Spec — Milestone 1: Walking skeleton

Status: **done** · Sprint: M1

## Goal
A runnable end-to-end slice: create a check, get a ping URL, send a ping, see
status on a dashboard. No alerting, no late detection.

## Scope
- Create / list checks (name, period in seconds, grace in seconds).
- Unique ping URL per check: `GET|POST /ping/{token}` (+ `/start`, `/fail`).
- A SUCCESS ping marks the check `UP` and advances its next-expected time.
- Server-rendered dashboard at `/`.
- Postgres + Flyway, Docker Compose, one Testcontainers test.

## Out of scope (later milestones)
Late detection / evaluator (M2), alerts (M3+), channels (M4), multi-instance
safety (M5), Spring Boot starter (M6), cron expressions & timezones (M7).

## Data model (V1)
- `checks(id, name, period_seconds, grace_seconds, status, ping_token,
  last_ping_at, next_expected_at, created_at)`
- `ping_event(id, check_id, kind, received_at, source_ip, user_agent)`

## Acceptance criteria
- [x] Creating a check stores it with status `NEW` and a unique token.
- [x] A SUCCESS ping sets status `UP`, records `last_ping_at`, and sets
  `next_expected_at = now + period + grace`.
- [x] START / FAIL pings are logged but do not change status in M1.
- [x] An unknown token returns 404 and records nothing.
- [x] The dashboard lists checks with status, period, last ping and ping URL.

## Notes carried into M2
M1 folds grace into `next_expected_at` for simplicity. M2 separates them:
`expected_at = last_ping + period`, `deadline = expected_at + grace`.
