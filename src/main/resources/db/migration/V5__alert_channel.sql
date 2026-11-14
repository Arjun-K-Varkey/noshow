-- NoShow Milestone 4: alert channels.
--
-- Generalises M3's single configured email recipient into a set of channels
-- (email, Slack, webhook). A notification fans out to one outbox row per
-- enabled channel (fan-out lands in M4.3); this migration adds the table and
-- links the outbox to a channel.

create table alert_channel (
    id         uuid        primary key,
    type       text        not null,          -- EMAIL | SLACK | WEBHOOK
    config     text        not null,          -- channel-specific: email address, webhook URL, ...
    verified   boolean     not null default false,
    created_at timestamptz not null default now()
);

-- Which channel an outbox row targets. Nullable for now: the M3 single-email
-- path still writes channel-less rows until fan-out (M4.3) replaces it.
alter table notification_outbox add column channel_id uuid references alert_channel (id);
