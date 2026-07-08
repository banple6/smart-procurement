import os
import secrets
import time

from fastapi import Request, Response


CSRF_COOKIE = "csrf_token"
QR_BINDING_COOKIE = "jrxp_qr_binding"
DEV_WEB_SESSION_COOKIE = "jrxp_dev_session"
INSECURE_PROD_WEB_SESSION_COOKIE = "jrxp_session"
PROD_WEB_SESSION_COOKIE = "__Host-jrxp_session"


def is_production() -> bool:
    return os.getenv("APP_ENV") == "production"


def insecure_production_http_enabled() -> bool:
    return (
        is_production()
        and os.getenv("ALLOW_INSECURE_PRODUCTION_HTTP", "").lower() in {"1", "true", "yes", "on"}
        and not os.getenv("WEB_PUBLIC_ORIGIN", "").startswith("https://")
    )


def web_session_cookie_name() -> str:
    if insecure_production_http_enabled():
        return INSECURE_PROD_WEB_SESSION_COOKIE
    return PROD_WEB_SESSION_COOKIE if is_production() else DEV_WEB_SESSION_COOKIE


def secure_cookie_enabled(request: Request) -> bool:
    if insecure_production_http_enabled():
        return request.headers.get("x-forwarded-proto", "").lower() == "https"
    if is_production():
        return True
    return request.headers.get("x-forwarded-proto", "").lower() == "https"


def web_idle_seconds() -> int:
    return int(os.getenv("WEB_SESSION_IDLE_MINUTES", "30")) * 60


def web_absolute_seconds() -> int:
    return int(os.getenv("WEB_SESSION_ABSOLUTE_HOURS", "8")) * 3600


def qr_ttl_seconds() -> int:
    return int(os.getenv("WEB_QR_TTL_SECONDS", "120"))


def set_qr_binding_cookie(response: Response, request: Request, binding: str):
    response.set_cookie(
        QR_BINDING_COOKIE,
        binding,
        max_age=qr_ttl_seconds(),
        httponly=True,
        secure=secure_cookie_enabled(request),
        samesite="strict",
        path="/api/v1/web-auth",
    )


def clear_qr_binding_cookie(response: Response):
    response.delete_cookie(QR_BINDING_COOKIE, path="/api/v1/web-auth", samesite="strict")


def set_web_cookies(response: Response, request: Request, token: str):
    max_age = web_absolute_seconds()
    secure = secure_cookie_enabled(request)
    response.set_cookie(
        web_session_cookie_name(),
        token,
        max_age=max_age,
        httponly=True,
        secure=secure,
        samesite="strict",
        path="/",
    )
    response.set_cookie(
        CSRF_COOKIE,
        secrets.token_urlsafe(32),
        max_age=max_age,
        httponly=False,
        secure=secure,
        samesite="strict",
        path="/",
    )


def clear_web_cookies(response: Response):
    response.delete_cookie(web_session_cookie_name(), path="/", samesite="strict")
    response.delete_cookie(PROD_WEB_SESSION_COOKIE, path="/", samesite="strict")
    response.delete_cookie(INSECURE_PROD_WEB_SESSION_COOKIE, path="/", samesite="strict")
    response.delete_cookie(DEV_WEB_SESSION_COOKIE, path="/", samesite="strict")
    response.delete_cookie(CSRF_COOKIE, path="/", samesite="strict")
    response.delete_cookie(CSRF_COOKIE, path="/api/v1", samesite="strict")


def session_expiry_epoch() -> int:
    return int(time.time()) + web_absolute_seconds()
