from app.database import connect, init_db

from loadtest_common import require_loadtest_environment


def main():
    require_loadtest_environment()
    init_db()
    with connect() as conn:
        checks = {
            "integrity_check": conn.execute("PRAGMA integrity_check").fetchone()[0],
            "quick_check": conn.execute("PRAGMA quick_check").fetchone()[0],
            "journal_mode": conn.execute("PRAGMA journal_mode").fetchone()[0],
            "foreign_keys": conn.execute("PRAGMA foreign_keys").fetchone()[0],
            "units": conn.execute("SELECT COUNT(*) AS c FROM units WHERE unit_code LIKE 'LOADTEST_%'").fetchone()["c"],
            "users": conn.execute("SELECT COUNT(*) AS c FROM users WHERE username LIKE 'LOADTEST_%'").fetchone()["c"],
            "products": conn.execute("SELECT COUNT(*) AS c FROM products WHERE product_code LIKE 'LOADTEST_%'").fetchone()["c"],
            "orders": conn.execute(
                """
                SELECT COUNT(*) AS c
                FROM orders o
                JOIN units u ON u.id = o.unit_id
                WHERE u.unit_code LIKE 'LOADTEST_%'
                """
            ).fetchone()["c"],
            "negative_stock": conn.execute("SELECT COUNT(*) AS c FROM products WHERE CAST(stock_quantity AS REAL) < 0").fetchone()["c"],
        }
    print(checks)


if __name__ == "__main__":
    main()
