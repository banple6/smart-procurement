import json
import os
import re
import time
from pathlib import Path
from uuid import uuid4

from fastapi import Request

from ..database import connect, one, write_audit
from ..security import hash_token
from ..web_session import web_session_cookie_name


UNSAFE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}
SENSITIVE_RE = re.compile(r"(password|token|authorization|cookie|secret|验证码|密码)", re.IGNORECASE)


def system_log_dir() -> Path:
    root = os.getenv("SYSTEM_LOG_DIR", str(Path(__file__).resolve().parents[2] / "logs"))
    path = Path(root)
    path.mkdir(parents=True, exist_ok=True)
    return path


def system_log_path() -> Path:
    return system_log_dir() / "system-events.log"


def sanitize_text(value: str, limit: int = 500) -> str:
    text = str(value or "").replace("\r", " ").replace("\n", " ").strip()
    if SENSITIVE_RE.search(text):
        return "[已脱敏]"
    return text[:limit]


def request_id(request: Request) -> str:
    return request.headers.get("x-request-id", "").strip()[:80] or str(uuid4())


def client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "").split(",")[0].strip()
    if forwarded:
        return forwarded[:80]
    return request.client.host[:80] if request.client else ""


def resolve_actor(request: Request) -> tuple[str | None, str]:
    authorization = request.headers.get("authorization", "")
    try:
        with connect() as conn:
            if authorization.startswith("Bearer "):
                token = authorization.removeprefix("Bearer ").strip()
                session = one(conn, "SELECT user_id FROM sessions WHERE token_hash = ?", (hash_token(token),))
                if session:
                    user = one(conn, "SELECT id, role FROM users WHERE id = ?", (session["user_id"],))
                    if user:
                        return user["id"], user["role"]
            web_token = request.cookies.get(web_session_cookie_name(), "").strip()
            if web_token:
                session = one(conn, "SELECT user_id, role FROM web_sessions WHERE token_hash = ?", (hash_token(web_token),))
                if session:
                    return session["user_id"], session["role"]
    except Exception:
        return None, "unknown"
    return None, "anonymous"


def action_for_request(method: str, path: str) -> str:
    cleaned = path.strip("/").replace("/", "_").replace("-", "_")
    cleaned = re.sub(r"[^A-Za-z0-9_]+", "_", cleaned).upper()[:80]
    return f"HTTP_{method.upper()}_{cleaned or 'ROOT'}"


def object_type_for_path(path: str) -> str:
    if "/orders" in path:
        return "order"
    if "/products" in path:
        return "product"
    if "/units" in path:
        return "unit"
    if "/users" in path or "/accounts" in path:
        return "user"
    if "/web-auth" in path:
        return "web_session"
    if "/app-update" in path:
        return "app_update"
    if "/system" in path:
        return "system"
    return "request"


def append_system_event(event: dict):
    payload = {
        **event,
        "created_at_epoch": int(time.time()),
    }
    with system_log_path().open("a", encoding="utf-8") as handle:
        handle.write(json.dumps(payload, ensure_ascii=False, separators=(",", ":")) + "\n")


def record_request_audit(
    request: Request,
    status_code: int,
    duration_ms: int,
    error_message: str = "",
):
    method = request.method.upper()
    path = request.url.path
    if method not in UNSAFE_METHODS:
        return
    if path.endswith("/client-errors"):
        return
    actor_id, actor_role = resolve_actor(request)
    result = "success" if status_code < 400 and not error_message else "failure"
    rid = request_id(request)
    event = {
        "request_id": rid,
        "actor_id": actor_id or "",
        "actor_role": actor_role,
        "action": action_for_request(method, path),
        "object_type": object_type_for_path(path),
        "method": method,
        "path": path,
        "status_code": status_code,
        "result": result,
        "ip_address": client_ip(request),
        "user_agent": sanitize_text(request.headers.get("user-agent", ""), 300),
        "error_message": sanitize_text(error_message),
        "duration_ms": duration_ms,
    }
    append_system_event(event)
    try:
        with connect() as conn:
            write_audit(
                conn,
                actor_id,
                actor_role,
                event["action"],
                event["object_type"],
                ip_address=event["ip_address"],
                result=result,
                method=method,
                path=path,
                status_code=status_code,
                request_id=rid,
                user_agent=event["user_agent"],
                error_message=event["error_message"],
                duration_ms=duration_ms,
            )
            conn.commit()
    except Exception as exc:
        append_system_event({**event, "audit_write_error": sanitize_text(str(exc))})
