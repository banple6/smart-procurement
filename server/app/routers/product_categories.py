import json
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import require_admin_user
from ..schemas import ProductCategoryCreate, ProductCategoryUpdate
from ..services.product_categories import LEGACY_ANDROID_CATEGORIES, normalize_category_name


router = APIRouter(prefix="/admin/product-categories", tags=["product-categories"])


def _category(category_id: str, conn):
    row = one(conn, "SELECT * FROM product_categories WHERE id = ?", (category_id,))
    if not row:
        raise HTTPException(status_code=404, detail="分类不存在")
    row["is_active"] = bool(row["is_active"])
    row["legacy_protected"] = row["name"] in LEGACY_ANDROID_CATEGORIES
    return row


def _assert_mutable(row: dict):
    if row["name"] in LEGACY_ANDROID_CATEGORIES:
        raise HTTPException(status_code=409, detail="当前分类仍需兼容旧版 App，暂不可修改名称或停用。")


@router.get("")
def list_product_categories(admin=Depends(require_admin_user)):
    with connect() as conn:
        rows = all_rows(conn, "SELECT id FROM product_categories ORDER BY sort_order, name, id")
        return [_category(row["id"], conn) for row in rows]


@router.post("")
def create_product_category(body: ProductCategoryCreate, admin=Depends(require_admin_user)):
    name = normalize_category_name(body.name)
    with transaction() as conn:
        if one(conn, "SELECT id FROM product_categories WHERE name = ?", (name,)):
            raise HTTPException(status_code=409, detail="分类已存在")
        sort_order = body.sort_order
        if sort_order is None:
            sort_order = int(one(conn, "SELECT COALESCE(MAX(sort_order), 0) AS value FROM product_categories")["value"]) + 10
        category_id = str(uuid4())
        conn.execute("INSERT INTO product_categories(id, name, sort_order, is_active) VALUES (?, ?, ?, 1)", (category_id, name, sort_order))
        write_audit(conn, admin["id"], admin["role"], "PRODUCT_CATEGORY_CREATED", "product_category", category_id, after_json=json.dumps({"name": name, "sort_order": sort_order}, ensure_ascii=False))
        return _category(category_id, conn)


@router.patch("/{category_id}")
def update_product_category(category_id: str, body: ProductCategoryUpdate, admin=Depends(require_admin_user)):
    values = body.model_dump(exclude_unset=True)
    with transaction() as conn:
        current = _category(category_id, conn)
        if "name" in values:
            _assert_mutable(current)
            name = normalize_category_name(values["name"])
            if name != current["name"] and one(conn, "SELECT id FROM product_categories WHERE name = ?", (name,)):
                raise HTTPException(status_code=409, detail="分类已存在")
            if name != current["name"]:
                conn.execute("UPDATE product_categories SET name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (name, category_id))
                conn.execute("UPDATE products SET category = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE category = ? AND is_deleted = 0", (name, current["name"]))
        if "is_active" in values:
            if not values["is_active"]:
                _assert_mutable(current)
            conn.execute("UPDATE product_categories SET is_active = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (int(values["is_active"]), category_id))
        if "sort_order" in values:
            conn.execute("UPDATE product_categories SET sort_order = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (values["sort_order"], category_id))
        write_audit(conn, admin["id"], admin["role"], "PRODUCT_CATEGORY_UPDATED", "product_category", category_id, before_json=json.dumps({"name": current["name"]}, ensure_ascii=False), after_json=json.dumps(values, ensure_ascii=False))
        return _category(category_id, conn)
