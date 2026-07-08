from __future__ import annotations

import random
from decimal import Decimal


def auth_headers(token: str) -> dict[str, str]:
    return {"Authorization": f"Bearer {token}"}


def list_products(client, token: str):
    return client.get("/api/v1/products", headers=auth_headers(token), name="products_list")


def list_orders(client, token: str):
    return client.get("/api/v1/orders", headers=auth_headers(token), name="orders_list")


def submit_small_order(client, token: str, products: list[dict]):
    candidates = [
        item for item in products
        if item.get("active", True)
        and item.get("supply_status") in {"normal", "low_stock", "正常供应", "库存紧张"}
        and Decimal(str(item.get("available_quantity", item.get("availableQuantity", "0")) or "0")) >= Decimal("1")
    ]
    if not candidates:
        return None
    product = random.choice(candidates)
    payload = {
        "items": [{"product_id": product["id"], "quantity": "1"}],
        "note": "LOADTEST_订单",
        "client_request_id": f"LOADTEST_{random.randrange(1_000_000_000)}",
    }
    return client.post("/api/v1/orders", headers=auth_headers(token), json=payload, name="orders_create")


def admin_dashboard(client, token: str):
    return client.get("/api/v1/admin/dashboard", headers=auth_headers(token), name="admin_dashboard")


def admin_orders(client, token: str):
    return client.get("/api/v1/admin/orders", headers=auth_headers(token), name="admin_orders")


def admin_products(client, token: str):
    return client.get("/api/v1/admin/products", headers=auth_headers(token), name="admin_products")
