import os
import sqlite3
import base64
from io import BytesIO
from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient
from PIL import Image


def make_client(tmp_path):
    os.environ["APP_ENV"] = "test"
    os.environ["APP_SECRET"] = "test-secret"
    os.environ["DATABASE_PATH"] = str(tmp_path / "smart_procurement.db")
    os.environ["UPLOAD_DIR"] = str(tmp_path / "uploads")
    os.environ["PRIVATE_UPLOAD_DIR"] = str(tmp_path / "private_uploads")
    os.environ["INITIAL_ADMIN_USERNAME"] = "root_admin"
    os.environ["INITIAL_ADMIN_PASSWORD"] = "StrongPassword123"

    from app.main import app, seed_initial_admin
    from app.database import connect, init_db

    init_db()
    seed_initial_admin()
    with connect() as conn:
        conn.execute("UPDATE procurement_settings SET cutoff_enabled = 0 WHERE id = 1")
        conn.commit()
    return TestClient(app)


def login(client, username, password):
    response = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    assert response.status_code == 200, response.text
    token = response.json()["token"]
    return {"Authorization": f"Bearer {token}"}


def test_health_allows_head_for_deployment_checks(tmp_path):
    client = make_client(tmp_path)
    assert client.get("/api/v1/health").status_code == 200
    assert client.head("/api/v1/health").status_code == 200
    assert client.get("/api/v1/health/ready").status_code == 200
    assert client.head("/api/v1/health/ready").status_code == 200


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


def sample_image_bytes(size=(32, 24), color=(180, 40, 30)) -> bytes:
    image = Image.new("RGB", size, color)
    out = BytesIO()
    image.save(out, format="JPEG")
    return out.getvalue()


def advance_to_preparing(client, admin_headers, order_id):
    accepted = client.patch(f"/api/v1/admin/orders/{order_id}/status", headers=admin_headers, json={"status": "accepted"})
    assert accepted.status_code == 200, accepted.text
    preparing = client.patch(f"/api/v1/admin/orders/{order_id}/status", headers=admin_headers, json={"status": "preparing"})
    assert preparing.status_code == 200, preparing.text
    return preparing.json()


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


def test_login_ignores_client_role_and_unit_and_locks_after_failures(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "LOCK", "unit_name": "锁定测试单位", "default_delivery_point": "锁定测试收货点"},
    ).json()
    created = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "lock_user",
            "password": "LockPassword123",
            "display_name": "锁定测试账号",
            "role": "unit_user",
            "unit_id": unit["id"],
            "must_change_password": False,
        },
    )
    assert created.status_code == 200, created.text

    login_with_spoofed_fields = client.post(
        "/api/v1/auth/login",
        json={
            "username": "lock_user",
            "password": "LockPassword123",
            "role": "admin",
            "unit_id": "fake-unit",
            "admin": True,
        },
    )
    assert login_with_spoofed_fields.status_code == 200, login_with_spoofed_fields.text
    assert login_with_spoofed_fields.json()["user"]["role"] == "unit_user"
    assert login_with_spoofed_fields.json()["user"]["unit_id"] == unit["id"]

    for _ in range(5):
        bad = client.post("/api/v1/auth/login", json={"username": "lock_user", "password": "bad-password"})
    assert bad.status_code == 401
    locked = client.post("/api/v1/auth/login", json={"username": "lock_user", "password": "LockPassword123"})
    assert locked.status_code == 423
    assert locked.json()["detail"] == "尝试次数过多，请稍后再试"

    from app.database import connect

    with connect() as conn:
        conn.execute("UPDATE users SET locked_until = NULL, failed_login_count = 4 WHERE username = 'lock_user'")
        conn.commit()
    success = client.post("/api/v1/auth/login", json={"username": "lock_user", "password": "LockPassword123"})
    assert success.status_code == 200, success.text
    with connect() as conn:
        row = conn.execute("SELECT failed_login_count, locked_until, last_login_at FROM users WHERE username = 'lock_user'").fetchone()
    assert row["failed_login_count"] == 0
    assert row["locked_until"] is None
    assert row["last_login_at"]


def test_login_and_me_return_unit_profile_fields(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit_id, _ = create_unit_user_product_order(client)

    login_response = client.post("/api/v1/auth/login", json={"username": "unit001", "password": "UnitPassword123"})
    assert login_response.status_code == 200, login_response.text
    user = login_response.json()["user"]
    assert user["username"] == "unit001"
    assert user["display_name"] == "第一食堂账号"
    assert user["role"] == "unit_user"
    assert user["unit_id"] == unit_id
    assert user["unit_code"] == "U001"
    assert user["unit_name"] == "第一食堂"
    assert user["default_delivery_point"] == "第一食堂收货点"
    assert user["must_change_password"] is False

    me = client.get("/api/v1/auth/me", headers=unit_headers)
    assert me.status_code == 200, me.text
    assert me.json()["unit_code"] == "U001"
    assert me.json()["unit_name"] == "第一食堂"
    assert me.json()["default_delivery_point"] == "第一食堂收货点"

    admin_me = client.get("/api/v1/auth/me", headers=admin_headers)
    assert admin_me.status_code == 200
    assert admin_me.json()["unit_id"] == ""
    assert admin_me.json()["unit_name"] == ""


def create_web_qr_challenge(client):
    response = client.post("/api/v1/web-auth/qr/challenges")
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload["challenge_id"]
    assert payload["qr_payload"].startswith("jingrongxianpei://web-login?token=")
    assert payload["qr_svg_data_url"].startswith("data:image/svg+xml;base64,")
    assert payload["status"] == "pending"
    assert payload["expires_at"] > payload["server_now"]
    assert client.cookies.get("jrxp_qr_binding")
    return payload


def test_web_qr_login_creates_bound_cookie_session_and_keeps_csrf(tmp_path):
    client = make_client(tmp_path)
    admin_headers, _, unit_id, _ = create_unit_user_product_order(client)

    challenge = create_web_qr_challenge(client)
    status = client.get(f"/api/v1/web-auth/qr/challenges/{challenge['challenge_id']}/status")
    assert status.status_code == 200
    assert status.json()["status"] == "pending"

    isolated_browser = TestClient(client.app)
    isolated_status = isolated_browser.get(f"/api/v1/web-auth/qr/challenges/{challenge['challenge_id']}/status")
    assert isolated_status.status_code == 403

    token = challenge["qr_payload"].split("token=", 1)[1]
    scan = client.post(
        "/api/v1/mobile/web-auth/qr/scan",
        headers=login(client, "unit001", "UnitPassword123"),
        json={"qr_token": token, "device_name": "Redmi Note 12", "app_version": "1.2.3"},
    )
    assert scan.status_code == 200, scan.text
    assert scan.json()["challenge_id"] == challenge["challenge_id"]
    assert scan.json()["status"] == "scanned"
    assert scan.json()["browser"]["name"]
    assert scan.json()["browser"]["ip"]
    assert scan.json()["user"]["role"] == "unit_user"
    assert scan.json()["user"]["unit_id"] == unit_id

    pending_consume = client.post(f"/api/v1/web-auth/qr/challenges/{challenge['challenge_id']}/consume")
    assert pending_consume.status_code == 409

    approved = client.post(
        f"/api/v1/mobile/web-auth/qr/{challenge['challenge_id']}/approve",
        headers=login(client, "unit001", "UnitPassword123"),
    )
    assert approved.status_code == 200, approved.text
    assert approved.json()["status"] == "approved"

    consume = client.post(f"/api/v1/web-auth/qr/challenges/{challenge['challenge_id']}/consume")
    assert consume.status_code == 200, consume.text
    assert consume.json()["user"]["username"] == "unit001"
    assert consume.json()["user"]["role"] == "unit_user"
    assert consume.json()["user"]["unit_id"] == unit_id
    assert "token" not in consume.json()

    session_cookie = client.cookies.get("jrxp_dev_session")
    csrf_cookie = client.cookies.get("csrf_token")
    assert session_cookie
    assert csrf_cookie
    assert "httponly" in consume.headers["set-cookie"].lower()
    assert "samesite=strict" in consume.headers["set-cookie"].lower()

    me = client.get("/api/v1/web-auth/me")
    assert me.status_code == 200, me.text
    assert me.json()["username"] == "unit001"

    forbidden_without_csrf = client.post(
        "/api/v1/orders",
        json={"items": []},
    )
    assert forbidden_without_csrf.status_code == 403
    assert forbidden_without_csrf.json()["detail"] == "请求已过期，请刷新页面后重试"

    allowed_with_csrf = client.get(
        "/api/v1/products",
        headers={"X-CSRF-Token": csrf_cookie},
    )
    assert allowed_with_csrf.status_code == 200

    logout_response = client.post("/api/v1/web-auth/logout", headers={"X-CSRF-Token": csrf_cookie})
    assert logout_response.status_code == 200
    assert client.get("/api/v1/web-auth/me").status_code == 401

    # Existing Android Bearer sessions still work after Web-specific additions.
    assert client.get("/api/v1/auth/me", headers=admin_headers).status_code == 200


def test_web_qr_reject_expiry_password_change_and_legacy_password_login_are_blocked(tmp_path):
    client = make_client(tmp_path)
    admin_headers, _, _, _ = create_unit_user_product_order(client)

    legacy = client.post(
        "/api/v1/web/auth/login",
        json={"username": "root_admin", "password": "StrongPassword123"},
    )
    assert legacy.status_code in (404, 405)

    challenge = create_web_qr_challenge(client)
    token = challenge["qr_payload"].split("token=", 1)[1]
    scan = client.post(
        "/api/v1/mobile/web-auth/qr/scan",
        headers=login(client, "unit001", "UnitPassword123"),
        json={"qr_token": token, "device_name": "Redmi Note 12", "app_version": "1.2.3"},
    )
    assert scan.status_code == 200
    rejected = client.post(
        f"/api/v1/mobile/web-auth/qr/{challenge['challenge_id']}/reject",
        headers=login(client, "unit001", "UnitPassword123"),
    )
    assert rejected.status_code == 200
    assert rejected.json()["status"] == "rejected"
    assert client.post(f"/api/v1/web-auth/qr/challenges/{challenge['challenge_id']}/consume").status_code == 409

    expired = create_web_qr_challenge(client)
    expired_token = expired["qr_payload"].split("token=", 1)[1]
    from app.database import connect

    with connect() as conn:
        conn.execute("UPDATE web_login_challenges SET expires_at = datetime('now', '-1 minute') WHERE id = ?", (expired["challenge_id"],))
        conn.commit()
    expired_scan = client.post(
        "/api/v1/mobile/web-auth/qr/scan",
        headers=login(client, "unit001", "UnitPassword123"),
        json={"qr_token": expired_token, "device_name": "Redmi Note 12", "app_version": "1.2.3"},
    )
    assert expired_scan.status_code == 410

    unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "PWD", "unit_name": "首次改密单位", "default_delivery_point": "首次改密点"},
    ).json()
    must_change = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "must_change",
            "password": "MustChange123",
            "display_name": "首次改密账号",
            "role": "unit_user",
            "unit_id": unit["id"],
            "must_change_password": True,
        },
    )
    assert must_change.status_code == 200
    change_headers = login(client, "must_change", "MustChange123")
    change_challenge = create_web_qr_challenge(client)
    change_scan = client.post(
        "/api/v1/mobile/web-auth/qr/scan",
        headers=change_headers,
        json={
            "qr_token": change_challenge["qr_payload"].split("token=", 1)[1],
            "device_name": "Redmi Note 12",
            "app_version": "1.2.3",
        },
    )
    assert change_scan.status_code == 403
    assert change_scan.json()["detail"] == "请先修改初始密码"


def test_admin_dashboard_overview_uses_beijing_business_date_and_keeps_legacy_dashboard(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit_id, product_id = create_unit_user_product_order(client)

    unit2 = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "U003", "unit_name": "第三食堂", "default_delivery_point": "三号点"},
    )
    assert unit2.status_code == 200, unit2.text
    product2 = client.post(
        "/api/v1/admin/products",
        headers=admin_headers,
        json={
            "product_code": "VEG-POTATO",
            "name": "土豆",
            "category": "蔬菜",
            "spec": "净菜",
            "unit": "公斤",
            "price_cents": 300,
            "stock_quantity": "30",
            "reserved_quantity": "0",
            "min_order_quantity": "1",
            "quantity_step": "1",
            "warning_quantity": "5",
            "supply_status": "normal",
        },
    )
    assert product2.status_code == 200, product2.text

    from app.database import connect

    def insert_order(
        *,
        unit: str,
        unit_name: str,
        status: str,
        total_cents: int,
        created_at: str,
        product: str,
        product_name: str,
        quantity: str,
        actual_quantity: str,
    ) -> str:
        order_id = str(uuid4())
        item_id = str(uuid4())
        with connect() as conn:
            admin_id = conn.execute("SELECT id FROM users WHERE username = 'root_admin'").fetchone()["id"]
            conn.execute(
                """
                INSERT INTO orders(
                  id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot,
                  note, status, total_cents, created_by, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, '', ?, ?, ?, ?, ?)
                """,
                (
                    order_id,
                    "SP20260703-" + order_id[:6],
                    unit,
                    unit_name,
                    f"{unit_name}配送点",
                    status,
                    total_cents,
                    admin_id,
                    created_at,
                    created_at,
                ),
            )
            conn.execute(
                """
                INSERT INTO order_items(
                  id, order_id, product_id, product_code_snapshot, product_name_snapshot,
                  category_snapshot, spec_snapshot, unit_snapshot, price_cents_snapshot,
                  quantity, requested_quantity, actual_quantity, subtotal_cents
                )
                VALUES (?, ?, ?, 'P', ?, '蔬菜', '普通', '公斤', 100, ?, ?, ?, ?)
                """,
                (item_id, order_id, product, product_name, quantity, quantity, actual_quantity, total_cents),
            )
            conn.commit()
        return order_id

    yesterday_local = insert_order(
        unit=unit_id,
        unit_name="第一食堂",
        status="completed",
        total_cents=500,
        created_at="2026-07-02 15:59:59",
        product=product_id,
        product_name="西红柿",
        quantity="9",
        actual_quantity="9",
    )
    pending_order = insert_order(
        unit=unit_id,
        unit_name="第一食堂",
        status="pending",
        total_cents=1000,
        created_at="2026-07-02 16:30:00",
        product=product_id,
        product_name="西红柿",
        quantity="5",
        actual_quantity="3",
    )
    insert_order(
        unit=unit2.json()["id"],
        unit_name="第三食堂",
        status="preparing",
        total_cents=2000,
        created_at="2026-07-03 08:00:00",
        product=product2.json()["id"],
        product_name="土豆",
        quantity="4",
        actual_quantity="",
    )
    insert_order(
        unit=unit2.json()["id"],
        unit_name="第三食堂",
        status="shipped",
        total_cents=300,
        created_at="2026-07-03 15:59:59",
        product=product2.json()["id"],
        product_name="土豆",
        quantity="1",
        actual_quantity="1",
    )
    insert_order(
        unit=unit_id,
        unit_name="第一食堂",
        status="cancelled",
        total_cents=9999,
        created_at="2026-07-03 03:00:00",
        product=product_id,
        product_name="西红柿",
        quantity="99",
        actual_quantity="99",
    )
    with connect() as conn:
        conn.execute("UPDATE products SET stock_quantity = '4', reserved_quantity = '3', warning_quantity = '2' WHERE id = ?", (product_id,))
        conn.execute(
            """
            INSERT INTO receipt_issues(id, order_id, unit_id, issue_type, description, status, reported_by, reported_at)
            VALUES (?, ?, ?, 'quality', '包装破损', 'open', (SELECT id FROM users WHERE username = 'unit001'), '2026-07-03 09:00:00')
            """,
            (str(uuid4()), pending_order, unit_id),
        )
        conn.commit()

    overview = client.get(
        "/api/v1/admin/dashboard/overview",
        headers=admin_headers,
        params={"business_date": "2026-07-03", "range_days": 7},
    )
    assert overview.status_code == 200, overview.text
    payload = overview.json()
    assert payload["business_date"] == "2026-07-03"
    assert payload["metrics"]["today_valid_orders"] == 3
    assert payload["metrics"]["today_total_cents"] == 3300
    assert payload["metrics"]["pending"] == 1
    assert payload["metrics"]["preparing"] == 1
    assert payload["metrics"]["waiting_shipment"] == 1
    assert payload["metrics"]["waiting_receipt"] == 1
    assert payload["metrics"]["open_receipt_issues"] == 1
    assert payload["metrics"]["tight_inventory"] == 1
    assert payload["comparisons"]["orders_vs_yesterday_percent"] == 200.0
    assert payload["trend"][-1]["date"] == "2026-07-03"
    assert payload["trend"][-1]["order_count"] == 3
    assert payload["trend"][-1]["amount_cents"] == 3300
    assert next(item for item in payload["demand_rank"] if item["name"] == "西红柿")["quantity"] == 3
    assert payload["unit_rank"][0]["unit_name"] == "第三食堂"
    assert len(payload["recent_orders"]) <= 10

    forbidden = client.get("/api/v1/admin/dashboard/overview", headers=unit_headers)
    assert forbidden.status_code == 403

    legacy = client.get("/api/v1/admin/dashboard", headers=admin_headers)
    assert legacy.status_code == 200
    assert {"today_orders", "today_total_cents", "pending", "recent_orders", "demand_rank"}.issubset(legacy.json().keys())
    assert yesterday_local


def test_change_password_requires_complex_password_and_new_login(tmp_path):
    client = make_client(tmp_path)
    _, unit_headers, _, _ = create_unit_user_product_order(client)

    too_short = client.post(
        "/api/v1/auth/change-password",
        headers=unit_headers,
        json={"old_password": "UnitPassword123", "new_password": "abc123"},
    )
    assert too_short.status_code == 400
    assert too_short.json()["detail"] == "密码至少 8 位，且包含字母和数字"

    same_as_username = client.post(
        "/api/v1/auth/change-password",
        headers=unit_headers,
        json={"old_password": "UnitPassword123", "new_password": "unit001"},
    )
    assert same_as_username.status_code == 400
    assert same_as_username.json()["detail"] == "新密码不能与账号相同"

    same_as_old = client.post(
        "/api/v1/auth/change-password",
        headers=unit_headers,
        json={"old_password": "UnitPassword123", "new_password": "UnitPassword123"},
    )
    assert same_as_old.status_code == 400
    assert same_as_old.json()["detail"] == "新密码不能与原密码相同"

    changed = client.post(
        "/api/v1/auth/change-password",
        headers=unit_headers,
        json={"old_password": "UnitPassword123", "new_password": "NewPass123"},
    )
    assert changed.status_code == 200
    assert client.get("/api/v1/auth/me", headers=unit_headers).status_code == 401
    assert client.post("/api/v1/auth/login", json={"username": "unit001", "password": "UnitPassword123"}).status_code == 401
    assert client.post("/api/v1/auth/login", json={"username": "unit001", "password": "NewPass123"}).status_code == 200


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
    assert order.json()["created_by_username"] == "unit001"
    assert "created_by" not in order.json()
    assert order.json()["total_cents"] == 900
    assert client.get("/api/v1/orders", headers=unit_headers).json()["total"] == 1
    assert client.get("/api/v1/admin/orders", headers=admin_headers).json()["total"] == 1


def test_unit_accounts_are_admin_created_and_share_only_their_unit_orders(tmp_path):
    client = make_client(tmp_path)
    admin_headers, first_headers, unit_id, product_id = create_unit_user_product_order(client)

    second_user = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "unit001_02",
            "password": "UnitPassword456",
            "display_name": "第一食堂第二账号",
            "role": "admin",
            "unit_id": unit_id,
            "must_change_password": False,
        },
    )
    assert second_user.status_code == 200, second_user.text
    assert second_user.json()["role"] == "unit_user"
    second_headers = login(client, "unit001_02", "UnitPassword456")

    other_unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "U-OTHER", "unit_name": "其他单位", "default_delivery_point": "其他收货点"},
    ).json()
    other_user = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "other_unit",
            "password": "OtherPassword123",
            "display_name": "其他单位账号",
            "role": "unit_user",
            "unit_id": other_unit["id"],
            "must_change_password": False,
        },
    )
    assert other_user.status_code == 200, other_user.text
    other_headers = login(client, "other_unit", "OtherPassword123")

    order = client.post(
        "/api/v1/orders",
        headers=first_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert order.status_code == 200, order.text
    assert client.get("/api/v1/orders", headers=second_headers).json()["total"] == 1
    hidden_detail = client.get(f"/api/v1/orders/{order.json()['id']}", headers=other_headers)
    assert hidden_detail.status_code == 404

    no_register = client.post(
        "/api/v1/auth/register",
        json={"username": "public_user", "password": "Public1234", "unit_id": unit_id, "role": "unit_user"},
    )
    assert no_register.status_code == 404

    no_unit = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={"username": "bad_unit", "password": "BadUnit123", "display_name": "无单位", "role": "unit_user"},
    )
    assert no_unit.status_code == 400
    assert no_unit.json()["detail"] == "请选择所属单位"

    disabled_unit = client.patch(f"/api/v1/admin/units/{other_unit['id']}/status", headers=admin_headers, json={"active": False})
    assert disabled_unit.status_code == 200
    disabled_create = client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "bad_disabled",
            "password": "BadDisabled123",
            "display_name": "停用单位账号",
            "role": "unit_user",
            "unit_id": other_unit["id"],
        },
    )
    assert disabled_create.status_code == 400
    assert disabled_create.json()["detail"] == "该单位已停用，暂时不能创建账号"


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

    accepted = client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "accepted"})
    assert accepted.status_code == 200
    assert accepted.json()["accepted_at"]
    assert not accepted.json()["preparing_at"]
    cannot_edit = client.put(
        f"/api/v1/orders/{order['id']}",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert cannot_edit.status_code == 409
    preparing = client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "preparing"})
    assert preparing.status_code == 200
    assert preparing.json()["accepted_at"]
    assert preparing.json()["preparing_at"]
    assert not preparing.json()["shipped_at"]
    direct_ship = client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "shipped"})
    assert direct_ship.status_code == 409
    assert direct_ship.json()["detail"] == "请先拍摄并上传发货照片"
    shipped = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"note": "已核对数量", "client_request_id": str(uuid4())},
        files=[("photos", ("ship.jpg", sample_image_bytes(), "image/jpeg"))],
    )
    assert shipped.status_code == 200
    assert shipped.json()["preparing_at"]
    assert shipped.json()["shipped_at"]
    assert shipped.json()["shipping_note"] == "已核对数量"
    assert shipped.json()["shipping_photo_count"] == 1
    completed = client.post(f"/api/v1/orders/{order['id']}/confirm-receipt", headers=unit_headers)
    assert completed.status_code == 200
    assert completed.json()["completed_at"]
    completed_product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert completed_product["stock_quantity"] == "7"
    assert completed_product["reserved_quantity"] == "0"


def test_ship_order_requires_private_photos_and_returns_authorized_views(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, unit_id, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    ).json()
    advance_to_preparing(client, admin_headers, order["id"])

    no_photo = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"note": "空照片", "client_request_id": str(uuid4())},
    )
    assert no_photo.status_code == 400
    assert no_photo.json()["detail"] == "请至少拍摄一张发货照片"

    too_many = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"note": "过多照片", "client_request_id": str(uuid4())},
        files=[("photos", (f"ship{i}.jpg", sample_image_bytes(), "image/jpeg")) for i in range(4)],
    )
    assert too_many.status_code == 400
    assert too_many.json()["detail"] == "最多上传三张发货照片"

    fake = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"note": "假图片", "client_request_id": str(uuid4())},
        files=[("photos", ("fake.jpg", b"not-a-real-image", "image/jpeg"))],
    )
    assert fake.status_code == 400
    assert fake.json()["detail"] == "发货照片格式不正确"
    assert client.get(f"/api/v1/orders/{order['id']}", headers=unit_headers).json()["status"] == "preparing"

    request_id = str(uuid4())
    shipped = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"note": "已核对数量", "client_request_id": request_id},
        files=[("photos", ("ship.jpg", sample_image_bytes(size=(1800, 1200)), "image/jpeg"))],
    )
    assert shipped.status_code == 200, shipped.text
    body = shipped.json()
    assert body["status"] == "shipped"
    assert body["shipped_at"]
    assert body["shipping_note"] == "已核对数量"
    assert body["shipping_photo_count"] == 1
    assert len(body["shipping_photos"]) == 1
    photo = body["shipping_photos"][0]
    assert photo["thumbnail_url"].startswith(f"/api/v1/orders/{order['id']}/shipping-photos/")
    assert photo["full_url"].endswith("variant=full")

    retry = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"note": "重复请求", "client_request_id": request_id},
        files=[("photos", ("ship-again.jpg", sample_image_bytes(color=(10, 40, 90)), "image/jpeg"))],
    )
    assert retry.status_code == 200, retry.text
    assert retry.json()["shipping_photo_count"] == 1

    thumbnail = client.get(photo["thumbnail_url"], headers=unit_headers)
    assert thumbnail.status_code == 200
    assert thumbnail.headers["content-type"] == "image/jpeg"
    assert "no-store" in thumbnail.headers["cache-control"]
    full = client.get(photo["full_url"], headers=admin_headers)
    assert full.status_code == 200
    with Image.open(BytesIO(full.content)) as processed:
        assert processed.format == "JPEG"
        assert max(processed.size) <= 1600
        assert not processed.getexif()

    other_unit = client.post(
        "/api/v1/admin/units",
        headers=admin_headers,
        json={"unit_code": "U-OTHER", "unit_name": "其他单位", "default_delivery_point": "其他收货点"},
    ).json()
    client.post(
        "/api/v1/admin/users",
        headers=admin_headers,
        json={
            "username": "unit_other",
            "password": "OtherPassword123",
            "display_name": "其他单位账号",
            "role": "unit_user",
            "unit_id": other_unit["id"],
            "must_change_password": False,
        },
    )
    other_headers = login(client, "unit_other", "OtherPassword123")
    assert client.get(photo["thumbnail_url"], headers=other_headers).status_code == 404
    assert client.get(photo["thumbnail_url"]).status_code == 401

    from app.database import connect

    with connect() as conn:
        photos = conn.execute("SELECT COUNT(*) AS c FROM order_shipping_photos WHERE order_id = ?", (order["id"],)).fetchone()["c"]
        logs = conn.execute("SELECT COUNT(*) AS c FROM order_logs WHERE order_id = ? AND action = 'ship'", (order["id"],)).fetchone()["c"]
        audits = conn.execute("SELECT COUNT(*) AS c FROM audit_logs WHERE object_id = ? AND action = 'order_ship'", (order["id"],)).fetchone()["c"]
        assert photos == 1
        assert logs == 1
        assert audits == 1


def test_ship_order_rejects_wrong_status_large_files_and_unit_users(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    pending_order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    ).json()
    pending_ship = client.post(
        f"/api/v1/admin/orders/{pending_order['id']}/ship",
        headers=admin_headers,
        data={"client_request_id": str(uuid4())},
        files=[("photos", ("ship.jpg", sample_image_bytes(), "image/jpeg"))],
    )
    assert pending_ship.status_code == 409
    assert pending_ship.json()["detail"] == "只有备货中的订单才能确认发货"

    accepted_order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    ).json()
    client.patch(f"/api/v1/admin/orders/{accepted_order['id']}/status", headers=admin_headers, json={"status": "accepted"})
    accepted_ship = client.post(
        f"/api/v1/admin/orders/{accepted_order['id']}/ship",
        headers=admin_headers,
        data={"client_request_id": str(uuid4())},
        files=[("photos", ("ship.jpg", sample_image_bytes(), "image/jpeg"))],
    )
    assert accepted_ship.status_code == 409
    assert accepted_ship.json()["detail"] == "只有备货中的订单才能确认发货"

    preparing_order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    ).json()
    advance_to_preparing(client, admin_headers, preparing_order["id"])
    unit_ship = client.post(
        f"/api/v1/admin/orders/{preparing_order['id']}/ship",
        headers=unit_headers,
        data={"client_request_id": str(uuid4())},
        files=[("photos", ("ship.jpg", sample_image_bytes(), "image/jpeg"))],
    )
    assert unit_ship.status_code == 403

    large = client.post(
        f"/api/v1/admin/orders/{preparing_order['id']}/ship",
        headers=admin_headers,
        data={"client_request_id": str(uuid4())},
        files=[("photos", ("large.jpg", b"x" * (5 * 1024 * 1024 + 1), "image/jpeg"))],
    )
    assert large.status_code == 400
    assert large.json()["detail"] == "发货照片不能超过 5MB"


def test_order_rejects_unavailable_below_min_and_invalid_step(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)

    below_min = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "0.5"}]},
    )
    assert below_min.status_code == 400
    assert below_min.json()["detail"] == "低于最小申领量"

    wrong_step = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1.25"}]},
    )
    assert wrong_step.status_code == 400
    assert wrong_step.json()["detail"] == "数量不符合申领步长"

    zero_stock_product = client.post(
        "/api/v1/admin/products",
        headers=admin_headers,
        json={
            "product_code": "ZERO-STOCK",
            "name": "空库存",
            "category": "蔬菜",
            "spec": "一级",
            "unit": "公斤",
            "price_cents": 300,
            "stock_quantity": "0",
            "min_order_quantity": "1",
            "quantity_step": "1",
            "supply_status": "normal",
        },
    ).json()
    insufficient = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": zero_stock_product["id"], "quantity": "1"}]},
    )
    assert insufficient.status_code == 409
    assert insufficient.json()["detail"] == "库存不足，请减少数量"

    client.patch(f"/api/v1/admin/products/{product_id}/status", headers=admin_headers, json={"supply_status": "paused", "active": True})
    paused = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert paused.status_code == 409
    assert paused.json()["detail"] == "食材已暂停供应或下架"

    zero_price_product = client.post(
        "/api/v1/admin/products",
        headers=admin_headers,
        json={
            "product_code": "ZERO-PRICE",
            "name": "无价格",
            "category": "蔬菜",
            "spec": "一级",
            "unit": "公斤",
            "price_cents": 0,
            "stock_quantity": "10",
            "min_order_quantity": "1",
            "quantity_step": "1",
            "supply_status": "paused",
            "active": False,
        },
    ).json()
    cannot_enable_zero_price = client.patch(
        f"/api/v1/admin/products/{zero_price_product['id']}/status",
        headers=admin_headers,
        json={"supply_status": "normal", "active": True},
    )
    assert cannot_enable_zero_price.status_code == 400
    assert cannot_enable_zero_price.json()["detail"] == "请先填写商品价格"


def test_product_restore_price_and_inventory_adjustment_are_logged(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)

    assert client.delete(f"/api/v1/admin/products/{product_id}", headers=admin_headers).status_code == 200
    hidden = client.get("/api/v1/products", headers=unit_headers).json()
    assert all(product["id"] != product_id for product in hidden)

    restored = client.post(f"/api/v1/admin/products/{product_id}/restore", headers=admin_headers)
    assert restored.status_code == 200, restored.text
    assert restored.json()["active"] is False
    assert restored.json()["supply_status"] == "paused"

    price = client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=admin_headers,
        json={"price_cents": 575, "reason": "恢复后重新定价"},
    )
    assert price.status_code == 200, price.text
    assert price.json()["price_cents"] == 575

    stock = client.patch(
        f"/api/v1/admin/products/{product_id}/stock",
        headers=admin_headers,
        json={"stock_quantity": "15.5", "detail": "盘点调整"},
    )
    assert stock.status_code == 200, stock.text
    assert stock.json()["stock_quantity"] == "15.5"
    assert stock.json()["available_quantity"] == "15.5"

    from app.database import connect

    with connect() as conn:
        price_logs = conn.execute("SELECT COUNT(*) FROM product_price_logs WHERE product_id = ?", (product_id,)).fetchone()[0]
        stock_logs = conn.execute("SELECT COUNT(*) FROM inventory_logs WHERE product_id = ? AND action = 'admin_adjust'", (product_id,)).fetchone()[0]
    assert price_logs >= 2
    assert stock_logs == 1


def test_product_code_price_inventory_specialized_updates_and_audit(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()

    generated = client.post(
        "/api/v1/admin/products",
        headers=admin_headers,
        json={
            "name": "自动编码白菜",
            "category": "蔬菜",
            "spec": "一级",
            "unit": "公斤",
            "price_cents": 320,
            "stock_quantity": "0",
            "min_order_quantity": "1",
            "quantity_step": "1",
            "supply_status": "normal",
            "active": True,
        },
    )
    assert generated.status_code == 200, generated.text
    assert generated.json()["product_code"].startswith("P")

    missing_reason = client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=admin_headers,
        json={
            "price_cents": 580,
            "reason": "  ",
            "expected_updated_at": product["updated_at"],
        },
    )
    assert missing_reason.status_code == 400
    assert missing_reason.json()["detail"] == "请填写价格调整原因"

    price = client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=admin_headers,
        json={
            "price_cents": 575,
            "reason": "今日采购价格调整",
            "expected_updated_at": product["updated_at"],
        },
    )
    assert price.status_code == 200, price.text
    assert price.json()["price_cents"] == 575

    stale_price = client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=admin_headers,
        json={
            "price_cents": 600,
            "reason": "过期请求",
            "expected_updated_at": product["updated_at"],
        },
    )
    assert stale_price.status_code == 409
    assert stale_price.json()["detail"] == "该食材刚刚被其他管理员修改，请刷新后重试"

    unit_forbidden = client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=unit_headers,
        json={"price_cents": 580, "reason": "无权操作", "expected_updated_at": price.json()["updated_at"]},
    )
    assert unit_forbidden.status_code == 403

    inventory = client.post(
        f"/api/v1/admin/products/{product_id}/inventory-adjust",
        headers=admin_headers,
        json={
            "mode": "increase",
            "quantity": "5.5",
            "reason": "新一批到货",
            "expected_updated_at": price.json()["updated_at"],
        },
    )
    assert inventory.status_code == 200, inventory.text
    assert inventory.json()["before_stock_quantity"] == "10"
    assert inventory.json()["after_stock_quantity"] == "15.5"
    assert inventory.json()["available_quantity"] == "15.5"
    adjusted_product = inventory.json()["product"]
    assert adjusted_product["stock_quantity"] == "15.5"

    decrease = client.post(
        f"/api/v1/admin/products/{product_id}/inventory-adjust",
        headers=admin_headers,
        json={
            "mode": "decrease",
            "quantity": "1.5",
            "reason": "盘点扣减",
            "expected_updated_at": adjusted_product["updated_at"],
        },
    )
    assert decrease.status_code == 200, decrease.text
    assert decrease.json()["after_stock_quantity"] == "14"

    set_stock = client.post(
        f"/api/v1/admin/products/{product_id}/inventory-adjust",
        headers=admin_headers,
        json={
            "mode": "set",
            "quantity": "12",
            "reason": "盘点设置",
            "expected_updated_at": decrease.json()["product"]["updated_at"],
        },
    )
    assert set_stock.status_code == 200, set_stock.text
    assert set_stock.json()["after_stock_quantity"] == "12"

    stale_inventory = client.post(
        f"/api/v1/admin/products/{product_id}/inventory-adjust",
        headers=admin_headers,
        json={
            "mode": "increase",
            "quantity": "1",
            "reason": "过期库存请求",
            "expected_updated_at": adjusted_product["updated_at"],
        },
    )
    assert stale_inventory.status_code == 409

    from app.database import connect

    with connect() as conn:
        price_log = conn.execute(
            "SELECT old_price_cents, new_price_cents, reason FROM product_price_logs WHERE product_id = ? AND reason = '今日采购价格调整' ORDER BY rowid DESC LIMIT 1",
            (product_id,),
        ).fetchone()
        inventory_log = conn.execute(
            "SELECT action, detail, mode, before_quantity, after_quantity, reserved_quantity FROM inventory_logs WHERE product_id = ? AND detail = '盘点设置' ORDER BY rowid DESC LIMIT 1",
            (product_id,),
        ).fetchone()
        price_audit = conn.execute("SELECT COUNT(*) FROM audit_logs WHERE object_id = ? AND action = 'PRODUCT_PRICE_CHANGED'", (product_id,)).fetchone()[0]
        inventory_audit = conn.execute("SELECT COUNT(*) FROM audit_logs WHERE object_id = ? AND action = 'PRODUCT_INVENTORY_ADJUSTED'", (product_id,)).fetchone()[0]
    assert price_log["old_price_cents"] == 450
    assert price_log["new_price_cents"] == 575
    assert price_log["reason"] == "今日采购价格调整"
    assert inventory_log["action"] == "admin_adjust"
    assert inventory_log["detail"] == "盘点设置"
    assert inventory_log["mode"] == "set"
    assert inventory_log["before_quantity"] == "14"
    assert inventory_log["after_quantity"] == "12"
    assert inventory_log["reserved_quantity"] == "0"
    assert price_audit == 1
    assert inventory_audit >= 3


def test_product_profile_update_does_not_overwrite_price_or_stock(tmp_path):
    client = make_client(tmp_path)
    admin_headers, _, _, product_id = create_unit_user_product_order(client)
    product = client.get(f"/api/v1/products/{product_id}", headers=admin_headers).json()
    update = client.put(
        f"/api/v1/admin/products/{product_id}",
        headers=admin_headers,
        json={
            "name": "西红柿新规格",
            "spec": "新规格",
            "unit": "公斤",
            "price_cents": 999,
            "stock_quantity": "99",
            "expected_updated_at": product["updated_at"],
        },
    )
    assert update.status_code == 200, update.text
    assert update.json()["name"] == "西红柿新规格"
    assert update.json()["price_cents"] == 450
    assert update.json()["stock_quantity"] == "10"


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
    from io import BytesIO
    from openpyxl import load_workbook

    workbook = load_workbook(BytesIO(export.content), read_only=True)
    assert workbook.sheetnames == ["订单台账", "商品需求汇总"]


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


def test_cli_creates_admin_without_printing_secret(tmp_path, monkeypatch, capsys):
    make_client(tmp_path)

    from app.cli import create_admin

    monkeypatch.setenv("NEW_ADMIN_PASSWORD", "AnotherAdmin456")
    created = create_admin("ops_admin", "运营管理员")
    assert created is True
    captured = capsys.readouterr()
    output = captured.out + captured.err
    assert "AnotherAdmin456" not in output

    client = TestClient(__import__("app.main", fromlist=["app"]).app)
    assert client.post("/api/v1/auth/login", json={"username": "ops_admin", "password": "AnotherAdmin456"}).status_code == 200


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


def test_admin_reset_revoke_sessions_unique_admin_guard_and_safe_audit(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, _ = create_unit_user_product_order(client)
    users = client.get("/api/v1/admin/users", headers=admin_headers).json()
    admin_user = next(user for user in users if user["username"] == "root_admin")
    unit_user = next(user for user in users if user["username"] == "unit001")

    cannot_disable_only_admin = client.patch(
        f"/api/v1/admin/users/{admin_user['id']}/status",
        headers=admin_headers,
        json={"active": False},
    )
    assert cannot_disable_only_admin.status_code == 409
    assert cannot_disable_only_admin.json()["detail"] == "不能停用当前唯一的管理员账号"

    revoke = client.post(f"/api/v1/admin/users/{unit_user['id']}/revoke-sessions", headers=admin_headers)
    assert revoke.status_code == 200
    assert revoke.json()["message"] == "该账号已从所有设备退出"
    assert client.get("/api/v1/auth/me", headers=unit_headers).status_code == 401

    fresh_headers = login(client, "unit001", "UnitPassword123")
    reset = client.post(
        f"/api/v1/admin/users/{unit_user['id']}/reset-password",
        headers=admin_headers,
        json={"new_password": "ResetPassword789", "must_change_password": True},
    )
    assert reset.status_code == 200
    assert reset.json()["message"] == "密码已重置"
    assert reset.json()["initial_password"] == "ResetPassword789"
    assert client.get("/api/v1/auth/me", headers=fresh_headers).status_code == 401
    assert client.post("/api/v1/auth/login", json={"username": "unit001", "password": "UnitPassword123"}).status_code == 401
    changed_login = client.post("/api/v1/auth/login", json={"username": "unit001", "password": "ResetPassword789"})
    assert changed_login.status_code == 200
    assert changed_login.json()["user"]["must_change_password"] is True

    from app.database import connect

    with connect() as conn:
        rows = conn.execute("SELECT action, before_json, after_json FROM audit_logs").fetchall()
    audit_text = "\n".join(f"{row['action']} {row['before_json']} {row['after_json']}" for row in rows)
    assert "ResetPassword789" not in audit_text
    assert "UnitPassword123" not in audit_text
    assert "Bearer " not in audit_text
    assert any(row["action"] == "ADMIN_RESET_PASSWORD" for row in rows)
    assert any(row["action"] == "ADMIN_REVOKE_USER_SESSIONS" for row in rows)


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


def test_cutoff_blocks_order_creation_with_business_error_code(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)

    enabled = client.patch(
        "/api/v1/admin/procurement/cutoff",
        headers=admin_headers,
        json={"enabled": True, "cutoff_time": "00:00"},
    )
    assert enabled.status_code == 200, enabled.text
    cutoff = client.get("/api/v1/procurement/cutoff", headers=unit_headers)
    assert cutoff.status_code == 200
    assert cutoff.json()["is_closed"] is True

    blocked = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    )
    assert blocked.status_code == 409
    assert blocked.json() == {"code": "ORDER_CUTOFF_PASSED", "detail": "今日采购已经截止，请联系管理员"}


def test_reorder_preview_uses_current_inventory_price_and_supply_status(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "2"}]},
    ).json()
    client.patch(
        f"/api/v1/admin/products/{product_id}/price",
        headers=admin_headers,
        json={"price_cents": 520, "reason": "今日价格"},
    )
    preview = client.get(f"/api/v1/orders/{order['id']}/reorder-preview", headers=unit_headers)
    assert preview.status_code == 200, preview.text
    item = preview.json()["items"][0]
    assert item["previous_price_cents"] == 450
    assert item["current_price_cents"] == 520
    assert item["available"] is True
    assert item["available_quantity"] == "8"

    client.patch(f"/api/v1/admin/products/{product_id}/status", headers=admin_headers, json={"supply_status": "paused", "active": True})
    unavailable = client.get(f"/api/v1/orders/{order['id']}/reorder-preview", headers=unit_headers).json()["items"][0]
    assert unavailable["available"] is False
    assert unavailable["message"] == "食材已暂停供应或下架"


def test_adjust_actual_quantity_updates_reserved_total_summary_delivery_and_excel(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "3"}]},
    ).json()
    accepted = client.patch(f"/api/v1/admin/orders/{order['id']}/status", headers=admin_headers, json={"status": "accepted"}).json()
    item_id = accepted["items"][0]["id"]

    adjusted = client.patch(
        f"/api/v1/admin/orders/{order['id']}/items/{item_id}/actual-quantity",
        headers=admin_headers,
        json={"actual_quantity": "2", "reason": "到货不足", "expected_updated_at": accepted["updated_at"]},
    )
    assert adjusted.status_code == 200, adjusted.text
    body = adjusted.json()
    assert body["total_cents"] == 900
    assert body["has_adjustments"] is True
    assert body["items"][0]["requested_quantity"] == "3"
    assert body["items"][0]["actual_quantity"] == "2"
    assert body["items"][0]["adjustment_reason"] == "到货不足"
    product = client.get(f"/api/v1/products/{product_id}", headers=unit_headers).json()
    assert product["reserved_quantity"] == "2"

    stale = client.patch(
        f"/api/v1/admin/orders/{order['id']}/items/{item_id}/actual-quantity",
        headers=admin_headers,
        json={"actual_quantity": "1", "reason": "过期请求", "expected_updated_at": accepted["updated_at"]},
    )
    assert stale.status_code == 409
    assert stale.json()["code"] == "ORDER_CONFLICT"

    summary = client.get("/api/v1/admin/preparation-summary", headers=admin_headers)
    assert summary.status_code == 200, summary.text
    assert summary.json()["items"][0]["requested_quantity"] == 3
    assert summary.json()["items"][0]["actual_quantity"] == 2
    delivery = client.get("/api/v1/admin/delivery-sheets", headers=admin_headers)
    assert delivery.status_code == 200
    delivery_item = delivery.json()["units"][0]["orders"][0]["items"][0]
    assert delivery_item["adjusted"] is True
    assert delivery_item["actual_quantity"] == "2"

    prep_excel = client.get("/api/v1/admin/preparation-summary/export.xlsx", headers=admin_headers)
    delivery_excel = client.get("/api/v1/admin/delivery-sheets/export.xlsx", headers=admin_headers)
    assert prep_excel.status_code == 200
    assert delivery_excel.status_code == 200
    assert prep_excel.content.startswith(b"PK")
    assert delivery_excel.content.startswith(b"PK")


def test_receipt_issue_blocks_confirm_until_admin_resolves_and_uses_private_photos(tmp_path):
    client = make_client(tmp_path)
    admin_headers, unit_headers, _, product_id = create_unit_user_product_order(client)
    order = client.post(
        "/api/v1/orders",
        headers=unit_headers,
        json={"items": [{"product_id": product_id, "quantity": "1"}]},
    ).json()
    advance_to_preparing(client, admin_headers, order["id"])
    shipped = client.post(
        f"/api/v1/admin/orders/{order['id']}/ship",
        headers=admin_headers,
        data={"client_request_id": str(uuid4())},
        files=[("photos", ("ship.jpg", sample_image_bytes(), "image/jpeg"))],
    ).json()
    assert shipped["status"] == "shipped"

    issue = client.post(
        f"/api/v1/orders/{order['id']}/receipt-issues",
        headers=unit_headers,
        data={"issue_type": "quantity_shortage", "description": "少了一筐"},
        files=[("photos", ("issue.jpg", sample_image_bytes(color=(30, 120, 40)), "image/jpeg"))],
    )
    assert issue.status_code == 200, issue.text
    assert issue.json()["status"] == "open"
    assert len(issue.json()["photos"]) == 1
    photo_url = issue.json()["photos"][0]["thumbnail_url"]
    assert client.get(photo_url, headers=unit_headers).status_code == 200
    assert client.post(f"/api/v1/orders/{order['id']}/confirm-receipt", headers=unit_headers).status_code == 409

    badges = client.get("/api/v1/notifications/badges", headers=admin_headers)
    assert badges.status_code == 200
    assert badges.json()["open_receipt_issues"] == 1
    admin_list = client.get("/api/v1/admin/receipt-issues?status=open", headers=admin_headers)
    assert admin_list.status_code == 200
    assert admin_list.json()[0]["order_no"] == order["order_no"]

    resolved = client.post(
        f"/api/v1/admin/receipt-issues/{issue.json()['id']}/resolve",
        headers=admin_headers,
        json={"resolution_note": "已补送"},
    )
    assert resolved.status_code == 200
    assert resolved.json()["status"] == "resolved"
    unit_badges = client.get("/api/v1/notifications/badges", headers=unit_headers).json()
    assert unit_badges["resolved_receipt_issues"] == 1
    confirmed = client.post(f"/api/v1/orders/{order['id']}/confirm-receipt", headers=unit_headers)
    assert confirmed.status_code == 200, confirmed.text
