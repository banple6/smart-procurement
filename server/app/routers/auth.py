import os
import secrets
from uuid import uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Request

from ..database import connect, one, revoke_user_sessions, transaction, write_audit
from ..dependencies import current_user
from ..registration import (
    SENSITIVE_CLIENT_FIELDS,
    ensure_invite_usable,
    invite_hash,
    masked_phone,
    normalized_phone,
    phone_hash,
    public_invite_payload,
)
from ..schemas import (
    ChangePasswordRequest,
    InviteInspectRequest,
    LoginRequest,
    PhoneSendCodeRequest,
    PhoneVerifyCodeRequest,
    RegisterWithInviteRequest,
    StepUpRequest,
)
from ..security import create_session_token, hash_password, hash_token, verify_password

router = APIRouter(prefix="/auth", tags=["auth"])


def validate_new_password(username: str, password: str):
    if password.lower() == username.lower():
        raise HTTPException(status_code=400, detail="新密码不能与账号相同")
    has_letter = any(ch.isalpha() for ch in password)
    has_digit = any(ch.isdigit() for ch in password)
    if len(password) < 8 or not has_letter or not has_digit:
        raise HTTPException(status_code=400, detail="密码至少 8 位，且包含字母和数字")


def validate_username(username: str):
    value = (username or "").strip()
    if len(value) < 3 or len(value) > 32:
        raise HTTPException(status_code=400, detail="账号长度需为 3-32 位")
    allowed = all(ch.isalnum() or ch in {"_", "-", "."} for ch in value)
    if not allowed:
        raise HTTPException(status_code=400, detail="账号只能包含字母、数字、点、横线或下划线")


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


@router.post("/invites/inspect")
def inspect_invite(body: InviteInspectRequest):
    with connect() as conn:
        try:
            row = ensure_invite_usable(conn, body.invite_token)
            payload = public_invite_payload(conn, row)
            conn.commit()
            return payload
        except HTTPException:
            conn.commit()
            return {
                "valid": False,
                "invite_type": "",
                "role_label": "",
                "unit_name": "",
                "unit_code": "",
                "delivery_point": "",
                "phone_required": False,
                "expires_at": "",
                "remaining_uses": 0,
            }


@router.post("/phone/send-code")
def send_phone_code(body: PhoneSendCodeRequest, request: Request):
    phone = normalized_phone(body.phone)
    if len(phone) < 7:
        raise HTTPException(status_code=400, detail="手机号格式不正确")
    purpose = body.purpose or "register"
    if purpose not in {"register", "bind_phone", "change_phone", "password_reset"}:
        raise HTTPException(status_code=400, detail="验证码用途不正确")
    sms_provider = os.getenv("SMS_PROVIDER", "disabled").lower()
    if sms_provider == "disabled":
        return {
            "ok": True,
            "sms_status": "disabled",
            "code_sent": False,
            "phone_masked": masked_phone(phone),
            "message": "短信服务未启用，当前环境不会发送验证码",
        }
    code = f"{secrets.randbelow(1_000_000):06d}"
    phone_digest = phone_hash(phone)
    invite_id = ""
    with transaction() as conn:
        if body.invite_token:
            try:
                invite = ensure_invite_usable(conn, body.invite_token)
                invite_id = invite["id"]
            except HTTPException:
                raise HTTPException(status_code=404, detail="邀请码无效或已过期") from None
        recent = one(
            conn,
            """
            SELECT COUNT(*) AS c
            FROM phone_verification_codes
            WHERE phone_hash = ? AND created_at > datetime('now', '-60 seconds')
            """,
            (phone_digest,),
        )["c"]
        if recent:
            raise HTTPException(status_code=429, detail="验证码发送过于频繁")
        daily = one(
            conn,
            """
            SELECT COUNT(*) AS c
            FROM phone_verification_codes
            WHERE phone_hash = ? AND created_at > datetime('now', '-1 day')
            """,
            (phone_digest,),
        )["c"]
        if daily >= int(os.getenv("SMS_DAILY_LIMIT_PER_PHONE", "10")):
            raise HTTPException(status_code=429, detail="今日验证码次数已达上限")
        conn.execute(
            """
            INSERT INTO phone_verification_codes(
              id, phone_hash, phone_masked, code_hash, purpose, invite_id, expires_at, max_attempts, request_ip
            )
            VALUES (?, ?, ?, ?, ?, ?, datetime('now', ?), ?, ?)
            """,
            (
                str(uuid4()),
                phone_digest,
                masked_phone(phone),
                hash_token(f"{phone_digest}:{code}"),
                purpose,
                invite_id,
                f"+{int(os.getenv('SMS_CODE_TTL_SECONDS', '300'))} seconds",
                int(os.getenv("SMS_MAX_ATTEMPTS", "5")),
                request.client.host if request.client else "",
            ),
        )
    return {"ok": True, "sms_status": "queued", "code_sent": True, "phone_masked": masked_phone(phone)}


@router.post("/phone/verify-code")
def verify_phone_code(body: PhoneVerifyCodeRequest):
    phone = normalized_phone(body.phone)
    phone_digest = phone_hash(phone)
    if not phone_digest or not body.code.isdigit() or len(body.code) != 6:
        raise HTTPException(status_code=400, detail="验证码不正确")
    with transaction() as conn:
        row = one(
            conn,
            """
            SELECT *
            FROM phone_verification_codes
            WHERE phone_hash = ?
              AND purpose = ?
              AND consumed_at IS NULL
              AND verified_at IS NULL
            ORDER BY created_at DESC
            LIMIT 1
            """,
            (phone_digest, body.purpose or "register"),
        )
        if not row:
            raise HTTPException(status_code=400, detail="验证码不正确或已过期")
        expired = one(conn, "SELECT CURRENT_TIMESTAMP > ? AS expired", (row["expires_at"],))["expired"]
        if expired:
            conn.execute("UPDATE phone_verification_codes SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))
            raise HTTPException(status_code=400, detail="验证码不正确或已过期")
        if row["attempts"] >= row["max_attempts"]:
            conn.execute("UPDATE phone_verification_codes SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))
            raise HTTPException(status_code=400, detail="验证码不正确或已过期")
        expected = hash_token(f"{phone_digest}:{body.code}")
        if expected != row["code_hash"]:
            attempts = int(row["attempts"]) + 1
            conn.execute(
                """
                UPDATE phone_verification_codes
                SET attempts = ?, consumed_at = CASE WHEN ? >= max_attempts THEN CURRENT_TIMESTAMP ELSE consumed_at END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                (attempts, attempts, row["id"]),
            )
            raise HTTPException(status_code=400, detail="验证码不正确")
        ticket = secrets.token_urlsafe(32)
        conn.execute(
            """
            UPDATE phone_verification_codes
            SET verified_at = CURRENT_TIMESTAMP, ticket_hash = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (hash_token(ticket), row["id"]),
        )
    return {"ok": True, "phone_masked": masked_phone(phone), "phone_verification_ticket": ticket}


def consume_phone_ticket(conn, phone: str, purpose: str, ticket: str):
    phone_digest = phone_hash(phone)
    row = one(
        conn,
        """
        SELECT *
        FROM phone_verification_codes
        WHERE phone_hash = ?
          AND purpose = ?
          AND ticket_hash = ?
          AND verified_at IS NOT NULL
          AND consumed_at IS NULL
        ORDER BY verified_at DESC
        LIMIT 1
        """,
        (phone_digest, purpose, hash_token(ticket)),
    )
    if not row:
        raise HTTPException(status_code=400, detail="手机号验证已失效")
    expired = one(conn, "SELECT CURRENT_TIMESTAMP > ? AS expired", (row["expires_at"],))["expired"]
    if expired:
        raise HTTPException(status_code=400, detail="手机号验证已失效")
    conn.execute("UPDATE phone_verification_codes SET consumed_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))


def mark_invite_used(conn, invite: dict):
    next_used = int(invite["used_count"]) + 1
    next_status = "used" if next_used >= int(invite["max_uses"]) else "active"
    updated = conn.execute(
        """
        UPDATE registration_invites
        SET used_count = ?, status = ?, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND used_count < max_uses AND status = 'active'
        """,
        (next_used, next_status, invite["id"]),
    ).rowcount
    if updated != 1:
        raise HTTPException(status_code=409, detail="邀请码已使用")


@router.post("/register-with-invite")
def register_with_invite(body: RegisterWithInviteRequest, request: Request):
    username = body.username.strip()
    display_name = body.display_name.strip()
    if not display_name:
        raise HTTPException(status_code=400, detail="请填写显示名称")
    validate_username(username)
    validate_new_password(username, body.password)
    extra = set((body.model_extra or {}).keys()) & SENSITIVE_CLIENT_FIELDS
    with transaction() as conn:
        invite = ensure_invite_usable(conn, body.invite_token)
        if extra:
            write_audit(
                conn,
                None,
                "anonymous",
                "CLIENT_IGNORED_REGISTRATION_FIELDS",
                "registration_invite",
                invite["id"],
                after_json=",".join(sorted(extra)),
                ip_address=request.client.host if request.client else "",
                result="warning",
            )
        if one(conn, "SELECT id FROM users WHERE username = ?", (username,)):
            raise HTTPException(status_code=409, detail="账号已存在")
        required_phone = bool(invite["phone_required"]) or os.getenv("PHONE_VERIFICATION_REQUIRED", "false").lower() == "true"
        if invite["allowed_phone_hash"] and phone_hash(body.phone) != invite["allowed_phone_hash"]:
            raise HTTPException(status_code=403, detail="手机号不在邀请范围内")
        if required_phone:
            if not body.phone or not body.phone_verification_ticket:
                raise HTTPException(status_code=400, detail="请先完成手机号验证")
            consume_phone_ticket(conn, body.phone, "register", body.phone_verification_ticket)
        if invite["invite_type"] == "manager":
            existing_request = one(
                conn,
                """
                SELECT id
                FROM manager_registration_requests
                WHERE username = ? AND status IN ('pending', 'approved')
                """,
                (username,),
            )
            if existing_request:
                raise HTTPException(status_code=409, detail="账号已存在")
            request_id = str(uuid4())
            conn.execute(
                """
                INSERT INTO manager_registration_requests(
                  id, invite_id, username, display_name, phone_hash, phone_masked, password_hash, status
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, 'pending')
                """,
                (
                    request_id,
                    invite["id"],
                    username,
                    display_name,
                    phone_hash(body.phone) if body.phone else "",
                    masked_phone(body.phone) if body.phone else "",
                    hash_password(body.password),
                ),
            )
            mark_invite_used(conn, invite)
            write_audit(
                conn,
                None,
                "anonymous",
                "MANAGER_REGISTRATION_REQUESTED",
                "manager_registration_request",
                request_id,
                ip_address=request.client.host if request.client else "",
            )
            return {
                "status": "pending_approval",
                "request_id": request_id,
                "approval_required": True,
                "message": "管理者权限需要系统管理员审批，提交申请不代表账号已激活。",
            }
        unit = None
        if invite["invite_type"] == "unit":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (invite["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=409, detail="所属单位不可用")
        user_id = str(uuid4())
        conn.execute(
            """
            INSERT INTO users(
              id, username, password_hash, display_name, role, unit_id, active, must_change_password,
              can_manage_accounts, can_issue_manager_invites, can_view_system_status,
              can_view_detailed_metrics, can_manage_backups, can_restore_backups
            )
            VALUES (?, ?, ?, ?, ?, ?, 1, 0, ?, ?, ?, ?, ?, ?)
            """,
            (
                user_id,
                username,
                hash_password(body.password),
                display_name,
                invite["role"],
                invite["unit_id"] if invite["role"] == "unit_user" else None,
                1 if invite["role"] == "admin" else 0,
                1 if invite["role"] == "admin" else 0,
                1 if invite["role"] == "admin" else 0,
                1 if invite["role"] == "admin" else 0,
                1 if invite["role"] == "admin" else 0,
                1 if invite["role"] == "admin" else 0,
            ),
        )
        mark_invite_used(conn, invite)
        write_audit(
            conn,
            user_id,
            invite["role"],
            "INVITE_REGISTER",
            "registration_invite",
            invite["id"],
            ip_address=request.client.host if request.client else "",
        )
        token, token_digest, expires_at = create_session_token()
        conn.execute(
            """
            INSERT INTO sessions(id, token_hash, user_id, expires_at, client_info, ip_address)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()),
                token_digest,
                user_id,
                expires_at,
                request.headers.get("user-agent", ""),
                request.client.host if request.client else "",
            ),
        )
        user = one(conn, "SELECT * FROM users WHERE id = ?", (user_id,))
        return {"token": token, "expires_at": expires_at, "user": public_user(user, unit)}


@router.post("/login")
def login(body: LoginRequest, request: Request):
    username = body.username.strip()
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE username = ?", (username,))
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


@router.post("/step-up")
def step_up(body: StepUpRequest, user=Depends(current_user)):
    if body.purpose not in {"restore_backup"}:
        raise HTTPException(status_code=400, detail="二次验证用途不正确")
    if not verify_password(body.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="密码错误")
    token = secrets.token_urlsafe(40)
    with transaction() as conn:
        conn.execute(
            """
            INSERT INTO step_up_tokens(id, token_hash, user_id, purpose, expires_at)
            VALUES (?, ?, ?, ?, datetime('now', '+5 minutes'))
            """,
            (str(uuid4()), hash_token(token), user["id"], body.purpose),
        )
    return {"step_up_token": token, "purpose": body.purpose, "expires_in_seconds": 300}


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
