from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.database import connect, init_db


QUERIES = {
    "orders_by_unit": (
        "SELECT * FROM orders WHERE unit_id = ? ORDER BY created_at DESC, id DESC LIMIT 30",
        ("unit-id",),
    ),
    "orders_by_status": (
        "SELECT * FROM orders WHERE status = ? ORDER BY created_at DESC, id DESC LIMIT 30",
        ("pending",),
    ),
    "products_by_active_category": (
        "SELECT * FROM products WHERE active = 1 AND category = ? ORDER BY updated_at DESC LIMIT 50",
        ("蔬菜",),
    ),
    "order_items_by_order": (
        "SELECT * FROM order_items WHERE order_id = ?",
        ("order-id",),
    ),
    "sessions_by_token": (
        "SELECT * FROM sessions WHERE token_hash = ?",
        ("token-hash",),
    ),
}


def main():
    init_db()
    with connect() as conn:
        for name, (sql, params) in QUERIES.items():
            print(f"\n{name}")
            for row in conn.execute("EXPLAIN QUERY PLAN " + sql, params):
                print(" | ".join(str(value) for value in row))


if __name__ == "__main__":
    main()
