from decimal import Decimal
from uuid import uuid4

from fastapi import HTTPException

from ..database import one


def decimal_text(value) -> str:
    normalized = Decimal(str(value)).normalize()
    text = format(normalized, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def as_decimal(value) -> Decimal:
    return Decimal(str(value))


def available(product) -> Decimal:
    return as_decimal(product["stock_quantity"]) - as_decimal(product["reserved_quantity"])


def validate_order_quantity(product, quantity: Decimal):
    minimum = as_decimal(product["min_order_quantity"])
    step = as_decimal(product["quantity_step"])
    if quantity <= 0:
        raise HTTPException(status_code=400, detail="申领数量必须大于 0")
    if quantity < minimum:
        raise HTTPException(status_code=400, detail="低于最小申领量")
    if step <= 0:
        raise HTTPException(status_code=409, detail="食材申领步长配置错误")
    remainder = (quantity - minimum) % step
    if remainder != 0:
        raise HTTPException(status_code=400, detail="数量不符合申领步长")


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
        raise HTTPException(status_code=409, detail="食材已暂停供应或下架")
    if int(product["price_cents"]) <= 0:
        raise HTTPException(status_code=409, detail="价格未设置")
    validate_order_quantity(product, quantity)
    if available(product) < quantity:
        raise HTTPException(status_code=409, detail="库存不足，请减少数量")
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
        raise HTTPException(status_code=404, detail="食材不存在")
    new_reserved = as_decimal(product["reserved_quantity"]) - quantity
    if new_reserved < 0:
        raise HTTPException(status_code=409, detail="预占库存不能小于 0")
    conn.execute(
        "UPDATE products SET reserved_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (decimal_text(new_reserved), product_id),
    )
    log_inventory(conn, product_id, order_id, "order_release", quantity, actor_id)


def complete_product(conn, product_id: str, quantity: Decimal, order_id: str, actor_id: str):
    product = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
    if not product:
        raise HTTPException(status_code=404, detail="食材不存在")
    new_stock = as_decimal(product["stock_quantity"]) - quantity
    new_reserved = as_decimal(product["reserved_quantity"]) - quantity
    if new_stock < 0 or new_reserved < 0:
        raise HTTPException(status_code=409, detail="库存不足，请减少数量")
    conn.execute(
        "UPDATE products SET stock_quantity = ?, reserved_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
        (decimal_text(new_stock), decimal_text(new_reserved), product_id),
    )
    log_inventory(conn, product_id, order_id, "order_complete", quantity, actor_id)
