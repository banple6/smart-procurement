import json
from uuid import uuid4


def enqueue_order_created(conn, order: dict) -> str:
    event_id = str(uuid4())
    payload = {"order_id": order["id"], "order_no": order["order_no"]}
    conn.execute(
        """
        INSERT INTO push_outbox(event_id, event_type, order_id, recipient_scope, payload_json)
        VALUES (?, 'ORDER_CREATED', ?, 'admins', ?)
        """,
        (event_id, order["id"], json.dumps(payload, ensure_ascii=False, separators=(",", ":"))),
    )
    return event_id


def enqueue_order_status_changed(conn, order: dict, new_status: str) -> str:
    event_id = str(uuid4())
    payload = {"order_id": order["id"], "order_no": order["order_no"], "status": new_status}
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
    return event_id
