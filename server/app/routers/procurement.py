from fastapi import APIRouter, Depends, Query, Response

from ..database import all_rows, connect, one, transaction
from ..dependencies import current_user, require_admin_user
from ..schemas import CutoffOverridePut, CutoffPatch
from ..services.exports import delivery_sheets_workbook, preparation_summary_workbook
from ..services.procurement import cutoff_payload, validate_cutoff_time

router = APIRouter(tags=["procurement"])

ACTUAL_EXPR = "COALESCE(NULLIF(order_items.actual_quantity, ''), order_items.quantity)"
REQUESTED_EXPR = "COALESCE(NULLIF(order_items.requested_quantity, ''), order_items.quantity)"


def business_date_filter(alias: str = "orders") -> str:
    return f"date(datetime({alias}.created_at, '+8 hours')) = date(?)"


def scope_statuses(scope: str) -> tuple[str, ...]:
    if scope == "active":
        return ("pending", "accepted", "preparing")
    if scope == "shipped":
        return ("shipped", "completed")
    return ("accepted", "preparing")


@router.get("/procurement/cutoff")
def get_cutoff(user=Depends(current_user)):
    with connect() as conn:
        return cutoff_payload(conn)


@router.patch("/admin/procurement/cutoff")
def patch_cutoff(body: CutoffPatch, admin=Depends(require_admin_user)):
    cutoff_time = validate_cutoff_time(body.cutoff_time)
    with transaction() as conn:
        conn.execute(
            """
            INSERT INTO procurement_settings(id, cutoff_enabled, cutoff_time, updated_by, updated_at)
            VALUES (1, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(id) DO UPDATE SET
              cutoff_enabled = excluded.cutoff_enabled,
              cutoff_time = excluded.cutoff_time,
              updated_by = excluded.updated_by,
              updated_at = CURRENT_TIMESTAMP
            """,
            (1 if body.enabled else 0, cutoff_time, admin["id"]),
        )
        return cutoff_payload(conn)


@router.put("/admin/procurement/cutoff/overrides/{business_date}")
def put_cutoff_override(business_date: str, body: CutoffOverridePut, admin=Depends(require_admin_user)):
    cutoff_time = validate_cutoff_time(body.cutoff_time)
    with transaction() as conn:
        conn.execute(
            """
            INSERT INTO procurement_cutoff_overrides(business_date, cutoff_enabled, cutoff_time, note, updated_by, updated_at)
            VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(business_date) DO UPDATE SET
              cutoff_enabled = excluded.cutoff_enabled,
              cutoff_time = excluded.cutoff_time,
              note = excluded.note,
              updated_by = excluded.updated_by,
              updated_at = CURRENT_TIMESTAMP
            """,
            (business_date, 1 if body.enabled else 0, cutoff_time, body.note.strip(), admin["id"]),
        )
        return cutoff_payload(conn)


def preparation_rows(conn, business_date: str, scope: str, category: str | None, page: int, page_size: int):
    statuses = scope_statuses(scope)
    where = [business_date_filter(), f"orders.status IN ({','.join('?' for _ in statuses)})"]
    params: list = [business_date, *statuses]
    if category:
        where.append("order_items.category_snapshot = ?")
        params.append(category)
    base_sql = f"""
        SELECT
          order_items.product_id,
          order_items.product_code_snapshot AS product_code,
          order_items.product_name_snapshot AS product_name,
          order_items.category_snapshot AS category,
          order_items.spec_snapshot AS spec,
          order_items.unit_snapshot AS unit,
          SUM(CAST({REQUESTED_EXPR} AS REAL)) AS requested_quantity,
          SUM(CAST({ACTUAL_EXPR} AS REAL)) AS actual_quantity,
          COUNT(DISTINCT orders.unit_id) AS unit_count,
          COUNT(DISTINCT orders.id) AS order_count
        FROM order_items
        JOIN orders ON orders.id = order_items.order_id
        WHERE {' AND '.join(where)}
        GROUP BY order_items.product_id, order_items.product_code_snapshot, order_items.product_name_snapshot,
                 order_items.category_snapshot, order_items.spec_snapshot, order_items.unit_snapshot
        ORDER BY order_items.category_snapshot, order_items.product_name_snapshot
    """
    total = one(conn, f"SELECT COUNT(*) AS c FROM ({base_sql}) AS grouped", params)["c"]
    rows = all_rows(conn, base_sql + " LIMIT ? OFFSET ?", (*params, page_size, (page - 1) * page_size))
    return {"items": rows, "total": total, "page": page, "page_size": page_size}


@router.get("/admin/preparation-summary")
def preparation_summary(
    business_date: str | None = None,
    scope: str = Query(default="pending_preparation", pattern="^(pending_preparation|active|shipped)$"),
    category: str | None = None,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=100, ge=1, le=500),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        date_text = business_date or cutoff_payload(conn)["business_date"]
        return preparation_rows(conn, date_text, scope, category, page, page_size)


@router.get("/admin/preparation-summary/export.xlsx")
def export_preparation_summary(
    business_date: str | None = None,
    scope: str = Query(default="pending_preparation", pattern="^(pending_preparation|active|shipped)$"),
    category: str | None = None,
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        date_text = business_date or cutoff_payload(conn)["business_date"]
        rows = preparation_rows(conn, date_text, scope, category, 1, 10000)["items"]
    return Response(
        preparation_summary_workbook(rows),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": "attachment; filename=preparation-summary.xlsx"},
    )


def delivery_sheet_rows(conn, business_date: str, status: str | None, unit_id: str | None):
    where = [business_date_filter(), "orders.status != 'cancelled'"]
    params: list = [business_date]
    if status:
        where.append("orders.status = ?")
        params.append(status)
    if unit_id:
        where.append("orders.unit_id = ?")
        params.append(unit_id)
    rows = all_rows(
        conn,
        f"""
        SELECT
          orders.id AS order_id,
          orders.order_no,
          orders.unit_id,
          orders.unit_name_snapshot AS unit_name,
          orders.delivery_point_snapshot AS delivery_point,
          orders.status,
          orders.created_at,
          order_items.id AS item_id,
          order_items.product_code_snapshot AS product_code,
          order_items.product_name_snapshot AS product_name,
          order_items.spec_snapshot AS spec,
          order_items.unit_snapshot AS unit,
          {REQUESTED_EXPR} AS requested_quantity,
          {ACTUAL_EXPR} AS actual_quantity,
          order_items.adjustment_reason
        FROM orders
        JOIN order_items ON order_items.order_id = orders.id
        WHERE {' AND '.join(where)}
        ORDER BY orders.unit_name_snapshot, orders.order_no, order_items.rowid
        """,
        params,
    )
    units: dict[str, dict] = {}
    for row in rows:
        unit = units.setdefault(
            row["unit_id"],
            {"unit_id": row["unit_id"], "unit_name": row["unit_name"], "delivery_point": row["delivery_point"], "orders": {}},
        )
        order = unit["orders"].setdefault(
            row["order_id"],
            {"order_id": row["order_id"], "order_no": row["order_no"], "status": row["status"], "created_at": row["created_at"], "items": []},
        )
        order["items"].append(
            {
                "item_id": row["item_id"],
                "product_code": row["product_code"],
                "product_name": row["product_name"],
                "spec": row["spec"],
                "unit": row["unit"],
                "requested_quantity": row["requested_quantity"],
                "actual_quantity": row["actual_quantity"],
                "adjustment_reason": row["adjustment_reason"] or "",
                "adjusted": str(row["requested_quantity"]) != str(row["actual_quantity"]),
            }
        )
    return [
        {**unit, "orders": list(unit["orders"].values())}
        for unit in units.values()
    ]


@router.get("/admin/delivery-sheets")
def delivery_sheets(
    business_date: str | None = None,
    status: str | None = None,
    unit_id: str | None = None,
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        date_text = business_date or cutoff_payload(conn)["business_date"]
        return {"business_date": date_text, "units": delivery_sheet_rows(conn, date_text, status, unit_id)}


@router.get("/admin/delivery-sheets/export.xlsx")
def export_delivery_sheets(
    business_date: str | None = None,
    status: str | None = None,
    unit_id: str | None = None,
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        date_text = business_date or cutoff_payload(conn)["business_date"]
        units = delivery_sheet_rows(conn, date_text, status, unit_id)
    return Response(
        delivery_sheets_workbook(units),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": "attachment; filename=delivery-sheets.xlsx"},
    )


@router.get("/notifications/badges")
def notification_badges(user=Depends(current_user)):
    with connect() as conn:
        if user["role"] == "admin":
            pending = one(conn, "SELECT COUNT(*) AS c FROM orders WHERE status = 'pending'")["c"]
            issues = one(conn, "SELECT COUNT(*) AS c FROM receipt_issues WHERE status = 'open'")["c"]
            return {"pending_orders": pending, "open_receipt_issues": issues}
        shipped = one(conn, "SELECT COUNT(*) AS c FROM orders WHERE unit_id = ? AND status = 'shipped'", (user["unit_id"],))["c"]
        adjusted = one(
            conn,
            f"""
            SELECT COUNT(DISTINCT orders.id) AS c
            FROM orders
            JOIN order_items ON order_items.order_id = orders.id
            WHERE orders.unit_id = ?
              AND orders.status NOT IN ('completed', 'cancelled')
              AND CAST({REQUESTED_EXPR} AS TEXT) != CAST({ACTUAL_EXPR} AS TEXT)
            """,
            (user["unit_id"],),
        )["c"]
        resolved = one(
            conn,
            """
            SELECT COUNT(*) AS c
            FROM receipt_issues
            JOIN orders ON orders.id = receipt_issues.order_id
            WHERE receipt_issues.unit_id = ? AND receipt_issues.status = 'resolved' AND orders.status != 'completed'
            """,
            (user["unit_id"],),
        )["c"]
        return {"shipped_unconfirmed": shipped, "adjusted_orders": adjusted, "resolved_receipt_issues": resolved}
