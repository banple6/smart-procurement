import os
import sqlite3
import base64
from pathlib import Path

from fastapi.testclient import TestClient


def make_client(tmp_path):
    os.environ["APP_ENV"] = "test"
    os.environ["APP_SECRET"] = "test-secret"
    os.environ["DATABASE_PATH"] = str(tmp_path / "smart_procurement.db")
    os.environ["UPLOAD_DIR"] = str(tmp_path / "uploads")
    os.environ["INITIAL_ADMIN_USERNAME"] = "root_admin"
    os.environ["INITIAL_ADMIN_PASSWORD"] = "StrongPassword123"

    from app.main import app, seed_initial_admin
    from app.database import init_db

    init_db()
    seed_initial_admin()
    return TestClient(app)


def login(client, username, password):
    response = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    assert response.status_code == 200, response.text
    token = response.json()["token"]
    return {"Authorization": f"Bearer {token}"}


def create_unit_user_product_order(client):
    admin_headers = login(client, "root_admin", "StrongPassword123")
    unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={
            "unit_code": "U001",
            "unit_name": "第一食堂",
            "default_delivery_point": "第一食堂收货点",
        },
    )
    assert unit.status_code == 200, unit.text
    unit_id = unit.json()["id"]

    user = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "unit001",
            "password": "UnitPassword123",
            "display_name": "第一食堂账号",
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
            "product_code": "VEG-TOMATO",
            "name": "西红柿",
            "category": "蔬菜",
            "spec": "普通大红款",
            "unit": "公斤",
            "price_cents": 450,
            "stock_quantity": "10",
            "min_order_quantity": "1",
            "quantity_step": "0.5",
            "warning_quantity": "2",
            "supply_status": "normal",
        },
    )
    assert product.status_code == 200, product.text
    return admin_headers, login(client, "unit001", "UnitPassword123"), unit_id, product.json()["id"]


def test_auth_and_disabled_account(tmp_path):
    client = make_client(tmp_path)
    assert client.post("/api/v1/auth/login", json={"username": "root_admin", "password": "bad"}).status_code == 401
    admin_headers = login(client, "root_admin", "StrongPassword123")
    unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "U002", "unit_name": "第二食堂", "default_delivery_point": "二号点"},
    ).json()
    user = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "disabled",
            "password": "Disabled123",
            "display_name": "停用账号",
            "role": "unit_user",
            "unit_id": unit["id"],
        },
    ).json()
    stopped = client.patch(f"/api/v1/admin/users/{user['id']}/status", headers=admin_headers, json={"active": False})
    assert stopped.status_code == 200
    assert client.post("/api/v1/auth/login", json={"username": "disabled", "password": "Disabled123"}).status_code == 403


def test_role_permissions_and_unit_order_isolation(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit_id, product_id = create_unit_user_product_order(client)
    forbidden = client.post(
        "/api/v1/admin/products",
        headers=unit_headers,
        json={"product_code": "X", "name": "X", "category": "其他", "spec": "x", "unit": "个", "price_cents": 1, "stock_quantity": "1"},
    )
    assert forbidden.status_code == 403

    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"note": "上午送达", "items": [{"product_id": product_id, "quantity": "2"}]},
    )
    assert order.status_code == 200, order.text
    assert order.json()["unit_id"] == unit_id
    assert order.json()["total_cents"] == 900
    assert client.get("/api/v1/orders", headers=unit_headers).json()["total"] == 1
    assert client.get("/api/v1/admin/orders", headers=admin_headers).json()["total"] == 1


def test_inventory_reservation_price_snapshot_and_status_flow(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "3"}]},
    ).json()
    product_after_order = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert product_after_order["reserved_quantity"] == "3"
    assert product_after_order["available_quantity"] == "7"

    price_update = client.put(
        f"/api/v1/admin/products/{product_id}",
        headers=admin_headers,
        json={"price_cents": 600, "stock_quantity": "10"},
    )
    assert price_update.status_code == 200
    detail = client.get(f"/api/v1/orders/{order['id']}", headers=unit_headers).json()
    assert detail["items"][0]["price_cents_snapshot"] == 450
    assert detail["items"][0]["subtotal_cents"] == 1350

    too_many = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "99"}]},
    )
    assert too_many.status_code == 409

    assert client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "accepted"}).status_code == 200
    cannot_edit = client.put(
        f"/api/v1/orders/{order['id']}",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert cannot_edit.status_code == 409
    assert client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "preparing"}).status_code == 200
    assert client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "shipped"}).status_code == 200
    assert client.post(f"/api/v1/orders/{order['id']}/confirm-receipt", headers=unit_headers).status_code == 200
    completed_product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert completed_product["stock_quantity"] == "7"
    assert completed_product["reserved_quantity"] == "0"


def test_cancel_releases_inventory_summary_and_excel(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "2"}]},
    ).json()
    assert client.post(f"/api/v1/orders/{order['id']}/cancel", headers=unit_headers).status_code == 200
    product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert product["reserved_quantity"] == "0"
    dashboard = client.get("/api/v1/admin/dashboard", headers=admin_headers)
    assert dashboard.status_code == 200
    summary = client.get("/api/v1/admin/order-summary", headers=admin_headers)
    assert summary.status_code == 200
    ledger = client.get("/api/v1/admin/ledger", headers=admin_headers)
    assert ledger.status_code == 200
    export = client.get("/api/v1/admin/ledger/export.xlsx", headers=admin_headers)
    assert export.status_code == 200
    assert export.headers["content-type"].startswith("application/vnd.openxmlformats-officedocument")


def test_image_upload_validates_file_type(tmp_path):
    client = make_client(tmp_path)
    admin_headers, _, _, product_id = create_unit_user_product_order(client)
    bad = client.post(
        f"/api/v1/admin/products/{product_id}/image",
        headers=admin_headers,
        files={"file": ("bad.txt", b"not-image", "text/plain")},
    )
    assert bad.status_code == 400
    png = base64.b64decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/p9sAAAAASUVORK5CYII="
    )
    ok = client.post(
        f"/api/v1/admin/products/{product_id}/image",
        headers=admin_headers,
        files={"file": ("image.png", png, "image/png")},
    )
    assert ok.status_code == 200
    assert ok.json()["image_path"].startswith("/uploads/")


def test_cli_resets_admin_password_without_printing_secret(tmp_path, monkeypatch, capsys):
    client = make_client(tmp_path)
    assert client.post("/api/v1/auth/login", json={"username": "root_admin", "password": "StrongPassword123"}).status_code == 200

    from app.cli import reset_admin_password

    monkeypatch.setenv("NEW_ADMIN_PASSWORD", "NewStrongPassword456")
    assert reset_admin_password("root_admin") is True

    captured = capsys.readouterr()
    output = captured.out + captured.err
    assert "NewStrongPassword456" not in output
    assert client.post("/api/v1/auth/login", json={"username": "root_admin", "password": "StrongPassword123"}).status_code == 401
    assert client.post("/api/v1/auth/login", json={"username": "root_admin", "password": "NewStrongPassword456"}).status_code == 200


def test_sessions_are_revoked_on_logout_password_reset_and_user_disable(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, _ = create_unit_user_product_order(client)

    assert client.get("/api/v1/auth/me", headers=unit_headers).status_code == 200
    logout = client.post("/api/v1/auth/logout", headers=unit_headers)
    assert logout.status_code == 200
    assert client.get("/api/v1/auth/me", headers=unit_headers).status_code == 401

    second_login = client.post("/api/v1/auth/login", json={"username": "unit001", "password": "UnitPassword123"})
    assert second_login.status_code == 200
    second_headers = {"Authorization": f"Bearer {second_login.json()['token']}"}
    users = client.get("/api/v1/admin/users", headers=admin_headers).json()
    unit_user = next(user for user in users if user["username"] == "unit001")
    reset = client.post(
        f"/api/v1/admin/users/{unit_user['id']}/reset-password",
        headers=admin_headers,
        json={"new_password": "ChangedPassword123", "must_change_password": True},
    )
    assert reset.status_code == 200
    assert client.get("/api/v1/auth/me", headers=second_headers).status_code == 401

    third_login = client.post("/api/v1/auth/login", json={"username": "unit001", "password": "ChangedPassword123"})
    assert third_login.status_code == 200
    third_headers = {"Authorization": f"Bearer {third_login.json()['token']}"}
    stopped = client.patch(f"/api/v1/admin/users/{unit_user['id']}/status", headers=admin_headers, json={"active": False})
    assert stopped.status_code == 200
    assert client.get("/api/v1/auth/me", headers=third_headers).status_code == 401


def test_unit_disable_blocks_login_and_order_creation_but_keeps_history(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit_id, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"client_request_id": "REQ-unit-disable-1", "items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert order.status_code == 200

    disabled = client.patch(f"/api/v1/admin/units/{unit_id}/status", headers=admin_headers, json={"active": False})
    assert disabled.status_code == 200
    assert client.post("/api/v1/auth/login", json={"username": "unit001", "password": "UnitPassword123"}).status_code == 403
    assert client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"client_request_id": "REQ-unit-disable-2", "items": [{"product_id": product_id, "quantity": "1"}]},
    ).status_code == 401
    history = client.get("/api/v1/admin/orders?unit_id=" + unit_id + "&include_items=true", headers=admin_headers)
    assert history.status_code == 200
    assert len(history.json()["items"]) == 1
    assert len(history.json()["items"][0]["items"]) == 1


def test_order_list_include_items_and_client_request_id_idempotency(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    payload = {"client_request_id": "REQ-idempotent-1", "items": [{"product_id": product_id, "quantity": "2"}]}
    first = client.post("/api/v1/orders", headers=unit_headers, json=payload)
    assert first.status_code == 200, first.text
    second = client.post("/api/v1/orders", headers=unit_headers, json=payload)
    assert second.status_code == 200, second.text
    assert first.json()["id"] == second.json()["id"]
    assert first.json()["order_no"].startswith("SP")
    assert "-" in first.json()["order_no"]

    product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert product["reserved_quantity"] == "2"
    unit_list = client.get("/api/v1/orders?include_items=true", headers=unit_headers)
    assert unit_list.status_code == 200
    assert unit_list.json()["total"] == 1
    assert len(unit_list.json()["items"][0]["items"]) == 1
    admin_list = client.get("/api/v1/admin/orders?include_items=true&status=pending&page=1&page_size=5", headers=admin_headers)
    assert admin_list.status_code == 200
    assert admin_list.json()["total"] == 1
    assert admin_list.json()["items"][0]["client_request_id"] == "REQ-idempotent-1"


def test_migration_status_and_readiness(tmp_path):
    client = make_client(tmp_path)
    ready = client.get("/api/v1/health/ready")
    assert ready.status_code == 200
    assert ready.json()["status"] == "ready"

    from app.cli import migration_status, migrate
    from app.database import connect

    status = migration_status()
    assert status["pending"] == []
    assert status["applied"]
    migrate()
    with connect() as conn:
        row = conn.execute("SELECT COUNT(*) AS c FROM schema_migrations").fetchone()
    assert row["c"] >= 1


def test_existing_database_is_migrated_without_dropping_data(tmp_path, monkeypatch):
    db_path = tmp_path / "legacy.db"
    monkeypatch.setenv("DATABASE_PATH", str(db_path))
    monkeypatch.setenv("UPLOAD_DIR", str(tmp_path / "uploads"))
    with sqlite3.connect(db_path) as conn:
        conn.execute(
            "CREATE TABLE units(id TEXT PRIMARY KEY, unit_code TEXT UNIQUE, unit_name TEXT, default_delivery_point TEXT, active INTEGER DEFAULT 1, created_at TEXT DEFAULT CURRENT_TIMESTAMP, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)"
        )
        conn.execute(
            "CREATE TABLE users(id TEXT PRIMARY KEY, username TEXT UNIQUE, password_hash TEXT, display_name TEXT, role TEXT, unit_id TEXT, active INTEGER DEFAULT 1, must_change_password INTEGER DEFAULT 0, created_at TEXT DEFAULT CURRENT_TIMESTAMP, updated_at TEXT DEFAULT CURRENT_TIMESTAMP)"
        )
        conn.execute("INSERT INTO units(id, unit_code, unit_name, default_delivery_point) VALUES ('u1', 'LEGACY', '历史单位', '历史配送点')")
        conn.commit()

    from app.database import init_db

    init_db()
    with sqlite3.connect(db_path) as conn:
        unit = conn.execute("SELECT unit_name, address_note FROM units WHERE id = 'u1'").fetchone()
        sessions = conn.execute("SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'sessions'").fetchone()
    assert unit == ("历史单位", "")
    assert sessions is not None
