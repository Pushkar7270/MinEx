"""Thin Postgres helpers. Phase 2 owns its tables; Phase 1 tables are read-only."""
import psycopg
from psycopg.rows import dict_row

from . import config


def connect() -> psycopg.Connection:
    return psycopg.connect(config.DATABASE_URL, row_factory=dict_row, autocommit=True)


# Idempotent DDL — Phase 2 owns these tables. It never alters Phase 1 tables, so
# reverting Phase 2 cannot affect the ingestion service.
_SCHEMA = """
CREATE EXTENSION IF NOT EXISTS vector;
CREATE TABLE IF NOT EXISTS document_chunks (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id        UUID REFERENCES documents(id) ON DELETE CASCADE,
    extracted_field_id UUID REFERENCES extracted_fields(id) ON DELETE CASCADE,
    chunk_text         TEXT NOT NULL,
    embedding          VECTOR(384),
    category_id        UUID REFERENCES categories(id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_document_chunks_document ON document_chunks(document_id);
CREATE INDEX IF NOT EXISTS idx_document_chunks_field ON document_chunks(extracted_field_id);
CREATE TABLE IF NOT EXISTS chat_sessions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS chat_messages (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id  UUID NOT NULL REFERENCES chat_sessions(id) ON DELETE CASCADE,
    role        TEXT NOT NULL CHECK (role IN ('user','assistant')),
    content     TEXT NOT NULL,
    sources     JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_messages_session ON chat_messages(session_id);
"""


def ensure_schema() -> None:
    with connect() as conn, conn.cursor() as cur:
        cur.execute(_SCHEMA)
