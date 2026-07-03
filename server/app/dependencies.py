import time

from fastapi import Depends, Header, HTTPException, Request

from .database import connect, one
from .security import hash_token
from .web_session import CSRF_COOKIE, web_idle_seconds, web_session_cookie_name


UNSAFE_METHODS = {"POST", "PUT", "PATCH", "DELETE"}


def session_token_from_request(request: Request, authorization: str | None) -> tuple[str, str]:
    if authorization and authorization.startswith("Bearer "):
        return authorization.removeprefix("Bearer ").strip(), "bearer"
    cookie_token = request.cookies.get(web_session_cookie_name(), "").strip()
    if cookie_token:
        return cookie_token, "web"
    return "", ""


def verify_csrf_for_cookie_session(request: Request, session_kind: str):
    if session_kind != "web" or request.method.upper() not in UNSAFE_METHODS:
        return
    cookie_value = request.cookies.get(CSRF_COOKIE, "")
    header_value = request.headers.get("x-csrf-token", "")
    if not cookie_value or not header_value or cookie_value != header_value:
        raise HTTPException(status_code=403, detail="请求已过期，请刷新页面后重试")


def current_user(request: Request, authorization: str | None = Header(default=None)):
    token, session_kind = session_token_from_request(request, authorization)
    if not token:
        raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
    verify_csrf_for_cookie_session(request, session_kind)
    with connect() as conn:
        if session_kind == "web":
            session = one(
                conn,
                """
                SELECT *,
                  CURRENT_TIMESTAMP > idle_expires_at AS idle_expired,
                  CURRENT_TIMESTAMP > absolute_expires_at AS absolute_expired
                FROM web_sessions
                WHERE token_hash = ?
                """,
                (hash_token(token),),
            )
            if not session or session["revoked_at"] or session["idle_expired"] or session["absolute_expired"]:
                raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
        else:
            session = one(conn, "SELECT * FROM sessions WHERE token_hash = ?", (hash_token(token),))
            if not session or session["revoked_at"] or int(session["expires_at"]) < int(time.time()):
                raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
        user = one(conn, "SELECT * FROM users WHERE id = ?", (session["user_id"],))
        if not user:
            raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
        if not user["active"]:
            raise HTTPException(status_code=403, detail="账号已停用，请联系管理员")
        if user["role"] == "unit_user":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=403, detail="所属单位已停用")
        if session_kind == "web":
            conn.execute(
                "UPDATE web_sessions SET last_seen_at = CURRENT_TIMESTAMP, idle_expires_at = datetime('now', ?) WHERE id = ?",
                (f"+{web_idle_seconds()} seconds", session["id"]),
            )
        else:
            conn.execute("UPDATE sessions SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", (session["id"],))
        conn.commit()
    return user


def current_bearer_user(request: Request, authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
    return current_user(request, authorization)


def require_admin_user(user=Depends(current_user)):
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="当前账号无管理员权限")
    return user


def require_unit_user(user=Depends(current_user)):
    if user["role"] != "unit_user":
        raise HTTPException(status_code=403, detail="当前账号不能提交订单")
    if not user["unit_id"]:
        raise HTTPException(status_code=403, detail="账号未绑定所属单位")
    return user
