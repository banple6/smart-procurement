from __future__ import annotations

import importlib


def test_unit_account_allocation_is_unique(monkeypatch):
    monkeypatch.setenv("LOADTEST_UNIT_USER_COUNT", "3")
    monkeypatch.setenv("LOADTEST_ADMIN_USER_COUNT", "2")
    import config

    importlib.reload(config)

    assert [config.claim_unit_username() for _ in range(3)] == [
        "LOADTEST_unit_01",
        "LOADTEST_unit_02",
        "LOADTEST_unit_03",
    ]


def test_admin_account_allocation_is_unique(monkeypatch):
    monkeypatch.setenv("LOADTEST_UNIT_USER_COUNT", "3")
    monkeypatch.setenv("LOADTEST_ADMIN_USER_COUNT", "2")
    import config

    importlib.reload(config)

    assert [config.claim_admin_username() for _ in range(2)] == [
        "LOADTEST_admin_01",
        "LOADTEST_admin_02",
    ]


def test_account_allocation_fails_when_pool_exhausted(monkeypatch):
    monkeypatch.setenv("LOADTEST_UNIT_USER_COUNT", "1")
    monkeypatch.setenv("LOADTEST_ADMIN_USER_COUNT", "1")
    import config

    importlib.reload(config)
    config.claim_unit_username()
    config.claim_admin_username()

    try:
        config.claim_unit_username()
    except RuntimeError as exc:
        assert "unit_user" in str(exc)
    else:
        raise AssertionError("unit account exhaustion did not fail")

    try:
        config.claim_admin_username()
    except RuntimeError as exc:
        assert "admin" in str(exc)
    else:
        raise AssertionError("admin account exhaustion did not fail")
