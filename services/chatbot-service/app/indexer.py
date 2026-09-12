"""Indexes approved/published figures into document_chunks (PRD §5.1).

Never indexes pending_review data, so the chatbot cannot leak unverified figures.
Idempotent: rebuild() replaces the chunk set wholesale.
"""
import re

from . import db, embedder

_METRIC = re.compile(r"[A-Za-z]{3,}")

_SQL = """
SELECT * FROM (
  SELECT DISTINCT ON (f.document_id, f.field_name, f.period)
         f.id, f.document_id, f.category_id, f.field_name, f.field_value, f.field_text,
         f.unit, f.period, f.status, f.version,
         c.name AS category, d.original_filename
  FROM extracted_fields f
  LEFT JOIN categories c ON c.id = f.category_id
  LEFT JOIN documents d ON d.id = f.document_id
  ORDER BY f.document_id, f.field_name, f.period, f.version DESC
) t
WHERE t.status IN ('approved', 'published')
"""


def _row_text(r: dict) -> str:
    val = r.get("field_value")
    if val is None:
        val = r.get("field_text")
    parts = [f"Field: {r['field_name']}"]
    if val is not None:
        parts.append(f"Value: {val} {(r.get('unit') or '').strip()}".strip())
    if r.get("period"):
        parts.append(f"Period: {r['period']}")
    if r.get("category"):
        parts.append(f"Category: {r['category']}")
    if r.get("original_filename"):
        parts.append(f"Source: {r['original_filename']}")
    return ". ".join(parts) + "."


def rebuild() -> int:
    with db.connect() as conn, conn.cursor() as cur:
        cur.execute(_SQL)
        rows = [r for r in cur.fetchall() if _METRIC.search(r["field_name"] or "")]
        cur.execute("DELETE FROM document_chunks")
        for r in rows:
            text = _row_text(r)
            vec = embedder.embed(text)
            cur.execute(
                "INSERT INTO document_chunks "
                "(document_id, extracted_field_id, chunk_text, embedding, category_id) "
                "VALUES (%s, %s, %s, %s::vector, %s)",
                (r["document_id"], r["id"], text, embedder.to_sql(vec), r["category_id"]),
            )
    return len(rows)


def count() -> int:
    with db.connect() as conn, conn.cursor() as cur:
        cur.execute("SELECT count(*) AS n FROM document_chunks")
        return cur.fetchone()["n"]
