"""Pluggable 384-dim embeddings.

Default is a dependency-free deterministic hash embedder so the service always
runs. Set CHAT_EMBEDDER=fastembed (and `pip install fastembed`) for the
open-weight BAAI/bge-small-en-v1.5 model (PRD §5).
"""
import hashlib
import math
import re

from . import config

_DIM = config.EMBEDDING_DIM
_TOKEN = re.compile(r"[a-z0-9]+")

try:  # optional
    from fastembed import TextEmbedding  # type: ignore
    _HAS_FASTEMBED = True
except Exception:  # pragma: no cover - optional dependency
    _HAS_FASTEMBED = False

_model = None


def _use_fastembed() -> bool:
    return config.EMBEDDER in ("auto", "fastembed", "bge") and _HAS_FASTEMBED


def _hash_embed(text: str):
    vec = [0.0] * _DIM
    for tok in _TOKEN.findall((text or "").lower()):
        h = int(hashlib.md5(tok.encode("utf-8")).hexdigest(), 16)
        vec[h % _DIM] += 1.0 if (h >> 7) & 1 else -1.0
    norm = math.sqrt(sum(v * v for v in vec))
    return [v / norm for v in vec] if norm else vec


def embed(text: str):
    global _model
    if _use_fastembed():
        if _model is None:
            _model = TextEmbedding(model_name="BAAI/bge-small-en-v1.5")
        return [float(x) for x in list(_model.embed([text]))[0]]
    return _hash_embed(text)


def to_sql(vec) -> str:
    """pgvector text literal, e.g. '[0.1,0.2,...]'."""
    return "[" + ",".join(f"{x:.6f}" for x in vec) + "]"
