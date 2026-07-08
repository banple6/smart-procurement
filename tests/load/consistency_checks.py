from __future__ import annotations

import os
import sqlite3
import sys


def main():
    db_path = os.getenv("LOADTEST_DB_PATH")
    if not db_path:
        raise SystemExit("LOADTEST_DB_PATH is required")
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    checks = {
        "integrity_check": conn.execute("PRAGMA integrity_check").fetchone()[0],
        "negative_stock": conn.execute("SELECT COUNT(*) AS c FROM products WHERE CAST(stock_quantity AS REAL) < 0").fetchone()["c"],
        "duplicate_order_no": conn.execute(
            "SELECT COUNT(*) AS c FROM (SELECT order_no FROM orders GROUP BY order_no HAVING COUNT(*) > 1)"
        ).fetchone()["c"],
        "loadtest_orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE order_no LIKE 'SPLOADTEST%' OR note LIKE 'LOADTEST_%'").fetchone()["c"],
    }
    print(checks)
    if checks["integrity_check"] != "ok" or checks["negative_stock"] or checks["duplicate_order_no"]:
        sys.exit(1)


if __name__ == "__main__":
    main()
