from __future__ import annotations

import argparse
import hashlib
import json
import sqlite3
from datetime import datetime
from pathlib import Path

SERVER_ROOT = Path(__file__).resolve().parents[1]
REPORT_ROOT = SERVER_ROOT.parent / "reports"

DELETE_ORDER = (
    "web_login_challenges",
    "qr_login_challenges",
    "sessions",
    "web_sessions",
    "order_shipping_photos",
    "receipt_issue_photos",
    "receipt_issues",
    "order_items",
    "order_logs",
    "orders",
    "inventory_logs",
    "product_price_logs",
    "products",
    "users",
    "units",
    "registration_invites",
    "invites",
    "notifications",
)


def table_exists(conn: sqlite3.Connection, table: str) -> bool:
    row = conn.execute("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", (table,)).fetchone()
    return row is not None


def table_count(conn: sqlite3.Connection, table: str) -> int:
    if not table_exists(conn, table):
        return 0
    return conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def backup_database(database: Path, backup_dir: Path) -> dict:
    backup_dir.mkdir(parents=True, exist_ok=True)
    stamp = datetime.now().strftime("%Y%m%d-%H%M%S")
    backup_path = backup_dir / f"public-beta-cleanup-{stamp}.db"
    source = sqlite3.connect(database)
    target = sqlite3.connect(backup_path)
    try:
        source.backup(target)
    finally:
        target.close()
        source.close()
    return {"path": str(backup_path), "sha256": sha256(backup_path)}


def normalize_allowlist(raw: dict) -> dict[str, list[str]]:
    allowlist = {}
    for table, ids in raw.items():
        if table not in DELETE_ORDER:
            continue
        if not isinstance(ids, list):
            continue
        clean_ids = [str(item).strip() for item in ids if str(item).strip()]
        if clean_ids:
            allowlist[table] = sorted(set(clean_ids))
    return allowlist


def load_allowlist(path: Path | None) -> dict[str, list[str]]:
    if not path:
        return {}
    payload = json.loads(path.read_text(encoding="utf-8"))
    if "delete" in payload and isinstance(payload["delete"], dict):
        payload = payload["delete"]
    return normalize_allowlist(payload)


def delete_ids(conn: sqlite3.Connection, table: str, ids: list[str]) -> int:
    if not ids or not table_exists(conn, table):
        return 0
    placeholders = ",".join("?" for _ in ids)
    if table == "orders":
        conn.execute(f"DELETE FROM order_shipping_photos WHERE order_id IN ({placeholders})", ids)
        conn.execute(f"DELETE FROM receipt_issues WHERE order_id IN ({placeholders})", ids)
        conn.execute(f"DELETE FROM order_items WHERE order_id IN ({placeholders})", ids)
        conn.execute(f"DELETE FROM order_logs WHERE order_id IN ({placeholders})", ids)
    if table == "products":
        conn.execute(f"DELETE FROM inventory_logs WHERE product_id IN ({placeholders})", ids)
        conn.execute(f"DELETE FROM product_price_logs WHERE product_id IN ({placeholders})", ids)
    if table == "users":
        conn.execute(f"DELETE FROM sessions WHERE user_id IN ({placeholders})", ids)
        conn.execute(f"DELETE FROM web_sessions WHERE user_id IN ({placeholders})", ids)
    before = conn.total_changes
    conn.execute(f"DELETE FROM {table} WHERE id IN ({placeholders})", ids)
    return conn.total_changes - before


def main():
    parser = argparse.ArgumentParser(description="Cleanup public beta test data by explicit ID allowlist.")
    parser.add_argument("--database", default=str(SERVER_ROOT / "data" / "smart_procurement.db"))
    parser.add_argument("--allowlist-json", default="")
    parser.add_argument("--backup-dir", default=str(REPORT_ROOT / "db-backups"))
    parser.add_argument("--execute", action="store_true")
    parser.add_argument("--confirm", default="")
    args = parser.parse_args()

    database = Path(args.database).resolve()
    allowlist = load_allowlist(Path(args.allowlist_json).resolve() if args.allowlist_json else None)
    report = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "database": str(database),
        "dry_run": not args.execute,
        "allowlist": allowlist,
        "backup": None,
        "before_counts": {},
        "deleted_counts": {},
        "after_counts": {},
        "integrity_check": None,
        "quick_check": None,
    }
    if not database.exists():
        raise SystemExit(f"Database not found: {database}")
    conn = sqlite3.connect(database)
    try:
        for table in DELETE_ORDER:
            report["before_counts"][table] = table_count(conn, table)
        if args.execute:
            if args.confirm != "DELETE_PUBLIC_BETA_TEST_DATA":
                raise SystemExit("Refusing execute without --confirm DELETE_PUBLIC_BETA_TEST_DATA")
            if not allowlist:
                raise SystemExit("Refusing execute without non-empty --allowlist-json")
            report["backup"] = backup_database(database, Path(args.backup_dir).resolve())
            conn.execute("BEGIN IMMEDIATE")
            for table in DELETE_ORDER:
                report["deleted_counts"][table] = delete_ids(conn, table, allowlist.get(table, []))
            conn.commit()
        else:
            report["deleted_counts"] = {table: len(ids) for table, ids in allowlist.items()}
        for table in DELETE_ORDER:
            report["after_counts"][table] = table_count(conn, table)
        report["integrity_check"] = conn.execute("PRAGMA integrity_check").fetchone()[0]
        report["quick_check"] = conn.execute("PRAGMA quick_check").fetchone()[0]
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    REPORT_ROOT.mkdir(parents=True, exist_ok=True)
    out = REPORT_ROOT / "public-beta-data-cleanup-report.json"
    out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps({"dry_run": report["dry_run"], "report": str(out), "deleted_counts": report["deleted_counts"]}, ensure_ascii=False))


if __name__ == "__main__":
    main()
