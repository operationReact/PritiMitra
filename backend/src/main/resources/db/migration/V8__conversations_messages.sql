-- Phase 5: one conversation per match, durable text messages, and per-participant read state.
-- Additive only; earlier migrations are never edited.

CREATE TABLE conversation (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id   uuid        NOT NULL DEFAULT gen_random_uuid(),
    match_id    uuid        NOT NULL REFERENCES profile_match (id) ON DELETE CASCADE,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT conversation_public_id_key UNIQUE (public_id),
    CONSTRAINT conversation_match_key UNIQUE (match_id)
);

CREATE TABLE conversation_message (
    id                 uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id          uuid         NOT NULL DEFAULT gen_random_uuid(),
    -- Server-assigned, strictly increasing ordering key used for deterministic keyset pagination.
    seq                bigint       GENERATED ALWAYS AS IDENTITY,
    conversation_id    uuid         NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    sender_id          uuid         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    -- Client-generated key; the unique constraint makes retries of the same send return the stored message.
    client_message_id  uuid         NOT NULL,
    body               varchar(2000) NOT NULL,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT conversation_message_public_id_key UNIQUE (public_id),
    CONSTRAINT conversation_message_seq_key UNIQUE (seq),
    CONSTRAINT conversation_message_client_key UNIQUE (conversation_id, sender_id, client_message_id),
    CONSTRAINT conversation_message_body_check CHECK (char_length(body) BETWEEN 1 AND 2000)
);

CREATE INDEX conversation_message_history_idx ON conversation_message (conversation_id, seq);

-- Highest message seq a participant has read. Absence means nothing has been read yet.
CREATE TABLE conversation_read_state (
    conversation_id  uuid   NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    user_id          uuid   NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    last_read_seq    bigint NOT NULL DEFAULT 0,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (conversation_id, user_id)
);
