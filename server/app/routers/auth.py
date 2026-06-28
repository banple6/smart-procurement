from uuid import uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from ..database import connect, one, revoke_user_sessions
from ..dependencies import current_user
from ..schemas import ChangePasswordRequest, LoginRequest
from ..security import create_session_token, hash_password, hash_token, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


def public_user(user: dict) -> dict:
    return {
        "id": user["id"],
        "username": user["username"],
        "display_name": user["display_name"],
        "role": user["role"],
        "unit_id": user["unit_id"],
        "active": bool(user["active"]),
        "must_change_password": bool(user["must_change_password"]),
    }


@router.post("/login")
def login(body: LoginRequest, request: Request):
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE username = ?", (body.username,))
        if not user or not verify_password(body.password, user["password_hash"]):
            raise HTTPException(status_code=401, detail="Invalid username or password")
        if not user["active"]:
            raise HTTPException(status_code=403, detail="User disabled")
        if user["role"] == "unit_user":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=403, detail="Unit disabled")
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
        return {"token": token, "expires_at": expires_at, "user": public_user(user)}


@router.get("/me")
def me(user=Depends(current_user)):
    return public_user(user)


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
        raise HTTPException(status_code=401, detail="Old password invalid")
    with connect() as conn:
        conn.execute(
            "UPDATE users SET password_hash = ?, must_change_password = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (hash_password(body.new_password), user["id"]),
        )
        revoke_user_sessions(conn, user["id"])
        conn.commit()
    return {"ok": True}
