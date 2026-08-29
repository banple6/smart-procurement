import json
import os
import re
import sqlite3
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException

from ..database import all_rows, connect, one, revoke_unit_sessions, revoke_user_sessions, transaction, write_audit
from ..dependencies import require_admin_user
from ..registration import generate_invite_token, invite_hash, masked_phone, phone_hash
from .auth import validate_new_password, validate_username
from ..schemas import (
    AdminInviteCreate,
    AdminUserCreate,
    ManagerRegistrationReview,
    ResetPasswordRequest,
    StatusPatch,
    UnitCreate,
    UnitUpdate,
    UnitQuotaAdjustment,
    UnitQuotaSettings,
    UnitUserCreate,
    UserCreate,
    UserPermissionsUpdate,
    UserUpdate,
)
from ..security import hash_password
from ..services.unit_quota import adjust_quota, quota_ledger, quota_payload, update_quota_settings

router = APIRouter(prefix="/admin", tags=["admin"])

PERMISSION_FIELDS = (
    "can_manage_accounts",
    "can_issue_manager_invites",
    "can_view_system_status",
    "can_view_detailed_metrics",
    "can_manage_backups",
    "can_restore_backups",
)
UNIT_CODE_RE = re.compile(r"^[A-Za-z0-9_-]+$")


def require_manage_accounts(admin=Depends(require_admin_user)):
    if not bool(admin.get("can_manage_accounts", 0)):
        raise HTTPException(status_code=403, detail="当前账号无权管理组织和账号")
    return admin


def _required_text(value: str, label: str) -> str:
    normalized = (value or "").strip()
    if not normalized:
        raise HTTPException(status_code=400, detail=f"{label}不能为空")
    return normalized


def _unit_payload(body: UnitCreate | UnitUpdate, partial: bool = False) -> dict:
    raw = body.model_dump(exclude_unset=partial)
    fields = {}
    if "unit_code" in raw:
        unit_code = _required_text(raw["unit_code"], "单位编码")
        if not UNIT_CODE_RE.fullmatch(unit_code):
            raise HTTPException(status_code=400, detail="单位编码只能包含字母、数字、横线或下划线")
        fields["unit_code"] = unit_code
    if "unit_name" in raw:
        fields["unit_name"] = _required_text(raw["unit_name"], "单位名称")
    for field in ("default_delivery_point", "address_note"):
        if field in raw:
            fields[field] = (raw[field] or "").strip()
    if "active" in raw:
        fields["active"] = int(bool(raw["active"]))
    return fields


def _safe_unit_payload(row: dict) -> dict:
    return {
        "unit_code": row.get("unit_code", ""),
        "unit_name": row.get("unit_name", ""),
        "default_delivery_point": row.get("default_delivery_point", ""),
        "address_note": row.get("address_note", ""),
        "active": bool(row.get("active", 0)),
    }


def _safe_user_payload(row: dict) -> dict:
    return {
        "username": row.get("username", ""),
        "display_name": row.get("display_name", ""),
        "role": row.get("role", ""),
        "unit_id": row.get("unit_id") or "",
        "active": bool(row.get("active", 0)),
        "must_change_password": bool(row.get("must_change_password", 0)),
        **{field: bool(row.get(field, 0)) for field in PERMISSION_FIELDS},
    }


def _public_user(row: dict) -> dict:
    return {
        "id": row["id"],
        "username": row["username"],
        "display_name": row["display_name"],
        "role": row["role"],
        "unit_id": row.get("unit_id") or "",
        "unit_name": row.get("unit_name") or "",
        "unit_code": row.get("unit_code") or "",
        "active": bool(row["active"]),
        "must_change_password": bool(row["must_change_password"]),
        "last_login_at": row.get("last_login_at") or "",
        "created_at": row.get("created_at") or "",
        "updated_at": row.get("updated_at") or "",
        **{field: bool(row.get(field, 0)) for field in PERMISSION_FIELDS},
    }


def _user_row(conn, user_id: str) -> dict | None:
    return one(
        conn,
        """
        SELECT u.*, units.unit_name, units.unit_code
        FROM users u
        LEFT JOIN units ON units.id = u.unit_id
        WHERE u.id = ?
        """,
        (user_id,),
    )


def _active_account_manager_count(conn) -> int:
    return one(
        conn,
        "SELECT COUNT(*) AS count FROM users WHERE role = 'admin' AND active = 1 AND can_manage_accounts = 1",
    )["count"]


def _raise_unique_error(error: sqlite3.IntegrityError, kind: str):
    message = str(error).lower()
    if "units.unit_code" in message:
        raise HTTPException(status_code=409, detail="单位编码已存在，请更换后重试") from None
    if "users.username" in message:
        raise HTTPException(status_code=409, detail="账号已存在") from None
    raise error


@router.get("/units")
def list_units(admin=Depends(require_admin_user)):
    with transaction() as conn:
        rows = all_rows(
            conn,
            """
            SELECT u.*,
              (SELECT COUNT(*) FROM users WHERE unit_id = u.id) AS account_count,
              (SELECT COUNT(*) FROM orders WHERE unit_id = u.id AND is_deleted = 0) AS order_count,
              (SELECT MAX(created_at) FROM orders WHERE unit_id = u.id AND is_deleted = 0) AS last_order_at
            FROM units u
            ORDER BY u.created_at DESC
            """,
        )
        for row in rows:
            row["quota"] = quota_payload(conn, row["id"])
        return rows


@router.get("/units/{unit_id}/quota")
def get_unit_quota(unit_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        if not one(conn, "SELECT id FROM units WHERE id = ?", (unit_id,)):
            raise HTTPException(status_code=404, detail="子单位不存在")
        return quota_payload(conn, unit_id)


@router.put("/units/{unit_id}/quota")
def set_unit_quota(unit_id: str, body: UnitQuotaSettings, admin=Depends(require_manage_accounts)):
    with transaction() as conn:
        if not one(conn, "SELECT id FROM units WHERE id = ?", (unit_id,)):
            raise HTTPException(status_code=404, detail="子单位不存在")
        return update_quota_settings(
            conn,
            unit_id=unit_id,
            enabled=body.enabled,
            default_monthly_quota_cents=body.default_monthly_quota_cents,
            actor=admin,
        )


@router.post("/units/{unit_id}/quota/adjustments")
def create_unit_quota_adjustment(unit_id: str, body: UnitQuotaAdjustment, admin=Depends(require_manage_accounts)):
    with transaction() as conn:
        if not one(conn, "SELECT id FROM units WHERE id = ?", (unit_id,)):
            raise HTTPException(status_code=404, detail="子单位不存在")
        return adjust_quota(conn, unit_id=unit_id, delta_cents=body.delta_cents, reason=body.reason, actor=admin)


@router.get("/units/{unit_id}/quota/ledger")
def list_unit_quota_ledger(unit_id: str, month: str | None = None, admin=Depends(require_admin_user)):
    with transaction() as conn:
        if not one(conn, "SELECT id FROM units WHERE id = ?", (unit_id,)):
            raise HTTPException(status_code=404, detail="子单位不存在")
        quota = quota_payload(conn, unit_id, month)
        return {"quota": quota, "items": quota_ledger(conn, unit_id, month)}


@router.post("/units")
def create_unit(body: UnitCreate, admin=Depends(require_manage_accounts)):
    fields = _unit_payload(body)
    unit_id = str(uuid4())
    try:
        with transaction() as conn:
            conn.execute(
                "INSERT INTO units(id, unit_code, unit_name, default_delivery_point, address_note, active) VALUES (?, ?, ?, ?, ?, 1)",
                (unit_id, fields["unit_code"], fields["unit_name"], fields.get("default_delivery_point", ""), fields.get("address_note", "")),
            )
            row = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
            write_audit(conn, admin["id"], admin["role"], "UNIT_CREATED", "unit", unit_id, after_json=json.dumps(_safe_unit_payload(row), ensure_ascii=False))
            return row
    except sqlite3.IntegrityError as error:
        _raise_unique_error(error, "unit")


@router.put("/units/{unit_id}")
def update_unit(unit_id: str, body: UnitUpdate, admin=Depends(require_manage_accounts)):
    fields = _unit_payload(body, partial=True)
    if not fields:
        raise HTTPException(status_code=400, detail="请填写需要保存的内容")
    assignments = ", ".join(f"{key} = ?" for key in fields)
    try:
        with transaction() as conn:
            before = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
            if not before:
                raise HTTPException(status_code=404, detail="子单位不存在")
            conn.execute(f"UPDATE units SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*fields.values(), unit_id))
            if not bool(fields.get("active", before["active"])):
                revoke_unit_sessions(conn, unit_id)
            row = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
            status_changed = "active" in fields and bool(before["active"]) != bool(row["active"])
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                ("UNIT_ENABLED" if row["active"] else "UNIT_DISABLED") if status_changed else "UNIT_UPDATED",
                "unit",
                unit_id,
                before_json=json.dumps(_safe_unit_payload(before), ensure_ascii=False),
                after_json=json.dumps(_safe_unit_payload(row), ensure_ascii=False),
            )
            return row
    except sqlite3.IntegrityError as error:
        _raise_unique_error(error, "unit")


@router.patch("/units/{unit_id}/status")
def update_unit_status(unit_id: str, body: StatusPatch, admin=Depends(require_manage_accounts)):
    with transaction() as conn:
        before = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
        if not before:
            raise HTTPException(status_code=404, detail="子单位不存在")
        conn.execute("UPDATE units SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(body.active), unit_id))
        if not body.active:
            revoke_unit_sessions(conn, unit_id)
        row = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "UNIT_ENABLED" if body.active else "UNIT_DISABLED",
            "unit",
            unit_id,
            before_json=json.dumps(_safe_unit_payload(before), ensure_ascii=False),
            after_json=json.dumps(_safe_unit_payload(row), ensure_ascii=False),
        )
        return row


@router.get("/users")
def list_users(unit_id: str | None = None, admin=Depends(require_admin_user)):
    with connect() as conn:
        where = "WHERE u.unit_id = ?" if unit_id else ""
        params = (unit_id,) if unit_id else ()
        return all_rows(
            conn,
            f"""
            SELECT u.id, u.username, u.display_name, u.role, u.unit_id, units.unit_name, units.unit_code,
              u.active, u.must_change_password, u.last_login_at, u.created_at, u.updated_at,
              u.can_manage_accounts, u.can_issue_manager_invites, u.can_view_system_status,
              u.can_view_detailed_metrics, u.can_manage_backups, u.can_restore_backups
            FROM users u
            LEFT JOIN units ON units.id = u.unit_id
            {where}
            ORDER BY u.created_at DESC
            """,
            params,
        )


@router.post("/users")
def create_user(body: UserCreate, admin=Depends(require_manage_accounts)):
    """Compatibility endpoint for existing clients; new Web flows use fixed-role endpoints below."""
    if body.role not in ("admin", "unit_user"):
        raise HTTPException(status_code=400, detail="账号角色不正确")
    return _create_account(
        username=body.username,
        password=body.password,
        display_name=body.display_name,
        role=body.role,
        unit_id=body.unit_id,
        must_change_password=body.must_change_password,
        permissions={},
        admin=admin,
    )


def _create_account(*, username: str, password: str, display_name: str, role: str, unit_id: str | None, must_change_password: bool, permissions: dict, admin: dict):
    normalized_username = (username or "").strip()
    validate_username(normalized_username)
    validate_new_password(normalized_username, password)
    normalized_display_name = _required_text(display_name, "显示名称")
    if role == "unit_user" and not unit_id:
        raise HTTPException(status_code=400, detail="请选择所属单位")
    user_id = str(uuid4())
    try:
        with transaction() as conn:
            if role == "unit_user":
                unit = one(conn, "SELECT * FROM units WHERE id = ?", (unit_id,))
                if not unit or not unit["active"]:
                    raise HTTPException(status_code=400, detail="所属单位不可用")
            values = {field: int(bool(permissions.get(field, False))) if role == "admin" else 0 for field in PERMISSION_FIELDS}
            conn.execute(
                """
                INSERT INTO users(
                  id, username, password_hash, display_name, role, unit_id, active, must_change_password,
                  can_manage_accounts, can_issue_manager_invites, can_view_system_status,
                  can_view_detailed_metrics, can_manage_backups, can_restore_backups
                )
                VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    user_id, normalized_username, hash_password(password), normalized_display_name, role,
                    unit_id if role == "unit_user" else None, int(bool(must_change_password)),
                    *(values[field] for field in PERMISSION_FIELDS),
                ),
            )
            row = _user_row(conn, user_id)
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                "ADMIN_CREATED" if role == "admin" else "USER_CREATED",
                "user",
                user_id,
                after_json=json.dumps(_safe_user_payload(row), ensure_ascii=False),
            )
            return _public_user(row)
    except sqlite3.IntegrityError as error:
        _raise_unique_error(error, "user")


@router.post("/accounts/unit-user")
def create_unit_user_account(body: UnitUserCreate, admin=Depends(require_manage_accounts)):
    return _create_account(
        username=body.username,
        password=body.password,
        display_name=body.display_name,
        role="unit_user",
        unit_id=body.unit_id,
        must_change_password=True,
        permissions={},
        admin=admin,
    )


@router.post("/accounts/admin")
def create_admin_account(body: AdminUserCreate, admin=Depends(require_manage_accounts)):
    permissions = {field: bool(getattr(body, field)) for field in PERMISSION_FIELDS}
    return _create_account(
        username=body.username,
        password=body.password,
        display_name=body.display_name,
        role="admin",
        unit_id=None,
        must_change_password=True,
        permissions=permissions,
        admin=admin,
    )


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


def _update_user_profile(user_id: str, body: UserUpdate, admin: dict) -> dict:
    fields = body.model_dump(exclude_unset=True)
    if not fields:
        raise HTTPException(status_code=400, detail="请填写需要保存的内容")
    if "display_name" in fields:
        fields["display_name"] = _required_text(fields["display_name"], "显示名称")
    with transaction() as conn:
        before = _user_row(conn, user_id)
        if not before:
            raise HTTPException(status_code=404, detail="账号不存在")
        if "unit_id" in fields:
            if before["role"] != "unit_user":
                raise HTTPException(status_code=400, detail="管理员账号不能绑定子单位")
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (fields["unit_id"],)) if fields["unit_id"] else None
            if not unit or not unit["active"]:
                raise HTTPException(status_code=400, detail="所属单位不可用")
        assignments = ", ".join(f"{key} = ?" for key in fields)
        values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
        conn.execute(f"UPDATE users SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, user_id))
        if {"unit_id", "must_change_password"} & set(fields):
            revoke_user_sessions(conn, user_id)
        row = _user_row(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "USER_UPDATED",
            "user",
            user_id,
            before_json=json.dumps(_safe_user_payload(before), ensure_ascii=False),
            after_json=json.dumps(_safe_user_payload(row), ensure_ascii=False),
        )
        return _public_user(row)


@router.put("/users/{user_id}")
def update_user(user_id: str, body: UserUpdate, admin=Depends(require_manage_accounts)):
    return _update_user_profile(user_id, body, admin)


@router.put("/accounts/{user_id}")
def update_account(user_id: str, body: UserUpdate, admin=Depends(require_manage_accounts)):
    return _update_user_profile(user_id, body, admin)


def _reset_password(user_id: str, body: ResetPasswordRequest, admin: dict) -> dict:
    with transaction() as conn:
        target = _user_row(conn, user_id)
        if not target:
            raise HTTPException(status_code=404, detail="账号不存在")
        validate_new_password(target["username"], body.new_password)
        conn.execute(
            "UPDATE users SET password_hash = ?, must_change_password = 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (hash_password(body.new_password), user_id),
        )
        revoke_user_sessions(conn, user_id)
        row = _user_row(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "USER_PASSWORD_RESET",
            "user",
            user_id,
            before_json=json.dumps(_safe_user_payload(target), ensure_ascii=False),
            after_json=json.dumps(_safe_user_payload(row), ensure_ascii=False),
        )
        return {"ok": True, "user": _public_user(row)}


@router.post("/users/{user_id}/reset-password")
def reset_password(user_id: str, body: ResetPasswordRequest, admin=Depends(require_manage_accounts)):
    return _reset_password(user_id, body, admin)


@router.post("/accounts/{user_id}/reset-password")
def reset_account_password(user_id: str, body: ResetPasswordRequest, admin=Depends(require_manage_accounts)):
    return _reset_password(user_id, body, admin)


def _set_user_status(user_id: str, body: StatusPatch, admin: dict) -> dict:
    with transaction() as conn:
        target = _user_row(conn, user_id)
        if not target:
            raise HTTPException(status_code=404, detail="账号不存在")
        if not body.active and user_id == admin["id"]:
            raise HTTPException(status_code=400, detail="不能停用当前登录账号")
        if not body.active and target["role"] == "admin" and bool(target.get("can_manage_accounts", 0)) and _active_account_manager_count(conn) <= 1:
            raise HTTPException(status_code=409, detail="不能停用最后一个账号管理员")
        conn.execute("UPDATE users SET active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(body.active), user_id))
        if not body.active:
            revoke_user_sessions(conn, user_id)
        row = _user_row(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "USER_ENABLED" if body.active else "USER_DISABLED",
            "user",
            user_id,
            before_json=json.dumps(_safe_user_payload(target), ensure_ascii=False),
            after_json=json.dumps(_safe_user_payload(row), ensure_ascii=False),
        )
        return _public_user(row)


@router.patch("/users/{user_id}/status")
def update_user_status(user_id: str, body: StatusPatch, admin=Depends(require_manage_accounts)):
    return _set_user_status(user_id, body, admin)


@router.patch("/accounts/{user_id}/status")
def update_account_status(user_id: str, body: StatusPatch, admin=Depends(require_manage_accounts)):
    return _set_user_status(user_id, body, admin)


@router.patch("/accounts/{user_id}/permissions")
def update_account_permissions(user_id: str, body: UserPermissionsUpdate, admin=Depends(require_manage_accounts)):
    fields = {field: int(bool(getattr(body, field))) for field in PERMISSION_FIELDS}
    with transaction() as conn:
        target = _user_row(conn, user_id)
        if not target:
            raise HTTPException(status_code=404, detail="账号不存在")
        if target["role"] != "admin":
            raise HTTPException(status_code=400, detail="子单位账号不支持管理员权限设置")
        removing_last_manager = bool(target.get("active", 0)) and bool(target.get("can_manage_accounts", 0)) and not bool(fields["can_manage_accounts"])
        if removing_last_manager and _active_account_manager_count(conn) <= 1:
            raise HTTPException(status_code=409, detail="不能移除最后一个账号管理员权限")
        assignments = ", ".join(f"{field} = ?" for field in PERMISSION_FIELDS)
        conn.execute(f"UPDATE users SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*(fields[field] for field in PERMISSION_FIELDS), user_id))
        row = _user_row(conn, user_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "USER_PERMISSIONS_UPDATED",
            "user",
            user_id,
            before_json=json.dumps(_safe_user_payload(target), ensure_ascii=False),
            after_json=json.dumps(_safe_user_payload(row), ensure_ascii=False),
        )
        return _public_user(row)
