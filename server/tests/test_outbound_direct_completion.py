from test_phase4_outbounds import _generate, _setup_closed_batch
from test_phase6_cross_client_shipment import _legacy_ship, _unit_outbound
from test_unit_quota import order_payload, setup_quota
from test_workflows import advance_to_preparing, login, make_client


def _direct_complete(client, admin_headers, outbound, request_id="outbound-direct-complete-1"):
    return client.post(
        f"/api/v1/admin/outbounds/{outbound['id']}/complete",
        headers=admin_headers,
        json={"expected_version": outbound["version"], "client_request_id": request_id},
    )


def test_direct_complete_legacy_pending_outbound_is_idempotent_and_needs_no_photo(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, _, _, _ = _setup_closed_batch(client, admin_headers)
    generated = _generate(client, admin_headers, batch["id"])
    outbound = next(item for item in generated["items"] if item["order_count"] == 1)

    stale = client.post(
        f"/api/v1/admin/outbounds/{outbound['id']}/complete",
        headers=admin_headers,
        json={"expected_version": outbound["version"] + 1, "client_request_id": "outbound-direct-stale"},
    )
    assert stale.status_code == 409

    completed = _direct_complete(client, admin_headers, outbound, "outbound-direct-idempotent")
    assert completed.status_code == 200, completed.text
    body = completed.json()
    assert body["status"] == "shipped"
    assert {order["status"] for order in body["orders"]} == {"completed"}
    assert body["orders"][0]["shipping_photo_count"] == 0

    repeated = _direct_complete(client, admin_headers, outbound, "outbound-direct-idempotent")
    assert repeated.status_code == 200, repeated.text
    assert repeated.json()["status"] == "shipped"

    from app.database import connect

    with connect() as conn:
        assert conn.execute(
            "SELECT COUNT(*) FROM audit_logs WHERE object_id = ? AND action = 'OUTBOUND_ORDER_DIRECT_COMPLETED'",
            (outbound["id"],),
        ).fetchone()[0] == 1
        order_id = body["orders"][0]["id"]
        assert conn.execute("SELECT COUNT(*) FROM order_shipping_photos WHERE order_id = ?", (order_id,)).fetchone()[0] == 0
        assert conn.execute("SELECT COUNT(*) FROM inventory_logs WHERE order_id = ? AND action = 'order_complete'", (order_id,)).fetchone()[0] == 1


def test_direct_complete_preserves_existing_photos_and_does_not_touch_other_units(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, _, _ = _setup_closed_batch(client, admin_headers)
    generated = _generate(client, admin_headers, batch["id"])
    target = _unit_outbound(generated)
    unrelated = next(item for item in generated["items"] if item["id"] != target["id"])

    _legacy_ship(client, admin_headers, orders[0]["id"], "legacy-photo-to-preserve")
    target = client.get(f"/api/v1/admin/outbounds/{target['id']}", headers=admin_headers).json()
    completed = _direct_complete(client, admin_headers, target, "outbound-direct-preserve-photo")
    assert completed.status_code == 200, completed.text
    body = completed.json()
    assert body["status"] == "shipped"
    assert {order["status"] for order in body["orders"]} == {"completed"}
    assert {order["shipping_photo_count"] for order in body["orders"]} == {0, 1}

    unaffected = client.get(f"/api/v1/admin/outbounds/{unrelated['id']}", headers=admin_headers).json()
    assert unaffected["status"] == "pending"
    assert {order["status"] for order in unaffected["orders"]} == {"preparing"}

    from app.database import connect

    with connect() as conn:
        assert conn.execute(
            "SELECT COUNT(*) FROM order_shipping_photos WHERE order_id = ?", (orders[0]["id"],)
        ).fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM order_shipping_photos WHERE order_id = ?", (orders[1]["id"],)
        ).fetchone()[0] == 0


def test_direct_complete_finalizes_reserved_quota_once(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit, product = setup_quota(client, cents=10_000)
    order = client.post("/api/v1/orders", headers=unit_headers, json=order_payload(product["id"], "2", "outbound-direct-quota")).json()
    preparing = advance_to_preparing(client, admin_headers, order["id"])
    batch = client.post(
        "/api/v1/admin/batches",
        headers=admin_headers,
        json={"name": "额度直接完成", "order_ids": [preparing["id"]]},
    ).json()
    closed = client.patch(
        f"/api/v1/admin/batches/{batch['id']}/status",
        headers=admin_headers,
        json={"status": "closed", "expected_version": batch["version"]},
    )
    assert closed.status_code == 200, closed.text
    outbound = _generate(client, admin_headers, batch["id"])["items"][0]

    first = _direct_complete(client, admin_headers, outbound, "outbound-direct-quota")
    assert first.status_code == 200, first.text
    second = _direct_complete(client, admin_headers, outbound, "outbound-direct-quota")
    assert second.status_code == 200, second.text

    from app.database import connect

    with connect() as conn:
        allocation = conn.execute("SELECT status FROM order_quota_allocations WHERE order_id = ?", (order["id"],)).fetchone()
        assert allocation["status"] == "finalized"
        assert conn.execute(
            "SELECT COUNT(*) FROM unit_quota_ledger WHERE order_id = ? AND event_type = 'ORDER_FINALIZED'", (order["id"],)
        ).fetchone()[0] == 1
