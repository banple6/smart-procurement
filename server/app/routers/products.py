import os
import hashlib
from decimal import Decimal, InvalidOperation
from uuid import uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Response, UploadFile

from ..database import all_rows, connect, one
from ..dependencies import current_user, require_admin_user
from ..models import EDITABLE_SUPPLY_STATUSES, PRODUCT_CATEGORIES, PRODUCT_STORAGE_METHODS, PRODUCT_UNITS
from ..schemas import ProductCreate, ProductPricePatch, ProductStatusPatch, ProductStockPatch, ProductUpdate
from ..services.dashboard_cache import invalidate_dashboard_cache
from ..services.images import save_upload
from ..services.inventory import as_decimal, decimal_text, log_inventory

router = APIRouter(tags=["products"])


def product_out(product: dict) -> dict:
    stock = Decimal(product["stock_quantity"])
    reserved = Decimal(product["reserved_quantity"])
    result = {
        **product,
        "active": bool(product["active"]),
        "is_deleted": bool(product["is_deleted"]),
        "available_quantity": decimal_text(stock - reserved),
    }
    for field in ("stock_quantity", "reserved_quantity", "min_order_quantity", "quantity_step", "warning_quantity"):
        result[field] = decimal_text(product.get(field) or "0")
    return result


def product_list_etag(conn, where: list[str], params: list) -> str:
    row = one(
        conn,
        f"""
        SELECT COUNT(*) AS c,
               COALESCE(SUM(version), 0) AS version_sum,
               COALESCE(MAX(updated_at), '') AS max_updated_at
        FROM products
        WHERE {' AND '.join(where)}
        """,
        params,
    )
    payload = f"{row['c']}:{row['version_sum']}:{row['max_updated_at']}"
    return '"' + hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16] + '"'


def ensure_expected_product_version(existing: dict, expected_version: int | None):
    if expected_version is not None and int(existing.get("version") or 1) != expected_version:
        raise HTTPException(status_code=409, detail="食材信息已被其他操作员更新，请刷新后重试")


def ensure_can_supply(price_cents: int, supply_status: str, active: bool):
    if active and supply_status in ("normal", "tight") and price_cents <= 0:
        raise HTTPException(status_code=400, detail="请先填写商品价格")


def parse_quantity(value: str, field_name: str) -> Decimal:
    try:
        return Decimal(str(value).strip())
    except (InvalidOperation, ValueError):
        raise HTTPException(status_code=400, detail=f"{field_name}格式不正确")


def validate_product_payload(fields: dict, existing: dict | None = None):
    if "name" in fields and not str(fields["name"]).strip():
        raise HTTPException(status_code=400, detail="食材名称不能为空")
    if "category" in fields and fields["category"] not in PRODUCT_CATEGORIES:
        raise HTTPException(status_code=400, detail="食材分类不正确")
    if "unit" in fields and fields["unit"] not in PRODUCT_UNITS:
        raise HTTPException(status_code=400, detail="计量单位不正确")
    if "spec" in fields and not str(fields["spec"]).strip():
        raise HTTPException(status_code=400, detail="规格不能为空")
    if "storage_method" in fields and fields["storage_method"] and fields["storage_method"] not in PRODUCT_STORAGE_METHODS:
        raise HTTPException(status_code=400, detail="储存方式不正确")
    if "supply_status" in fields and fields["supply_status"] not in EDITABLE_SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="供应状态不正确")

    checks = (
        ("stock_quantity", "库存", Decimal("0"), True),
        ("reserved_quantity", "预占库存", Decimal("0"), True),
        ("min_order_quantity", "最小申领量", Decimal("0"), False),
        ("quantity_step", "数量步长", Decimal("0"), False),
        ("warning_quantity", "库存预警值", Decimal("0"), True),
    )
    for key, label, limit, allow_equal in checks:
        if key not in fields:
            continue
        value = parse_quantity(fields[key], label)
        if value < limit or (value == limit and not allow_equal):
            suffix = "不能小于 0" if allow_equal else "必须大于 0"
            raise HTTPException(status_code=400, detail=f"{label}{suffix}")

    next_price = fields.get("price_cents", existing["price_cents"] if existing else 0)
    next_status = fields.get("supply_status", existing["supply_status"] if existing else "normal")
    next_active = fields.get("active", bool(existing["active"]) if existing else True)
    ensure_can_supply(int(next_price), next_status, bool(next_active))


@router.get("/products")
def list_products(
    response: Response,
    user=Depends(current_user),
    category: str | None = None,
    q: str | None = None,
    if_none_match: str | None = Header(default=None, alias="If-None-Match"),
):
    where = ["is_deleted = 0"]
    params = []
    if user["role"] == "unit_user":
        where.append("active = 1")
        where.append("supply_status IN ('normal', 'tight')")
    if category:
        where.append("category = ?")
        params.append(category)
    if q:
        where.append("(name LIKE ? OR product_code LIKE ?)")
        params.extend([f"%{q}%", f"%{q}%"])
    with connect() as conn:
        etag = product_list_etag(conn, where, params)
        if if_none_match == etag:
            return Response(status_code=304, headers={"ETag": etag})
        response.headers["ETag"] = etag
        rows = all_rows(conn, f"SELECT * FROM products WHERE {' AND '.join(where)} ORDER BY created_at DESC", params)
    return [product_out(row) for row in rows]


@router.get("/admin/products")
def admin_list_products(category: str | None = None, q: str | None = None, admin=Depends(require_admin_user)):
    where = ["is_deleted = 0"]
    params = []
    if category:
        where.append("category = ?")
        params.append(category)
    if q:
        where.append("(name LIKE ? OR product_code LIKE ?)")
        params.extend([f"%{q}%", f"%{q}%"])
    with connect() as conn:
        rows = all_rows(conn, f"SELECT * FROM products WHERE {' AND '.join(where)} ORDER BY created_at DESC", params)
    return [product_out(row) for row in rows]


@router.get("/products/{product_id}")
def product_detail(product_id: str, user=Depends(current_user)):
    with connect() as conn:
        product = one(conn, "SELECT * FROM products WHERE id = ? AND is_deleted = 0", (product_id,))
    if not product:
        raise HTTPException(status_code=404, detail="食材不存在")
    return product_out(product)


@router.post("/admin/products")
def create_product(body: ProductCreate, admin=Depends(require_admin_user)):
    validate_product_payload(body.model_dump())
    product_id = str(uuid4())
    with connect() as conn:
        conn.execute(
            """
            INSERT INTO products(id, product_code, name, category, spec, unit, price_cents, stock_quantity,
              reserved_quantity, min_order_quantity, quantity_step, warning_quantity, origin, supplier,
              shelf_life, storage_method, description, supply_status, active, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                product_id, body.product_code, body.name, body.category, body.spec, body.unit, body.price_cents,
                body.stock_quantity, body.reserved_quantity, body.min_order_quantity, body.quantity_step,
                body.warning_quantity, body.origin, body.supplier, body.shelf_life, body.storage_method,
                body.description, body.supply_status, int(body.active), admin["id"],
            ),
        )
        conn.execute(
            "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, NULL, ?, ?)",
            (str(uuid4()), product_id, body.price_cents, admin["id"]),
        )
        conn.commit()
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.put("/admin/products/{product_id}")
def update_product(product_id: str, body: ProductUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    expected_version = fields.pop("expected_version", None)
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_expected_product_version(existing, expected_version)
        validate_product_payload(fields, existing)
        if fields:
            assignments = ", ".join(f"{key} = ?" for key in fields)
            values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
            conn.execute(f"UPDATE products SET {assignments}, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, product_id))
        if "price_cents" in fields and fields["price_cents"] != existing["price_cents"]:
            conn.execute(
                "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, ?, ?, ?)",
                (str(uuid4()), product_id, existing["price_cents"], fields["price_cents"], admin["id"]),
            )
        if "stock_quantity" in fields and fields["stock_quantity"] != existing["stock_quantity"]:
            delta = as_decimal(fields["stock_quantity"]) - as_decimal(existing["stock_quantity"])
            log_inventory(conn, product_id, None, "admin_adjust", delta, admin["id"], "编辑食材库存")
        conn.commit()
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/image")
async def upload_image(product_id: str, file: UploadFile, admin=Depends(require_admin_user)):
    max_mb = int(os.getenv("MAX_UPLOAD_MB", "5"))
    path = await save_upload(file, max_mb=max_mb)
    with connect() as conn:
        conn.execute("UPDATE products SET image_path = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (path, product_id))
        conn.commit()
        invalidate_dashboard_cache()
        return {"image_path": path}


@router.patch("/admin/products/{product_id}/status")
def patch_status(product_id: str, body: ProductStatusPatch, admin=Depends(require_admin_user)):
    if body.supply_status not in EDITABLE_SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="供应状态不正确")
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_expected_product_version(existing, body.expected_version)
        ensure_can_supply(int(existing["price_cents"]), body.supply_status, body.active)
        conn.execute(
            "UPDATE products SET supply_status = ?, active = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (body.supply_status, int(body.active), product_id),
        )
        conn.commit()
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/restore")
def restore_product(product_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_can_supply(int(existing["price_cents"]), "normal", True)
        conn.execute(
            "UPDATE products SET is_deleted = 0, active = 1, supply_status = 'normal', version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (product_id,),
        )
        conn.commit()
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.patch("/admin/products/{product_id}/price")
def patch_price(product_id: str, body: ProductPricePatch, admin=Depends(require_admin_user)):
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_expected_product_version(existing, body.expected_version)
        if body.price_cents != existing["price_cents"]:
            conn.execute(
                "UPDATE products SET price_cents = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                (body.price_cents, product_id),
            )
            conn.execute(
                "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, ?, ?, ?)",
                (str(uuid4()), product_id, existing["price_cents"], body.price_cents, admin["id"]),
            )
            conn.commit()
            invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.patch("/admin/products/{product_id}/stock")
def patch_stock(product_id: str, body: ProductStockPatch, admin=Depends(require_admin_user)):
    new_stock = as_decimal(body.stock_quantity)
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_expected_product_version(existing, body.expected_version)
        reserved = as_decimal(existing["reserved_quantity"])
        if new_stock < reserved:
            raise HTTPException(status_code=409, detail="库存不能小于已预占库存")
        delta = new_stock - as_decimal(existing["stock_quantity"])
        conn.execute(
            "UPDATE products SET stock_quantity = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (decimal_text(new_stock), product_id),
        )
        log_inventory(conn, product_id, None, "admin_adjust", delta, admin["id"], body.detail)
        conn.commit()
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.delete("/admin/products/{product_id}")
def delete_product(product_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        conn.execute("UPDATE products SET is_deleted = 1, active = 0, supply_status = 'off_shelf', version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (product_id,))
        conn.commit()
        invalidate_dashboard_cache()
    return {"ok": True}
