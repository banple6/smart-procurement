import hashlib
import json
from datetime import datetime, timezone
from uuid import uuid4
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, HTTPException, Query

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import current_user, require_admin_user
from ..schemas import AnnouncementAction, AnnouncementCreate, AnnouncementUpdate
from ..services.dashboard_cache import invalidate_dashboard_cache
from ..services.local_time import display_local_time
from ..services.realtime import bump_resources


router = APIRouter(tags=["announcements"])
SHANGHAI = ZoneInfo("Asia/Shanghai")


def _time_value(value: str | None, field: str) -> str | None:
    text = str(value or "").strip()
    if not text:
        return None
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=f"{field}格式不正确") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=SHANGHAI)
    return parsed.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def _normalize(body) -> dict:
    title = body.title.strip()
    content = body.content.strip()
    if not title or not content:
        raise HTTPException(status_code=400, detail="公告标题和正文不能为空")
    publish_at = _time_value(body.publish_at, "发布时间")
    expire_at = _time_value(body.expire_at, "失效时间")
    if publish_at and expire_at and expire_at <= publish_at:
        raise HTTPException(status_code=400, detail="失效时间必须晚于发布时间")
    unit_ids = list(dict.fromkeys(item.strip() for item in body.unit_ids if item.strip()))
    if body.audience_type == "specific_units" and not unit_ids:
        raise HTTPException(status_code=400, detail="指定子单位范围至少选择一个单位")
    if body.audience_type != "specific_units":
        unit_ids = []
    return {
        "title": title,
        "content": content,
        "level": body.level,
        "audience_type": body.audience_type,
        "unit_ids": unit_ids,
        "is_pinned": int(bool(body.is_pinned)),
        "publish_at": publish_at,
        "expire_at": expire_at,
    }


def _validate_units(conn, unit_ids: list[str]):
    if not unit_ids:
        return
    placeholders = ",".join("?" for _ in unit_ids)
    rows = all_rows(conn, f"SELECT id FROM units WHERE id IN ({placeholders})", unit_ids)
    found = {row["id"] for row in rows}
    if len(found) != len(unit_ids):
        raise HTTPException(status_code=404, detail="存在不存在的子单位，请刷新后重试")


def _units(conn, announcement_id: str) -> list[dict]:
    return all_rows(
        conn,
        """
        SELECT u.id, u.unit_code, u.unit_name
        FROM announcement_units au JOIN units u ON u.id = au.unit_id
        WHERE au.announcement_id = ?
        ORDER BY CASE WHEN length(u.unit_code) = 3 AND u.unit_code GLOB '[0-9][0-9][0-9]' THEN 0 ELSE 1 END, u.unit_code, u.unit_name
        """,
        (announcement_id,),
    )


def _out(conn, row: dict) -> dict:
    result = {**row, "is_pinned": bool(row.get("is_pinned")), "version": int(row.get("version") or 1)}
    result["units"] = _units(conn, row["id"])
    for field in ("publish_at", "expire_at", "created_at", "updated_at"):
        result[f"display_{field}"] = display_local_time(result.get(field))
    return result


def _announcement(conn, announcement_id: str) -> dict:
    row = one(
        conn,
        """
        SELECT a.*, u.display_name AS created_by_name
        FROM announcements a JOIN users u ON u.id = a.created_by
        WHERE a.id = ?
        """,
        (announcement_id,),
    )
    if not row:
        raise HTTPException(status_code=404, detail="公告不存在")
    return row


def _write_units(conn, announcement_id: str, unit_ids: list[str]):
    conn.execute("DELETE FROM announcement_units WHERE announcement_id = ?", (announcement_id,))
    conn.executemany(
        "INSERT INTO announcement_units(announcement_id, unit_id) VALUES (?, ?)",
        [(announcement_id, unit_id) for unit_id in unit_ids],
    )


def _hash(payload: dict) -> str:
    return hashlib.sha256(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")).hexdigest()


def _idempotent(conn, admin: dict, action: str, request_id: str | None, request_hash: str) -> dict | None:
    if not request_id:
        return None
    row = one(
        conn,
        """
        SELECT object_id, after_json FROM audit_logs
        WHERE actor_id = ? AND action = ? AND request_id = ? AND result = 'success'
        ORDER BY created_at, id LIMIT 1
        """,
        (admin["id"], action, request_id),
    )
    if not row:
        return None
    try:
        saved = json.loads(row.get("after_json") or "{}")
    except json.JSONDecodeError:
        saved = {}
    if saved.get("request_hash") != request_hash:
        raise HTTPException(status_code=409, detail="请求编号已被其他公告操作使用")
    return _out(conn, _announcement(conn, row["object_id"]))


def _audit(conn, admin: dict, action: str, announcement: dict, request_id: str | None = None, before: dict | None = None, request_hash: str = ""):
    write_audit(
        conn,
        admin["id"],
        admin["role"],
        action,
        "announcement",
        announcement["id"],
        before_json=json.dumps(before or {}, ensure_ascii=False, separators=(",", ":")),
        after_json=json.dumps(
            {
                "title": announcement["title"],
                "status": announcement["status"],
                "level": announcement["level"],
                "audience_type": announcement["audience_type"],
                "version": announcement["version"],
                "request_hash": request_hash,
            },
            ensure_ascii=False,
            separators=(",", ":"),
        ),
        request_id=request_id or "",
    )


def _active_filter_for_user(user: dict) -> tuple[str, list[str]]:
    scope = ["a.audience_type = 'all'"]
    params: list[str] = []
    if user["role"] == "admin":
        scope.append("a.audience_type = 'admins'")
    elif user.get("unit_id"):
        scope.append("a.audience_type = 'units'")
        scope.append("(a.audience_type = 'specific_units' AND EXISTS (SELECT 1 FROM announcement_units au WHERE au.announcement_id = a.id AND au.unit_id = ?))")
        params.append(user["unit_id"])
    return "(" + " OR ".join(scope) + ")", params


def active_announcements(conn, user: dict, limit: int = 10) -> list[dict]:
    scope, params = _active_filter_for_user(user)
    rows = all_rows(
        conn,
        f"""
        SELECT a.*, u.display_name AS created_by_name
        FROM announcements a JOIN users u ON u.id = a.created_by
        WHERE a.status = 'published'
          AND (a.publish_at IS NULL OR a.publish_at <= CURRENT_TIMESTAMP)
          AND (a.expire_at IS NULL OR a.expire_at > CURRENT_TIMESTAMP)
          AND {scope}
        ORDER BY a.is_pinned DESC,
                 CASE a.level WHEN 'urgent' THEN 0 WHEN 'important' THEN 1 ELSE 2 END,
                 COALESCE(a.publish_at, a.created_at) DESC, a.id DESC
        LIMIT ?
        """,
        (*params, max(1, min(int(limit), 20))),
    )
    return [_out(conn, row) for row in rows]


@router.get("/admin/announcements")
def list_admin_announcements(
    status: str | None = Query(default=None, pattern="^(draft|published|offline)$"),
    level: str | None = Query(default=None, pattern="^(normal|important|urgent)$"),
    q: str = Query(default="", max_length=160),
    limit: int = Query(default=100, ge=1, le=200),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        clauses, params = ["1=1"], []
        if status:
            clauses.append("a.status = ?")
            params.append(status)
        if level:
            clauses.append("a.level = ?")
            params.append(level)
        if q.strip():
            clauses.append("(a.title LIKE ? ESCAPE '\\' OR a.content LIKE ? ESCAPE '\\')")
            escaped = q.strip().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
            params.extend([f"%{escaped}%", f"%{escaped}%"])
        rows = all_rows(
            conn,
            f"""SELECT a.*, u.display_name AS created_by_name FROM announcements a JOIN users u ON u.id = a.created_by
                WHERE {' AND '.join(clauses)}
                ORDER BY a.is_pinned DESC, COALESCE(a.publish_at, a.created_at) DESC, a.updated_at DESC LIMIT ?""",
            (*params, limit),
        )
        return {"items": [_out(conn, row) for row in rows]}


@router.post("/admin/announcements")
def create_announcement(body: AnnouncementCreate, admin=Depends(require_admin_user)):
    data = _normalize(body)
    request_hash = _hash(data)
    with transaction() as conn:
        existing = _idempotent(conn, admin, "ANNOUNCEMENT_CREATED", body.client_request_id, request_hash)
        if existing:
            return existing
        _validate_units(conn, data["unit_ids"])
        announcement_id = str(uuid4())
        conn.execute(
            """INSERT INTO announcements(id, title, content, level, status, is_pinned, audience_type, publish_at, expire_at, created_by)
               VALUES (?, ?, ?, ?, 'draft', ?, ?, ?, ?, ?)""",
            (announcement_id, data["title"], data["content"], data["level"], data["is_pinned"], data["audience_type"], data["publish_at"], data["expire_at"], admin["id"]),
        )
        _write_units(conn, announcement_id, data["unit_ids"])
        announcement = _announcement(conn, announcement_id)
        _audit(conn, admin, "ANNOUNCEMENT_CREATED", announcement, body.client_request_id, request_hash=request_hash)
        bump_resources(conn, "announcements", "dashboard")
        return _out(conn, announcement)


@router.get("/admin/announcements/{announcement_id}")
def get_admin_announcement(announcement_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        return _out(conn, _announcement(conn, announcement_id))


@router.patch("/admin/announcements/{announcement_id}")
def update_announcement(announcement_id: str, body: AnnouncementUpdate, admin=Depends(require_admin_user)):
    data = _normalize(body)
    with transaction() as conn:
        current = _announcement(conn, announcement_id)
        if int(current["version"]) != body.expected_version:
            raise HTTPException(status_code=409, detail="公告已被其他管理员修改，请刷新后重试。")
        _validate_units(conn, data["unit_ids"])
        cursor = conn.execute(
            """UPDATE announcements SET title = ?, content = ?, level = ?, audience_type = ?, is_pinned = ?, publish_at = ?, expire_at = ?,
               updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ? AND version = ?""",
            (data["title"], data["content"], data["level"], data["audience_type"], data["is_pinned"], data["publish_at"], data["expire_at"], announcement_id, body.expected_version),
        )
        if cursor.rowcount != 1:
            raise HTTPException(status_code=409, detail="公告已被其他管理员修改，请刷新后重试。")
        _write_units(conn, announcement_id, data["unit_ids"])
        updated = _announcement(conn, announcement_id)
        before = {"status": current["status"], "level": current["level"], "audience_type": current["audience_type"], "is_pinned": bool(current["is_pinned"]), "version": current["version"]}
        _audit(conn, admin, "ANNOUNCEMENT_UPDATED", updated, body.client_request_id, before=before)
        if bool(current["is_pinned"]) != bool(updated["is_pinned"]):
            _audit(conn, admin, "ANNOUNCEMENT_PIN_CHANGED", updated, body.client_request_id, before=before)
        bump_resources(conn, "announcements", "dashboard")
        invalidate_dashboard_cache()
        return _out(conn, updated)


def _transition(announcement_id: str, body: AnnouncementAction, admin: dict, *, target: str, action: str):
    request_hash = _hash({"announcement_id": announcement_id, "target": target})
    with transaction() as conn:
        existing = _idempotent(conn, admin, action, body.client_request_id, request_hash)
        if existing:
            return existing
        current = _announcement(conn, announcement_id)
        if current["status"] == target:
            return _out(conn, current)
        if int(current["version"]) != body.expected_version:
            raise HTTPException(status_code=409, detail="公告已被其他管理员修改，请刷新后重试。")
        if target == "published" and current["status"] not in {"draft", "offline"}:
            raise HTTPException(status_code=409, detail="当前公告不能发布")
        if target == "offline" and current["status"] != "published":
            raise HTTPException(status_code=409, detail="只有已发布公告可以下线")
        cursor = conn.execute(
            """UPDATE announcements SET status = ?, publish_at = CASE WHEN ? = 'published' THEN COALESCE(publish_at, CURRENT_TIMESTAMP) ELSE publish_at END,
               updated_at = CURRENT_TIMESTAMP, version = version + 1 WHERE id = ? AND version = ?""",
            (target, target, announcement_id, body.expected_version),
        )
        if cursor.rowcount != 1:
            raise HTTPException(status_code=409, detail="公告已被其他管理员修改，请刷新后重试。")
        updated = _announcement(conn, announcement_id)
        _audit(conn, admin, action, updated, body.client_request_id, before={"status": current["status"], "version": current["version"]}, request_hash=request_hash)
        bump_resources(conn, "announcements", "dashboard")
        invalidate_dashboard_cache()
        return _out(conn, updated)


@router.post("/admin/announcements/{announcement_id}/publish")
def publish_announcement(announcement_id: str, body: AnnouncementAction, admin=Depends(require_admin_user)):
    return _transition(announcement_id, body, admin, target="published", action="ANNOUNCEMENT_PUBLISHED")


@router.post("/admin/announcements/{announcement_id}/offline")
def offline_announcement(announcement_id: str, body: AnnouncementAction, admin=Depends(require_admin_user)):
    return _transition(announcement_id, body, admin, target="offline", action="ANNOUNCEMENT_OFFLINED")


@router.get("/announcements")
def list_visible_announcements(limit: int = Query(default=10, ge=1, le=20), user=Depends(current_user)):
    with connect() as conn:
        return {"items": active_announcements(conn, user, limit)}


@router.get("/announcements/{announcement_id}")
def visible_announcement_detail(announcement_id: str, user=Depends(current_user)):
    with connect() as conn:
        scope, params = _active_filter_for_user(user)
        item = one(
            conn,
            f"""SELECT a.*, u.display_name AS created_by_name
                FROM announcements a JOIN users u ON u.id = a.created_by
                WHERE a.id = ?
                  AND a.status = 'published'
                  AND (a.publish_at IS NULL OR a.publish_at <= CURRENT_TIMESTAMP)
                  AND (a.expire_at IS NULL OR a.expire_at > CURRENT_TIMESTAMP)
                  AND {scope}""",
            (announcement_id, *params),
        )
        if not item:
            raise HTTPException(status_code=404, detail="公告不存在或当前账号无权查看")
        return _out(conn, item)
