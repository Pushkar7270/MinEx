# MinEx Chatbot Service — Phase 2 (RAG)

FastAPI microservice that answers questions **only** from approved/published
figures, cites sources, and refuses rather than hallucinates (PRD §5).

Phase 1 (the Java `Backend/`) is **not modified at all** — this service owns its
own tables (`document_chunks`, `chat_sessions`, `chat_messages`) and only reads
Phase 1's published rows. That makes Phase 2 fully revertible.

- Retrieval: pgvector over `document_chunks` + keyword fallback.
- Embeddings: `hash` (default, zero-dependency, 384-dim) or set
  `CHAT_EMBEDDER=fastembed` and install `fastembed` for BAAI/bge-small-en-v1.5.
- LLM: `APP_LLM_PROVIDER=none|ollama|openai-compatible`. If none/fails, it returns
  a deterministic extractive answer from the retrieved figures.
- Auth: verifies the same Phase 1 HS256 JWT (`APP_JWT_SECRET`). Read-only over Phase 1.

## Endpoints
| Method | Path | Notes |
|---|---|---|
| POST | `/api/v2/chat/query` | `{ "message": "...", "session_id": null }` → `{ session_id, answer, sources }` |
| GET  | `/api/v2/chat/sessions/{id}` | conversation history (owner only) |
| POST | `/api/v2/chat/index` | admin: rebuild the index from published figures |
| GET  | `/health` | liveness |

## Tests
`docker compose exec chatbot-service python -m unittest discover -s tests -v`

## Revert to Phase 1
See `PHASE2_REVERT.md` at the repo root.
