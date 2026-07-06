import re
import secrets
from urllib.parse import parse_qs, unquote, urlparse

from fastapi import HTTPException

from .database import one
from .security import hash_token

INVITE_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ"
LEGACY_INVITE_URI_PREFIX = "jingrongxianpei://register"
INVITE_URI_PREFIX = "jingrongxianpei://invite"
SENSITIVE_CLIENT_FIELDS = {"role", "unit_id", "is_admin", "permissions", "organization_id"}


def generate_invite_token(length: int = 14) -> str:
    return "".join(secrets.choice(INVITE_ALPHABET) for _ in range(length))


def normalize_invite_token(raw: str) -> str:
    value = (raw or "").strip()
    if value.startswith(INVITE_URI_PREFIX):
        parsed = urlparse(value)
        query = parse_qs(parsed.query)
        value = query.get("token", [""])[0]
    elif value.startswith(LEGACY_INVITE_URI_PREFIX):
        parsed = urlparse(value)
        query = parse_qs(parsed.query)
        value = query.get("invite", [""])[0]
    value = unquote(value).strip().replace(" ", "").replace("-", "").upper()
    return value


def invite_hash(raw: str) -> str:
    token = normalize_invite_token(raw)
    if not token:
        return ""
    return hash_token(token)


def phone_hash(phone: str) -> str:
    digits = normalized_phone(phone)
    if not digits:
        return ""
    return hash_token(digits)


def normalized_phone(phone: str) -> str:
    return re.sub(r"\D+", "", phone or "")


def masked_phone(phone: str) -> str:
    digits = normalized_phone(phone)
    if len(digits) < 7:
        return digits[:2] + "****" if digits else ""
    return f"{digits[:3]}****{digits[-4:]}"


def ensure_invite_usable(conn, token: str) -> dict:
    row = one(
        conn,
        """
        SELECT *
        FROM registration_invites
        WHERE token_hash = ?
        """,
        (invite_hash(token),),
    )
    if not row:
        raise HTTPException(status_code=404, detail="邀请码无效或已过期")
    expired = one(conn, "SELECT CURRENT_TIMESTAMP > ? AS expired", (row["expires_at"],))["expired"]
    if expired and row["status"] == "active":
        conn.execute("UPDATE registration_invites SET status = 'expired', updated_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))
        row = {**row, "status": "expired"}
    if row["status"] != "active" or expired:
        raise HTTPException(status_code=409, detail="邀请码无效或已过期")
    if int(row["used_count"]) >= int(row["max_uses"]):
        conn.execute("UPDATE registration_invites SET status = 'used', updated_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))
        raise HTTPException(status_code=409, detail="邀请码已使用")
    if row["invite_type"] == "unit":
        unit = one(conn, "SELECT * FROM units WHERE id = ?", (row["unit_id"],))
        if not unit or not unit["active"]:
            raise HTTPException(status_code=409, detail="所属单位不可用")
    return row


def public_invite_payload(conn, row: dict, valid: bool = True) -> dict:
    unit = one(conn, "SELECT * FROM units WHERE id = ?", (row["unit_id"],)) if row.get("unit_id") else None
    creator = one(conn, "SELECT display_name FROM users WHERE id = ?", (row["created_by"],)) if row.get("created_by") else None
    remaining = max(0, int(row["max_uses"]) - int(row["used_count"]))
    is_manager = row["invite_type"] == "manager"
    issuer_name = mask_display_name(creator["display_name"]) if creator else "系统管理员"
    return {
        "valid": valid,
        "invite_type": row["invite_type"],
        "display_role": "管理者申请" if is_manager else "子单位",
        "role_label": "管理者申请" if is_manager else "子单位",
        "issuer_name_masked": issuer_name,
        "issuer_name": issuer_name,
        "issuer_org": "XX公安局",
        "unit_name": unit["unit_name"] if unit else "",
        "unit_code": unit["unit_code"] if unit else "",
        "delivery_point": unit["default_delivery_point"] if unit else "",
        "phone_required": bool(row["phone_required"]),
        "approval_required": is_manager,
        "expires_at": row["expires_at"],
        "remaining_uses": remaining,
    }


def mask_display_name(name: str) -> str:
    value = (name or "").strip()
    if len(value) <= 1:
        return value
    if len(value) == 2:
        return value[0] + "*"
    return value[0] + "*" * (len(value) - 2) + value[-1]
