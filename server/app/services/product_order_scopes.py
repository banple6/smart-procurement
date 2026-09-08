from __future__ import annotations

from fastapi import HTTPException

from ..database import one


ORDER_SCOPE_ALL = "all"
ORDER_SCOPE_SELECTED = "selected"
ORDER_SCOPE_MODES = {ORDER_SCOPE_ALL, ORDER_SCOPE_SELECTED}


def unit_can_order_product(conn, product: dict, unit_id: str) -> bool:
    if product.get("order_scope_mode", ORDER_SCOPE_ALL) == ORDER_SCOPE_ALL:
        return True
    return one(
        conn,
        "SELECT 1 FROM product_order_scope_units WHERE product_id = ? AND unit_id = ?",
        (product["id"], unit_id),
    ) is not None


def require_product_order_scope(conn, product: dict, unit_id: str, *, mutation: bool = False) -> None:
    if unit_can_order_product(conn, product, unit_id):
        return
    if mutation:
        raise HTTPException(status_code=409, detail="该食材不在当前单位下单范围内，请刷新食材目录后重试")
    raise HTTPException(status_code=404, detail="食材不存在")
