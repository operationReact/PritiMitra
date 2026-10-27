-- Phase 7B: serialize concurrent rolling-window rate-limit checks per user and action.
-- Each row is a lock primitive only; message and post content are never duplicated here.
CREATE TABLE user_activity_rate_limit_lock (
    user_id    uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    action     varchar(24) NOT NULL,
    touched_at timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT user_activity_rate_limit_lock_pk PRIMARY KEY (user_id, action),
    CONSTRAINT user_activity_rate_limit_action_check CHECK (action IN ('COMMUNITY_POST', 'MESSAGE_SEND'))
);

-- Supports the rolling-hour count for an account's sent messages without scanning unrelated conversations.
CREATE INDEX conversation_message_sender_created_idx
    ON conversation_message (sender_id, created_at DESC);
