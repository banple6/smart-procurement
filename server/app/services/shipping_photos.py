from __future__ import annotations

import hashlib
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from uuid import uuid4
from zoneinfo import ZoneInfo

from fastapi import HTTPException, UploadFile
from PIL import Image, ImageDraw, ImageFont, ImageOps, UnidentifiedImageError

from ..database import private_upload_dir

MAX_PHOTO_BYTES = 5 * 1024 * 1024
MAX_FULL_SIDE = 1600
MAX_THUMB_SIDE = 480
ALLOWED_FORMATS = {"JPEG", "PNG", "WEBP"}


@dataclass(frozen=True)
class ProcessedShippingPhoto:
    id: str
    image_path: str
    thumbnail_path: str
    mime_type: str
    file_size: int
    width: int
    height: int
    sha256: str


def private_root() -> Path:
    return Path(private_upload_dir()).resolve()


def resolve_private_path(relative_path: str) -> Path:
    root = private_root()
    path = (root / relative_path).resolve()
    if not path.is_relative_to(root):
        raise HTTPException(status_code=404, detail="照片不存在")
    return path


def cleanup_photos(photos: list[ProcessedShippingPhoto]):
    for photo in photos:
        for relative_path in (photo.image_path, photo.thumbnail_path):
            resolve_private_path(relative_path).unlink(missing_ok=True)


async def process_shipping_uploads(
    uploads: list[UploadFile],
    *,
    order_no: str,
    unit_name: str,
    operator_username: str,
) -> list[ProcessedShippingPhoto]:
    if not uploads:
        raise HTTPException(status_code=400, detail="请至少拍摄一张发货照片")
    if len(uploads) > 3:
        raise HTTPException(status_code=400, detail="最多上传三张发货照片")

    processed: list[ProcessedShippingPhoto] = []
    try:
        for upload in uploads:
            processed.append(
                await process_one_upload(
                    upload,
                    order_no=order_no,
                    unit_name=unit_name,
                    operator_username=operator_username,
                )
            )
        return processed
    except Exception:
        cleanup_photos(processed)
        raise


async def process_one_upload(
    upload: UploadFile,
    *,
    order_no: str,
    unit_name: str,
    operator_username: str,
) -> ProcessedShippingPhoto:
    raw = await upload.read()
    if len(raw) > MAX_PHOTO_BYTES:
        raise HTTPException(status_code=400, detail="发货照片不能超过 5MB")

    try:
        from io import BytesIO

        with Image.open(BytesIO(raw)) as opened:
            if opened.format not in ALLOWED_FORMATS:
                raise HTTPException(status_code=400, detail="发货照片格式不正确")
            image = ImageOps.exif_transpose(opened).convert("RGB")
    except HTTPException:
        raise
    except (UnidentifiedImageError, OSError, ValueError):
        raise HTTPException(status_code=400, detail="发货照片格式不正确") from None

    image.thumbnail((MAX_FULL_SIDE, MAX_FULL_SIDE), Image.Resampling.LANCZOS)
    add_watermark(
        image,
        [
            f"订单号：{order_no}",
            f"收货单位：{unit_name}",
            f"发货时间：{datetime.now(ZoneInfo('Asia/Shanghai')).strftime('%Y-%m-%d %H:%M')}",
            f"操作账号：{operator_username}",
        ],
    )

    thumb = image.copy()
    thumb.thumbnail((MAX_THUMB_SIDE, MAX_THUMB_SIDE), Image.Resampling.LANCZOS)

    now = datetime.now(ZoneInfo("Asia/Shanghai"))
    relative_dir = Path("shipping") / f"{now.year:04d}" / f"{now.month:02d}"
    root = private_root()
    target_dir = root / relative_dir
    target_dir.mkdir(parents=True, exist_ok=True)
    photo_id = str(uuid4())
    image_relative = str(relative_dir / f"{photo_id}.jpg")
    thumb_relative = str(relative_dir / f"{photo_id}_thumb.jpg")
    image_path = resolve_private_path(image_relative)
    thumb_path = resolve_private_path(thumb_relative)

    image.save(image_path, format="JPEG", quality=88, optimize=True)
    thumb.save(thumb_path, format="JPEG", quality=84, optimize=True)
    content = image_path.read_bytes()
    return ProcessedShippingPhoto(
        id=photo_id,
        image_path=image_relative,
        thumbnail_path=thumb_relative,
        mime_type="image/jpeg",
        file_size=len(content),
        width=image.width,
        height=image.height,
        sha256=hashlib.sha256(content).hexdigest(),
    )


def add_watermark(image: Image.Image, lines: list[str]):
    draw = ImageDraw.Draw(image, "RGBA")
    font = load_watermark_font(max(14, min(image.size) // 22))
    spacing = 4
    padding = 10
    measured = [draw.textbbox((0, 0), line, font=font) for line in lines]
    width = max(box[2] - box[0] for box in measured)
    line_heights = [box[3] - box[1] for box in measured]
    height = sum(line_heights) + spacing * (len(lines) - 1)
    x0 = max(0, image.width - width - padding * 2 - 12)
    y0 = max(0, image.height - height - padding * 2 - 12)
    x1 = image.width - 8
    y1 = image.height - 8
    draw.rounded_rectangle((x0, y0, x1, y1), radius=8, fill=(0, 0, 0, 130))
    y = y0 + padding
    for line, line_height in zip(lines, line_heights, strict=True):
        draw.text((x0 + padding, y), line, fill=(255, 255, 255, 235), font=font)
        y += line_height + spacing


def load_watermark_font(size: int):
    candidates = [
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
        "/System/Library/Fonts/PingFang.ttc",
        "/System/Library/Fonts/STHeiti Light.ttc",
        "/Library/Fonts/Arial Unicode.ttf",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        if Path(path).exists():
            try:
                return ImageFont.truetype(path, size=size)
            except OSError:
                continue
    return ImageFont.load_default(size=size)
