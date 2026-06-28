from decimal import Decimal
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException

from ..database import all_rows, one, transaction, connect
from ..dependencies import current_user, require_admin_user, require_unit_user
from ..models import ADMIN_TRANSITIONS
from ..schemas import OrderCreate, OrderStatusPatch
from ..services.inventory import as_decimal, complete_product, decimal_text, release_product, reserve_product

router = APIRouter(tags=["orders"])


def order_no(conn) -> str:
    row = one(conn, "SELECT COUNT(*) AS c FROM orders")
    return f"SP{int(row['c']) + 1:08d}"


def get_unit(conn, unit_id: str):
    unit = one(conn, "SELECT * FROM units WHERE id = ? AND active = 1", (unit_id,))
    if not unit:
        raise HTTPException(status_code=403, detail="Unit unavailable")
    return unit


def order_items(conn, order_id: str):
    return all_rows(conn, "SELECT * FROM order_items WHERE order_id = ? ORDER BY rowid", (order_id,))


def order_out(conn, order: dict) -> dict:
    return {**order, "items": order_items(conn, order["id"])}


def fetch_order_for_user(conn, order_id: str, user: dict):
    order = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
    if not order:
        raise HTTPException(status_code=404, detail="Order not found")
    if user["role"] == "unit_user" and order["unit_id"] != user["unit_id"]:
        raise HTTPException(status_code=404, detail="Order not found")
    return order


def create_order_rows(conn, body: OrderCreate, user: dict, existing_order_id: str | None = None):
    if not body.items:
        raise HTTPException(status_code=400, detail="Order requires items")
    unit = get_unit(conn, user["unit_id"])
    order_id = existing_order_id or str(uuid4())
    total = 0
    items_payload = []
    for item in body.items:
        quantity = as_decimal(item.quantity)
        product = reserve_product(conn, item.product_id, quantity, order_id, user["id"])
        subtotal = int((Decimal(product["price_cents"]) * quantity).to_integral_value())
        total += subtotal
        items_payload.append((product, quantity, subtotal))

    if existing_order_id:
        conn.execute("DELETE FROM order_items WHERE order_id = ?", (existing_order_id,))
        conn.execute(
            "UPDATE orders SET total_cents = ?, note = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (total, body.note, existing_order_id),
        )
    else:
        conn.execute(
            """
            INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot, note, status, total_cents, created_by)
            VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?)
            """,
            (order_id, order_no(conn), unit["id"], unit["unit_name"], unit["default_delivery_point"], body.note, total, user["id"]),
        )
    for product, quantity, subtotal in items_payload:
        conn.execute(
            """
            INSERT INTO order_items(id, order_id, product_id, product_code_snapshot, product_name_snapshot,
              category_snapshot, spec_snapshot, unit_snapshot, price_cents_snapshot, quantity, subtotal_cents)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()), order_id, product["id"], product["product_code"], product["name"], product["category"],
                product["spec"], product["unit"], product["price_cents"], decimal_text(quantity), subtotal,
            ),
        )
    conn.execute(
        "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (str(uuid4()), order_id, user["id"], "create" if not existing_order_id else "update", None, "pending", body.note),
    )
    return one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))


@router.post("/orders")
def create_order(body: OrderCreate, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = create_order_rows(conn, body, user)
        return order_out(conn, order)


@router.get("/orders")
def list_orders(user=Depends(require_unit_user)):
    with connect() as conn:
        return all_rows(conn, "SELECT * FROM orders WHERE unit_id = ? ORDER BY created_at DESC", (user["unit_id"],))


@router.get("/orders/{order_id}")
def order_detail(order_id: str, user=Depends(current_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        return order_out(conn, order)


@router.put("/orders/{order_id}")
def update_pending_order(order_id: str, body: OrderCreate, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "pending":
            raise HTTPException(status_code=409, detail="Only pending orders can be edited")
        for item in order_items(conn, order_id):
            release_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, user["id"])
        updated = create_order_rows(conn, body, user, existing_order_id=order_id)
        return order_out(conn, updated)


@router.post("/orders/{order_id}/cancel")
def cancel_order(order_id: str, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] not in ("pending",):
            raise HTTPException(status_code=409, detail="Only pending orders can be cancelled by unit")
        for item in order_items(conn, order_id):
            release_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, user["id"])
        conn.execute("UPDATE orders SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'cancel', ?, 'cancelled')",
            (str(uuid4()), order_id, user["id"], order["status"]),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.post("/orders/{order_id}/confirm-receipt")
def confirm_receipt(order_id: str, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "shipped":
            raise HTTPException(status_code=409, detail="Only shipped orders can be completed")
        for item in order_items(conn, order_id):
            complete_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, user["id"])
        conn.execute("UPDATE orders SET status = 'completed', completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'confirm_receipt', 'shipped', 'completed')",
            (str(uuid4()), order_id, user["id"]),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.get("/admin/orders")
def admin_orders(admin=Depends(require_admin_user)):
    with connect() as conn:
        return all_rows(conn, "SELECT * FROM orders ORDER BY created_at DESC")


@router.get("/admin/orders/{order_id}")
def admin_order_detail(order_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        return order_out(conn, order)


@router.patch("/admin/orders/{order_id}/status")
def admin_order_status(order_id: str, body: OrderStatusPatch, admin=Depends(require_admin_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        allowed = ADMIN_TRANSITIONS.get(order["status"], set())
        if body.status not in allowed:
            raise HTTPException(status_code=409, detail="Illegal status transition")
        if body.status == "cancelled":
            for item in order_items(conn, order_id):
                release_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, admin["id"])
        elif body.status == "completed":
            for item in order_items(conn, order_id):
                complete_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, admin["id"])
        time_field = {
            "accepted": "accepted_at",
            "preparing": "preparing_at",
            "shipped": "shipped_at",
            "completed": "completed_at",
        }.get(body.status)
        if time_field:
            conn.execute(f"UPDATE orders SET status = ?, {time_field} = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (body.status, order_id))
        else:
            conn.execute("UPDATE orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (body.status, order_id))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'status', ?, ?)",
            (str(uuid4()), order_id, admin["id"], order["status"], body.status),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))

