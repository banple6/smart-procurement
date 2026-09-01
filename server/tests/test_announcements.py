from datetime import datetime, timedelta, timezone
from pathlib import Path

from app.database import connect
from test_workflows import create_unit_user_product_order, login, make_client


def _admin(client):
    return login(client, "root_admin", "StrongPassword123")


def _create_unit_user(client, admin, suffix: str):
    unit = client.post(
        "/api/v1/admin/units",
        headers=admin,
        json={"unit_code": f"A{suffix}", "unit_name": f"公告单位{suffix}", "default_delivery_point": "隔离收货点"},
    )
    assert unit.status_code == 200, unit.text
    unit_id = unit.json()["id"]
    user = client.post(
        "/api/v1/admin/users",
        headers=admin,
        json={"username": f"announcement{suffix}", "password": "UnitPassword123", "display_name": f"公告账号{suffix}", "role": "unit_user", "unit_id": unit_id, "must_change_password": False},
    )
    assert user.status_code == 200, user.text
    return unit.json(), login(client, f"announcement{suffix}", "UnitPassword123")


def _draft_payload(**changes):
    payload = {
        "title": "<script>alert(1)</script>重要通知",
        "content": "请各单位按要求完成下单。\n<script>window.bad=true</script>",
        "level": "important",
        "audience_type": "all",
        "unit_ids": [],
        "is_pinned": True,
        "client_request_id": "announcement-create-1",
    }
    payload.update(changes)
    return payload


def test_announcement_draft_edit_publish_offline_occ_idempotency_and_audit(tmp_path):
    client = make_client(tmp_path)
    admin = _admin(client)
    created = client.post("/api/v1/admin/announcements", headers=admin, json=_draft_payload())
    assert created.status_code == 200, created.text
    announcement = created.json()
    assert announcement["status"] == "draft"
    assert announcement["title"].startswith("<script>")

    updated = client.patch(
        f"/api/v1/admin/announcements/{announcement['id']}",
        headers=admin,
        json={**_draft_payload(title="9月采购安排", content="正文\n第二行", is_pinned=False, client_request_id="announcement-update-1"), "expected_version": announcement["version"]},
    )
    assert updated.status_code == 200, updated.text
    announcement = updated.json()
    stale = client.patch(
        f"/api/v1/admin/announcements/{announcement['id']}",
        headers=admin,
        json={**_draft_payload(title="过期写入", client_request_id="announcement-update-stale"), "expected_version": 1},
    )
    assert stale.status_code == 409

    publish_body = {"expected_version": announcement["version"], "client_request_id": "announcement-publish-1"}
    published = client.post(f"/api/v1/admin/announcements/{announcement['id']}/publish", headers=admin, json=publish_body)
    assert published.status_code == 200, published.text
    assert published.json()["status"] == "published"
    repeated = client.post(f"/api/v1/admin/announcements/{announcement['id']}/publish", headers=admin, json=publish_body)
    assert repeated.status_code == 200
    assert repeated.json()["version"] == published.json()["version"]

    offline_body = {"expected_version": published.json()["version"], "client_request_id": "announcement-offline-1"}
    offlined = client.post(f"/api/v1/admin/announcements/{announcement['id']}/offline", headers=admin, json=offline_body)
    assert offlined.status_code == 200, offlined.text
    assert offlined.json()["status"] == "offline"
    assert client.post(f"/api/v1/admin/announcements/{announcement['id']}/offline", headers=admin, json=offline_body).status_code == 200

    with connect() as conn:
        actions = {row["action"] for row in conn.execute("SELECT action FROM audit_logs WHERE object_id = ?", (announcement["id"],)).fetchall()}
    assert {"ANNOUNCEMENT_CREATED", "ANNOUNCEMENT_UPDATED", "ANNOUNCEMENT_PIN_CHANGED", "ANNOUNCEMENT_PUBLISHED", "ANNOUNCEMENT_OFFLINED"} <= actions


def test_announcement_audience_schedule_expiry_and_non_admin_management_rejected(tmp_path):
    client = make_client(tmp_path)
    admin = _admin(client)
    unit_a, unit_a_headers = _create_unit_user(client, admin, "01")
    unit_b, unit_b_headers = _create_unit_user(client, admin, "02")

    specific = client.post(
        "/api/v1/admin/announcements",
        headers=admin,
        json=_draft_payload(title="指定单位公告", audience_type="specific_units", unit_ids=[unit_a["id"]], client_request_id="announcement-specific"),
    )
    assert specific.status_code == 200, specific.text
    published = client.post(
        f"/api/v1/admin/announcements/{specific.json()['id']}/publish",
        headers=admin,
        json={"expected_version": specific.json()["version"], "client_request_id": "announcement-specific-publish"},
    )
    assert published.status_code == 200
    assert [item["id"] for item in client.get("/api/v1/announcements", headers=unit_a_headers).json()["items"]] == [specific.json()["id"]]
    assert client.get(f"/api/v1/announcements/{specific.json()['id']}", headers=unit_a_headers).status_code == 200
    assert client.get("/api/v1/announcements", headers=unit_b_headers).json()["items"] == []
    assert client.get(f"/api/v1/announcements/{specific.json()['id']}", headers=unit_b_headers).status_code == 404
    assert client.post("/api/v1/admin/announcements", headers=unit_a_headers, json=_draft_payload(client_request_id="unit-denied")).status_code == 403

    admins_only = client.post("/api/v1/admin/announcements", headers=admin, json=_draft_payload(title="仅管理员", audience_type="admins", client_request_id="announcement-admins"))
    units_only = client.post("/api/v1/admin/announcements", headers=admin, json=_draft_payload(title="全部子单位", audience_type="units", client_request_id="announcement-units"))
    assert admins_only.status_code == 200 and units_only.status_code == 200
    assert client.post(f"/api/v1/admin/announcements/{admins_only.json()['id']}/publish", headers=admin, json={"expected_version": admins_only.json()["version"], "client_request_id": "announcement-admins-publish"}).status_code == 200
    assert client.post(f"/api/v1/admin/announcements/{units_only.json()['id']}/publish", headers=admin, json={"expected_version": units_only.json()["version"], "client_request_id": "announcement-units-publish"}).status_code == 200
    admin_ids = {item["id"] for item in client.get("/api/v1/announcements", headers=admin).json()["items"]}
    unit_ids = {item["id"] for item in client.get("/api/v1/announcements", headers=unit_a_headers).json()["items"]}
    assert admins_only.json()["id"] in admin_ids and admins_only.json()["id"] not in unit_ids
    assert units_only.json()["id"] not in admin_ids and units_only.json()["id"] in unit_ids

    future = client.post(
        "/api/v1/admin/announcements",
        headers=admin,
        json=_draft_payload(title="定时公告", publish_at=(datetime.now(timezone.utc) + timedelta(days=1)).isoformat(), client_request_id="announcement-future"),
    )
    assert future.status_code == 200
    assert client.post(f"/api/v1/admin/announcements/{future.json()['id']}/publish", headers=admin, json={"expected_version": future.json()["version"], "client_request_id": "announcement-future-publish"}).status_code == 200
    assert all(item["id"] != future.json()["id"] for item in client.get("/api/v1/announcements", headers=admin).json()["items"])

    expired = client.post(
        "/api/v1/admin/announcements",
        headers=admin,
        json=_draft_payload(title="失效公告", expire_at=(datetime.now(timezone.utc) + timedelta(minutes=1)).isoformat(), client_request_id="announcement-expired"),
    )
    assert expired.status_code == 200
    assert client.post(f"/api/v1/admin/announcements/{expired.json()['id']}/publish", headers=admin, json={"expected_version": expired.json()["version"], "client_request_id": "announcement-expired-publish"}).status_code == 200
    with connect() as conn:
        conn.execute("UPDATE announcements SET expire_at = datetime('now', '-1 second') WHERE id = ?", (expired.json()["id"],))
        conn.commit()
    visible = client.get("/api/v1/announcements", headers=admin).json()["items"]
    assert all(item["id"] != expired.json()["id"] for item in visible)


def test_announcement_validation_dashboard_and_existing_business_paths(tmp_path):
    client = make_client(tmp_path)
    admin, _, _, _ = create_unit_user_product_order(client)
    invalid = client.post(
        "/api/v1/admin/announcements",
        headers=admin,
        json=_draft_payload(publish_at="2026-09-02T10:00", expire_at="2026-09-02T09:00", client_request_id="invalid-time"),
    )
    assert invalid.status_code == 400
    missing_unit = client.post(
        "/api/v1/admin/announcements",
        headers=admin,
        json=_draft_payload(audience_type="specific_units", unit_ids=["missing"], client_request_id="missing-unit"),
    )
    assert missing_unit.status_code == 404

    created = client.post("/api/v1/admin/announcements", headers=admin, json=_draft_payload(title="工作台公告", level="urgent", client_request_id="dashboard-announcement"))
    assert created.status_code == 200
    assert client.post(f"/api/v1/admin/announcements/{created.json()['id']}/publish", headers=admin, json={"expected_version": created.json()["version"], "client_request_id": "dashboard-announcement-publish"}).status_code == 200
    visible = client.get("/api/v1/announcements", headers=admin).json()["items"]
    assert visible[0]["title"] == "工作台公告"
    assert client.get("/api/v1/admin/dashboard", headers=admin).status_code == 200
    assert client.get("/api/v1/admin/orders", headers=admin).status_code == 200
    assert client.get("/api/v1/admin/units", headers=admin).status_code == 200
    dashboard_js = Path(__file__).resolve().parents[1] / "app" / "static" / "admin" / "dashboard.js"
    source = dashboard_js.read_text(encoding="utf-8")
    assert "html(item.title)" in source
    assert "html(item.content).replaceAll" in source
    assert '"/admin/announcements"' in source
    assert "urgentAnnouncementBanner" in source
