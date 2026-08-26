import json
from io import BytesIO
from urllib.parse import quote
from uuid import uuid4
from zipfile import ZIP_DEFLATED, ZipFile

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Response, UploadFile

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import require_admin_user
from ..services.batch_exports import outbound_order_workbook
from ..services.dashboard_cache import invalidate_dashboard_cache
from ..services.local_time import display_local_time, local_now
from ..services.push_outbox import enqueue_order_status_changed
from ..services.shipping_photos import cleanup_photos, process_shipping_uploads


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
        SELECT outbound_orders.*, delivery_batches.batch_no, delivery_batches.name AS batch_name,
               delivery_batches.status AS batch_status
        FROM outbound_orders
        JOIN delivery_batches ON delivery_batches.id = outbound_orders.preparation_batch_id
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
    order_count = one(conn, "SELECT COUNT(*) AS count FROM outbound_order_orders WHERE outbound_order_id = ?", (row["id"],))["count"]
    result["order_count"] = int(order_count)
    line_count = len(_outbound_lines(conn, row["id"]))
    result["product_count"] = line_count
    if include_details:
        result["orders"] = _outbound_orders(conn, row["id"])
        result["lines"] = _outbound_lines(conn, row["id"])
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
            existing = one(
                conn,
                "SELECT * FROM outbound_orders WHERE preparation_batch_id = ? AND unit_id = ?",
                (batch_id, unit_id),
            )
            if existing:
                continue
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
                    batch_id,
                    unit_id,
                    first["unit_name_snapshot"],
                    first["delivery_point_snapshot"] or "",
                    admin["id"],
                ),
            )
            conn.executemany(
                "INSERT INTO outbound_order_orders(outbound_order_id, order_id) VALUES (?, ?)",
                [(outbound_id, order["id"]) for order in orders],
            )
            created += 1
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                "OUTBOUND_ORDER_CREATED",
                "outbound_order",
                outbound_id,
                after_json=json.dumps({"outbound_no": outbound_no, "batch_no": batch["batch_no"], "order_ids": [order["id"] for order in orders]}, ensure_ascii=False),
            )
        rows = all_rows(
            conn,
            "SELECT * FROM outbound_orders WHERE preparation_batch_id = ? ORDER BY unit_name_snapshot, id",
            (batch_id,),
        )
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
            SELECT outbound_orders.*, delivery_batches.batch_no, delivery_batches.name AS batch_name
            FROM outbound_orders
            JOIN delivery_batches ON delivery_batches.id = outbound_orders.preparation_batch_id
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
            entries.append((f"出库单_{document['unit_name_snapshot']}_{document['outbound_no']}.xlsx", workbook, row["id"]))
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
    filename = f"出库单_{document['unit_name_snapshot']}_{document['outbound_no']}.xlsx"
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
        if not orders or any(order["status"] != "preparing" for order in orders):
            raise HTTPException(status_code=409, detail="出库单中的订单状态已变化，请刷新后重试")
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
            if not orders or any(order["status"] != "preparing" for order in orders):
                raise HTTPException(status_code=409, detail="出库单中的订单状态已变化，请刷新后重试")
            for order in orders:
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
                """
                UPDATE outbound_orders
                SET status = 'shipped', shipped_at = CURRENT_TIMESTAMP, shipped_by = ?, ship_request_id = ?,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'pending'
                """,
                (admin["id"], request_id, outbound_id),
            )
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
            return _outbound_out(conn, _outbound(conn, outbound_id), include_details=True)
    except Exception:
        cleanup_photos(processed)
        raise
