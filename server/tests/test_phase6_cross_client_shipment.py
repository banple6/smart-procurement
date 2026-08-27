from io import BytesIO
from uuid import uuid4

from PIL import Image

from test_phase4_outbounds import _generate, _setup_closed_batch
from test_workflows import login, make_client


def _png_bytes() -> bytes:
    stream = BytesIO()
    Image.new("RGB", (64, 48), "purple").save(stream, format="PNG")
    return stream.getvalue()


def _legacy_ship(client, admin_headers, order_id: str, request_id: str):
    response = client.post(
        f"/api/v1/admin/orders/{order_id}/ship",
        headers=admin_headers,
        data={"note": "Phase6 旧客户端发货", "client_request_id": request_id},
        files=[("photos", ("legacy-proof.png", _png_bytes(), "image/png"))],
    )
    assert response.status_code == 200, response.text
    return response.json()


def _unit_outbound(generated):
    return next(item for item in generated["items"] if item["order_count"] == 2)


def test_phase6_legacy_shipping_reconciles_outbound_only_after_last_order(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, unit_headers, products = _setup_closed_batch(client, admin_headers)
    outbound = _unit_outbound(_generate(client, admin_headers, batch["id"]))
    before = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()

    _legacy_ship(client, admin_headers, orders[0]["id"], "phase6-legacy-one")
    partial = client.get(f"/api/v1/admin/outbounds/{outbound['id']}", headers=admin_headers).json()
    assert partial["status"] == "pending"
    assert {row["id"]: row["status"] for row in partial["orders"]} == {
        orders[0]["id"]: "shipped",
        orders[1]["id"]: "preparing",
    }

    received = client.post(f"/api/v1/orders/{orders[0]['id']}/confirm-receipt", headers=unit_headers[0])
    assert received.status_code == 200, received.text
    assert received.json()["status"] == "completed"

    _legacy_ship(client, admin_headers, orders[1]["id"], "phase6-legacy-two")
    reconciled = client.get(f"/api/v1/admin/outbounds/{outbound['id']}", headers=admin_headers).json()
    assert reconciled["status"] == "shipped"
    assert {row["status"] for row in reconciled["orders"]} == {"completed", "shipped"}
    assert reconciled["version"] == 2

    retry = _legacy_ship(client, admin_headers, orders[1]["id"], "phase6-legacy-two")
    assert retry["shipping_photo_count"] == 1
    after = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()
    assert (after["stock_quantity"], after["reserved_quantity"]) == ("997", "13")

    from app.database import connect

    with connect() as conn:
        assert conn.execute(
            "SELECT COUNT(*) FROM audit_logs WHERE object_id = ? AND action = 'OUTBOUND_ORDER_RECONCILED_SHIPPED'",
            (outbound["id"],),
        ).fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM order_shipping_photos WHERE order_id = ?",
            (orders[1]["id"],),
        ).fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM inventory_logs WHERE order_id = ? AND action = 'order_complete'",
            (orders[1]["id"],),
        ).fetchone()[0] == 0


def test_phase6_web_outbound_finishes_only_remaining_legacy_orders(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, _, products = _setup_closed_batch(client, admin_headers)
    outbound = _unit_outbound(_generate(client, admin_headers, batch["id"]))
    before = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()

    _legacy_ship(client, admin_headers, orders[0]["id"], "phase6-mixed-legacy")
    shipped = client.post(
        f"/api/v1/admin/outbounds/{outbound['id']}/ship",
        headers=admin_headers,
        data={"note": "Phase6 Web 补发", "client_request_id": str(uuid4())},
        files=[("photos", ("web-proof.png", _png_bytes(), "image/png"))],
    )
    assert shipped.status_code == 200, shipped.text
    body = shipped.json()
    assert body["status"] == "shipped"
    assert {row["status"] for row in body["orders"]} == {"shipped"}

    after = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()
    assert (after["stock_quantity"], after["reserved_quantity"]) == (before["stock_quantity"], before["reserved_quantity"])

    from app.database import connect

    with connect() as conn:
        assert conn.execute(
            "SELECT COUNT(*) FROM order_shipping_photos WHERE order_id = ?",
            (orders[0]["id"],),
        ).fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM order_shipping_photos WHERE order_id = ?",
            (orders[1]["id"],),
        ).fetchone()[0] == 1
        assert conn.execute(
            "SELECT COUNT(*) FROM audit_logs WHERE object_id = ? AND action = 'OUTBOUND_ORDER_RECONCILED_SHIPPED'",
            (outbound["id"],),
        ).fetchone()[0] == 1


def test_phase6_single_order_legacy_shipping_marks_its_outbound_shipped(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, _, _, _ = _setup_closed_batch(client, admin_headers)
    generated = _generate(client, admin_headers, batch["id"])
    outbound = next(item for item in generated["items"] if item["order_count"] == 1)
    only_order_id = client.get(
        f"/api/v1/admin/outbounds/{outbound['id']}", headers=admin_headers
    ).json()["orders"][0]["id"]

    _legacy_ship(client, admin_headers, only_order_id, "phase6-single-legacy")
    result = client.get(f"/api/v1/admin/outbounds/{outbound['id']}", headers=admin_headers).json()
    assert result["status"] == "shipped"
    assert result["version"] == 2


def test_phase6_reconciles_historical_pending_outbound_after_all_legacy_orders_shipped(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, _, _ = _setup_closed_batch(client, admin_headers)
    outbound = _unit_outbound(_generate(client, admin_headers, batch["id"]))
    _legacy_ship(client, admin_headers, orders[0]["id"], "phase6-historical-one")
    _legacy_ship(client, admin_headers, orders[1]["id"], "phase6-historical-two")

    # Simulate an outbound created before this reconciliation logic existed.
    from app.database import connect

    with connect() as conn:
        conn.execute(
            "UPDATE outbound_orders SET status = 'pending', ship_request_id = NULL WHERE id = ?",
            (outbound["id"],),
        )
        conn.commit()

    reconciled = client.post(
        f"/api/v1/admin/outbounds/{outbound['id']}/ship",
        headers=admin_headers,
        data={"note": "历史出库单状态同步", "client_request_id": str(uuid4())},
    )
    assert reconciled.status_code == 200, reconciled.text
    assert reconciled.json()["status"] == "shipped"
    assert all(order["shipping_photo_count"] == 1 for order in reconciled.json()["orders"])


def test_phase6_outbound_rejects_invalid_linked_order_state(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, _, _ = _setup_closed_batch(client, admin_headers)
    outbound = _unit_outbound(_generate(client, admin_headers, batch["id"]))

    from app.database import connect

    with connect() as conn:
        conn.execute("UPDATE orders SET status = 'cancelled' WHERE id = ?", (orders[0]["id"],))
        conn.commit()

    response = client.post(
        f"/api/v1/admin/outbounds/{outbound['id']}/ship",
        headers=admin_headers,
        data={"note": "不应发货", "client_request_id": str(uuid4())},
        files=[("photos", ("invalid.png", _png_bytes(), "image/png"))],
    )
    assert response.status_code == 409
    assert client.get(f"/api/v1/admin/outbounds/{outbound['id']}", headers=admin_headers).json()["status"] == "pending"
