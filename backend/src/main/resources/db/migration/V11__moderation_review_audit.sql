-- Phase 7A: moderator-only report review, immutable audit trail, and report rate-limit support.
-- Additive only; previously released migrations are never edited.
ALTER TABLE user_report DROP CONSTRAINT user_report_status_check;
ALTER TABLE user_report ADD CONSTRAINT user_report_status_check
    CHECK (status IN ('PENDING', 'ACTIONED', 'DISMISSED'));

ALTER TABLE user_report
    ADD COLUMN reviewed_at timestamptz,
    ADD COLUMN reviewer_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
    ADD COLUMN resolution_action varchar(32),
    ADD COLUMN resolution_note varchar(500);

ALTER TABLE user_report ADD CONSTRAINT user_report_review_state_check CHECK (
    (status = 'PENDING' AND reviewed_at IS NULL AND resolution_action IS NULL)
    OR (status IN ('ACTIONED', 'DISMISSED') AND reviewed_at IS NOT NULL AND resolution_action IS NOT NULL)
);
ALTER TABLE user_report ADD CONSTRAINT user_report_resolution_action_check CHECK (
    resolution_action IS NULL OR resolution_action IN ('DISMISS', 'HIDE_CONTENT', 'SUSPEND_ACCOUNT')
);
CREATE INDEX user_report_review_queue_idx ON user_report (status, created_at DESC, public_id DESC);

-- Do not store report evidence or account PII in the audit row. The report and public target IDs are sufficient
-- to correlate the event while the report's existing retention rules preserve the underlying evidence.
CREATE TABLE moderation_audit_event (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_user_id    uuid REFERENCES app_user (id) ON DELETE SET NULL,
    report_id        uuid REFERENCES user_report (id) ON DELETE SET NULL,
    action           varchar(32) NOT NULL,
    target_type      varchar(16) NOT NULL,
    target_public_id uuid,
    target_user_id   uuid REFERENCES app_user (id) ON DELETE SET NULL,
    note             varchar(500),
    created_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT moderation_audit_action_check CHECK (
        action IN ('REPORT_DISMISSED', 'CONTENT_HIDDEN', 'ACCOUNT_SUSPENDED')
    ),
    CONSTRAINT moderation_audit_target_type_check CHECK (
        target_type IN ('PROFILE', 'MESSAGE', 'COMMUNITY_POST')
    ),
    CONSTRAINT moderation_audit_note_check CHECK (note IS NULL OR char_length(note) <= 500)
);
CREATE INDEX moderation_audit_created_idx ON moderation_audit_event (created_at DESC);
CREATE INDEX moderation_audit_target_idx ON moderation_audit_event (target_type, target_public_id, created_at DESC);

CREATE FUNCTION reject_moderation_audit_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'moderation_audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER moderation_audit_event_append_only
    BEFORE UPDATE OR DELETE ON moderation_audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_moderation_audit_mutation();
