from decimal import Decimal
from datetime import datetime
import json
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse, JSONResponse

from ..database import all_rows, one, transaction, connect
from ..dependencies import current_user, require_admin_user, require_unit_user
from ..models import ADMIN_TRANSITIONS
from ..schemas import OrderCreate, OrderItemActualQuantityPatch, OrderStatusPatch, ReceiptIssueResolve
from ..services.inventory import as_decimal, complete_product, decimal_text, release_product, reserve_product, validate_order_quantity, available
from ..services.procurement import cutoff_payload
from ..services.shipping_photos import cleanup_photos, process_receipt_issue_uploads, process_shipping_uploads, resolve_private_path

router = APIRouter(tags=["orders"])


def order_no(conn) -> str:
    prefix = "SP" + datetime.now().strftime("%Y%m%d")
    name = f"order-{prefix}"
    conn.execute("INSERT OR IGNORE INTO app_sequences(name, value) VALUES (?, 0)", (name,))
    conn.execute("UPDATE app_sequences SET value = value + 1 WHERE name = ?", (name,))
    row = one(conn, "SELECT value FROM app_sequences WHERE name = ?", (name,))
    return f"{prefix}-{int(row['value']):06d}"


def get_unit(conn, unit_id: str):
    unit = one(conn, "SELECT * FROM units WHERE id = ? AND active = 1", (unit_id,))
    if not unit:
        raise HTTPException(status_code=403, detail="所属单位已停用，请联系管理员")
    return unit


def order_items(conn, order_id: str):
    rows = all_rows(conn, "SELECT * FROM order_items WHERE order_id = ? ORDER BY rowid", (order_id,))
    for row in rows:
        row["requested_quantity"] = row.get("requested_quantity") or row["quantity"]
        row["actual_quantity"] = row.get("actual_quantity") or row["quantity"]
        row["adjusted"] = row["requested_quantity"] != row["actual_quantity"]
    return rows


def item_actual_quantity(item: dict) -> Decimal:
    return as_decimal(item.get("actual_quantity") or item["quantity"])


def shipping_photo_rows(conn, order_id: str):
    return all_rows(
        conn,
        """
        SELECT p.*, users.username AS uploaded_by_username
        FROM order_shipping_photos p
        LEFT JOIN users ON users.id = p.uploaded_by
        WHERE p.order_id = ?
        ORDER BY p.uploaded_at, p.rowid
        """,
        (order_id,),
    )


def shipping_photo_out(order_id: str, row: dict) -> dict:
    return {
        "id": row["id"],
        "thumbnail_url": f"/api/v1/orders/{order_id}/shipping-photos/{row['id']}?variant=thumbnail",
        "full_url": f"/api/v1/orders/{order_id}/shipping-photos/{row['id']}?variant=full",
        "uploaded_at": row["uploaded_at"],
        "uploaded_by_username": row.get("uploaded_by_username") or "",
        "source": row["source"],
    }


def order_out(conn, order: dict, include_items: bool = True, include_shipping_photos: bool = True) -> dict:
    username = one(conn, "SELECT username FROM users WHERE id = ?", (order.get("created_by"),))
    item_count = one(conn, "SELECT COUNT(*) AS c FROM order_items WHERE order_id = ?", (order["id"],))["c"]
    photo_count = one(conn, "SELECT COUNT(*) AS c FROM order_shipping_photos WHERE order_id = ?", (order["id"],))["c"]
    issue_count = one(conn, "SELECT COUNT(*) AS c FROM receipt_issues WHERE order_id = ? AND status = 'open'", (order["id"],))["c"]
    adjustment_count = one(
        conn,
        """
        SELECT COUNT(*) AS c
        FROM order_items
        WHERE order_id = ?
          AND COALESCE(NULLIF(requested_quantity, ''), quantity) != COALESCE(NULLIF(actual_quantity, ''), quantity)
        """,
        (order["id"],),
    )["c"]
    payload = {
        **order,
        "created_by_username": username["username"] if username else "",
        "item_count": item_count,
        "shipping_note": order.get("shipping_note") or "",
        "shipping_photo_count": photo_count,
        "open_receipt_issue_count": issue_count,
        "has_adjustments": adjustment_count > 0,
    }
    payload.pop("created_by", None)
    if include_items:
        payload["items"] = order_items(conn, order["id"])
    if include_shipping_photos:
        payload["shipping_photos"] = [shipping_photo_out(order["id"], row) for row in shipping_photo_rows(conn, order["id"])]
    payload["receipt_issues"] = receipt_issue_rows(conn, order["id"]) if include_items else []
    return payload


def receipt_issue_photo_out(order_id: str, issue_id: str, row: dict) -> dict:
    return {
        "id": row["id"],
        "thumbnail_url": f"/api/v1/orders/{order_id}/receipt-issues/{issue_id}/photos/{row['id']}?variant=thumbnail",
        "full_url": f"/api/v1/orders/{order_id}/receipt-issues/{issue_id}/photos/{row['id']}?variant=full",
        "uploaded_at": row["uploaded_at"],
        "source": row["source"],
    }


def receipt_issue_rows(conn, order_id: str) -> list[dict]:
    rows = all_rows(conn, "SELECT * FROM receipt_issues WHERE order_id = ? ORDER BY reported_at DESC", (order_id,))
    for row in rows:
        photos = all_rows(conn, "SELECT * FROM receipt_issue_photos WHERE issue_id = ? ORDER BY uploaded_at, rowid", (row["id"],))
        row["photos"] = [receipt_issue_photo_out(order_id, row["id"], photo) for photo in photos]
    return rows


def order_list(conn, sql: str, params: list, include_items: bool, page: int, page_size: int) -> dict:
    count_sql = f"SELECT COUNT(*) AS c FROM ({sql}) AS filtered_orders"
    total = one(conn, count_sql, params)["c"]
    rows = all_rows(conn, sql + " ORDER BY created_at DESC LIMIT ? OFFSET ?", (*params, page_size, (page - 1) * page_size))
    rows = [order_out(conn, row, include_items=include_items, include_shipping_photos=False) for row in rows]
    return {"items": rows, "total": total, "page": page, "page_size": page_size}


def fetch_order_for_user(conn, order_id: str, user: dict):
    order = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在或无权查看")
    if user["role"] == "unit_user" and order["unit_id"] != user["unit_id"]:
        raise HTTPException(status_code=404, detail="订单不存在或无权查看")
    return order


def create_order_rows(conn, body: OrderCreate, user: dict, existing_order_id: str | None = None):
    if not body.items:
        raise HTTPException(status_code=400, detail="请先选择食材")
    unit = get_unit(conn, user["unit_id"])
    if body.client_request_id and not existing_order_id:
        existing = one(
            conn,
            "SELECT * FROM orders WHERE client_request_id = ? AND created_by = ?",
            (body.client_request_id, user["id"]),
        )
        if existing:
            return existing
    order_id = existing_order_id or str(uuid4())
    total = 0
    items_payload = []
    for item in body.items:
        quantity = as_decimal(item.quantity)
        product = reserve_product(conn, item.product_id, quantity, order_id, user["id"])
        subtotal = int((Decimal(product["price_cents"]) * quantity).to_integral_value())
        total += subtotal
        items_payload.append((product, quantity, subtotal))

    if existing_order_id:
        conn.execute("DELETE FROM order_items WHERE order_id = ?", (existing_order_id,))
        conn.execute(
            "UPDATE orders SET total_cents = ?, note = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (total, body.note, existing_order_id),
        )
    else:
        conn.execute(
            """
            INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot, note, status, total_cents, created_by, client_request_id)
            VALUES (?, ?, ?, ?, ?, ?, 'pending', ?, ?, ?)
            """,
            (order_id, order_no(conn), unit["id"], unit["unit_name"], unit["default_delivery_point"], body.note, total, user["id"], body.client_request_id),
        )
    for product, quantity, subtotal in items_payload:
        conn.execute(
            """
            INSERT INTO order_items(id, order_id, product_id, product_code_snapshot, product_name_snapshot,
              category_snapshot, spec_snapshot, unit_snapshot, price_cents_snapshot, quantity, requested_quantity,
              actual_quantity, subtotal_cents)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()), order_id, product["id"], product["product_code"], product["name"], product["category"],
                product["spec"], product["unit"], product["price_cents"], decimal_text(quantity), decimal_text(quantity),
                decimal_text(quantity), subtotal,
            ),
        )
    conn.execute(
        "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (str(uuid4()), order_id, user["id"], "create" if not existing_order_id else "update", None, "pending", body.note),
    )
    return one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))


@router.post("/orders")
def create_order(body: OrderCreate, user=Depends(require_unit_user)):
    with transaction() as conn:
        cutoff = cutoff_payload(conn)
        if cutoff["is_closed"]:
            return JSONResponse(
                status_code=409,
                content={"code": "ORDER_CUTOFF_PASSED", "detail": "今日采购已经截止，请联系管理员"},
            )
        order = create_order_rows(conn, body, user)
        return order_out(conn, order)


@router.get("/orders")
def list_orders(
    include_items: bool = False,
    status: str | None = None,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=200),
    user=Depends(require_unit_user),
):
    with connect() as conn:
        sql = "SELECT * FROM orders WHERE unit_id = ?"
        params = [user["unit_id"]]
        if status:
            sql += " AND status = ?"
            params.append(status)
        return order_list(conn, sql, params, include_items, page, page_size)


@router.get("/orders/{order_id}")
def order_detail(order_id: str, user=Depends(current_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        return order_out(conn, order)


@router.get("/orders/{order_id}/reorder-preview")
def reorder_preview(order_id: str, user=Depends(require_unit_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        items = []
        for item in order_items(conn, order_id):
            product = one(conn, "SELECT * FROM products WHERE id = ? AND is_deleted = 0", (item["product_id"],))
            previous_quantity = as_decimal(item["requested_quantity"])
            available_text = "0"
            message = ""
            can_add = True
            current_price = 0
            if not product:
                can_add = False
                message = "食材已删除"
            else:
                available_qty = available(product)
                available_text = decimal_text(available_qty)
                current_price = int(product["price_cents"])
                try:
                    if not product["active"] or product["supply_status"] not in ("normal", "tight"):
                        raise HTTPException(status_code=409, detail="食材已暂停供应或下架")
                    validate_order_quantity(product, previous_quantity)
                    if available_qty < previous_quantity:
                        raise HTTPException(status_code=409, detail="库存不足，请减少数量")
                except HTTPException as exc:
                    can_add = False
                    message = str(exc.detail)
            items.append(
                {
                    "order_item_id": item["id"],
                    "product_id": item["product_id"],
                    "product_name": item["product_name_snapshot"],
                    "spec": item["spec_snapshot"],
                    "unit": item["unit_snapshot"],
                    "previous_quantity": decimal_text(previous_quantity),
                    "previous_price_cents": item["price_cents_snapshot"],
                    "current_price_cents": current_price,
                    "available_quantity": available_text,
                    "available": can_add,
                    "message": message,
                }
            )
        return {"order_id": order_id, "order_no": order["order_no"], "items": items}


@router.get("/orders/{order_id}/shipping-photos/{photo_id}")
def shipping_photo_file(
    order_id: str,
    photo_id: str,
    variant: str = Query(default="thumbnail", pattern="^(thumbnail|full)$"),
    user=Depends(current_user),
):
    with connect() as conn:
        fetch_order_for_user(conn, order_id, user)
        row = one(conn, "SELECT * FROM order_shipping_photos WHERE id = ? AND order_id = ?", (photo_id, order_id))
        if not row:
            raise HTTPException(status_code=404, detail="照片不存在")
    relative_path = row["thumbnail_path"] if variant == "thumbnail" else row["image_path"]
    path = resolve_private_path(relative_path)
    if not path.exists():
        raise HTTPException(status_code=404, detail="照片不存在")
    return FileResponse(
        path,
        media_type="image/jpeg",
        headers={
            "Cache-Control": "no-store, private",
            "Pragma": "no-cache",
        },
    )


@router.put("/orders/{order_id}")
def update_pending_order(order_id: str, body: OrderCreate, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "pending":
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        for item in order_items(conn, order_id):
            release_product(conn, item["product_id"], item_actual_quantity(item), order_id, user["id"])
        updated = create_order_rows(conn, body, user, existing_order_id=order_id)
        return order_out(conn, updated)


@router.post("/orders/{order_id}/cancel")
def cancel_order(order_id: str, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] not in ("pending",):
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        for item in order_items(conn, order_id):
            release_product(conn, item["product_id"], item_actual_quantity(item), order_id, user["id"])
        conn.execute("UPDATE orders SET status = 'cancelled', updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'cancel', ?, 'cancelled')",
            (str(uuid4()), order_id, user["id"], order["status"]),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.post("/orders/{order_id}/confirm-receipt")
def confirm_receipt(order_id: str, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "shipped":
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        open_issue = one(conn, "SELECT id FROM receipt_issues WHERE order_id = ? AND status = 'open'", (order_id,))
        if open_issue:
            raise HTTPException(status_code=409, detail="收货异常处理完成后才能确认收货")
        for item in order_items(conn, order_id):
            complete_product(conn, item["product_id"], item_actual_quantity(item), order_id, user["id"])
        conn.execute("UPDATE orders SET status = 'completed', completed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'confirm_receipt', 'shipped', 'completed')",
            (str(uuid4()), order_id, user["id"]),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.get("/admin/orders")
def admin_orders(
    include_items: bool = False,
    status: str | None = None,
    unit_id: str | None = None,
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=200),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        sql = "SELECT * FROM orders WHERE 1 = 1"
        params: list = []
        if status:
            sql += " AND status = ?"
            params.append(status)
        if unit_id:
            sql += " AND unit_id = ?"
            params.append(unit_id)
        return order_list(conn, sql, params, include_items, page, page_size)


@router.get("/admin/orders/{order_id}")
def admin_order_detail(order_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        return order_out(conn, order)


@router.patch("/admin/orders/{order_id}/items/{item_id}/actual-quantity")
def adjust_order_item_actual_quantity(
    order_id: str,
    item_id: str,
    body: OrderItemActualQuantityPatch,
    admin=Depends(require_admin_user),
):
    reason = body.reason.strip()
    if not reason:
        raise HTTPException(status_code=400, detail="请填写缺货调整原因")
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        if order["status"] not in ("pending", "accepted", "preparing"):
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        if body.expected_updated_at and body.expected_updated_at != order["updated_at"]:
            return JSONResponse(
                status_code=409,
                content={"code": "ORDER_CONFLICT", "detail": "订单刚刚被修改，请刷新后重试"},
            )
        item = one(conn, "SELECT * FROM order_items WHERE id = ? AND order_id = ?", (item_id, order_id))
        if not item:
            raise HTTPException(status_code=404, detail="订单食材不存在")
        requested = as_decimal(item.get("requested_quantity") or item["quantity"])
        before_actual = as_decimal(item.get("actual_quantity") or item["quantity"])
        after_actual = as_decimal(body.actual_quantity)
        if after_actual < 0:
            raise HTTPException(status_code=400, detail="实发数量不能小于 0")
        if after_actual > requested:
            raise HTTPException(status_code=400, detail="实发数量不能超过申领数量")
        if after_actual > before_actual:
            raise HTTPException(status_code=400, detail="缺货调整只能减少数量")
        other_actual_total = sum(
            item_actual_quantity(row)
            for row in order_items(conn, order_id)
            if row["id"] != item_id
        )
        if other_actual_total + after_actual <= 0:
            raise HTTPException(status_code=400, detail="订单不能全部调整为 0")
        delta = before_actual - after_actual
        if delta > 0:
            release_product(conn, item["product_id"], delta, order_id, admin["id"])
        subtotal = int((Decimal(item["price_cents_snapshot"]) * after_actual).to_integral_value())
        conn.execute(
            """
            UPDATE order_items
            SET actual_quantity = ?,
                quantity = ?,
                subtotal_cents = ?,
                adjustment_reason = ?,
                adjusted_by = ?,
                adjusted_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (decimal_text(after_actual), decimal_text(after_actual), subtotal, reason, admin["id"], item_id),
        )
        total = one(conn, "SELECT COALESCE(SUM(subtotal_cents), 0) AS total FROM order_items WHERE order_id = ?", (order_id,))["total"]
        conn.execute("UPDATE orders SET total_cents = ?, updated_at = strftime('%Y-%m-%d %H:%M:%f', 'now') WHERE id = ?", (total, order_id))
        conn.execute(
            """
            INSERT INTO order_item_adjustments(
              id, order_id, order_item_id, product_id, before_actual_quantity, after_actual_quantity, reason, actor_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (str(uuid4()), order_id, item_id, item["product_id"], decimal_text(before_actual), decimal_text(after_actual), reason, admin["id"]),
        )
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'adjust_item', ?, ?, ?)",
            (str(uuid4()), order_id, admin["id"], order["status"], order["status"], reason),
        )
        conn.execute(
            """
            INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, before_json, after_json, result)
            VALUES (?, ?, ?, 'order_item_adjust', 'order_item', ?, ?, ?, 'success')
            """,
            (
                str(uuid4()),
                admin["id"],
                admin["role"],
                item_id,
                json.dumps({"actual_quantity": decimal_text(before_actual)}, ensure_ascii=False),
                json.dumps({"actual_quantity": decimal_text(after_actual), "reason": reason}, ensure_ascii=False),
            ),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.patch("/admin/orders/{order_id}/status")
def admin_order_status(order_id: str, body: OrderStatusPatch, admin=Depends(require_admin_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        if body.status == "shipped":
            raise HTTPException(status_code=409, detail="请先拍摄并上传发货照片")
        allowed = ADMIN_TRANSITIONS.get(order["status"], set())
        if body.status not in allowed:
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        if body.status == "cancelled":
            for item in order_items(conn, order_id):
                release_product(conn, item["product_id"], item_actual_quantity(item), order_id, admin["id"])
        elif body.status == "completed":
            for item in order_items(conn, order_id):
                complete_product(conn, item["product_id"], item_actual_quantity(item), order_id, admin["id"])
        time_field = {
            "accepted": "accepted_at",
            "preparing": "preparing_at",
            "shipped": "shipped_at",
            "completed": "completed_at",
        }.get(body.status)
        if time_field:
            conn.execute(f"UPDATE orders SET status = ?, {time_field} = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (body.status, order_id))
        else:
            conn.execute("UPDATE orders SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (body.status, order_id))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'status', ?, ?)",
            (str(uuid4()), order_id, admin["id"], order["status"], body.status),
        )
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))


@router.post("/admin/orders/{order_id}/ship")
async def ship_order(
    order_id: str,
    photos: list[UploadFile] | None = File(default=None),
    note: str = Form(default=""),
    client_request_id: str = Form(...),
    admin=Depends(require_admin_user),
):
    if not client_request_id.strip():
        raise HTTPException(status_code=400, detail="缺少请求编号，请重试")
    request_id = client_request_id.strip()
    uploads = photos or []

    with connect() as conn:
        existing = one(conn, "SELECT * FROM orders WHERE ship_request_id = ?", (request_id,))
        if existing:
            if existing["id"] != order_id:
                raise HTTPException(status_code=409, detail="请求编号已被其他订单使用")
            return order_out(conn, existing)
        order = fetch_order_for_user(conn, order_id, admin)
        if order["status"] != "preparing":
            raise HTTPException(status_code=409, detail="只有备货中的订单才能确认发货")

    processed = await process_shipping_uploads(
        uploads,
        order_no=order["order_no"],
        unit_name=order["unit_name_snapshot"],
        operator_username=admin["username"],
    )
    try:
        with transaction() as conn:
            existing = one(conn, "SELECT * FROM orders WHERE ship_request_id = ?", (request_id,))
            if existing:
                cleanup_photos(processed)
                if existing["id"] != order_id:
                    raise HTTPException(status_code=409, detail="请求编号已被其他订单使用")
                return order_out(conn, existing)
            current = fetch_order_for_user(conn, order_id, admin)
            if current["status"] != "preparing":
                raise HTTPException(status_code=409, detail="只有备货中的订单才能确认发货")
            for photo in processed:
                conn.execute(
                    """
                    INSERT INTO order_shipping_photos(
                      id, order_id, image_path, thumbnail_path, uploaded_by, source, mime_type, file_size, width, height, sha256
                    ) VALUES (?, ?, ?, ?, ?, 'camera', ?, ?, ?, ?, ?)
                    """,
                    (
                        photo.id,
                        order_id,
                        photo.image_path,
                        photo.thumbnail_path,
                        admin["id"],
                        photo.mime_type,
                        photo.file_size,
                        photo.width,
                        photo.height,
                        photo.sha256,
                    ),
                )
            conn.execute(
                """
                UPDATE orders
                SET status = 'shipped',
                    shipped_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP,
                    shipping_note = ?,
                    ship_request_id = ?
                WHERE id = ?
                """,
                (note.strip(), request_id, order_id),
            )
            conn.execute(
                "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'ship', 'preparing', 'shipped', ?)",
                (str(uuid4()), order_id, admin["id"], note.strip()),
            )
            conn.execute(
                """
                INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, after_json, result)
                VALUES (?, ?, ?, 'order_ship', 'order', ?, ?, 'success')
                """,
                (
                    str(uuid4()),
                    admin["id"],
                    admin["role"],
                    order_id,
                    json.dumps({"shipping_photo_count": len(processed), "client_request_id": request_id}, ensure_ascii=False),
                ),
            )
            return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)))
    except Exception:
        cleanup_photos(processed)
        raise


@router.post("/orders/{order_id}/receipt-issues")
async def create_receipt_issue(
    order_id: str,
    issue_type: str = Form(...),
    description: str = Form(default=""),
    photos: list[UploadFile] | None = File(default=None),
    user=Depends(require_unit_user),
):
    uploads = photos or []
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "shipped":
            raise HTTPException(status_code=409, detail="只有已发货订单才能反馈收货异常")
        open_issue = one(conn, "SELECT id FROM receipt_issues WHERE order_id = ? AND status = 'open'", (order_id,))
        if open_issue:
            raise HTTPException(status_code=409, detail="该订单已有未处理的收货异常")

    processed = await process_receipt_issue_uploads(
        uploads,
        order_no=order["order_no"],
        unit_name=order["unit_name_snapshot"],
        reporter_username=user["username"],
    )
    try:
        with transaction() as conn:
            current = fetch_order_for_user(conn, order_id, user)
            if current["status"] != "shipped":
                raise HTTPException(status_code=409, detail="只有已发货订单才能反馈收货异常")
            open_issue = one(conn, "SELECT id FROM receipt_issues WHERE order_id = ? AND status = 'open'", (order_id,))
            if open_issue:
                raise HTTPException(status_code=409, detail="该订单已有未处理的收货异常")
            issue_id = str(uuid4())
            conn.execute(
                """
                INSERT INTO receipt_issues(id, order_id, unit_id, issue_type, description, status, reported_by)
                VALUES (?, ?, ?, ?, ?, 'open', ?)
                """,
                (issue_id, order_id, current["unit_id"], issue_type.strip(), description.strip(), user["id"]),
            )
            for photo in processed:
                conn.execute(
                    """
                    INSERT INTO receipt_issue_photos(
                      id, issue_id, image_path, thumbnail_path, uploaded_by, source, mime_type, file_size, width, height, sha256
                    ) VALUES (?, ?, ?, ?, ?, 'camera', ?, ?, ?, ?, ?)
                    """,
                    (
                        photo.id,
                        issue_id,
                        photo.image_path,
                        photo.thumbnail_path,
                        user["id"],
                        photo.mime_type,
                        photo.file_size,
                        photo.width,
                        photo.height,
                        photo.sha256,
                    ),
                )
            conn.execute(
                "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'receipt_issue', 'shipped', 'shipped', ?)",
                (str(uuid4()), order_id, user["id"], description.strip()),
            )
            conn.execute(
                """
                INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, after_json, result)
                VALUES (?, ?, ?, 'receipt_issue_create', 'receipt_issue', ?, ?, 'success')
                """,
                (str(uuid4()), user["id"], user["role"], issue_id, json.dumps({"photo_count": len(processed)}, ensure_ascii=False)),
            )
            return receipt_issue_rows(conn, order_id)[0]
    except Exception:
        cleanup_photos(processed)
        raise


@router.get("/orders/{order_id}/receipt-issues")
def list_receipt_issues(order_id: str, user=Depends(current_user)):
    with connect() as conn:
        fetch_order_for_user(conn, order_id, user)
        return receipt_issue_rows(conn, order_id)


@router.get("/orders/{order_id}/receipt-issues/{issue_id}/photos/{photo_id}")
def receipt_issue_photo_file(
    order_id: str,
    issue_id: str,
    photo_id: str,
    variant: str = Query(default="thumbnail", pattern="^(thumbnail|full)$"),
    user=Depends(current_user),
):
    with connect() as conn:
        fetch_order_for_user(conn, order_id, user)
        issue = one(conn, "SELECT * FROM receipt_issues WHERE id = ? AND order_id = ?", (issue_id, order_id))
        if not issue:
            raise HTTPException(status_code=404, detail="照片不存在")
        row = one(conn, "SELECT * FROM receipt_issue_photos WHERE id = ? AND issue_id = ?", (photo_id, issue_id))
        if not row:
            raise HTTPException(status_code=404, detail="照片不存在")
    relative_path = row["thumbnail_path"] if variant == "thumbnail" else row["image_path"]
    path = resolve_private_path(relative_path)
    if not path.exists():
        raise HTTPException(status_code=404, detail="照片不存在")
    return FileResponse(path, media_type="image/jpeg", headers={"Cache-Control": "no-store, private", "Pragma": "no-cache"})


@router.post("/admin/receipt-issues/{issue_id}/resolve")
def resolve_receipt_issue(issue_id: str, body: ReceiptIssueResolve, admin=Depends(require_admin_user)):
    with transaction() as conn:
        issue = one(conn, "SELECT * FROM receipt_issues WHERE id = ?", (issue_id,))
        if not issue:
            raise HTTPException(status_code=404, detail="收货异常不存在")
        if issue["status"] != "open":
            raise HTTPException(status_code=409, detail="收货异常已处理")
        conn.execute(
            """
            UPDATE receipt_issues
            SET status = 'resolved',
                resolved_by = ?,
                resolved_at = CURRENT_TIMESTAMP,
                resolution_note = ?
            WHERE id = ?
            """,
            (admin["id"], body.resolution_note.strip(), issue_id),
        )
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'receipt_issue_resolve', NULL, NULL, ?)",
            (str(uuid4()), issue["order_id"], admin["id"], body.resolution_note.strip()),
        )
        conn.execute(
            """
            INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, after_json, result)
            VALUES (?, ?, ?, 'receipt_issue_resolve', 'receipt_issue', ?, ?, 'success')
            """,
            (str(uuid4()), admin["id"], admin["role"], issue_id, json.dumps({"resolution_note": body.resolution_note.strip()}, ensure_ascii=False)),
        )
        return one(conn, "SELECT * FROM receipt_issues WHERE id = ?", (issue_id,))


@router.get("/admin/receipt-issues")
def admin_receipt_issues(status: str | None = None, admin=Depends(require_admin_user)):
    with connect() as conn:
        where = ["1 = 1"]
        params = []
        if status:
            where.append("receipt_issues.status = ?")
            params.append(status)
        rows = all_rows(
            conn,
            f"""
            SELECT receipt_issues.*, orders.order_no, orders.status AS order_status,
                   orders.unit_name_snapshot, orders.delivery_point_snapshot
            FROM receipt_issues
            JOIN orders ON orders.id = receipt_issues.order_id
            WHERE {' AND '.join(where)}
            ORDER BY receipt_issues.reported_at DESC
            """,
            params,
        )
        for row in rows:
            photos = all_rows(conn, "SELECT * FROM receipt_issue_photos WHERE issue_id = ? ORDER BY uploaded_at, rowid", (row["id"],))
            row["photos"] = [receipt_issue_photo_out(row["order_id"], row["id"], photo) for photo in photos]
        return rows
