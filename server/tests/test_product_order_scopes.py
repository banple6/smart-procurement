from test_new_business_features import create_product, login, make_client
from test_workflows import create_unit_user_product_order, set_web_session


def _second_unit_user(client, admin_headers):
    unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "002", "unit_name": "第二单位", "default_delivery_point": "第二收货点"},
    )
    assert unit.status_code == 200, unit.text
    user = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "unit002",
            "password": "UnitPassword123",
            "display_name": "第二单位账号",
            "role": "unit_user",
            "unit_id": unit.json()["id"],
            "must_change_password": False,
        },
    )
    assert user.status_code == 200, user.text
    return unit.json(), user.json(), login(client, "unit002", "UnitPassword123")


def _set_selected_scope(client, admin_headers, product, unit_ids):
    response = client.put(
        f"/api/v1/admin/products/{product['id']}/order-scope",
        headers=admin_headers,
        json={"mode": "selected", "unit_ids": unit_ids, "expected_version": product["version"]},
    )
    assert response.status_code == 200, response.text
    return response.json()


def test_scope_migration_defaults_existing_products_to_all_without_touching_history(tmp_path):
    client = make_client(tmp_path)
    headers = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, headers, code="SCOPE-DEFAULT", name="默认范围食材")

    from app.database import connect, migration_status

    assert migration_status()["applied"][-1] == "0028_product_order_scopes"
    with connect() as conn:
        row = conn.execute("SELECT order_scope_mode, version FROM products WHERE id = ?", (product["id"],)).fetchone()
        assert row["order_scope_mode"] == "all"
        assert row["version"] == product["version"]
        assert conn.execute("SELECT COUNT(*) AS count FROM product_order_scope_units").fetchone()["count"] == 0


def test_scope_migration_upgrades_pre_0028_product_rows_without_rewriting_them(tmp_path):
    import sqlite3

    from app.database import apply_product_order_scopes_migration, ensure_core_schema

    conn = sqlite3.connect(tmp_path / "legacy.db")
    conn.row_factory = sqlite3.Row
    ensure_core_schema(conn)
    conn.execute(
        """
        INSERT INTO products(
          id, product_code, name, category, spec, unit, price_cents, stock_quantity,
          reserved_quantity, min_order_quantity, quantity_step, warning_quantity, created_by
        ) VALUES ('legacy-product', 'LEGACY-SCOPE', '历史目录食材', '蔬菜', '散装', '斤', 250, '10', '0', '1', '1', '0', 'admin')
        """
    )
    apply_product_order_scopes_migration(conn)
    row = conn.execute("SELECT name, category, order_scope_mode FROM products WHERE id = 'legacy-product'").fetchone()
    assert dict(row) == {"name": "历史目录食材", "category": "蔬菜", "order_scope_mode": "all"}
    assert conn.execute("SELECT COUNT(*) FROM product_order_scope_units").fetchone()[0] == 0
    conn.close()


def test_scope_filters_unit_catalog_detail_and_web_portal_but_not_admin(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_a_headers, unit_a_id, _ = create_unit_user_product_order(client)
    unit_b, unit_b_user, unit_b_headers = _second_unit_user(client, admin_headers)
    product = create_product(client, admin_headers, code="SCOPE-PRIVATE", name="指定单位食材")
    scope = _set_selected_scope(client, admin_headers, product, [unit_a_id])

    assert scope["mode"] == "selected"
    assert scope["unit_ids"] == [unit_a_id]
    assert client.get("/api/v1/admin/products", headers=admin_headers).status_code == 200
    assert product["id"] in {row["id"] for row in client.get("/api/v1/admin/products", headers=admin_headers).json()}
    assert product["id"] in {row["id"] for row in client.get("/api/v1/products?q=指定", headers=unit_a_headers).json()}
    assert product["id"] not in {row["id"] for row in client.get("/api/v1/products?q=指定", headers=unit_b_headers).json()}
    assert client.get(f"/api/v1/products/{product['id']}", headers=unit_a_headers).status_code == 200
    assert client.get(f"/api/v1/products/{product['id']}", headers=unit_b_headers).status_code == 404

    web_headers = set_web_session(client, unit_b_user["id"], role="unit_user", unit_id=unit_b["id"])
    response = client.get("/unit/products/data?q=指定", headers=web_headers)
    assert response.status_code == 200, response.text
    assert product["id"] not in {row["id"] for row in response.json()["items"]}


def test_scope_rejects_tampered_order_and_revoked_web_cart_without_partial_effects(tmp_path):
    client = make_client(tmp_path)
    admin_headers, _, unit_a_id, _ = create_unit_user_product_order(client)
    unit_b, unit_b_user, unit_b_headers = _second_unit_user(client, admin_headers)
    product = create_product(client, admin_headers, code="SCOPE-ENFORCED", name="仅第一单位")
    _set_selected_scope(client, admin_headers, product, [unit_a_id])

    rejected = client.post("/api/v1/orders", headers=unit_b_headers, json={"items": [{"product_id": product["id"], "quantity": "1"}]})
    assert rejected.status_code == 409
    assert "下单范围" in rejected.json()["detail"]

    from app.database import connect

    with connect() as conn:
        assert conn.execute("SELECT COUNT(*) AS count FROM orders").fetchone()["count"] == 0
        row = conn.execute("SELECT reserved_quantity FROM products WHERE id = ?", (product["id"],)).fetchone()
        assert row["reserved_quantity"] == "0"

    web_headers = set_web_session(client, unit_b_user["id"], role="unit_user", unit_id=unit_b["id"])
    blocked_cart = client.post("/api/v1/unit/cart/items", headers=web_headers, json={"product_id": product["id"], "quantity": "1"})
    assert blocked_cart.status_code == 404


def test_scope_admin_contract_validation_occ_and_audit(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit_id, product_id = create_unit_user_product_order(client)
    product = client.get("/api/v1/admin/products", headers=admin_headers).json()[0]
    assert client.get(f"/api/v1/admin/products/{product_id}/order-scope", headers=unit_headers).status_code == 403
    assert client.put(
        f"/api/v1/admin/products/{product_id}/order-scope",
        headers=admin_headers,
        json={"mode": "selected", "unit_ids": [], "expected_version": product["version"]},
    ).status_code == 400
    updated = _set_selected_scope(client, admin_headers, product, [unit_id])
    assert updated["version"] == product["version"] + 1
    assert client.put(
        f"/api/v1/admin/products/{product_id}/order-scope",
        headers=admin_headers,
        json={"mode": "all", "unit_ids": [], "expected_version": product["version"]},
    ).status_code == 409
    with __import__("app.database", fromlist=["connect"]).connect() as conn:
        audit = conn.execute("SELECT before_json, after_json FROM audit_logs WHERE action = 'PRODUCT_ORDER_SCOPE_UPDATED'").fetchone()
        assert audit is not None
        assert '"selected"' in audit["after_json"]
