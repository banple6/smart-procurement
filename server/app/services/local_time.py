from __future__ import annotations

from datetime import datetime, timezone
from zoneinfo import ZoneInfo


LOCAL_TZ = ZoneInfo("Asia/Shanghai")
UTC = timezone.utc


def local_now() -> datetime:
    return datetime.now(LOCAL_TZ)


def display_local_time(value: str | None) -> str:
    """Convert database UTC timestamps to the user-facing Shanghai time."""
    text = str(value or "").strip()
    if not text:
        return ""
    try:
        parsed = datetime.fromisoformat(text.replace("Z", "+00:00"))
    except ValueError:
        return text
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=UTC)
    return parsed.astimezone(LOCAL_TZ).strftime("%Y-%m-%d %H:%M:%S")
