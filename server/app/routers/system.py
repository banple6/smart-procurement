import hashlib
import json
import os
import sqlite3
import tarfile
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import APIRouter, Body, Depends, Header, HTTPException, Query

from ..database import all_rows, backup_dir, connect, database_path, private_upload_dir, upload_dir, write_audit
from ..dependencies import current_user, require_manage_backups, require_system_status
from ..metrics import request_snapshot, uptime_seconds
from ..security import hash_token

router = APIRouter(prefix="/admin/system", tags=["system"])


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def dir_size(path: Path) -> int:
    if not path.exists():
        return 0
    return sum(file.stat().st_size for file in path.rglob("*") if file.is_file())


def safe_storage_status() -> dict:
    root = Path(database_path()).parent
    usage = os.statvfs(root)
    total = usage.f_blocks * usage.f_frsize
    free = usage.f_bavail * usage.f_frsize
    used = max(0, total - free)
    percent = int((used / total) * 100) if total else 0
    return {
        "scope": "container",
        "disk_total_mb": total // 1024 // 1024,
        "disk_used_mb": used // 1024 // 1024,
        "disk_free_mb": free // 1024 // 1024,
        "disk_used_percent": percent,
        "database_mb": Path(database_path()).stat().st_size // 1024 // 1024 if Path(database_path()).exists() else 0,
        "uploads_mb": dir_size(Path(upload_dir())) // 1024 // 1024,
        "private_uploads_mb": dir_size(Path(private_upload_dir())) // 1024 // 1024,
    }


def read_int_file(path: Path) -> int | None:
    try:
        value = path.read_text(encoding="utf-8").strip()
        if not value or value == "max":
            return None
        return int(value)
    except (OSError, ValueError):
        return None


def container_memory() -> tuple[int, int]:
    current = read_int_file(Path("/sys/fs/cgroup/memory.current"))
    maximum = read_int_file(Path("/sys/fs/cgroup/memory.max"))
    if current is not None and maximum:
        return current, maximum
    mem_total = 0
    mem_available = 0
    try:
        for line in Path("/proc/meminfo").read_text(encoding="utf-8").splitlines():
            if line.startswith("MemTotal:"):
                mem_total = int(line.split()[1]) * 1024
            elif line.startswith("MemAvailable:"):
                mem_available = int(line.split()[1]) * 1024
    except OSError:
        pass
    if mem_total:
        return max(0, mem_total - mem_available), mem_total
    return 0, 0


def estimated_cpu_percent() -> float:
    try:
        cores = os.cpu_count() or 1
        return round(min(100.0, max(0.0, os.getloadavg()[0] / cores * 100)), 1)
    except (AttributeError, OSError):
        return 0.0


def database_total_bytes() -> int:
    base = Path(database_path())
    return sum(path.stat().st_size for path in [base, Path(str(base) + "-wal"), Path(str(base) + "-shm")] if path.exists())


def resources_payload() -> dict:
    storage = safe_storage_status()
    memory_used, memory_total = container_memory()
    return {
        "scope": "container",
        "cpu_percent": estimated_cpu_percent(),
        "memory_used_bytes": memory_used,
        "memory_total_bytes": memory_total,
        "disk_used_bytes": storage["disk_used_mb"] * 1024 * 1024,
        "disk_total_bytes": storage["disk_total_mb"] * 1024 * 1024,
        "disk_free_bytes": storage["disk_free_mb"] * 1024 * 1024,
    }


def service_statuses(conn) -> dict:
    database_status = "healthy"
    try:
        conn.execute("SELECT 1").fetchone()
    except sqlite3.Error:
        database_status = "error"
    uploads_status = "healthy" if Path(upload_dir()).exists() and os.access(upload_dir(), os.W_OK) else "error"
    backup_status = "healthy" if Path(backup_dir()).exists() and os.access(backup_dir(), os.W_OK) else "error"
    sms_provider = os.getenv("SMS_PROVIDER", "disabled").lower()
    if sms_provider == "disabled":
        sms = "disabled"
    elif sms_provider == "aliyun":
        sms = "healthy" if os.getenv("ALIYUN_SMS_ACCESS_KEY_ID") and os.getenv("ALIYUN_SMS_TEMPLATE_CODE") else "unconfigured"
    else:
        sms = "unconfigured"
    return {
        "api": "healthy",
        "database": database_status,
        "uploads": uploads_status,
        "backup": backup_status,
        "web": "healthy",
        "sms": sms,
    }


def latest_backup_payload(conn) -> dict | None:
    latest = conn.execute(
        """
        SELECT id, backup_type, status, started_at, finished_at, size_bytes, verify_status, verified_at,
               app_version, database_version, offsite_synced
        FROM backup_runs
        ORDER BY started_at DESC
        LIMIT 1
        """
    ).fetchone()
    if not latest:
        return None
    return {
        "id": latest["id"],
        "status": latest["status"],
        "created_at": latest["started_at"],
        "finished_at": latest["finished_at"] or "",
        "size_bytes": latest["size_bytes"],
        "verified": latest["verify_status"] == "success",
        "verified_at": latest["verified_at"] or "",
        "offsite_synced": bool(latest["offsite_synced"]),
        "app_version": latest["app_version"] or "",
        "database_version": latest["database_version"] or "",
    }


def backup_summary(conn) -> dict:
    latest = latest_backup_payload(conn)
    if not latest:
        return {"latest": None, "rpo_hours": 6, "rto_minutes": 60, "status": "missing"}
    return {
        "latest": {
            "id": latest["id"],
            "backup_type": "manual",
            "status": latest["status"],
            "started_at": latest["created_at"],
            "finished_at": latest["finished_at"],
            "size_bytes": latest["size_bytes"],
            "verify_status": "success" if latest["verified"] else "failed",
            "verified_at": latest["verified_at"],
        },
        "rpo_hours": 6,
        "rto_minutes": 60,
        "status": "ok" if latest["status"] == "success" and latest["verified"] else "warning",
    }


def upsert_alert(conn, alert_key: str, severity: str, title: str, message: str):
    existing = conn.execute("SELECT id FROM system_alerts WHERE alert_key = ?", (alert_key,)).fetchone()
    if existing:
        conn.execute(
            """
            UPDATE system_alerts
            SET severity = ?, title = ?, message = ?, last_seen_at = CURRENT_TIMESTAMP, hit_count = hit_count + 1, status = 'open'
            WHERE alert_key = ?
            """,
            (severity, title, message, alert_key),
        )
    else:
        conn.execute(
            "INSERT INTO system_alerts(id, alert_key, severity, title, message) VALUES (?, ?, ?, ?, ?)",
            (str(uuid4()), alert_key, severity, title, message),
        )


def status_level(resources: dict, performance: dict, services: dict, latest_backup: dict | None) -> str:
    disk_percent = (resources["disk_used_bytes"] / resources["disk_total_bytes"] * 100) if resources["disk_total_bytes"] else 0
    memory_percent = (resources["memory_used_bytes"] / resources["memory_total_bytes"] * 100) if resources["memory_total_bytes"] else 0
    critical = disk_percent >= float(os.getenv("DISK_CRITICAL_PERCENT", "90")) or memory_percent >= float(os.getenv("MEMORY_CRITICAL_PERCENT", "90"))
    critical = critical or services["database"] == "error" or services["uploads"] == "error"
    if critical:
        return "critical"
    warning = disk_percent >= float(os.getenv("DISK_WARNING_PERCENT", "75")) or memory_percent >= float(os.getenv("MEMORY_WARNING_PERCENT", "75"))
    warning = warning or resources["cpu_percent"] >= float(os.getenv("CPU_WARNING_PERCENT", "70"))
    warning = warning or performance["p95_latency_ms"] >= 1000 or performance["error_rate_percent"] >= 5
    warning = warning or latest_backup is None or (latest_backup and not latest_backup["verified"])
    warning = warning or os.getenv("WEB_PUBLIC_ORIGIN", "").startswith("http://")
    return "warning" if warning else "healthy"


def capacity_payload(resources: dict, performance: dict) -> dict:
    disk_free = resources.get("disk_free_bytes", max(0, resources["disk_total_bytes"] - resources["disk_used_bytes"]))
    memory_percent = (resources["memory_used_bytes"] / resources["memory_total_bytes"] * 100) if resources["memory_total_bytes"] else 0
    disk_percent = (resources["disk_used_bytes"] / resources["disk_total_bytes"] * 100) if resources["disk_total_bytes"] else 0
    risk = performance["p95_latency_ms"] >= 1000 or performance["error_rate_percent"] >= 5 or disk_percent >= 90 or memory_percent >= 90
    moderate = performance["p95_latency_ms"] >= 500 or disk_percent >= 75 or memory_percent >= 75 or disk_free < 5 * 1024 * 1024 * 1024
    if risk:
        status = "risk"
        summary = "近期负载或资源指标存在性能风险，建议先排查后再扩大使用范围"
    elif moderate:
        status = "moderate"
        summary = "当前容量余量一般，请关注磁盘、内存和接口延迟变化"
    else:
        status = "sufficient"
        summary = "最近 24 小时运行平稳，当前用户规模下暂未发现性能瓶颈"
    return {
        "status": status,
        "summary": summary,
        "recent_concurrent_peak_15m": performance["request_count_5m"],
        "api_p95_latency_ms": performance["p95_latency_ms"],
        "cpu_peak_percent": resources["cpu_percent"],
        "memory_peak_percent": round(memory_percent, 1),
        "disk_free_bytes": disk_free,
        "sqlite_lock_count_24h": performance["sqlite_lock_count_24h"],
        "error_rate_percent": performance["error_rate_percent"],
        "disclaimer": "该结论基于近期实际负载，不等同于压力测试结果",
    }


def alert_payloads(conn, resources: dict, performance: dict, latest_backup: dict | None) -> list[dict]:
    disk_percent = (resources["disk_used_bytes"] / resources["disk_total_bytes"] * 100) if resources["disk_total_bytes"] else 0
    memory_percent = (resources["memory_used_bytes"] / resources["memory_total_bytes"] * 100) if resources["memory_total_bytes"] else 0
    if disk_percent >= float(os.getenv("DISK_WARNING_PERCENT", "75")):
        upsert_alert(conn, "disk_capacity", "warning", "磁盘空间需要关注", f"磁盘使用率已达到 {disk_percent:.1f}%")
    if memory_percent >= float(os.getenv("MEMORY_WARNING_PERCENT", "75")):
        upsert_alert(conn, "memory_capacity", "warning", "内存使用需要关注", f"内存使用率已达到 {memory_percent:.1f}%")
    if latest_backup is None:
        upsert_alert(conn, "backup_missing", "warning", "尚无成功备份", "请创建并验证一次完整备份")
    elif not latest_backup["verified"]:
        upsert_alert(conn, "backup_unverified", "warning", "最近备份未通过校验", "请重新验证或创建新的备份")
    if os.getenv("BACKUP_OFFSITE_ENABLED", "false").lower() != "true":
        upsert_alert(conn, "offsite_backup_disabled", "info", "异地备份未启用", "当前仅使用本地备份，请评估 OSS 异地备份")
    if os.getenv("WEB_PUBLIC_ORIGIN", "").startswith("http://"):
        upsert_alert(conn, "https_not_ready", "warning", "HTTPS 未配置完成", "正式启用短信注册和远程恢复前必须配置 HTTPS")
    if performance["sqlite_lock_count_24h"] > 0:
        upsert_alert(conn, "sqlite_locked", "warning", "数据库写入曾发生等待", "请观察高峰期订单提交和管理操作是否变慢")
    rows = all_rows(
        conn,
        """
        SELECT severity, title, message, last_seen_at, hit_count
        FROM system_alerts
        WHERE status = 'open'
        ORDER BY CASE severity WHEN 'critical' THEN 0 WHEN 'warning' THEN 1 ELSE 2 END, last_seen_at DESC
        LIMIT 10
        """,
    )
    return [
        {
            "level": row["severity"],
            "title": row["title"],
            "occurred_at": row["last_seen_at"],
            "impact": row["message"],
            "suggestion": "请在业务低峰期处理并完成复查",
            "hit_count": row["hit_count"],
        }
        for row in rows
    ]


def business_stats(conn) -> dict:
    return {
        "active_users": conn.execute("SELECT COUNT(*) AS c FROM users WHERE active = 1").fetchone()["c"],
        "active_units": conn.execute("SELECT COUNT(*) AS c FROM units WHERE active = 1").fetchone()["c"],
        "active_products": conn.execute("SELECT COUNT(*) AS c FROM products WHERE active = 1 AND is_deleted = 0").fetchone()["c"],
        "open_orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE status NOT IN ('completed', 'cancelled')").fetchone()["c"],
        "today_orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE date(created_at) = date('now')").fetchone()["c"],
        "pending_orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE status = 'pending'").fetchone()["c"],
        "preparing_orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE status = 'preparing'").fetchone()["c"],
        "shipped_orders": conn.execute("SELECT COUNT(*) AS c FROM orders WHERE status = 'shipped'").fetchone()["c"],
        "open_receipt_issues": conn.execute("SELECT COUNT(*) AS c FROM receipt_issues WHERE status = 'open'").fetchone()["c"],
    }


def overview_payload(conn, include_detail: bool) -> dict:
    resources = resources_payload()
    performance = request_snapshot()
    sessions = {
        "active_app_sessions": conn.execute("SELECT COUNT(*) AS c FROM sessions WHERE revoked_at IS NULL").fetchone()["c"],
        "active_web_sessions": conn.execute("SELECT COUNT(*) AS c FROM web_sessions WHERE revoked_at IS NULL").fetchone()["c"]
        if conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='web_sessions'").fetchone()
        else 0,
    }
    storage = {
        "database_bytes": database_total_bytes(),
        "product_images_bytes": dir_size(Path(upload_dir())),
        "shipping_photos_bytes": dir_size(Path(private_upload_dir()) / "shipping"),
        "receipt_issue_photos_bytes": dir_size(Path(private_upload_dir()) / "receipt_issues"),
        "backups_bytes": dir_size(Path(backup_dir())),
    }
    services = service_statuses(conn)
    latest_backup = latest_backup_payload(conn)
    capacity = capacity_payload(resources, performance)
    alerts = alert_payloads(conn, resources, performance, latest_backup)
    stats = business_stats(conn)
    overall_status = status_level(resources, performance, services, latest_backup)
    payload = {
        "overall_status": overall_status,
        "checked_at": conn.execute("SELECT CURRENT_TIMESTAMP AS now").fetchone()["now"],
        "uptime_seconds": uptime_seconds(),
        "resources": resources,
        "performance": performance,
        "sessions": sessions,
        "storage": storage,
        "services": services,
        "capacity": capacity,
        "latest_backup": latest_backup
        or {
            "status": "missing",
            "created_at": "",
            "size_bytes": 0,
            "verified": False,
            "offsite_synced": False,
            "app_version": "",
            "database_version": "",
        },
        "alerts": alerts,
        "data_freshness": {"orders": "实时", "products": "实时", "backup_rpo_hours": 6},
        "api": {"status": "ok"},
        "database": {"status": "ok", "journal_mode": "WAL"},
        "backup": {"latest": latest_backup, "rpo_hours": 6, "rto_minutes": 60, "status": "ok" if latest_backup and latest_backup["verified"] else "warning"},
        "restore": {
            "android_restore_allowed": os.getenv("ALLOW_REMOTE_RESTORE", "false").lower() == "true",
            "production_restore_requires": "系统管理员权限、二次验证、确认文字和恢复前安全备份",
        },
        "business": stats,
        "environment": {
            "https_ready": not os.getenv("WEB_PUBLIC_ORIGIN", "").startswith("http://"),
            "sms_provider": os.getenv("SMS_PROVIDER", "disabled").lower(),
            "remote_restore_enabled": os.getenv("ALLOW_REMOTE_RESTORE", "false").lower() == "true",
            "offsite_backup_enabled": os.getenv("BACKUP_OFFSITE_ENABLED", "false").lower() == "true",
        },
        "today_orders": stats["today_orders"],
        "pending_orders": stats["pending_orders"],
        "preparing_orders": stats["preparing_orders"],
        "shipped_orders": stats["shipped_orders"],
        "active_units": stats["active_units"],
        "active_users": stats["active_users"],
        "products": stats["active_products"],
        "open_receipt_issues": stats["open_receipt_issues"],
        "latest_backup_status": (latest_backup or {}).get("status", "missing"),
        "latest_backup_time": (latest_backup or {}).get("created_at", ""),
    }
    if include_detail:
        recent_hour = conn.execute(
            """
            SELECT request_count, error_count, avg_latency_ms, p95_latency_ms, sqlite_locked_count
            FROM system_metric_hourly
            ORDER BY hour_start DESC LIMIT 1
            """
        ).fetchone()
        payload["hourly_performance"] = {
            "recent_hour": dict(recent_hour)
            if recent_hour
            else {"request_count": 0, "error_count": 0, "avg_latency_ms": 0, "p95_latency_ms": 0, "sqlite_locked_count": 0}
        }
        payload["offsite"] = {"enabled": os.getenv("BACKUP_OFFSITE_ENABLED", "false").lower() == "true", "status": "disabled"}
    return payload


@router.get("/overview")
def system_overview(detail: bool = Query(default=False), admin=Depends(require_system_status)):
    include_detail = detail and bool(admin.get("can_view_detailed_metrics"))
    with connect() as conn:
        payload = overview_payload(conn, include_detail)
        payload["detail_allowed"] = include_detail
        conn.commit()
        return payload


def add_tar_if_exists(archive: tarfile.TarFile, path: Path, arcname: str):
    if path.exists():
        archive.add(path, arcname=arcname)


def database_version() -> str:
    with connect() as conn:
        row = conn.execute("SELECT version FROM schema_migrations ORDER BY version DESC LIMIT 1").fetchone()
        return row["version"] if row else ""


def create_backup_files(backup_id: str, kind: str) -> dict:
    root = Path(backup_dir()) / backup_id
    root.mkdir(parents=True, exist_ok=False)
    db_target = root / "database.sqlite3"
    source = sqlite3.connect(database_path(), timeout=10)
    target = sqlite3.connect(str(db_target), timeout=10)
    try:
        source.backup(target)
    finally:
        target.close()
        source.close()
    uploads_tar = root / "uploads.tar.gz"
    with tarfile.open(uploads_tar, "w:gz") as archive:
        add_tar_if_exists(archive, Path(upload_dir()), "uploads")
    private_tar = root / "private_uploads.tar.gz"
    with tarfile.open(private_tar, "w:gz") as archive:
        add_tar_if_exists(archive, Path(private_upload_dir()), "private_uploads")
    files = [db_target, uploads_tar, private_tar]
    manifest = {
        "backup_id": backup_id,
        "backup_type": kind,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "app_version": os.getenv("APP_VERSION", "1.0.0"),
        "database_version": database_version(),
        "file_count": len(files),
        "uncompressed_size_bytes": database_total_bytes() + dir_size(Path(upload_dir())) + dir_size(Path(private_upload_dir())),
        "compressed_size_bytes": sum(file.stat().st_size for file in files),
        "checksum_status": "success",
        "created_by": "api",
        "offsite_synced": os.getenv("BACKUP_OFFSITE_ENABLED", "false").lower() == "true",
        "files": [{"name": file.name, "size_bytes": file.stat().st_size, "sha256": sha256_file(file)} for file in files],
    }
    manifest_path = root / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    checksum_path = root / "checksums.sha256"
    checksum_items = files + [manifest_path]
    checksum_path.write_text(
        "\n".join(f"{sha256_file(file)}  {file.name}" for file in checksum_items) + "\n",
        encoding="utf-8",
    )
    return {
        "manifest": manifest,
        "size_bytes": sum((root / item["name"]).stat().st_size for item in manifest["files"]) + manifest_path.stat().st_size + checksum_path.stat().st_size,
        "object_count": len(manifest["files"]) + 2,
    }


@router.post("/backups")
def create_backup(admin=Depends(require_manage_backups)):
    backup_id = str(uuid4())
    with connect() as conn:
        conn.execute(
            "INSERT INTO backup_runs(id, backup_type, status, created_by, job_id) VALUES (?, 'manual', 'running', ?, ?)",
            (backup_id, admin["id"], backup_id),
        )
        conn.execute(
            "INSERT INTO backup_jobs(id, backup_id, status, stage, requested_by) VALUES (?, ?, 'running', 'creating_files', ?)",
            (backup_id, backup_id, admin["id"]),
        )
        conn.commit()
    try:
        result = create_backup_files(backup_id, "manual")
        with connect() as conn:
            conn.execute(
                """
                UPDATE backup_runs
                SET status = 'success', finished_at = CURRENT_TIMESTAMP, size_bytes = ?, object_count = ?,
                    manifest_json = ?, verify_status = 'success', verified_at = CURRENT_TIMESTAMP,
                    app_version = ?, database_version = ?, offsite_synced = ?
                WHERE id = ?
                """,
                (
                    result["size_bytes"],
                    result["object_count"],
                    json.dumps(result["manifest"], ensure_ascii=False),
                    result["manifest"]["app_version"],
                    result["manifest"]["database_version"],
                    int(result["manifest"]["offsite_synced"]),
                    backup_id,
                ),
            )
            conn.execute(
                "UPDATE backup_jobs SET status = 'success', stage = 'completed', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                (backup_id,),
            )
            write_audit(conn, admin["id"], admin["role"], "ADMIN_CREATE_BACKUP", "backup_run", backup_id)
            conn.commit()
    except Exception:
        with connect() as conn:
            conn.execute(
                "UPDATE backup_runs SET status = 'failed', finished_at = CURRENT_TIMESTAMP, error_message = '备份失败' WHERE id = ?",
                (backup_id,),
            )
            conn.execute(
                "UPDATE backup_jobs SET status = 'failed', stage = 'failed', error_message = '备份失败', updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                (backup_id,),
            )
            conn.commit()
        raise HTTPException(status_code=500, detail="备份失败，请稍后重试") from None
    with connect() as conn:
        row = conn.execute(
            """
            SELECT id, backup_type, status, started_at, finished_at, size_bytes, verify_status,
                   app_version, database_version, offsite_synced, job_id
            FROM backup_runs
            WHERE id = ?
            """,
            (backup_id,),
        ).fetchone()
        return dict(row)


def verify_backup_id(backup_id: str) -> bool:
    root = Path(backup_dir()) / backup_id
    manifest_path = root / "manifest.json"
    if not manifest_path.exists():
        return False
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    checksum_path = root / "checksums.sha256"
    if checksum_path.exists():
        expected = {}
        for line in checksum_path.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            digest, name = line.split(None, 1)
            expected[name.strip()] = digest
        for name, digest in expected.items():
            path = root / name
            if not path.exists() or sha256_file(path) != digest:
                return False
    for item in manifest.get("files", []):
        path = root / item["name"]
        if not path.exists() or path.stat().st_size != item["size_bytes"] or sha256_file(path) != item["sha256"]:
            return False
    db_path = root / "database.sqlite3"
    if not db_path.exists():
        db_path = root / "database.sqlite"
    if sqlite3.connect(str(db_path)).execute("PRAGMA integrity_check").fetchone()[0] != "ok":
        return False
    return True


@router.post("/backups/{backup_id}/verify")
def verify_backup(backup_id: str, admin=Depends(require_manage_backups)):
    with connect() as conn:
        row = conn.execute("SELECT id FROM backup_runs WHERE id = ?", (backup_id,)).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="备份记录不存在")
    ok = verify_backup_id(backup_id)
    with connect() as conn:
        conn.execute(
            "UPDATE backup_runs SET verify_status = ?, verified_at = CURRENT_TIMESTAMP WHERE id = ?",
            ("success" if ok else "failed", backup_id),
        )
        write_audit(conn, admin["id"], admin["role"], "ADMIN_VERIFY_BACKUP", "backup_run", backup_id, result="success" if ok else "failure")
        conn.commit()
    if not ok:
        raise HTTPException(status_code=409, detail="备份校验失败")
    return {"ok": True, "backup_id": backup_id, "verify_status": "success"}


@router.get("/backups")
def list_backups(admin=Depends(require_system_status)):
    with connect() as conn:
        return {
            "items": all_rows(
                conn,
                """
                SELECT id, backup_type, status, started_at, finished_at, size_bytes, object_count,
                       verify_status, verified_at, app_version, database_version, offsite_synced, job_id
                FROM backup_runs
                ORDER BY started_at DESC
                LIMIT 50
                """,
            )
        }


@router.get("/backup-jobs/{job_id}")
def backup_job(job_id: str, admin=Depends(require_system_status)):
    with connect() as conn:
        row = conn.execute(
            "SELECT id, backup_id, status, stage, created_at, updated_at, error_message FROM backup_jobs WHERE id = ?",
            (job_id,),
        ).fetchone()
        if not row:
            raise HTTPException(status_code=404, detail="备份任务不存在")
        return dict(row)


def require_restore_backup_user(user=Depends(current_user)):
    if user["role"] != "admin" or not bool(user.get("can_restore_backups", 0)):
        raise HTTPException(status_code=403, detail="当前账号无权恢复数据")
    return user


def consume_step_up_token(conn, user_id: str, purpose: str, token: str):
    if not token:
        raise HTTPException(status_code=403, detail="请先完成二次验证")
    row = conn.execute(
        """
        SELECT *
        FROM step_up_tokens
        WHERE token_hash = ?
          AND user_id = ?
          AND purpose = ?
          AND used_at IS NULL
        """,
        (hash_token(token), user_id, purpose),
    ).fetchone()
    if not row:
        raise HTTPException(status_code=403, detail="二次验证已失效")
    expired = conn.execute("SELECT CURRENT_TIMESTAMP > ? AS expired", (row["expires_at"],)).fetchone()["expired"]
    if expired:
        raise HTTPException(status_code=403, detail="二次验证已失效")
    conn.execute("UPDATE step_up_tokens SET used_at = CURRENT_TIMESTAMP WHERE id = ?", (row["id"],))


@router.post("/backups/{backup_id}/restore")
def restore_backup(
    backup_id: str,
    body: dict = Body(default_factory=dict),
    x_step_up_token: str = Header(default="", alias="X-Step-Up-Token"),
    admin=Depends(require_restore_backup_user),
):
    confirm_text = body.get("confirm_text") or body.get("confirmation_text") or ""
    if confirm_text != "确认恢复数据":
        raise HTTPException(status_code=400, detail="确认文字不正确")
    with connect() as conn:
        backup = conn.execute("SELECT id, status, verify_status FROM backup_runs WHERE id = ?", (backup_id,)).fetchone()
        if not backup:
            raise HTTPException(status_code=404, detail="备份记录不存在")
        if backup["status"] != "success" or backup["verify_status"] != "success":
            raise HTTPException(status_code=409, detail="备份未通过校验，不能恢复")
        consume_step_up_token(conn, admin["id"], "restore_backup", x_step_up_token)
        job_id = str(uuid4())
        remote_restore_enabled = os.getenv("ALLOW_REMOTE_RESTORE", "false").lower() == "true"
        conn.execute(
            """
            INSERT INTO restore_jobs(id, backup_id, status, stage, requested_by, error_message)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                job_id,
                backup_id,
                "disabled" if not remote_restore_enabled else "queued",
                "prechecking",
                admin["id"],
                "" if remote_restore_enabled else "当前环境未启用远程恢复",
            ),
        )
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "ADMIN_REQUEST_RESTORE",
            "backup_run",
            backup_id,
            result="blocked" if not remote_restore_enabled else "queued",
        )
        conn.commit()
    if not remote_restore_enabled:
        raise HTTPException(status_code=403, detail="当前环境未启用远程恢复，请在 staging 完成演练后再开启")
    return {"job_id": job_id, "backup_id": backup_id, "status": "queued", "stage": "prechecking"}
