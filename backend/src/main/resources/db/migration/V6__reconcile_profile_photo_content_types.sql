-- Reconcile the profile photo content-type constraint with the processor, which only emits JPEG and PNG.
-- V5 allowed image/webp, which the validator never produces. This migration fails closed if any WebP rows exist
-- rather than silently deleting user data; such rows must be reviewed and removed manually before upgrading.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM profile_photo WHERE content_type NOT IN ('image/jpeg', 'image/png')
    ) THEN
        RAISE EXCEPTION 'profile_photo contains unsupported content types; resolve these rows before applying V6';
    END IF;
END
$$;

ALTER TABLE profile_photo DROP CONSTRAINT profile_photo_content_type_check;
ALTER TABLE profile_photo ADD CONSTRAINT profile_photo_content_type_check
    CHECK (content_type IN ('image/jpeg', 'image/png'));
