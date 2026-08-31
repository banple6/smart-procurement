from test_new_business_features import advance_order_to_preparing, create_product, create_unit_order
from test_workflows import login, make_client


def _fast_complete(client, admin_headers, order, request_id="fast-complete-1"):
    return client.post(
        f"/api/v1/admin/orders/{order['id']}/complete",
        headers=admin_headers,
        json={"expected_version": order["version"], "client_request_id": request_id},
    )


def test_fast_complete_creates_one_shipped_outbound_and_preserves_order_snapshots(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, admin_headers, code="FAST-SNAPSHOT", name="快完成土豆", unit="斤", price_cents=260)
    order = create_unit_order(client, admin_headers, product["id"], "FAST-A", "3")
    preparing = advance_order_to_preparing(client, admin_headers, order)

    from app.database import connect, one

    with connect() as conn:
        snapshot = one(conn, "SELECT product_name_snapshot, spec_snapshot, unit_snapshot, quantity, price_cents_snapshot FROM order_items WHERE order_id = ?", (order["id"],))
        conn.execute("UPDATE products SET price_cents = 9999 WHERE id = ?", (product["id"],))

    response = _fast_complete(client, admin_headers, preparing, "fast-snapshot-request")
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload["idempotent"] is False
    assert payload["order"]["status"] == "completed"
    assert payload["outbound"]["status"] == "shipped"
    assert payload["outbound"]["order_count"] == 1
    assert payload["outbound"]["orders"][0]["id"] == order["id"]
    line = payload["outbound"]["lines"][0]
    assert line["product_name"] == snapshot["product_name_snapshot"]
    assert line["spec"] == snapshot["spec_snapshot"]
    assert line["unit"] == snapshot["unit_snapshot"]
    assert line["quantity"] == snapshot["quantity"]
    assert line["price_cents_snapshot"] == snapshot["price_cents_snapshot"]

    with connect() as conn:
        assert one(conn, "SELECT COUNT(*) AS count FROM outbound_order_orders WHERE order_id = ?", (order["id"],))["count"] == 1
        audit = one(
            conn,
            "SELECT after_json FROM audit_logs WHERE action = 'ORDER_FAST_COMPLETED' AND object_id = ? AND request_id = ?",
            (order["id"], "fast-snapshot-request"),
        )
        assert audit and "generated_outbound_id" in audit["after_json"]


def test_fast_complete_is_idempotent_and_rejects_stale_order_version(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, admin_headers, code="FAST-IDEMPOTENT", name="快完成萝卜", unit="斤")
    order = create_unit_order(client, admin_headers, product["id"], "FAST-B", "2")
    preparing = advance_order_to_preparing(client, admin_headers, order)

    stale = client.post(
        f"/api/v1/admin/orders/{order['id']}/complete",
        headers=admin_headers,
        json={"expected_version": preparing["version"] - 1, "client_request_id": "fast-stale-request"},
    )
    assert stale.status_code == 409

    first = _fast_complete(client, admin_headers, preparing, "fast-idempotent-request")
    assert first.status_code == 200, first.text
    repeated = _fast_complete(client, admin_headers, preparing, "fast-idempotent-request")
    assert repeated.status_code == 200, repeated.text
    assert repeated.json()["idempotent"] is True
    assert repeated.json()["outbound"]["id"] == first.json()["outbound"]["id"]

    from app.database import connect, one

    with connect() as conn:
        assert one(conn, "SELECT COUNT(*) AS count FROM outbound_order_orders WHERE order_id = ?", (order["id"],))["count"] == 1
        assert one(conn, "SELECT COUNT(*) AS count FROM outbound_orders", ())["count"] == 1


def test_fast_complete_detaches_only_target_from_shared_mixed_unit_open_batch(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, admin_headers, code="FAST-SHARED", name="快完成青菜", unit="斤")
    target = advance_order_to_preparing(client, admin_headers, create_unit_order(client, admin_headers, product["id"], "FAST-C1", "2"))
    same_unit = advance_order_to_preparing(client, admin_headers, create_unit_order(client, admin_headers, product["id"], "FAST-C1B", "4"))
    other_unit = advance_order_to_preparing(client, admin_headers, create_unit_order(client, admin_headers, product["id"], "FAST-C2", "6"))

    # Create two orders for the exact same unit by reusing the target unit account.
    # The public helper creates distinct units, so update only this isolated fixture's unit id.
    from app.database import connect, one

    with connect() as conn:
        target_unit_id = one(conn, "SELECT unit_id FROM orders WHERE id = ?", (target["id"],))["unit_id"]
        conn.execute("UPDATE orders SET unit_id = ?, unit_name_snapshot = (SELECT unit_name FROM units WHERE id = ?) WHERE id = ?", (target_unit_id, target_unit_id, same_unit["id"]))

    batch_response = client.post(
        "/api/v1/admin/batches",
        headers=admin_headers,
        json={"name": "共享混合单位备货单", "note": "隔离测试", "order_ids": [target["id"], same_unit["id"], other_unit["id"]]},
    )
    assert batch_response.status_code == 200, batch_response.text
    source_batch = batch_response.json()

    completed = _fast_complete(client, admin_headers, target, "fast-shared-request")
    assert completed.status_code == 200, completed.text

    with connect() as conn:
        remaining = {row["order_id"] for row in conn.execute("SELECT order_id FROM delivery_batch_orders WHERE batch_id = ?", (source_batch["id"],))}
        assert remaining == {same_unit["id"], other_unit["id"]}
        assert one(conn, "SELECT status FROM delivery_batches WHERE id = ?", (source_batch["id"],))["status"] == "open"
        assert one(conn, "SELECT status FROM orders WHERE id = ?", (same_unit["id"],))["status"] == "preparing"
        assert one(conn, "SELECT status FROM orders WHERE id = ?", (other_unit["id"],))["status"] == "preparing"
        target_batches = [row["batch_id"] for row in conn.execute("SELECT batch_id FROM delivery_batch_orders WHERE order_id = ?", (target["id"],))]
        assert len(target_batches) == 1 and target_batches[0] != source_batch["id"]
        assert one(conn, "SELECT status FROM delivery_batches WHERE id = ?", (target_batches[0],))["status"] == "closed"
        outbound_orders = [row["order_id"] for row in conn.execute("SELECT order_id FROM outbound_order_orders")]
        assert outbound_orders == [target["id"]]


def test_fast_complete_blocks_closed_or_outbound_batch_without_changing_target_order(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, admin_headers, code="FAST-BLOCKED", name="快完成白菜", unit="斤")
    order = advance_order_to_preparing(client, admin_headers, create_unit_order(client, admin_headers, product["id"], "FAST-D", "2"))
    batch = client.post(
        "/api/v1/admin/batches",
        headers=admin_headers,
        json={"name": "已完成备货单", "order_ids": [order["id"]]},
    )
    assert batch.status_code == 200, batch.text
    closed = client.patch(
        f"/api/v1/admin/batches/{batch.json()['id']}/status",
        headers=admin_headers,
        json={"status": "closed", "expected_version": batch.json()["version"]},
    )
    assert closed.status_code == 200, closed.text

    blocked = _fast_complete(client, admin_headers, order, "fast-closed-request")
    assert blocked.status_code == 409
    assert "已完成备货单" in blocked.json()["detail"]

    from app.database import connect, one

    with connect() as conn:
        assert one(conn, "SELECT status FROM orders WHERE id = ?", (order["id"],))["status"] == "preparing"
        assert one(conn, "SELECT COUNT(*) AS count FROM outbound_order_orders WHERE order_id = ?", (order["id"],))["count"] == 0
