from decimal import Decimal
from uuid import uuid4

from fastapi import HTTPException

from ..database import one


def decimal_text(value) -> str:
    return str(Decimal(str(value)).normalize())


def as_decimal(value) -> Decimal:
    return Decimal(str(value))


def available(product) -> Decimal:
    return as_decimal(product["stock_quantity"]) - as_decimal(product["reserved_quantity"])


def log_inventory(conn, product_id: str, order_id: str | None, action: str, quantity: Decimal, actor_id: str, detail: str = ""):
    conn.execute(
        """
        INSERT INTO inventory_logs(id, product_id, order_id, action, quantity, actor_id, detail)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (str(uuid4()), product_id, order_id, action, decimal_text(quantity), actor_id, detail),
    )


def reserve_product(conn, product_id: str, quantity: Decimal, order_id: str | None, actor_id: str):
    product = one(conn, "SELECT * FROM products WHERE id = ? AND is_deleted = 0", (product_id,))
    if not product or not product["active"] or product["supply_status"] not in ("normal", "tight"):
        raise HTTPException(status_code=409, detail="Product unavailable")
    if quantity <= 0:
        raise HTTPException(status_code=400, detail="Quantity must be positive")
    if available(product) < quantity:
        raise HTTPException(status_code=409, detail="Insufficient inventory")
    new_reserved = as_decimal(product["reserved_quantity"]) + quantity
    conn.execute(
        "UPDATE products SET reserved_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (decimal_text(new_reserved), product_id),
    )
    log_inventory(conn, product_id, order_id, "order_reserve", quantity, actor_id)
    return product


def release_product(conn, product_id: str, quantity: Decimal, order_id: str | None, actor_id: str):
    product = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    new_reserved = as_decimal(product["reserved_quantity"]) - quantity
    if new_reserved < 0:
        raise HTTPException(status_code=409, detail="Reserved inventory cannot be negative")
    conn.execute(
        "UPDATE products SET reserved_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (decimal_text(new_reserved), product_id),
    )
    log_inventory(conn, product_id, order_id, "order_release", quantity, actor_id)


def complete_product(conn, product_id: str, quantity: Decimal, order_id: str, actor_id: str):
    product = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    new_stock = as_decimal(product["stock_quantity"]) - quantity
    new_reserved = as_decimal(product["reserved_quantity"]) - quantity
    if new_stock < 0 or new_reserved < 0:
        raise HTTPException(status_code=409, detail="Inventory cannot be negative")
    conn.execute(
        "UPDATE products SET stock_quantity = ?, reserved_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (decimal_text(new_stock), decimal_text(new_reserved), product_id),
    )
    log_inventory(conn, product_id, order_id, "order_complete", quantity, actor_id)

