from __future__ import annotations

import os
import argparse
from decimal import Decimal

from app.database import column_names, connect, init_db
from app.security import hash_password

from loadtest_common import NAMESPACE, deterministic_id, ensure_dirs, require_loadtest_environment, rng


CATEGORIES = ["蔬菜", "水果", "肉禽", "水产", "粮油", "蛋奶", "调料", "其他"]
UNITS = ["公斤", "斤", "箱", "袋", "个", "筐", "盒", "瓶", "份", "包"]
STATUSES = ["normal", "low_stock", "paused"]
ADMIN_USER_COUNT = 15
UNIT_USER_COUNT = 40


def insert_user(conn, *, user_id: str, username: str, display_name: str, role: str, unit_id: str | None, password: str):
    fields = column_names(conn, "users")
    payload = {
        "id": user_id,
        "username": username,
        "password_hash": hash_password(password),
        "display_name": display_name,
        "role": role,
        "unit_id": unit_id,
        "active": 1,
        "must_change_password": 0,
    }
    for optional in (
        "session_version",
        "can_manage_accounts",
        "can_issue_manager_invites",
        "can_view_system_status",
        "can_view_detailed_metrics",
        "can_manage_backups",
        "can_restore_backups",
    ):
        if optional in fields:
            payload[optional] = 1 if role == "admin" else 0
    columns = [name for name in payload if name in fields]
    updates = [f"{name}=excluded.{name}" for name in columns if name not in {"id", "username"}]
    conn.execute(
        f"""
        INSERT INTO users({", ".join(columns)})
        VALUES ({", ".join("?" for _ in columns)})
        ON CONFLICT(username) DO UPDATE SET {", ".join(updates)}
        """,
        [payload[name] for name in columns],
    )


def seed_units(conn):
    for idx in range(1, 11):
        conn.execute(
            """
            INSERT INTO units(id, unit_code, unit_name, default_delivery_point, active)
            VALUES (?, ?, ?, ?, 1)
            ON CONFLICT(unit_code) DO UPDATE SET
              unit_name = excluded.unit_name,
              default_delivery_point = excluded.default_delivery_point,
              active = 1,
              updated_at = CURRENT_TIMESTAMP
            """,
            (
                deterministic_id(f"unit-{idx}"),
                f"{NAMESPACE}_UNIT_{idx:02d}",
                f"{NAMESPACE}_测试单位_{idx:02d}",
                f"{NAMESPACE}_配送点_{idx:02d}",
            ),
        )


def seed_users(conn, password: str):
    for idx in range(1, ADMIN_USER_COUNT + 1):
        insert_user(
            conn,
            user_id=deterministic_id(f"admin-{idx}"),
            username=f"{NAMESPACE}_admin_{idx:02d}",
            display_name=f"{NAMESPACE}_测试管理员_{idx:02d}",
            role="admin",
            unit_id=None,
            password=password,
        )
    for serial in range(1, UNIT_USER_COUNT + 1):
        unit_idx = ((serial - 1) % 10) + 1
        insert_user(
            conn,
            user_id=deterministic_id(f"unit-user-{serial}"),
            username=f"{NAMESPACE}_unit_{serial:02d}",
            display_name=f"{NAMESPACE}_单位账号_{serial:02d}",
            role="unit_user",
            unit_id=deterministic_id(f"unit-{unit_idx}"),
            password=password,
        )


def seed_products(conn):
    random = rng()
    for idx in range(1, 101):
        category = CATEGORIES[(idx - 1) % len(CATEGORIES)]
        unit = UNITS[(idx - 1) % len(UNITS)]
        status = STATUSES[(idx - 1) % len(STATUSES)]
        active = 0 if idx % 17 == 0 else 1
        stock = Decimal("3") if idx % 11 == 0 else Decimal(str(random.randint(20, 300)))
        price_cents = random.randint(120, 3800)
        conn.execute(
            """
            INSERT INTO products(
              id, product_code, name, category, spec, unit, price_cents, stock_quantity,
              reserved_quantity, min_order_quantity, quantity_step, warning_quantity,
              origin, supplier, shelf_life, storage_method, description, image_path,
              supply_status, active, is_deleted, created_by
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, '0', ?, ?, ?, ?, ?, ?, ?, ?, '', ?, ?, 0, ?)
            ON CONFLICT(product_code) DO UPDATE SET
              name = excluded.name,
              category = excluded.category,
              spec = excluded.spec,
              unit = excluded.unit,
              price_cents = excluded.price_cents,
              stock_quantity = excluded.stock_quantity,
              reserved_quantity = '0',
              min_order_quantity = excluded.min_order_quantity,
              quantity_step = excluded.quantity_step,
              warning_quantity = excluded.warning_quantity,
              origin = excluded.origin,
              supplier = excluded.supplier,
              shelf_life = excluded.shelf_life,
              storage_method = excluded.storage_method,
              description = excluded.description,
              supply_status = excluded.supply_status,
              active = excluded.active,
              is_deleted = 0,
              updated_at = CURRENT_TIMESTAMP
            """,
            (
                deterministic_id(f"product-{idx}"),
                f"{NAMESPACE}_PRODUCT_{idx:03d}",
                f"{NAMESPACE}_测试食材_{idx:03d}",
                category,
                "散装" if idx % 2 else "标准包装",
                unit,
                price_cents,
                str(stock),
                "1",
                "0.5" if idx % 4 == 0 else "1",
                "5",
                f"{NAMESPACE}_产地",
                f"{NAMESPACE}_供应商",
                "测试保质期",
                ["常温", "冷藏", "冷冻", "阴凉干燥"][idx % 4],
                f"{NAMESPACE}_压测数据，可安全清理",
                status,
                active,
                deterministic_id("admin-1"),
            ),
        )


def seed_orders(conn):
    for idx in range(1, 6):
        unit_idx = idx
        unit_id = deterministic_id(f"unit-{unit_idx}")
        user_id = deterministic_id(f"unit-user-{(unit_idx - 1) * 3 + 1}")
        product_id = deterministic_id(f"product-{idx}")
        order_id = deterministic_id(f"order-{idx}")
        order_no = f"SPLOADTEST{idx:06d}"
        conn.execute(
            """
            INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot, note, status, total_cents, created_by)
            VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?)
            ON CONFLICT(order_no) DO NOTHING
            """,
            (
                order_id,
                order_no,
                unit_id,
                f"{NAMESPACE}_测试单位_{unit_idx:02d}",
                f"{NAMESPACE}_配送点_{unit_idx:02d}",
                f"{NAMESPACE}_初始测试订单",
                500 + idx * 100,
                user_id,
            ),
        )
        conn.execute(
            """
            INSERT OR IGNORE INTO order_items(
              id, order_id, product_id, product_name_snapshot, category_snapshot, spec_snapshot,
              unit_snapshot, quantity, price_cents_snapshot, subtotal_cents
            )
            VALUES (?, ?, ?, ?, '蔬菜', '散装', '公斤', '1', ?, ?)
            """,
            (
                deterministic_id(f"order-item-{idx}"),
                order_id,
                product_id,
                f"{NAMESPACE}_测试食材_{idx:03d}",
                500 + idx * 100,
                500 + idx * 100,
            ),
        )


def main():
    parser = argparse.ArgumentParser(description="Seed isolated loadtest data.")
    parser.add_argument("--users-only", action="store_true", help="Only upsert LOADTEST units and users.")
    args = parser.parse_args()
    require_loadtest_environment()
    ensure_dirs()
    password = os.getenv("LOADTEST_USER_PASSWORD", "")
    if len(password) < 8:
        raise SystemExit("LOADTEST_USER_PASSWORD must be set and at least 8 characters")
    init_db()
    with connect() as conn:
        seed_units(conn)
        seed_users(conn, password)
        if not args.users_only:
            seed_products(conn)
            seed_orders(conn)
        conn.commit()
        counts = {
            "units": conn.execute("SELECT COUNT(*) AS c FROM units WHERE unit_code LIKE 'LOADTEST_%'").fetchone()["c"],
            "users": conn.execute("SELECT COUNT(*) AS c FROM users WHERE username LIKE 'LOADTEST_%'").fetchone()["c"],
            "products": conn.execute("SELECT COUNT(*) AS c FROM products WHERE product_code LIKE 'LOADTEST_%'").fetchone()["c"],
            "orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE order_no LIKE 'SPLOADTEST%'").fetchone()["c"],
        }
    print(counts)


if __name__ == "__main__":
    main()
