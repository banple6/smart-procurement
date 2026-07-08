from __future__ import annotations

from locust.exception import StopUser

from config import EXPECTED_ENVIRONMENT, EXPECTED_FINGERPRINT, EXPECTED_NAMESPACE


def verify_environment(client) -> dict:
    response = client.get("/api/v1/system/environment", name="environment_guard")
    if response.status_code != 200:
        raise StopUser(f"environment guard failed: HTTP {response.status_code}")
    body = response.json()
    if body.get("environment") != EXPECTED_ENVIRONMENT:
        raise StopUser(f"wrong environment: {body}")
    if body.get("load_test_allowed") is not True:
        raise StopUser(f"load test not allowed: {body}")
    if body.get("data_namespace") != EXPECTED_NAMESPACE:
        raise StopUser(f"wrong namespace: {body}")
    if EXPECTED_FINGERPRINT and body.get("database_fingerprint") != EXPECTED_FINGERPRINT:
        raise StopUser(f"wrong database fingerprint: {body}")
    return body
