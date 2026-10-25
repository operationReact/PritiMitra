-- Phase 3 (AWS-aligned): Amazon Cognito owns credentials, verification codes, reset codes and refresh tokens.
-- The application keeps profile, onboarding, eligibility and consent data keyed by the immutable Cognito subject.
-- This migration fails loudly (NOT NULL on cognito_sub) if app_user already has rows, rather than silently
-- discarding or inventing identities.

DROP TABLE refresh_session;
DROP TABLE email_challenge;

ALTER TABLE app_user DROP COLUMN email;
ALTER TABLE app_user DROP COLUMN email_normalized;
ALTER TABLE app_user DROP COLUMN password_hash;
ALTER TABLE app_user DROP COLUMN display_name;
ALTER TABLE app_user DROP COLUMN email_verified_at;

ALTER TABLE app_user ADD COLUMN cognito_sub varchar(128) NOT NULL;
ALTER TABLE app_user ADD CONSTRAINT app_user_cognito_sub_key UNIQUE (cognito_sub);
ALTER TABLE app_user ADD CONSTRAINT app_user_cognito_sub_check CHECK (char_length(btrim(cognito_sub)) BETWEEN 1 AND 128);

-- ACTIVE is set only by the server once email, age, consent and required onboarding steps are all satisfied.
ALTER TABLE app_user DROP CONSTRAINT app_user_status_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_status_check CHECK (
    status IN ('PENDING_EMAIL_VERIFICATION', 'ONBOARDING_INCOMPLETE', 'ACTIVE', 'SUSPENDED', 'INELIGIBLE')
);
ALTER TABLE app_user ALTER COLUMN status SET DEFAULT 'PENDING_EMAIL_VERIFICATION';

ALTER TABLE user_connection_goal DROP CONSTRAINT user_connection_goal_check;
ALTER TABLE user_connection_goal ADD CONSTRAINT user_connection_goal_check CHECK (
    goal IN ('DATING', 'FRIENDSHIP', 'COMMUNITY', 'MEET_PEOPLE', 'OPEN_TO_CONNECTION')
);

ALTER TABLE onboarding_profile ADD COLUMN display_name varchar(60);
ALTER TABLE onboarding_profile ADD CONSTRAINT onboarding_display_name_check CHECK (
    display_name IS NULL OR char_length(btrim(display_name)) BETWEEN 1 AND 60
);

-- Ineligible accounts keep only the status and this timestamp; the date of birth is never stored for them.
-- A scheduled job deletes them (app row and Cognito identity) once the configured retention period has passed.
ALTER TABLE app_user ADD COLUMN ineligible_at timestamptz;
ALTER TABLE app_user ADD CONSTRAINT app_user_ineligible_at_check CHECK (
    status <> 'INELIGIBLE' OR ineligible_at IS NOT NULL
);
CREATE INDEX app_user_ineligible_at_idx ON app_user (ineligible_at) WHERE status = 'INELIGIBLE';

-- Interested-in preferences are validated enum codes, stored as a set. ALL_IDENTITIES is mutually exclusive
-- with the other codes; that rule is enforced by the application service because it spans rows.
ALTER TABLE onboarding_profile DROP COLUMN discovery_interested_in;
CREATE TABLE discovery_interest (
    user_id     uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    interest    varchar(20) NOT NULL,
    PRIMARY KEY (user_id, interest),
    CONSTRAINT discovery_interest_check CHECK (interest IN ('MEN', 'WOMEN', 'NON_BINARY', 'ALL_IDENTITIES'))
);
