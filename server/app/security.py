import base64
import hashlib
import hmac
import json
import os
import secrets
import time
from typing import Any


def hash_password(password: str) -> str:
    salt = secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), 150_000)
    return f"pbkdf2_sha256$150000${salt}${digest.hex()}"


def verify_password(password: str, encoded: str) -> bool:
    try:
        method, iterations, salt, digest = encoded.split("$")
        if method != "pbkdf2_sha256":
            return False
        actual = hashlib.pbkdf2_hmac("sha256", password.encode(), bytes.fromhex(salt), int(iterations))
        return hmac.compare_digest(actual.hex(), digest)
    except Exception:
        return False


def secret_key() -> str:
    value = os.getenv("APP_SECRET", "")
    if not value and os.getenv("APP_ENV") != "production":
        return "dev-only-secret"
    if not value:
        raise RuntimeError("APP_SECRET is required in production")
    return value


def create_token(payload: dict[str, Any], hours: int | None = None) -> tuple[str, int]:
    exp = int(time.time()) + int(hours or int(os.getenv("SESSION_HOURS", "72"))) * 3600
    body = {**payload, "exp": exp}
    raw = base64.urlsafe_b64encode(json.dumps(body, separators=(",", ":")).encode()).decode().rstrip("=")
    sig = hmac.new(secret_key().encode(), raw.encode(), hashlib.sha256).hexdigest()
    return f"{raw}.{sig}", exp


def decode_token(token: str) -> dict[str, Any] | None:
    try:
        raw, sig = token.split(".", 1)
        expected = hmac.new(secret_key().encode(), raw.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(sig, expected):
            return None
        padded = raw + "=" * (-len(raw) % 4)
        payload = json.loads(base64.urlsafe_b64decode(padded.encode()))
        if int(payload.get("exp", 0)) < int(time.time()):
            return None
        return payload
    except Exception:
        return None


def create_session_token(hours: int | None = None) -> tuple[str, str, int]:
    token = secrets.token_urlsafe(48)
    exp = int(time.time()) + int(hours or int(os.getenv("SESSION_HOURS", "72"))) * 3600
    return token, hash_token(token), exp


def hash_token(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()
