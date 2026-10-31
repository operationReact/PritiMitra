-- P02-W03: server-side design experience preference. Additive only.

-- One row per account. Absence of a row means the default experience (spark).
CREATE TABLE user_design_experience (
    user_id     uuid        PRIMARY KEY REFERENCES app_user (id) ON DELETE CASCADE,
    experience  varchar(16) NOT NULL,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT user_design_experience_value_check CHECK (experience IN ('spark', 'essence'))
);
