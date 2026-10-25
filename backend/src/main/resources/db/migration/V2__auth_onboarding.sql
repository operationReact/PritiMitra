-- Phase 3: authentication, sessions, onboarding and consent records.

CREATE TABLE app_user (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email               varchar(254) NOT NULL,
    email_normalized    varchar(254) NOT NULL,
    display_name        varchar(60)  NOT NULL,
    password_hash       varchar(100) NOT NULL,
    status              varchar(32)  NOT NULL,
    email_verified_at   timestamptz,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT app_user_email_normalized_key UNIQUE (email_normalized),
    CONSTRAINT app_user_email_normalized_lower CHECK (email_normalized = lower(email_normalized)),
    CONSTRAINT app_user_status_check CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'INELIGIBLE', 'DISABLED')),
    CONSTRAINT app_user_display_name_check CHECK (char_length(btrim(display_name)) BETWEEN 1 AND 60)
);

-- Single-use email verification and password-reset challenges. Only HMAC digests are stored.
CREATE TABLE email_challenge (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    purpose         varchar(32) NOT NULL,
    code_digest     varchar(64) NOT NULL,
    expires_at      timestamptz NOT NULL,
    consumed_at     timestamptz,
    failed_attempts integer     NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT email_challenge_purpose_check CHECK (purpose IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT email_challenge_attempts_check CHECK (failed_attempts >= 0)
);

-- At most one live challenge per user and purpose; issuing a new one replaces the previous one.
CREATE UNIQUE INDEX email_challenge_one_live_idx
    ON email_challenge (user_id, purpose)
    WHERE consumed_at IS NULL;

CREATE INDEX email_challenge_user_created_idx ON email_challenge (user_id, created_at);

-- Rotating opaque refresh tokens. Only digests are stored; a family groups rotations for reuse detection.
CREATE TABLE refresh_session (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    family_id         uuid        NOT NULL,
    token_digest      varchar(64) NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    last_used_at      timestamptz,
    expires_at        timestamptz NOT NULL,
    revoked_at        timestamptz,
    revoke_reason     varchar(32),
    replaced_by_id    uuid REFERENCES refresh_session (id),
    CONSTRAINT refresh_session_token_digest_key UNIQUE (token_digest),
    CONSTRAINT refresh_session_revoke_reason_check CHECK (
        revoke_reason IS NULL OR revoke_reason IN ('LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'PASSWORD_RESET', 'ADMIN')
    )
);

CREATE INDEX refresh_session_user_idx ON refresh_session (user_id);
CREATE INDEX refresh_session_family_idx ON refresh_session (family_id);

-- One onboarding row per user. Completion is derived by the server from the required fields, never trusted from clients.
CREATE TABLE onboarding_profile (
    user_id                     uuid PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    date_of_birth               date,
    age_verified_at             timestamptz,
    bio                         varchar(500),
    profile_prompt              varchar(200),
    gender_identity             varchar(40),
    pronouns                    varchar(40),
    gender_identity_visible     boolean     NOT NULL DEFAULT false,
    pronouns_visible            boolean     NOT NULL DEFAULT false,
    sexual_orientation          varchar(40),
    sexual_orientation_visible  boolean     NOT NULL DEFAULT false,
    discovery_interested_in     varchar(40),
    discovery_min_age           integer,
    discovery_max_age           integer,
    discovery_max_distance_km   integer,
    completed_at                timestamptz,
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT onboarding_min_age_check CHECK (discovery_min_age IS NULL OR discovery_min_age BETWEEN 18 AND 99),
    CONSTRAINT onboarding_max_age_check CHECK (discovery_max_age IS NULL OR discovery_max_age BETWEEN 18 AND 99),
    CONSTRAINT onboarding_age_range_check CHECK (
        discovery_min_age IS NULL OR discovery_max_age IS NULL OR discovery_min_age <= discovery_max_age
    ),
    CONSTRAINT onboarding_distance_check CHECK (
        discovery_max_distance_km IS NULL OR discovery_max_distance_km BETWEEN 1 AND 500
    )
);

CREATE TABLE user_connection_goal (
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    goal        varchar(32) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, goal),
    CONSTRAINT user_connection_goal_check CHECK (goal IN ('DATING', 'FRIENDSHIP', 'COMMUNITY', 'MEET_PEOPLE'))
);

CREATE TABLE user_interest (
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    interest    varchar(40) NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, interest),
    CONSTRAINT user_interest_length_check CHECK (char_length(btrim(interest)) BETWEEN 1 AND 40)
);

-- Versioned, append-only consent history. Rows are never updated or deleted.
CREATE TABLE consent_record (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    consent_type    varchar(32) NOT NULL,
    policy_version  varchar(20) NOT NULL,
    accepted        boolean     NOT NULL,
    recorded_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT consent_record_type_check CHECK (consent_type IN ('TERMS_OF_SERVICE', 'PRIVACY_NOTICE', 'COMMUNITY_NICKNAME'))
);

CREATE INDEX consent_record_user_idx ON consent_record (user_id, consent_type, recorded_at);

CREATE FUNCTION reject_consent_record_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'consent_record is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER consent_record_append_only
    BEFORE UPDATE OR DELETE ON consent_record
    FOR EACH ROW EXECUTE FUNCTION reject_consent_record_mutation();
