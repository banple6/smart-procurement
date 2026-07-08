from pathlib import Path
from urllib.parse import urlencode

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request, UploadFile, Form
from fastapi.responses import StreamingResponse
from pydantic import BaseModel, Field

from ..dependencies import current_user, require_admin_user
from ..services import app_update

router = APIRouter(tags=["app-update"])


class ReleaseAction(BaseModel):
    reason: str = ""


class ReleasePublish(BaseModel):
    manifest_signature: str = ""
    manifest_public_key: str = ""
    manifest_key_id: str = ""


class RolloutPatch(BaseModel):
    rollout_percent: int


class UpdateEventBody(BaseModel):
    installation_id: str = Field(min_length=8, max_length=128)
    release_id: str | None = None
    event_type: str
    failure_code: str = ""
    metadata: dict = Field(default_factory=dict)


def optional_bearer_user(request: Request, authorization: str | None):
    if not authorization or not authorization.startswith("Bearer "):
        return None
    try:
        return current_user(request, authorization)
    except HTTPException:
        return None


@router.post("/admin/app-update/releases")
async def create_release(
    apk: UploadFile,
    channel: str = Form(...),
    package_name: str = Form(...),
    update_type: str = Form(...),
    title: str = Form(...),
    release_notes: str = Form(""),
    minimum_supported_version_code: int = Form(0),
    rollout_percent: int = Form(100),
    admin=Depends(require_admin_user),
):
    return app_update.create_release(
        admin=admin,
        apk=apk,
        channel=channel,
        package_name=package_name,
        update_type=update_type,
        title=title,
        release_notes=release_notes,
        minimum_supported_version_code=minimum_supported_version_code,
        rollout_percent=rollout_percent,
    )


@router.get("/admin/app-update/releases")
def list_releases(status: str | None = None, admin=Depends(require_admin_user)):
    return {"items": app_update.list_releases(status)}


@router.get("/admin/app-update/releases/{release_id}/manifest")
def release_manifest(release_id: str, admin=Depends(require_admin_user)):
    return app_update.manifest_payload_for_release(release_id)


@router.post("/admin/app-update/releases/{release_id}/publish")
def publish_release(release_id: str, body: ReleasePublish, admin=Depends(require_admin_user)):
    return app_update.mutate_release_status(
        release_id,
        admin,
        "published",
        manifest_signature=body.manifest_signature,
        manifest_public_key=body.manifest_public_key,
        manifest_key_id=body.manifest_key_id,
    )


@router.post("/admin/app-update/releases/{release_id}/pause")
def pause_release(release_id: str, body: ReleaseAction, admin=Depends(require_admin_user)):
    return app_update.mutate_release_status(release_id, admin, "paused", body.reason)


@router.post("/admin/app-update/releases/{release_id}/revoke")
def revoke_release(release_id: str, body: ReleaseAction, admin=Depends(require_admin_user)):
    return app_update.mutate_release_status(release_id, admin, "revoked", body.reason)


@router.patch("/admin/app-update/releases/{release_id}/rollout")
def patch_rollout(release_id: str, body: RolloutPatch, admin=Depends(require_admin_user)):
    return app_update.update_rollout(release_id, admin, body.rollout_percent)


@router.get("/admin/app-update/coverage")
def coverage(admin=Depends(require_admin_user)):
    return app_update.coverage_summary()


@router.get("/app-update/check")
def check_update(
    request: Request,
    package_name: str,
    channel: str,
    current_version_code: int,
    android_api_level: int,
    installation_id: str = Query(min_length=8, max_length=128),
    current_version_name: str = "",
    android_version: str = "",
    device_model: str = "",
    authorization: str | None = Header(default=None),
):
    user = optional_bearer_user(request, authorization)
    return app_update.check_update(
        package_name=package_name,
        channel=channel,
        current_version_code=current_version_code,
        current_version_name=current_version_name,
        android_api_level=android_api_level,
        installation_id=installation_id,
        android_version=android_version,
        device_model=device_model,
        user=user,
    )


@router.get("/app-update/latest")
def latest_release(channel: str = "production", package_name: str = ""):
    release = app_update.latest_public_release(channel=channel, package_name=package_name)
    if not release:
        return {
            "available": False,
            "environment": app_update.env_name(),
            "channel": channel,
            "message": "暂无可下载版本，请联系管理员",
        }
    download_query = urlencode({"channel": channel, **({"package_name": package_name} if package_name else {})})
    return {
        "available": True,
        "environment": app_update.env_name(),
        "channel": channel,
        "release": {
            **app_update.release_out(release),
            "download_url": f"/api/v1/app-update/latest/download?{download_query}",
        },
    }


def parse_range(range_header: str | None, total_size: int) -> tuple[int, int, int]:
    if not range_header:
        return 0, total_size - 1, 200
    if not range_header.startswith("bytes="):
        raise HTTPException(status_code=416, detail="下载范围不正确")
    start_text, _, end_text = range_header.removeprefix("bytes=").partition("-")
    try:
        if start_text:
            start = int(start_text)
            end = int(end_text) if end_text else total_size - 1
        else:
            suffix = int(end_text)
            start = max(0, total_size - suffix)
            end = total_size - 1
    except ValueError as exc:
        raise HTTPException(status_code=416, detail="下载范围不正确") from exc
    if start < 0 or end < start or start >= total_size:
        raise HTTPException(status_code=416, detail="下载范围不正确")
    return start, min(end, total_size - 1), 206


def file_iterator(path: Path, start: int, end: int):
    with path.open("rb") as handle:
        handle.seek(start)
        remaining = end - start + 1
        while remaining > 0:
            chunk = handle.read(min(1024 * 1024, remaining))
            if not chunk:
                break
            remaining -= len(chunk)
            yield chunk


@router.get("/app-update/releases/{release_id}/download")
def download_release(
    release_id: str,
    download_ticket: str,
    range_header: str | None = Header(default=None, alias="Range"),
):
    release = app_update.verify_download_ticket(release_id, download_ticket)
    path = app_update.release_storage_path(release["apk_storage_key"])
    if not path.exists():
        raise HTTPException(status_code=404, detail="更新文件不存在，请联系管理员")
    total_size = path.stat().st_size
    start, end, status_code = parse_range(range_header, total_size)
    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1),
        "Content-Disposition": f'attachment; filename="sangongxianpei-{release["version_name"]}.apk"',
    }
    if status_code == 206:
        headers["Content-Range"] = f"bytes {start}-{end}/{total_size}"
    return StreamingResponse(
        file_iterator(path, start, end),
        status_code=status_code,
        media_type="application/vnd.android.package-archive",
        headers=headers,
    )


@router.get("/app-update/latest/download")
def download_latest_release(
    channel: str = "production",
    package_name: str = "",
    range_header: str | None = Header(default=None, alias="Range"),
):
    release = app_update.latest_public_release(channel=channel, package_name=package_name)
    if not release:
        raise HTTPException(status_code=404, detail="暂无可下载版本，请联系管理员")
    path = app_update.release_storage_path(release["apk_storage_key"])
    if not path.exists():
        raise HTTPException(status_code=404, detail="更新文件不存在，请联系管理员")
    total_size = path.stat().st_size
    start, end, status_code = parse_range(range_header, total_size)
    headers = {
        "Accept-Ranges": "bytes",
        "Content-Length": str(end - start + 1),
        "Content-Disposition": f'attachment; filename="sangongxianpei-{release["version_name"]}.apk"',
    }
    if status_code == 206:
        headers["Content-Range"] = f"bytes {start}-{end}/{total_size}"
    return StreamingResponse(
        file_iterator(path, start, end),
        status_code=status_code,
        media_type="application/vnd.android.package-archive",
        headers=headers,
    )


@router.post("/app-update/events")
def record_event(body: UpdateEventBody, request: Request, authorization: str | None = Header(default=None)):
    return app_update.record_update_event(
        installation_id=body.installation_id,
        release_id=body.release_id,
        event_type=body.event_type,
        failure_code=body.failure_code,
        metadata=body.metadata,
        user=optional_bearer_user(request, authorization),
    )
