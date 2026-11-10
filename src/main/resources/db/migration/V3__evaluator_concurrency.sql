-- NoShow Milestone 2.3 review follow-up.
--
-- Optimistic-locking version column so a stale evaluator sweep cannot
-- overwrite a check that a concurrent success ping updated: the sweep's save
-- fails and is skipped, and the ping wins.
alter table checks add column version bigint not null default 0;

-- The evaluator selects only *due* checks (expected_at <= now); index it so
-- the sweep doesn't scan the whole table.
create index idx_checks_expected_at on checks (expected_at);
