from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException

from ..database import all_rows, connect, one, revoke_unit_sessions, revoke_user_sessions
from ..dependencies import require_admin_user
from ..schemas import ResetPasswordRequest, StatusPatch, UnitCreate, UnitUpdate, UserCreate, UserUpdate
from ..security import hash_password

router = APIRouter(prefix="/admin", tags=["admin"])


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
        conn.commit()
        return one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))


@router.put("/units/{unit_id}")
def update_unit(unit_id: str, body: UnitUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    if not fields:
        raise HTTPException(status_code=400, detail="No fields")
    assignments = ", ".join(f"{key} = ?" for key in fields)
    values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
    with connect() as conn:
        conn.execute(f"UPDATE units SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, unit_id))
        conn.commit()
        return one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))


@router.patch("/units/{unit_id}/status")
def update_unit_status(unit_id: str, body: StatusPatch, admin=Depends(require_admin_user)):
    with connect() as conn:
        conn.execute("UPDATE units SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(body.active), unit_id))
        if not body.active:
            revoke_unit_sessions(conn, unit_id)
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
    if body.role not in ("admin", "unit_user"):
        raise HTTPException(status_code=400, detail="Invalid role")
    if body.role == "unit_user" and not body.unit_id:
        raise HTTPException(status_code=400, detail="unit_id required")
    user_id = str(uuid4())
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO users(id, username, password_hash, display_name, role, unit_id, must_change_password)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (user_id, body.username, hash_password(body.password), body.display_name, body.role, body.unit_id, int(body.must_change_password)),
        )
        conn.commit()
        return one(conn, "SELECT id, username, display_name, role, unit_id, active, must_change_password FROM users WHERE id = ?", (user_id,))


@router.put("/users/{user_id}")
def update_user(user_id: str, body: UserUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    if not fields:
        raise HTTPException(status_code=400, detail="No fields")
    assignments = ", ".join(f"{key} = ?" for key in fields)
    values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
    with connect() as conn:
        conn.execute(f"UPDATE users SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, user_id))
        conn.commit()
        return one(conn, "SELECT id, username, display_name, role, unit_id, active, must_change_password FROM users WHERE id = ?", (user_id,))


@router.post("/users/{user_id}/reset-password")
def reset_password(user_id: str, body: ResetPasswordRequest, admin=Depends(require_admin_user)):
    with connect() as conn:
        conn.execute(
            "UPDATE users SET password_hash = ?, must_change_password = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (hash_password(body.new_password), int(body.must_change_password), user_id),
        )
        revoke_user_sessions(conn, user_id)
        conn.commit()
        return {"ok": True}


@router.patch("/users/{user_id}/status")
def update_user_status(user_id: str, body: StatusPatch, admin=Depends(require_admin_user)):
    with connect() as conn:
        conn.execute("UPDATE users SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(body.active), user_id))
        if not body.active:
            revoke_user_sessions(conn, user_id)
        conn.commit()
        return one(conn, "SELECT id, username, display_name, role, unit_id, active, must_change_password FROM users WHERE id = ?", (user_id,))
