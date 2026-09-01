import http.client
import json
import logging
import socket
import threading
import time
from urllib.parse import urlparse

import pytest
import uvicorn

from app.database import connect, transaction
from app.services.realtime import RESOURCE_NAMES, bump_resources, resource_revisions
from test_new_business_features import advance_order_to_preparing, create_product, create_unit_order, login, make_client


def _revisions():
    with connect() as conn:
        return resource_revisions(conn)


def _free_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
        probe.bind(("127.0.0.1", 0))
        return int(probe.getsockname()[1])


def _start_http_server():
    from app.main import app

    server = uvicorn.Server(
        uvicorn.Config(app, host="127.0.0.1", port=_free_port(), lifespan="off", log_level="error")
    )
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    deadline = time.monotonic() + 5
    while not server.started and time.monotonic() < deadline:
        time.sleep(0.01)
    assert server.started
    return server, thread, f"http://127.0.0.1:{server.config.port}"


def _open_sse(base_url: str, headers: dict[str, str]):
    parsed = urlparse(base_url)
    connection = http.client.HTTPConnection(parsed.hostname, parsed.port, timeout=6)
    connection.request("GET", "/api/v1/admin/realtime/events", headers=headers)
    response = connection.getresponse()
    return connection, response


def _read_sse_event(response) -> tuple[str, dict]:
    event_type = ""
    payload = ""
    deadline = time.monotonic() + 6
    while time.monotonic() < deadline:
        line = response.readline().decode("utf-8").strip()
        if not line:
            if event_type:
                return event_type, json.loads(payload)
            continue
        if line.startswith("event: "):
            event_type = line.removeprefix("event: ")
        elif line.startswith("data: "):
            payload = line.removeprefix("data: ")
    raise AssertionError("timed out waiting for SSE event")


def _wait_for_disconnect_log(caplog):
    deadline = time.monotonic() + 4
    while time.monotonic() < deadline:
        if any("realtime_sse_disconnected" in record.message for record in caplog.records):
            return
        time.sleep(0.05)
    raise AssertionError("SSE stream did not run its disconnect cleanup")


def test_realtime_revisions_are_initialized_and_admin_only(tmp_path):
    client = make_client(tmp_path)
    admin = login(client, "root_admin", "StrongPassword123")
    response = client.get("/api/v1/admin/realtime/revisions", headers=admin)
    assert response.status_code == 200
    assert response.json() == {name: 0 for name in RESOURCE_NAMES}
    assert client.get("/api/v1/admin/realtime/revisions").status_code == 401


def test_realtime_revisions_follow_order_accept_and_fast_complete_transaction(tmp_path):
    client = make_client(tmp_path)
    admin = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, admin)
    created = create_unit_order(client, admin, product["id"], "RT", "2")
    after_create = _revisions()
    assert after_create["orders"] == 1
    assert after_create["dashboard"] == 1
    assert after_create["quota"] == 1

    preparing = advance_order_to_preparing(client, admin, created)
    after_accept = _revisions()
    assert after_accept["orders"] == 2
    assert after_accept["dashboard"] == 2

    completed = client.post(
        f"/api/v1/admin/orders/{created['id']}/complete",
        headers=admin,
        json={"expected_version": preparing["version"], "client_request_id": "realtime-fast-complete"},
    )
    assert completed.status_code == 200, completed.text
    after_complete = _revisions()
    assert after_complete["orders"] == 3
    assert after_complete["outbounds"] == 1
    assert after_complete["batches"] == 1
    assert after_complete["dashboard"] == 3
    assert after_complete["quota"] == 2


def test_realtime_bump_rolls_back_with_business_transaction(tmp_path):
    make_client(tmp_path)
    before = _revisions()
    with pytest.raises(RuntimeError):
        with transaction() as conn:
            bump_resources(conn, "announcements", "dashboard")
            raise RuntimeError("rollback")
    assert _revisions() == before


def test_realtime_revision_api_detects_isolated_business_mutation(tmp_path):
    client = make_client(tmp_path)
    admin = login(client, "root_admin", "StrongPassword123")
    baseline = client.get("/api/v1/admin/realtime/revisions", headers=admin)
    assert baseline.status_code == 200
    before = baseline.json()

    product = create_product(client, admin, code="RT-FALLBACK")
    create_unit_order(client, admin, product["id"], "RT-FALLBACK", "2")

    after = client.get("/api/v1/admin/realtime/revisions", headers=admin)
    assert after.status_code == 200
    revisions = after.json()
    assert revisions["orders"] == before["orders"] + 1
    assert revisions["dashboard"] == before["dashboard"] + 1
    assert revisions["quota"] == before["quota"] + 1


def test_realtime_sse_streams_revision_changes_and_reconnects(tmp_path, caplog):
    caplog.set_level(logging.INFO, logger="app.routers.realtime")
    client = make_client(tmp_path)
    admin = login(client, "root_admin", "StrongPassword123")
    server, thread, base_url = _start_http_server()
    first_connection = second_connection = None
    try:
        first_connection, first = _open_sse(base_url, admin)
        assert first.status == 200
        assert first.getheader("Content-Type").startswith("text/event-stream")
        time.sleep(0.2)
        before = _revisions()["orders"]
        with transaction() as conn:
            bump_resources(conn, "orders")
        event_type, event = _read_sse_event(first)
        assert event_type == "resource_changed"
        assert event == {"resource": "orders", "revision": before + 1}

        first_connection.close()
        _wait_for_disconnect_log(caplog)

        second_connection, second = _open_sse(base_url, admin)
        assert second.status == 200
        assert second.getheader("Content-Type").startswith("text/event-stream")
        time.sleep(0.2)
        before = _revisions()["orders"]
        with transaction() as conn:
            bump_resources(conn, "orders")
        event_type, event = _read_sse_event(second)
        assert event_type == "resource_changed"
        assert event == {"resource": "orders", "revision": before + 1}
    finally:
        if first_connection:
            first_connection.close()
        if second_connection:
            second_connection.close()
        server.should_exit = True
        thread.join(timeout=5)
        assert not thread.is_alive()
