import os
from datetime import datetime, timedelta, timezone
from time import perf_counter
from uuid import uuid4

from fastapi.testclient import TestClient


def make_client(tmp_path):
    os.environ["APP_ENV"] = "test"
    os.environ["APP_SECRET"] = "analytics-test-secret"
    os.environ["DATABASE_PATH"] = str(tmp_path / "analytics.db")
    os.environ["UPLOAD_DIR"] = str(tmp_path / "uploads")
    os.environ["PRIVATE_UPLOAD_DIR"] = str(tmp_path / "private_uploads")
    os.environ["INITIAL_ADMIN_USERNAME"] = "analytics_admin"
    os.environ["INITIAL_ADMIN_PASSWORD"] = "StrongPassword123"
    from app.database import init_db
    from app.main import app, seed_initial_admin

    init_db()
    seed_initial_admin()
    return TestClient(app)


def admin_headers(client):
    response = client.post(
        "/api/v1/auth/login",
        json={"username": "analytics_admin", "password": "StrongPassword123"},
    )
    assert response.status_code == 200, response.text
    return {"Authorization": f"Bearer {response.json()['token']}"}


def seed_analytics_data():
    from app.database import connect

    unit_a, unit_b = str(uuid4()), str(uuid4())
    product_a, product_b = str(uuid4()), str(uuid4())
    order_a, order_b, cancelled = str(uuid4()), str(uuid4()), str(uuid4())
    with connect() as conn:
        conn.executemany(
            "INSERT INTO units(id, unit_code, unit_name, default_delivery_point) VALUES (?, ?, ?, ?)",
            [(unit_a, "UA", "一食堂", "一食堂收货点"), (unit_b, "UB", "二食堂", "二食堂收货点")],
        )
        conn.executemany(
            """
            INSERT INTO products(id, product_code, name, category, spec, unit, price_cents, stock_quantity,
              reserved_quantity, warning_quantity, supply_status, active)
            VALUES (?, ?, ?, ?, '散装', ?, ?, ?, ?, ?, ?, 1)
            """,
            [
                (product_a, "P-A", "土豆", "蔬菜", "公斤", 600, "10", "2", "3", "normal"),
                (product_b, "P-B", "苹果", "水果", "箱", 5000, "0", "0", "1", "paused"),
            ],
        )
        conn.executemany(
            """
            INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot, status,
              total_cents, created_at, is_deleted)
            VALUES (?, ?, ?, ?, '收货点', ?, ?, ?, 0)
            """,
            [
                (order_a, "SP-A", unit_a, "一食堂", "completed", 1200, "2026-08-20 16:30:00"),
                (order_b, "SP-B", unit_b, "二食堂", "pending", 600, "2026-08-21 01:00:00"),
                (cancelled, "SP-C", unit_a, "一食堂", "cancelled", 9999, "2026-08-21 02:00:00"),
            ],
        )
        conn.executemany(
            """
            INSERT INTO order_items(id, order_id, product_id, product_code_snapshot, product_name_snapshot,
              category_snapshot, spec_snapshot, unit_snapshot, price_cents_snapshot, quantity,
              requested_quantity, actual_quantity, subtotal_cents)
            VALUES (?, ?, ?, ?, ?, ?, '散装', ?, ?, ?, ?, ?, ?)
            """,
            [
                (str(uuid4()), order_a, product_a, "P-A", "土豆", "蔬菜", "公斤", 400, "3", "3", "2", 800),
                (str(uuid4()), order_a, product_a, "P-A", "土豆", "蔬菜", "公斤", 400, "1", "1", "1", 400),
                (str(uuid4()), order_b, product_a, "P-A", "土豆", "蔬菜", "公斤", 600, "1", "1", "", 600),
                (str(uuid4()), cancelled, product_a, "P-A", "土豆", "蔬菜", "公斤", 9999, "1", "1", "1", 9999),
            ],
        )
        conn.executemany(
            """
            INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            [
                (str(uuid4()), product_a, None, 400, "2026-08-01 00:00:00"),
                (str(uuid4()), product_a, 400, 600, "2026-08-21 03:00:00"),
                (str(uuid4()), product_b, None, 5000, "2026-08-21 04:00:00"),
            ],
        )
        conn.commit()
    return unit_a, unit_b, product_a, product_b


def test_overview_uses_snapshots_actual_quantity_and_shanghai_days(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    seed_analytics_data()

    response = client.get(
        "/api/v1/admin/analytics/overview?start_date=2026-08-21&end_date=2026-08-21",
        headers=headers,
    )
    assert response.status_code == 200, response.text
    body = response.json()
    assert body["summary"]["valid_order_count"] == 2
    assert body["summary"]["total_cents"] == 1800
    assert body["summary"]["unit_count"] == 2
    assert body["trend"] == [{"date": "2026-08-21", "order_count": 2, "total_cents": 1800}]
    assert body["demand_rank"][0]["quantity"] == "4"
    assert body["comparison"]["total_cents_percent"] is None


def test_shanghai_day_includes_all_local_boundaries_and_excludes_neighbors(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    unit_a, _, product_a, _ = seed_analytics_data()
    from app.database import connect

    local_times = [
        ("2026-08-20 15:59:00", 100),  # 20日 23:59
        ("2026-08-20 16:01:00", 101),  # 21日 00:01
        ("2026-08-20 23:59:00", 102),  # 21日 07:59
        ("2026-08-21 00:00:00", 103),  # 21日 08:00
        ("2026-08-21 15:59:00", 104),  # 21日 23:59
        ("2026-08-21 16:00:00", 105),  # 22日 00:00
    ]
    with connect() as conn:
        for index, (created_at, amount) in enumerate(local_times):
            order_id = str(uuid4())
            conn.execute(
                """INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot,
                   status, total_cents, created_at, is_deleted) VALUES (?, ?, ?, '一食堂', '收货点',
                   'completed', ?, ?, 0)""",
                (order_id, f"BOUNDARY-{index}", unit_a, amount, created_at),
            )
            conn.execute(
                """INSERT INTO order_items(id, order_id, product_id, product_code_snapshot,
                   product_name_snapshot, category_snapshot, spec_snapshot, unit_snapshot,
                   price_cents_snapshot, quantity, requested_quantity, actual_quantity, subtotal_cents)
                   VALUES (?, ?, ?, 'P-A', '土豆', '蔬菜', '散装', '公斤', ?, '1', '1', '1', ?)""",
                (str(uuid4()), order_id, product_a, amount, amount),
            )
        conn.commit()

    body = client.get(
        "/api/v1/admin/analytics/overview?start_date=2026-08-21&end_date=2026-08-21&limit=50",
        headers=headers,
    ).json()
    # 原有 2 笔 + 本测试在 21 日的 4 笔。
    assert body["summary"]["valid_order_count"] == 6
    assert body["summary"]["total_cents"] == 1800 + 101 + 102 + 103 + 104
    assert len(body["demand_rank"]) <= 50


def test_overview_category_filter_uses_item_subtotals_and_units_do_not_duplicate_orders(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    unit_a, _, _, _ = seed_analytics_data()

    overview = client.get(
        "/api/v1/admin/analytics/overview?start_date=2026-08-21&end_date=2026-08-21&category=蔬菜",
        headers=headers,
    ).json()
    assert overview["summary"]["total_cents"] == 1800

    units = client.get(
        "/api/v1/admin/analytics/units?start_date=2026-08-21&end_date=2026-08-21&sort=amount",
        headers=headers,
    ).json()["items"]
    assert [item["total_cents"] for item in units] == [1200, 600]
    assert [item["order_count"] for item in units] == [1, 1]

    filtered = client.get(
        f"/api/v1/admin/analytics/overview?start_date=2026-08-21&end_date=2026-08-21&unit_id={unit_a}",
        headers=headers,
    ).json()
    assert filtered["summary"]["unit_count"] == 1
    assert filtered["summary"]["total_cents"] == 1200


def test_price_and_product_analytics_use_real_events(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    _, _, product_a, product_b = seed_analytics_data()

    prices = client.get(
        "/api/v1/admin/analytics/prices?start_date=2026-08-21&end_date=2026-08-21",
        headers=headers,
    ).json()["items"]
    potato = next(item for item in prices if item["product_id"] == product_a)
    apple = next(item for item in prices if item["product_id"] == product_b)
    assert potato["initial_price_cents"] == 400
    assert potato["current_price_cents"] == 600
    assert potato["change_percent"] == 50.0
    assert apple["is_new"] is True
    assert apple["change_percent"] is None

    detail = client.get(
        f"/api/v1/admin/analytics/products/{product_a}?start_date=2026-08-21&end_date=2026-08-21",
        headers=headers,
    )
    assert detail.status_code == 200, detail.text
    assert detail.json()["price_history"][0]["new_price_cents"] == 600
    assert sum(int(item["quantity"]) for item in detail.json()["demand_trend"]) == 4
    assert detail.json()["price"] == {
        "current_cents": 600,
        "range_start_cents": 400,
        "min_cents": 400,
        "max_cents": 600,
        "change_cents": 200,
        "change_percent": 50.0,
        "is_new": False,
    }
    assert detail.json()["period"]["amount_cents"] == 1800


def test_inventory_risk_and_decimal_shapes(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    _, _, product_a, product_b = seed_analytics_data()

    body = client.get("/api/v1/admin/analytics/inventory", headers=headers).json()
    potato = next(item for item in body["items"] if item["product_id"] == product_a)
    apple = next(item for item in body["items"] if item["product_id"] == product_b)
    assert potato["available_quantity"] == "8"
    assert potato["risk"] == "normal"
    assert apple["risk"] == "out_of_stock"
    assert apple["risk_level"] == "out_of_stock"
    assert apple["risk_text"] == "库存不足"
    assert isinstance(potato["average_daily_demand"], str)


def test_inventory_estimate_warning_and_no_demand(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    unit_a, _, _, product_b = seed_analytics_data()
    from app.database import connect

    order_id = str(uuid4())
    product_estimate = str(uuid4())
    now_utc = datetime.now(timezone.utc).replace(tzinfo=None).strftime("%Y-%m-%d %H:%M:%S")
    with connect() as conn:
        conn.execute(
            """INSERT INTO products(id, product_code, name, category, spec, unit, price_cents,
               stock_quantity, reserved_quantity, warning_quantity, supply_status, active)
               VALUES (?, 'P-ESTIMATE', '专项估算菜', '蔬菜', '散装', '公斤', 400,
               '280', '0', '300', 'normal', 1)""",
            (product_estimate,),
        )
        conn.execute(
            "UPDATE products SET stock_quantity='10', reserved_quantity='0', warning_quantity='0', supply_status='normal' WHERE id=?",
            (product_b,),
        )
        conn.execute(
            """INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot,
               status, total_cents, created_at, is_deleted) VALUES (?, 'ESTIMATE', ?, '一食堂', '收货点',
               'completed', 560000, ?, 0)""",
            (order_id, unit_a, now_utc),
        )
        conn.execute(
            """INSERT INTO order_items(id, order_id, product_id, product_code_snapshot,
               product_name_snapshot, category_snapshot, spec_snapshot, unit_snapshot,
               price_cents_snapshot, quantity, requested_quantity, actual_quantity, subtotal_cents)
               VALUES (?, ?, ?, 'P-A', '土豆', '蔬菜', '散装', '公斤', 400, '1400', '1400', '1400', 560000)""",
            (str(uuid4()), order_id, product_estimate),
        )
        conn.commit()

    items = client.get("/api/v1/admin/analytics/inventory", headers=headers).json()["items"]
    potato = next(item for item in items if item["product_id"] == product_estimate)
    apple = next(item for item in items if item["product_id"] == product_b)
    assert potato["available_quantity"] == "280"
    assert potato["average_daily_demand"] == "100"
    assert potato["estimated_days_available"] == "2.8"
    assert potato["risk"] == "warning"
    assert apple["estimated_days_available"] is None


def test_deleted_orders_and_different_units_never_merge(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    unit_a, _, product_a, _ = seed_analytics_data()
    from app.database import connect

    with connect() as conn:
        for suffix, item_unit, deleted in (("JIN", "斤", 0), ("DELETED", "公斤", 1)):
            order_id = str(uuid4())
            conn.execute(
                """INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot,
                   status, total_cents, created_at, is_deleted) VALUES (?, ?, ?, '一食堂', '收货点',
                   'completed', 100, '2026-08-21 02:30:00', ?)""",
                (order_id, suffix, unit_a, deleted),
            )
            conn.execute(
                """INSERT INTO order_items(id, order_id, product_id, product_code_snapshot,
                   product_name_snapshot, category_snapshot, spec_snapshot, unit_snapshot,
                   price_cents_snapshot, quantity, requested_quantity, actual_quantity, subtotal_cents)
                   VALUES (?, ?, ?, 'P-A', '土豆', '蔬菜', '散装', ?, 100, '2', '2', '2', 100)""",
                (str(uuid4()), order_id, product_a, item_unit),
            )
        conn.commit()

    body = client.get(
        "/api/v1/admin/analytics/overview?start_date=2026-08-21&end_date=2026-08-21",
        headers=headers,
    ).json()
    ranks = {(item["product_id"], item["unit"]): item["quantity"] for item in body["demand_rank"]}
    assert ranks[(product_a, "公斤")] == "4"
    assert ranks[(product_a, "斤")] == "2"
    assert body["summary"]["total_cents"] == 1900


def test_analytics_is_admin_only_and_validates_range(tmp_path):
    client = make_client(tmp_path)
    assert client.get("/api/v1/admin/analytics/overview").status_code == 401
    headers = admin_headers(client)
    invalid = client.get(
        "/api/v1/admin/analytics/overview?start_date=2026-08-22&end_date=2026-08-21",
        headers=headers,
    )
    assert invalid.status_code == 400


def test_analytics_web_assets_are_local_and_loadable(tmp_path):
    client = make_client(tmp_path)
    html = client.get("/admin-assets/dashboard.html")
    script = client.get("/admin-assets/analytics.js")
    chart = client.get("/admin-assets/vendor/echarts-5.6.0/echarts.min.js")
    assert html.status_code == script.status_code == chart.status_code == 200
    assert "/admin-assets/vendor/echarts-5.6.0/echarts.min.js" in html.text
    combined = html.text + script.text
    assert "cdn.jsdelivr" not in combined
    assert "unpkg" not in combined
    assert "cdnjs" not in combined


def test_analytics_performance_with_16_units_and_90_days(tmp_path):
    client = make_client(tmp_path)
    headers = admin_headers(client)
    from app.database import connect

    units = [(str(uuid4()), f"U{index:02d}", f"单位{index:02d}", f"收货点{index:02d}") for index in range(16)]
    products = [
        (str(uuid4()), f"P{index:03d}", f"食材{index:03d}", "蔬菜" if index % 2 == 0 else "水果", 100 + index)
        for index in range(40)
    ]
    orders = []
    items = []
    for day in range(90):
        created_at = (datetime(2026, 5, 24) + timedelta(days=day)).strftime("%Y-%m-%d 04:00:00")
        for unit_index, unit in enumerate(units):
            order_id = str(uuid4())
            orders.append((order_id, f"PERF-{day:03d}-{unit_index:02d}", unit[0], unit[2], 1000, created_at))
            for item_index in range(3):
                product = products[(day + unit_index + item_index) % len(products)]
                items.append((str(uuid4()), order_id, product[0], product[1], product[2], product[3], product[4]))
    with connect() as conn:
        conn.executemany(
            "INSERT INTO units(id, unit_code, unit_name, default_delivery_point) VALUES (?, ?, ?, ?)",
            units,
        )
        conn.executemany(
            """INSERT INTO products(id, product_code, name, category, spec, unit, price_cents,
               stock_quantity, reserved_quantity, warning_quantity, supply_status, active)
               VALUES (?, ?, ?, ?, '散装', '公斤', ?, '500', '20', '50', 'normal', 1)""",
            products,
        )
        conn.executemany(
            """INSERT INTO orders(id, order_no, unit_id, unit_name_snapshot, delivery_point_snapshot,
               status, total_cents, created_at, is_deleted) VALUES (?, ?, ?, ?, '收货点', 'completed', ?, ?, 0)""",
            orders,
        )
        conn.executemany(
            """INSERT INTO order_items(id, order_id, product_id, product_code_snapshot,
               product_name_snapshot, category_snapshot, spec_snapshot, unit_snapshot,
               price_cents_snapshot, quantity, requested_quantity, actual_quantity, subtotal_cents)
               VALUES (?, ?, ?, ?, ?, ?, '散装', '公斤', ?, '1', '1', '1', 333)""",
            items,
        )
        conn.executemany(
            "INSERT INTO product_price_logs(id, product_id, old_price_cents, new_price_cents, created_at) VALUES (?, ?, NULL, ?, '2026-05-01 00:00:00')",
            [(str(uuid4()), product[0], product[4]) for product in products],
        )
        conn.commit()

    endpoints = {
        "overview": "/api/v1/admin/analytics/overview?start_date=2026-05-24&end_date=2026-08-21&limit=50",
        "units": "/api/v1/admin/analytics/units?start_date=2026-05-24&end_date=2026-08-21&sort=amount",
        "prices": "/api/v1/admin/analytics/prices?start_date=2026-05-24&end_date=2026-08-21",
        "inventory": "/api/v1/admin/analytics/inventory",
    }
    timings = {}
    bodies = {}
    for name, endpoint in endpoints.items():
        started = perf_counter()
        response = client.get(endpoint, headers=headers)
        timings[name] = round((perf_counter() - started) * 1000, 1)
        assert response.status_code == 200, response.text
        assert timings[name] < 5000
        bodies[name] = response.json()

    assert bodies["overview"]["summary"]["valid_order_count"] == 1440
    assert len(bodies["units"]["items"]) == 16
    assert len(bodies["prices"]["items"]) == 40
    assert bodies["inventory"]["summary"]["product_count"] == 40
    print(f"analytics_performance_ms={timings}")
