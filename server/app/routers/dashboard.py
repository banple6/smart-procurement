from fastapi import APIRouter, Depends

from ..database import all_rows, connect, one
from ..dependencies import require_admin_user

router = APIRouter(prefix="/admin", tags=["dashboard"])


@router.get("/dashboard")
def dashboard(admin=Depends(require_admin_user)):
    with connect() as conn:
        total = one(conn, "SELECT COUNT(*) AS c, COALESCE(SUM(total_cents), 0) AS amount FROM orders WHERE date(created_at) = date('now')")
        pending = one(conn, "SELECT COUNT(*) AS c FROM orders WHERE status = 'pending'")
        preparing = one(conn, "SELECT COUNT(*) AS c FROM orders WHERE status = 'preparing'")
        shipped = one(conn, "SELECT COUNT(*) AS c FROM orders WHERE status = 'shipped'")
        tight = one(conn, "SELECT COUNT(*) AS c FROM products WHERE supply_status = 'tight' OR CAST(stock_quantity AS REAL) - CAST(reserved_quantity AS REAL) <= CAST(warning_quantity AS REAL)")
        recent_orders = all_rows(
            conn,
            """
            SELECT id, order_no, unit_name_snapshot, delivery_point_snapshot, status, total_cents, created_at
            FROM orders
            ORDER BY created_at DESC
            LIMIT 5
            """,
        )
        demand_rank = all_rows(
            conn,
            """
            SELECT product_id, product_name_snapshot AS name, unit_snapshot AS unit,
                   SUM(CAST(COALESCE(NULLIF(actual_quantity, ''), quantity) AS REAL)) AS quantity
            FROM order_items
            JOIN orders ON orders.id = order_items.order_id
            WHERE orders.status NOT IN ('cancelled')
            GROUP BY product_id, product_name_snapshot, unit_snapshot
            ORDER BY quantity DESC
            LIMIT 5
            """,
        )
        open_issues = one(conn, "SELECT COUNT(*) AS c FROM receipt_issues WHERE status = 'open'")
    return {
        "today_orders": total["c"],
        "today_total_cents": total["amount"],
        "pending": pending["c"],
        "preparing": preparing["c"],
        "shipped": shipped["c"],
        "tight_inventory": tight["c"],
        "open_receipt_issues": open_issues["c"],
        "recent_orders": recent_orders,
        "demand_rank": demand_rank,
    }


@router.get("/order-summary")
def order_summary(admin=Depends(require_admin_user)):
    with connect() as conn:
        return all_rows(
            conn,
            """
            SELECT product_id, product_name_snapshot AS name, unit_snapshot AS unit,
                   SUM(CAST(COALESCE(NULLIF(actual_quantity, ''), quantity) AS REAL)) AS quantity
            FROM order_items
            JOIN orders ON orders.id = order_items.order_id
            WHERE orders.status NOT IN ('cancelled')
            GROUP BY product_id, product_name_snapshot, unit_snapshot
            ORDER BY quantity DESC
            """,
        )
