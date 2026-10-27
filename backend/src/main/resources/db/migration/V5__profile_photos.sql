-- Profile photo metadata. Object bytes live in a private bucket; the storage key is server-generated
-- and never returned to clients. New photos stay PENDING_REVIEW until moderation exists (Phase 7).
CREATE TABLE profile_photo (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    storage_key   varchar(200) NOT NULL UNIQUE,
    content_type  varchar(32) NOT NULL,
    size_bytes    integer NOT NULL,
    review_status varchar(24) NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT profile_photo_content_type_check CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT profile_photo_size_check CHECK (size_bytes > 0 AND size_bytes <= 5242880),
    CONSTRAINT profile_photo_review_status_check CHECK (review_status IN ('PENDING_REVIEW', 'APPROVED'))
);

CREATE INDEX profile_photo_user_idx ON profile_photo (user_id, created_at, id);
