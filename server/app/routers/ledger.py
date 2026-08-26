from fastapi import APIRouter, Depends, HTTPException, Query, Response
from datetime import date
from urllib.parse import quote

from ..database import all_rows, connect, decimal_text
from ..dependencies import require_admin_user
from ..services.exports import ledger_workbook
from ..services.local_time import display_local_time

router = APIRouter(prefix="/admin", tags=["ledger"])


def excel_attachment(filename: str) -> dict[str, str]:
    return {"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"}


def _text_or_none(value: str | None) -> str | None:
    value = (value or "").strip()
    return value or None


def _validate_dates(start_date: str | None, end_date: str | None) -> tuple[str | None, str | None]:
    start_date = _text_or_none(start_date)
    end_date = _text_or_none(end_date)
    for value, label in ((start_date, "开始日期"), (end_date, "结束日期")):
        if value:
            try:
                date.fromisoformat(value)
            except ValueError as error:
                raise HTTPException(status_code=422, detail=f"{label}格式应为 YYYY-MM-DD") from error
    if start_date and end_date and start_date > end_date:
        raise HTTPException(status_code=422, detail="开始日期不能晚于结束日期")
    return start_date, end_date


def _ledger_filters(start_date=None, end_date=None, unit_id=None, status=None, product=None, order_no=None):
    start_date, end_date = _validate_dates(start_date, end_date)
    where = ["orders.is_deleted = 0"]
    params = []
    if start_date:
        where.append("date(datetime(orders.created_at, '+8 hours')) >= date(?)")
        params.append(start_date)
    if end_date:
        where.append("date(datetime(orders.created_at, '+8 hours')) <= date(?)")
        params.append(end_date)
    if unit_id := _text_or_none(unit_id):
        where.append("orders.unit_id = ?")
        params.append(unit_id)
    if status := _text_or_none(status):
        where.append("orders.status = ?")
        params.append(status)
    if product := _text_or_none(product):
        where.append("(order_items.product_name_snapshot LIKE ? OR order_items.product_code_snapshot LIKE ?)")
        params.extend([f"%{product}%", f"%{product}%"])
    if order_no := _text_or_none(order_no):
        where.append("orders.order_no LIKE ?")
        params.append(f"%{order_no}%")
    return where, params


def ledger_rows(conn, start_date=None, end_date=None, unit_id=None, status=None, product=None, order_no=None, limit: int | None = None, offset: int = 0):
    where, params = _ledger_filters(start_date, end_date, unit_id, status, product, order_no)
    pagination = ""
    if limit is not None:
        pagination = " LIMIT ? OFFSET ?"
        params.extend([limit, offset])
    rows = all_rows(
        conn,
        f"""
        SELECT orders.*, order_items.*
        FROM orders
        JOIN order_items ON order_items.order_id = orders.id
        WHERE {' AND '.join(where)}
        ORDER BY orders.created_at DESC
        {pagination}
        """,
        params,
    )
    for row in rows:
        quantity = decimal_text(row.get("quantity") or "0")
        row["quantity"] = quantity
        if "requested_quantity" in row:
            row["requested_quantity"] = decimal_text(row.get("requested_quantity") or quantity)
        if "actual_quantity" in row:
            row["actual_quantity"] = decimal_text(row.get("actual_quantity") or quantity)
        for field in ("created_at", "accepted_at", "preparing_at", "shipped_at", "completed_at", "cancelled_at"):
            row[field] = display_local_time(row.get(field))
    return rows


def ledger_count(conn, start_date=None, end_date=None, unit_id=None, status=None, product=None, order_no=None) -> int:
    where, params = _ledger_filters(start_date, end_date, unit_id, status, product, order_no)
    row = conn.execute(
        f"""
        SELECT COUNT(*) AS total
        FROM orders
        JOIN order_items ON order_items.order_id = orders.id
        WHERE {' AND '.join(where)}
        """,
        params,
    ).fetchone()
    return int(row["total"] if row else 0)


@router.get("/ledger")
def ledger(
    start_date: str | None = None,
    end_date: str | None = None,
    unit_id: str | None = None,
    status: str | None = None,
    product: str | None = None,
    order_no: str | None = None,
    page: int | None = Query(default=None, ge=1),
    page_size: int = Query(default=20, ge=1, le=200),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        # Keep the established array response for existing Android callers. Web passes page explicitly.
        if page is None:
            return ledger_rows(conn, start_date, end_date, unit_id, status, product, order_no)
        total = ledger_count(conn, start_date, end_date, unit_id, status, product, order_no)
        return {
            "items": ledger_rows(conn, start_date, end_date, unit_id, status, product, order_no, limit=page_size, offset=(page - 1) * page_size),
            "total": total,
            "page": page,
            "page_size": page_size,
        }


def _ledger_filename(start_date: str | None, end_date: str | None, export_all: bool) -> str:
    today = date.today().strftime("%Y%m%d")
    if export_all:
        return f"三公鲜配_采购台账_全部_{today}.xlsx"
    start_date, end_date = _validate_dates(start_date, end_date)
    if start_date and end_date:
        return f"三公鲜配_采购台账_{start_date.replace('-', '')}-{end_date.replace('-', '')}.xlsx"
    if start_date:
        return f"三公鲜配_采购台账_{start_date.replace('-', '')}起.xlsx"
    if end_date:
        return f"三公鲜配_采购台账_截至{end_date.replace('-', '')}.xlsx"
    return f"三公鲜配_采购台账_{today}.xlsx"


@router.get("/ledger/export.xlsx")
def export_ledger(
    start_date: str | None = None,
    end_date: str | None = None,
    unit_id: str | None = None,
    status: str | None = None,
    product: str | None = None,
    order_no: str | None = None,
    all: bool = False,
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        rows = ledger_rows(conn) if all else ledger_rows(conn, start_date, end_date, unit_id, status, product, order_no)
        if not rows:
            raise HTTPException(status_code=400, detail="当前筛选条件下没有可导出的采购台账")
        content = ledger_workbook(rows)
    return Response(
        content,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers=excel_attachment(_ledger_filename(start_date, end_date, all)),
    )
