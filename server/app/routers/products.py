import os
import json
from decimal import Decimal
from datetime import datetime, timezone
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, UploadFile

from ..database import all_rows, connect, one, transaction
from ..dependencies import current_user, require_admin_user
from ..models import SUPPLY_STATUSES
from ..schemas import ProductCreate, ProductInventoryAdjust, ProductPricePatch, ProductStatusPatch, ProductStockPatch, ProductUpdate
from ..services.images import save_upload
from ..services.inventory import as_decimal, decimal_text, log_inventory

router = APIRouter(tags=["products"])


def product_out(product: dict) -> dict:
    stock = Decimal(product["stock_quantity"])
    reserved = Decimal(product["reserved_quantity"])
    return {**product, "active": bool(product["active"]), "is_deleted": bool(product["is_deleted"]), "available_quantity": str((stock - reserved).normalize())}


def ensure_can_supply(price_cents: int, supply_status: str, active: bool):
    if active and supply_status in ("normal", "tight") and price_cents <= 0:
        raise HTTPException(status_code=400, detail="请先填写商品价格")


def now_text() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def conflict():
    raise HTTPException(status_code=409, detail="该食材刚刚被其他管理员修改，请刷新后重试")


def audit(conn, admin: dict, action: str, product_id: str, before: dict | None = None, after: dict | None = None):
    conn.execute(
        """
        INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, before_json, after_json)
        VALUES (?, ?, ?, ?, 'product', ?, ?, ?)
        """,
        (
            str(uuid4()),
            admin["id"],
            admin["role"],
            action,
            product_id,
            json.dumps(before or {}, ensure_ascii=False),
            json.dumps(after or {}, ensure_ascii=False),
        ),
    )


def generate_product_code(conn) -> str:
    day = datetime.now().strftime("%Y%m%d")
    name = f"product-P{day}"
    conn.execute("INSERT OR IGNORE INTO app_sequences(name, value) VALUES (?, 0)", (name,))
    conn.execute("UPDATE app_sequences SET value = value + 1 WHERE name = ?", (name,))
    row = one(conn, "SELECT value FROM app_sequences WHERE name = ?", (name,))
    return f"P{day}-{int(row['value']):04d}"


def require_fresh(existing: dict, expected_updated_at: str | None):
    if expected_updated_at and expected_updated_at != existing["updated_at"]:
        conflict()


@router.get("/products")
def list_products(user=Depends(current_user), category: str | None = None, q: str | None = None):
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
    if body.supply_status not in SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="供应状态不正确")
    ensure_can_supply(body.price_cents, body.supply_status, body.active)
    product_id = str(uuid4())
    with connect() as conn:
        product_code = (body.product_code or "").strip() or generate_product_code(conn)
        conn.execute(
            """
            INSERT INTO products(id, product_code, name, category, spec, unit, price_cents, stock_quantity,
              reserved_quantity, min_order_quantity, quantity_step, warning_quantity, origin, supplier,
              shelf_life, storage_method, description, supply_status, active, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                product_id, product_code, body.name.strip(), body.category, body.spec.strip(), body.unit, body.price_cents,
                body.stock_quantity, body.reserved_quantity, body.min_order_quantity, body.quantity_step,
                body.warning_quantity, body.origin, body.supplier, body.shelf_life, body.storage_method,
                body.description, body.supply_status, int(body.active), admin["id"],
            ),
        )
        conn.execute(
            "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, reason, actor_id) VALUES (?, ?, NULL, ?, ?, ?)",
            (str(uuid4()), product_id, body.price_cents, "创建商品", admin["id"]),
        )
        audit(conn, admin, "PRODUCT_CREATED", product_id, after={"price_cents": body.price_cents, "stock_quantity": body.stock_quantity})
        conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.put("/admin/products/{product_id}")
def update_product(product_id: str, body: ProductUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    expected_updated_at = fields.pop("expected_updated_at", None)
    for critical in ("price_cents", "stock_quantity", "reserved_quantity", "supply_status", "active"):
        fields.pop(critical, None)
    allowed = {
        "product_code", "name", "category", "spec", "unit", "min_order_quantity", "quantity_step",
        "warning_quantity", "origin", "supplier", "shelf_life", "storage_method", "description",
    }
    fields = {key: value for key, value in fields.items() if key in allowed}
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        require_fresh(existing, expected_updated_at)
        if fields:
            assignments = ", ".join(f"{key} = ?" for key in fields)
            values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
            conn.execute(f"UPDATE products SET {assignments}, updated_at = ? WHERE id = ?", (*values, now_text(), product_id))
            audit(conn, admin, "PRODUCT_UPDATED", product_id, before={key: existing.get(key) for key in fields}, after=fields)
        conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/image")
async def upload_image(product_id: str, file: UploadFile, admin=Depends(require_admin_user)):
    max_mb = int(os.getenv("MAX_UPLOAD_MB", "5"))
    path = await save_upload(file, max_mb=max_mb)
    with connect() as conn:
        conn.execute("UPDATE products SET image_path = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (path, product_id))
        conn.commit()
        return {"image_path": path}


@router.patch("/admin/products/{product_id}/status")
def patch_status(product_id: str, body: ProductStatusPatch, admin=Depends(require_admin_user)):
    if body.supply_status not in SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="供应状态不正确")
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        ensure_can_supply(int(existing["price_cents"]), body.supply_status, body.active)
        new_status = body.supply_status
        new_active = body.active
        if body.active and body.supply_status in ("normal", "tight"):
            stock = as_decimal(existing["stock_quantity"])
            warning = as_decimal(existing["warning_quantity"])
            new_status = "tight" if warning > 0 and stock <= warning else "normal"
        if not body.active:
            new_status = "off_shelf"
        conn.execute(
            "UPDATE products SET supply_status = ?, active = ?, updated_at = ? WHERE id = ?",
            (new_status, int(new_active), now_text(), product_id),
        )
        audit(conn, admin, "PRODUCT_PUBLISHED" if new_active else "PRODUCT_UNPUBLISHED", product_id, before={"supply_status": existing["supply_status"], "active": bool(existing["active"])}, after={"supply_status": new_status, "active": new_active})
        conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/restore")
def restore_product(product_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        conn.execute(
            "UPDATE products SET is_deleted = 0, active = 0, supply_status = 'paused', updated_at = ? WHERE id = ?",
            (now_text(), product_id),
        )
        audit(conn, admin, "PRODUCT_RESTORED", product_id, before={"is_deleted": bool(existing["is_deleted"])}, after={"is_deleted": False, "active": False, "supply_status": "paused"})
        conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.patch("/admin/products/{product_id}/price")
def patch_price(product_id: str, body: ProductPricePatch, admin=Depends(require_admin_user)):
    if not body.reason.strip():
        raise HTTPException(status_code=400, detail="请填写价格调整原因")
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        require_fresh(existing, body.expected_updated_at)
        if body.price_cents != existing["price_cents"]:
            conn.execute(
                "UPDATE products SET price_cents = ?, updated_at = ? WHERE id = ?",
                (body.price_cents, now_text(), product_id),
            )
            conn.execute(
                "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, reason, actor_id) VALUES (?, ?, ?, ?, ?, ?)",
                (str(uuid4()), product_id, existing["price_cents"], body.price_cents, body.reason.strip(), admin["id"]),
            )
            audit(conn, admin, "PRODUCT_PRICE_CHANGED", product_id, before={"price_cents": existing["price_cents"]}, after={"price_cents": body.price_cents, "reason": body.reason.strip()})
            conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.patch("/admin/products/{product_id}/stock")
def patch_stock(product_id: str, body: ProductStockPatch, admin=Depends(require_admin_user)):
    new_stock = as_decimal(body.stock_quantity)
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        reserved = as_decimal(existing["reserved_quantity"])
        if new_stock < reserved:
            raise HTTPException(status_code=409, detail="库存不能小于已预占库存")
        delta = new_stock - as_decimal(existing["stock_quantity"])
        conn.execute(
            "UPDATE products SET stock_quantity = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (decimal_text(new_stock), product_id),
        )
        log_inventory(conn, product_id, None, "admin_adjust", delta, admin["id"], body.detail)
        conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.post("/admin/products/{product_id}/inventory-adjust")
def adjust_inventory(product_id: str, body: ProductInventoryAdjust, admin=Depends(require_admin_user)):
    if body.mode not in ("set", "increase", "decrease"):
        raise HTTPException(status_code=400, detail="库存调整方式不正确")
    if not body.reason.strip():
        raise HTTPException(status_code=400, detail="请填写库存调整原因")
    quantity = as_decimal(body.quantity)
    if quantity < 0:
        raise HTTPException(status_code=400, detail="库存数量不能小于 0")
    with transaction() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="食材不存在")
        require_fresh(existing, body.expected_updated_at)
        before = as_decimal(existing["stock_quantity"])
        reserved = as_decimal(existing["reserved_quantity"])
        if body.mode == "set":
            after = quantity
            change = after - before
        elif body.mode == "increase":
            change = quantity
            after = before + quantity
        else:
            change = -quantity
            after = before - quantity
        if after < 0:
            raise HTTPException(status_code=400, detail="库存数量不能小于 0")
        if after < reserved:
            raise HTTPException(status_code=409, detail="库存不能小于已预占库存")
        conn.execute(
            "UPDATE products SET stock_quantity = ?, updated_at = ? WHERE id = ?",
            (decimal_text(after), now_text(), product_id),
        )
        conn.execute(
            """
            INSERT INTO inventory_logs(id, product_id, order_id, action, quantity, detail, mode, before_quantity, after_quantity, reserved_quantity, actor_id)
            VALUES (?, ?, NULL, 'admin_adjust', ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()), product_id, decimal_text(change), body.reason.strip(), body.mode,
                decimal_text(before), decimal_text(after), decimal_text(reserved), admin["id"],
            ),
        )
        audit(
            conn,
            admin,
            "PRODUCT_INVENTORY_ADJUSTED",
            product_id,
            before={"stock_quantity": decimal_text(before), "reserved_quantity": decimal_text(reserved)},
            after={"stock_quantity": decimal_text(after), "mode": body.mode, "change_quantity": decimal_text(change), "reason": body.reason.strip()},
        )
        product = product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))
        return {
            "product": product,
            "before_stock_quantity": decimal_text(before),
            "after_stock_quantity": decimal_text(after),
            "reserved_quantity": decimal_text(reserved),
            "available_quantity": product["available_quantity"],
        }


@router.delete("/admin/products/{product_id}")
def delete_product(product_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        conn.execute("UPDATE products SET is_deleted = 1, active = 0, supply_status = 'off_shelf', updated_at = ? WHERE id = ?", (now_text(), product_id))
        audit(conn, admin, "PRODUCT_DELETED", product_id, before={"is_deleted": bool(existing["is_deleted"]) if existing else False}, after={"is_deleted": True})
        conn.commit()
    return {"ok": True}
