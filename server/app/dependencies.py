import time

from fastapi import Depends, Header, HTTPException

from .database import connect, one
from .security import hash_token


def current_user(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing token")
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise HTTPException(status_code=401, detail="Invalid token")
    with connect() as conn:
        session = one(conn, "SELECT * FROM sessions WHERE token_hash = ?", (hash_token(token),))
        if not session or session["revoked_at"] or int(session["expires_at"]) < int(time.time()):
            raise HTTPException(status_code=401, detail="Invalid token")
        user = one(conn, "SELECT * FROM users WHERE id = ?", (session["user_id"],))
        if not user:
            raise HTTPException(status_code=401, detail="User not found")
        if not user["active"]:
            raise HTTPException(status_code=403, detail="User disabled")
        if user["role"] == "unit_user":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=403, detail="Unit disabled")
        conn.execute("UPDATE sessions SET last_used_at = CURRENT_TIMESTAMP WHERE id = ?", (session["id"],))
        conn.commit()
    return user


def require_admin_user(user=Depends(current_user)):
    if user["role"] != "admin":
        raise HTTPException(status_code=403, detail="Admin only")
    return user


def require_unit_user(user=Depends(current_user)):
    if user["role"] != "unit_user":
        raise HTTPException(status_code=403, detail="Unit user only")
    if not user["unit_id"]:
        raise HTTPException(status_code=403, detail="Unit account missing unit")
    return user
