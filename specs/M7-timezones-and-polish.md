# Spec — Milestone 7: Timezone/DST hardening + polish

Status: **todo (outline — refine at sprint start)** · Sprint: M7

## Goal
Support cron-expression schedules with correct timezone and DST handling, add
schedule editing, and polish the dashboard and docs. Until now a schedule is a
fixed interval; real jobs run on cron-like times ("daily at 02:00
Europe/London") that shift with daylight saving.

## Scope
- A cron schedule kind alongside the fixed interval; per-check timezone.
- Compute `expected_at` from the cron expression evaluated in the check's zone.
- Schedule editing in the UI; dashboard polish; getting-started docs.

## Design (to refine)
- Add `schedule_kind (INTERVAL | CRON)`, `cron_expr`, `timezone` to `checks`.
- Use a zone-aware cron evaluator (e.g. Spring `CronExpression`) to compute the
  next expected time after the last ping in the check's `ZoneId`; DST is
  handled by evaluating in-zone rather than in UTC offsets.

## Acceptance criteria
- [ ] A "daily 02:00 Europe/London" check computes correct next-expected times
      across a spring-forward and autumn-back DST change.
- [ ] Interval checks keep working unchanged.
- [ ] Schedules can be edited after creation.

## Tests
- [ ] Explicit DST-boundary cases for a cron schedule in a DST-observing zone.
- [ ] Interval-schedule regression still passes.

## Stories (to be broken out at sprint start)
1. Cron schedule model + migration.
2. Zone-aware next-expected computation.
3. Schedule editing UI.
4. DST boundary tests.
5. Getting-started docs.
