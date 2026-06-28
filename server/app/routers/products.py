import os
from decimal import Decimal
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, UploadFile

from ..database import all_rows, connect, one
from ..dependencies import current_user, require_admin_user
from ..models import SUPPLY_STATUSES
from ..schemas import ProductCreate, ProductStatusPatch, ProductUpdate
from ..services.images import save_upload

router = APIRouter(tags=["products"])


def product_out(product: dict) -> dict:
    stock = Decimal(product["stock_quantity"])
    reserved = Decimal(product["reserved_quantity"])
    return {**product, "active": bool(product["active"]), "is_deleted": bool(product["is_deleted"]), "available_quantity": str((stock - reserved).normalize())}


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
        raise HTTPException(status_code=404, detail="Product not found")
    return product_out(product)


@router.post("/admin/products")
def create_product(body: ProductCreate, admin=Depends(require_admin_user)):
    if body.supply_status not in SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="Invalid supply status")
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
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.put("/admin/products/{product_id}")
def update_product(product_id: str, body: ProductUpdate, admin=Depends(require_admin_user)):
    fields = body.model_dump(exclude_unset=True)
    if "supply_status" in fields and fields["supply_status"] not in SUPPLY_STATUSES:
        raise HTTPException(status_code=400, detail="Invalid supply status")
    with connect() as conn:
        existing = one(conn, "SELECT * FROM products WHERE id = ?", (product_id,))
        if not existing:
            raise HTTPException(status_code=404, detail="Product not found")
        if fields:
            assignments = ", ".join(f"{key} = ?" for key in fields)
            values = [int(v) if isinstance(v, bool) else v for v in fields.values()]
            conn.execute(f"UPDATE products SET {assignments}, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (*values, product_id))
        if "price_cents" in fields and fields["price_cents"] != existing["price_cents"]:
            conn.execute(
                "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, actor_id) VALUES (?, ?, ?, ?, ?)",
                (str(uuid4()), product_id, existing["price_cents"], fields["price_cents"], admin["id"]),
            )
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
        raise HTTPException(status_code=400, detail="Invalid supply status")
    with connect() as conn:
        conn.execute(
            "UPDATE products SET supply_status = ?, active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (body.supply_status, int(body.active), product_id),
        )
        conn.commit()
        return product_out(one(conn, "SELECT * FROM products WHERE id = ?", (product_id,)))


@router.delete("/admin/products/{product_id}")
def delete_product(product_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        conn.execute("UPDATE products SET is_deleted = 1, active = 0, supply_status = 'off_shelf', updated_at = CURRENT_TIMESTAMP WHERE id = ?", (product_id,))
        conn.commit()
    return {"ok": True}
