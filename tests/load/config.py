import os
from threading import Lock


EXPECTED_ENVIRONMENT = os.getenv("LOADTEST_EXPECTED_ENVIRONMENT", "loadtest")
EXPECTED_NAMESPACE = os.getenv("LOADTEST_EXPECTED_NAMESPACE", "LOADTEST")
EXPECTED_FINGERPRINT = os.getenv("LOADTEST_DATABASE_FINGERPRINT", "")
ADMIN_PASSWORD = os.getenv("LOADTEST_ADMIN_PASSWORD", os.getenv("LOADTEST_USER_PASSWORD", ""))
UNIT_PASSWORD = os.getenv("LOADTEST_UNIT_PASSWORD", os.getenv("LOADTEST_USER_PASSWORD", ""))
ADMIN_USER_COUNT = int(os.getenv("LOADTEST_ADMIN_USER_COUNT", "15"))
UNIT_USER_COUNT = int(os.getenv("LOADTEST_UNIT_USER_COUNT", "40"))
MAX_ORDERS_PER_USER = int(os.getenv("LOADTEST_MAX_ORDERS_PER_USER", "2"))

_admin_lock = Lock()
_unit_lock = Lock()
_admin_next = 1
_unit_next = 1


def admin_username(index: int) -> str:
    return f"LOADTEST_admin_{index:02d}"


def unit_username(index: int) -> str:
    return f"LOADTEST_unit_{index:02d}"


def claim_admin_username() -> str:
    global _admin_next
    with _admin_lock:
        if _admin_next > ADMIN_USER_COUNT:
            raise RuntimeError(f"LOADTEST admin account pool exhausted: {ADMIN_USER_COUNT}")
        username = admin_username(_admin_next)
        _admin_next += 1
        return username


def claim_unit_username() -> str:
    global _unit_next
    with _unit_lock:
        if _unit_next > UNIT_USER_COUNT:
            raise RuntimeError(f"LOADTEST unit_user account pool exhausted: {UNIT_USER_COUNT}")
        username = unit_username(_unit_next)
        _unit_next += 1
        return username
