"""Phase 2 RAG chatbot API (PRD §5.2).

POST /api/v2/chat/query   { session_id?, message } -> { session_id, answer, sources }
GET  /api/v2/chat/sessions/{id}
POST /api/v2/chat/index   (admin) rebuild the pgvector index over published data
GET  /health
"""
import json
import time
from contextlib import asynccontextmanager
from typing import Optional

from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from . import answer, config, db, indexer, rag
from .auth import claims


@asynccontextmanager
async def lifespan(_: FastAPI):
    # Retry while Phase 1's Flyway migrations finish (fresh-start race), then
    # build the index if empty. Never block or crash startup.
    for attempt in range(10):
        try:
            db.ensure_schema()
            if indexer.count() == 0:
                print(f"[chat] indexed {indexer.rebuild()} published figures")
            break
        except Exception as ex:  # noqa: BLE001 - demo resilience
            if attempt == 9:
                print(f"[chat] startup indexing skipped: {ex}")
            else:
                time.sleep(3)
    yield


app = FastAPI(title="MinEx Chatbot — Phase 2 (RAG)", version="1.0.0", lifespan=lifespan)
app.add_middleware(
    CORSMiddleware,
    allow_origins=config.CORS_ORIGINS or ["*"],
    allow_methods=["*"],
    allow_headers=["*"],
    allow_credentials=False,
)


class QueryRequest(BaseModel):
    message: str
    session_id: Optional[str] = None


def _user_id(cur, email: str):
    cur.execute("SELECT id FROM users WHERE lower(email) = lower(%s)", (email,))
    row = cur.fetchone()
    if not row:
        raise HTTPException(status_code=401, detail="Unknown user")
    return row["id"]


def _loads(value):
    if value is None:
        return []
    if isinstance(value, str):
        try:
            return json.loads(value)
        except Exception:  # noqa: BLE001
            return []
    return value


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/api/v2/chat/index")
def reindex(user=Depends(claims)):
    if str(user.get("role", "")).upper() != "ADMIN":
        raise HTTPException(status_code=403, detail="Admin only")
    return {"indexed": indexer.rebuild(), "total": indexer.count()}


@app.post("/api/v2/chat/query")
def query(req: QueryRequest, user=Depends(claims)):
    message = (req.message or "").strip()
    if not message:
        raise HTTPException(status_code=400, detail="Empty message")

    with db.connect() as conn, conn.cursor() as cur:
        uid = _user_id(cur, user.get("sub", ""))
        if req.session_id:
            cur.execute(
                "SELECT id FROM chat_sessions WHERE id = %s AND user_id = %s",
                (req.session_id, uid),
            )
            row = cur.fetchone()
            if not row:
                raise HTTPException(status_code=404, detail="Session not found")
            session_id = row["id"]
        else:
            cur.execute("INSERT INTO chat_sessions (user_id) VALUES (%s) RETURNING id", (uid,))
            session_id = cur.fetchone()["id"]
        cur.execute(
            "INSERT INTO chat_messages (session_id, role, content) VALUES (%s, 'user', %s)",
            (session_id, message),
        )

    candidates = rag.retrieve(message)
    qualifying = [c for c in candidates if (c.get("score") or 0) >= config.MIN_SCORE][: config.TOP_K]

    if not qualifying:
        text, sources = config.REFUSAL, []
    else:
        text = answer.generate(message, qualifying) or _extractive(qualifying)
        sources = [] if config.REFUSAL in text else [_source(c) for c in qualifying]

    with db.connect() as conn, conn.cursor() as cur:
        cur.execute(
            "INSERT INTO chat_messages (session_id, role, content, sources) "
            "VALUES (%s, 'assistant', %s, %s::jsonb)",
            (session_id, text, json.dumps(sources)),
        )
    return {"session_id": str(session_id), "answer": text, "sources": sources}


@app.get("/api/v2/chat/sessions/{session_id}")
def session(session_id: str, user=Depends(claims)):
    with db.connect() as conn, conn.cursor() as cur:
        uid = _user_id(cur, user.get("sub", ""))
        cur.execute(
            "SELECT id FROM chat_sessions WHERE id = %s AND user_id = %s",
            (session_id, uid),
        )
        if not cur.fetchone():
            raise HTTPException(status_code=404, detail="Session not found")
        cur.execute(
            "SELECT role, content, sources, created_at FROM chat_messages "
            "WHERE session_id = %s ORDER BY created_at",
            (session_id,),
        )
        rows = cur.fetchall()
    return {
        "session_id": session_id,
        "messages": [
            {
                "role": m["role"],
                "content": m["content"],
                "sources": _loads(m["sources"]),
                "created_at": m["created_at"].isoformat() if m["created_at"] else None,
            }
            for m in rows
        ],
    }


def _extractive(chunks: list[dict]) -> str:
    lines = ["Based on verified figures:"]
    for c in chunks[:3]:
        val = c.get("field_value")
        if val is None:
            val = c.get("field_text")
        unit = (c.get("unit") or "").strip()
        period = f" ({c['period']})" if c.get("period") else ""
        cat = f" — {c['category']}" if c.get("category") else ""
        lines.append(f"• {c['field_name']}{period}: {val} {unit}{cat}".rstrip())
    return "\n".join(lines)


def _source(c: dict) -> dict:
    return {
        "document_id": str(c["document_id"]) if c.get("document_id") else None,
        "document": c.get("original_filename"),
        "category": c.get("category"),
        "field": c.get("field_name"),
        "value": c.get("field_value"),
        "unit": c.get("unit"),
        "period": c.get("period"),
    }
