-- Phase 5: durable mutual matches. Additive only; earlier migrations are never edited.
-- A match exists only after both users have a directional like for each other (see MatchService).

CREATE TABLE profile_match (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id     uuid        NOT NULL DEFAULT gen_random_uuid(),
    -- Canonical unordered pair: user_low_id is always the smaller UUID, so (A,B) and (B,A) map to one row.
    user_low_id   uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    user_high_id  uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    status        varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    ended_at      timestamptz,
    CONSTRAINT profile_match_public_id_key UNIQUE (public_id),
    CONSTRAINT profile_match_pair_key UNIQUE (user_low_id, user_high_id),
    CONSTRAINT profile_match_ordered_pair CHECK (user_low_id < user_high_id),
    CONSTRAINT profile_match_status_check CHECK (status IN ('ACTIVE', 'UNMATCHED')),
    CONSTRAINT profile_match_ended_check CHECK (
        (status = 'ACTIVE' AND ended_at IS NULL) OR (status = 'UNMATCHED' AND ended_at IS NOT NULL))
);

-- Participant lookups for the match list; keyset pagination orders by public_id.
CREATE INDEX profile_match_low_active_idx ON profile_match (user_low_id, public_id) WHERE status = 'ACTIVE';
CREATE INDEX profile_match_high_active_idx ON profile_match (user_high_id, public_id) WHERE status = 'ACTIVE';
