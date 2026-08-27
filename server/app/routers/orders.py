from decimal import Decimal
import base64
import json
from uuid import uuid4

from fastapi import APIRouter, Depends, File, Form, Header, HTTPException, Query, UploadFile
from fastapi.responses import FileResponse

from ..database import all_rows, one, transaction, connect
from ..dependencies import current_user, require_admin_user, require_unit_user
from ..models import ADMIN_TRANSITIONS
from ..schemas import OrderCreate, OrderLifecycleReason, OrderSoftDeleteRequest, OrderStatusPatch
from ..services.dashboard_cache import invalidate_dashboard_cache
from ..services.inventory import as_decimal, complete_product, decimal_text, release_product, reserve_product
from ..services.order_status import order_status_payload
from ..services.outbound_reconciliation import reconcile_outbound_status_for_orders
from ..services.push_outbox import enqueue_order_created, enqueue_order_status_changed
from ..services.shipping_photos import cleanup_photos, process_shipping_uploads, resolve_private_path
from ..services.local_time import display_local_time, local_now

router = APIRouter(tags=["orders"])


def order_no(conn) -> str:
    prefix = "SP" + local_now().strftime("%Y%m%d")
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


def order_item_out(item: dict) -> dict:
    result = {**item}
    quantity = decimal_text(item.get("quantity") or "0")
    result["quantity"] = quantity
    if "requested_quantity" in result:
        result["requested_quantity"] = decimal_text(item.get("requested_quantity") or quantity)
    if "actual_quantity" in result:
        result["actual_quantity"] = decimal_text(item.get("actual_quantity") or quantity)
    return result


ORDER_TIME_FIELDS = (
    "created_at",
    "updated_at",
    "accepted_at",
    "preparing_at",
    "shipped_at",
    "completed_at",
    "cancelled_at",
    "voided_at",
    "archived_at",
    "deleted_at",
)


def order_with_local_times(order: dict) -> dict:
    result = {**order}
    for field in ORDER_TIME_FIELDS:
        if field in result:
            result[field] = display_local_time(result.get(field))
    return result


def order_items(conn, order_id: str):
    return [order_item_out(item) for item in all_rows(conn, "SELECT * FROM order_items WHERE order_id = ? ORDER BY rowid", (order_id,))]


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
        "uploaded_at": display_local_time(row["uploaded_at"]),
        "uploaded_by_username": row.get("uploaded_by_username") or "",
        "source": row["source"],
    }


def order_actions(order: dict, viewer: dict | None = None) -> dict:
    status = order.get("status", "")
    is_archived = bool(order.get("archived_at"))
    is_deleted = bool(order.get("is_deleted"))
    role = (viewer or {}).get("role", "")
    can_delete = role == "admin" and not is_deleted and not is_archived and status in {
        "pending", "cancelled", "voided", "shipped", "completed",
    }
    if can_delete:
        delete_reason = ""
    elif role != "admin":
        delete_reason = "只有管理员可以删除订单"
    elif status in {"accepted", "preparing"}:
        delete_reason = "请先作废订单，再执行删除"
    elif status in {"shipped", "completed"}:
        delete_reason = "删除将安全归档订单，历史记录仍会保留"
    else:
        delete_reason = "当前订单不可删除"
    return {
        "archived": is_archived,
        "is_deleted": is_deleted,
        "can_cancel": not is_deleted and not is_archived and status == "pending" and role in {"admin", "unit_user"},
        "can_void": not is_deleted and not is_archived and status in {"accepted", "preparing"} and role == "admin",
        "can_archive": not is_deleted and not is_archived and status in {"completed", "cancelled", "voided"},
        "can_unarchive": not is_deleted and is_archived,
        "can_delete": can_delete,
        "delete_reason": delete_reason,
    }


def order_out(conn, order: dict, include_items: bool = True, include_shipping_photos: bool = True, viewer: dict | None = None) -> dict:
    username = one(conn, "SELECT username FROM users WHERE id = ?", (order.get("created_by"),))
    item_count = one(conn, "SELECT COUNT(*) AS c FROM order_items WHERE order_id = ?", (order["id"],))["c"]
    photo_count = one(conn, "SELECT COUNT(*) AS c FROM order_shipping_photos WHERE order_id = ?", (order["id"],))["c"]
    localized_order = order_with_local_times(order)
    payload = {
        **localized_order,
        **order_status_payload(order.get("status", "")),
        "accepted_at": localized_order.get("accepted_at") or "",
        "preparing_at": localized_order.get("preparing_at") or "",
        "shipped_at": localized_order.get("shipped_at") or "",
        "completed_at": localized_order.get("completed_at") or "",
        "cancelled_at": localized_order.get("cancelled_at") or "",
        "version": int(order.get("version") or 1),
        "created_by_username": username["username"] if username else "",
        "item_count": item_count,
        "shipping_note": order.get("shipping_note") or "",
        "shipping_photo_count": photo_count,
        **order_actions(order, viewer),
    }
    payload.pop("created_by", None)
    if include_items:
        payload["items"] = order_items(conn, order["id"])
    if include_shipping_photos:
        payload["shipping_photos"] = [shipping_photo_out(order["id"], row) for row in shipping_photo_rows(conn, order["id"])]
    return payload


def _placeholders(values: list[str]) -> str:
    return ",".join("?" for _ in values)


def order_list_out(conn, orders: list[dict], include_items: bool, viewer: dict | None = None) -> list[dict]:
    if not orders:
        return []
    order_ids = [row["id"] for row in orders]
    created_by_ids = sorted({row.get("created_by") for row in orders if row.get("created_by")})
    usernames = {}
    if created_by_ids:
        usernames = {
            row["id"]: row["username"]
            for row in all_rows(conn, f"SELECT id, username FROM users WHERE id IN ({_placeholders(created_by_ids)})", created_by_ids)
        }
    item_counts = {
        row["order_id"]: row["c"]
        for row in all_rows(
            conn,
            f"SELECT order_id, COUNT(*) AS c FROM order_items WHERE order_id IN ({_placeholders(order_ids)}) GROUP BY order_id",
            order_ids,
        )
    }
    photo_counts = {
        row["order_id"]: row["c"]
        for row in all_rows(
            conn,
            f"SELECT order_id, COUNT(*) AS c FROM order_shipping_photos WHERE order_id IN ({_placeholders(order_ids)}) GROUP BY order_id",
            order_ids,
        )
    }
    items_by_order: dict[str, list[dict]] = {order_id: [] for order_id in order_ids}
    if include_items:
        for item in all_rows(
            conn,
            f"SELECT * FROM order_items WHERE order_id IN ({_placeholders(order_ids)}) ORDER BY rowid",
            order_ids,
        ):
            items_by_order.setdefault(item["order_id"], []).append(order_item_out(item))
    payloads = []
    for order in orders:
        localized_order = order_with_local_times(order)
        payload = {
            **localized_order,
            **order_status_payload(order.get("status", "")),
            "accepted_at": localized_order.get("accepted_at") or "",
            "preparing_at": localized_order.get("preparing_at") or "",
            "shipped_at": localized_order.get("shipped_at") or "",
            "completed_at": localized_order.get("completed_at") or "",
            "cancelled_at": localized_order.get("cancelled_at") or "",
            "version": int(order.get("version") or 1),
            "created_by_username": usernames.get(order.get("created_by"), ""),
            "item_count": int(item_counts.get(order["id"], 0)),
            "shipping_note": order.get("shipping_note") or "",
            "shipping_photo_count": int(photo_counts.get(order["id"], 0)),
            **order_actions(order, viewer),
        }
        payload.pop("created_by", None)
        if include_items:
            payload["items"] = items_by_order.get(order["id"], [])
        payloads.append(payload)
    return payloads


def ensure_expected_order_state(order: dict, expected_status: str | None = None, expected_version: int | None = None):
    if expected_status and order.get("status") != expected_status:
        raise HTTPException(status_code=409, detail="订单状态已被其他操作员更新，页面已刷新")
    if expected_version is not None and int(order.get("version") or 1) != expected_version:
        raise HTTPException(status_code=409, detail="订单状态已被其他操作员更新，页面已刷新")


def _encode_cursor(created_at: str, order_id: str) -> str:
    raw = json.dumps({"created_at": created_at, "id": order_id}, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _decode_cursor(value: str) -> tuple[str, str]:
    try:
        raw = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
        payload = json.loads(raw)
        created_at = str(payload["created_at"])
        order_id = str(payload["id"])
    except (KeyError, TypeError, ValueError, json.JSONDecodeError) as exc:
        raise HTTPException(status_code=400, detail="分页游标无效，请刷新列表") from exc
    if not created_at or not order_id:
        raise HTTPException(status_code=400, detail="分页游标无效，请刷新列表")
    return created_at, order_id


def order_list(conn, sql: str, params: list, include_items: bool, limit: int, cursor: str | None, viewer: dict) -> dict:
    count_sql = f"SELECT COUNT(*) AS c FROM ({sql}) AS filtered_orders"
    total = one(conn, count_sql, params)["c"]
    if cursor:
        created_at, order_id = _decode_cursor(cursor)
        sql += " AND (created_at < ? OR (created_at = ? AND id < ?))"
        params = [*params, created_at, created_at, order_id]
    rows = all_rows(conn, sql + " ORDER BY created_at DESC, id DESC LIMIT ?", (*params, limit + 1))
    has_more = len(rows) > limit
    page_rows = rows[:limit]
    next_cursor = _encode_cursor(page_rows[-1]["created_at"], page_rows[-1]["id"]) if has_more and page_rows else ""
    return {
        "items": order_list_out(conn, page_rows, include_items=include_items, viewer=viewer),
        "total": total,
        "page": 1,
        "page_size": limit,
        "next_cursor": next_cursor,
        "has_more": has_more,
    }


def fetch_order_for_user(conn, order_id: str, user: dict, include_deleted: bool = False):
    deleted_clause = "" if include_deleted else " AND is_deleted = 0"
    order = one(conn, f"SELECT * FROM orders WHERE id = ?{deleted_clause}", (order_id,))
    if not order:
        raise HTTPException(status_code=404, detail="订单不存在或无权查看")
    if user["role"] == "unit_user" and order["unit_id"] != user["unit_id"]:
        raise HTTPException(status_code=404, detail="订单不存在或无权查看")
    return order


def create_order_rows(conn, body: OrderCreate, user: dict, existing_order_id: str | None = None, idempotency_key: str | None = None):
    if not body.items:
        raise HTTPException(status_code=400, detail="请先选择食材")
    unit = get_unit(conn, user["unit_id"])
    effective_idempotency_key = (idempotency_key or body.client_request_id or "").strip() or None
    if effective_idempotency_key and not existing_order_id:
        existing = one(
            conn,
            """
            SELECT * FROM orders
            WHERE created_by = ?
              AND (idempotency_key = ? OR client_request_id = ?)
            """,
            (user["id"], effective_idempotency_key, effective_idempotency_key),
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
            "UPDATE orders SET total_cents = ?, note = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
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
        conn.execute("UPDATE orders SET idempotency_key = ? WHERE id = ?", (effective_idempotency_key, order_id))
    for product, quantity, subtotal in items_payload:
        conn.execute(
            """
            INSERT INTO order_items(id, order_id, product_id, product_code_snapshot, product_name_snapshot,
              category_snapshot, spec_snapshot, unit_snapshot, price_cents_snapshot, quantity, subtotal_cents)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(uuid4()), order_id, product["id"], product["product_code"], product["name"], product["category"],
                product["spec"], product["unit"], product["price_cents"], decimal_text(quantity), subtotal,
            ),
        )
    conn.execute(
        "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, ?, ?, ?, ?)",
        (str(uuid4()), order_id, user["id"], "create" if not existing_order_id else "update", None, "pending", body.note),
    )
    return one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))


@router.post("/orders")
def create_order(body: OrderCreate, user=Depends(require_unit_user), idempotency_key: str | None = Header(default=None, alias="Idempotency-Key")):
    with transaction() as conn:
        order = create_order_rows(conn, body, user, idempotency_key=idempotency_key)
        existing_event = one(
            conn,
            "SELECT event_id FROM push_outbox WHERE order_id = ? AND event_type = 'ORDER_CREATED'",
            (order["id"],),
        )
        if not existing_event:
            enqueue_order_created(conn, order)
        invalidate_dashboard_cache()
        return order_out(conn, order, viewer=user)


@router.get("/orders")
def list_orders(
    include_items: bool = False,
    status: str | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
    query: str | None = Query(default=None, max_length=64),
    archived: bool = False,
    cursor: str | None = None,
    limit: int = Query(default=20, ge=1, le=100),
    user=Depends(require_unit_user),
):
    with connect() as conn:
        sql = "SELECT * FROM orders WHERE unit_id = ? AND is_deleted = 0"
        params = [user["unit_id"]]
        if status:
            sql += " AND status = ?"
            params.append(status)
        sql += " AND (archived_at IS NOT NULL)" if archived else " AND archived_at IS NULL"
        if date_from:
            sql += " AND date(datetime(created_at, '+8 hours')) >= date(?)"
            params.append(date_from)
        if date_to:
            sql += " AND date(datetime(created_at, '+8 hours')) <= date(?)"
            params.append(date_to)
        if query and query.strip():
            text = query.strip()
            sql += " AND (order_no = ? OR order_no LIKE ? ESCAPE '\\')"
            params.extend([text, text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"])
        return order_list(conn, sql, params, include_items, limit, cursor, user)


@router.get("/orders/{order_id}")
def order_detail(order_id: str, user=Depends(current_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        return order_out(conn, order, viewer=user)


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
            release_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, user["id"])
        updated = create_order_rows(conn, body, user, existing_order_id=order_id)
        invalidate_dashboard_cache()
        return order_out(conn, updated, viewer=user)


def _audit_lifecycle(conn, order: dict, actor: dict, action: str, old_status: str, new_status: str, reason: str, source: str):
    payload = json.dumps(
        {
            "event_type": action,
            "order_id": order["id"],
            "order_no": order["order_no"],
            "old_status": old_status,
            "new_status": new_status,
            "operator_user_id": actor["id"],
            "operator_role": actor["role"],
            "operator_unit_id": actor.get("unit_id") or "",
            "reason": reason,
            "source": source,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
    conn.execute(
        """
        INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, before_json, after_json, result)
        VALUES (?, ?, ?, ?, 'order', ?, ?, ?, 'success')
        """,
        (str(uuid4()), actor["id"], actor["role"], action, order["id"], json.dumps({"status": old_status}), payload),
    )


def _release_order_reservations(conn, order: dict, actor: dict, action: str, reason: str):
    for item in order_items(conn, order["id"]):
        release_product(
            conn,
            item["product_id"],
            as_decimal(item["quantity"]),
            order["id"],
            actor["id"],
            action=action,
            detail=reason,
        )


def lifecycle_reason(value: str) -> str:
    reason = value.strip()
    if not reason:
        raise HTTPException(status_code=400, detail="请填写取消或作废原因")
    return reason


def _cancel_order(conn, order: dict, actor: dict, reason: str, source: str) -> dict:
    if order["status"] == "cancelled":
        return order
    if order["status"] != "pending":
        raise HTTPException(status_code=409, detail="只有待接单订单可以取消")
    cursor = conn.execute(
        """
        UPDATE orders
        SET status = 'cancelled', cancelled_at = CURRENT_TIMESTAMP, cancelled_by = ?, cancel_reason = ?,
            version = version + 1, updated_at = CURRENT_TIMESTAMP
        WHERE id = ? AND status = 'pending' AND cancelled_at IS NULL
        """,
        (actor["id"], reason, order["id"]),
    )
    if cursor.rowcount != 1:
        refreshed = one(conn, "SELECT * FROM orders WHERE id = ?", (order["id"],))
        if refreshed and refreshed["status"] == "cancelled":
            return refreshed
        raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
    _release_order_reservations(conn, order, actor, "ORDER_CANCEL_RELEASE", reason)
    conn.execute(
        "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'cancel', 'pending', 'cancelled', ?)",
        (str(uuid4()), order["id"], actor["id"], reason),
    )
    updated = one(conn, "SELECT * FROM orders WHERE id = ?", (order["id"],))
    _audit_lifecycle(conn, updated, actor, "ORDER_CANCELLED", "pending", "cancelled", reason, source)
    enqueue_order_status_changed(conn, updated, "cancelled")
    return updated


@router.post("/orders/{order_id}/cancel")
def cancel_order(order_id: str, body: OrderLifecycleReason, user=Depends(require_unit_user)):
    reason = lifecycle_reason(body.reason)
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        updated = _cancel_order(conn, order, user, reason, "android_or_api")
        invalidate_dashboard_cache()
        return order_out(conn, updated, viewer=user)


@router.post("/orders/{order_id}/confirm-receipt")
def confirm_receipt(order_id: str, user=Depends(require_unit_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order["status"] != "shipped":
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        for item in order_items(conn, order_id):
            complete_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, user["id"])
        conn.execute("UPDATE orders SET status = 'completed', completed_at = CURRENT_TIMESTAMP, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'confirm_receipt', 'shipped', 'completed')",
            (str(uuid4()), order_id, user["id"]),
        )
        invalidate_dashboard_cache()
        return order_out(conn, one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,)), viewer=user)


@router.get("/admin/orders")
def admin_orders(
    include_items: bool = False,
    status: str | None = None,
    unit_id: str | None = None,
    date_from: str | None = None,
    date_to: str | None = None,
    query: str | None = Query(default=None, max_length=64),
    archived: bool = False,
    deleted: bool = False,
    cursor: str | None = None,
    limit: int = Query(default=30, ge=1, le=100),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        sql = "SELECT * FROM orders WHERE is_deleted = ?"
        params: list = [1 if deleted else 0]
        if status:
            statuses = [value.strip() for value in status.split(",") if value.strip()]
            sql += f" AND status IN ({','.join('?' for _ in statuses)})"
            params.extend(statuses)
        if unit_id:
            sql += " AND unit_id = ?"
            params.append(unit_id)
        sql += " AND archived_at IS NOT NULL" if archived else " AND archived_at IS NULL"
        if date_from:
            sql += " AND date(datetime(created_at, '+8 hours')) >= date(?)"
            params.append(date_from)
        if date_to:
            sql += " AND date(datetime(created_at, '+8 hours')) <= date(?)"
            params.append(date_to)
        if query and query.strip():
            text = query.strip()
            sql += " AND (order_no = ? OR order_no LIKE ? ESCAPE '\\' OR unit_name_snapshot LIKE ? ESCAPE '\\')"
            prefix = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
            params.extend([text, prefix, prefix])
        return order_list(conn, sql, params, include_items, limit, cursor, admin)


@router.get("/admin/orders/{order_id}")
def admin_order_detail(order_id: str, include_deleted: bool = False, admin=Depends(require_admin_user)):
    with connect() as conn:
        order = fetch_order_for_user(conn, order_id, admin, include_deleted=include_deleted)
        return order_out(conn, order, viewer=admin)


@router.post("/admin/orders/{order_id}/cancel")
def admin_cancel_order(order_id: str, body: OrderLifecycleReason, admin=Depends(require_admin_user)):
    reason = lifecycle_reason(body.reason)
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        updated = _cancel_order(conn, order, admin, reason, "admin_web_or_api")
        invalidate_dashboard_cache()
        return order_out(conn, updated, viewer=admin)


@router.post("/admin/orders/{order_id}/void")
def void_order(order_id: str, body: OrderLifecycleReason, admin=Depends(require_admin_user)):
    reason = lifecycle_reason(body.reason)
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        if order["status"] == "voided":
            return order_out(conn, order, viewer=admin)
        if order["status"] not in {"accepted", "preparing"}:
            raise HTTPException(status_code=409, detail="只有已接单或备货中的订单可以作废")
        cursor = conn.execute(
            """
            UPDATE orders
            SET status = 'voided', voided_at = CURRENT_TIMESTAMP, voided_by = ?, void_reason = ?,
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status IN ('accepted', 'preparing') AND voided_at IS NULL
            """,
            (admin["id"], reason, order_id),
        )
        if cursor.rowcount != 1:
            refreshed = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
            if refreshed and refreshed["status"] == "voided":
                return order_out(conn, refreshed, viewer=admin)
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        _release_order_reservations(conn, order, admin, "ORDER_VOID_RELEASE", reason)
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'void', ?, 'voided', ?)",
            (str(uuid4()), order_id, admin["id"], order["status"], reason),
        )
        updated = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
        _audit_lifecycle(conn, updated, admin, "ORDER_VOIDED", order["status"], "voided", reason, "admin_web_or_api")
        enqueue_order_status_changed(conn, updated, "voided")
        invalidate_dashboard_cache()
        return order_out(conn, updated, viewer=admin)


@router.post("/orders/{order_id}/archive")
def archive_order(order_id: str, user=Depends(current_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if order.get("archived_at"):
            return order_out(conn, order, viewer=user)
        if order["status"] not in {"completed", "cancelled", "voided"}:
            raise HTTPException(status_code=409, detail="只有已完成、已取消或已作废订单可以归档")
        conn.execute(
            "UPDATE orders SET archived_at = CURRENT_TIMESTAMP, archived_by = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND archived_at IS NULL",
            (user["id"], order_id),
        )
        updated = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'archive', ?, ?, '')",
            (str(uuid4()), order_id, user["id"], order["status"], order["status"]),
        )
        _audit_lifecycle(conn, updated, user, "ORDER_ARCHIVED", order["status"], order["status"], "", "api")
        invalidate_dashboard_cache()
        return order_out(conn, updated, viewer=user)


@router.post("/orders/{order_id}/unarchive")
def unarchive_order(order_id: str, user=Depends(current_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, user)
        if not order.get("archived_at"):
            return order_out(conn, order, viewer=user)
        conn.execute("UPDATE orders SET archived_at = NULL, archived_by = NULL, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (order_id,))
        updated = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'unarchive', ?, ?, '')",
            (str(uuid4()), order_id, user["id"], order["status"], order["status"]),
        )
        _audit_lifecycle(conn, updated, user, "ORDER_UNARCHIVED", order["status"], order["status"], "", "api")
        invalidate_dashboard_cache()
        return order_out(conn, updated, viewer=user)


@router.patch("/admin/orders/{order_id}/status")
def admin_order_status(order_id: str, body: OrderStatusPatch, admin=Depends(require_admin_user)):
    with transaction() as conn:
        order = fetch_order_for_user(conn, order_id, admin)
        if body.status == "accepted" and order["status"] == "preparing":
            return order_out(conn, order, viewer=admin)
        if body.status == "accepted" and order["status"] in {"pending", "accepted"}:
            if order["status"] == "pending":
                ensure_expected_order_state(order, body.expected_status, body.expected_version)
            conn.execute(
                """
                UPDATE orders
                SET status = 'preparing',
                    accepted_at = COALESCE(accepted_at, CURRENT_TIMESTAMP),
                    preparing_at = COALESCE(preparing_at, CURRENT_TIMESTAMP),
                    version = version + 1,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status IN ('pending', 'accepted') AND is_deleted = 0
                """,
                (order_id,),
            )
            conn.execute(
                "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'accept', ?, 'preparing', '接单后自动进入备货')",
                (str(uuid4()), order_id, admin["id"], order["status"]),
            )
            updated_order = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
            _audit_lifecycle(conn, updated_order, admin, "ORDER_ACCEPTED", order["status"], "preparing", "接单后自动进入备货", "admin_web_or_api")
            enqueue_order_status_changed(conn, updated_order, "preparing")
            invalidate_dashboard_cache()
            return order_out(conn, updated_order, viewer=admin)
        ensure_expected_order_state(order, body.expected_status, body.expected_version)
        if body.status == "shipped":
            raise HTTPException(status_code=409, detail="请先拍摄并上传发货照片")
        allowed = ADMIN_TRANSITIONS.get(order["status"], set())
        if body.status not in allowed:
            raise HTTPException(status_code=409, detail="订单状态已变化，请刷新后重试")
        if body.status == "completed":
            for item in order_items(conn, order_id):
                complete_product(conn, item["product_id"], as_decimal(item["quantity"]), order_id, admin["id"])
        time_field = {
            "accepted": "accepted_at",
            "preparing": "preparing_at",
            "shipped": "shipped_at",
            "completed": "completed_at",
        }.get(body.status)
        if time_field:
            conn.execute(f"UPDATE orders SET status = ?, {time_field} = CURRENT_TIMESTAMP, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (body.status, order_id))
        else:
            conn.execute("UPDATE orders SET status = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?", (body.status, order_id))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status) VALUES (?, ?, ?, 'status', ?, ?)",
            (str(uuid4()), order_id, admin["id"], order["status"], body.status),
        )
        updated_order = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
        _audit_lifecycle(conn, updated_order, admin, "ORDER_STATUS_CHANGED", order["status"], body.status, "", "admin_web_or_api")
        enqueue_order_status_changed(conn, updated_order, body.status)
        invalidate_dashboard_cache()
        return order_out(conn, updated_order, viewer=admin)


@router.delete("/admin/orders/{order_id}")
def soft_delete_order(order_id: str, body: OrderSoftDeleteRequest, admin=Depends(require_admin_user)):
    reason = body.reason.strip() or "管理员删除订单"
    with transaction() as conn:
        order = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
        if not order:
            raise HTTPException(status_code=404, detail="订单不存在")
        if order.get("is_deleted") or order.get("archived_at"):
            return {"ok": True, "order_id": order_id, "already_deleted": True}
        if order["status"] == "pending":
            order = _cancel_order(conn, order, admin, reason, "admin_web_or_api")
        elif order["status"] not in {"cancelled", "voided", "shipped", "completed"}:
            if order["status"] in {"accepted", "preparing"}:
                raise HTTPException(status_code=409, detail="请先作废订单，再执行删除")
            raise HTTPException(status_code=409, detail="当前订单不可删除")
        conn.execute(
            """
            UPDATE orders
            SET archived_at = CURRENT_TIMESTAMP, archived_by = ?, updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE id = ? AND is_deleted = 0
            """,
            (admin["id"], order_id),
        )
        updated = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
        conn.execute(
            "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'soft_delete', ?, ?, ?)",
            (str(uuid4()), order_id, admin["id"], updated["status"], updated["status"], reason),
        )
        _audit_lifecycle(conn, updated, admin, "ORDER_ARCHIVED", updated["status"], updated["status"], reason, "admin_web_or_api")
        invalidate_dashboard_cache()
        return {"ok": True, "order_id": order_id, "already_deleted": False, "archived": True}


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
            return order_out(conn, existing, viewer=admin)
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
                return order_out(conn, existing, viewer=admin)
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
                    version = version + 1,
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
            updated_order = one(conn, "SELECT * FROM orders WHERE id = ?", (order_id,))
            enqueue_order_status_changed(conn, updated_order, "shipped")
            reconcile_outbound_status_for_orders(conn, [order_id], admin)
            invalidate_dashboard_cache()
            return order_out(conn, updated_order, viewer=admin)
    except Exception:
        cleanup_photos(processed)
        raise
