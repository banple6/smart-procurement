import os
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

from fastapi.testclient import TestClient


def make_client(tmp_path):
    os.environ.update(
        APP_ENV="test",
        APP_SECRET="quota-test-secret",
        DATABASE_PATH=str(tmp_path / "quota.db"),
        UPLOAD_DIR=str(tmp_path / "uploads"),
        PRIVATE_UPLOAD_DIR=str(tmp_path / "private_uploads"),
        INITIAL_ADMIN_USERNAME="root_admin",
        INITIAL_ADMIN_PASSWORD="StrongPassword123",
    )
    from app.database import init_db
    from app.main import app, seed_initial_admin

    init_db()
    seed_initial_admin()
    return TestClient(app)


def login(client, username, password):
    response = client.post("/api/v1/auth/login", json={"username": username, "password": password})
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['token']}"}


def setup_quota(client, cents=1000):
    admin = login(client, "root_admin", "StrongPassword123")
    unit = client.post("/api/v1/admin/units", headers=admin, json={"unit_code": "QUOTA-1", "unit_name": "额度测试单位"}).json()
    user = client.post(
        "/api/v1/admin/users", headers=admin,
        json={"username": "quota_unit", "password": "UnitPassword123", "display_name": "额度用户", "role": "unit_user", "unit_id": unit["id"], "must_change_password": False},
    )
    assert user.status_code == 200, user.text
    product = client.post(
        "/api/v1/admin/products", headers=admin,
        json={"product_code": "QUOTA-POTATO", "name": "额度土豆", "category": "蔬菜", "spec": "散装", "unit": "斤", "price_cents": 321, "stock_quantity": "1000", "min_order_quantity": "0.5", "quantity_step": "0.5", "warning_quantity": "1", "supply_status": "normal"},
    )
    assert product.status_code == 200, product.text
    enabled = client.put(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin, json={"enabled": True, "default_monthly_quota_cents": cents})
    assert enabled.status_code == 200, enabled.text
    return admin, login(client, "quota_unit", "UnitPassword123"), unit, product.json()


def order_payload(product_id, quantity, request_id):
    return {"client_request_id": request_id, "items": [{"product_id": product_id, "quantity": quantity}]}


def test_quota_disabled_is_backward_compatible(tmp_path):
    client = make_client(tmp_path)
    admin, unit_headers, unit, product = setup_quota(client, cents=1000)
    assert client.put(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin, json={"enabled": False, "default_monthly_quota_cents": 0}).status_code == 200
    created = client.post("/api/v1/orders", headers=unit_headers, json=order_payload(product["id"], "10", "quota-disabled"))
    assert created.status_code == 200, created.text
    assert client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()["enabled"] is False


def test_quota_reserve_idempotency_and_cancel_release(tmp_path):
    client = make_client(tmp_path)
    admin, unit_headers, unit, product = setup_quota(client, cents=1000)
    first = client.post("/api/v1/orders", headers={**unit_headers, "Idempotency-Key": "quota-idem"}, json=order_payload(product["id"], "2", "quota-idem"))
    repeat = client.post("/api/v1/orders", headers={**unit_headers, "Idempotency-Key": "quota-idem"}, json=order_payload(product["id"], "2", "quota-idem"))
    assert first.status_code == repeat.status_code == 200
    assert first.json()["id"] == repeat.json()["id"]
    quota = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    assert quota["available_cents"] == 358
    assert client.post(f"/api/v1/orders/{first.json()['id']}/cancel", headers=unit_headers, json={"reason": "测试取消"}).status_code == 200
    assert client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()["available_cents"] == 1000


def test_quota_insufficient_never_overdraws_and_adjustment_is_audited(tmp_path):
    client = make_client(tmp_path)
    admin, unit_headers, unit, product = setup_quota(client, cents=1000)
    success = client.post("/api/v1/orders", headers=unit_headers, json=order_payload(product["id"], "2", "quota-ok"))
    blocked = client.post("/api/v1/orders", headers=unit_headers, json=order_payload(product["id"], "2", "quota-blocked"))
    assert success.status_code == 200
    assert blocked.status_code == 409
    assert blocked.json()["detail"]["code"] == "QUOTA_INSUFFICIENT"
    assert client.post(f"/api/v1/admin/units/{unit['id']}/quota/adjustments", headers=admin, json={"delta_cents": 500, "reason": "临时保障"}).status_code == 200
    quota = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    assert quota["available_cents"] == 858
    ledger = client.get(f"/api/v1/admin/units/{unit['id']}/quota/ledger", headers=admin).json()["items"]
    assert any(row["event_type"] == "MANUAL_INCREASE" and row["delta_cents"] == 500 for row in ledger)


def test_quota_management_rejects_stale_settings_and_adjustments(tmp_path):
    client = make_client(tmp_path)
    admin, _, unit, _ = setup_quota(client, cents=1000)
    initial = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    assert initial["updated_at"]
    assert initial["display_updated_at"]

    changed = client.put(
        f"/api/v1/admin/units/{unit['id']}/quota",
        headers=admin,
        json={"enabled": True, "default_monthly_quota_cents": 1200, "expected_version": initial["version"]},
    )
    assert changed.status_code == 200, changed.text
    stale_settings = client.put(
        f"/api/v1/admin/units/{unit['id']}/quota",
        headers=admin,
        json={"enabled": True, "default_monthly_quota_cents": 1500, "expected_version": initial["version"]},
    )
    assert stale_settings.status_code == 409
    assert stale_settings.json()["detail"]["code"] == "STALE_WRITE"

    current = changed.json()
    adjustment = client.post(
        f"/api/v1/admin/units/{unit['id']}/quota/adjustments",
        headers=admin,
        json={"delta_cents": 100, "reason": "测试补充", "expected_version": current["version"]},
    )
    assert adjustment.status_code == 200, adjustment.text
    stale_adjustment = client.post(
        f"/api/v1/admin/units/{unit['id']}/quota/adjustments",
        headers=admin,
        json={"delta_cents": 100, "reason": "旧页面重复提交", "expected_version": current["version"]},
    )
    assert stale_adjustment.status_code == 409
    assert stale_adjustment.json()["detail"]["code"] == "STALE_WRITE"
    ledger = client.get(f"/api/v1/admin/units/{unit['id']}/quota/ledger", headers=admin).json()["items"]
    assert ledger[0]["display_created_at"]


def test_unit_web_maps_structured_quota_conflict_to_actionable_message():
    source = (Path(__file__).parents[1] / "app" / "static" / "unit" / "unit.js").read_text(encoding="utf-8")
    assert 'detail.code === "QUOTA_INSUFFICIENT"' in source
    assert "本月可用采购额度不足，请调整申领数量后重试。" in source


def test_simultaneous_order_reservations_allow_only_one_when_quota_is_exhausted(tmp_path):
    client = make_client(tmp_path)
    admin, unit_headers, unit, product = setup_quota(client, cents=1000)

    def create(request_id):
        return client.post(
            "/api/v1/orders",
            headers={**unit_headers, "Idempotency-Key": request_id},
            json=order_payload(product["id"], "2", request_id),
        )

    with ThreadPoolExecutor(max_workers=2) as executor:
        responses = list(executor.map(create, ["quota-race-a", "quota-race-b"]))

    assert sorted(response.status_code for response in responses) == [200, 409]
    quota = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    assert quota["available_cents"] == 358
    assert len(client.get(f"/api/v1/admin/units/{unit['id']}/quota/ledger", headers=admin).json()["items"]) == 2  # grant + one reserve


def test_quota_month_grants_only_once_and_carries_balance(tmp_path):
    client = make_client(tmp_path)
    admin, _, unit, _ = setup_quota(client, cents=1000)
    from app.database import transaction
    from app.services.unit_quota import ensure_quota_month, quota_payload

    with transaction() as conn:
        conn.execute("UPDATE unit_quota_accounts SET balance_cents = 400, last_granted_month = '2026-08' WHERE unit_id = ?", (unit["id"],))
        ensure_quota_month(conn, unit["id"], "2026-10")
        assert quota_payload(conn, unit["id"], "2026-10")["available_cents"] == 2400
        ensure_quota_month(conn, unit["id"], "2026-10")
        assert quota_payload(conn, unit["id"], "2026-10")["available_cents"] == 2400
        count = conn.execute("SELECT COUNT(*) FROM unit_quota_months WHERE unit_id = ?", (unit["id"],)).fetchone()[0]
        assert count == 3


def test_future_month_plan_default_fallback_modify_and_restore(tmp_path):
    client = make_client(tmp_path)
    admin, _, unit, _ = setup_quota(client, cents=1000)
    initial = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    future = initial["future_months"][0]
    assert future["source"] == "default"
    assert future["planned_quota_cents"] == 1000

    planned = client.put(
        f"/api/v1/admin/units/{unit['id']}/quota/future-months/{future['quota_month']}",
        headers=admin,
        json={"planned_quota_cents": 2500, "expected_version": initial["version"], "client_request_id": "future-plan-one"},
    )
    assert planned.status_code == 200, planned.text
    explicit = next(item for item in planned.json()["future_months"] if item["quota_month"] == future["quota_month"])
    assert explicit == {"quota_month": future["quota_month"], "planned_quota_cents": 2500, "source": "explicit", "editable": True}

    changed_default = client.put(
        f"/api/v1/admin/units/{unit['id']}/quota",
        headers=admin,
        json={"enabled": True, "default_monthly_quota_cents": 1800, "expected_version": planned.json()["version"], "client_request_id": "default-change"},
    )
    assert changed_default.status_code == 200, changed_default.text
    explicit_after_default = next(item for item in changed_default.json()["future_months"] if item["quota_month"] == future["quota_month"])
    assert explicit_after_default["planned_quota_cents"] == 2500
    assert explicit_after_default["source"] == "explicit"

    restored = client.post(
        f"/api/v1/admin/units/{unit['id']}/quota/future-months/{future['quota_month']}/restore-default",
        headers=admin,
        json={"expected_version": changed_default.json()["version"], "client_request_id": "future-plan-restore"},
    )
    assert restored.status_code == 200, restored.text
    default_after_restore = next(item for item in restored.json()["future_months"] if item["quota_month"] == future["quota_month"])
    assert default_after_restore["source"] == "default"
    assert default_after_restore["planned_quota_cents"] == 1800


def test_current_month_correction_and_balance_adjustment_are_distinct_and_idempotent(tmp_path):
    client = make_client(tmp_path)
    admin, _, unit, _ = setup_quota(client, cents=1000)
    initial = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    correction_payload = {
        "effective_quota_cents": 1400,
        "reason": "本月预算修正",
        "expected_version": initial["version"],
        "client_request_id": "month-correction-one",
    }
    corrected = client.post(f"/api/v1/admin/units/{unit['id']}/quota/current-month-correction", headers=admin, json=correction_payload)
    assert corrected.status_code == 200, corrected.text
    repeat = client.post(f"/api/v1/admin/units/{unit['id']}/quota/current-month-correction", headers=admin, json=correction_payload)
    assert repeat.status_code == 200, repeat.text
    assert repeat.json()["available_cents"] == 1400
    assert repeat.json()["monthly_correction_cents"] == 400
    assert repeat.json()["effective_quota_cents"] == 1400

    adjusted = client.post(
        f"/api/v1/admin/units/{unit['id']}/quota/adjustments",
        headers=admin,
        json={"delta_cents": -300, "reason": "临时扣减", "expected_version": repeat.json()["version"], "client_request_id": "balance-adjustment-one"},
    )
    assert adjusted.status_code == 200, adjusted.text
    assert adjusted.json()["available_cents"] == 1100
    assert adjusted.json()["monthly_correction_cents"] == 400
    assert adjusted.json()["effective_quota_cents"] == 1400
    assert adjusted.json()["balance_adjustment_cents"] == -300

    unsafe = client.post(
        f"/api/v1/admin/units/{unit['id']}/quota/current-month-correction",
        headers=admin,
        json={"effective_quota_cents": 0, "reason": "不安全降低", "expected_version": adjusted.json()["version"], "client_request_id": "month-correction-negative"},
    )
    assert unsafe.status_code == 409
    assert "当前可用额度为负" in unsafe.json()["detail"]

    ledger = client.get(f"/api/v1/admin/units/{unit['id']}/quota/ledger", headers=admin).json()["items"]
    assert sum(item["event_type"] == "MONTHLY_QUOTA_CORRECTION" for item in ledger) == 1
    assert any(item["event_type"] == "MANUAL_DECREASE" for item in ledger)


def test_future_explicit_plan_materializes_once_when_activated(tmp_path):
    client = make_client(tmp_path)
    admin, _, unit, _ = setup_quota(client, cents=1000)
    initial = client.get(f"/api/v1/admin/units/{unit['id']}/quota", headers=admin).json()
    future = initial["future_months"][0]
    planned = client.put(
        f"/api/v1/admin/units/{unit['id']}/quota/future-months/{future['quota_month']}",
        headers=admin,
        json={"planned_quota_cents": 2500, "expected_version": initial["version"], "client_request_id": "future-materialize"},
    )
    assert planned.status_code == 200, planned.text

    from app.database import transaction
    from app.services.unit_quota import ensure_quota_month

    with transaction() as conn:
        activated = ensure_quota_month(conn, unit["id"], future["quota_month"])
        assert activated["balance_cents"] == 3500
        ensure_quota_month(conn, unit["id"], future["quota_month"])
        assert conn.execute(
            "SELECT COUNT(*) FROM unit_quota_ledger WHERE unit_id = ? AND quota_month = ? AND event_type = 'MONTHLY_GRANT'",
            (unit["id"], future["quota_month"]),
        ).fetchone()[0] == 1
