from decimal import Decimal, InvalidOperation
from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Request
from fastapi.responses import HTMLResponse
from pydantic import BaseModel

from ..database import all_rows, connect, one, transaction
from ..dependencies import require_csrf, require_unit_web_session
from ..routers.orders import fetch_order_for_user, order_items, order_out
from ..schemas import OrderCreate, OrderItemRequest
from ..services.inventory import as_decimal, complete_product, decimal_text
from ..services.procurement import cutoff_payload

router = APIRouter(tags=["unit-web"])

TEMPLATE_ROOT = Path(__file__).resolve().parents[1] / "templates" / "unit"
SUPPLY_ALLOWED = ("normal", "tight")


class CartItemBody(BaseModel):
    product_id: str = ""
    quantity: str = "1"


class CartQuantityBody(BaseModel):
    quantity: str = "1"


class UnitOrderSubmitBody(BaseModel):
    note: str = ""


class ReceiptIssueBody(BaseModel):
    issue_type: str = "other"
    description: str = ""


def html_file(name: str) -> str:
    return (TEMPLATE_ROOT / name).read_text(encoding="utf-8")


def no_store(response: HTMLResponse) -> HTMLResponse:
    response.headers["Cache-Control"] = "no-store, private"
    response.headers["Pragma"] = "no-cache"
    return response


def render_unit_page(template_name: str, title: str, user: dict) -> HTMLResponse:
    content = html_file(template_name)
    base = html_file("base.html")
    html = (
        base.replace("{{title}}", title)
        .replace("{{content}}", content)
        .replace("{{username}}", user["display_name"] or user["username"])
    )
    return no_store(HTMLResponse(html))


def product_out(row: dict) -> dict:
    stock = as_decimal(row["stock_quantity"])
    reserved = as_decimal(row["reserved_quantity"])
    available = stock - reserved
    return {
        **row,
        "active": bool(row["active"]),
        "is_deleted": bool(row["is_deleted"]),
        "available_quantity": decimal_text(available),
        "can_order": bool(row["active"]) and not bool(row["is_deleted"]) and row["supply_status"] in SUPPLY_ALLOWED and available > 0,
    }


def money_subtotal(price_cents: int, quantity: Decimal) -> int:
    return int((Decimal(price_cents) * quantity).to_integral_value())


def validate_quantity(product: dict, quantity_text: str) -> Decimal:
    try:
        quantity = Decimal(str(quantity_text))
    except InvalidOperation as exc:
        raise HTTPException(status_code=400, detail="数量不正确") from exc
    if quantity <= 0:
        raise HTTPException(status_code=400, detail="数量不正确")
    if bool(product["is_deleted"]) or not bool(product["active"]) or product["supply_status"] not in SUPPLY_ALLOWED:
        raise HTTPException(status_code=409, detail="食材暂不可申领")
    minimum = as_decimal(product["min_order_quantity"])
    step = as_decimal(product["quantity_step"])
    available = as_decimal(product["stock_quantity"]) - as_decimal(product["reserved_quantity"])
    if quantity < minimum:
        raise HTTPException(status_code=409, detail="低于最小申领量")
    if step <= 0:
        raise HTTPException(status_code=409, detail="食材申领步长配置不正确")
    if (quantity - minimum) % step != 0:
        raise HTTPException(status_code=409, detail="数量不符合申领步长")
    if quantity > available:
        raise HTTPException(status_code=409, detail="库存不足，请减少数量")
    return quantity


def get_product_for_cart(conn, product_id: str) -> dict:
    product = one(conn, "SELECT * FROM products WHERE id = ? AND is_deleted = 0", (product_id,))
    if not product:
        raise HTTPException(status_code=404, detail="食材不存在")
    return product


def cart_rows(conn, user: dict) -> list[dict]:
    rows = all_rows(
        conn,
        """
        SELECT c.id, c.user_id, c.unit_id, c.product_id, c.quantity, c.created_at, c.updated_at,
               p.product_code, p.name, p.category, p.spec, p.unit, p.price_cents,
               p.stock_quantity, p.reserved_quantity, p.min_order_quantity, p.quantity_step,
               p.supply_status, p.active, p.is_deleted, p.image_path
        FROM web_cart_items c
        JOIN products p ON p.id = c.product_id
        WHERE c.user_id = ? AND c.unit_id = ?
        ORDER BY c.created_at
        """,
        (user["id"], user["unit_id"]),
    )
    for row in rows:
        quantity = as_decimal(row["quantity"])
        row["subtotal_cents"] = money_subtotal(int(row["price_cents"]), quantity)
        row["available_quantity"] = decimal_text(as_decimal(row["stock_quantity"]) - as_decimal(row["reserved_quantity"]))
        row["active"] = bool(row["active"])
        row["is_deleted"] = bool(row["is_deleted"])
    return rows


def cart_payload(conn, user: dict) -> dict:
    rows = cart_rows(conn, user)
    unit = one(conn, "SELECT unit_name, unit_code, default_delivery_point FROM units WHERE id = ?", (user["unit_id"],))
    return {
        "items": rows,
        "item_count": len(rows),
        "total_cents": sum(int(row["subtotal_cents"]) for row in rows),
        "delivery_point": unit["default_delivery_point"] if unit else "",
        "unit": unit,
    }


@router.get("/unit/home", include_in_schema=False)
def unit_home(user=Depends(require_unit_web_session)):
    return render_unit_page("home.html", "单位首页", user)


@router.get("/unit/products", include_in_schema=False)
def unit_products_page(user=Depends(require_unit_web_session)):
    return render_unit_page("products.html", "食材申领", user)


@router.get("/unit/cart", include_in_schema=False)
def unit_cart_page(user=Depends(require_unit_web_session)):
    return render_unit_page("cart.html", "采购清单", user)


@router.get("/unit/orders", include_in_schema=False)
def unit_orders_page(user=Depends(require_unit_web_session)):
    return render_unit_page("orders.html", "我的订单", user)


@router.get("/unit/profile", include_in_schema=False)
def unit_profile_page(user=Depends(require_unit_web_session)):
    return render_unit_page("profile.html", "我的", user)


@router.get("/unit/home/data")
def unit_home_data(user=Depends(require_unit_web_session)):
    with connect() as conn:
        unit = one(conn, "SELECT * FROM units WHERE id = ?", (user["unit_id"],))
        cutoff = cutoff_payload(conn)
        cart_count = one(conn, "SELECT COUNT(*) AS c FROM web_cart_items WHERE user_id = ? AND unit_id = ?", (user["id"], user["unit_id"]))["c"]
        waiting_receipt = one(conn, "SELECT COUNT(*) AS c FROM orders WHERE unit_id = ? AND status = 'shipped'", (user["unit_id"],))["c"]
        recent_orders = all_rows(conn, "SELECT * FROM orders WHERE unit_id = ? ORDER BY created_at DESC LIMIT 5", (user["unit_id"],))
        return {
            "unit": unit,
            "cutoff": cutoff,
            "cart_count": cart_count,
            "waiting_receipt": waiting_receipt,
            "recent_orders": [order_out(conn, row, include_items=False, include_shipping_photos=False) for row in recent_orders],
            "tips": ["请按实际需求申领食材", "提交前请核对数量和配送点"],
        }


@router.get("/unit/products/data")
def unit_products_data(q: str | None = None, category: str | None = None, status: str | None = None, user=Depends(require_unit_web_session)):
    where = ["is_deleted = 0", "active = 1", "supply_status IN ('normal', 'tight')"]
    params: list[str] = []
    if q:
        where.append("(name LIKE ? OR product_code LIKE ?)")
        params.extend([f"%{q}%", f"%{q}%"])
    if category:
        where.append("category = ?")
        params.append(category)
    if status in SUPPLY_ALLOWED:
        where.append("supply_status = ?")
        params.append(status)
    with connect() as conn:
        rows = all_rows(conn, f"SELECT * FROM products WHERE {' AND '.join(where)} ORDER BY category, name", params)
        return {"items": [product_out(row) for row in rows]}


@router.get("/unit/cart/data")
def unit_cart_data(user=Depends(require_unit_web_session)):
    with connect() as conn:
        return cart_payload(conn, user)


@router.post("/unit/cart/items")
def add_cart_item(body: CartItemBody, request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        product = get_product_for_cart(conn, body.product_id)
        quantity = validate_quantity(product, body.quantity)
        item_id = str(uuid4())
        conn.execute(
            """
            INSERT INTO web_cart_items(id, user_id, unit_id, product_id, quantity)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(user_id, product_id) DO UPDATE SET
              quantity = excluded.quantity,
              unit_id = excluded.unit_id,
              updated_at = CURRENT_TIMESTAMP
            """,
            (item_id, user["id"], user["unit_id"], product["id"], decimal_text(quantity)),
        )
        return cart_payload(conn, user)


@router.patch("/unit/cart/items/{item_id}")
def update_cart_item(item_id: str, body: CartQuantityBody, request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        item = one(conn, "SELECT * FROM web_cart_items WHERE id = ? AND user_id = ? AND unit_id = ?", (item_id, user["id"], user["unit_id"]))
        if not item:
            raise HTTPException(status_code=404, detail="清单食材不存在")
        product = get_product_for_cart(conn, item["product_id"])
        quantity = validate_quantity(product, body.quantity)
        conn.execute("UPDATE web_cart_items SET quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (decimal_text(quantity), item_id))
        return cart_payload(conn, user)


@router.delete("/unit/cart/items/{item_id}")
def delete_cart_item(item_id: str, request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        conn.execute("DELETE FROM web_cart_items WHERE id = ? AND user_id = ? AND unit_id = ?", (item_id, user["id"], user["unit_id"]))
        return cart_payload(conn, user)


@router.delete("/unit/cart/items")
def clear_cart_items(request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        conn.execute("DELETE FROM web_cart_items WHERE user_id = ? AND unit_id = ?", (user["id"], user["unit_id"]))
        return cart_payload(conn, user)


@router.post("/unit/orders")
def submit_unit_order(body: UnitOrderSubmitBody, request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        rows = cart_rows(conn, user)
        if not rows:
            raise HTTPException(status_code=400, detail="请先选择食材")
        order_body = OrderCreate(
            note=body.note,
            items=[OrderItemRequest(product_id=row["product_id"], quantity=row["quantity"]) for row in rows],
        )
        from ..routers.orders import create_order_rows

        order = create_order_rows(conn, order_body, user)
        conn.execute("DELETE FROM web_cart_items WHERE user_id = ? AND unit_id = ?", (user["id"], user["unit_id"]))
        return order_out(conn, order)


@router.get("/unit/orders/data")
def unit_orders_data(status: str | None = None, user=Depends(require_unit_web_session)):
    with connect() as conn:
        sql = "SELECT * FROM orders WHERE unit_id = ?"
        params: list[str] = [user["unit_id"]]
        if status:
            sql += " AND status = ?"
            params.append(status)
        rows = all_rows(conn, sql + " ORDER BY created_at DESC LIMIT 100", params)
        return {"items": [order_out(conn, row, include_items=False, include_shipping_photos=False) for row in rows]}


@router.get("/unit/orders/{order_id}", include_in_schema=False)
def unit_order_detail_page(order_id: str, user=Depends(require_unit_web_session)):
    return render_unit_page("order_detail.html", "订单详情", user)


@router.get("/unit/orders/{order_id}/data")
def unit_order_detail_data(order_id: str, user=Depends(require_unit_web_session)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        return order_out(conn, order)


@router.post("/unit/orders/{order_id}/confirm-receipt")
def unit_confirm_receipt(order_id: str, request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "shipped":
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        for item in order_items(conn, order_id):
            complete_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, user["id"])
        conn.execute("UPDATE orders SET status = 'completed', completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.post("/unit/orders/{order_id}/receipt-issues")
def unit_receipt_issue(order_id: str, body: ReceiptIssueBody, request: Request, user=Depends(require_unit_web_session)):
    require_csrf(request)
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] not in ("shipped", "completed"):
            raise HTTPException(status_code=409, detail="当前订单不能提交收货异常")
        issue_id = str(uuid4())
        conn.execute(
            """
            INSERT INTO receipt_issues(id, order_id, unit_id, issue_type, description, reported_by)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (issue_id, order_id, user["unit_id"], body.issue_type.strip()[:40] or "other", body.description.strip()[:500], user["id"]),
        )
        return one(conn, "SELECT * FROM receipt_issues WHERE id = ?", (issue_id,))
