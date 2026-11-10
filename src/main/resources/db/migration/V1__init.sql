-- NoShow initial schema (Milestone 1: walking skeleton)
-- "checks" and "ping_event" only. Alerting tables (outbox, channels,
-- check_event history) arrive in later milestones.

create table checks (
    id                uuid         primary key,
    name              text         not null,
    period_seconds    bigint       not null,
    grace_seconds     bigint       not null default 0,
    status            text         not null default 'NEW',
    ping_token        text         not null unique,
    last_ping_at      timestamptz,
    next_expected_at  timestamptz,
    created_at        timestamptz  not null default now()
);

create table ping_event (
    id          uuid         primary key,
    check_id    uuid         not null references checks (id) on delete cascade,
    kind        text         not null,
    received_at timestamptz  not null default now(),
    source_ip   text,
    user_agent  text
);

create index idx_ping_event_check_id on ping_event (check_id);
create index idx_ping_event_received_at on ping_event (received_at);
