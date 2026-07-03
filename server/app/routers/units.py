import json
import re
import sqlite3
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException

from ..database import all_rows, connect, one, revoke_unit_sessions, revoke_user_sessions, write_audit
from ..dependencies import require_admin_user
from ..schemas import ResetPasswordRequest, StatusPatch, UnitCreate, UnitUpdate, UserCreate, UserUpdate
from ..security import hash_password

router = APIRouter(prefix="/admin", tags=["admin"])

USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]{4,32}$")


def normalize_username(username: str) -> str:
    return username.strip().lower()


def validate_username(username: str):
    if not USERNAME_RE.fullmatch(username):
        raise HTTPException(status_code=400, detail="账号只能使用 4-32 位字母、数字和下划线")


def validate_password(username: str, password: str):
    if password.lower() == username.lower():
        raise HTTPException(status_code=400, detail="新密码不能与账号相同")
    if len(password) < 8 or not any(ch.isalpha() for ch in password) or not any(ch.isdigit() for ch in password):
        raise HTTPException(status_code=400, detail="密码至少 8 位，且包含字母和数字")


def unit_for_new_user(conn, unit_id: str | None):
    if not unit_id:
        raise HTTPException(status_code=400, detail="请选择所属单位")
    unit = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
    if not unit:
        raise HTTPException(status_code=400, detail="请选择所属单位")
    if not unit["active"]:
        raise HTTPException(status_code=400, detail="该单位已停用，暂时不能创建账号")
    return unit


def public_admin_user(conn, user_id: str):
    return one(
        conn,
        """
        SELECT u.id, u.username, u.display_name, u.role, u.unit_id, units.unit_name,
          u.active, u.must_change_password, u.last_login_at, u.created_at, u.updated_at
        FROM users u
        LEFT JOIN units ON units.id = u.unit_id
        WHERE u.id = ?
        """,
        (user_id,),
    )


@router.get("/units")
def list_units(admin=Depends(require_admin_user)):
    with connect() as conn:
        return all_rows(
            conn,
            """
            SELECT u.*,
              (SELECT COUNT(*) FROM users WHERE unit_id = u.id) AS account_count,
              (SELECT COUNT(*) FROM orders WHERE unit_id = u.id) AS order_count,
              (SELECT MAX(created_at) FROM orders WHERE unit_id = u.id) AS last_order_at
            FROM units u
            ORDER BY u.created_at DESC
            """,
        )


@router.post("/units")
def create_unit(body: UnitCreate, admin=Depends(require_admin_user)):
    unit_id = str(uuid4())
    with connect() as conn:
        conn.execute(
            "INSERT INTO units(id, unit_code, unit_name, default_delivery_point, address_note) VALUES (?, ?, ?, ?, ?)",
            (unit_id, body.unit_code, body.unit_name, body.default_delivery_point, body.address_note),
        )
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_CREATE_UNIT",
            "unit",
            unit_id,
            after_json=json.dumps({"unit_code": body.unit_code, "unit_name": body.unit_name}, ensure_ascii=False),
        )
        conn.commit()
        return one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))


@router.put("/units/{unit_id}")
def update_unit(unit_id: str, body: UnitUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    if not fields:
        raise HTTPException(status_code=400, detail="请填写需要保存的内容")
    assignments = ", ".join(f"{key} = ?" for key in fields)
    values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
    with connect() as conn:
        before = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
        if not before:
            raise HTTPException(status_code=404, detail="单位不存在")
        conn.execute(f"UPDATE units SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, unit_id))
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_UPDATE_UNIT",
            "unit",
            unit_id,
            before_json=json.dumps({key: before.get(key) for key in fields}, ensure_ascii=False),
            after_json=json.dumps(fields, ensure_ascii=False),
        )
        conn.commit()
        return one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))


@router.patch("/units/{unit_id}/status")
def update_unit_status(unit_id: str, body: StatusPatch, admin=Depends(require_admin_user)):
    with connect() as conn:
        before = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
        if not before:
            raise HTTPException(status_code=404, detail="单位不存在")
        conn.execute("UPDATE units SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(body.active), unit_id))
        if not body.active:
            revoke_unit_sessions(conn, unit_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_ENABLE_UNIT" if body.active else "ADMIN_DISABLE_UNIT",
            "unit",
            unit_id,
            before_json=json.dumps({"active": bool(before["active"])}, ensure_ascii=False),
            after_json=json.dumps({"active": body.active}, ensure_ascii=False),
        )
        conn.commit()
        return one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))


@router.get("/users")
def list_users(admin=Depends(require_admin_user)):
    with connect() as conn:
        return all_rows(
            conn,
            """
            SELECT u.id, u.username, u.display_name, u.role, u.unit_id, units.unit_name,
              u.active, u.must_change_password, u.last_login_at, u.created_at, u.updated_at
            FROM users u
            LEFT JOIN units ON units.id = u.unit_id
            ORDER BY u.created_at DESC
            """,
        )


@router.post("/users")
def create_user(body: UserCreate, admin=Depends(require_admin_user)):
    username = normalize_username(body.username)
    validate_username(username)
    validate_password(username, body.password)
    user_id = str(uuid4())
    with connect() as conn:
        unit_for_new_user(conn, body.unit_id)
        try:
            conn.execute(
                """
                INSERT INTO users(id, username, password_hash, display_name, role, unit_id, must_change_password)
                VALUES (?, ?, ?, ?, 'unit_user', ?, ?)
                """,
                (user_id, username, hash_password(body.password), body.display_name.strip(), body.unit_id, int(body.must_change_password)),
            )
        except sqlite3.IntegrityError:
            raise HTTPException(status_code=409, detail="该账号已存在") from None
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_CREATE_USER",
            "user",
            user_id,
            after_json=json.dumps({"username": username, "role": "unit_user", "unit_id": body.unit_id}, ensure_ascii=False),
        )
        conn.commit()
        user = public_admin_user(conn, user_id)
        user["initial_password"] = body.password
        user["message"] = "账号已创建"
        return user


@router.put("/users/{user_id}")
def update_user(user_id: str, body: UserUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    if not fields:
        raise HTTPException(status_code=400, detail="请填写需要保存的内容")
    assignments = ", ".join(f"{key} = ?" for key in fields)
    values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
    with connect() as conn:
        before = one(conn, "SELECT * FROM users WHERE id = ?", (user_id,))
        if not before:
            raise HTTPException(status_code=404, detail="账号不存在")
        if "unit_id" in fields:
            unit_for_new_user(conn, fields["unit_id"])
        conn.execute(f"UPDATE users SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, user_id))
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_UPDATE_USER",
            "user",
            user_id,
            before_json=json.dumps({key: before.get(key) for key in fields}, ensure_ascii=False),
            after_json=json.dumps(fields, ensure_ascii=False),
        )
        conn.commit()
        return public_admin_user(conn, user_id)


@router.post("/users/{user_id}/reset-password")
def reset_password(user_id: str, body: ResetPasswordRequest, admin=Depends(require_admin_user)):
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE id = ?", (user_id,))
        if not user:
            raise HTTPException(status_code=404, detail="账号不存在")
        validate_password(user["username"], body.new_password)
        conn.execute(
            """
            UPDATE users
            SET password_hash = ?, must_change_password = ?, password_changed_at = CURRENT_TIMESTAMP,
                failed_login_count = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (hash_password(body.new_password), int(body.must_change_password), user_id),
        )
        revoke_user_sessions(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_RESET_PASSWORD",
            "user",
            user_id,
            after_json=json.dumps({"username": user["username"], "must_change_password": body.must_change_password}, ensure_ascii=False),
        )
        conn.commit()
        return {"ok": True, "message": "密码已重置", "initial_password": body.new_password}


@router.post("/users/{user_id}/revoke-sessions")
def revoke_sessions(user_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE id = ?", (user_id,))
        if not user:
            raise HTTPException(status_code=404, detail="账号不存在")
        revoke_user_sessions(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_REVOKE_USER_SESSIONS",
            "user",
            user_id,
            after_json=json.dumps({"username": user["username"]}, ensure_ascii=False),
        )
        conn.commit()
    return {"message": "该账号已从所有设备退出"}


@router.patch("/users/{user_id}/status")
def update_user_status(user_id: str, body: StatusPatch, admin=Depends(require_admin_user)):
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE id = ?", (user_id,))
        if not user:
            raise HTTPException(status_code=404, detail="账号不存在")
        if not body.active and user["role"] == "admin":
            active_admins = one(conn, "SELECT COUNT(*) AS c FROM users WHERE role = 'admin' AND active = 1")["c"]
            if active_admins <= 1:
                raise HTTPException(status_code=409, detail="不能停用当前唯一的管理员账号")
        conn.execute("UPDATE users SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(body.active), user_id))
        if not body.active:
            revoke_user_sessions(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_ENABLE_USER" if body.active else "ADMIN_DISABLE_USER",
            "user",
            user_id,
            before_json=json.dumps({"active": bool(user["active"])}, ensure_ascii=False),
            after_json=json.dumps({"active": body.active}, ensure_ascii=False),
        )
        conn.commit()
        return public_admin_user(conn, user_id)
