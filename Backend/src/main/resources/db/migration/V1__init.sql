-- V1 — Phase 1 + Foundation schema (PRD §6, scoped to Phase 1).
-- Phase 2 (chat) and Phase 3 (draft) tables come in later migrations.
-- Conventions: UUID PKs, TIMESTAMPTZ, no silent deletes (status flags instead).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------- Identity & RBAC ----------
CREATE TABLE departments (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL UNIQUE,   -- DATA_CORRECTOR / SUB_SUPERVISOR / SUPERVISOR / MANAGER / ADMIN
    rank        INT  NOT NULL UNIQUE,   -- 10,20,30,40,100 : higher = more privilege
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    full_name     TEXT NOT NULL,
    role_id       UUID NOT NULL REFERENCES roles(id),
    department_id UUID REFERENCES departments(id),
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_role ON users(role_id);
CREATE INDEX idx_users_department ON users(department_id);

-- Configurable approval graph (NOT hardcoded hierarchy — PRD §2).
-- "A user with role can_approve_role_id may approve work submitted by role_id",
-- optionally scoped to one category (NULL = all categories).
CREATE TABLE approval_rules (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id             UUID NOT NULL REFERENCES roles(id),              -- submitter role
    can_approve_role_id UUID NOT NULL REFERENCES roles(id),              -- approver role
    category_id         UUID NULL,                                       -- FK added after categories exists
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (role_id, can_approve_role_id, category_id)
);

CREATE TABLE audit_log (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID REFERENCES users(id),
    action      TEXT NOT NULL,          -- e.g. FIELD_CORRECTED, FIELD_APPROVED, DOCUMENT_UPLOADED
    entity_type TEXT NOT NULL,          -- e.g. extracted_field, document
    entity_id   UUID,
    old_value   JSONB,
    new_value   JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_user ON audit_log(user_id);

-- ---------- Taxonomy ----------
CREATE TABLE categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT NOT NULL UNIQUE,   -- Yield, Expense, Budget, Safety, Geological Survey, ...
    parent_id   UUID REFERENCES categories(id),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE approval_rules
    ADD CONSTRAINT fk_approval_rules_category FOREIGN KEY (category_id) REFERENCES categories(id);

-- ---------- Ingestion ----------
CREATE TABLE documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id          UUID,                                   -- groups files unpacked from one ZIP
    original_filename TEXT NOT NULL,
    mime_type         TEXT NOT NULL,
    storage_path      TEXT NOT NULL,                          -- MinIO key: documents/{year}/{doc_id}/original.{ext}
    uploaded_by       UUID REFERENCES users(id),
    status            TEXT NOT NULL DEFAULT 'UPLOADED'
                      CHECK (status IN ('UPLOADED','PROCESSING','PROCESSED','FAILED','QUEUED_FOR_OCR')),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_batch ON documents(batch_id);
CREATE INDEX idx_documents_status ON documents(status);
CREATE INDEX idx_documents_uploaded_by ON documents(uploaded_by);

CREATE TABLE extracted_content (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    page_number      INT,
    block_type       TEXT NOT NULL DEFAULT 'text' CHECK (block_type IN ('text','table','figure','header','footer')),
    raw_text         TEXT,
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (confidence_score BETWEEN 0 AND 1),
    is_boilerplate   BOOLEAN NOT NULL DEFAULT FALSE,          -- PRD §4.6 ads/clutter removal
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_extracted_content_doc ON extracted_content(document_id);

CREATE TABLE extracted_fields (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id      UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    category_id      UUID REFERENCES categories(id),
    period           TEXT,                                     -- fiscal year/quarter, e.g. '2024-25'
    field_name       TEXT NOT NULL,
    field_value      DOUBLE PRECISION,
    field_text       TEXT,                                     -- non-numeric values
    unit             TEXT,
    confidence_score DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (confidence_score BETWEEN 0 AND 1),
    needs_review     BOOLEAN NOT NULL DEFAULT FALSE,
    status           TEXT NOT NULL DEFAULT 'pending_review'
                     CHECK (status IN ('draft','pending_review','approved','rejected','published')),
    version          INT NOT NULL DEFAULT 1,                   -- corrections create new versions
    created_by       UUID REFERENCES users(id),
    reviewed_by      UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_fields_doc ON extracted_fields(document_id);
CREATE INDEX idx_fields_category ON extracted_fields(category_id);
CREATE INDEX idx_fields_status ON extracted_fields(status);
CREATE INDEX idx_fields_period ON extracted_fields(period);

CREATE TABLE anomalies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    extracted_field_id  UUID NOT NULL REFERENCES extracted_fields(id) ON DELETE CASCADE,
    rule_name           TEXT NOT NULL,                        -- e.g. SUBSIDIARY_SUM_MISMATCH, YOY_DEVIATION
    description         TEXT NOT NULL,
    status              TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open','acknowledged','resolved')),
    detected_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_anomalies_field ON anomalies(extracted_field_id);
CREATE INDEX idx_anomalies_status ON anomalies(status);
