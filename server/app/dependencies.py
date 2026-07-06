import time

from fastapi import Depends, Header, HTTPException, Request

from .database import connect, one
from .security import hash_token
from .web_session import web_idle_seconds, web_session_cookie_name


def current_user(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(status_code=401, detail="登录已过期，请重新登录")
    with connect() as conn:
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
        conn.execute("UPDATE sessions SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", (session["id"],))
        conn.commit()
    return user


def require_admin_user(user=Depends(current_user)):
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="当前账号无管理员权限")
    return user


def current_web_user(request: Request):
    token = request.cookies.get(web_session_cookie_name(), "").strip()
    if not token:
        raise HTTPException(status_code=401, detail="登录状态已失效，请重新扫码登录")
    with connect() as conn:
        session = one(conn, "SELECT * FROM web_sessions WHERE token_hash = ?", (hash_token(token),))
        if (
            not session
            or session["revoked_at"]
            or one(conn, "SELECT CURRENT_TIMESTAMP > ? AS expired", (session["idle_expires_at"],))["expired"]
            or one(conn, "SELECT CURRENT_TIMESTAMP > ? AS expired", (session["absolute_expires_at"],))["expired"]
        ):
            raise HTTPException(status_code=401, detail="登录状态已失效，请重新扫码登录")
        user = one(conn, "SELECT * FROM users WHERE id = ?", (session["user_id"],))
        if not user:
            raise HTTPException(status_code=401, detail="登录状态已失效，请重新扫码登录")
        if not user["active"]:
            raise HTTPException(status_code=403, detail="账号已停用，请联系管理员")
        if user["must_change_password"]:
            raise HTTPException(status_code=403, detail="请先在 App 修改初始密码")
        if user["role"] == "unit_user":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=403, detail="所属单位已停用")
        conn.execute(
            """
            UPDATE web_sessions
            SET last_seen_at = CURRENT_TIMESTAMP,
                idle_expires_at = datetime('now', ?)
            WHERE id = ?
            """,
            (f"+{web_idle_seconds()} seconds", session["id"]),
        )
        conn.commit()
        return user


def require_web_admin_user(user=Depends(current_web_user)):
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="当前账号无管理员权限")
    return user


def current_bearer_user(user=Depends(current_user)):
    return user


def require_system_status(user=Depends(require_admin_user)):
    if not bool(user.get("can_view_system_status", 0)):
        raise HTTPException(status_code=403, detail="当前账号无权查看系统状态")
    return {**dict(user), "can_view_detailed_metrics": bool(user.get("can_view_detailed_metrics", 0))}


def require_manage_backups(user=Depends(require_admin_user)):
    if not bool(user.get("can_manage_backups", 0)):
        raise HTTPException(status_code=403, detail="当前账号无权管理备份")
    return user


def require_unit_user(user=Depends(current_user)):
    if user["role"] != "unit_user":
        raise HTTPException(status_code=403, detail="当前账号不能提交订单")
    if not user["unit_id"]:
        raise HTTPException(status_code=403, detail="账号未绑定所属单位")
    return user
