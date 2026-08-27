import json

from ..database import all_rows, write_audit


FULFILLED_ORDER_STATUSES = {"shipped", "completed"}


def reconcile_outbound_status_for_orders(conn, order_ids: list[str], actor: dict) -> list[str]:
    """Mark affected pending outbounds shipped only after every linked order is fulfilled.

    Legacy single-order shipment remains supported. This helper only synchronizes the
    parent outbound document; it never touches inventory, photos, or push delivery.
    """
    unique_order_ids = sorted({order_id for order_id in order_ids if order_id})
    if not unique_order_ids:
        return []

    placeholders = ", ".join("?" for _ in unique_order_ids)
    outbound_rows = all_rows(
        conn,
        f"""
        SELECT DISTINCT outbound_orders.id, outbound_orders.outbound_no
        FROM outbound_orders
        JOIN outbound_order_orders ON outbound_order_orders.outbound_order_id = outbound_orders.id
        WHERE outbound_orders.status = 'pending'
          AND outbound_order_orders.order_id IN ({placeholders})
        """,
        unique_order_ids,
    )

    reconciled: list[str] = []
    for outbound in outbound_rows:
        statuses = all_rows(
            conn,
            """
            SELECT orders.id, orders.status
            FROM outbound_order_orders
            JOIN orders ON orders.id = outbound_order_orders.order_id
            WHERE outbound_order_orders.outbound_order_id = ?
            """,
            (outbound["id"],),
        )
        if not statuses or any(row["status"] not in FULFILLED_ORDER_STATUSES for row in statuses):
            continue

        cursor = conn.execute(
            """
            UPDATE outbound_orders
            SET status = 'shipped',
                shipped_at = COALESCE(shipped_at, CURRENT_TIMESTAMP),
                shipped_by = COALESCE(shipped_by, ?),
                version = version + 1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending'
            """,
            (actor["id"], outbound["id"]),
        )
        if cursor.rowcount != 1:
            continue
        write_audit(
            conn,
            actor["id"],
            actor["role"],
            "OUTBOUND_ORDER_RECONCILED_SHIPPED",
            "outbound_order",
            outbound["id"],
            after_json=json.dumps(
                {
                    "outbound_no": outbound["outbound_no"],
                    "order_ids": [row["id"] for row in statuses],
                    "source": "order_shipment_reconciliation",
                },
                ensure_ascii=False,
            ),
        )
        reconciled.append(outbound["id"])
    return reconciled
