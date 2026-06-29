from uuid import uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from ..database import connect, one, revoke_user_sessions
from ..dependencies import current_user
from ..schemas import ChangePasswordRequest, LoginRequest
from ..security import create_session_token, hash_password, hash_token, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


def validate_new_password(username: str, password: str):
    if password.lower() == username.lower():
        raise HTTPException(status_code=400, detail="新密码不能与账号相同")
    has_letter = any(ch.isalpha() for ch in password)
    has_digit = any(ch.isdigit() for ch in password)
    if len(password) < 8 or not has_letter or not has_digit:
        raise HTTPException(status_code=400, detail="密码至少 8 位，且包含字母和数字")


def public_user(user: dict, unit: dict | None = None) -> dict:
    return {
        "id": user["id"],
        "username": user["username"],
        "display_name": user["display_name"],
        "role": user["role"],
        "unit_id": user["unit_id"] or "",
        "unit_code": unit["unit_code"] if unit else "",
        "unit_name": unit["unit_name"] if unit else "",
        "default_delivery_point": unit["default_delivery_point"] if unit else "",
        "active": bool(user["active"]),
        "must_change_password": bool(user["must_change_password"]),
    }


@router.post("/login")
def login(body: LoginRequest, request: Request):
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE username = ?", (body.username,))
        if not user or not verify_password(body.password, user["password_hash"]):
            raise HTTPException(status_code=401, detail="账号或密码错误")
        if not user["active"]:
            raise HTTPException(status_code=403, detail="账号已停用，请联系管理员")
        unit = None
        if user["role"] == "unit_user":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=403, detail="所属单位已停用")
        token, token_hash, expires_at = create_session_token()
        conn.execute(
            """
            INSERT INTO sessions(id, token_hash, user_id, expires_at, client_info, ip_address)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()),
                token_hash,
                user["id"],
                expires_at,
                request.headers.get("user-agent", ""),
                request.client.host if request.client else "",
            ),
        )
        conn.execute("UPDATE users SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?", (user["id"],))
        conn.commit()
        return {"token": token, "expires_at": expires_at, "user": public_user(user, unit)}


@router.get("/me")
def me(user=Depends(current_user)):
    unit = None
    if user["unit_id"]:
        with connect() as conn:
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
    return public_user(user, unit)


@router.post("/logout")
def logout(authorization: str | None = Header(default=None)):
    if authorization and authorization.startswith("Bearer "):
        token = authorization.removeprefix("Bearer ").strip()
        with connect() as conn:
            conn.execute(
                "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE token_hash = ? AND revoked_at IS NULL",
                (hash_token(token),),
            )
            conn.commit()
    return {"ok": True}


@router.post("/change-password")
def change_password(body: ChangePasswordRequest, user=Depends(current_user)):
    if not verify_password(body.old_password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="原密码错误")
    validate_new_password(user["username"], body.new_password)
    with connect() as conn:
        conn.execute(
            "UPDATE users SET password_hash = ?, must_change_password = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (hash_password(body.new_password), user["id"]),
        )
        revoke_user_sessions(conn, user["id"])
        conn.commit()
    return {"ok": True}
