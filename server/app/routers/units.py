import os
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException

from ..database import all_rows, connect, one, revoke_unit_sessions, revoke_user_sessions, transaction, write_audit
from ..dependencies import require_admin_user, require_manage_backups
from ..registration import generate_invite_token, invite_hash, masked_phone, phone_hash
from ..schemas import AdminInviteCreate, ManagerRegistrationReview, ResetPasswordRequest, StatusPatch, UnitCreate, UnitUpdate, UserCreate, UserUpdate
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
        raise HTTPException(status_code=400, detail="请填写需要保存的内容")
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
        raise HTTPException(status_code=400, detail="账号角色不正确")
    if body.role == "unit_user" and not body.unit_id:
        raise HTTPException(status_code=400, detail="请选择所属单位")
    user_id = str(uuid4())
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO users(
              id, username, password_hash, display_name, role, unit_id, must_change_password,
              can_manage_accounts, can_issue_manager_invites, can_view_system_status,
              can_view_detailed_metrics, can_manage_backups, can_restore_backups
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                user_id,
                body.username,
                hash_password(body.password),
                body.display_name,
                body.role,
                body.unit_id,
                int(body.must_change_password),
                1 if body.role == "admin" else 0,
                1 if body.role == "admin" else 0,
                1 if body.role == "admin" else 0,
                1 if body.role == "admin" else 0,
                1 if body.role == "admin" else 0,
                1 if body.role == "admin" else 0,
            ),
        )
        conn.commit()
        return one(conn, "SELECT id, username, display_name, role, unit_id, active, must_change_password FROM users WHERE id = ?", (user_id,))


@router.post("/invites")
def create_registration_invite(body: AdminInviteCreate, admin=Depends(require_admin_user)):
    if body.invite_type not in {"manager", "unit"}:
        raise HTTPException(status_code=400, detail="邀请类型不正确")
    if body.invite_type == "manager" and not bool(admin.get("can_issue_manager_invites", 0)):
        raise HTTPException(status_code=403, detail="当前账号无权签发管理者邀请码")
    if body.invite_type == "unit" and not body.unit_id:
        raise HTTPException(status_code=400, detail="请选择所属单位")
    if body.phone_required and os.getenv("SMS_PROVIDER", "disabled").lower() == "disabled":
        raise HTTPException(status_code=400, detail="短信服务未启用，不能创建需手机验证的邀请码")
    role = "admin" if body.invite_type == "manager" else "unit_user"
    max_uses = 1 if body.invite_type == "manager" else body.max_uses
    allowed_phone_hash = phone_hash(body.allowed_phone) if body.allowed_phone else ""
    allowed_phone_masked = masked_phone(body.allowed_phone) if body.allowed_phone else ""
    invite_id = str(uuid4())
    with transaction() as conn:
        unit = None
        if body.invite_type == "unit":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (body.unit_id,))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=400, detail="所属单位不可用")
        token = generate_invite_token()
        while one(conn, "SELECT id FROM registration_invites WHERE token_hash = ?", (invite_hash(token),)):
            token = generate_invite_token()
        expires_at = one(conn, "SELECT datetime('now', ?) AS expires_at", (f"+{body.expires_in_hours} hours",))["expires_at"]
        conn.execute(
            """
            INSERT INTO registration_invites(
              id, token_hash, display_code_suffix, invite_type, role, unit_id, created_by, expires_at,
              max_uses, phone_required, allowed_phone_hash, allowed_phone_masked
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                invite_id,
                invite_hash(token),
                token[-4:],
                body.invite_type,
                role,
                body.unit_id if body.invite_type == "unit" else None,
                admin["id"],
                expires_at,
                max_uses,
                int(body.phone_required),
                allowed_phone_hash,
                allowed_phone_masked,
            ),
        )
        write_audit(conn, admin["id"], admin["role"], "ADMIN_CREATE_REGISTRATION_INVITE", "registration_invite", invite_id)
        return {
            "id": invite_id,
            "invite_type": body.invite_type,
            "role_label": "管理者申请" if body.invite_type == "manager" else "子单位",
            "unit_name": unit["unit_name"] if unit else "",
            "unit_code": unit["unit_code"] if unit else "",
            "max_uses": max_uses,
            "phone_required": body.phone_required,
            "expires_at": expires_at,
            "invite_token": token,
            "display_code_suffix": token[-4:],
            "qr_payload": f"jingrongxianpei://invite?token={token}",
            "notice": "该邀请码关闭后不再完整显示，请立即复制。",
        }


def require_manage_accounts(admin=Depends(require_admin_user)):
    if not bool(admin.get("can_manage_accounts", 0)):
        raise HTTPException(status_code=403, detail="当前账号无权审批管理者申请")
    return admin


@router.get("/manager-registration-requests")
def list_manager_registration_requests(admin=Depends(require_manage_accounts)):
    with connect() as conn:
        return {
            "items": all_rows(
                conn,
                """
                SELECT r.id, r.invite_id, r.username, r.display_name, r.phone_masked, r.status,
                       r.requested_at, r.reviewed_at, r.review_note, r.activated_user_id,
                       inviter.display_code_suffix AS invite_code_suffix
                FROM manager_registration_requests r
                LEFT JOIN registration_invites inviter ON inviter.id = r.invite_id
                ORDER BY r.requested_at DESC
                LIMIT 100
                """,
            )
        }


@router.post("/manager-registration-requests/{request_id}/approve")
def approve_manager_registration_request(request_id: str, body: ManagerRegistrationReview | None = None, admin=Depends(require_manage_accounts)):
    note = (body.review_note if body else "").strip()
    with transaction() as conn:
        row = one(conn, "SELECT * FROM manager_registration_requests WHERE id = ?", (request_id,))
        if not row:
            raise HTTPException(status_code=404, detail="管理者申请不存在")
        if row["status"] != "pending":
            raise HTTPException(status_code=409, detail="管理者申请已处理")
        if one(conn, "SELECT id FROM users WHERE username = ?", (row["username"],)):
            raise HTTPException(status_code=409, detail="账号已存在")
        user_id = str(uuid4())
        conn.execute(
            """
            INSERT INTO users(
              id, username, password_hash, display_name, role, unit_id, active, must_change_password,
              can_manage_accounts, can_issue_manager_invites, can_view_system_status,
              can_view_detailed_metrics, can_manage_backups, can_restore_backups
            )
            VALUES (?, ?, ?, ?, 'admin', NULL, 1, 0, 0, 0, 1, 0, 0, 0)
            """,
            (user_id, row["username"], row["password_hash"], row["display_name"]),
        )
        conn.execute(
            """
            UPDATE manager_registration_requests
            SET status = 'approved', reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP,
                review_note = ?, activated_user_id = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (admin["id"], note, user_id, request_id),
        )
        write_audit(conn, admin["id"], admin["role"], "ADMIN_APPROVE_MANAGER_REGISTRATION", "manager_registration_request", request_id)
        return {
            "id": request_id,
            "status": "approved",
            "activated_user_id": user_id,
            "username": row["username"],
            "display_name": row["display_name"],
        }


@router.post("/manager-registration-requests/{request_id}/reject")
def reject_manager_registration_request(request_id: str, body: ManagerRegistrationReview | None = None, admin=Depends(require_manage_accounts)):
    note = (body.review_note if body else "").strip()
    with transaction() as conn:
        row = one(conn, "SELECT * FROM manager_registration_requests WHERE id = ?", (request_id,))
        if not row:
            raise HTTPException(status_code=404, detail="管理者申请不存在")
        if row["status"] != "pending":
            raise HTTPException(status_code=409, detail="管理者申请已处理")
        conn.execute(
            """
            UPDATE manager_registration_requests
            SET status = 'rejected', reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP,
                review_note = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (admin["id"], note, request_id),
        )
        write_audit(conn, admin["id"], admin["role"], "ADMIN_REJECT_MANAGER_REGISTRATION", "manager_registration_request", request_id)
        return {"id": request_id, "status": "rejected"}


@router.post("/invites/{invite_id}/revoke")
def revoke_registration_invite(invite_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        row = one(conn, "SELECT id, status FROM registration_invites WHERE id = ?", (invite_id,))
        if not row:
            raise HTTPException(status_code=404, detail="邀请码不存在")
        if row["status"] not in {"active", "expired"}:
            return {"ok": True, "id": invite_id, "status": row["status"]}
        conn.execute(
            """
            UPDATE registration_invites
            SET status = 'revoked', revoked_at = CURRENT_TIMESTAMP, revoked_by = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (admin["id"], invite_id),
        )
        write_audit(conn, admin["id"], admin["role"], "ADMIN_REVOKE_REGISTRATION_INVITE", "registration_invite", invite_id)
    return {"ok": True, "id": invite_id, "status": "revoked"}


@router.put("/users/{user_id}")
def update_user(user_id: str, body: UserUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    if not fields:
        raise HTTPException(status_code=400, detail="请填写需要保存的内容")
    assignments = ", ".join(f"{key} = ?" for key in fields)
    values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
    with connect() as conn:
        conn.execute(f"UPDATE users SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, user_id))
        if {"unit_id", "must_change_password"} & set(fields):
            revoke_user_sessions(conn, user_id)
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
