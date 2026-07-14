from __future__ import annotations

from datetime import date, datetime, time, timedelta, timezone
from decimal import Decimal, InvalidOperation
from zoneinfo import ZoneInfo

from ..database import all_rows, decimal_text, one
from ..routers.system import backup_summary, safe_storage_status


SHANGHAI = ZoneInfo("Asia/Shanghai")
UTC = timezone.utc
VALID_ORDER_STATUSES = ("pending", "accepted", "preparing", "shipped", "completed")


def parse_business_date(value: str | None) -> date:
    if not value:
        return datetime.now(SHANGHAI).date()
    return date.fromisoformat(value)


def utc_bounds(day: date) -> tuple[str, str]:
    start = datetime.combine(day, time.min, tzinfo=SHANGHAI).astimezone(UTC)
    end = start + timedelta(days=1)
    return sql_time(start), sql_time(end)


def sql_time(value: datetime) -> str:
    return value.strftime("%Y-%m-%d %H:%M:%S")


def decimal_number(value) -> float:
    try:
        return float(Decimal(str(value or "0")))
    except (InvalidOperation, ValueError):
        return 0.0


def percent_change(today: int, yesterday: int) -> float | None:
    if yesterday == 0:
        return None if today == 0 else 100.0
    return round(((today - yesterday) / yesterday) * 100, 1)


def wait_seconds(now_utc: datetime, value: str | None) -> int:
    if not value:
        return 0
    try:
        created = datetime.strptime(value, "%Y-%m-%d %H:%M:%S").replace(tzinfo=UTC)
    except ValueError:
        return 0
    return max(0, int((now_utc - created).total_seconds()))


def count_status(conn, statuses: tuple[str, ...]) -> int:
    placeholders = ",".join("?" for _ in statuses)
    return int(one(conn, f"SELECT COUNT(*) AS c FROM orders WHERE status IN ({placeholders})", statuses)["c"])


def oldest_timestamp(conn, statuses: tuple[str, ...], field: str = "created_at") -> str | None:
    placeholders = ",".join("?" for _ in statuses)
    row = one(conn, f"SELECT MIN(COALESCE({field}, created_at)) AS oldest FROM orders WHERE status IN ({placeholders})", statuses)
    return row["oldest"] if row else None


def today_totals(conn, start_utc: str, end_utc: str) -> dict:
    return one(
        conn,
        """
        SELECT COUNT(*) AS order_count, COALESCE(SUM(total_cents), 0) AS amount_cents
        FROM orders
        WHERE created_at >= ? AND created_at < ? AND status != 'cancelled'
        """,
        (start_utc, end_utc),
    )


def trend_rows(conn, business_day: date, range_days: int) -> list[dict]:
    range_days = min(max(range_days, 7), 30)
    start_day = business_day - timedelta(days=range_days - 1)
    start_utc, _ = utc_bounds(start_day)
    _, end_utc = utc_bounds(business_day)
    rows = all_rows(
        conn,
        """
        SELECT date(datetime(created_at, '+8 hours')) AS local_date,
               COUNT(*) AS order_count,
               COALESCE(SUM(total_cents), 0) AS amount_cents
        FROM orders
        WHERE created_at >= ? AND created_at < ? AND status != 'cancelled'
        GROUP BY local_date
        ORDER BY local_date
        """,
        (start_utc, end_utc),
    )
    by_day = {row["local_date"]: row for row in rows}
    result = []
    for offset in range(range_days):
        day = start_day + timedelta(days=offset)
        key = day.isoformat()
        row = by_day.get(key, {})
        result.append({"date": key, "order_count": int(row.get("order_count") or 0), "amount_cents": int(row.get("amount_cents") or 0)})
    return result


def recent_orders(conn) -> list[dict]:
    return all_rows(
        conn,
        """
        SELECT o.id, o.order_no, o.unit_id, o.unit_name_snapshot, o.created_at, o.status, o.total_cents,
               COUNT(DISTINCT oi.id) AS item_count,
               COUNT(DISTINCT CASE WHEN ri.status = 'open' THEN ri.id END) AS open_issue_count
        FROM orders o
        LEFT JOIN order_items oi ON oi.order_id = o.id
        LEFT JOIN receipt_issues ri ON ri.order_id = o.id
        GROUP BY o.id
        ORDER BY o.created_at DESC
        LIMIT 10
        """,
    )


def inventory_alerts(conn) -> list[dict]:
    rows = all_rows(
        conn,
        """
        SELECT id, name, unit, stock_quantity, reserved_quantity, warning_quantity, supply_status, active,
               CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) AS available_quantity
        FROM products
        WHERE is_deleted = 0
          AND (
            supply_status = 'tight'
            OR supply_status = 'paused'
            OR CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL)
          )
        ORDER BY
          CASE WHEN CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= 0 THEN 0
               WHEN CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL) THEN 1
               WHEN supply_status = 'paused' THEN 2
               ELSE 3 END,
          available_quantity ASC
        LIMIT 8
        """,
    )
    for row in rows:
        row["available_quantity"] = decimal_text(row["available_quantity"] or 0)
    return rows


def demand_rank(conn, start_utc: str, end_utc: str) -> list[dict]:
    return all_rows(
        conn,
        """
        SELECT oi.product_id,
               oi.product_name_snapshot AS name,
               oi.unit_snapshot AS unit,
               SUM(CAST(COALESCE(NULLIF(oi.actual_quantity, ''), oi.quantity) AS REAL)) AS quantity,
               COUNT(DISTINCT o.unit_id) AS unit_count,
               COUNT(DISTINCT o.id) AS order_count
        FROM order_items oi
        JOIN orders o ON o.id = oi.order_id
        WHERE o.created_at >= ? AND o.created_at < ? AND o.status != 'cancelled'
        GROUP BY oi.product_id, oi.product_name_snapshot, oi.unit_snapshot
        ORDER BY quantity DESC
        LIMIT 8
        """,
        (start_utc, end_utc),
    )


def unit_rank(conn, start_utc: str, end_utc: str, sort_by: str) -> list[dict]:
    order_by = "total_cents DESC" if sort_by != "orders" else "order_count DESC"
    return all_rows(
        conn,
        f"""
        SELECT o.unit_id,
               o.unit_name_snapshot AS unit_name,
               COUNT(DISTINCT o.id) AS order_count,
               COALESCE(SUM(o.total_cents), 0) AS total_cents,
               COUNT(DISTINCT oi.product_id) AS product_kinds,
               COUNT(DISTINCT CASE WHEN ri.status = 'open' THEN ri.id END) AS open_issue_count
        FROM orders o
        LEFT JOIN order_items oi ON oi.order_id = o.id
        LEFT JOIN receipt_issues ri ON ri.order_id = o.id
        WHERE o.created_at >= ? AND o.created_at < ? AND o.status != 'cancelled'
        GROUP BY o.unit_id, o.unit_name_snapshot
        ORDER BY {order_by}
        LIMIT 8
        """,
        (start_utc, end_utc),
    )


def task_payload(conn, now_utc: datetime, tight_count: int, open_issues: int) -> list[dict]:
    pending = count_status(conn, ("pending",))
    preparing = count_status(conn, ("accepted", "preparing"))
    waiting_ship = count_status(conn, ("preparing",))
    tasks = [
        {
            "type": "receipt_issues",
            "name": "收货异常",
            "count": open_issues,
            "unit_label": "项",
            "risk": "有待处理收货异常" if open_issues else "暂无待处理异常",
            "priority": "urgent" if open_issues else "normal",
            "action_label": "查看异常",
            "target_url": "/admin/receipt-issues?status=open",
        },
        {
            "type": "pending_orders",
            "name": "待接单订单",
            "count": pending,
            "unit_label": "笔",
            "oldest_wait_seconds": wait_seconds(now_utc, oldest_timestamp(conn, ("pending",))),
            "priority": "high" if pending else "normal",
            "action_label": "立即处理",
            "target_url": "/admin/orders?status=pending",
        },
        {
            "type": "waiting_shipment",
            "name": "等待发货",
            "count": waiting_ship,
            "unit_label": "笔",
            "oldest_wait_seconds": wait_seconds(now_utc, oldest_timestamp(conn, ("preparing",), "preparing_at")),
            "priority": "high" if waiting_ship else "normal",
            "action_label": "确认发货",
            "target_url": "/admin/orders?status=preparing",
        },
        {
            "type": "stock_alerts",
            "name": "库存预警",
            "count": tight_count,
            "unit_label": "种",
            "risk": "请检查库存和供应状态" if tight_count else "库存状态正常",
            "priority": "warning" if tight_count else "normal",
            "action_label": "查看库存",
            "target_url": "/admin/products?status=tight",
        },
        {
            "type": "preparing_orders",
            "name": "备货中订单",
            "count": preparing,
            "unit_label": "笔",
            "oldest_wait_seconds": wait_seconds(now_utc, oldest_timestamp(conn, ("accepted", "preparing"), "accepted_at")),
            "priority": "normal",
            "action_label": "查看备货",
            "target_url": "/admin/preparation-summary",
        },
    ]
    return tasks


def dashboard_overview(conn, business_date: str | None, range_days: int, unit_sort: str) -> dict:
    business_day = parse_business_date(business_date)
    start_utc, end_utc = utc_bounds(business_day)
    y_start_utc, y_end_utc = utc_bounds(business_day - timedelta(days=1))
    now_local = datetime.now(SHANGHAI)
    now_utc = now_local.astimezone(UTC)
    today = today_totals(conn, start_utc, end_utc)
    yesterday = today_totals(conn, y_start_utc, y_end_utc)
    pending = count_status(conn, ("pending",))
    preparing = count_status(conn, ("accepted", "preparing"))
    waiting_ship = count_status(conn, ("preparing",))
    shipped = count_status(conn, ("shipped",))
    open_issues = int(one(conn, "SELECT COUNT(*) AS c FROM receipt_issues WHERE status = 'open'")["c"])
    tight_inventory = int(
        one(
            conn,
            """
            SELECT COUNT(*) AS c
            FROM products
            WHERE is_deleted = 0
              AND (
                supply_status = 'tight'
                OR supply_status = 'paused'
                OR CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL)
              )
            """,
        )["c"]
    )
    storage = safe_storage_status()
    backup = backup_summary(conn)
    return {
        "business_date": business_day.isoformat(),
        "server_now": now_local.isoformat(timespec="seconds"),
        "refreshed_at": now_local.isoformat(timespec="seconds"),
        "metrics": {
            "today_valid_orders": int(today["order_count"]),
            "today_total_cents": int(today["amount_cents"]),
            "pending": pending,
            "preparing": preparing,
            "waiting_shipment": waiting_ship,
            "waiting_receipt": shipped,
            "open_receipt_issues": open_issues,
            "tight_inventory": tight_inventory,
        },
        "comparisons": {
            "orders_vs_yesterday_percent": percent_change(int(today["order_count"]), int(yesterday["order_count"])),
            "amount_vs_yesterday_percent": percent_change(int(today["amount_cents"]), int(yesterday["amount_cents"])),
        },
        "tasks": task_payload(conn, now_utc, tight_inventory, open_issues),
        "trend": trend_rows(conn, business_day, range_days),
        "recent_orders": recent_orders(conn),
        "inventory_alerts": inventory_alerts(conn),
        "demand_rank": demand_rank(conn, start_utc, end_utc),
        "unit_rank": unit_rank(conn, start_utc, end_utc, unit_sort),
        "system_status": {
            "service": "ok",
            "last_backup_at": (backup.get("latest") or {}).get("finished_at") or "",
            "backup_status": backup.get("status", "missing"),
            "disk_usage_percent": storage["disk_used_percent"],
            "version": "Web 1.1.0",
            "last_data_sync": now_local.strftime("%Y-%m-%d %H:%M:%S"),
        },
    }
