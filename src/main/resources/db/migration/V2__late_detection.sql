-- NoShow Milestone 2: late-detection evaluator groundwork.
--
-- Stop folding grace into a single next-expected time. Model the two moments
-- explicitly:
--   expected_at = last success ping + period
--   deadline    = expected_at + grace
-- A check is LATE between expected_at and deadline, DOWN past deadline.

alter table checks add column expected_at timestamptz;
alter table checks add column deadline     timestamptz;

-- Backfill from the M1 column where a ping was already recorded, then drop it.
-- next_expected_at held (last + period + grace), i.e. the deadline.
update checks
   set deadline    = next_expected_at,
       expected_at = next_expected_at - make_interval(secs => grace_seconds)
 where next_expected_at is not null;

alter table checks drop column next_expected_at;

-- Append-only log of status transitions, newest shown on the dashboard.
create table check_event (
    id          uuid         primary key,
    check_id    uuid         not null references checks (id) on delete cascade,
    from_status text         not null,
    to_status   text         not null,
    at          timestamptz  not null default now(),
    reason      text
);

create index idx_check_event_check_id on check_event (check_id);
create index idx_check_event_at on check_event (at);

-- The evaluator selects due checks by deadline; index it.
create index idx_checks_deadline on checks (deadline);
