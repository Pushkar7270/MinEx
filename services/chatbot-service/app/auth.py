"""JWT verification — reuses the same HS256 token Phase 1 issues.

We only read the token; we never mint or mutate auth state, so Phase 1's
security model is untouched. `role`/`sub` come straight from Phase 1.
"""
import jwt
from fastapi import Header, HTTPException

from . import config

# JJWT picks the HMAC strength from the secret length (HS256/384/512); accept the
# whole HMAC family so we verify whatever Phase 1 issued, with the same key.
_ALLOWED_ALGS = ["HS256", "HS384", "HS512"]


def claims(authorization: str | None = Header(default=None)) -> dict:
    if not authorization or not authorization.lower().startswith("bearer "):
        raise HTTPException(status_code=401, detail="Missing bearer token")
    token = authorization.split(" ", 1)[1]
    try:
        return jwt.decode(token, config.JWT_SECRET, algorithms=_ALLOWED_ALGS)
    except Exception:
        raise HTTPException(status_code=401, detail="Invalid or expired token")
