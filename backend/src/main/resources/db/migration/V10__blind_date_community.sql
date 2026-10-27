-- Phase 6: opt-in Blind Date scheduling and community primitives. Additive only.

CREATE TABLE blind_date_pair (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_low_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    user_high_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    starts_at timestamptz NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'SCHEDULED',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT blind_date_pair_public_id_key UNIQUE (public_id),
    CONSTRAINT blind_date_pair_ordered_users CHECK (user_low_id < user_high_id),
    CONSTRAINT blind_date_pair_slot_key UNIQUE (user_low_id, user_high_id, starts_at),
    CONSTRAINT blind_date_pair_status_check CHECK (status IN ('SCHEDULED', 'CANCELLED', 'COMPLETED'))
);
CREATE INDEX blind_date_pair_low_idx ON blind_date_pair (user_low_id, starts_at DESC);
CREATE INDEX blind_date_pair_high_idx ON blind_date_pair (user_high_id, starts_at DESC);

CREATE TABLE blind_date_opt_in (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    starts_at timestamptz NOT NULL,
    time_zone varchar(64) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'WAITING',
    pair_id uuid REFERENCES blind_date_pair (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT blind_date_opt_in_public_id_key UNIQUE (public_id),
    CONSTRAINT blind_date_opt_in_user_slot_key UNIQUE (user_id, starts_at),
    CONSTRAINT blind_date_opt_in_status_check CHECK (status IN ('WAITING', 'PAIRED', 'WITHDRAWN')),
    CONSTRAINT blind_date_opt_in_pair_check CHECK (
        (status = 'WAITING' AND pair_id IS NULL)
        OR (status = 'PAIRED' AND pair_id IS NOT NULL)
        OR status = 'WITHDRAWN'
    )
);
CREATE INDEX blind_date_opt_in_waiting_slot_idx ON blind_date_opt_in (starts_at, created_at, id) WHERE status = 'WAITING';
CREATE INDEX blind_date_opt_in_user_idx ON blind_date_opt_in (user_id, starts_at DESC);

CREATE TABLE community_group (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id uuid NOT NULL DEFAULT gen_random_uuid(),
    slug varchar(64) NOT NULL,
    name varchar(100) NOT NULL,
    description varchar(500) NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT community_group_public_id_key UNIQUE (public_id),
    CONSTRAINT community_group_slug_key UNIQUE (slug),
    CONSTRAINT community_group_slug_check CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE TABLE community_group_member (
    group_id uuid NOT NULL REFERENCES community_group (id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    joined_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT community_group_member_pk PRIMARY KEY (group_id, user_id),
    CONSTRAINT community_group_member_status_check CHECK (status IN ('ACTIVE', 'LEFT'))
);
CREATE INDEX community_group_member_user_idx ON community_group_member (user_id, status, group_id);

CREATE TABLE community_post (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    public_id uuid NOT NULL DEFAULT gen_random_uuid(),
    seq bigint GENERATED ALWAYS AS IDENTITY,
    group_id uuid NOT NULL REFERENCES community_group (id),
    author_id uuid REFERENCES app_user (id) ON DELETE SET NULL,
    body varchar(2000) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PUBLISHED',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT community_post_public_id_key UNIQUE (public_id),
    CONSTRAINT community_post_seq_key UNIQUE (seq),
    CONSTRAINT community_post_body_check CHECK (char_length(body) BETWEEN 1 AND 2000),
    CONSTRAINT community_post_status_check CHECK (status IN ('PUBLISHED', 'HIDDEN', 'REMOVED'))
);
CREATE INDEX community_post_feed_idx ON community_post (group_id, created_at DESC, public_id DESC) WHERE status = 'PUBLISHED';
CREATE INDEX community_post_author_idx ON community_post (author_id, created_at DESC);

CREATE TABLE community_post_reaction (
    post_id uuid NOT NULL REFERENCES community_post (id) ON DELETE CASCADE,
    user_id uuid NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    reaction varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT community_post_reaction_pk PRIMARY KEY (post_id, user_id),
    CONSTRAINT community_post_reaction_type_check CHECK (reaction IN ('LIKE', 'HEART', 'SUPPORT'))
);
CREATE INDEX community_post_reaction_user_idx ON community_post_reaction (user_id, post_id);

INSERT INTO community_group (slug, name, description) VALUES
 ('friendship-and-social', 'Friendship & Social', 'Meet people, make friends, and find your community at your own pace.'),
 ('dating-and-relationships', 'Dating & Relationships', 'Talk about dating, boundaries, communication, and healthy relationships.'),
 ('identity-and-belonging', 'Identity & Belonging', 'Share experiences about identity, belonging, and being yourself.'),
 ('events-and-meetups', 'Events & Meetups', 'Discover community activities and plan respectful, consent-first meetups.')
ON CONFLICT (slug) DO NOTHING;

-- Reports for user-generated community posts remain private and are not returned by feed endpoints.
ALTER TABLE user_report ADD COLUMN target_post_id uuid REFERENCES community_post (id);
ALTER TABLE user_report DROP CONSTRAINT user_report_target_type_check;
ALTER TABLE user_report ADD CONSTRAINT user_report_target_type_check
    CHECK (target_type IN ('PROFILE', 'MESSAGE', 'COMMUNITY_POST'));
ALTER TABLE user_report DROP CONSTRAINT user_report_target_check;
ALTER TABLE user_report ADD CONSTRAINT user_report_target_check CHECK (
    (target_type = 'PROFILE' AND target_message_id IS NULL AND target_post_id IS NULL)
    OR (target_type = 'MESSAGE' AND target_message_id IS NOT NULL AND target_post_id IS NULL)
    OR (target_type = 'COMMUNITY_POST' AND target_message_id IS NULL AND target_post_id IS NOT NULL)
);
CREATE UNIQUE INDEX user_report_open_post_key ON user_report (reporter_id, target_post_id)
    WHERE target_type = 'COMMUNITY_POST' AND status = 'PENDING';
