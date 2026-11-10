-- NoShow Milestone 3: alert delivery via an outbox.
--
-- Notifications are enqueued in the same transaction as the status change
-- that triggered them, then delivered asynchronously by a worker with
-- retries. This keeps the evaluator fast and gives at-least-once delivery.

create table notification_outbox (
    id              uuid         primary key,
    check_id        uuid         not null references checks (id) on delete cascade,
    kind            text         not null,                      -- DOWN | RECOVERY
    recipient       text         not null,
    subject         text         not null,
    body            text         not null,
    status          text         not null default 'PENDING',    -- PENDING | SENT | FAILED
    attempts        int          not null default 0,
    next_attempt_at timestamptz,
    created_at      timestamptz  not null default now(),
    sent_at         timestamptz
);

-- The worker claims PENDING rows that are due for a (first or retry) attempt.
create index idx_outbox_due on notification_outbox (status, next_attempt_at);
create index idx_outbox_check_id on notification_outbox (check_id);
