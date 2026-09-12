"""Runtime configuration for the Phase 2 RAG chatbot (env-driven, PRD §5/§9)."""
import os


def _env(name: str, default: str = "") -> str:
    return os.getenv(name, default)


# Shared Postgres (same instance as Phase 1).
DATABASE_URL = _env(
    "DATABASE_URL",
    "postgresql://intellireport:intellireport@localhost:5432/intellireport",
)

# Must match Phase 1's APP_JWT_SECRET so we can verify the same access tokens.
JWT_SECRET = _env("APP_JWT_SECRET", "change-me-to-a-256-bit-secret-for-local-dev-only-0123456789")
JWT_ALGORITHM = "HS256"

# LLM: none | ollama | openai-compatible (hosted needs explicit consent, PRD §9).
LLM_PROVIDER = _env("APP_LLM_PROVIDER", "none").strip().lower()
LLM_ENDPOINT = _env("APP_LLM_ENDPOINT", "http://localhost:11434").rstrip("/")
LLM_MODEL = _env("APP_LLM_MODEL", "llama3.1:8b")
LLM_API_KEY = _env("APP_LLM_API_KEY", "")

CORS_ORIGINS = [o.strip() for o in _env(
    "APP_CORS_ORIGINS", "http://localhost:3000,http://localhost:5173,http://localhost:8000"
).split(",") if o.strip()]

EMBEDDER = _env("CHAT_EMBEDDER", "hash").strip().lower()
EMBEDDING_DIM = 384  # matches document_chunks.embedding VECTOR(384)

TOP_K = int(_env("CHAT_TOP_K", "6"))
MIN_SCORE = float(_env("CHAT_MIN_SCORE", "0.12"))

REFUSAL = (
    "I could not find that in the verified data. "
    "I can only answer from approved or published figures."
)
