"""Retrieval over the approved/published corpus via pgvector + keyword fallback."""
import re

from . import config, db, embedder

_VECTOR = """
SELECT f.field_name, f.field_value, f.field_text, f.unit, f.period,
       c.name AS category, f.document_id, d.original_filename,
       ch.chunk_text,
       1 - (ch.embedding <=> %s::vector) AS similarity
FROM document_chunks ch
JOIN extracted_fields f ON f.id = ch.extracted_field_id
LEFT JOIN categories c ON c.id = f.category_id
LEFT JOIN documents d ON d.id = f.document_id
WHERE ch.embedding IS NOT NULL
  AND f.status IN ('approved', 'published')
ORDER BY ch.embedding <=> %s::vector
LIMIT %s
"""

_KEYWORD = """
SELECT f.field_name, f.field_value, f.field_text, f.unit, f.period,
       c.name AS category, f.document_id, d.original_filename,
       ch.chunk_text,
       0.0 AS similarity
FROM document_chunks ch
JOIN extracted_fields f ON f.id = ch.extracted_field_id
LEFT JOIN categories c ON c.id = f.category_id
LEFT JOIN documents d ON d.id = f.document_id
WHERE f.status IN ('approved', 'published')
  AND (ch.chunk_text ILIKE ANY(%s) OR c.name ILIKE ANY(%s))
LIMIT %s
"""

_STOP = {
    "the", "was", "were", "what", "when", "which", "how", "much", "many", "for",
    "and", "of", "in", "is", "are", "a", "an", "to", "on", "by", "with", "from",
    "that", "this", "there", "did", "does", "has", "have", "had", "will", "would",
}


def _tokens(text: str):
    return [t for t in re.findall(r"[a-z0-9\-]+", (text or "").lower())
            if len(t) >= 3 and t not in _STOP]


def retrieve(question: str, k: int | None = None) -> list[dict]:
    k = k or config.TOP_K
    vec = embedder.to_sql(embedder.embed(question))
    best: dict[str, dict] = {}
    with db.connect() as conn, conn.cursor() as cur:
        cur.execute(_VECTOR, (vec, vec, k * 3))
        for r in cur.fetchall():
            best[str(r["chunk_text"])] = r
        kws = ["%" + t + "%" for t in _tokens(question)]
        if kws:
            cur.execute(_KEYWORD, (kws, kws, k * 3))
            for r in cur.fetchall():
                best.setdefault(str(r["chunk_text"]), r)

    toks = set(_tokens(question))
    rows = list(best.values())
    for r in rows:
        chunk_toks = set(_tokens(r["chunk_text"]))
        overlap = len(toks & chunk_toks)
        r["score"] = (r.get("similarity") or 0.0) + 0.15 * overlap
    rows.sort(key=lambda r: r["score"], reverse=True)
    return rows
