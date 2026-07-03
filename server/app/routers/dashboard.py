import os
import shutil
from datetime import date, datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query

from ..database import all_rows, connect, database_path, one
from ..dependencies import require_admin_user
from ..services.procurement import cutoff_payload

router = APIRouter(prefix="/admin", tags=["dashboard"])

SHANGHAI = ZoneInfo("Asia/Shanghai")
ACTUAL_EXPR = "COALESCE(NULLIF(order_items.actual_quantity, ''), order_items.quantity)"
VALID_ORDER_WHERE = "orders.status != 'cancelled'"


def now_shanghai() -> datetime:
    return datetime.now(SHANGHAI)


def parse_business_date(value: str | None) -> date:
    if value:
        return date.fromisoformat(value)
    return now_shanghai().date()


def utc_bounds(day: date) -> tuple[str, str]:
    start_local = datetime.combine(day, time.min, tzinfo=SHANGHAI)
    end_local = start_local + timedelta(days=1)
    return utc_text(start_local), utc_text(end_local)


def utc_text(value: datetime) -> str:
    return value.astimezone(timezone.utc).strftime("%Y-%m-%d %H:%M:%S")


def percent_change(current: int | float, previous: int | float) -> float:
    current_value = float(current or 0)
    previous_value = float(previous or 0)
    if previous_value == 0:
        return 0.0 if current_value == 0 else 100.0
    return round(((current_value - previous_value) / previous_value) * 100, 1)


def date_range(days: int, end_day: date) -> list[date]:
    return [end_day - timedelta(days=offset) for offset in range(days - 1, -1, -1)]


def date_filter(alias: str = "orders") -> str:
    return f"{alias}.created_at >= ? AND {alias}.created_at < ?"


def stats_for_day(conn, day: date) -> dict:
    start_utc, end_utc = utc_bounds(day)
    return one(
        conn,
        f"""
        SELECT COUNT(*) AS order_count, COALESCE(SUM(total_cents), 0) AS amount_cents
        FROM orders
        WHERE {date_filter()} AND {VALID_ORDER_WHERE}
        """,
        (start_utc, end_utc),
    )


def count_orders(conn, where: str, params=()) -> int:
    return int(one(conn, f"SELECT COUNT(*) AS c FROM orders WHERE {where}", params)["c"])


def oldest_wait_seconds(row: dict | None, now_utc: datetime) -> int | None:
    if not row or not row.get("created_at"):
        return None
    try:
        created = datetime.strptime(row["created_at"].split(".")[0], "%Y-%m-%d %H:%M:%S").replace(tzinfo=timezone.utc)
    except ValueError:
        return None
    return max(0, int((now_utc - created).total_seconds()))


def metric_tasks(conn, metrics: dict, business_day: date, now_utc: datetime) -> list[dict]:
    earliest_pending = one(conn, "SELECT created_at FROM orders WHERE status = 'pending' ORDER BY created_at LIMIT 1")
    first_alert = one(
        conn,
        """
        SELECT name
        FROM products
        WHERE is_deleted = 0
          AND (supply_status = 'tight'
               OR CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL))
        ORDER BY
          CASE
            WHEN CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= 0 THEN 0
            WHEN CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL) THEN 1
            WHEN supply_status = 'tight' THEN 2
            ELSE 3
          END,
          name
        LIMIT 1
        """,
    )
    cutoff = cutoff_payload(conn)
    tasks = [
        {
            "type": "receipt_issues",
            "title": "收货异常",
            "count": metrics["open_receipt_issues"],
            "priority": "critical" if metrics["open_receipt_issues"] else "normal",
            "description": "有异常需要尽快处理" if metrics["open_receipt_issues"] else "暂无未处理异常",
            "action_label": "查看异常",
            "target_url": "/admin/receipt-issues?status=open",
        },
        {
            "type": "pending_orders",
            "title": "待接单订单",
            "count": metrics["pending"],
            "oldest_wait_seconds": oldest_wait_seconds(earliest_pending, now_utc),
            "priority": "high" if metrics["pending"] else "normal",
            "description": "最早订单等待较久" if metrics["pending"] else "暂无待接单订单",
            "action_label": "立即处理",
            "target_url": "/admin/orders?status=pending",
        },
        {
            "type": "waiting_shipment",
            "title": "等待发货",
            "count": metrics["waiting_shipment"],
            "priority": "high" if metrics["waiting_shipment"] else "normal",
            "description": "备货完成后请确认发货" if metrics["waiting_shipment"] else "暂无待发货订单",
            "action_label": "确认发货",
            "target_url": "/admin/orders?status=preparing",
        },
        {
            "type": "tight_inventory",
            "title": "库存预警",
            "count": metrics["tight_inventory"],
            "priority": "warning" if metrics["tight_inventory"] else "normal",
            "description": f"{first_alert['name']}库存需要关注" if first_alert else "暂无库存预警",
            "action_label": "查看库存",
            "target_url": "/admin/products?status=tight",
        },
        {
            "type": "cutoff",
            "title": "今日截止时间",
            "count": 1 if cutoff.get("enabled") else 0,
            "priority": "normal",
            "description": f"{business_day.isoformat()} {cutoff.get('cutoff_time', '16:00')}",
            "action_label": "调整时间",
            "target_url": "/admin/settings",
        },
    ]
    priority_order = {"critical": 0, "high": 1, "warning": 2, "normal": 3}
    return sorted(tasks, key=lambda item: (priority_order[item["priority"]], -int(item.get("count") or 0)))


def trend_rows(conn, end_day: date, range_days: int) -> list[dict]:
    days = date_range(range_days, end_day)
    start_utc, _ = utc_bounds(days[0])
    _, end_utc = utc_bounds(end_day)
    rows = all_rows(
        conn,
        f"""
        SELECT date(datetime(created_at, '+8 hours')) AS business_date,
               COUNT(*) AS order_count,
               COALESCE(SUM(total_cents), 0) AS amount_cents
        FROM orders
        WHERE created_at >= ? AND created_at < ? AND status != 'cancelled'
        GROUP BY business_date
        """,
        (start_utc, end_utc),
    )
    by_date = {row["business_date"]: row for row in rows}
    return [
        {
            "date": day.isoformat(),
            "order_count": int(by_date.get(day.isoformat(), {}).get("order_count", 0)),
            "amount_cents": int(by_date.get(day.isoformat(), {}).get("amount_cents", 0)),
        }
        for day in days
    ]


def recent_orders(conn) -> list[dict]:
    return all_rows(
        conn,
        """
        SELECT orders.id, orders.order_no, orders.unit_id, orders.unit_name_snapshot,
               orders.delivery_point_snapshot, orders.status, orders.total_cents, orders.created_at,
               (SELECT COUNT(*) FROM order_items WHERE order_items.order_id = orders.id) AS item_count,
               (SELECT COUNT(*) FROM receipt_issues WHERE receipt_issues.order_id = orders.id AND receipt_issues.status = 'open') AS open_receipt_issue_count
        FROM orders
        ORDER BY orders.created_at DESC
        LIMIT 10
        """,
    )


def inventory_alerts(conn) -> list[dict]:
    return all_rows(
        conn,
        """
        SELECT id, name, unit, stock_quantity, reserved_quantity,
               CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) AS available_quantity,
               warning_quantity, supply_status, active
        FROM products
        WHERE is_deleted = 0
          AND (supply_status IN ('tight', 'paused')
               OR CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL))
        ORDER BY
          CASE
            WHEN CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= 0 THEN 0
            WHEN CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL) THEN 1
            WHEN supply_status = 'paused' THEN 2
            WHEN supply_status = 'tight' THEN 3
            ELSE 4
          END,
          available_quantity ASC,
          name
        LIMIT 8
        """,
    )


def demand_rank(conn, day: date) -> list[dict]:
    start_utc, end_utc = utc_bounds(day)
    return all_rows(
        conn,
        f"""
        SELECT order_items.product_id,
               order_items.product_name_snapshot AS name,
               order_items.unit_snapshot AS unit,
               SUM(CAST({ACTUAL_EXPR} AS REAL)) AS quantity,
               COUNT(DISTINCT orders.unit_id) AS unit_count,
               COUNT(DISTINCT orders.id) AS order_count
        FROM order_items
        JOIN orders ON orders.id = order_items.order_id
        WHERE {date_filter()} AND {VALID_ORDER_WHERE}
        GROUP BY order_items.product_id, order_items.product_name_snapshot, order_items.unit_snapshot
        ORDER BY quantity DESC, order_count DESC, name
        LIMIT 8
        """,
        (start_utc, end_utc),
    )


def unit_rank(conn, day: date) -> list[dict]:
    start_utc, end_utc = utc_bounds(day)
    return all_rows(
        conn,
        f"""
        SELECT orders.unit_id,
               orders.unit_name_snapshot AS unit_name,
               COUNT(*) AS order_count,
               COALESCE(SUM(orders.total_cents), 0) AS amount_cents,
               (
                 SELECT COUNT(DISTINCT order_items.product_id)
                 FROM order_items
                 JOIN orders AS scoped_orders ON scoped_orders.id = order_items.order_id
                 WHERE scoped_orders.unit_id = orders.unit_id
                   AND scoped_orders.created_at >= ?
                   AND scoped_orders.created_at < ?
                   AND scoped_orders.status != 'cancelled'
               ) AS product_count,
               (
                 SELECT COUNT(*)
                 FROM receipt_issues
                 JOIN orders AS issue_orders ON issue_orders.id = receipt_issues.order_id
                 WHERE issue_orders.unit_id = orders.unit_id
                   AND issue_orders.created_at >= ?
                   AND issue_orders.created_at < ?
                   AND issue_orders.status != 'cancelled'
               ) AS issue_count
        FROM orders
        WHERE {date_filter()} AND {VALID_ORDER_WHERE}
        GROUP BY orders.unit_id, orders.unit_name_snapshot
        ORDER BY amount_cents DESC, order_count DESC, unit_name
        LIMIT 8
        """,
        (start_utc, end_utc, start_utc, end_utc, start_utc, end_utc),
    )


def system_status(refreshed_at: str) -> dict:
    db_path = Path(database_path())
    disk_path = db_path.parent if db_path.parent.exists() else Path.cwd()
    usage = shutil.disk_usage(disk_path)
    backup_dir = Path(os.getenv("BACKUP_DIR", str(db_path.parent.parent / "backups")))
    backups = []
    if backup_dir.exists():
        backups = [path for path in backup_dir.iterdir() if path.is_file() and path.name.startswith("smart_procurement_")]
    last_backup_at = ""
    if backups:
        last_backup_at = datetime.fromtimestamp(max(path.stat().st_mtime for path in backups), tz=SHANGHAI).isoformat()
    return {
        "service": "ok",
        "last_backup_at": last_backup_at,
        "disk_usage_percent": round((usage.used / usage.total) * 100, 1),
        "version": os.getenv("WEB_VERSION", "Web 1.1.0"),
        "refreshed_at": refreshed_at,
    }


def dashboard_overview_payload(conn, business_day: date, range_days: int) -> dict:
    range_days = max(7, min(range_days, 30))
    start_utc, end_utc = utc_bounds(business_day)
    today = stats_for_day(conn, business_day)
    yesterday = stats_for_day(conn, business_day - timedelta(days=1))
    pending = count_orders(conn, "status = 'pending'")
    preparing = count_orders(conn, "status IN ('accepted', 'preparing')")
    waiting_shipment = count_orders(conn, "status = 'preparing'")
    waiting_receipt = count_orders(conn, "status = 'shipped'")
    open_issues = int(one(conn, "SELECT COUNT(*) AS c FROM receipt_issues WHERE status = 'open'")["c"])
    tight = int(
        one(
            conn,
            """
            SELECT COUNT(*) AS c
            FROM products
            WHERE is_deleted = 0
              AND (supply_status = 'tight'
                   OR CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL))
            """,
        )["c"]
    )
    metrics = {
        "today_valid_orders": int(today["order_count"]),
        "today_total_cents": int(today["amount_cents"]),
        "pending": pending,
        "preparing": preparing,
        "waiting_shipment": waiting_shipment,
        "waiting_receipt": waiting_receipt,
        "open_receipt_issues": open_issues,
        "tight_inventory": tight,
    }
    refreshed_at = now_shanghai().isoformat()
    now_utc = datetime.now(timezone.utc)
    return {
        "business_date": business_day.isoformat(),
        "server_now": refreshed_at,
        "refreshed_at": refreshed_at,
        "date_range_utc": {"start": start_utc, "end": end_utc},
        "metrics": metrics,
        "comparisons": {
            "orders_vs_yesterday_percent": percent_change(today["order_count"], yesterday["order_count"]),
            "amount_vs_yesterday_percent": percent_change(today["amount_cents"], yesterday["amount_cents"]),
        },
        "tasks": metric_tasks(conn, metrics, business_day, now_utc),
        "trend": trend_rows(conn, business_day, range_days),
        "recent_orders": recent_orders(conn),
        "inventory_alerts": inventory_alerts(conn),
        "demand_rank": demand_rank(conn, business_day),
        "unit_rank": unit_rank(conn, business_day),
        "system_status": system_status(refreshed_at),
    }


@router.get("/dashboard/overview")
def dashboard_overview(
    business_date: str | None = None,
    range_days: int = Query(default=7, ge=7, le=30),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        return dashboard_overview_payload(conn, parse_business_date(business_date), range_days)


@router.get("/dashboard")
def dashboard(admin=Depends(require_admin_user)):
    with connect() as conn:
        overview = dashboard_overview_payload(conn, parse_business_date(None), 7)
    return {
        "today_orders": overview["metrics"]["today_valid_orders"],
        "today_total_cents": overview["metrics"]["today_total_cents"],
        "pending": overview["metrics"]["pending"],
        "preparing": overview["metrics"]["preparing"],
        "shipped": overview["metrics"]["waiting_receipt"],
        "tight_inventory": overview["metrics"]["tight_inventory"],
        "open_receipt_issues": overview["metrics"]["open_receipt_issues"],
        "recent_orders": overview["recent_orders"][:5],
        "demand_rank": overview["demand_rank"][:5],
    }


@router.get("/order-summary")
def order_summary(admin=Depends(require_admin_user)):
    with connect() as conn:
        return all_rows(
            conn,
            f"""
            SELECT product_id, product_name_snapshot AS name, unit_snapshot AS unit,
                   SUM(CAST({ACTUAL_EXPR} AS REAL)) AS quantity
            FROM order_items
            JOIN orders ON orders.id = order_items.order_id
            WHERE orders.status NOT IN ('cancelled')
            GROUP BY product_id, product_name_snapshot, unit_snapshot
            ORDER BY quantity DESC
            """,
        )
