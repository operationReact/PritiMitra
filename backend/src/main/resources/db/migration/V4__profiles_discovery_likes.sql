-- Phase 4: profile discovery visibility, opaque public profile identifiers and directional likes.
-- Additive only; earlier migrations are never edited.

-- Private by default: a profile is discoverable only after the owner opts in.
ALTER TABLE onboarding_profile
    ADD COLUMN discovery_visible boolean NOT NULL DEFAULT false,
    ADD COLUMN public_id uuid NOT NULL DEFAULT gen_random_uuid();

ALTER TABLE onboarding_profile
    ADD CONSTRAINT onboarding_profile_public_id_key UNIQUE (public_id);

-- Partial index covering the discovery candidate set; keyset pagination orders by public_id.
CREATE INDEX onboarding_profile_discoverable_idx
    ON onboarding_profile (public_id)
    WHERE discovery_visible AND completed_at IS NOT NULL;

-- A like is a directional, idempotent signal. Likes never create matches; matching belongs to a later phase.
CREATE TABLE profile_like (
    from_user_id uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    to_user_id   uuid        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (from_user_id, to_user_id),
    CONSTRAINT profile_like_not_self CHECK (from_user_id <> to_user_id)
);

CREATE INDEX profile_like_to_user_idx ON profile_like (to_user_id);
