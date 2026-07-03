import base64
import os
import secrets
from io import BytesIO
from urllib.parse import quote
from uuid import uuid4

import qrcode
import qrcode.image.svg
from fastapi import APIRouter, Depends, Header, HTTPException, Request, Response

from ..database import all_rows, connect, one, write_audit
from ..dependencies import current_bearer_user, current_user
from ..schemas import WebQrScanRequest
from ..security import hash_token
from ..web_session import (
    CSRF_COOKIE,
    QR_BINDING_COOKIE,
    clear_qr_binding_cookie,
    clear_web_cookies,
    qr_ttl_seconds,
    set_qr_binding_cookie,
    set_web_cookies,
    web_absolute_seconds,
    web_idle_seconds,
    web_session_cookie_name,
)
from .auth import public_user

router = APIRouter(prefix="/web-auth", tags=["web-auth"])
mobile_router = APIRouter(prefix="/mobile", tags=["mobile-web-auth"])


def ensure_qr_allowed():
    if os.getenv("APP_ENV") == "production" and not os.getenv("WEB_PUBLIC_ORIGIN", "").startswith("https://"):
        raise HTTPException(status_code=503, detail="网页版扫码登录需要 HTTPS 域名")


def now_epoch(conn) -> int:
    return int(one(conn, "SELECT CAST(strftime('%s', 'now') AS INTEGER) AS value")["value"])


def text_epoch(conn, value: str) -> int:
    return int(one(conn, "SELECT CAST(strftime('%s', ?) AS INTEGER) AS value", (value,))["value"])


def is_expired(conn, expires_at: str) -> bool:
    return bool(one(conn, "SELECT CURRENT_TIMESTAMP > ? AS expired", (expires_at,))["expired"])


def public_status(conn, challenge: dict) -> str:
    if challenge["status"] in {"rejected", "consumed"}:
        return challenge["status"]
    if is_expired(conn, challenge["expires_at"]):
        return "expired"
    return challenge["status"]


def detect_browser(user_agent: str) -> tuple[str, str]:
    ua = user_agent or ""
    if "Edg/" in ua:
        browser = "Edge"
    elif "Chrome/" in ua or "CriOS/" in ua:
        browser = "Chrome"
    elif "Firefox/" in ua:
        browser = "Firefox"
    elif "Safari/" in ua:
        browser = "Safari"
    else:
        browser = "浏览器"

    if "Windows" in ua:
        os_name = "Windows"
    elif "Mac OS X" in ua or "Macintosh" in ua:
        os_name = "macOS"
    elif "iPhone" in ua or "iPad" in ua:
        os_name = "iOS"
    elif "Android" in ua:
        os_name = "Android"
    elif "Linux" in ua:
        os_name = "Linux"
    else:
        os_name = "未知系统"
    return browser, os_name


def client_ip(request: Request) -> str:
    forwarded = request.headers.get("x-forwarded-for", "").split(",", 1)[0].strip()
    if forwarded:
        return forwarded
    return request.client.host if request.client else ""


def mask_ip(value: str) -> str:
    parts = value.split(".")
    if len(parts) == 4:
        return f"{parts[0]}.{parts[1]}.*.*"
    return value or "未知"


def qr_svg_data_url(payload: str) -> str:
    image = qrcode.make(payload, image_factory=qrcode.image.svg.SvgPathImage, box_size=8, border=2)
    out = BytesIO()
    image.save(out)
    encoded = base64.b64encode(out.getvalue()).decode()
    return f"data:image/svg+xml;base64,{encoded}"


def response_payload(conn, challenge: dict, include_qr: str = "") -> dict:
    payload = {
        "challenge_id": challenge["id"],
        "status": public_status(conn, challenge),
        "expires_at": text_epoch(conn, challenge["expires_at"]),
        "server_now": now_epoch(conn),
    }
    if include_qr:
        payload["qr_payload"] = include_qr
        payload["qr_svg_data_url"] = qr_svg_data_url(include_qr)
    return payload


def binding_hash_from_request(request: Request) -> str:
    raw = request.cookies.get(QR_BINDING_COOKIE, "").strip()
    if not raw:
        raise HTTPException(status_code=403, detail="二维码已失效，请刷新页面")
    return hash_token(raw)


def challenge_for_browser(conn, challenge_id: str, request: Request) -> dict:
    challenge = one(conn, "SELECT * FROM web_login_challenges WHERE id = ?", (challenge_id,))
    if not challenge:
        raise HTTPException(status_code=404, detail="二维码不存在")
    if challenge["browser_binding_hash"] != binding_hash_from_request(request):
        raise HTTPException(status_code=403, detail="二维码已失效，请刷新页面")
    return challenge


def session_id_from_authorization(conn, authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        return ""
    token = authorization.removeprefix("Bearer ").strip()
    session = one(conn, "SELECT id FROM sessions WHERE token_hash = ?", (hash_token(token),))
    return session["id"] if session else ""


def user_with_unit(conn, user_id: str) -> tuple[dict, dict | None]:
    user = one(conn, "SELECT * FROM users WHERE id = ?", (user_id,))
    if not user or not user["active"]:
        raise HTTPException(status_code=403, detail="账号已停用，请联系管理员")
    unit = None
    if user["role"] == "unit_user":
        unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
        if not unit or not unit["active"]:
            raise HTTPException(status_code=403, detail="所属单位已停用")
    if user["must_change_password"]:
        raise HTTPException(status_code=403, detail="请先修改初始密码")
    return user, unit


def browser_info(challenge: dict) -> dict:
    return {
        "name": challenge["browser_name"] or "浏览器",
        "os": challenge["browser_os"] or "未知系统",
        "ip": mask_ip(challenge["browser_ip"]),
        "user_agent": challenge["browser_user_agent"],
    }


@router.post("/qr/challenges")
def create_challenge(request: Request, response: Response):
    ensure_qr_allowed()
    qr_token = secrets.token_urlsafe(48)
    binding = secrets.token_urlsafe(48)
    challenge_id = str(uuid4())
    user_agent = request.headers.get("user-agent", "")
    browser_name, browser_os = detect_browser(user_agent)
    ip = client_ip(request)
    qr_payload = f"jingrongxianpei://web-login?token={quote(qr_token)}"
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO web_login_challenges(
              id, qr_token_hash, browser_binding_hash, status, expires_at,
              browser_user_agent, browser_name, browser_os, browser_ip
            )
            VALUES (?, ?, ?, 'pending', datetime('now', ?), ?, ?, ?, ?)
            """,
            (
                challenge_id,
                hash_token(qr_token),
                hash_token(binding),
                f"+{qr_ttl_seconds()} seconds",
                user_agent,
                browser_name,
                browser_os,
                ip,
            ),
        )
        challenge = one(conn, "SELECT * FROM web_login_challenges WHERE id = ?", (challenge_id,))
        conn.commit()
        set_qr_binding_cookie(response, request, binding)
        return response_payload(conn, challenge, qr_payload)


@router.get("/qr/challenges/{challenge_id}/status")
def challenge_status(challenge_id: str, request: Request):
    with connect() as conn:
        challenge = challenge_for_browser(conn, challenge_id, request)
        return response_payload(conn, challenge)


@router.post("/qr/challenges/{challenge_id}/consume")
def consume_challenge(challenge_id: str, request: Request, response: Response):
    with connect() as conn:
        challenge = challenge_for_browser(conn, challenge_id, request)
        if public_status(conn, challenge) == "expired":
            raise HTTPException(status_code=410, detail="二维码已过期，请刷新后重试")
        if challenge["status"] != "approved" or challenge["consumed_at"]:
            raise HTTPException(status_code=409, detail="请先在 App 上确认登录")
        user, unit = user_with_unit(conn, challenge["approved_by_user_id"])
        raw_session_token = secrets.token_urlsafe(48)
        session_id = str(uuid4())
        conn.execute(
            """
            INSERT INTO web_sessions(
              id, token_hash, user_id, role, unit_id, idle_expires_at, absolute_expires_at,
              browser_user_agent, browser_name, browser_os, browser_ip, source_challenge_id
            )
            VALUES (?, ?, ?, ?, ?, datetime('now', ?), datetime('now', ?), ?, ?, ?, ?, ?)
            """,
            (
                session_id,
                hash_token(raw_session_token),
                user["id"],
                user["role"],
                user["unit_id"],
                f"+{web_idle_seconds()} seconds",
                f"+{web_absolute_seconds()} seconds",
                challenge["browser_user_agent"],
                challenge["browser_name"],
                challenge["browser_os"],
                challenge["browser_ip"],
                challenge_id,
            ),
        )
        conn.execute(
            """
            UPDATE web_login_challenges
            SET status = 'consumed', consumed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (challenge_id,),
        )
        write_audit(conn, user["id"], user["role"], "WEB_QR_LOGIN_CONSUMED", "web_login_challenge", challenge_id)
        conn.commit()
    set_web_cookies(response, request, raw_session_token)
    clear_qr_binding_cookie(response)
    return {
        "ok": True,
        "user": public_user(user, unit),
        "idle_expires_in": web_idle_seconds(),
        "absolute_expires_in": web_absolute_seconds(),
    }


@router.get("/me")
def web_me(user=Depends(current_user)):
    with connect() as conn:
        unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],)) if user["unit_id"] else None
    return public_user(user, unit)


@router.post("/logout")
def web_logout(request: Request, response: Response, user=Depends(current_user)):
    token = request.cookies.get(web_session_cookie_name(), "").strip()
    if token:
        with connect() as conn:
            conn.execute(
                """
                UPDATE web_sessions
                SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = '用户退出登录'
                WHERE token_hash = ? AND revoked_at IS NULL
                """,
                (hash_token(token),),
            )
            write_audit(conn, user["id"], user["role"], "WEB_LOGOUT", "web_session", "")
            conn.commit()
    clear_web_cookies(response)
    return {"ok": True}


@mobile_router.post("/web-auth/qr/scan")
def mobile_scan_qr(
    body: WebQrScanRequest,
    authorization: str | None = Header(default=None),
    user=Depends(current_bearer_user),
):
    if user["must_change_password"]:
        raise HTTPException(status_code=403, detail="请先修改初始密码")
    with connect() as conn:
        if user["role"] == "unit_user":
            unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
            if not unit or not unit["active"]:
                raise HTTPException(status_code=403, detail="所属单位已停用")
        challenge = one(conn, "SELECT * FROM web_login_challenges WHERE qr_token_hash = ?", (hash_token(body.qr_token),))
        if not challenge:
            raise HTTPException(status_code=404, detail="二维码不存在")
        if public_status(conn, challenge) == "expired":
            raise HTTPException(status_code=410, detail="二维码已过期，请刷新后重试")
        if challenge["status"] != "pending":
            raise HTTPException(status_code=409, detail="二维码状态已变化，请刷新页面")
        conn.execute(
            """
            UPDATE web_login_challenges
            SET status = 'scanned', scanned_at = CURRENT_TIMESTAMP, scanned_by_user_id = ?,
                app_session_id = ?, device_name = ?, app_version = ?, role_snapshot = ?,
                unit_id_snapshot = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (
                user["id"],
                session_id_from_authorization(conn, authorization),
                body.device_name.strip()[:80],
                body.app_version.strip()[:40],
                user["role"],
                user["unit_id"],
                challenge["id"],
            ),
        )
        challenge = one(conn, "SELECT * FROM web_login_challenges WHERE id = ?", (challenge["id"],))
        unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],)) if user["unit_id"] else None
        write_audit(conn, user["id"], user["role"], "WEB_QR_SCAN", "web_login_challenge", challenge["id"])
        conn.commit()
        return {
            "challenge_id": challenge["id"],
            "status": "scanned",
            "browser": browser_info(challenge),
            "device_name": challenge["device_name"],
            "app_version": challenge["app_version"],
            "user": public_user(user, unit),
        }


def update_scanned_challenge(challenge_id: str, status: str, user: dict):
    if user["must_change_password"]:
        raise HTTPException(status_code=403, detail="请先修改初始密码")
    with connect() as conn:
        challenge = one(conn, "SELECT * FROM web_login_challenges WHERE id = ?", (challenge_id,))
        if not challenge:
            raise HTTPException(status_code=404, detail="二维码不存在")
        if public_status(conn, challenge) == "expired":
            raise HTTPException(status_code=410, detail="二维码已过期，请刷新后重试")
        if challenge["status"] != "scanned":
            raise HTTPException(status_code=409, detail="二维码状态已变化，请刷新页面")
        if challenge["scanned_by_user_id"] != user["id"]:
            raise HTTPException(status_code=403, detail="只能确认自己扫码的登录")
        if status == "approved":
            conn.execute(
                """
                UPDATE web_login_challenges
                SET status = 'approved', approved_at = CURRENT_TIMESTAMP, approved_by_user_id = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                (user["id"], challenge_id),
            )
            action = "WEB_QR_APPROVE"
        else:
            conn.execute(
                """
                UPDATE web_login_challenges
                SET status = 'rejected', rejected_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                (challenge_id,),
            )
            action = "WEB_QR_REJECT"
        write_audit(conn, user["id"], user["role"], action, "web_login_challenge", challenge_id)
        updated = one(conn, "SELECT * FROM web_login_challenges WHERE id = ?", (challenge_id,))
        conn.commit()
        return {"challenge_id": challenge_id, "status": updated["status"]}


@mobile_router.post("/web-auth/qr/{challenge_id}/approve")
def mobile_approve_qr(challenge_id: str, user=Depends(current_bearer_user)):
    return update_scanned_challenge(challenge_id, "approved", user)


@mobile_router.post("/web-auth/qr/{challenge_id}/reject")
def mobile_reject_qr(challenge_id: str, user=Depends(current_bearer_user)):
    return update_scanned_challenge(challenge_id, "rejected", user)


@mobile_router.get("/web-sessions")
def mobile_web_sessions(user=Depends(current_bearer_user)):
    with connect() as conn:
        rows = all_rows(
            conn,
            """
            SELECT s.id, s.created_at, s.last_seen_at, s.idle_expires_at, s.absolute_expires_at,
              s.revoked_at, s.browser_name, s.browser_os, s.browser_ip,
              c.device_name, c.app_version
            FROM web_sessions s
            LEFT JOIN web_login_challenges c ON c.id = s.source_challenge_id
            WHERE s.user_id = ?
            ORDER BY s.last_seen_at DESC
            """,
            (user["id"],),
        )
    for row in rows:
        row["browser_ip"] = mask_ip(row["browser_ip"])
        row["active"] = not row["revoked_at"]
    return {"items": rows}


@mobile_router.delete("/web-sessions/{session_id}")
def mobile_revoke_web_session(session_id: str, user=Depends(current_bearer_user)):
    with connect() as conn:
        conn.execute(
            """
            UPDATE web_sessions
            SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = 'App 主动退出网页登录'
            WHERE id = ? AND user_id = ? AND revoked_at IS NULL
            """,
            (session_id, user["id"]),
        )
        write_audit(conn, user["id"], user["role"], "WEB_SESSION_REVOKE", "web_session", session_id)
        conn.commit()
    return {"ok": True}


@mobile_router.post("/web-sessions/revoke-all")
def mobile_revoke_all_web_sessions(user=Depends(current_bearer_user)):
    with connect() as conn:
        conn.execute(
            """
            UPDATE web_sessions
            SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = 'App 主动退出全部网页登录'
            WHERE user_id = ? AND revoked_at IS NULL
            """,
            (user["id"],),
        )
        write_audit(conn, user["id"], user["role"], "WEB_SESSION_REVOKE_ALL", "web_session", "")
        conn.commit()
    return {"ok": True}
