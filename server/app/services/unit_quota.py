"""Transactional monthly procurement quota accounts for units.

The account balance is the authoritative, carried-forward value.  Monthly
history and the ledger are audit records; they are not recomputed from orders.
"""

from __future__ import annotations

import json
from datetime import date
from uuid import uuid4

from fastapi import HTTPException

from ..database import all_rows, one, write_audit
from .local_time import display_local_time, local_now


def quota_month_now() -> str:
    return local_now().strftime("%Y-%m")


def _month_after(value: str) -> str:
    year, month = (int(part) for part in value.split("-", 1))
    return f"{year + 1:04d}-01" if month == 12 else f"{year:04d}-{month + 1:02d}"


def _valid_month(value: str) -> str:
    try:
        year, month = (int(part) for part in value.split("-", 1))
        date(year, month, 1)
    except (TypeError, ValueError):
        raise HTTPException(status_code=400, detail="额度月份格式应为 YYYY-MM") from None
    return value


def _account(conn, unit_id: str) -> dict:
    conn.execute(
        """
        INSERT OR IGNORE INTO unit_quota_accounts(
          unit_id, quota_enabled, default_monthly_quota_cents, balance_cents, version
        ) VALUES (?, 0, 0, 0, 1)
        """,
        (unit_id,),
    )
    return one(conn, "SELECT * FROM unit_quota_accounts WHERE unit_id = ?", (unit_id,))


def _ledger(
    conn,
    *,
    unit_id: str,
    quota_month: str,
    event_type: str,
    delta_cents: int,
    balance_after_cents: int,
    order_id: str | None = None,
    actor_id: str | None = None,
    note: str = "",
    request_id: str | None = None,
):
    conn.execute(
        """
        INSERT INTO unit_quota_ledger(
          id, unit_id, quota_month, event_type, delta_cents, balance_after_cents,
          order_id, actor_id, note, request_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (
            str(uuid4()), unit_id, quota_month, event_type, int(delta_cents), int(balance_after_cents),
            order_id, actor_id, note.strip()[:300], request_id or None,
        ),
    )


def ensure_quota_month(conn, unit_id: str, current_month: str | None = None) -> dict:
    """Lazily grant each unmaterialized Shanghai month exactly once."""
    current_month = _valid_month(current_month or quota_month_now())
    account = _account(conn, unit_id)
    if not bool(account["quota_enabled"]):
        return account
    cursor_month = account.get("last_granted_month") or current_month
    while cursor_month <= current_month:
        existing = one(
            conn,
            "SELECT id FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?",
            (unit_id, cursor_month),
        )
        if not existing:
            opening = int(account["balance_cents"])
            grant = int(account["default_monthly_quota_cents"])
            conn.execute(
                """
                INSERT INTO unit_quota_months(
                  id, unit_id, quota_month, base_quota_cents, opening_balance_cents, grant_cents
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                (str(uuid4()), unit_id, cursor_month, grant, opening, grant),
            )
            conn.execute(
                """
                UPDATE unit_quota_accounts
                SET balance_cents = balance_cents + ?, last_granted_month = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE unit_id = ?
                """,
                (grant, cursor_month, unit_id),
            )
            account = _account(conn, unit_id)
            _ledger(
                conn,
                unit_id=unit_id,
                quota_month=cursor_month,
                event_type="MONTHLY_GRANT",
                delta_cents=grant,
                balance_after_cents=int(account["balance_cents"]),
                note="月度基础额度发放",
            )
        else:
            conn.execute(
                "UPDATE unit_quota_accounts SET last_granted_month = ? WHERE unit_id = ? AND (last_granted_month IS NULL OR last_granted_month < ?)",
                (cursor_month, unit_id, cursor_month),
            )
        if cursor_month == current_month:
            break
        cursor_month = _month_after(cursor_month)
        account = _account(conn, unit_id)
    return _account(conn, unit_id)


def _insufficient(available: int, required: int):
    raise HTTPException(
        status_code=409,
        detail={
            "code": "QUOTA_INSUFFICIENT",
            "message": "本月可用采购额度不足，无法提交需求。",
            "available_cents": available,
            "required_cents": required,
            "shortfall_cents": max(required - available, 0),
        },
    )


def reserve_order_quota(conn, *, unit_id: str, order_id: str, amount_cents: int, actor_id: str | None, request_id: str | None = None):
    account = ensure_quota_month(conn, unit_id)
    if not bool(account["quota_enabled"]):
        return
    existing = one(conn, "SELECT * FROM order_quota_allocations WHERE order_id = ?", (order_id,))
    if existing:
        return
    available = int(account["balance_cents"])
    amount = int(amount_cents)
    cursor = conn.execute(
        """
        UPDATE unit_quota_accounts
        SET balance_cents = balance_cents - ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
        WHERE unit_id = ? AND quota_enabled = 1 AND balance_cents >= ?
        """,
        (amount, unit_id, amount),
    )
    if cursor.rowcount != 1:
        _insufficient(available, amount)
    account = _account(conn, unit_id)
    month = quota_month_now()
    conn.execute(
        """
        INSERT INTO order_quota_allocations(id, order_id, unit_id, quota_month, amount_cents, status)
        VALUES (?, ?, ?, ?, ?, 'reserved')
        """,
        (str(uuid4()), order_id, unit_id, month, amount),
    )
    _ledger(
        conn, unit_id=unit_id, quota_month=month, event_type="ORDER_RESERVE", delta_cents=-amount,
        balance_after_cents=int(account["balance_cents"]), order_id=order_id, actor_id=actor_id,
        request_id=request_id,
    )


def adjust_order_quota(conn, *, unit_id: str, order_id: str, new_amount_cents: int, actor_id: str | None):
    allocation = one(conn, "SELECT * FROM order_quota_allocations WHERE order_id = ?", (order_id,))
    if not allocation or allocation["status"] != "reserved":
        return
    old_amount = int(allocation["amount_cents"])
    new_amount = int(new_amount_cents)
    delta = new_amount - old_amount
    if delta == 0:
        return
    account = ensure_quota_month(conn, unit_id)
    if delta > 0:
        available = int(account["balance_cents"])
        cursor = conn.execute(
            "UPDATE unit_quota_accounts SET balance_cents = balance_cents - ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ? AND balance_cents >= ?",
            (delta, unit_id, delta),
        )
        if cursor.rowcount != 1:
            _insufficient(available, delta)
    else:
        conn.execute(
            "UPDATE unit_quota_accounts SET balance_cents = balance_cents + ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ?",
            (-delta, unit_id),
        )
    conn.execute("UPDATE order_quota_allocations SET amount_cents = ?, updated_at = CURRENT_TIMESTAMP WHERE order_id = ?", (new_amount, order_id))
    account = _account(conn, unit_id)
    _ledger(
        conn, unit_id=unit_id, quota_month=allocation["quota_month"], event_type="ORDER_ADJUST", delta_cents=-delta,
        balance_after_cents=int(account["balance_cents"]), order_id=order_id, actor_id=actor_id,
    )


def release_order_quota(conn, *, order_id: str, actor_id: str | None, note: str = ""):
    allocation = one(conn, "SELECT * FROM order_quota_allocations WHERE order_id = ?", (order_id,))
    if not allocation or allocation["status"] != "reserved":
        return
    amount = int(allocation["amount_cents"])
    cursor = conn.execute(
        "UPDATE order_quota_allocations SET status = 'released', updated_at = CURRENT_TIMESTAMP WHERE order_id = ? AND status = 'reserved'",
        (order_id,),
    )
    if cursor.rowcount != 1:
        return
    conn.execute(
        "UPDATE unit_quota_accounts SET balance_cents = balance_cents + ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ?",
        (amount, allocation["unit_id"]),
    )
    account = _account(conn, allocation["unit_id"])
    _ledger(
        conn, unit_id=allocation["unit_id"], quota_month=allocation["quota_month"], event_type="ORDER_RELEASE",
        delta_cents=amount, balance_after_cents=int(account["balance_cents"]), order_id=order_id,
        actor_id=actor_id, note=note,
    )


def finalize_order_quota(conn, *, order_id: str, actor_id: str | None):
    allocation = one(conn, "SELECT * FROM order_quota_allocations WHERE order_id = ?", (order_id,))
    if not allocation or allocation["status"] != "reserved":
        return
    if conn.execute("UPDATE order_quota_allocations SET status = 'finalized', updated_at = CURRENT_TIMESTAMP WHERE order_id = ? AND status = 'reserved'", (order_id,)).rowcount:
        account = _account(conn, allocation["unit_id"])
        _ledger(
            conn, unit_id=allocation["unit_id"], quota_month=allocation["quota_month"], event_type="ORDER_FINALIZED",
            delta_cents=0, balance_after_cents=int(account["balance_cents"]), order_id=order_id, actor_id=actor_id,
        )


def quota_payload(conn, unit_id: str, current_month: str | None = None) -> dict:
    account = ensure_quota_month(conn, unit_id, current_month)
    month = _valid_month(current_month or quota_month_now())
    row = one(conn, "SELECT * FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?", (unit_id, month))
    adjustment = one(
        conn,
        "SELECT COALESCE(SUM(delta_cents), 0) AS amount FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type IN ('MANUAL_INCREASE', 'MANUAL_DECREASE')",
        (unit_id, month),
    )["amount"]
    used = one(
        conn,
        "SELECT COALESCE(-SUM(delta_cents), 0) AS amount FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type IN ('ORDER_RESERVE', 'ORDER_ADJUST', 'ORDER_RELEASE')",
        (unit_id, month),
    )["amount"]
    return {
        "enabled": bool(account["quota_enabled"]),
        "quota_month": month,
        "default_monthly_quota_cents": int(account["default_monthly_quota_cents"]),
        "base_quota_cents": int(row["base_quota_cents"]) if row else 0,
        "opening_balance_cents": int(row["opening_balance_cents"]) if row else int(account["balance_cents"]),
        "adjustment_cents": int(adjustment or 0),
        "used_this_month_cents": int(used or 0),
        "available_cents": int(account["balance_cents"]),
        "last_granted_month": account.get("last_granted_month") or "",
        "version": int(account["version"]),
        "updated_at": account.get("updated_at") or "",
        # Keep the UTC database value intact while giving clients an explicit
        # user-facing Shanghai-time field.
        "display_updated_at": display_local_time(account.get("updated_at")),
    }


def _stale_write(account: dict):
    raise HTTPException(
        status_code=409,
        detail={
            "code": "STALE_WRITE",
            "message": "数据已被其他管理员更新，请刷新后重试",
            "entity_type": "unit_quota_account",
            "entity_id": account["unit_id"],
            "current_version": int(account["version"]),
        },
    )


def update_quota_settings(
    conn,
    *,
    unit_id: str,
    enabled: bool,
    default_monthly_quota_cents: int,
    actor: dict,
    expected_version: int | None = None,
):
    if enabled and default_monthly_quota_cents <= 0:
        raise HTTPException(status_code=400, detail="启用额度控制时必须设置大于 0 的月度基础额度")
    before = _account(conn, unit_id)
    sql = "UPDATE unit_quota_accounts SET quota_enabled = ?, default_monthly_quota_cents = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ?"
    params: tuple = (int(enabled), int(default_monthly_quota_cents), unit_id)
    if expected_version is not None:
        sql += " AND version = ?"
        params += (expected_version,)
    if conn.execute(sql, params).rowcount != 1:
        _stale_write(_account(conn, unit_id))
    if enabled:
        ensure_quota_month(conn, unit_id)
    after = _account(conn, unit_id)
    write_audit(
        conn, actor["id"], actor["role"], "UNIT_QUOTA_SETTINGS_UPDATED", "unit_quota_account", unit_id,
        before_json=json.dumps({"enabled": bool(before["quota_enabled"]), "default_monthly_quota_cents": int(before["default_monthly_quota_cents"])}, ensure_ascii=False),
        after_json=json.dumps({"enabled": bool(after["quota_enabled"]), "default_monthly_quota_cents": int(after["default_monthly_quota_cents"])}, ensure_ascii=False),
    )
    return quota_payload(conn, unit_id)


def adjust_quota(
    conn,
    *,
    unit_id: str,
    delta_cents: int,
    reason: str,
    actor: dict,
    expected_version: int | None = None,
):
    if not reason.strip():
        raise HTTPException(status_code=400, detail="请填写额度调整原因")
    account = ensure_quota_month(conn, unit_id)
    if not bool(account["quota_enabled"]):
        raise HTTPException(status_code=409, detail="该单位尚未启用额度控制")
    delta = int(delta_cents)
    if not delta:
        raise HTTPException(status_code=400, detail="调整金额不能为 0")
    if delta < 0:
        sql = "UPDATE unit_quota_accounts SET balance_cents = balance_cents + ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ? AND balance_cents >= ?"
        params: tuple = (delta, unit_id, -delta)
        if expected_version is not None:
            sql += " AND version = ?"
            params += (expected_version,)
        cursor = conn.execute(sql, params)
        if cursor.rowcount != 1:
            current = _account(conn, unit_id)
            if expected_version is not None and int(current["version"]) != expected_version:
                _stale_write(current)
            raise HTTPException(status_code=409, detail="减少额度不能使当前可用余额小于 0")
    else:
        sql = "UPDATE unit_quota_accounts SET balance_cents = balance_cents + ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ?"
        params = (delta, unit_id)
        if expected_version is not None:
            sql += " AND version = ?"
            params += (expected_version,)
        if conn.execute(sql, params).rowcount != 1:
            _stale_write(_account(conn, unit_id))
    account = _account(conn, unit_id)
    month = quota_month_now()
    conn.execute(
        "UPDATE unit_quota_months SET manual_adjustment_cents = manual_adjustment_cents + ?, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ? AND quota_month = ?",
        (delta, unit_id, month),
    )
    _ledger(
        conn, unit_id=unit_id, quota_month=month, event_type="MANUAL_INCREASE" if delta > 0 else "MANUAL_DECREASE",
        delta_cents=delta, balance_after_cents=int(account["balance_cents"]), actor_id=actor["id"], note=reason,
    )
    write_audit(conn, actor["id"], actor["role"], "UNIT_QUOTA_ADJUSTED", "unit_quota_account", unit_id, after_json=json.dumps({"delta_cents": delta, "reason": reason.strip()[:300], "balance_cents": int(account["balance_cents"])}, ensure_ascii=False))
    return quota_payload(conn, unit_id)


def quota_ledger(conn, unit_id: str, month: str | None = None) -> list[dict]:
    params: list[str] = [unit_id]
    sql = """
      SELECT ledger.*, orders.order_no, users.display_name AS actor_name
      FROM unit_quota_ledger ledger
      LEFT JOIN orders ON orders.id = ledger.order_id
      LEFT JOIN users ON users.id = ledger.actor_id
      WHERE ledger.unit_id = ?
    """
    if month:
        sql += " AND ledger.quota_month = ?"
        params.append(_valid_month(month))
    rows = all_rows(conn, sql + " ORDER BY ledger.created_at DESC, ledger.rowid DESC", params)
    for row in rows:
        row["display_created_at"] = display_local_time(row.get("created_at"))
    return rows
