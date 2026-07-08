from __future__ import annotations

import pytest

pytest.importorskip("locust")

from unit_user import UnitProcurementUser


def test_unit_user_order_limit_blocks_extra_submits(monkeypatch):
    calls = []
    user = object.__new__(UnitProcurementUser)
    user.created_orders = 1
    user.max_orders = 1
    user.product_cache = [{"id": "p1", "active": True, "supply_status": "normal", "available_quantity": "10"}]
    user.client = object()
    user.token = "token"

    monkeypatch.setattr("unit_user.submit_small_order", lambda *args: calls.append(args))

    UnitProcurementUser.create_order(user)

    assert calls == []
