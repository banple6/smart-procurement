from test_new_business_features import create_product, create_unit_order
from test_workflows import login, make_client


def test_admin_order_list_and_detail_expose_the_authoritative_note(tmp_path):
    client = make_client(tmp_path)
    admin_headers = login(client, "root_admin", "StrongPassword123")
    product = create_product(client, admin_headers, code="REMARK-VISIBILITY", name="备注青菜", unit="斤")
    order = create_unit_order(client, admin_headers, product["id"], "REMARK-UNIT", "1")

    from app.database import connect

    note = "青菜要嫩一点，八角不要替换品牌"
    with connect() as conn:
        conn.execute("UPDATE orders SET note = ? WHERE id = ?", (note, order["id"]))

    listing = client.get("/api/v1/admin/orders", headers=admin_headers)
    assert listing.status_code == 200, listing.text
    listed = next(item for item in listing.json()["items"] if item["id"] == order["id"])
    assert listed["note"] == note

    detail = client.get(f"/api/v1/admin/orders/{order['id']}", headers=admin_headers)
    assert detail.status_code == 200, detail.text
    assert detail.json()["note"] == note
