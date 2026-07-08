from __future__ import annotations

from locust.exception import StopUser


def login(client, username: str, password: str) -> str:
    if not password:
        raise StopUser("LOADTEST password is not configured")
    response = client.post(
        "/api/v1/auth/login",
        json={"username": username, "password": password},
        name="auth_login",
    )
    if response.status_code != 200:
        raise StopUser(f"login failed for {username}: HTTP {response.status_code} {response.text[:200]}")
    token = response.json().get("token", "")
    if not token:
        raise StopUser(f"login response missing token for {username}")
    return token
