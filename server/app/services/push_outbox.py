import json
from datetime import datetime, timezone
from uuid import uuid4


def enqueue_order_created(conn, order: dict) -> str:
    event_id = str(uuid4())
    payload = {
        "event_type": "ORDER_CREATED",
        "entity_type": "order",
        "entity_id": order["id"],
        "order_id": order["id"],
        "order_no": order["order_no"],
        "version": int(order.get("version") or 1),
        "occurred_at": datetime.now(timezone.utc).isoformat(),
    }
    conn.execute(
        """
        INSERT INTO push_outbox(event_id, event_type, order_id, recipient_scope, payload_json)
        VALUES (?, 'ORDER_CREATED', ?, 'admins', ?)
        """,
        (event_id, order["id"], json.dumps(payload, ensure_ascii=False, separators=(",", ":"))),
    )
    return event_id


def enqueue_order_status_changed(conn, order: dict, new_status: str) -> str:
    """Queue one committed status invalidation for the owner and all admins.

    The outbox schema deliberately has one audience per row.  The two rows are
    delivery fan-out for one committed business transition; neither path changes
    order state and retries remain idempotent per device.
    """
    event_id = str(uuid4())
    payload = {
        "event_type": "ORDER_STATUS_CHANGED",
        "entity_type": "order",
        "entity_id": order["id"],
        "order_id": order["id"],
        "order_no": order["order_no"],
        "status": new_status,
        "version": int(order.get("version") or 1),
        "occurred_at": datetime.now(timezone.utc).isoformat(),
    }
    conn.execute(
        """
        INSERT INTO push_outbox(
          event_id, event_type, order_id, recipient_scope, recipient_unit_id, payload_json
        ) VALUES (?, 'ORDER_STATUS_CHANGED', ?, 'unit', ?, ?)
        """,
        (
            event_id,
            order["id"],
            order["unit_id"],
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        ),
    )
    conn.execute(
        """
        INSERT INTO push_outbox(event_id, event_type, order_id, recipient_scope, payload_json)
        VALUES (?, 'ORDER_STATUS_CHANGED', ?, 'admins', ?)
        """,
        (
            str(uuid4()),
            order["id"],
            json.dumps(payload, ensure_ascii=False, separators=(",", ":")),
        ),
    )
    return event_id
