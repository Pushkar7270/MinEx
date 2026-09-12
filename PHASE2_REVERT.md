# Phase 2 (RAG Chatbot) — How to revert to Phase 1

Phase 2 is fully **additive** and lives on the `phase-2` git branch. `main` stays
Phase 1. **No Phase 1 code, tables, or APIs were modified** — the chatbot service
owns its own tables (`document_chunks`, `chat_sessions`, `chat_messages`) and only
*reads* approved/published rows.

## Revert (back to Phase 1)
```bash
git checkout main
docker compose down --remove-orphans          # removes the chatbot container too
docker compose up -d --build                  # rebuilds Phase 1 only
```
That's it — the dashboard, review queue, and Google auth are exactly as before.

## Optional: drop the Phase 2 tables (they are inert if you don't)
```sql
DROP TABLE IF EXISTS chat_messages, chat_sessions, document_chunks;
```
(The `vector` extension can stay; Phase 1 doesn't depend on it either way.)

## What Phase 2 adds (removed automatically by the steps above)
| Area | Files |
|---|---|
| Service | `services/chatbot-service/**` |
| Compose | `chatbot-service` block + `VITE_CHAT_URL` in `docker-compose.yml` |
| Frontend | `frontend/src/pages/Chat.jsx`, chat methods in `api.js`, `/chat` route in `App.jsx`, chat CSS in `theme.css`, `VITE_CHAT_URL` in `frontend/Dockerfile` |

## API key
The LLM key lives only in the gitignored `.env` (`APP_LLM_API_KEY`); it is never
committed. Remove/rotate it whenever you like.
