from fastapi import Depends, Header, HTTPException

from .database import connect, one
from .security import decode_token


def current_user(authorization: str | None = Header(default=None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing token")
    payload = decode_token(authorization.removeprefix("Bearer ").strip())
    if not payload:
        raise HTTPException(status_code=401, detail="Invalid token")
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE id = ?", (payload.get("sub"),))
    if not user:
        raise HTTPException(status_code=401, detail="User not found")
    if not user["active"]:
        raise HTTPException(status_code=403, detail="User disabled")
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
