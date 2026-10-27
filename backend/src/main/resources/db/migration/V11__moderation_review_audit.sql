-- Phase 7 initial slice: moderator decisions and append-only audit events.
-- Earlier migrations are immutable. Reports retain their evidence after review.

ALTER TABLE user_report DROP CONSTRAINT user_report_status_check;
ALTER TABLE user_report ADD COLUMN reviewed_by_sub varchar(128);
ALTER TABLE user_report ADD COLUMN reviewed_at timestamptz;
ALTER TABLE user_report ADD COLUMN decision varchar(24);
ALTER TABLE user_report ADD COLUMN review_note varchar(500);

ALTER TABLE user_report ADD CONSTRAINT user_report_status_check
    CHECK (status IN ('PENDING', 'ACTIONED', 'DISMISSED'));
ALTER TABLE user_report ADD CONSTRAINT user_report_review_state_check CHECK (
    (status = 'PENDING' AND reviewed_by_sub IS NULL AND reviewed_at IS NULL AND decision IS NULL)
    OR
    (status IN ('ACTIONED', 'DISMISSED') AND reviewed_by_sub IS NOT NULL
        AND reviewed_at IS NOT NULL AND decision IS NOT NULL)
);
ALTER TABLE user_report ADD CONSTRAINT user_report_decision_check CHECK (
    decision IS NULL
    OR (status = 'DISMISSED' AND decision = 'DISMISS')
    OR (status = 'ACTIONED' AND decision IN ('HIDE_POST', 'HIDE_PROFILE', 'SUSPEND_ACCOUNT'))
);
ALTER TABLE user_report ADD CONSTRAINT user_report_review_note_check
    CHECK (review_note IS NULL OR char_length(review_note) <= 500);

CREATE INDEX user_report_review_queue_idx
    ON user_report (created_at DESC, public_id DESC)
    WHERE status = 'PENDING';
CREATE INDEX user_report_review_history_idx
    ON user_report (reviewed_at DESC, public_id DESC)
    WHERE status IN ('ACTIONED', 'DISMISSED');

-- Do not copy report details or message bodies into audit records.
CREATE TABLE safety_audit_event (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_sub        varchar(128) NOT NULL,
    event_type       varchar(32) NOT NULL,
    report_public_id uuid NOT NULL,
    target_type      varchar(16) NOT NULL,
    target_public_id uuid,
    note             varchar(500),
    created_at       timestamptz NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT safety_audit_event_type_check CHECK (
        event_type IN ('REPORT_DISMISSED', 'COMMUNITY_POST_HIDDEN', 'PROFILE_HIDDEN', 'ACCOUNT_SUSPENDED')
    ),
    CONSTRAINT safety_audit_target_type_check
        CHECK (target_type IN ('PROFILE', 'MESSAGE', 'COMMUNITY_POST')),
    CONSTRAINT safety_audit_note_check CHECK (note IS NULL OR char_length(note) <= 500)
);
CREATE INDEX safety_audit_report_idx ON safety_audit_event (report_public_id, created_at DESC);
CREATE INDEX safety_audit_created_idx ON safety_audit_event (created_at DESC, id DESC);

CREATE FUNCTION reject_safety_audit_event_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'safety_audit_event is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER safety_audit_event_append_only
    BEFORE UPDATE OR DELETE ON safety_audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_safety_audit_event_mutation();
