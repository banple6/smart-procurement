from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
from pathlib import Path

SERVER_ROOT = Path(__file__).resolve().parents[1]
if str(SERVER_ROOT) not in sys.path:
    sys.path.insert(0, str(SERVER_ROOT))

from app.services.customer_unit_codes import apply_customer_unit_codes, preview_customer_unit_codes


def database_path(value: str | None) -> Path:
    return Path(value or os.getenv("DATABASE_PATH", SERVER_ROOT / "data" / "smart_procurement.db")).resolve()


def main() -> int:
    parser = argparse.ArgumentParser(description="Preview or backfill the 21 customer unit codes")
    parser.add_argument("--database", help="SQLite database path; defaults to DATABASE_PATH")
    parser.add_argument("--execute", action="store_true", help="apply only when every mapping is MATCHED")
    args = parser.parse_args()

    path = database_path(args.database)
    if not path.exists():
        raise SystemExit(f"数据库不存在: {path}")
    conn = sqlite3.connect(path)
    conn.row_factory = sqlite3.Row
    try:
        preview = preview_customer_unit_codes(conn)
        print(json.dumps({"database": str(path), "execute": args.execute, "items": [item.payload() for item in preview]}, ensure_ascii=False, indent=2))
        if not args.execute:
            return 0
        conn.execute("BEGIN IMMEDIATE")
        try:
            updated = apply_customer_unit_codes(conn, preview)
            conn.commit()
        except Exception:
            conn.rollback()
            raise
        print(json.dumps({"updated": updated, "status": "BACKFILL_COMPLETE"}, ensure_ascii=False))
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    raise SystemExit(main())
