-- Phase 5: blocks, private reports, and durable in-app notifications. Additive only.

-- One row per directional block. Enforcement checks both directions of a pair.
CREATE TABLE user_block (
    blocker_id  uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    blocked_id  uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CONSTRAINT user_block_not_self CHECK (blocker_id <> blocked_id)
);

CREATE INDEX user_block_blocked_idx ON user_block (blocked_id);

-- Private safety reports. Reporter and reported references are nulled rather than cascaded so that
-- evidence is not silently removed when an account is purged; see docs/phase-5-matching-messaging.md.
CREATE TABLE user_report (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    reporter_id        uuid         REFERENCES app_user (id) ON DELETE SET NULL,
    reported_user_id   uuid         REFERENCES app_user (id) ON DELETE SET NULL,
    target_type        varchar(16)  NOT NULL,
    target_message_id  uuid         REFERENCES conversation_message (id) ON DELETE SET NULL,
    category           varchar(32)  NOT NULL,
    details            varchar(500),
    status             varchar(16)  NOT NULL DEFAULT 'PENDING',
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT user_report_public_id_key UNIQUE (public_id),
    CONSTRAINT user_report_target_type_check CHECK (target_type IN ('PROFILE', 'MESSAGE')),
    CONSTRAINT user_report_target_check CHECK (
        (target_type = 'PROFILE' AND target_message_id IS NULL)
        OR (target_type = 'MESSAGE' AND target_message_id IS NOT NULL)),
    CONSTRAINT user_report_category_check CHECK (
        category IN ('HARASSMENT', 'IMPERSONATION', 'UNWANTED_CONTENT', 'SPAM', 'OTHER')),
    CONSTRAINT user_report_status_check CHECK (status IN ('PENDING')),
    CONSTRAINT user_report_details_check CHECK (details IS NULL OR char_length(details) <= 500)
);

-- Duplicate protection: one open report per reporter and profile, and one per reporter and message.
CREATE UNIQUE INDEX user_report_open_profile_key ON user_report (reporter_id, reported_user_id)
    WHERE target_type = 'PROFILE' AND status = 'PENDING';
CREATE UNIQUE INDEX user_report_open_message_key ON user_report (reporter_id, target_message_id)
    WHERE target_type = 'MESSAGE' AND status = 'PENDING';

-- Per-recipient notifications. The row is written in the same transaction as the event it describes,
-- so it doubles as the durable outbox for in-app delivery. No message body is stored here.
CREATE TABLE notification (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id        uuid         NOT NULL DEFAULT gen_random_uuid(),
    -- Server-assigned, strictly increasing ordering key for newest-first keyset pagination.
    seq              bigint       GENERATED ALWAYS AS IDENTITY,
    recipient_id     uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    kind             varchar(32)  NOT NULL,
    match_id         uuid         REFERENCES profile_match (id) ON DELETE CASCADE,
    conversation_id  uuid         REFERENCES conversation (id) ON DELETE CASCADE,
    message_id       uuid         REFERENCES conversation_message (id) ON DELETE CASCADE,
    -- Deterministic per event, so a retried transaction cannot create a second notification.
    dedupe_key       varchar(128) NOT NULL,
    read_at          timestamptz,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT notification_public_id_key UNIQUE (public_id),
    CONSTRAINT notification_seq_key UNIQUE (seq),
    CONSTRAINT notification_dedupe_key UNIQUE (recipient_id, dedupe_key),
    CONSTRAINT notification_kind_check CHECK (kind IN ('MATCH', 'NEW_MESSAGE'))
);

CREATE INDEX notification_recipient_seq_idx ON notification (recipient_id, seq);
CREATE INDEX notification_unread_idx ON notification (recipient_id) WHERE read_at IS NULL;
