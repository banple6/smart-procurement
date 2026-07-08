from datetime import datetime
from zoneinfo import ZoneInfo

from fastapi import APIRouter, Depends, Query

from ..database import all_rows, connect, one
from ..dependencies import require_admin_user, require_web_admin_user
from ..services.dashboard_cache import get_dashboard_cached
from ..services.dashboard_overview import dashboard_overview, utc_bounds

router = APIRouter(prefix="/admin", tags=["dashboard"])


@router.get("/dashboard")
def dashboard(admin=Depends(require_admin_user)):
    business_day = datetime.now(ZoneInfo("Asia/Shanghai")).date()
    key = ("api-dashboard", business_day.isoformat(), 7, "amount")
    overview = get_dashboard_cached(
        key,
        lambda: _dashboard_overview_payload(business_day.isoformat(), 7, "amount"),
    )
    metrics = overview["metrics"]
    return {
        "today_orders": metrics["today_valid_orders"],
        "today_total_cents": metrics["today_total_cents"],
        "pending": metrics["pending"],
        "preparing": metrics["preparing"],
        "shipped": metrics["waiting_receipt"],
        "tight_inventory": metrics["tight_inventory"],
        "open_receipt_issues": metrics["open_receipt_issues"],
        "recent_orders": overview["recent_orders"][:5],
        "demand_rank": overview["demand_rank"][:5],
    }


@router.get("/dashboard/overview")
def dashboard_overview_api(
    business_date: str | None = None,
    range_days: int = Query(default=7, ge=7, le=30),
    unit_sort: str = Query(default="amount", pattern="^(amount|orders)$"),
    admin=Depends(require_web_admin_user),
):
    date_key = business_date or datetime.now(ZoneInfo("Asia/Shanghai")).date().isoformat()
    return get_dashboard_cached(
        ("web-dashboard-overview", date_key, range_days, unit_sort),
        lambda: _dashboard_overview_payload(business_date, range_days, unit_sort),
    )


def _dashboard_overview_payload(business_date: str | None, range_days: int, unit_sort: str):
    with connect() as conn:
        return dashboard_overview(conn, business_date, range_days, unit_sort)


@router.get("/order-summary")
def order_summary(admin=Depends(require_admin_user)):
    with connect() as conn:
        return all_rows(
            conn,
            """
            SELECT product_id, product_name_snapshot AS name, unit_snapshot AS unit, SUM(CAST(quantity AS REAL)) AS quantity
            FROM order_items
            JOIN orders ON orders.id = order_items.order_id
            WHERE orders.status NOT IN ('cancelled')
            GROUP BY product_id, product_name_snapshot, unit_snapshot
            ORDER BY quantity DESC
            """,
        )
