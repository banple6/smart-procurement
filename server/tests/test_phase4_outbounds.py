from concurrent.futures import ThreadPoolExecutor
from io import BytesIO
from zipfile import ZipFile

from fastapi.testclient import TestClient
from openpyxl import load_workbook
from PIL import Image

from test_phase3_batches import _create_preparing_order, _create_product, _create_unit_user
from test_workflows import login, make_client


def _png_bytes() -> bytes:
    stream = BytesIO()
    Image.new("RGB", (64, 48), "green").save(stream, format="PNG")
    return stream.getvalue()


def _setup_closed_batch(client, admin_headers):
    product_jin = _create_product(client, admin_headers, "PH4-POTATO-JIN", "Phase4土豆", "斤")
    product_box = _create_product(client, admin_headers, "PH4-POTATO-BOX", "Phase4土豆", "箱")
    unit_a = _create_unit_user(client, admin_headers, "PH4A")
    unit_b = _create_unit_user(client, admin_headers, "PH4B")
    unit_c = _create_unit_user(client, admin_headers, "PH4C")
    orders = [
        _create_preparing_order(client, admin_headers, unit_a, product_jin, 3),
        _create_preparing_order(client, admin_headers, unit_a, product_jin, 2),
        _create_preparing_order(client, admin_headers, unit_b, product_jin, 7),
        _create_preparing_order(client, admin_headers, unit_b, product_box, 2),
        _create_preparing_order(client, admin_headers, unit_c, product_jin, 4),
    ]
    batch = client.post(
        "/api/v1/admin/batches",
        headers=admin_headers,
        json={"name": "Phase4单位出库", "note": "隔离测试", "order_ids": [order["id"] for order in orders]},
    )
    assert batch.status_code == 200, batch.text
    closed = client.patch(
        f"/api/v1/admin/batches/{batch.json()['id']}/status",
        headers=admin_headers,
        json={"status": "closed", "expected_version": batch.json()["version"]},
    )
    assert closed.status_code == 200, closed.text
    return batch.json(), orders, (unit_a, unit_b, unit_c), (product_jin, product_box)


def _generate(client, admin_headers, batch_id):
    response = client.post(f"/api/v1/admin/outbounds/from-batch/{batch_id}", headers=admin_headers)
    assert response.status_code == 200, response.text
    return response.json()


def test_phase4_outbounds_split_units_export_and_keep_snapshot(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, _, products = _setup_closed_batch(client, admin_headers)

    generated = _generate(client, admin_headers, batch["id"])
    assert generated["created_count"] == 3
    assert len(generated["items"]) == 3
    by_name = {item["unit_name_snapshot"]: item for item in generated["items"]}
    unit_a = next(item for item in generated["items"] if item["order_count"] == 2)
    detail = client.get(f"/api/v1/admin/outbounds/{unit_a['id']}", headers=admin_headers)
    assert detail.status_code == 200, detail.text
    body = detail.json()
    assert len(body["orders"]) == 2
    assert {order["id"] for order in body["orders"]} == {orders[0]["id"], orders[1]["id"]}
    assert [(line["quantity"], line["unit"]) for line in body["lines"]] == [("5", "斤")]

    summary = client.get(f"/api/v1/admin/batches/{batch['id']}/summary", headers=admin_headers).json()
    assert sum(float(line["quantity"]) for item in generated["items"] for line in client.get(f"/api/v1/admin/outbounds/{item['id']}", headers=admin_headers).json()["lines"] if line["unit"] == "斤") == sum(float(item["total_quantity"]) for item in summary["by_product"] if item["unit"] == "斤")

    current = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()
    changed = client.patch(f"/api/v1/admin/products/{products[0]}/price", headers=admin_headers, json={"price_cents": 999, "expected_version": current["version"]})
    assert changed.status_code == 200, changed.text
    exported = client.get(f"/api/v1/admin/outbounds/{unit_a['id']}/export.xlsx", headers=admin_headers)
    assert exported.status_code == 200, exported.text
    assert len(exported.content) > 0
    workbook = load_workbook(BytesIO(exported.content), data_only=True)
    sheet = workbook["出库单"]
    assert sheet.cell(1, 1).value == "三公鲜配出库单"
    assert unit_a["outbound_no"] in " ".join(str(cell.value or "") for cell in sheet[2])
    values = [str(cell.value or "") for row in sheet.iter_rows(values_only=False) for cell in row]
    assert "分类金额汇总" not in values
    assert any(value.startswith("出库人：") for value in values)
    assert any(value.startswith("收货人：") for value in values)

    bulk = client.get("/api/v1/admin/outbounds/bulk.zip?" + "&".join(f"outbound_ids={item['id']}" for item in generated["items"]), headers=admin_headers)
    assert bulk.status_code == 200, bulk.text
    with ZipFile(BytesIO(bulk.content)) as archive:
        assert len(archive.namelist()) == 3
        for filename in archive.namelist():
            assert filename.endswith(".xlsx")
            assert load_workbook(BytesIO(archive.read(filename)), read_only=True).sheetnames == ["出库单"]

    repeated = _generate(client, admin_headers, batch["id"])
    assert repeated["created_count"] == 0
    assert {item["id"] for item in repeated["items"]} == {item["id"] for item in generated["items"]}
    assert by_name


def test_phase4_rejects_open_batch_and_is_concurrent_safe(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    product_id = _create_product(client, admin_headers, "PH4-OPEN", "Phase4白菜", "斤")
    unit = _create_unit_user(client, admin_headers, "PH4OPEN")
    order = _create_preparing_order(client, admin_headers, unit, product_id, 2)
    batch = client.post("/api/v1/admin/batches", headers=admin_headers, json={"name": "未完成备货", "order_ids": [order["id"]]}).json()
    assert client.post(f"/api/v1/admin/outbounds/from-batch/{batch['id']}", headers=admin_headers).status_code == 409
    client.patch(f"/api/v1/admin/batches/{batch['id']}/status", headers=admin_headers, json={"status": "closed", "expected_version": batch["version"]})

    from app.main import app

    client_b = TestClient(app)
    headers_b = login(client_b, "root_admin", "StrongPassword123")
    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(executor.map(lambda args: args[0].post(f"/api/v1/admin/outbounds/from-batch/{batch['id']}", headers=args[1]).status_code, [(client, admin_headers), (client_b, headers_b)]))
    assert sorted(results) == [200, 200]
    listed = client.get("/api/v1/admin/outbounds", headers=admin_headers).json()["items"]
    assert len(listed) == 1


def test_phase4_ships_only_selected_unit_once_and_preserves_receipt_flow(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    batch, orders, unit_headers, products = _setup_closed_batch(client, admin_headers)
    generated = _generate(client, admin_headers, batch["id"])
    unit_a = next(item for item in generated["items"] if item["order_count"] == 2)
    before = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()
    payload = {
        "data": {"note": "Phase4 发货核对", "client_request_id": "phase4-ship-a"},
        "files": [("photos", ("proof.png", _png_bytes(), "image/png"))],
    }
    shipped = client.post(f"/api/v1/admin/outbounds/{unit_a['id']}/ship", headers=admin_headers, **payload)
    assert shipped.status_code == 200, shipped.text
    assert shipped.json()["status"] == "shipped"
    statuses = {order["id"]: client.get(f"/api/v1/admin/orders/{order['id']}", headers=admin_headers).json()["status"] for order in orders}
    assert statuses[orders[0]["id"]] == "shipped"
    assert statuses[orders[1]["id"]] == "shipped"
    assert statuses[orders[2]["id"]] == "preparing"
    assert statuses[orders[3]["id"]] == "preparing"
    assert statuses[orders[4]["id"]] == "preparing"
    assert all(client.get(f"/api/v1/admin/orders/{order['id']}", headers=admin_headers).json()["shipping_photo_count"] == 1 for order in orders[:2])
    after_ship = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()
    assert (after_ship["stock_quantity"], after_ship["reserved_quantity"]) == (before["stock_quantity"], before["reserved_quantity"])
    retry = client.post(f"/api/v1/admin/outbounds/{unit_a['id']}/ship", headers=admin_headers, **payload)
    assert retry.status_code == 200, retry.text
    assert client.get(f"/api/v1/admin/orders/{orders[0]['id']}", headers=admin_headers).json()["shipping_photo_count"] == 1

    receipt = client.post(f"/api/v1/orders/{orders[0]['id']}/confirm-receipt", headers=unit_headers[0])
    assert receipt.status_code == 200, receipt.text
    assert receipt.json()["status"] == "completed"
    after_receipt = client.get(f"/api/v1/products/{products[0]}", headers=admin_headers).json()
    assert after_receipt["stock_quantity"] == "997"
    assert after_receipt["reserved_quantity"] == "13"

    assert client.delete(f"/api/v1/admin/outbounds/{unit_a['id']}", headers=admin_headers).status_code == 409
    pending = next(item for item in generated["items"] if item["id"] != unit_a["id"])
    archived = client.delete(f"/api/v1/admin/outbounds/{pending['id']}", headers=admin_headers)
    assert archived.status_code == 200, archived.text
    assert archived.json()["status"] == "archived"
