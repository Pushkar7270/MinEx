"""Answer generation. Hosted/local LLM when configured, else a deterministic
extractive answer built only from the retrieved verified figures."""
import httpx

from . import config

_SYSTEM = (
    "You are MinEx AI for a mining-intelligence platform. "
    "Answer ONLY using the VERIFIED FIGURES provided. Never invent or estimate numbers. "
    "If the figures do not contain the answer, reply exactly: " + config.REFUSAL
)


def generate(question: str, chunks: list[dict]) -> str | None:
    if config.LLM_PROVIDER in ("", "none"):
        return None
    context = "\n".join(f"- {c['chunk_text']}" for c in chunks)
    prompt = f"VERIFIED FIGURES:\n{context}\n\nQUESTION: {question}"
    messages = [
        {"role": "system", "content": _SYSTEM},
        {"role": "user", "content": prompt},
    ]
    try:
        if config.LLM_PROVIDER == "ollama":
            r = httpx.post(
                config.LLM_ENDPOINT + "/api/chat",
                json={"model": config.LLM_MODEL, "stream": False, "messages": messages},
                timeout=60,
            )
            r.raise_for_status()
            return (r.json().get("message") or {}).get("content")
        # openai-compatible hosted endpoint (explicit-consent only, PRD §9)
        r = httpx.post(
            config.LLM_ENDPOINT + "/chat/completions",
            headers={"Authorization": f"Bearer {config.LLM_API_KEY}"},
            json={"model": config.LLM_MODEL, "temperature": 0, "messages": messages},
            timeout=60,
        )
        r.raise_for_status()
        return r.json()["choices"][0]["message"]["content"]
    except Exception as ex:  # never fail the request — fall back to extractive answer
        print(f"[chat] LLM call failed, using extractive answer: {ex}")
        return None
