-- Phase 7: privacy-safe self-service deletion and append-only optional consent history.
-- Earlier migrations are immutable; deletion is processed asynchronously and retries external cleanup safely.

ALTER TABLE app_user DROP CONSTRAINT app_user_status_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_status_check CHECK (
    status IN ('PENDING_EMAIL_VERIFICATION', 'ONBOARDING_INCOMPLETE', 'ACTIVE', 'SUSPENDED', 'INELIGIBLE', 'DELETION_PENDING')
);

-- A row exists for the entire lifetime of a deletion request. The Cognito subject is retained only while cleanup
-- is pending so a job can retry Cognito/S3 deletion; it is nulled on completion. The digest is a non-reversible
-- tombstone that prevents an unexpired access token from provisioning the account again.
CREATE TABLE account_deletion_request (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_digest  varchar(64) NOT NULL UNIQUE,
    cognito_sub     varchar(128),
    status          varchar(16) NOT NULL DEFAULT 'PENDING',
    requested_at    timestamptz NOT NULL DEFAULT clock_timestamp(),
    completed_at    timestamptz,
    CONSTRAINT account_deletion_status_check CHECK (
        (status = 'PENDING' AND cognito_sub IS NOT NULL AND completed_at IS NULL)
        OR (status = 'COMPLETED' AND cognito_sub IS NULL AND completed_at IS NOT NULL)
    )
);
CREATE INDEX account_deletion_pending_idx ON account_deletion_request (requested_at, id) WHERE status = 'PENDING';

-- Durable privacy-safe audit. Do not store an email, Cognito subject, message body, or profile identifier here.
CREATE TABLE account_lifecycle_audit_event (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    subject_digest  varchar(64) NOT NULL,
    event_type      varchar(40) NOT NULL,
    consent_type    varchar(32),
    created_at      timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT account_lifecycle_event_type_check CHECK (
        event_type IN ('ACCOUNT_DELETION_REQUESTED', 'ACCOUNT_DELETION_COMPLETED',
                       'OPTIONAL_CONSENT_GRANTED', 'OPTIONAL_CONSENT_WITHDRAWN')
    ),
    CONSTRAINT account_lifecycle_consent_type_check CHECK (
        consent_type IS NULL OR consent_type = 'COMMUNITY_NICKNAME'
    )
);
CREATE INDEX account_lifecycle_audit_subject_idx
    ON account_lifecycle_audit_event (subject_digest, created_at DESC);

CREATE FUNCTION reject_account_lifecycle_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'account_lifecycle_audit_event is append-only';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER account_lifecycle_audit_append_only
    BEFORE UPDATE OR DELETE ON account_lifecycle_audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_account_lifecycle_audit_mutation();

-- A deleted user's reported message can be removed while its private report remains as a record of the incident.
-- Application writes still require a real message ID; this nullable state is only needed after target deletion.
ALTER TABLE user_report DROP CONSTRAINT user_report_target_check;
ALTER TABLE user_report ADD CONSTRAINT user_report_target_check CHECK (
    (target_type = 'PROFILE' AND target_message_id IS NULL AND target_post_id IS NULL)
    OR (target_type = 'MESSAGE' AND target_post_id IS NULL)
    OR (target_type = 'COMMUNITY_POST' AND target_message_id IS NULL AND target_post_id IS NOT NULL)
);

-- Consent history remains append-only for normal application operations. During the transaction that deletes an
-- account, the deletion service sets a transaction-local flag so the ON DELETE CASCADE can remove personal consent
-- rows in accordance with the user's deletion request. A direct DELETE/UPDATE still fails.
CREATE OR REPLACE FUNCTION reject_consent_record_mutation() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'DELETE' AND current_setting('pritimitra.allow_consent_delete', true) = 'on' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'consent_record is append-only';
END;
$$ LANGUAGE plpgsql;
