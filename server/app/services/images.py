from pathlib import Path
from uuid import uuid4

from fastapi import HTTPException, UploadFile

from ..database import upload_dir

ALLOWED = {
    "image/jpeg": (".jpg", [b"\xff\xd8\xff"]),
    "image/png": (".png", [b"\x89PNG\r\n\x1a\n"]),
    "image/webp": (".webp", [b"RIFF"]),
}


async def save_upload(file: UploadFile, max_mb: int = 5) -> str:
    if file.content_type not in ALLOWED:
        raise HTTPException(status_code=400, detail="Unsupported image type")
    content = await file.read()
    if len(content) > max_mb * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image too large")
    suffix, signatures = ALLOWED[file.content_type]
    if not any(content.startswith(sig) for sig in signatures):
        raise HTTPException(status_code=400, detail="Invalid image file")
    target_dir = Path(upload_dir())
    target_dir.mkdir(parents=True, exist_ok=True)
    name = f"{uuid4()}{suffix}"
    path = target_dir / name
    path.write_bytes(content)
    return f"/uploads/{name}"

