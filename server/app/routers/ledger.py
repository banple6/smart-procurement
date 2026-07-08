from fastapi import APIRouter, Depends, Response
from datetime import date
from urllib.parse import quote

from ..database import all_rows, connect
from ..dependencies import require_admin_user
from ..services.exports import ledger_workbook

router = APIRouter(prefix="/admin", tags=["ledger"])


def excel_attachment(filename: str) -> dict[str, str]:
    return {"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"}


def ledger_rows(conn, start_date=None, end_date=None, unit_id=None, status=None, product=None, order_no=None):
    where = ["1 = 1"]
    params = []
    if start_date:
        where.append("date(orders.created_at) >= date(?)")
        params.append(start_date)
    if end_date:
        where.append("date(orders.created_at) <= date(?)")
        params.append(end_date)
    if unit_id:
        where.append("orders.unit_id = ?")
        params.append(unit_id)
    if status:
        where.append("orders.status = ?")
        params.append(status)
    if product:
        where.append("(order_items.product_name_snapshot LIKE ? OR order_items.product_code_snapshot LIKE ?)")
        params.extend([f"%{product}%", f"%{product}%"])
    if order_no:
        where.append("orders.order_no LIKE ?")
        params.append(f"%{order_no}%")
    return all_rows(
        conn,
        f"""
        SELECT orders.*, order_items.*
        FROM orders
        JOIN order_items ON order_items.order_id = orders.id
        WHERE {' AND '.join(where)}
        ORDER BY orders.created_at DESC
        """,
        params,
    )


@router.get("/ledger")
def ledger(start_date: str | None = None, end_date: str | None = None, unit_id: str | None = None, status: str | None = None, product: str | None = None, order_no: str | None = None, admin=Depends(require_admin_user)):
    with connect() as conn:
        return ledger_rows(conn, start_date, end_date, unit_id, status, product, order_no)


@router.get("/ledger/export.xlsx")
def export_ledger(start_date: str | None = None, end_date: str | None = None, unit_id: str | None = None, status: str | None = None, product: str | None = None, order_no: str | None = None, admin=Depends(require_admin_user)):
    with connect() as conn:
        content = ledger_workbook(ledger_rows(conn, start_date, end_date, unit_id, status, product, order_no))
    return Response(
        content,
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers=excel_attachment(f"三公鲜配_采购台账_{date.today().strftime('%Y%m%d')}.xlsx"),
    )
