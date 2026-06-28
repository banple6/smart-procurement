from pathlib import Path
from uuid import uuid4
from io import BytesIO

from fastapi import HTTPException, UploadFile
from PIL import Image, UnidentifiedImageError

from ..database import upload_dir

ALLOWED = {
    "JPEG": (".jpg", "JPEG"),
    "PNG": (".png", "PNG"),
    "WEBP": (".webp", "WEBP"),
}


async def save_upload(file: UploadFile, max_mb: int = 5) -> str:
    content = await file.read()
    if len(content) > max_mb * 1024 * 1024:
        raise HTTPException(status_code=400, detail="Image too large")
    try:
        with Image.open(BytesIO(content)) as image:
            image.load()
            if image.format not in ALLOWED:
                raise HTTPException(status_code=400, detail="Unsupported image type")
            suffix, output_format = ALLOWED[image.format]
            clean = image.convert("RGB") if image.mode not in ("RGB", "RGBA") else image.copy()
            clean.thumbnail((1600, 1600))
            out = BytesIO()
            save_kwargs = {"quality": 85, "optimize": True} if output_format in ("JPEG", "WEBP") else {"optimize": True}
            if output_format == "JPEG" and clean.mode == "RGBA":
                clean = clean.convert("RGB")
            clean.save(out, format=output_format, **save_kwargs)
            encoded = out.getvalue()
    except UnidentifiedImageError:
        raise HTTPException(status_code=400, detail="Invalid image file")
    except OSError:
        raise HTTPException(status_code=400, detail="Invalid image file")
    target_dir = Path(upload_dir())
    target_dir.mkdir(parents=True, exist_ok=True)
    name = f"{uuid4()}{suffix}"
    path = target_dir / name
    path.write_bytes(encoded)
    return f"/uploads/{name}"
