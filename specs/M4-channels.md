# Spec — Milestone 4: Slack & webhook channels

Status: **todo** · Sprint: M4

## Goal
Deliver alerts to Slack and generic webhooks in addition to email, with
multiple channels configurable per account/check and a verification step.

## Scope
- `alert_channel` table generalising M3's single email recipient: type
  (EMAIL | SLACK | WEBHOOK), config (JSON), verified flag.
- Fan-out: an outbox notification is delivered to every enabled channel.
- Slack sender (incoming webhook / API) and generic webhook sender (POST JSON
  payload).
- Channel verification: send a test message and mark the channel verified.

## Non-goals
- On-call scheduling / escalation, status pages (out of product scope for v1).

## Design
- Replace M3's inline recipient with `alert_channel`. The outbox becomes one
  row per (notification, channel), so retries/backoff are per channel.
- Senders are selected by channel type behind a common interface.
- Unverified channels are skipped by delivery; verification sends a one-off
  test through the sender.

## Data model (V5 migration)
- `alert_channel(id, account_id, type, config, verified, created_at)`
- `notification_outbox` gains `channel_id` (one row per channel per
  notification).

## Acceptance criteria
- [ ] A DOWN alert fans out to all enabled channels for the check/account.
- [ ] Slack and webhook deliveries succeed against a stub endpoint.
- [ ] An unverified channel is skipped.
- [ ] Verifying a channel sends a test and flips `verified`.

## Tests (Testcontainers + mocked HTTP)
- [ ] Fan-out count equals the number of enabled channels.
- [ ] Slack and webhook senders post the expected payload.
- [ ] Unverified channel receives nothing.
- [ ] Verification path sends a test message.

## Stories (issues for the M4 sprint)
1. V5 migration + `AlertChannel` entity/repository; migrate email to a channel.
2. Channel CRUD + verification endpoint/UI.
3. Outbox fan-out per channel.
4. Slack sender.
5. Generic webhook sender.
6. Tests for fan-out, senders and verification.
