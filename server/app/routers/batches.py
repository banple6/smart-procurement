import hashlib
import json
from urllib.parse import quote
from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from ..database import all_rows, connect, one, transaction, write_audit
from ..dependencies import require_admin_user
from ..schemas import DeliveryBatchCreate, DeliveryBatchOrdersPatch, DeliveryBatchReconcile, DeliveryBatchStatusPatch
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


def _request_hash(payload: dict) -> str:
    encoded = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _idempotent_batch(conn, admin: dict, action: str, request_id: str | None, request_hash: str):
    if not request_id:
        return None
    row = one(
        conn,
        """
        SELECT object_id, after_json
        FROM audit_logs
        WHERE actor_id = ? AND action = ? AND request_id = ? AND result = 'success'
        ORDER BY created_at, id
        LIMIT 1
        """,
        (admin["id"], action, request_id),
    )
    if not row:
        return None
    try:
        previous = json.loads(row.get("after_json") or "{}")
    except json.JSONDecodeError:
        previous = {}
    if previous.get("request_hash") != request_hash:
        raise HTTPException(status_code=409, detail="请求编号已被其他备货操作使用")
    return _batch_out(conn, _batch(conn, row["object_id"]))


def _batch_has_outbound(conn, batch_id: str) -> bool:
    return one(conn, "SELECT 1 AS found FROM outbound_orders WHERE preparation_batch_id = ? LIMIT 1", (batch_id,)) is not None


def _assert_editable_batch(conn, batch: dict, expected_version: int | None = None):
    if batch["status"] != "open":
        raise HTTPException(status_code=409, detail="只有未完成备货且未归档的备货单可以调整")
    if _batch_has_outbound(conn, batch["id"]):
        raise HTTPException(status_code=409, detail="该备货单已经生成出库单，不能再调整")
    if expected_version is not None and int(batch["version"]) != int(expected_version):
        raise HTTPException(status_code=409, detail="备货单已被其他管理员修改，请刷新后重试")


def _assert_orders_not_outbound(conn, order_ids: list[str]):
    if not order_ids:
        return
    placeholders = ",".join("?" for _ in order_ids)
    if one(
        conn,
        f"SELECT 1 AS found FROM outbound_order_orders WHERE order_id IN ({placeholders}) LIMIT 1",
        order_ids,
    ):
        raise HTTPException(status_code=409, detail="部分订单已经进入出库流程，不能重新整理")


def _validate_orders_for_batch(conn, order_ids: list[str], current_batch_id: str | None = None):
    if not order_ids:
        raise HTTPException(status_code=400, detail="请至少选择一笔订单")
    placeholders = ",".join("?" for _ in order_ids)
    orders = all_rows(
        conn,
        f"SELECT id, order_no, unit_id, status, is_deleted FROM orders WHERE id IN ({placeholders})",
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
    _assert_orders_not_outbound(conn, order_ids)
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
    request_id = (body.client_request_id or "").strip() or None
    request_hash = _request_hash({"name": body.name.strip(), "note": body.note.strip(), "order_ids": sorted(order_ids)})
    with transaction() as conn:
        previous = _idempotent_batch(conn, admin, "DELIVERY_BATCH_CREATED", request_id, request_hash)
        if previous:
            return previous
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
            after_json=json.dumps({"batch_no": batch_no, "order_count": len(order_ids), "request_hash": request_hash}, ensure_ascii=False),
            request_id=request_id or "",
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
                   GROUP_CONCAT(DISTINCT CASE WHEN orders.is_deleted = 0 THEN units.unit_code END) AS unit_codes,
                   GROUP_CONCAT(
                       DISTINCT CASE WHEN orders.is_deleted = 0
                       THEN COALESCE(NULLIF(units.unit_code, ''), '--') || ' · ' || orders.unit_name_snapshot END
                   ) AS unit_labels
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


@router.get("/unit-workbench")
def delivery_batch_unit_workbench(admin=Depends(require_admin_user)):
    with connect() as conn:
        pending = all_rows(
            conn,
            """
            SELECT orders.id, orders.order_no, orders.unit_id, COALESCE(units.unit_code, '') AS unit_code,
                   orders.unit_name_snapshot, orders.delivery_point_snapshot, orders.status, orders.total_cents,
                   orders.created_at, orders.version
            FROM orders
            LEFT JOIN units ON units.id = orders.unit_id
            WHERE orders.is_deleted = 0
              AND orders.status IN ('accepted', 'preparing')
              AND NOT EXISTS (
                SELECT 1 FROM delivery_batch_orders
                JOIN delivery_batches ON delivery_batches.id = delivery_batch_orders.batch_id
                WHERE delivery_batch_orders.order_id = orders.id
                  AND delivery_batches.status IN ('open', 'closed')
              )
            ORDER BY orders.created_at, orders.id
            """,
        )
        memberships = all_rows(
            conn,
            """
            SELECT delivery_batches.id AS batch_id, delivery_batches.batch_no, delivery_batches.name,
                   delivery_batches.version, orders.id AS order_id, orders.order_no, orders.unit_id,
                   COALESCE(units.unit_code, '') AS unit_code, orders.unit_name_snapshot,
                   orders.delivery_point_snapshot, orders.status, orders.total_cents, orders.created_at
            FROM delivery_batches
            JOIN delivery_batch_orders ON delivery_batch_orders.batch_id = delivery_batches.id
            JOIN orders ON orders.id = delivery_batch_orders.order_id
            LEFT JOIN units ON units.id = orders.unit_id
            WHERE delivery_batches.status = 'open'
              AND orders.is_deleted = 0
              AND orders.status IN ('accepted', 'preparing')
              AND NOT EXISTS (
                SELECT 1 FROM outbound_orders WHERE outbound_orders.preparation_batch_id = delivery_batches.id
              )
            ORDER BY delivery_batches.created_at, delivery_batches.id, orders.created_at, orders.id
            """,
        )
        groups: dict[str, dict] = {}

        def group_for(row: dict) -> dict:
            return groups.setdefault(
                row["unit_id"],
                {
                    "unit_id": row["unit_id"],
                    "unit_code": row.get("unit_code") or "",
                    "unit_name": row["unit_name_snapshot"],
                    "delivery_point": row.get("delivery_point_snapshot") or "",
                    "pending_orders": [],
                    "open_batches": {},
                    "all_order_ids": set(),
                },
            )

        for row in pending:
            row["created_at"] = display_local_time(row.get("created_at"))
            group = group_for(row)
            group["pending_orders"].append(row)
            group["all_order_ids"].add(row["id"])
        for row in memberships:
            row["created_at"] = display_local_time(row.get("created_at"))
            group = group_for(row)
            batch = group["open_batches"].setdefault(
                row["batch_id"],
                {"id": row["batch_id"], "batch_no": row["batch_no"], "name": row["name"], "version": int(row["version"]), "orders": []},
            )
            batch["orders"].append(
                {key: row[key] for key in ("order_id", "order_no", "status", "total_cents", "created_at")}
            )
            group["all_order_ids"].add(row["order_id"])
        result = []
        for group in groups.values():
            order_ids = sorted(group.pop("all_order_ids"))
            product_count = 0
            if order_ids:
                placeholders = ",".join("?" for _ in order_ids)
                product_count = int(
                    one(conn, f"SELECT COUNT(DISTINCT product_id || '|' || unit_snapshot) AS count FROM order_items WHERE order_id IN ({placeholders})", order_ids)["count"]
                )
            group["open_batches"] = list(group["open_batches"].values())
            group["pending_order_count"] = len(group["pending_orders"])
            group["open_batch_count"] = len(group["open_batches"])
            group["product_count"] = product_count
            group["actionable"] = group["pending_order_count"] > 0 or group["open_batch_count"] > 1
            if group["actionable"]:
                result.append(group)
        result.sort(key=lambda item: (not bool(item["unit_code"]), item["unit_code"] or "", item["unit_name"]))
        return {"items": result}


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
    request_id = (body.client_request_id or "").strip() or None
    request_hash = _request_hash(
        {"batch_id": batch_id, "add_order_ids": sorted(add_ids), "remove_order_ids": sorted(remove_ids)}
    )
    with transaction() as conn:
        previous = _idempotent_batch(conn, admin, "DELIVERY_BATCH_ORDERS_UPDATED", request_id, request_hash)
        if previous:
            return previous
        batch = _batch(conn, batch_id)
        _assert_editable_batch(conn, batch, body.expected_version)
        before_order_ids = [
            row["order_id"]
            for row in all_rows(
                conn,
                "SELECT order_id FROM delivery_batch_orders WHERE batch_id = ? ORDER BY order_id",
                (batch_id,),
            )
        ]
        if add_ids:
            _validate_orders_for_batch(conn, add_ids, batch_id)
            _release_archived_batch_links(conn, add_ids)
            conn.executemany(
                "INSERT OR IGNORE INTO delivery_batch_orders(batch_id, order_id, added_by) VALUES (?, ?, ?)",
                [(batch_id, order_id, admin["id"]) for order_id in add_ids],
            )
        if remove_ids:
            placeholders = ",".join("?" for _ in remove_ids)
            existing = all_rows(
                conn,
                f"""
                SELECT delivery_batch_orders.order_id, orders.order_no, orders.status, orders.is_deleted
                FROM delivery_batch_orders
                JOIN orders ON orders.id = delivery_batch_orders.order_id
                WHERE delivery_batch_orders.batch_id = ? AND delivery_batch_orders.order_id IN ({placeholders})
                """,
                (batch_id, *remove_ids),
            )
            if len(existing) != len(remove_ids):
                raise HTTPException(status_code=409, detail="部分订单已经不在该备货单中，请刷新后重试")
            if any(row["is_deleted"] or row["status"] not in {"accepted", "preparing"} for row in existing):
                raise HTTPException(status_code=409, detail="部分订单已不能移出备货单，请刷新后重试")
            _assert_orders_not_outbound(conn, remove_ids)
            conn.execute(
                f"DELETE FROM delivery_batch_orders WHERE batch_id = ? AND order_id IN ({placeholders})",
                (batch_id, *remove_ids),
            )
        remaining = int(one(conn, "SELECT COUNT(*) AS count FROM delivery_batch_orders WHERE batch_id = ?", (batch_id,))["count"])
        if remaining:
            conn.execute(
                "UPDATE delivery_batches SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                (batch_id,),
            )
        else:
            conn.execute(
                """
                UPDATE delivery_batches
                SET status = 'cancelled', closed_by = ?, closed_at = CURRENT_TIMESTAMP,
                    version = version + 1, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                (admin["id"], batch_id),
            )
        audit_payload = {
            "added": add_ids,
            "removed": remove_ids,
            "remaining_order_count": remaining,
            "order_ids": [
                row["order_id"]
                for row in all_rows(
                    conn,
                    "SELECT order_id FROM delivery_batch_orders WHERE batch_id = ? ORDER BY order_id",
                    (batch_id,),
                )
            ],
            "request_hash": request_hash,
        }
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "DELIVERY_BATCH_ORDERS_UPDATED",
            "delivery_batch",
            batch_id,
            before_json=json.dumps({"order_ids": before_order_ids, "version": int(batch["version"])}, ensure_ascii=False),
            after_json=json.dumps(audit_payload, ensure_ascii=False),
            request_id=request_id or "",
        )
        if add_ids:
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                "BATCH_ORDER_ADDED",
                "delivery_batch",
                batch_id,
                after_json=json.dumps({"order_ids": add_ids}, ensure_ascii=False),
                request_id=request_id or "",
            )
        if remove_ids:
            write_audit(
                conn,
                admin["id"],
                admin["role"],
                "BATCH_ORDER_REMOVED",
                "delivery_batch",
                batch_id,
                after_json=json.dumps({"order_ids": remove_ids, "batch_archived": remaining == 0}, ensure_ascii=False),
                request_id=request_id or "",
            )
        return _batch_out(conn, _batch(conn, batch_id))


@router.post("/reconcile")
def reconcile_delivery_batches(body: DeliveryBatchReconcile, admin=Depends(require_admin_user)):
    source_ids = _unique_ids(body.source_batch_ids)
    order_ids = _unique_ids(body.order_ids)
    if body.target_batch_id in source_ids:
        raise HTTPException(status_code=400, detail="目标备货单不能同时作为来源备货单")
    involved_ids = [body.target_batch_id, *source_ids]
    if set(body.expected_versions) != set(involved_ids):
        raise HTTPException(status_code=400, detail="必须提供全部目标和来源备货单的最新版本")
    if any(int(version) < 1 for version in body.expected_versions.values()):
        raise HTTPException(status_code=400, detail="备货单版本必须大于等于 1")
    request_id = body.client_request_id.strip()
    request_hash = _request_hash(
        {
            "unit_id": body.unit_id,
            "target_batch_id": body.target_batch_id,
            "source_batch_ids": sorted(source_ids),
            "order_ids": sorted(order_ids),
            "expected_versions": body.expected_versions,
        }
    )
    with transaction() as conn:
        previous = _idempotent_batch(conn, admin, "BATCH_RECONCILED", request_id, request_hash)
        if previous:
            return previous
        batches = {batch_id: _batch(conn, batch_id) for batch_id in involved_ids}
        for batch_id, batch in batches.items():
            _assert_editable_batch(conn, batch, body.expected_versions[batch_id])
        placeholders = ",".join("?" for _ in involved_ids)
        linked = all_rows(
            conn,
            f"""
            SELECT delivery_batch_orders.batch_id, orders.id AS order_id, orders.order_no,
                   orders.unit_id, orders.status, orders.is_deleted
            FROM delivery_batch_orders
            JOIN orders ON orders.id = delivery_batch_orders.order_id
            WHERE delivery_batch_orders.batch_id IN ({placeholders})
              AND orders.unit_id = ?
            ORDER BY delivery_batch_orders.batch_id, orders.id
            """,
            (*involved_ids, body.unit_id),
        )
        linked_ids = {row["order_id"] for row in linked}
        if not linked_ids:
            raise HTTPException(status_code=409, detail="所选备货单中没有该单位的可整理订单")
        if set(order_ids) != linked_ids:
            raise HTTPException(status_code=409, detail="该单位的备货订单已经变化，请刷新后重新确认")
        invalid = [row["order_no"] for row in linked if row["is_deleted"] or row["status"] not in {"accepted", "preparing"}]
        if invalid:
            raise HTTPException(status_code=409, detail="部分订单已不能重新整理，请刷新后重试")
        _assert_orders_not_outbound(conn, order_ids)
        source_order_ids = [row["order_id"] for row in linked if row["batch_id"] in source_ids]
        if not source_order_ids:
            raise HTTPException(status_code=409, detail="没有需要迁移到目标备货单的订单")
        moving_placeholders = ",".join("?" for _ in source_order_ids)
        conn.execute(
            f"DELETE FROM delivery_batch_orders WHERE batch_id IN ({','.join('?' for _ in source_ids)}) AND order_id IN ({moving_placeholders})",
            (*source_ids, *source_order_ids),
        )
        conn.executemany(
            "INSERT INTO delivery_batch_orders(batch_id, order_id, added_by) VALUES (?, ?, ?)",
            [(body.target_batch_id, order_id, admin["id"]) for order_id in source_order_ids],
        )
        source_results = []
        for source_id in source_ids:
            remaining = int(one(conn, "SELECT COUNT(*) AS count FROM delivery_batch_orders WHERE batch_id = ?", (source_id,))["count"])
            if remaining:
                conn.execute(
                    "UPDATE delivery_batches SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    (source_id,),
                )
            else:
                conn.execute(
                    """
                    UPDATE delivery_batches
                    SET status = 'cancelled', closed_by = ?, closed_at = CURRENT_TIMESTAMP,
                        version = version + 1, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (admin["id"], source_id),
                )
            source_results.append({"batch_id": source_id, "remaining_order_count": remaining, "archived": remaining == 0})
        conn.execute(
            "UPDATE delivery_batches SET version = version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
            (body.target_batch_id,),
        )
        before = {
            "unit_id": body.unit_id,
            "target_batch_id": body.target_batch_id,
            "source_batch_ids": source_ids,
            "order_ids": order_ids,
            "versions": {batch_id: int(batch["version"]) for batch_id, batch in batches.items()},
        }
        after = {
            **before,
            "moved_order_ids": source_order_ids,
            "source_results": source_results,
            "request_hash": request_hash,
        }
        write_audit(
            conn,
            admin["id"],
            admin["role"],
            "BATCH_RECONCILED",
            "delivery_batch",
            body.target_batch_id,
            before_json=json.dumps(before, ensure_ascii=False),
            after_json=json.dumps(after, ensure_ascii=False),
            request_id=request_id,
        )
        return _batch_out(conn, _batch(conn, body.target_batch_id))


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
