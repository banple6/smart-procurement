import json
from io import BytesIO
from urllib.parse import quote
from uuid import uuid4
from zipfile import ZIP_DEFLATED, ZipFile

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Response, UploadFile

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import require_admin_user
from ..schemas import OutboundDirectCompleteRequest
from ..services.batch_exports import outbound_order_filename, outbound_order_workbook
from ..services.dashboard_cache import invalidate_dashboard_cache
from ..services.inventory import as_decimal, complete_product
from ..services.local_time import display_local_time, local_now
from ..services.outbound_reconciliation import FULFILLED_ORDER_STATUSES, reconcile_outbound_status_for_orders
from ..services.push_outbox import enqueue_order_status_changed
from ..services.realtime import bump_resources
from ..services.shipping_photos import cleanup_photos, process_shipping_uploads
from ..services.unit_quota import finalize_order_quota


router = APIRouter(prefix="/admin/outbounds", tags=["outbound-orders"])


def _outbound_no(conn) -> str:
    prefix = "CK" + local_now().strftime("%Y%m%d")
    sequence = f"outbound-order-{prefix}"
    conn.execute("INSERT OR IGNORE INTO app_sequences(name, value) VALUES (?, 0)", (sequence,))
    conn.execute("UPDATE app_sequences SET value = value + 1 WHERE name = ?", (sequence,))
    value = one(conn, "SELECT value FROM app_sequences WHERE name = ?", (sequence,))["value"]
    return f"{prefix}-{int(value):04d}"


def _outbound(conn, outbound_id: str) -> dict:
    row = one(
        conn,
        """
        SELECT outbound_orders.*, COALESCE(units.unit_code, '') AS unit_code,
               delivery_batches.batch_no, delivery_batches.name AS batch_name,
               delivery_batches.status AS batch_status
        FROM outbound_orders
        JOIN delivery_batches ON delivery_batches.id = outbound_orders.preparation_batch_id
        LEFT JOIN units ON units.id = outbound_orders.unit_id
        WHERE outbound_orders.id = ?
        """,
        (outbound_id,),
    )
    if not row:
        raise HTTPException(status_code=404, detail="出库单不存在")
    return row


def _outbound_lines(conn, outbound_id: str) -> list[dict]:
    rows = all_rows(
        conn,
        """
        SELECT order_items.product_id,
               order_items.category_snapshot AS category,
               order_items.product_name_snapshot AS product_name,
               order_items.spec_snapshot AS spec,
               order_items.unit_snapshot AS unit,
               order_items.quantity,
               order_items.price_cents_snapshot,
               order_items.subtotal_cents
        FROM outbound_order_orders
        JOIN order_items ON order_items.order_id = outbound_order_orders.order_id
        WHERE outbound_order_orders.outbound_order_id = ?
        ORDER BY order_items.category_snapshot, order_items.product_name_snapshot, order_items.spec_snapshot, order_items.unit_snapshot
        """,
        (outbound_id,),
    )
    grouped: dict[tuple[str, str, str, str, str], dict] = {}
    for row in rows:
        key = (row["product_id"], row["category"], row["product_name"], row["spec"], row["unit"])
        line = grouped.setdefault(
            key,
            {
                "product_id": row["product_id"],
                "category": row["category"],
                "product_name": row["product_name"],
                "spec": row["spec"],
                "unit": row["unit"],
                "quantity": "0",
                "subtotal_cents": 0,
                "price_cents_snapshot": int(row["price_cents_snapshot"] or 0),
            },
        )
        from decimal import Decimal

        quantity = Decimal(str(line["quantity"])) + Decimal(str(row["quantity"] or "0"))
        line["quantity"] = format(quantity.normalize(), "f") if quantity else "0"
        line["subtotal_cents"] += int(row["subtotal_cents"] or 0)
    return list(grouped.values())


def _outbound_orders(conn, outbound_id: str) -> list[dict]:
    rows = all_rows(
        conn,
        """
        SELECT orders.id, orders.order_no, orders.status, orders.created_at, orders.shipped_at,
               orders.total_cents, orders.delivery_point_snapshot,
               COUNT(order_shipping_photos.id) AS shipping_photo_count
        FROM outbound_order_orders
        JOIN orders ON orders.id = outbound_order_orders.order_id
        LEFT JOIN order_shipping_photos ON order_shipping_photos.order_id = orders.id
        WHERE outbound_order_orders.outbound_order_id = ?
        GROUP BY orders.id
        ORDER BY orders.created_at, orders.id
        """,
        (outbound_id,),
    )
    for row in rows:
        row["created_at"] = display_local_time(row.get("created_at"))
        row["shipped_at"] = display_local_time(row.get("shipped_at"))
    return rows


def _outbound_out(conn, row: dict, include_details: bool = False) -> dict:
    result = dict(row)
    for field in ("created_at", "updated_at", "shipped_at", "archived_at"):
        result[field] = display_local_time(result.get(field))
    result["business_date"] = result["created_at"][:10] if len(result.get("created_at") or "") >= 10 else ""
    order_count = one(conn, "SELECT COUNT(*) AS count FROM outbound_order_orders WHERE outbound_order_id = ?", (row["id"],))["count"]
    result["order_count"] = int(order_count)
    lines = _outbound_lines(conn, row["id"])
    result["product_count"] = len(lines)
    # Every outbound response needs the same historical amount as its export.
    # The amount is derived from order-item snapshots, never the current catalog price.
    result["total_cents"] = sum(int(line["subtotal_cents"] or 0) for line in lines)
    if include_details:
        result["orders"] = _outbound_orders(conn, row["id"])
        result["lines"] = lines
    return result


def _batch(conn, batch_id: str) -> dict:
    batch = one(conn, "SELECT * FROM delivery_batches WHERE id = ?", (batch_id,))
    if not batch:
        raise HTTPException(status_code=404, detail="备货单不存在")
    return batch


def _batch_unit_orders(conn, batch_id: str) -> list[dict]:
    return all_rows(
        conn,
        """
        SELECT orders.id, orders.unit_id, orders.unit_name_snapshot, orders.delivery_point_snapshot
        FROM delivery_batch_orders
        JOIN orders ON orders.id = delivery_batch_orders.order_id
        WHERE delivery_batch_orders.batch_id = ?
          AND orders.is_deleted = 0
          AND orders.status IN ('preparing', 'shipped', 'completed')
        ORDER BY orders.unit_name_snapshot, orders.created_at, orders.id
        """,
        (batch_id,),
    )


def create_outbound_for_orders(conn, batch: dict, orders: list[dict], admin: dict) -> tuple[dict, bool]:
    """Create one unit-level outbound from existing order snapshots.

    Both the normal batch workflow and fast order completion use this helper so
    outbound identity, relationships, and audit data have one implementation.
    """
    if not orders:
        raise HTTPException(status_code=409, detail="出库单缺少有效订单")
    unit_ids = {order["unit_id"] for order in orders}
    if len(unit_ids) != 1:
        raise HTTPException(status_code=409, detail="不同单位订单不能生成同一张出库单")
    unit_id = next(iter(unit_ids))
    existing = one(
        conn,
        "SELECT * FROM outbound_orders WHERE preparation_batch_id = ? AND unit_id = ?",
        (batch["id"], unit_id),
    )
    if existing:
        return _outbound(conn, existing["id"]), False
    order_ids = [order["id"] for order in orders]
    placeholders = ",".join("?" for _ in order_ids)
    linked = one(
        conn,
        f"SELECT outbound_order_id FROM outbound_order_orders WHERE order_id IN ({placeholders}) LIMIT 1",
        order_ids,
    )
    if linked:
        raise HTTPException(status_code=409, detail="订单已经进入其它出库流程，不能重复生成")

    first = orders[0]
    outbound_id = str(uuid4())
    outbound_no = _outbound_no(conn)
    conn.execute(
        """
        INSERT INTO outbound_orders(
          id, outbound_no, preparation_batch_id, unit_id, unit_name_snapshot,
          delivery_point_snapshot, created_by
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        (
            outbound_id,
            outbound_no,
            batch["id"],
            unit_id,
            first["unit_name_snapshot"],
            first["delivery_point_snapshot"] or "",
            admin["id"],
        ),
    )
    conn.executemany(
        "INSERT INTO outbound_order_orders(outbound_order_id, order_id) VALUES (?, ?)",
        [(outbound_id, order_id) for order_id in order_ids],
    )
    write_audit(
        conn,
        admin["id"],
        admin["role"],
        "OUTBOUND_ORDER_CREATED",
        "outbound_order",
        outbound_id,
        after_json=json.dumps({"outbound_no": outbound_no, "batch_no": batch["batch_no"], "order_ids": order_ids}, ensure_ascii=False),
    )
    return _outbound(conn, outbound_id), True


def _outbound_order_records(conn, outbound_id: str) -> list[dict]:
    """Return authoritative order rows for a single outbound transaction."""
    return all_rows(
        conn,
        """
        SELECT orders.*
        FROM outbound_order_orders
        JOIN orders ON orders.id = outbound_order_orders.order_id
        WHERE outbound_order_orders.outbound_order_id = ?
        ORDER BY orders.created_at, orders.id
        """,
        (outbound_id,),
    )


@router.post("/{outbound_id}/complete")
def complete_outbound_order(
    outbound_id: str,
    body: OutboundDirectCompleteRequest,
    admin=Depends(require_admin_user),
):
    """Finish one legacy pending outbound without requiring a new photo.

    The old photo-upload shipment endpoints stay intact for Android and history.
    This action is deliberately outbound-scoped, so it cannot complete another
    unit's outbound or unrelated orders from the same preparation batch.
    """
    request_id = body.client_request_id.strip()
    if not request_id:
        raise HTTPException(status_code=400, detail="缺少请求编号，请重试")
    operation_id = f"direct-complete:{request_id}"
    with transaction() as conn:
        previous = one(conn, "SELECT * FROM outbound_orders WHERE ship_request_id = ?", (operation_id,))
        if previous:
            if previous["id"] != outbound_id:
                raise HTTPException(status_code=409, detail="请求编号已被其他出库单使用")
            return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)

        outbound = _outbound(conn, outbound_id)
        if outbound["status"] == "shipped":
            # A completed document is safe to return for a retry or a late click.
            return _outbound_out(conn, outbound, include_details=True)
        if outbound["status"] != "pending":
            raise HTTPException(status_code=409, detail="当前出库单不能完成")
        if int(outbound["version"] or 1) != body.expected_version:
            raise HTTPException(status_code=409, detail="出库单已被其他管理员修改，请刷新后重试")

        orders = _outbound_order_records(conn, outbound_id)
        allowed = {"preparing", "shipped", "completed"}
        if not orders or any(order["status"] not in allowed for order in orders):
            raise HTTPException(status_code=409, detail="出库单中的订单状态已变化，请刷新后重试")

        completed_order_ids: list[str] = []
        for order in orders:
            if order["status"] == "completed":
                continue
            for item in all_rows(conn, "SELECT product_id, quantity FROM order_items WHERE order_id = ?", (order["id"],)):
                complete_product(conn, item["product_id"], as_decimal(item["quantity"]), order["id"], admin["id"])
            finalize_order_quota(conn, order_id=order["id"], actor_id=admin["id"])
            cursor = conn.execute(
                """
                UPDATE orders
                SET status = 'completed', shipped_at = COALESCE(shipped_at, CURRENT_TIMESTAMP),
                    completed_at = CURRENT_TIMESTAMP, version = version + 1,
                    updated_at = CURRENT_TIMESTAMP,
                    shipping_note = CASE WHEN TRIM(shipping_note) = '' THEN '系统直接完成出库（无需照片）' ELSE shipping_note END,
                    ship_request_id = COALESCE(ship_request_id, ?)
                WHERE id = ? AND status IN ('preparing', 'shipped')
                """,
                (f"{operation_id}:{order['id']}", order["id"]),
            )
            if cursor.rowcount != 1:
                raise HTTPException(status_code=409, detail="订单已被其他管理员修改，请刷新后重试")
            conn.execute(
                """
                INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail)
                VALUES (?, ?, ?, 'outbound_direct_complete', ?, 'completed', ?)
                """,
                (str(uuid4()), order["id"], admin["id"], order["status"], outbound["outbound_no"]),
            )
            updated_order = one(conn, "SELECT * FROM orders WHERE id = ?", (order["id"],))
            enqueue_order_status_changed(conn, updated_order, "completed")
            completed_order_ids.append(order["id"])

        cursor = conn.execute(
            """
            UPDATE outbound_orders
            SET status = 'shipped', shipped_at = CURRENT_TIMESTAMP, shipped_by = ?,
                ship_request_id = ?, version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending' AND version = ?
            """,
            (admin["id"], operation_id, outbound_id, body.expected_version),
        )
        if cursor.rowcount != 1:
            raise HTTPException(status_code=409, detail="出库单已被其他管理员修改，请刷新后重试")
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "OUTBOUND_ORDER_DIRECT_COMPLETED",
            "outbound_order",
            outbound_id,
            before_json=json.dumps({"status": outbound["status"], "version": outbound["version"]}, ensure_ascii=False),
            after_json=json.dumps(
                {
                    "status": "shipped",
                    "order_ids": [order["id"] for order in orders],
                    "completed_order_ids": completed_order_ids,
                    "photo_upload_required": False,
                    "client_request_id": request_id,
                },
                ensure_ascii=False,
            ),
            request_id=request_id,
        )
        bump_resources(conn, "orders", "outbounds", "dashboard", "quota")
        invalidate_dashboard_cache()
        return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)


@router.post("/from-batch/{batch_id}")
def generate_outbound_orders(batch_id: str, admin=Depends(require_admin_user)):
    """Generate every unit document in one transaction; retries return the same rows."""
    with transaction() as conn:
        batch = _batch(conn, batch_id)
        if batch["status"] != "closed":
            raise HTTPException(status_code=409, detail="只有已完成备货的备货单可以生成出库单")
        candidates = _batch_unit_orders(conn, batch_id)
        if not candidates:
            raise HTTPException(status_code=409, detail="该备货单暂无可生成出库单的有效订单")
        grouped: dict[str, list[dict]] = {}
        for order in candidates:
            grouped.setdefault(order["unit_id"], []).append(order)
        created = 0
        for unit_id, orders in grouped.items():
            _, was_created = create_outbound_for_orders(conn, batch, orders, admin)
            created += int(was_created)
        rows = all_rows(
            conn,
            "SELECT * FROM outbound_orders WHERE preparation_batch_id = ? ORDER BY unit_name_snapshot, id",
            (batch_id,),
        )
        if created:
            bump_resources(conn, "outbounds", "dashboard")
        return {"created_count": created, "items": [_outbound_out(conn, _outbound(conn, row["id"])) for row in rows]}


@router.get("")
def list_outbound_orders(
    status: str | None = Query(default=None, pattern="^(pending|shipped|archived)$"),
    date_from: str | None = Query(default=None),
    date_to: str | None = Query(default=None),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        conditions = []
        params: list[str] = []
        if status:
            conditions.append("outbound_orders.status = ?")
            params.append(status)
        else:
            conditions.append("outbound_orders.status IN ('pending', 'shipped')")
        if date_from:
            conditions.append("date(datetime(outbound_orders.created_at, '+8 hours')) >= date(?)")
            params.append(date_from)
        if date_to:
            conditions.append("date(datetime(outbound_orders.created_at, '+8 hours')) <= date(?)")
            params.append(date_to)
        where = " WHERE " + " AND ".join(conditions)
        rows = all_rows(
            conn,
            """
            SELECT outbound_orders.*, COALESCE(units.unit_code, '') AS unit_code,
                   delivery_batches.batch_no, delivery_batches.name AS batch_name
            FROM outbound_orders
            JOIN delivery_batches ON delivery_batches.id = outbound_orders.preparation_batch_id
            LEFT JOIN units ON units.id = outbound_orders.unit_id
            """ + where + " ORDER BY outbound_orders.created_at DESC, outbound_orders.id DESC",
            params,
        )
        return {"items": [_outbound_out(conn, row) for row in rows]}


@router.get("/bulk.zip")
def export_outbound_orders_bulk(outbound_ids: list[str] = Query(default=[]), admin=Depends(require_admin_user)):
    ids = list(dict.fromkeys(value.strip() for value in outbound_ids if value.strip()))
    if not ids:
        raise HTTPException(status_code=400, detail="请至少选择一张出库单")
    with transaction() as conn:
        placeholders = ",".join("?" for _ in ids)
        rows = all_rows(conn, f"SELECT * FROM outbound_orders WHERE id IN ({placeholders})", ids)
        if len(rows) != len(ids):
            raise HTTPException(status_code=404, detail="部分出库单不存在，请刷新后重试")
        entries = []
        for row in rows:
            outbound = _outbound(conn, row["id"])
            document = _outbound_out(conn, outbound, include_details=True)
            workbook = outbound_order_workbook(document, document["lines"])
            entries.append((outbound_order_filename(document), workbook, row["id"]))
            write_audit(conn, admin["id"], admin["role"], "OUTBOUND_ORDER_EXPORTED", "outbound_order", row["id"])
    stream = BytesIO()
    with ZipFile(stream, "w", ZIP_DEFLATED) as archive:
        for filename, workbook, _ in entries:
            archive.writestr(filename, workbook)
    return Response(stream.getvalue(), media_type="application/zip", headers={"Content-Disposition": "attachment; filename*=UTF-8''" + quote("三公鲜配_出库单批量导出.zip")})


@router.get("/{outbound_id}")
def outbound_order_detail(outbound_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)


@router.get("/{outbound_id}/export.xlsx")
def export_outbound_order(outbound_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        outbound = _outbound(conn, outbound_id)
        document = _outbound_out(conn, outbound, include_details=True)
        write_audit(conn, admin["id"], admin["role"], "OUTBOUND_ORDER_EXPORTED", "outbound_order", outbound_id)
    filename = outbound_order_filename(document)
    return Response(
        outbound_order_workbook(document, document["lines"]),
        media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers={"Content-Disposition": "attachment; filename*=UTF-8''" + quote(filename)},
    )


@router.delete("/{outbound_id}")
def archive_outbound_order(outbound_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        outbound = _outbound(conn, outbound_id)
        if outbound["status"] == "shipped":
            raise HTTPException(status_code=409, detail="已发货出库单只能保留历史，不能归档")
        if outbound["status"] == "archived":
            return _outbound_out(conn, outbound)
        conn.execute(
            """
            UPDATE outbound_orders
            SET status = 'archived', archived_at = CURRENT_TIMESTAMP, archived_by = ?,
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending'
            """,
            (admin["id"], outbound_id),
        )
        updated = _outbound(conn, outbound_id)
        write_audit(conn, admin["id"], admin["role"], "OUTBOUND_ORDER_ARCHIVED", "outbound_order", outbound_id)
        bump_resources(conn, "outbounds", "dashboard")
        return _outbound_out(conn, updated)


@router.post("/{outbound_id}/ship")
async def ship_outbound_order(
    outbound_id: str,
    photos: list[UploadFile] | None = File(default=None),
    note: str = Form(default=""),
    client_request_id: str = Form(...),
    admin=Depends(require_admin_user),
):
    request_id = client_request_id.strip()
    if not request_id:
        raise HTTPException(status_code=400, detail="缺少请求编号，请重试")
    uploads = photos or []
    with connect() as conn:
        existing = one(conn, "SELECT * FROM outbound_orders WHERE ship_request_id = ?", (request_id,))
        if existing:
            if existing["id"] != outbound_id:
                raise HTTPException(status_code=409, detail="请求编号已被其他出库单使用")
            return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)
        outbound = _outbound(conn, outbound_id)
        if outbound["status"] != "pending":
            raise HTTPException(status_code=409, detail="当前出库单不能确认发货")
        orders = _outbound_orders(conn, outbound_id)
        if not orders or any(order["status"] not in FULFILLED_ORDER_STATUSES | {"preparing"} for order in orders):
            raise HTTPException(status_code=409, detail="出库单中的订单状态已变化，请刷新后重试")
        if not any(order["status"] == "preparing" for order in orders):
            with transaction() as reconcile_conn:
                reconcile_conn.execute(
                    "UPDATE outbound_orders SET ship_request_id = ? WHERE id = ? AND ship_request_id IS NULL",
                    (request_id, outbound_id),
                )
                reconcile_outbound_status_for_orders(reconcile_conn, [order["id"] for order in orders], admin)
                bump_resources(reconcile_conn, "orders", "outbounds", "dashboard", "quota")
                return _outbound_out(reconcile_conn, _outbound(reconcile_conn, outbound_id), include_details=True)
    processed = await process_shipping_uploads(
        uploads,
        order_no=outbound["outbound_no"],
        unit_name=outbound["unit_name_snapshot"],
        operator_username=admin["username"],
    )
    try:
        with transaction() as conn:
            existing = one(conn, "SELECT * FROM outbound_orders WHERE ship_request_id = ?", (request_id,))
            if existing:
                cleanup_photos(processed)
                if existing["id"] != outbound_id:
                    raise HTTPException(status_code=409, detail="请求编号已被其他出库单使用")
                return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)
            current = _outbound(conn, outbound_id)
            if current["status"] != "pending":
                raise HTTPException(status_code=409, detail="当前出库单不能确认发货")
            orders = _outbound_orders(conn, outbound_id)
            if not orders or any(order["status"] not in FULFILLED_ORDER_STATUSES | {"preparing"} for order in orders):
                raise HTTPException(status_code=409, detail="出库单中的订单状态已变化，请刷新后重试")
            preparing_orders = [order for order in orders if order["status"] == "preparing"]
            if not preparing_orders:
                cleanup_photos(processed)
                conn.execute(
                    "UPDATE outbound_orders SET ship_request_id = ? WHERE id = ? AND ship_request_id IS NULL",
                    (request_id, outbound_id),
                )
                reconcile_outbound_status_for_orders(conn, [order["id"] for order in orders], admin)
                bump_resources(conn, "orders", "outbounds", "dashboard", "quota")
                return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)
            for order in preparing_orders:
                for photo in processed:
                    conn.execute(
                        """
                        INSERT INTO order_shipping_photos(
                          id, order_id, image_path, thumbnail_path, uploaded_by, source, mime_type, file_size, width, height, sha256
                        ) VALUES (?, ?, ?, ?, ?, 'camera', ?, ?, ?, ?, ?)
                        """,
                        (str(uuid4()), order["id"], photo.image_path, photo.thumbnail_path, admin["id"], photo.mime_type, photo.file_size, photo.width, photo.height, photo.sha256),
                    )
                conn.execute(
                    """
                    UPDATE orders
                    SET status = 'shipped', shipped_at = CURRENT_TIMESTAMP, version = version + 1,
                        updated_at = CURRENT_TIMESTAMP, shipping_note = ?, ship_request_id = ?
                    WHERE id = ? AND status = 'preparing'
                    """,
                    (note.strip(), f"outbound:{outbound_id}:{request_id}:{order['id']}", order["id"]),
                )
                conn.execute(
                    "INSERT INTO order_logs(id, order_id, actor_id, action, old_status, new_status, detail) VALUES (?, ?, ?, 'ship', 'preparing', 'shipped', ?)",
                    (str(uuid4()), order["id"], admin["id"], note.strip()),
                )
                updated_order = one(conn, "SELECT * FROM orders WHERE id = ?", (order["id"],))
                enqueue_order_status_changed(conn, updated_order, "shipped")
            conn.execute(
                "UPDATE outbound_orders SET ship_request_id = ? WHERE id = ? AND status = 'pending' AND ship_request_id IS NULL",
                (request_id, outbound_id),
            )
            reconcile_outbound_status_for_orders(conn, [order["id"] for order in orders], admin)
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                "OUTBOUND_ORDER_SHIPPED",
                "outbound_order",
                outbound_id,
                after_json=json.dumps({"order_ids": [order["id"] for order in orders], "shipping_photo_count": len(processed), "client_request_id": request_id}, ensure_ascii=False),
            )
            invalidate_dashboard_cache()
            bump_resources(conn, "orders", "outbounds", "dashboard", "quota")
            return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)
    except Exception:
        cleanup_photos(processed)
        raise
