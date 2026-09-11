-- V3 — Discord-style roles + Google sign-in (additive; existing RBAC untouched).
ALTER TABLE roles ADD COLUMN IF NOT EXISTS color TEXT NOT NULL DEFAULT '#8b6cc1';

-- Discord-style seed colors, ordered by rank (hierarchy display).
UPDATE roles SET color = CASE name
    WHEN 'DATA_CORRECTOR' THEN '#7ddba3'  -- green
    WHEN 'SUB_SUPERVISOR' THEN '#6fc3df'  -- teal
    WHEN 'SUPERVISOR'     THEN '#6f9fdf'  -- blue
    WHEN 'MANAGER'        THEN '#e3b341'  -- gold
    WHEN 'ADMIN'          THEN '#e06c75'  -- red
    ELSE '#8b6cc1' END;

-- Google-linked accounts: password optional, identity = provider + subject.
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider TEXT NOT NULL DEFAULT 'local';
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_subject TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_provider_subject
    ON users(provider, provider_subject) WHERE provider <> 'local';
