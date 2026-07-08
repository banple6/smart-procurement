from __future__ import annotations

import argparse
import csv
import json
import os
import sqlite3
from datetime import datetime
from pathlib import Path

SERVER_ROOT = Path(__file__).resolve().parents[1]
REPORT_ROOT = SERVER_ROOT.parent / "reports"

TEST_PATTERNS = (
    "LOADTEST_",
    "E2E_",
    "BUGTEST_",
    "测试",
    "演示",
    "Demo",
    "debug",
    "staging",
    "loadtest",
)

TABLE_SCANS = {
    "units": ["id", "unit_code", "unit_name", "default_delivery_point"],
    "users": ["id", "username", "display_name", "role", "unit_id"],
    "products": ["id", "product_code", "name", "category", "spec"],
    "orders": ["id", "order_no", "unit_name_snapshot", "delivery_point_snapshot", "note", "status"],
    "order_items": ["id", "order_id", "product_code_snapshot", "product_name_snapshot", "category_snapshot", "spec_snapshot"],
    "inventory_logs": ["id", "product_id", "action", "detail"],
    "sessions": ["id", "user_id", "device_name", "device_id"],
    "web_sessions": ["id", "user_id", "browser_name", "browser_os", "browser_ip"],
    "invites": ["id", "code", "unit_id", "note"],
    "registration_invites": ["id", "code", "unit_id", "note"],
    "app_releases": ["id", "channel", "package_name", "title", "release_notes", "apk_storage_key"],
    "notifications": ["id", "title", "message", "target_role", "target_unit_id"],
    "web_login_challenges": ["id", "challenge_code", "status", "browser_name", "browser_os"],
    "qr_login_challenges": ["id", "challenge_code", "status", "browser_name", "browser_os"],
}

FILE_SCANS = {
    "uploads": "UPLOAD_DIR",
    "private_uploads": "PRIVATE_UPLOAD_DIR",
    "backups": "BACKUP_DIR",
    "releases": "APP_UPDATE_RELEASE_DIR",
}


def default_database_path() -> Path:
    return Path(os.getenv("DATABASE_PATH", SERVER_ROOT / "data" / "smart_procurement.db")).resolve()


def env_path(key: str, fallback: Path) -> Path:
    return Path(os.getenv(key, str(fallback))).resolve()


def row_to_dict(row: sqlite3.Row) -> dict:
    return {key: row[key] for key in row.keys()}


def table_exists(conn: sqlite3.Connection, table: str) -> bool:
    row = conn.execute("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", (table,)).fetchone()
    return row is not None


def table_columns(conn: sqlite3.Connection, table: str) -> set[str]:
    return {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}


def match_reason(values: list[str]) -> str:
    combined = "\n".join(str(value or "") for value in values)
    hits = [pattern for pattern in TEST_PATTERNS if pattern.lower() in combined.lower()]
    return ", ".join(hits)


def scan_table(conn: sqlite3.Connection, table: str, preferred_columns: list[str], limit: int) -> list[dict]:
    if not table_exists(conn, table):
        return []
    columns = table_columns(conn, table)
    selected = [column for column in preferred_columns if column in columns]
    text_columns = [column for column in selected if column != "id"]
    if not selected or not text_columns:
        return []
    params = [f"%{pattern.lower()}%" for column in text_columns for pattern in TEST_PATTERNS]
    where = " OR ".join(
        [f"LOWER(COALESCE(CAST({column} AS TEXT), '')) LIKE ?" for column in text_columns for _ in TEST_PATTERNS]
    )
    rows = conn.execute(f"SELECT {', '.join(selected)} FROM {table} WHERE {where} LIMIT ?", (*params, limit)).fetchall()
    result = []
    for row in rows:
        payload = row_to_dict(row)
        payload["_table"] = table
        payload["_reason"] = match_reason([payload.get(column, "") for column in text_columns])
        payload["_recommended_action"] = "人工确认后再清理" if table in {"orders", "order_items", "users", "products", "units"} else "可按白名单清理"
        result.append(payload)
    return result


def scan_files(label: str, path: Path, limit: int) -> list[dict]:
    if not path.exists():
        return []
    result = []
    for item in path.rglob("*"):
        if len(result) >= limit:
            break
        if not item.is_file():
            continue
        relative = str(item.relative_to(path))
        reason = match_reason([relative])
        if not reason:
            continue
        stat = item.stat()
        result.append(
            {
                "_table": label,
                "path": relative,
                "size_bytes": stat.st_size,
                "updated_at": datetime.fromtimestamp(stat.st_mtime).isoformat(timespec="seconds"),
                "_reason": reason,
                "_recommended_action": "人工确认后再清理文件",
            }
        )
    return result


def write_reports(audit: dict, output_dir: Path):
    output_dir.mkdir(parents=True, exist_ok=True)
    json_path = output_dir / "public-beta-data-audit.json"
    csv_path = output_dir / "public-beta-data-audit.csv"
    md_path = output_dir / "public-beta-data-audit.md"
    json_path.write_text(json.dumps(audit, ensure_ascii=False, indent=2), encoding="utf-8")
    rows = []
    for section, items in audit["findings"].items():
        for item in items:
            rows.append(
                {
                    "section": section,
                    "id": item.get("id", ""),
                    "label": item.get("unit_code")
                    or item.get("username")
                    or item.get("product_code")
                    or item.get("order_no")
                    or item.get("path")
                    or item.get("title")
                    or "",
                    "reason": item.get("_reason", ""),
                    "recommended_action": item.get("_recommended_action", ""),
                }
            )
    with csv_path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=["section", "id", "label", "reason", "recommended_action"])
        writer.writeheader()
        writer.writerows(rows)
    lines = [
        "# 公测测试数据审计",
        "",
        f"- 生成时间：{audit['generated_at']}",
        f"- 数据库：`{audit['database_path']}`",
        f"- 数据库存在：{audit['database_exists']}",
        f"- 环境：{audit['environment']}",
        f"- integrity_check：{audit['checks'].get('integrity_check', '未执行')}",
        f"- quick_check：{audit['checks'].get('quick_check', '未执行')}",
        "",
        "## 汇总",
    ]
    for section, items in audit["findings"].items():
        lines.append(f"- {section}: {len(items)}")
    lines.extend(["", "## 明细"])
    for section, items in audit["findings"].items():
        lines.extend(["", f"### {section}"])
        if not items:
            lines.append("未发现疑似测试数据。")
            continue
        for item in items:
            label = item.get("unit_code") or item.get("username") or item.get("product_code") or item.get("order_no") or item.get("path") or item.get("title") or item.get("id")
            lines.append(f"- `{item.get('id', '')}` {label}；原因：{item.get('_reason', '')}；建议：{item.get('_recommended_action', '')}")
    md_path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def build_audit(database: Path, limit: int) -> dict:
    audit = {
        "generated_at": datetime.now().isoformat(timespec="seconds"),
        "environment": os.getenv("APP_ENV", "development"),
        "database_path": str(database),
        "database_exists": database.exists(),
        "checks": {},
        "findings": {},
    }
    if database.exists():
        conn = sqlite3.connect(database)
        conn.row_factory = sqlite3.Row
        try:
            audit["checks"]["integrity_check"] = conn.execute("PRAGMA integrity_check").fetchone()[0]
            audit["checks"]["quick_check"] = conn.execute("PRAGMA quick_check").fetchone()[0]
            for table, columns in TABLE_SCANS.items():
                audit["findings"][table] = scan_table(conn, table, columns, limit)
        finally:
            conn.close()
    else:
        for table in TABLE_SCANS:
            audit["findings"][table] = []
    audit["findings"]["loadtest_environment"] = [
        {
            "_table": "loadtest_environment",
            "DATABASE_PATH": os.getenv("LOADTEST_DATABASE_PATH", ""),
            "DATA_NAMESPACE": os.getenv("DATA_NAMESPACE", ""),
            "LOAD_TEST_ALLOWED": os.getenv("LOAD_TEST_ALLOWED", ""),
            "_reason": "环境变量记录",
            "_recommended_action": "确认压测环境与正式环境隔离",
        }
    ]
    default_dirs = {
        "uploads": SERVER_ROOT / "uploads",
        "private_uploads": SERVER_ROOT / "private_uploads",
        "backups": SERVER_ROOT / "backups",
        "releases": SERVER_ROOT / "releases",
    }
    for label, env_key in FILE_SCANS.items():
        audit["findings"][label] = scan_files(label, env_path(env_key, default_dirs[label]), limit)
    return audit


def main():
    parser = argparse.ArgumentParser(description="Audit likely test data before public beta.")
    parser.add_argument("--database", default=str(default_database_path()))
    parser.add_argument("--output-dir", default=str(REPORT_ROOT))
    parser.add_argument("--limit", type=int, default=500)
    args = parser.parse_args()
    audit = build_audit(Path(args.database).resolve(), args.limit)
    write_reports(audit, Path(args.output_dir).resolve())
    print(json.dumps({"database": audit["database_path"], "findings": {key: len(value) for key, value in audit["findings"].items()}}, ensure_ascii=False))


if __name__ == "__main__":
    main()
