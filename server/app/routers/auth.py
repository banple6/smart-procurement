from fastapi import APIRouter, Depends, HTTPException

from ..database import connect, one
from ..dependencies import current_user
from ..schemas import ChangePasswordRequest, LoginRequest
from ..security import create_token, hash_password, verify_password

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
def login(body: LoginRequest):
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE username = ?", (body.username,))
    if not user or not verify_password(body.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Invalid username or password")
    if not user["active"]:
        raise HTTPException(status_code=403, detail="User disabled")
    token, expires_at = create_token({"sub": user["id"], "role": user["role"]})
    return {"token": token, "expires_at": expires_at, "user": public_user(user)}


@router.get("/me")
def me(user=Depends(current_user)):
    return public_user(user)


@router.post("/logout")
def logout():
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
        conn.commit()
    return {"ok": True}

