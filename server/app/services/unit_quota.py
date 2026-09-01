"""Transactional monthly procurement quota accounts for units.

The account balance is the authoritative, carried-forward value.  Monthly
history and the ledger are audit records; they are not recomputed from orders.
"""

from __future__ import annotations

import hashlib
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


def _request_hash(payload: dict) -> str:
    return hashlib.sha256(
        json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    ).hexdigest()


def _already_applied(
    conn,
    *,
    actor: dict,
    action: str,
    unit_id: str,
    client_request_id: str | None,
    request_hash: str,
) -> bool:
    """Use the existing audit request-id column for safe retry handling."""
    request_id = (client_request_id or "").strip()
    if not request_id:
        return False
    previous = one(
        conn,
        """
        SELECT after_json FROM audit_logs
        WHERE actor_id = ? AND action = ? AND object_type = 'unit_quota_account'
          AND object_id = ? AND request_id = ? AND result = 'success'
        ORDER BY created_at, id
        LIMIT 1
        """,
        (actor["id"], action, unit_id, request_id),
    )
    if not previous:
        return False
    try:
        previous_hash = json.loads(previous.get("after_json") or "{}").get("request_hash")
    except json.JSONDecodeError:
        previous_hash = None
    if previous_hash != request_hash:
        raise HTTPException(status_code=409, detail="请求编号已被其他额度操作使用")
    return True


def _future_plan_rows(conn, unit_id: str, account: dict, current_month: str, count: int = 3) -> list[dict]:
    rows: list[dict] = []
    month = _month_after(current_month)
    for _ in range(count):
        row = one(
            conn,
            "SELECT * FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?",
            (unit_id, month),
        )
        # grant_cents == 0 means a future plan has not become a real account
        # month yet. A zero base means it follows the default at activation.
        if row and int(row["grant_cents"]) != 0:
            source, amount, editable = "active", int(row["base_quota_cents"]), False
        elif row and int(row["base_quota_cents"]) > 0:
            source, amount, editable = "explicit", int(row["base_quota_cents"]), True
        else:
            source, amount, editable = "default", int(account["default_monthly_quota_cents"]), True
        rows.append({"quota_month": month, "planned_quota_cents": amount, "source": source, "editable": editable})
        month = _month_after(month)
    return rows


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
            "SELECT * FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?",
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
        elif int(existing["grant_cents"]) == 0:
            # Materialize an explicit/default future plan exactly once when its
            # month becomes active; no future plan changes a real balance early.
            opening = int(account["balance_cents"])
            grant = int(existing["base_quota_cents"]) or int(account["default_monthly_quota_cents"])
            conn.execute(
                """
                UPDATE unit_quota_months
                SET base_quota_cents = ?, opening_balance_cents = ?, grant_cents = ?,
                    granted_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND grant_cents = 0
                """,
                (grant, opening, grant, existing["id"]),
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
    balance_adjustment = one(
        conn,
        "SELECT COALESCE(SUM(delta_cents), 0) AS amount FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type IN ('MANUAL_INCREASE', 'MANUAL_DECREASE')",
        (unit_id, month),
    )["amount"]
    used = one(
        conn,
        "SELECT COALESCE(-SUM(delta_cents), 0) AS amount FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type IN ('ORDER_RESERVE', 'ORDER_ADJUST', 'ORDER_RELEASE')",
        (unit_id, month),
    )["amount"]
    monthly_correction = one(
        conn,
        "SELECT COALESCE(SUM(delta_cents), 0) AS amount FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type = 'MONTHLY_QUOTA_CORRECTION'",
        (unit_id, month),
    )["amount"]
    return {
        "enabled": bool(account["quota_enabled"]),
        "quota_month": month,
        "default_monthly_quota_cents": int(account["default_monthly_quota_cents"]),
        "base_quota_cents": int(row["base_quota_cents"]) if row else 0,
        "opening_balance_cents": int(row["opening_balance_cents"]) if row else int(account["balance_cents"]),
        # Keep the old field for clients that only understand balance changes.
        "adjustment_cents": int(balance_adjustment or 0),
        "balance_adjustment_cents": int(balance_adjustment or 0),
        "monthly_correction_cents": int(monthly_correction or 0),
        "effective_quota_cents": int(row["base_quota_cents"]) + int(monthly_correction or 0) if row else 0,
        "used_this_month_cents": int(used or 0),
        "available_cents": int(account["balance_cents"]),
        "last_granted_month": account.get("last_granted_month") or "",
        "version": int(account["version"]),
        "updated_at": account.get("updated_at") or "",
        # Keep the UTC database value intact while giving clients an explicit
        # user-facing Shanghai-time field.
        "display_updated_at": display_local_time(account.get("updated_at")),
        "future_months": _future_plan_rows(conn, unit_id, account, month),
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
    client_request_id: str | None = None,
):
    if enabled and default_monthly_quota_cents <= 0:
        raise HTTPException(status_code=400, detail="启用额度控制时必须设置大于 0 的月度基础额度")
    request_hash = _request_hash({"enabled": bool(enabled), "default_monthly_quota_cents": int(default_monthly_quota_cents)})
    if _already_applied(conn, actor=actor, action="UNIT_QUOTA_SETTINGS_UPDATED", unit_id=unit_id, client_request_id=client_request_id, request_hash=request_hash):
        return quota_payload(conn, unit_id)
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
        after_json=json.dumps({"enabled": bool(after["quota_enabled"]), "default_monthly_quota_cents": int(after["default_monthly_quota_cents"]), "request_hash": request_hash}, ensure_ascii=False),
        request_id=(client_request_id or "").strip(),
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
    client_request_id: str | None = None,
):
    if not reason.strip():
        raise HTTPException(status_code=400, detail="请填写额度调整原因")
    request_hash = _request_hash({"delta_cents": int(delta_cents), "reason": reason.strip()})
    if _already_applied(conn, actor=actor, action="UNIT_QUOTA_BALANCE_ADJUSTED", unit_id=unit_id, client_request_id=client_request_id, request_hash=request_hash):
        return quota_payload(conn, unit_id)
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
        request_id=(client_request_id or "").strip(),
    )
    write_audit(conn, actor["id"], actor["role"], "UNIT_QUOTA_BALANCE_ADJUSTED", "unit_quota_account", unit_id, after_json=json.dumps({"delta_cents": delta, "reason": reason.strip()[:300], "balance_cents": int(account["balance_cents"]), "request_hash": request_hash}, ensure_ascii=False), request_id=(client_request_id or "").strip())
    return quota_payload(conn, unit_id)


def correct_current_month_quota(
    conn,
    *,
    unit_id: str,
    effective_quota_cents: int,
    reason: str,
    actor: dict,
    expected_version: int | None = None,
    client_request_id: str | None = None,
):
    if not reason.strip():
        raise HTTPException(status_code=400, detail="请填写本月额度修正原因")
    request_hash = _request_hash({"effective_quota_cents": int(effective_quota_cents), "reason": reason.strip()})
    if _already_applied(conn, actor=actor, action="UNIT_QUOTA_MONTH_CORRECTED", unit_id=unit_id, client_request_id=client_request_id, request_hash=request_hash):
        return quota_payload(conn, unit_id)
    account = ensure_quota_month(conn, unit_id)
    if not bool(account["quota_enabled"]):
        raise HTTPException(status_code=409, detail="该单位尚未启用额度控制")
    month = quota_month_now()
    row = one(conn, "SELECT * FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?", (unit_id, month))
    if not row:
        raise HTTPException(status_code=409, detail="本月额度账户尚未初始化")
    correction = one(
        conn,
        "SELECT COALESCE(SUM(delta_cents), 0) AS amount FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type = 'MONTHLY_QUOTA_CORRECTION'",
        (unit_id, month),
    )["amount"]
    previous_effective = int(row["base_quota_cents"]) + int(correction or 0)
    delta = int(effective_quota_cents) - previous_effective
    if delta == 0:
        raise HTTPException(status_code=400, detail="本月有效额度未发生变化")
    sql = "UPDATE unit_quota_accounts SET balance_cents = balance_cents + ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ? AND balance_cents + ? >= 0"
    params: tuple = (delta, unit_id, delta)
    if expected_version is not None:
        sql += " AND version = ?"
        params += (expected_version,)
    if conn.execute(sql, params).rowcount != 1:
        current = _account(conn, unit_id)
        if expected_version is not None and int(current["version"]) != expected_version:
            _stale_write(current)
        raise HTTPException(status_code=409, detail="本次修正会导致当前可用额度为负，无法保存。")
    account = _account(conn, unit_id)
    _ledger(
        conn,
        unit_id=unit_id,
        quota_month=month,
        event_type="MONTHLY_QUOTA_CORRECTION",
        delta_cents=delta,
        balance_after_cents=int(account["balance_cents"]),
        actor_id=actor["id"],
        note=reason,
        request_id=(client_request_id or "").strip(),
    )
    write_audit(
        conn, actor["id"], actor["role"], "UNIT_QUOTA_MONTH_CORRECTED", "unit_quota_account", unit_id,
        after_json=json.dumps({"quota_month": month, "previous_effective_quota_cents": previous_effective, "effective_quota_cents": int(effective_quota_cents), "delta_cents": delta, "reason": reason.strip()[:300], "request_hash": request_hash}, ensure_ascii=False),
        request_id=(client_request_id or "").strip(),
    )
    return quota_payload(conn, unit_id)


def _future_plan_row_is_locked(conn, unit_id: str, quota_month: str, row: dict | None) -> bool:
    if row and int(row["grant_cents"]) != 0:
        return True
    return bool(one(conn, "SELECT id FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? LIMIT 1", (unit_id, quota_month)))


def set_future_quota_plan(
    conn,
    *,
    unit_id: str,
    quota_month: str,
    planned_quota_cents: int,
    actor: dict,
    expected_version: int | None = None,
    client_request_id: str | None = None,
):
    month = _valid_month(quota_month)
    if month <= quota_month_now():
        raise HTTPException(status_code=409, detail="仅可设置未来月份计划额度")
    if int(planned_quota_cents) <= 0:
        raise HTTPException(status_code=400, detail="未来计划额度必须大于 0")
    request_hash = _request_hash({"quota_month": month, "planned_quota_cents": int(planned_quota_cents)})
    if _already_applied(conn, actor=actor, action="UNIT_QUOTA_FUTURE_PLAN_SET", unit_id=unit_id, client_request_id=client_request_id, request_hash=request_hash):
        return quota_payload(conn, unit_id)
    account = _account(conn, unit_id)
    if not bool(account["quota_enabled"]):
        raise HTTPException(status_code=409, detail="该单位尚未启用额度控制")
    row = one(conn, "SELECT * FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?", (unit_id, month))
    if _future_plan_row_is_locked(conn, unit_id, month, row):
        raise HTTPException(status_code=409, detail="该未来月份已形成实际额度记录，不能按计划直接修改")
    sql = "UPDATE unit_quota_accounts SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ?"
    params: tuple = (unit_id,)
    if expected_version is not None:
        sql += " AND version = ?"
        params += (expected_version,)
    if conn.execute(sql, params).rowcount != 1:
        _stale_write(_account(conn, unit_id))
    if row:
        conn.execute("UPDATE unit_quota_months SET base_quota_cents = ?, opening_balance_cents = 0, grant_cents = 0, manual_adjustment_cents = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(planned_quota_cents), row["id"]))
    else:
        conn.execute("INSERT INTO unit_quota_months(id, unit_id, quota_month, base_quota_cents, opening_balance_cents, grant_cents) VALUES (?, ?, ?, ?, 0, 0)", (str(uuid4()), unit_id, month, int(planned_quota_cents)))
    write_audit(conn, actor["id"], actor["role"], "UNIT_QUOTA_FUTURE_PLAN_SET", "unit_quota_account", unit_id, after_json=json.dumps({"quota_month": month, "planned_quota_cents": int(planned_quota_cents), "request_hash": request_hash}, ensure_ascii=False), request_id=(client_request_id or "").strip())
    return quota_payload(conn, unit_id)


def restore_future_quota_default(
    conn,
    *,
    unit_id: str,
    quota_month: str,
    actor: dict,
    expected_version: int | None = None,
    client_request_id: str | None = None,
):
    month = _valid_month(quota_month)
    if month <= quota_month_now():
        raise HTTPException(status_code=409, detail="仅可恢复未来月份默认额度")
    request_hash = _request_hash({"quota_month": month, "restore_default": True})
    if _already_applied(conn, actor=actor, action="UNIT_QUOTA_FUTURE_PLAN_RESTORED", unit_id=unit_id, client_request_id=client_request_id, request_hash=request_hash):
        return quota_payload(conn, unit_id)
    row = one(conn, "SELECT * FROM unit_quota_months WHERE unit_id = ? AND quota_month = ?", (unit_id, month))
    if not row:
        return quota_payload(conn, unit_id)
    if _future_plan_row_is_locked(conn, unit_id, month, row):
        raise HTTPException(status_code=409, detail="该未来月份已形成实际额度记录，不能恢复默认")
    sql = "UPDATE unit_quota_accounts SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE unit_id = ?"
    params: tuple = (unit_id,)
    if expected_version is not None:
        sql += " AND version = ?"
        params += (expected_version,)
    if conn.execute(sql, params).rowcount != 1:
        _stale_write(_account(conn, unit_id))
    # Retain the row rather than deleting a monthly-history record. base=0
    # explicitly means this future month follows the account default.
    conn.execute("UPDATE unit_quota_months SET base_quota_cents = 0, opening_balance_cents = 0, grant_cents = 0, manual_adjustment_cents = 0, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))
    write_audit(conn, actor["id"], actor["role"], "UNIT_QUOTA_FUTURE_PLAN_RESTORED", "unit_quota_account", unit_id, after_json=json.dumps({"quota_month": month, "source": "default", "request_hash": request_hash}, ensure_ascii=False), request_id=(client_request_id or "").strip())
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
