import base64
import hashlib
import json
import os
import re
import secrets
import subprocess
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, UploadFile
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey

from ..database import all_rows, app_release_dir, connect, one, write_audit
from ..security import hash_token

CHANNELS = {"staging", "production"}
UPDATE_TYPES = {"optional", "recommended", "mandatory"}
RELEASE_STATUSES = {"draft", "validating", "ready", "published", "paused", "revoked", "failed"}
ROLLOUT_VALUES = {10, 25, 50, 100}
EVENT_TYPES = {
    "update_seen",
    "download_started",
    "download_paused",
    "download_completed",
    "verification_passed",
    "verification_failed",
    "install_requested",
    "install_confirmed",
    "install_cancelled",
    "install_failed",
    "first_launch_after_update",
}
FAILURE_CODES = {
    "",
    "UPDATE_NOT_FOUND",
    "UPDATE_CHANNEL_MISMATCH",
    "UPDATE_RELEASE_PAUSED",
    "UPDATE_RELEASE_REVOKED",
    "UPDATE_TICKET_EXPIRED",
    "UPDATE_DOWNLOAD_FAILED",
    "UPDATE_STORAGE_INSUFFICIENT",
    "UPDATE_HASH_MISMATCH",
    "UPDATE_PACKAGE_MISMATCH",
    "UPDATE_SIGNATURE_MISMATCH",
    "UPDATE_VERSION_INVALID",
    "UPDATE_ANDROID_INCOMPATIBLE",
    "UPDATE_INSTALL_CANCELLED",
    "UPDATE_INSTALL_FAILED",
}
SENSITIVE_METADATA_KEYS = {"token", "cookie", "authorization", "password", "download_ticket", "server_path", "path"}
HEX_SHA256 = re.compile(r"^[a-fA-F0-9]{64}$")


def utc_now() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z")


def app_update_enabled() -> bool:
    return os.getenv("APP_UPDATE_ENABLED", "true").lower() in {"1", "true", "yes", "on"}


def app_update_public_origin() -> str:
    return os.getenv("APP_UPDATE_PUBLIC_ORIGIN", "").strip().rstrip("/")


def app_update_keep_releases() -> int:
    return max(1, int(os.getenv("APP_UPDATE_KEEP_RELEASES", "3")))


def app_update_ticket_seconds() -> int:
    return max(60, int(os.getenv("APP_UPDATE_DOWNLOAD_TICKET_SECONDS", "600")))


def max_update_bytes() -> int:
    return int(os.getenv("APP_UPDATE_DOWNLOAD_MAX_BYTES", "209715200"))


def env_name() -> str:
    return os.getenv("APP_ENV", "development").lower()


def production_https_required(channel: str):
    if env_name() == "production" and channel == "production" and not app_update_public_origin().startswith("https://"):
        raise HTTPException(status_code=400, detail="HTTPS 未配置，正式更新功能已停用")


def release_storage_path(storage_key: str) -> Path:
    root = Path(app_release_dir()).resolve()
    path = (root / storage_key).resolve()
    if root not in path.parents and path != root:
        raise HTTPException(status_code=400, detail="更新文件路径不正确")
    return path


def release_out(row: dict, *, include_internal: bool = False) -> dict:
    result = {
        "id": row["id"],
        "channel": row["channel"],
        "package_name": row["package_name"],
        "version_code": row["version_code"],
        "version_name": row["version_name"],
        "minimum_supported_version_code": row["minimum_supported_version_code"],
        "update_type": row["update_type"],
        "title": row["title"],
        "release_notes": [line for line in row["release_notes"].splitlines() if line.strip()],
        "apk_size_bytes": row["apk_size_bytes"],
        "apk_sha256": row["apk_sha256"],
        "signer_sha256": row["signer_sha256"],
        "rollout_percent": row["rollout_percent"],
        "status": row["status"],
        "published_at": row["published_at"],
        "created_at": row["created_at"],
        "updated_at": row["updated_at"],
        "min_sdk": row.get("min_sdk", 0),
        "manifest_signature": row.get("manifest_signature", ""),
        "manifest_public_key": row.get("manifest_public_key", ""),
        "manifest_key_id": row.get("manifest_key_id", ""),
        "manifest_signature_algorithm": row.get("manifest_signature_algorithm", ""),
    }
    if include_internal:
        result["apk_storage_key"] = row["apk_storage_key"]
    return result


def canonical_manifest_payload(row: dict) -> str:
    payload = {
        "schema_version": 1,
        "release_id": row["id"],
        "channel": row["channel"],
        "package_name": row["package_name"],
        "version_code": int(row["version_code"]),
        "version_name": row["version_name"],
        "minimum_supported_version_code": int(row["minimum_supported_version_code"]),
        "update_type": row["update_type"],
        "title": row["title"],
        "release_notes": [line for line in row["release_notes"].splitlines() if line.strip()],
        "apk_size_bytes": int(row["apk_size_bytes"]),
        "apk_sha256": row["apk_sha256"],
        "signer_sha256": row["signer_sha256"],
        "min_sdk": int(row.get("min_sdk", 0)),
    }
    return json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))


def manifest_payload_for_release(release_id: str) -> dict:
    with connect() as conn:
        release = one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,))
        if not release:
            raise HTTPException(status_code=404, detail="版本不存在")
    return {
        "release_id": release_id,
        "manifest_payload": canonical_manifest_payload(release),
        "manifest_signature_algorithm": "Ed25519",
    }


def verify_manifest_signature(release: dict, signature_text: str, public_key_text: str, key_id: str) -> tuple[str, str, str]:
    if not signature_text.strip() or not public_key_text.strip() or not key_id.strip():
        raise HTTPException(status_code=400, detail="发布前必须提供离线 Ed25519 清单签名")
    try:
        signature = base64.b64decode(signature_text, validate=True)
        public_key = base64.b64decode(public_key_text, validate=True)
        Ed25519PublicKey.from_public_bytes(public_key).verify(signature, canonical_manifest_payload(release).encode("utf-8"))
    except (ValueError, InvalidSignature) as exc:
        raise HTTPException(status_code=400, detail="清单签名校验失败") from exc
    if len(signature) != 64 or len(public_key) != 32:
        raise HTTPException(status_code=400, detail="清单签名校验失败")
    return (
        base64.b64encode(signature).decode("ascii"),
        base64.b64encode(public_key).decode("ascii"),
        key_id.strip()[:80],
    )


def validate_release_inputs(channel: str, package_name: str, update_type: str, title: str, release_notes: str, rollout_percent: int):
    if channel not in CHANNELS:
        raise HTTPException(status_code=400, detail="更新通道不正确")
    if update_type not in UPDATE_TYPES:
        raise HTTPException(status_code=400, detail="更新级别不正确")
    if rollout_percent not in ROLLOUT_VALUES:
        raise HTTPException(status_code=400, detail="灰度比例只能选择 10、25、50 或 100")
    if not package_name.strip():
        raise HTTPException(status_code=400, detail="应用包名不能为空")
    if not title.strip():
        raise HTTPException(status_code=400, detail="请填写版本标题")
    notes = [line.strip() for line in release_notes.splitlines() if line.strip()]
    if len(notes) > 20 or any(len(line) > 120 for line in notes):
        raise HTTPException(status_code=400, detail="版本说明过长，请精简后再发布")


def parse_apksigner_output(text: str) -> str:
    for line in text.splitlines():
        lower = line.lower()
        if "sha-256 digest" in lower or "sha256" in lower:
            candidate = line.rsplit(":", 1)[-1].strip().replace(" ", "")
            if HEX_SHA256.fullmatch(candidate):
                return candidate.upper()
    return ""


def parse_aapt_output(text: str) -> dict:
    first = text.splitlines()[0] if text.splitlines() else ""
    package_match = re.search(r"name='([^']+)'", first)
    version_code_match = re.search(r"versionCode='([0-9]+)'", first)
    version_name_match = re.search(r"versionName='([^']+)'", first)
    sdk_match = re.search(r"sdkVersion:'([0-9]+)'", text)
    if not package_match or not version_code_match or not version_name_match:
        raise HTTPException(status_code=400, detail="APK 无法解析")
    return {
        "package_name": package_match.group(1),
        "version_code": int(version_code_match.group(1)),
        "version_name": version_name_match.group(1),
        "min_sdk": int(sdk_match.group(1)) if sdk_match else 0,
    }


def parse_apk_metadata(path: Path) -> dict:
    apksigner = os.getenv("APKSIGNER_PATH", "").strip()
    aapt = os.getenv("AAPT_PATH", "").strip()
    if apksigner and aapt:
        verify = subprocess.run([apksigner, "verify", "--print-certs", str(path)], check=False, capture_output=True, text=True)
        if verify.returncode != 0:
            raise HTTPException(status_code=400, detail="APK 签名校验失败")
        signer = parse_apksigner_output(verify.stdout + "\n" + verify.stderr)
        if not signer:
            raise HTTPException(status_code=400, detail="APK 签名证书无法识别")
        badging = subprocess.run([aapt, "dump", "badging", str(path)], check=False, capture_output=True, text=True)
        if badging.returncode != 0:
            raise HTTPException(status_code=400, detail="APK 无法解析")
        return {**parse_aapt_output(badging.stdout), "signer_sha256": signer}

    allow_test_metadata = env_name() in {"test", "development", "staging"} or os.getenv("APP_UPDATE_ALLOW_TEST_APK_METADATA", "").lower() == "true"
    if allow_test_metadata:
        try:
            with zipfile.ZipFile(path) as archive:
                metadata = json.loads(archive.read("META-INF/JRXP-APK-METADATA.json").decode("utf-8"))
        except Exception as exc:
            raise HTTPException(status_code=400, detail="APK 无法解析") from exc
        signer = str(metadata.get("signer_sha256", "")).replace(" ", "").upper()
        if not HEX_SHA256.fullmatch(signer):
            raise HTTPException(status_code=400, detail="APK 签名证书无法识别")
        return {
            "package_name": str(metadata.get("package_name", "")),
            "version_code": int(metadata.get("version_code", 0)),
            "version_name": str(metadata.get("version_name", "")),
            "channel": str(metadata.get("channel", "")),
            "signer_sha256": signer,
            "min_sdk": int(metadata.get("min_sdk", 0)),
        }

    raise HTTPException(status_code=400, detail="服务器未配置 APK 解析和签名校验工具，禁止发布")


def save_upload_to_release_dir(upload: UploadFile, storage_key: str) -> tuple[int, str]:
    if not upload.filename.lower().endswith(".apk"):
        raise HTTPException(status_code=400, detail="请上传 APK 文件")
    path = release_storage_path(storage_key)
    path.parent.mkdir(parents=True, exist_ok=True)
    digest = hashlib.sha256()
    size = 0
    with path.open("wb") as out:
        while True:
            chunk = upload.file.read(1024 * 1024)
            if not chunk:
                break
            size += len(chunk)
            if size > max_update_bytes():
                out.close()
                path.unlink(missing_ok=True)
                raise HTTPException(status_code=400, detail="APK 文件超过大小限制")
            digest.update(chunk)
            out.write(chunk)
    if size == 0:
        path.unlink(missing_ok=True)
        raise HTTPException(status_code=400, detail="APK 文件为空")
    return size, digest.hexdigest()


def create_release(
    *,
    admin: dict,
    apk: UploadFile,
    channel: str,
    package_name: str,
    update_type: str,
    title: str,
    release_notes: str,
    minimum_supported_version_code: int,
    rollout_percent: int,
) -> dict:
    validate_release_inputs(channel, package_name, update_type, title, release_notes, rollout_percent)
    production_https_required(channel)
    release_id = str(uuid4())
    storage_key = f"{channel}/{release_id}.apk"
    size, apk_sha256 = save_upload_to_release_dir(apk, storage_key)
    path = release_storage_path(storage_key)
    try:
        metadata = parse_apk_metadata(path)
        if metadata["package_name"] != package_name:
            raise HTTPException(status_code=400, detail="APK 包名与发布通道不一致")
        if metadata.get("channel") and metadata["channel"] != channel:
            raise HTTPException(status_code=400, detail="APK 更新通道不一致")
        version_code = int(metadata["version_code"])
        version_name = str(metadata["version_name"])
        if version_code <= 0 or not version_name:
            raise HTTPException(status_code=400, detail="APK 版本号不正确")
        with connect() as conn:
            highest = one(
                conn,
                "SELECT MAX(version_code) AS max_version FROM app_releases WHERE channel = ? AND package_name = ?",
                (channel, package_name),
            )
            if highest and highest["max_version"] is not None and version_code <= int(highest["max_version"]):
                raise HTTPException(status_code=409, detail="versionCode 必须高于已发布最高版本")
            conn.execute(
                """
                INSERT INTO app_releases(
                  id, channel, package_name, version_code, version_name, minimum_supported_version_code,
                  update_type, title, release_notes, apk_storage_key, apk_size_bytes, apk_sha256,
                  signer_sha256, rollout_percent, status, created_by, min_sdk
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ready', ?, ?)
                """,
                (
                    release_id,
                    channel,
                    package_name,
                    version_code,
                    version_name,
                    minimum_supported_version_code,
                    update_type,
                    title.strip(),
                    "\n".join(line.strip() for line in release_notes.splitlines() if line.strip()),
                    storage_key,
                    size,
                    apk_sha256,
                    metadata["signer_sha256"],
                    rollout_percent,
                    admin["id"],
                    int(metadata.get("min_sdk", 0)),
                ),
            )
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                "APP_RELEASE_VALIDATED",
                "app_release",
                release_id,
                after_json=json.dumps({"channel": channel, "package_name": package_name, "version_code": version_code}, ensure_ascii=False),
            )
            conn.commit()
            return release_out(one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,)))
    except Exception:
        path.unlink(missing_ok=True)
        raise


def list_releases(status: str | None = None) -> list[dict]:
    where = []
    params = []
    if status:
        where.append("status = ?")
        params.append(status)
    sql = "SELECT * FROM app_releases"
    if where:
        sql += " WHERE " + " AND ".join(where)
    sql += " ORDER BY created_at DESC"
    with connect() as conn:
        return [release_out(row) for row in all_rows(conn, sql, params)]


def mutate_release_status(
    release_id: str,
    admin: dict,
    status: str,
    reason: str = "",
    manifest_signature: str = "",
    manifest_public_key: str = "",
    manifest_key_id: str = "",
) -> dict:
    if status not in RELEASE_STATUSES:
        raise HTTPException(status_code=400, detail="版本状态不正确")
    with connect() as conn:
        release = one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,))
        if not release:
            raise HTTPException(status_code=404, detail="版本不存在")
        if status == "published":
            production_https_required(release["channel"])
            signature, public_key, key_id = verify_manifest_signature(release, manifest_signature, manifest_public_key, manifest_key_id)
            conn.execute(
                """
                UPDATE app_releases
                SET status = 'published',
                    published_by = ?,
                    published_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    manifest_signature = ?,
                    manifest_public_key = ?,
                    manifest_key_id = ?,
                    manifest_signature_algorithm = 'Ed25519'
                WHERE id = ?
                """,
                (admin["id"], signature, public_key, key_id, release_id),
            )
            action = "APP_RELEASE_PUBLISHED"
        elif status == "paused":
            conn.execute("UPDATE app_releases SET status = 'paused', updated_at = CURRENT_TIMESTAMP WHERE id = ?", (release_id,))
            action = "APP_RELEASE_PAUSED"
        elif status == "revoked":
            conn.execute(
                """
                UPDATE app_releases
                SET status = 'revoked', revoked_by = ?, revoked_at = CURRENT_TIMESTAMP, revoke_reason = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                (admin["id"], reason.strip(), release_id),
            )
            action = "APP_RELEASE_REVOKED"
        else:
            conn.execute("UPDATE app_releases SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (status, release_id))
            action = "APP_RELEASE_STATUS_UPDATED"
        write_audit(conn, admin["id"], admin["role"], action, "app_release", release_id, after_json=json.dumps({"status": status, "reason": reason}, ensure_ascii=False))
        conn.commit()
        return release_out(one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,)))


def update_rollout(release_id: str, admin: dict, rollout_percent: int) -> dict:
    if rollout_percent not in ROLLOUT_VALUES:
        raise HTTPException(status_code=400, detail="灰度比例只能选择 10、25、50 或 100")
    with connect() as conn:
        release = one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,))
        if not release:
            raise HTTPException(status_code=404, detail="版本不存在")
        if rollout_percent < int(release["rollout_percent"]):
            raise HTTPException(status_code=400, detail="灰度比例只能扩大，不能降低")
        conn.execute("UPDATE app_releases SET rollout_percent = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (rollout_percent, release_id))
        write_audit(conn, admin["id"], admin["role"], "APP_RELEASE_ROLLOUT_EXPANDED", "app_release", release_id, after_json=json.dumps({"rollout_percent": rollout_percent}, ensure_ascii=False))
        conn.commit()
        return release_out(one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,)))


def installation_hash(installation_id: str) -> str:
    return hashlib.sha256(installation_id.strip().encode("utf-8")).hexdigest()


def rollout_allows(installation_id_hash: str, release_id: str, percent: int, mandatory: bool) -> bool:
    if mandatory:
        return True
    if percent >= 100:
        return True
    bucket = int(hashlib.sha256(f"{installation_id_hash}:{release_id}".encode("utf-8")).hexdigest()[:8], 16) % 100
    return bucket < percent


def record_installation(
    *,
    installation_id: str,
    package_name: str,
    channel: str,
    current_version_code: int,
    current_version_name: str = "",
    android_version: str = "",
    device_model: str = "",
    user: dict | None = None,
    latest_release_id: str | None = None,
    latest_update_status: str = "",
) -> str:
    hashed = installation_hash(installation_id)
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO app_installations(
              id, installation_id_hash, user_id, unit_id, role, package_name, channel, current_version_code,
              current_version_name, android_version, device_model, last_check_at, last_seen_at,
              latest_update_status, latest_release_id, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(installation_id_hash) DO UPDATE SET
              user_id = excluded.user_id,
              unit_id = excluded.unit_id,
              role = excluded.role,
              package_name = excluded.package_name,
              channel = excluded.channel,
              current_version_code = excluded.current_version_code,
              current_version_name = excluded.current_version_name,
              android_version = excluded.android_version,
              device_model = excluded.device_model,
              last_check_at = CURRENT_TIMESTAMP,
              last_seen_at = CURRENT_TIMESTAMP,
              latest_update_status = excluded.latest_update_status,
              latest_release_id = excluded.latest_release_id,
              updated_at = CURRENT_TIMESTAMP
            """,
            (
                str(uuid4()),
                hashed,
                user["id"] if user else None,
                user.get("unit_id") if user else None,
                user.get("role", "") if user else "",
                package_name,
                channel,
                current_version_code,
                current_version_name,
                android_version,
                device_model,
                latest_update_status,
                latest_release_id,
            ),
        )
        conn.commit()
    return hashed


def create_download_ticket(conn, release_id: str, installation_id_hash: str) -> tuple[str, str]:
    ticket = secrets.token_urlsafe(32)
    expires_modifier = f"+{app_update_ticket_seconds()} seconds"
    ticket_id = str(uuid4())
    conn.execute(
        """
        INSERT INTO app_update_download_tickets(id, ticket_hash, release_id, installation_id_hash, expires_at)
        VALUES (?, ?, ?, ?, datetime('now', ?))
        """,
        (ticket_id, hash_token(ticket), release_id, installation_id_hash, expires_modifier),
    )
    row = one(conn, "SELECT expires_at FROM app_update_download_tickets WHERE id = ?", (ticket_id,))
    return ticket, row["expires_at"]


def best_release(package_name: str, channel: str, current_version_code: int) -> dict | None:
    with connect() as conn:
        return one(
            conn,
            """
            SELECT * FROM app_releases
            WHERE package_name = ?
              AND channel = ?
              AND status = 'published'
              AND version_code > ?
            ORDER BY version_code DESC, published_at DESC
            LIMIT 1
            """,
            (package_name, channel, current_version_code),
        )


def check_update(
    *,
    package_name: str,
    channel: str,
    current_version_code: int,
    current_version_name: str,
    android_api_level: int,
    installation_id: str,
    user: dict | None = None,
    android_version: str = "",
    device_model: str = "",
) -> dict:
    if not app_update_enabled():
        return {"update_available": False, "server_now": utc_now()}
    if channel not in CHANNELS:
        raise HTTPException(status_code=400, detail="更新通道不正确")
    installation_id_hash = record_installation(
        installation_id=installation_id,
        package_name=package_name,
        channel=channel,
        current_version_code=current_version_code,
        current_version_name=current_version_name,
        android_version=android_version,
        device_model=device_model,
        user=user,
    )
    release = best_release(package_name, channel, current_version_code)
    if not release:
        return {"update_available": False, "server_now": utc_now()}
    mandatory = release["update_type"] == "mandatory" or int(release["minimum_supported_version_code"]) > current_version_code
    if not rollout_allows(installation_id_hash, release["id"], int(release["rollout_percent"]), mandatory):
        return {"update_available": False, "server_now": utc_now()}
    with connect() as conn:
        ticket, expires_at = create_download_ticket(conn, release["id"], installation_id_hash)
        conn.execute(
            "UPDATE app_installations SET latest_update_status = 'update_seen', latest_release_id = ?, updated_at = CURRENT_TIMESTAMP WHERE installation_id_hash = ?",
            (release["id"], installation_id_hash),
        )
        conn.execute(
            """
            INSERT INTO app_update_events(id, installation_id_hash, user_id, release_id, event_type, metadata_json)
            VALUES (?, ?, ?, ?, 'update_seen', '{}')
            """,
            (str(uuid4()), installation_id_hash, user["id"] if user else None, release["id"]),
        )
        conn.commit()
    release_payload = {
        "release_id": release["id"],
        "id": release["id"],
        "package_name": release["package_name"],
        "channel": release["channel"],
        "version_code": release["version_code"],
        "version_name": release["version_name"],
        "minimum_supported_version_code": release["minimum_supported_version_code"],
        "update_type": release["update_type"],
        "mandatory": mandatory,
        "title": release["title"],
        "release_notes": [line for line in release["release_notes"].splitlines() if line.strip()],
        "apk_size_bytes": release["apk_size_bytes"],
        "size_bytes": release["apk_size_bytes"],
        "apk_sha256": release["apk_sha256"],
        "signer_sha256": release["signer_sha256"],
        "min_sdk": release["min_sdk"],
        "published_at": release["published_at"],
        "download_ticket": ticket,
        "download_ticket_expires_at": expires_at,
        "manifest_signature": release["manifest_signature"],
        "manifest_public_key": release["manifest_public_key"],
        "manifest_key_id": release["manifest_key_id"],
        "manifest_signature_algorithm": release["manifest_signature_algorithm"],
    }
    return {
        "update_available": True,
        "mandatory": mandatory,
        "server_now": utc_now(),
        "release": release_payload,
        **release_payload,
    }


def verify_download_ticket(release_id: str, ticket: str) -> dict:
    with connect() as conn:
        release = one(conn, "SELECT * FROM app_releases WHERE id = ?", (release_id,))
        if not release:
            raise HTTPException(status_code=404, detail="版本不存在")
        if release["status"] != "published":
            raise HTTPException(status_code=403, detail="更新已暂停，请稍后重试")
        ticket_row = one(
            conn,
            """
            SELECT *, CURRENT_TIMESTAMP > expires_at AS expired
            FROM app_update_download_tickets
            WHERE ticket_hash = ? AND release_id = ?
            """,
            (hash_token(ticket), release_id),
        )
        if not ticket_row:
            raise HTTPException(status_code=403, detail="下载授权已失效，正在重新获取")
        if ticket_row["expired"]:
            raise HTTPException(status_code=403, detail="下载授权已失效，正在重新获取")
        return release


def sanitize_metadata(metadata: dict | None) -> dict:
    clean: dict = {}
    for key, value in (metadata or {}).items():
        lowered = str(key).lower()
        if any(sensitive in lowered for sensitive in SENSITIVE_METADATA_KEYS):
            continue
        if isinstance(value, (str, int, float, bool)) or value is None:
            clean[str(key)] = value
    return clean


def record_update_event(
    *,
    installation_id: str,
    release_id: str | None,
    event_type: str,
    failure_code: str = "",
    metadata: dict | None = None,
    user: dict | None = None,
) -> dict:
    if event_type not in EVENT_TYPES:
        raise HTTPException(status_code=400, detail="更新事件类型不正确")
    if failure_code not in FAILURE_CODES:
        raise HTTPException(status_code=400, detail="更新失败代码不正确")
    hashed = installation_hash(installation_id)
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO app_update_events(id, installation_id_hash, user_id, release_id, event_type, failure_code, metadata_json)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (str(uuid4()), hashed, user["id"] if user else None, release_id, event_type, failure_code, json.dumps(sanitize_metadata(metadata), ensure_ascii=False)),
        )
        conn.execute(
            "UPDATE app_installations SET latest_update_status = ?, latest_release_id = COALESCE(?, latest_release_id), updated_at = CURRENT_TIMESTAMP WHERE installation_id_hash = ?",
            (event_type, release_id, hashed),
        )
        conn.commit()
    return {"ok": True}


def coverage_summary() -> dict:
    with connect() as conn:
        distribution = all_rows(
            conn,
            """
            SELECT package_name, channel, current_version_code AS version_code,
              current_version_name AS version_name, COUNT(*) AS installation_count
            FROM app_installations
            GROUP BY package_name, channel, current_version_code, current_version_name
            ORDER BY installation_count DESC, version_code DESC
            """,
        )
        total_row = one(conn, "SELECT COUNT(*) AS count FROM app_installations") or {"count": 0}
        events = all_rows(
            conn,
            "SELECT event_type, COUNT(*) AS count FROM app_update_events GROUP BY event_type",
        )
        failures = all_rows(
            conn,
            "SELECT failure_code, COUNT(*) AS count FROM app_update_events WHERE failure_code != '' GROUP BY failure_code",
        )
        recent = all_rows(
            conn,
            """
            SELECT i.id, i.package_name, i.channel, i.current_version_code, i.current_version_name,
              i.role, units.unit_name, i.latest_update_status, i.last_seen_at
            FROM app_installations i
            LEFT JOIN units ON units.id = i.unit_id
            ORDER BY i.last_seen_at DESC
            LIMIT 50
            """,
        )
    total = max(1, int(total_row["count"] or 0))
    return {
        "active_installations_24h": int(total_row["count"] or 0),
        "version_distribution": [
            {**row, "percent": round((int(row["installation_count"]) / total) * 100, 1)}
            for row in distribution
        ],
        "event_summary": {row["event_type"]: row["count"] for row in events},
        "failure_summary": {row["failure_code"]: row["count"] for row in failures},
        "installations": [
            {
                "device_name": f"设备-{row['id'][:8]}",
                "unit_name": row.get("unit_name") or "",
                "role": row.get("role") or "",
                "package_name": row["package_name"],
                "channel": row["channel"],
                "version_code": row["current_version_code"],
                "version_name": row["current_version_name"],
                "latest_update_status": row["latest_update_status"],
                "last_seen_at": row["last_seen_at"],
            }
            for row in recent
        ],
    }


def update_required_for_client(package_name: str, channel: str, version_code: int, installation_id: str) -> dict | None:
    release = best_release(package_name, channel, version_code)
    if not release:
        return None
    if int(release["minimum_supported_version_code"]) <= version_code and release["update_type"] != "mandatory":
        return None
    hashed = record_installation(
        installation_id=installation_id,
        package_name=package_name,
        channel=channel,
        current_version_code=version_code,
        latest_release_id=release["id"],
        latest_update_status="blocked_by_mandatory",
    )
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO app_update_events(id, installation_id_hash, release_id, event_type, failure_code, metadata_json)
            VALUES (?, ?, ?, 'update_seen', '', ?)
            """,
            (str(uuid4()), hashed, release["id"], json.dumps({"blocked": True}, ensure_ascii=False)),
        )
        conn.commit()
    return {
        "code": "APP_UPDATE_REQUIRED",
        "detail": "当前版本过低，请更新后继续使用",
        "update": {
            "version_name": release["version_name"],
            "version_code": release["version_code"],
            "release_id": release["id"],
        },
    }
