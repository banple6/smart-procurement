from __future__ import annotations

import argparse

from app.database import connect

from loadtest_common import require_loadtest_environment


def counts(conn) -> dict[str, int]:
    return {
        "order_items": conn.execute(
            "SELECT COUNT(*) AS c FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE 'SPLOADTEST%')"
        ).fetchone()["c"],
        "orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE order_no LIKE 'SPLOADTEST%'").fetchone()["c"],
        "products": conn.execute("SELECT COUNT(*) AS c FROM products WHERE product_code LIKE 'LOADTEST_%'").fetchone()["c"],
        "users": conn.execute("SELECT COUNT(*) AS c FROM users WHERE username LIKE 'LOADTEST_%'").fetchone()["c"],
        "units": conn.execute("SELECT COUNT(*) AS c FROM units WHERE unit_code LIKE 'LOADTEST_%'").fetchone()["c"],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--execute", action="store_true", help="actually delete LOADTEST data")
    args = parser.parse_args()

    require_loadtest_environment()
    with connect() as conn:
        before = counts(conn)
        print({"dry_run": not args.execute, "before": before})
        if not args.execute:
            return
        conn.execute("BEGIN IMMEDIATE")
        conn.execute("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE 'SPLOADTEST%')")
        conn.execute("DELETE FROM order_logs WHERE order_id IN (SELECT id FROM orders WHERE order_no LIKE 'SPLOADTEST%')")
        conn.execute("DELETE FROM orders WHERE order_no LIKE 'SPLOADTEST%'")
        conn.execute("DELETE FROM inventory_logs WHERE product_id IN (SELECT id FROM products WHERE product_code LIKE 'LOADTEST_%')")
        conn.execute("DELETE FROM product_price_logs WHERE product_id IN (SELECT id FROM products WHERE product_code LIKE 'LOADTEST_%')")
        conn.execute("DELETE FROM products WHERE product_code LIKE 'LOADTEST_%'")
        conn.execute("DELETE FROM sessions WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'LOADTEST_%')")
        conn.execute("DELETE FROM web_sessions WHERE user_id IN (SELECT id FROM users WHERE username LIKE 'LOADTEST_%')")
        conn.execute("DELETE FROM users WHERE username LIKE 'LOADTEST_%'")
        conn.execute("DELETE FROM units WHERE unit_code LIKE 'LOADTEST_%'")
        conn.commit()
        after = counts(conn)
        integrity = conn.execute("PRAGMA integrity_check").fetchone()[0]
    print({"after": after, "integrity_check": integrity})


if __name__ == "__main__":
    main()
