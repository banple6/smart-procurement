import os

from fastapi.testclient import TestClient


def make_client(tmp_path):
    os.environ["APP_ENV"] = "test"
    os.environ["APP_SECRET"] = "test-secret"
    os.environ["DATABASE_PATH"] = str(tmp_path / "smart_procurement.db")
    os.environ["UPLOAD_DIR"] = str(tmp_path / "uploads")
    os.environ["PRIVATE_UPLOAD_DIR"] = str(tmp_path / "private_uploads")
    os.environ["INITIAL_ADMIN_USERNAME"] = "root_admin"
    os.environ["INITIAL_ADMIN_PASSWORD"] = "StrongPassword123"

    from app.database import init_db
    from app.main import app, seed_initial_admin

    init_db()
    seed_initial_admin()
    return TestClient(app)


def login(client, username, password):
    response = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['token']}"}


def create_unit_user_product(client, stock_quantity="10"):
    admin_headers = login(client, "root_admin", "StrongPassword123")
    unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "U-PERF", "unit_name": "性能测试单位", "default_delivery_point": "性能测试收货点"},
    )
    assert unit.status_code == 200, unit.text
    unit_id = unit.json()["id"]
    user = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "perf_unit",
            "password": "UnitPassword123",
            "display_name": "性能测试账号",
            "role": "unit_user",
            "unit_id": unit_id,
            "must_change_password": False,
        },
    )
    assert user.status_code == 200, user.text
    product = client.post(
        "/api/v1/admin/products",
        headers=admin_headers,
        json={
            "product_code": "PERF-TOMATO",
            "name": "性能西红柿",
            "category": "蔬菜",
            "spec": "散装",
            "unit": "公斤",
            "price_cents": 450,
            "stock_quantity": stock_quantity,
            "min_order_quantity": "1",
            "quantity_step": "1",
            "warning_quantity": "1",
            "supply_status": "normal",
        },
    )
    assert product.status_code == 200, product.text
    return admin_headers, login(client, "perf_unit", "UnitPassword123"), product.json()["id"]


def test_sqlite_pragmas_and_indexes_are_configured(tmp_path):
    client = make_client(tmp_path)
    assert client.get("/api/v1/health").status_code == 200

    from app.database import connect

    with connect() as conn:
        assert conn.execute("PRAGMA journal_mode").fetchone()[0].lower() == "wal"
        assert conn.execute("PRAGMA synchronous").fetchone()[0] == 1
        assert conn.execute("PRAGMA cache_size").fetchone()[0] == -32000
        assert conn.execute("PRAGMA temp_store").fetchone()[0] == 2
        assert conn.execute("PRAGMA mmap_size").fetchone()[0] >= 268435456
        assert conn.execute("PRAGMA busy_timeout").fetchone()[0] == 5000
        indexes = {row["name"] for row in conn.execute("SELECT name FROM sqlite_master WHERE type = 'index'")}

    assert "idx_orders_unit_created" in indexes
    assert "idx_orders_status_created" in indexes
    assert "idx_products_active_category_updated" in indexes
    assert "idx_order_items_order" in indexes
    assert "idx_sessions_token_hash" in indexes
    assert "idx_orders_created_by_idempotency_key" in indexes


def test_order_idempotency_key_header_returns_same_order_without_double_reserve(tmp_path):
    client = make_client(tmp_path)
    _, unit_headers, product_id = create_unit_user_product(client, stock_quantity="10")
    headers = {**unit_headers, "Idempotency-Key": "idem-key-001"}
    payload = {"items": [{"product_id": product_id, "quantity": "2"}], "note": "幂等测试"}

    first = client.post("/api/v1/orders", headers=headers, json=payload)
    second = client.post("/api/v1/orders", headers=headers, json=payload)

    assert first.status_code == 200, first.text
    assert second.status_code == 200, second.text
    assert first.json()["id"] == second.json()["id"]
    product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert product["reserved_quantity"] == "2"


def test_atomic_reserve_rejects_when_available_stock_is_exhausted(tmp_path):
    client = make_client(tmp_path)
    _, unit_headers, product_id = create_unit_user_product(client, stock_quantity="3")

    first = client.post(
        "/api/v1/orders",
        headers={**unit_headers, "Idempotency-Key": "stock-ok"},
        json={"items": [{"product_id": product_id, "quantity": "2"}]},
    )
    second = client.post(
        "/api/v1/orders",
        headers={**unit_headers, "Idempotency-Key": "stock-fail"},
        json={"items": [{"product_id": product_id, "quantity": "2"}]},
    )

    assert first.status_code == 200, first.text
    assert second.status_code == 409
    assert second.json()["detail"] == "库存不足，请减少数量"
    product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert product["reserved_quantity"] == "2"


def test_products_etag_returns_304_until_product_changes(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, product_id = create_unit_user_product(client, stock_quantity="10")

    first = client.get("/api/v1/products", headers=unit_headers)
    assert first.status_code == 200
    etag = first.headers.get("etag")
    assert etag

    cached = client.get("/api/v1/products", headers={**unit_headers, "If-None-Match": etag})
    assert cached.status_code == 304
    assert not cached.content

    changed = client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=admin_headers,
        json={"price_cents": 500},
    )
    assert changed.status_code == 200, changed.text
    fresh = client.get("/api/v1/products", headers={**unit_headers, "If-None-Match": etag})
    assert fresh.status_code == 200
    assert fresh.headers.get("etag") != etag


def test_dashboard_cache_is_invalidated_after_order_creation(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, product_id = create_unit_user_product(client, stock_quantity="10")

    before = client.get("/api/v1/admin/dashboard", headers=admin_headers)
    assert before.status_code == 200, before.text
    assert before.json()["pending"] == 0

    created = client.post(
        "/api/v1/orders",
        headers={**unit_headers, "Idempotency-Key": "dashboard-cache-1"},
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert created.status_code == 200, created.text

    after = client.get("/api/v1/admin/dashboard", headers=admin_headers)
    assert after.status_code == 200, after.text
    assert after.json()["pending"] == 1
