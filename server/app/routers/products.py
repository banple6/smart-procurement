import os
import hashlib
import json
from decimal import Decimal, InvalidOperation
from urllib.parse import quote
from uuid import uuid4

from fastapi import APIRouter, Depends, Header, HTTPException, Response, UploadFile

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import current_user, require_admin_user
from ..models import EDITABLE_SUPPLY_STATUSES, PRODUCT_CATEGORIES, PRODUCT_STORAGE_METHODS, PRODUCT_UNITS, resolve_product_spec
from ..schemas import (
    ProductBatchDelete,
    ProductCreate,
    ProductDeleteAll,
    ProductPricePatch,
    ProductStatusPatch,
    ProductStockPatch,
    ProductUpdate,
)
from ..services.dashboard_cache import invalidate_dashboard_cache
from ..services.exports import product_import_template_workbook, product_menu_workbook
from ..services.images import save_upload
from ..services.inventory import as_decimal, decimal_text, log_inventory
from ..services.local_time import local_now

router = APIRouter(tags=["products"])


def _archive_products(conn, product_ids: list[str], admin: dict, action: str) -> dict:
    unique_ids = list(dict.fromkeys(product_ids))
    placeholders = ",".join("?" for _ in unique_ids)
    rows = all_rows(conn, f"SELECT id, name, is_deleted FROM products WHERE id IN ({placeholders})", unique_ids)
    found_ids = {row["id"] for row in rows}
    missing = [product_id for product_id in unique_ids if product_id not in found_ids]
    if missing:
        raise HTTPException(status_code=404, detail="部分食材不存在，请刷新后重试")
    active_ids = [row["id"] for row in rows if not bool(row["is_deleted"])]
    if active_ids:
        active_placeholders = ",".join("?" for _ in active_ids)
        conn.execute(
            f"""
            UPDATE products
            SET is_deleted = 1, active = 0, supply_status = 'off_shelf',
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id IN ({active_placeholders}) AND is_deleted = 0
            """,
            active_ids,
        )
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            action,
            "product_batch",
            after_json=json.dumps(
                {"archived_count": len(active_ids), "product_ids": active_ids},
                ensure_ascii=False,
                separators=(",", ":"),
            ),
        )
    return {
        "ok": True,
        "archived_count": len(active_ids),
        "already_archived_count": len(unique_ids) - len(active_ids),
        "requested_count": len(unique_ids),
    }


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


def update_current_product(conn, assignments: str, values: list, product_id: str, expected_version: int | None) -> None:
    """Apply a product mutation only to the revision the caller reviewed."""
    where = "id = ?"
    params = [*values, product_id]
    if expected_version is not None:
        where += " AND version = ?"
        params.append(expected_version)
    cursor = conn.execute(
        f"UPDATE products SET {assignments}, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE {where}",
        params,
    )
    if cursor.rowcount != 1:
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


@router.get("/admin/products/import-template.xlsx")
def download_product_import_template(admin=Depends(require_admin_user)):
    return Response(
        product_import_template_workbook(),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote('三公鲜配_食材导入模板.xlsx')}"},
    )


@router.get("/admin/products/export.xlsx")
def export_product_menu(admin=Depends(require_admin_user)):
    with connect() as conn:
        rows = all_rows(
            conn,
            """
            SELECT name, spec, price_cents
            FROM products
            WHERE is_deleted = 0
              AND active = 1
              AND supply_status IN ('normal', 'tight')
            ORDER BY created_at DESC
            """,
        )
    filename_date = local_now().strftime("%Y%m%d")
    return Response(
        product_menu_workbook(rows),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": f"attachment; filename*=UTF-8''{quote(f'三公鲜配商品菜单_{filename_date}.xlsx')}"},
    )


@router.get("/products/{product_id}")
def product_detail(product_id: str, user=Depends(current_user)):
    with connect() as conn:
        product = one(conn, "SELECT * FROM products WHERE id = ? AND is_deleted = 0", (product_id,))
    if not product:
        raise HTTPException(status_code=404, detail="食材不存在")
    return product_out(product)


@router.post("/admin/products")
def create_product(body: ProductCreate, admin=Depends(require_admin_user)):
    fields = body.model_dump()
    fields["spec"] = resolve_product_spec(fields["unit"], fields.get("spec"))
    validate_product_payload(fields)
    product_id = str(uuid4())
    with transaction() as conn:
        conn.execute(
            """
            INSERT INTO products(id, product_code, name, category, spec, unit, price_cents, stock_quantity,
              reserved_quantity, min_order_quantity, quantity_step, warning_quantity, origin, supplier,
              shelf_life, storage_method, description, supply_status, active, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                product_id, fields["product_code"], fields["name"], fields["category"], fields["spec"], fields["unit"], fields["price_cents"],
                fields["stock_quantity"], fields["reserved_quantity"], fields["min_order_quantity"], fields["quantity_step"],
                fields["warning_quantity"], fields["origin"], fields["supplier"], fields["shelf_life"], fields["storage_method"],
                fields["description"], fields["supply_status"], int(fields["active"]), admin["id"],
            ),
        )
        conn.execute(
            "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, NULL, ?, ?)",
            (str(uuid4()), product_id, fields["price_cents"], admin["id"]),
        )
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.put("/admin/products/{product_id}")
def update_product(product_id: str, body: ProductUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    expected_version = fields.pop("expected_version", None)
    with transaction() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_expected_product_version(existing, expected_version)
        if "unit" in fields or "spec" in fields:
            next_unit = fields.get("unit", existing["unit"])
            fields["spec"] = resolve_product_spec(next_unit, fields.get("spec"), existing.get("spec"))
        validate_product_payload(fields, existing)
        if fields:
            assignments = ", ".join(f"{key} = ?" for key in fields)
            values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
            update_current_product(conn, assignments, values, product_id, expected_version)
        if "price_cents" in fields and fields["price_cents"] != existing["price_cents"]:
            conn.execute(
                "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, ?, ?, ?)",
                (str(uuid4()), product_id, existing["price_cents"], fields["price_cents"], admin["id"]),
            )
        if "stock_quantity" in fields and fields["stock_quantity"] != existing["stock_quantity"]:
            delta = as_decimal(fields["stock_quantity"]) - as_decimal(existing["stock_quantity"])
            log_inventory(conn, product_id, None, "admin_adjust", delta, admin["id"], "编辑食材库存")
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/image")
async def upload_image(product_id: str, file: UploadFile, admin=Depends(require_admin_user)):
    max_mb = int(os.getenv("MAX_UPLOAD_MB", "5"))
    path = await save_upload(file, max_mb=max_mb)
    with transaction() as conn:
        conn.execute("UPDATE products SET image_path = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (path, product_id))
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
        update_current_product(
            conn,
            "supply_status = ?, active = ?",
            [body.supply_status, int(body.active)],
            product_id,
            body.expected_version,
        )
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/restore")
def restore_product(product_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
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
            update_current_product(conn, "price_cents = ?", [body.price_cents], product_id, body.expected_version)
            conn.execute(
                "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, ?, ?, ?)",
                (str(uuid4()), product_id, existing["price_cents"], body.price_cents, admin["id"]),
            )
            invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.patch("/admin/products/{product_id}/stock")
def patch_stock(product_id: str, body: ProductStockPatch, admin=Depends(require_admin_user)):
    new_stock = as_decimal(body.stock_quantity)
    with transaction() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_expected_product_version(existing, body.expected_version)
        reserved = as_decimal(existing["reserved_quantity"])
        if new_stock < reserved:
            raise HTTPException(status_code=409, detail="库存不能小于已预占库存")
        delta = new_stock - as_decimal(existing["stock_quantity"])
        update_current_product(conn, "stock_quantity = ?", [decimal_text(new_stock)], product_id, body.expected_version)
        log_inventory(conn, product_id, None, "admin_adjust", delta, admin["id"], body.detail)
        invalidate_dashboard_cache()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.delete("/admin/products/batch")
def delete_products_batch(body: ProductBatchDelete, admin=Depends(require_admin_user)):
    if not body.confirmed:
        raise HTTPException(status_code=400, detail="请确认后再批量删除食材")
    with transaction() as conn:
        result = _archive_products(conn, body.ids, admin, "PRODUCTS_BATCH_ARCHIVED")
        invalidate_dashboard_cache()
        return result


@router.delete("/admin/products/all")
def delete_all_products(body: ProductDeleteAll, admin=Depends(require_admin_user)):
    if not body.confirmed or body.confirmation_text.strip() != "确认删除":
        raise HTTPException(status_code=400, detail="请输入“确认删除”后再清空食材")
    with transaction() as conn:
        rows = all_rows(conn, "SELECT id FROM products WHERE is_deleted = 0 ORDER BY id")
        current_count = len(rows)
        if current_count != body.expected_count:
            raise HTTPException(status_code=409, detail="食材数量已变化，请刷新后重新确认")
        if not rows:
            return {"ok": True, "archived_count": 0, "already_archived_count": 0, "requested_count": 0}
        result = _archive_products(conn, [row["id"] for row in rows], admin, "PRODUCT_CATALOG_ARCHIVED")
        invalidate_dashboard_cache()
        return result


@router.delete("/admin/products/{product_id}")
def delete_product(product_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        result = _archive_products(conn, [product_id], admin, "PRODUCT_ARCHIVED")
        invalidate_dashboard_cache()
        return result
