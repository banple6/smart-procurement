import json
from urllib.parse import quote
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import require_admin_user
from ..schemas import DeliveryBatchCreate, DeliveryBatchOrdersPatch, DeliveryBatchStatusPatch
from ..services.batch_aggregation import aggregate_batch
from ..services.batch_exports import (
    batch_outbound_workbook,
    batch_picking_filename,
    batch_picking_workbook,
    batch_picking_workbook_multi,
    batch_summary_workbook,
)
from ..services.local_time import display_local_time, local_now


router = APIRouter(prefix="/admin/batches", tags=["delivery-batches"])


def _batch_no(conn) -> str:
    prefix = "PS" + local_now().strftime("%Y%m%d")
    sequence = f"delivery-batch-{prefix}"
    conn.execute("INSERT OR IGNORE INTO app_sequences(name, value) VALUES (?, 0)", (sequence,))
    conn.execute("UPDATE app_sequences SET value = value + 1 WHERE name = ?", (sequence,))
    value = one(conn, "SELECT value FROM app_sequences WHERE name = ?", (sequence,))["value"]
    return f"{prefix}-{int(value):04d}"


def _batch(conn, batch_id: str) -> dict:
    row = one(conn, "SELECT * FROM delivery_batches WHERE id = ?", (batch_id,))
    if not row:
        raise HTTPException(status_code=404, detail="配送批次不存在")
    return row


def _batch_out(conn, batch: dict, include_orders: bool = True) -> dict:
    result = {**batch}
    for field in ("created_at", "updated_at", "closed_at"):
        result[field] = display_local_time(result.get(field))
    result["version"] = int(result.get("version") or 1)
    if include_orders:
        result["orders"] = all_rows(
            conn,
            """
            SELECT orders.id, orders.order_no, orders.unit_id, COALESCE(units.unit_code, '') AS unit_code, orders.unit_name_snapshot,
                   orders.delivery_point_snapshot, orders.status, orders.total_cents, orders.created_at,
                   orders.version
            FROM delivery_batch_orders
            JOIN orders ON orders.id = delivery_batch_orders.order_id
            LEFT JOIN units ON units.id = orders.unit_id
            WHERE delivery_batch_orders.batch_id = ? AND orders.is_deleted = 0
            ORDER BY orders.unit_name_snapshot, orders.created_at, orders.id
            """,
            (batch["id"],),
        )
        for order in result["orders"]:
            order["created_at"] = display_local_time(order.get("created_at"))
    return result


def _unique_ids(values: list[str]) -> list[str]:
    return list(dict.fromkeys(value.strip() for value in values if value.strip()))


def _validate_orders_for_batch(conn, order_ids: list[str], current_batch_id: str | None = None):
    if not order_ids:
        raise HTTPException(status_code=400, detail="请至少选择一笔订单")
    placeholders = ",".join("?" for _ in order_ids)
    orders = all_rows(
        conn,
        f"SELECT id, order_no, status, is_deleted FROM orders WHERE id IN ({placeholders})",
        order_ids,
    )
    if len(orders) != len(order_ids):
        raise HTTPException(status_code=404, detail="部分订单不存在，请刷新后重试")
    # The current order state machine moves a successful accept directly to
    # preparing. Keep accepted for compatibility with older data, but never
    # let pending or already fulfilled orders enter a new preparation order.
    invalid = [row["order_no"] for row in orders if row["is_deleted"] or row["status"] not in {"accepted", "preparing"}]
    if invalid:
        raise HTTPException(status_code=409, detail="所选订单中包含不能生成备货单的订单，请仅选择已接单且未进入备货流程的订单")
    existing = all_rows(
        conn,
        f"""
        SELECT delivery_batch_orders.order_id, delivery_batch_orders.batch_id
        FROM delivery_batch_orders
        JOIN delivery_batches ON delivery_batches.id = delivery_batch_orders.batch_id
        WHERE delivery_batch_orders.order_id IN ({placeholders})
          AND delivery_batches.status IN ('open', 'closed')
        """,
        order_ids,
    )
    conflicting = [row for row in existing if row["batch_id"] != current_batch_id]
    if conflicting:
        raise HTTPException(status_code=409, detail="部分订单已经属于其他备货单")


def _release_archived_batch_links(conn, order_ids: list[str]):
    """Release legacy archived links required by the existing UNIQUE(order_id)."""
    if not order_ids:
        return
    placeholders = ",".join("?" for _ in order_ids)
    conn.execute(
        f"""
        DELETE FROM delivery_batch_orders
        WHERE order_id IN ({placeholders})
          AND batch_id IN (SELECT id FROM delivery_batches WHERE status = 'cancelled')
        """,
        order_ids,
    )


@router.post("")
def create_delivery_batch(body: DeliveryBatchCreate, admin=Depends(require_admin_user)):
    order_ids = _unique_ids(body.order_ids)
    with transaction() as conn:
        _validate_orders_for_batch(conn, order_ids)
        _release_archived_batch_links(conn, order_ids)
        batch_id = str(uuid4())
        batch_no = _batch_no(conn)
        conn.execute(
            "INSERT INTO delivery_batches(id, batch_no, name, note, created_by) VALUES (?, ?, ?, ?, ?)",
            (batch_id, batch_no, body.name.strip(), body.note.strip(), admin["id"]),
        )
        conn.executemany(
            "INSERT INTO delivery_batch_orders(batch_id, order_id, added_by) VALUES (?, ?, ?)",
            [(batch_id, order_id, admin["id"]) for order_id in order_ids],
        )
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "DELIVERY_BATCH_CREATED",
            "delivery_batch",
            batch_id,
            after_json=json.dumps({"batch_no": batch_no, "order_count": len(order_ids)}, ensure_ascii=False),
        )
        return _batch_out(conn, _batch(conn, batch_id))


@router.get("")
def list_delivery_batches(
    status: str | None = Query(default=None, pattern="^(open|closed|cancelled)$"),
    date_from: str | None = Query(default=None),
    date_to: str | None = Query(default=None),
    admin=Depends(require_admin_user),
):
    with connect() as conn:
        conditions = []
        params: list[str] = []
        if status:
            conditions.append("delivery_batches.status = ?")
            params.append(status)
        else:
            # Archived batches remain available to audit callers via status=cancelled,
            # but should not compete with active/completed preparation records.
            conditions.append("delivery_batches.status IN ('open', 'closed')")
        if date_from:
            conditions.append("date(datetime(delivery_batches.created_at, '+8 hours')) >= date(?)")
            params.append(date_from)
        if date_to:
            conditions.append("date(datetime(delivery_batches.created_at, '+8 hours')) <= date(?)")
            params.append(date_to)
        where = f"WHERE {' AND '.join(conditions)}" if conditions else ""
        rows = all_rows(
            conn,
            f"""
            SELECT delivery_batches.*,
                   COUNT(DISTINCT CASE WHEN orders.is_deleted = 0 THEN orders.id END) AS order_count,
                   COUNT(DISTINCT CASE WHEN orders.is_deleted = 0 THEN orders.unit_id END) AS unit_count,
                   COUNT(DISTINCT CASE WHEN orders.is_deleted = 0 THEN order_items.product_id END) AS product_count,
                   GROUP_CONCAT(DISTINCT CASE WHEN orders.is_deleted = 0 THEN units.unit_code END) AS unit_codes
            FROM delivery_batches
            LEFT JOIN delivery_batch_orders ON delivery_batch_orders.batch_id = delivery_batches.id
            LEFT JOIN orders ON orders.id = delivery_batch_orders.order_id
            LEFT JOIN units ON units.id = orders.unit_id
            LEFT JOIN order_items ON order_items.order_id = orders.id
            {where}
            GROUP BY delivery_batches.id
            ORDER BY delivery_batches.created_at DESC, delivery_batches.id DESC
            """,
            params,
        )
        return {"items": [_batch_out(conn, row, include_orders=False) for row in rows]}


@router.get("/eligible-orders")
def eligible_delivery_batch_orders(
    status: str = "accepted,preparing",
    admin=Depends(require_admin_user),
):
    allowed = {"accepted", "preparing", "shipped"}
    statuses = list(dict.fromkeys(value.strip() for value in status.split(",") if value.strip()))
    if not statuses or any(value not in allowed for value in statuses):
        raise HTTPException(status_code=400, detail="可选订单状态不正确")
    placeholders = ",".join("?" for _ in statuses)
    with connect() as conn:
        rows = all_rows(
            conn,
            f"""
            SELECT orders.id, orders.order_no, orders.unit_id, COALESCE(units.unit_code, '') AS unit_code, orders.unit_name_snapshot,
                   orders.delivery_point_snapshot, orders.status, orders.total_cents,
                   orders.created_at, orders.version
            FROM orders
            LEFT JOIN units ON units.id = orders.unit_id
            WHERE orders.is_deleted = 0
              AND orders.status IN ({placeholders})
            AND NOT EXISTS (
                SELECT 1
                FROM delivery_batch_orders
                JOIN delivery_batches ON delivery_batches.id = delivery_batch_orders.batch_id
                WHERE delivery_batch_orders.order_id = orders.id
                  AND delivery_batches.status IN ('open', 'closed')
              )
            ORDER BY orders.created_at, orders.id
            """,
            statuses,
        )
        for row in rows:
            row["created_at"] = display_local_time(row.get("created_at"))
        return {"items": rows}


@router.get("/bulk.xlsx")
def export_delivery_batches_picking_list(batch_ids: list[str] = Query(default=[]), admin=Depends(require_admin_user)):
    ids = _unique_ids(batch_ids)
    if not ids:
        raise HTTPException(status_code=400, detail="请至少选择一张备货单")
    with transaction() as conn:
        placeholders = ",".join("?" for _ in ids)
        batches = all_rows(conn, f"SELECT * FROM delivery_batches WHERE id IN ({placeholders})", ids)
        if len(batches) != len(ids):
            raise HTTPException(status_code=404, detail="部分备货单不存在，请刷新后重试")
        aggregations = [aggregate_batch(conn, batch["id"], "all") for batch in batches]
        for batch in batches:
            write_audit(conn, admin["id"], admin["role"], "DELIVERY_BATCH_PICKING_LIST_EXPORTED", "delivery_batch", batch["id"])
    return _document_response(batch_picking_workbook_multi(aggregations), "三公鲜配_备货单批量导出.xlsx")


@router.get("/{batch_id}")
def delivery_batch_detail(batch_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        return _batch_out(conn, _batch(conn, batch_id))


@router.delete("/{batch_id}")
def archive_delivery_batch(batch_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        batch = _batch(conn, batch_id)
        if batch["status"] == "closed":
            raise HTTPException(status_code=409, detail="已完成备货的备货单只能保留，不能删除")
        if batch["status"] == "cancelled":
            return _batch_out(conn, batch)
        archived_order_ids = [
            row["order_id"]
            for row in all_rows(conn, "SELECT order_id FROM delivery_batch_orders WHERE batch_id = ?", (batch_id,))
        ]
        conn.execute(
            """
            UPDATE delivery_batches
            SET status = 'cancelled', closed_by = ?, closed_at = CURRENT_TIMESTAMP,
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'open'
            """,
            (admin["id"], batch_id),
        )
        # The legacy table has UNIQUE(order_id), so archived preparation-order
        # links must be released while the batch metadata and audit trail stay.
        conn.execute("DELETE FROM delivery_batch_orders WHERE batch_id = ?", (batch_id,))
        updated = _batch(conn, batch_id)
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "DELIVERY_BATCH_ARCHIVED",
            "delivery_batch",
            batch_id,
            before_json=json.dumps({"status": batch["status"]}, ensure_ascii=False),
            after_json=json.dumps({"status": updated["status"], "released_order_ids": archived_order_ids}, ensure_ascii=False),
        )
        return _batch_out(conn, updated)


@router.patch("/{batch_id}/orders")
def patch_delivery_batch_orders(batch_id: str, body: DeliveryBatchOrdersPatch, admin=Depends(require_admin_user)):
    add_ids = _unique_ids(body.add_order_ids)
    remove_ids = _unique_ids(body.remove_order_ids)
    if set(add_ids) & set(remove_ids):
        raise HTTPException(status_code=400, detail="同一订单不能同时加入和移出批次")
    if not add_ids and not remove_ids:
        raise HTTPException(status_code=400, detail="未选择需要调整的订单")
    with transaction() as conn:
        batch = _batch(conn, batch_id)
        if batch["status"] != "open":
            raise HTTPException(status_code=409, detail="只有备货中的备货单可以调整订单")
        if body.expected_version is not None and int(batch["version"]) != body.expected_version:
            raise HTTPException(status_code=409, detail="备货单已被其他管理员更新，请刷新后重试")
        if add_ids:
            _validate_orders_for_batch(conn, add_ids, batch_id)
            _release_archived_batch_links(conn, add_ids)
            conn.executemany(
                "INSERT OR IGNORE INTO delivery_batch_orders(batch_id, order_id, added_by) VALUES (?, ?, ?)",
                [(batch_id, order_id, admin["id"]) for order_id in add_ids],
            )
        if remove_ids:
            placeholders = ",".join("?" for _ in remove_ids)
            conn.execute(
                f"DELETE FROM delivery_batch_orders WHERE batch_id = ? AND order_id IN ({placeholders})",
                (batch_id, *remove_ids),
            )
        conn.execute(
            "UPDATE delivery_batches SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (batch_id,),
        )
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "DELIVERY_BATCH_ORDERS_UPDATED",
            "delivery_batch",
            batch_id,
            after_json=json.dumps({"added": add_ids, "removed": remove_ids}, ensure_ascii=False),
        )
        return _batch_out(conn, _batch(conn, batch_id))


@router.patch("/{batch_id}/status")
def patch_delivery_batch_status(batch_id: str, body: DeliveryBatchStatusPatch, admin=Depends(require_admin_user)):
    with transaction() as conn:
        batch = _batch(conn, batch_id)
        if body.expected_version is not None and int(batch["version"]) != body.expected_version:
            raise HTTPException(status_code=409, detail="备货单已被其他管理员更新，请刷新后重试")
        if batch["status"] == body.status:
            return _batch_out(conn, batch)
        allowed = {"open": {"closed", "cancelled"}, "closed": set(), "cancelled": set()}
        if body.status not in allowed.get(batch["status"], set()):
            raise HTTPException(status_code=409, detail="批次状态不能这样变更")
        closed_values = (admin["id"],) if body.status == "closed" else (None,)
        conn.execute(
            """
            UPDATE delivery_batches
            SET status = ?, closed_by = ?, closed_at = CASE WHEN ? = 'closed' THEN CURRENT_TIMESTAMP ELSE closed_at END,
                version = version + 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (body.status, closed_values[0], body.status, batch_id),
        )
        write_audit(conn, admin["id"], admin["role"], "DELIVERY_BATCH_STATUS_CHANGED", "delivery_batch", batch_id, before_json=json.dumps({"status": batch["status"]}), after_json=json.dumps({"status": body.status}))
        return _batch_out(conn, _batch(conn, batch_id))


@router.get("/{batch_id}/summary")
def delivery_batch_summary(batch_id: str, admin=Depends(require_admin_user)):
    with connect() as conn:
        _batch(conn, batch_id)
        return aggregate_batch(conn, batch_id, "all")


def _download_headers(filename: str) -> dict[str, str]:
    return {"Content-Disposition": f"attachment; filename*=UTF-8''{quote(filename)}"}


def _document_response(content: bytes, filename: str) -> Response:
    return Response(content, media_type="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", headers=_download_headers(filename))


def _audit_download(conn, admin: dict, batch_id: str, action: str):
    write_audit(conn, admin["id"], admin["role"], action, "delivery_batch", batch_id)


@router.get("/{batch_id}/summary.xlsx")
def export_delivery_batch_summary(batch_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        _batch(conn, batch_id)
        result = aggregate_batch(conn, batch_id, "all")
        _audit_download(conn, admin, batch_id, "DELIVERY_BATCH_SUMMARY_EXPORTED")
    return _document_response(batch_summary_workbook(result), f"三公鲜配_批次汇总_{result['batch']['batch_no']}.xlsx")


@router.get("/{batch_id}/picking-list.xlsx")
def export_delivery_batch_picking_list(batch_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        _batch(conn, batch_id)
        result = aggregate_batch(conn, batch_id, "picking")
        if not result["document_lines"]:
            raise HTTPException(status_code=409, detail="该批次暂无已接单或备货中的订单")
        _audit_download(conn, admin, batch_id, "DELIVERY_BATCH_PICKING_LIST_EXPORTED")
    return _document_response(batch_picking_workbook(result), batch_picking_filename(result))


@router.get("/{batch_id}/outbound.xlsx")
def export_delivery_batch_outbound(batch_id: str, admin=Depends(require_admin_user)):
    with transaction() as conn:
        _batch(conn, batch_id)
        result = aggregate_batch(conn, batch_id, "shipped")
        if not result["document_lines"]:
            raise HTTPException(status_code=409, detail="该批次暂无已发货订单，不能生成出库单")
        _audit_download(conn, admin, batch_id, "DELIVERY_BATCH_OUTBOUND_EXPORTED")
    return _document_response(batch_outbound_workbook(result), f"三公鲜配_出库单_{result['batch']['batch_no']}.xlsx")
