import json
import os
import sqlite3
from datetime import datetime, timezone
from uuid import uuid4

import httpx


def configure_db(tmp_path):
    os.environ["APP_ENV"] = "test"
    os.environ["DATABASE_PATH"] = str(tmp_path / "push-worker.db")
    os.environ["UPLOAD_DIR"] = str(tmp_path / "uploads")
    os.environ["PRIVATE_UPLOAD_DIR"] = str(tmp_path / "private_uploads")
    from app.database import init_db

    init_db()


def insert_admin_event(tmp_path):
    configure_db(tmp_path)
    from app.database import connect

    user_id = str(uuid4())
    device_id = str(uuid4())
    event_id = str(uuid4())
    order_id = str(uuid4())
    with connect() as conn:
        conn.execute(
            "INSERT INTO users(id, username, password_hash, display_name, role, active, session_version) VALUES (?, ?, 'x', '推送管理员', 'admin', 1, 1)",
            (user_id, f"push-admin-{user_id[:6]}"),
        )
        conn.execute(
            """
            INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot, status)
            VALUES (?, 'SP-PUSH-001', ?, '推送单位', '收货点', 'pending')
            """,
            (order_id, _insert_unit(conn)),
        )
        conn.execute(
            """
            INSERT INTO push_devices(id, user_id, registration_id, installation_id, active, session_version)
            VALUES (?, ?, 'registration-worker-0001', 'installation-worker-0001', 1, 1)
            """,
            (device_id, user_id),
        )
        conn.execute(
            """
            INSERT INTO push_outbox(event_id, event_type, order_id, recipient_scope, payload_json)
            VALUES (?, 'ORDER_CREATED', ?, 'admins', ?)
            """,
            (event_id, order_id, json.dumps({"order_id": order_id, "order_no": "SP-PUSH-001"})),
        )
        conn.commit()
    return event_id, device_id


def _insert_unit(conn):
    unit_id = str(uuid4())
    conn.execute(
        "INSERT INTO units(id, unit_code, unit_name, default_delivery_point, active) VALUES (?, ?, '推送单位', '收货点', 1)",
        (unit_id, f"PUSH-{unit_id[:6]}"),
    )
    return unit_id


def test_jpush_client_uses_registration_id_and_reports_provider_acceptance():
    from app.services.jpush import JPushClient

    captured = {}

    def handler(request: httpx.Request):
        captured["authorization"] = request.headers.get("authorization", "")
        captured["payload"] = json.loads(request.content)
        return httpx.Response(200, json={"sendno": "0", "msg_id": "998877"})

    client = JPushClient(
        app_key="test-app-key",
        master_secret="test-master-secret",
        enabled=True,
        transport=httpx.MockTransport(handler),
    )
    result = client.send(
        "registration-worker-0001",
        {
            "event_id": str(uuid4()),
            "event_type": "ORDER_CREATED",
            "order_id": str(uuid4()),
            "payload_json": json.dumps({"order_no": "SP-PUSH-001"}),
        },
    )

    assert result.accepted is True
    assert result.provider_message_id == "998877"
    assert result.invalid_device is False
    assert captured["authorization"].startswith("Basic ")
    assert captured["payload"]["audience"] == {"registration_id": ["registration-worker-0001"]}
    android = captured["payload"]["notification"]["android"]
    assert android["channel_id"] == "new_orders"
    assert android["title"] == "三公鲜配 · 新订单"
    assert set(android["extras"]) == {"event_id", "event_type", "order_id"}
    assert "test-master-secret" not in json.dumps(captured["payload"])


def test_jpush_client_maps_single_missing_target_to_invalid_device():
    from app.services.jpush import JPushClient

    client = JPushClient(
        app_key="test-app-key",
        master_secret="test-master-secret",
        enabled=True,
        transport=httpx.MockTransport(lambda request: httpx.Response(400, json={"error": {"code": 1011, "message": "no target"}})),
    )
    result = client.send(
        "registration-missing-0001",
        {"event_id": str(uuid4()), "event_type": "ORDER_STATUS_CHANGED", "order_id": str(uuid4()), "payload_json": "{}"},
    )
    assert result.accepted is False
    assert result.invalid_device is True
    assert "test-master-secret" not in result.error


def test_worker_claims_event_once_and_marks_provider_acceptance(tmp_path):
    event_id, device_id = insert_admin_event(tmp_path)
    from app.database import connect, one
    from app.services.jpush import JPushResult
    from app.workers.push_worker import claim_next_event, process_event

    first = claim_next_event()
    second = claim_next_event()
    assert first["event_id"] == event_id
    assert second is None

    class AcceptedClient:
        def send(self, registration_id, event):
            assert registration_id == "registration-worker-0001"
            return JPushResult(True, False, "provider-message-1", 200, "")

    process_event(first, AcceptedClient())
    with connect() as conn:
        outbox = one(conn, "SELECT * FROM push_outbox WHERE event_id = ?", (event_id,))
        delivery = one(conn, "SELECT * FROM push_deliveries WHERE event_id = ? AND device_id = ?", (event_id, device_id))
    assert outbox["status"] == "sent"
    assert delivery["status"] == "accepted_by_provider"
    assert delivery["provider_message_id"] == "provider-message-1"


def test_worker_retries_then_deactivates_invalid_device(tmp_path):
    event_id, device_id = insert_admin_event(tmp_path)
    from app.database import connect, one
    from app.services.jpush import JPushResult
    from app.workers.push_worker import claim_next_event, process_event

    event = claim_next_event()

    class FailedClient:
        def send(self, registration_id, event):
            return JPushResult(False, False, "", 503, "服务暂时不可用")

    process_event(event, FailedClient())
    with connect() as conn:
        retry = one(conn, "SELECT * FROM push_outbox WHERE event_id = ?", (event_id,))
    assert retry["status"] == "retry"
    assert retry["attempt_count"] == 1
    assert retry["next_attempt_at"]

    with connect() as conn:
        conn.execute("UPDATE push_outbox SET status = 'processing' WHERE event_id = ?", (event_id,))
        conn.commit()

    class InvalidClient:
        def send(self, registration_id, event):
            return JPushResult(False, True, "", 400, "无效设备")

    process_event(event, InvalidClient())
    with connect() as conn:
        device = one(conn, "SELECT * FROM push_devices WHERE id = ?", (device_id,))
        delivery = one(conn, "SELECT * FROM push_deliveries WHERE event_id = ? AND device_id = ?", (event_id, device_id))
    assert device["active"] == 0
    assert delivery["status"] == "invalid_device"


def test_worker_recovers_stale_processing_events(tmp_path):
    event_id, _ = insert_admin_event(tmp_path)
    from app.database import connect, one
    from app.workers.push_worker import recover_stale_events

    with connect() as conn:
        conn.execute(
            "UPDATE push_outbox SET status = 'processing', processing_started_at = datetime('now', '-10 minutes') WHERE event_id = ?",
            (event_id,),
        )
        conn.commit()

    recovered = recover_stale_events(timeout_seconds=300)
    with connect() as conn:
        event = one(conn, "SELECT * FROM push_outbox WHERE event_id = ?", (event_id,))
    assert recovered == 1
    assert event["status"] == "retry"
    assert event["processing_started_at"] is None


def test_worker_only_treats_missing_push_tables_as_startup_retryable():
    from app.workers.push_worker import is_push_schema_not_ready

    assert is_push_schema_not_ready(sqlite3.OperationalError("no such table: push_outbox")) is True
    assert is_push_schema_not_ready(sqlite3.OperationalError("no such table: push_deliveries")) is True
    assert is_push_schema_not_ready(sqlite3.OperationalError("database is locked")) is False
