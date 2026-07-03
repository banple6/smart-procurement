from datetime import datetime, time, timezone
from zoneinfo import ZoneInfo

from fastapi import HTTPException

from ..database import one

LOCAL_TZ = ZoneInfo("Asia/Shanghai")


def local_now() -> datetime:
    return datetime.now(LOCAL_TZ)


def validate_cutoff_time(value: str) -> str:
    text = value.strip()
    try:
        hour_text, minute_text = text.split(":", 1)
        hour = int(hour_text)
        minute = int(minute_text)
    except (ValueError, AttributeError):
        raise HTTPException(status_code=400, detail="截止时间格式应为 HH:MM") from None
    if hour < 0 or hour > 23 or minute < 0 or minute > 59:
        raise HTTPException(status_code=400, detail="截止时间格式应为 HH:MM")
    return f"{hour:02d}:{minute:02d}"


def ensure_settings(conn):
    conn.execute("INSERT OR IGNORE INTO procurement_settings(id, cutoff_enabled, cutoff_time) VALUES (1, 1, '16:00')")


def cutoff_row(conn, business_date: str | None = None) -> dict:
    ensure_settings(conn)
    date_text = business_date or local_now().date().isoformat()
    override = one(conn, "SELECT * FROM procurement_cutoff_overrides WHERE business_date = ?", (date_text,))
    if override:
        return {
            "business_date": date_text,
            "enabled": bool(override["cutoff_enabled"]),
            "cutoff_time": override["cutoff_time"],
            "note": override.get("note") or "",
        }
    settings = one(conn, "SELECT * FROM procurement_settings WHERE id = 1")
    return {
        "business_date": date_text,
        "enabled": bool(settings["cutoff_enabled"]),
        "cutoff_time": settings["cutoff_time"],
        "note": "",
    }


def cutoff_payload(conn, now: datetime | None = None) -> dict:
    current = now.astimezone(LOCAL_TZ) if now else local_now()
    business_date = current.date().isoformat()
    row = cutoff_row(conn, business_date)
    cutoff_time = validate_cutoff_time(row["cutoff_time"])
    hour, minute = [int(part) for part in cutoff_time.split(":")]
    cutoff_local = datetime.combine(current.date(), time(hour, minute), LOCAL_TZ)
    cutoff_utc = cutoff_local.astimezone(timezone.utc)
    now_utc = current.astimezone(timezone.utc)
    is_closed = bool(row["enabled"]) and current >= cutoff_local
    remaining = 0 if is_closed or not row["enabled"] else max(0, int((cutoff_local - current).total_seconds()))
    return {
        "business_date": business_date,
        "enabled": bool(row["enabled"]),
        "cutoff_time": cutoff_time,
        "cutoff_at": cutoff_utc.isoformat().replace("+00:00", "Z"),
        "server_now": now_utc.isoformat().replace("+00:00", "Z"),
        "is_closed": is_closed,
        "remaining_seconds": remaining,
        "note": row.get("note") or "",
    }


def assert_order_window_open(conn):
    payload = cutoff_payload(conn)
    if payload["is_closed"]:
        raise HTTPException(
            status_code=409,
            detail="今日采购已经截止，请联系管理员",
            headers={"X-Error-Code": "ORDER_CUTOFF_PASSED"},
        )
