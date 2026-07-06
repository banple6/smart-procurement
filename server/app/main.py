import os
import time
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI, HTTPException, Request, Response
from fastapi.staticfiles import StaticFiles

from .database import connect, init_db, one, private_upload_dir, transaction, upload_dir
from .metrics import record_request
from .routers import app_update, auth, dashboard, ledger, orders, procurement, products, system, unit_web, units, web_auth
from .security import hash_password
from .web import router as web_router

app = FastAPI(title="生鲜后勤 API", version="1.0.0")


def production_web_origin_ready() -> bool:
    return os.getenv("APP_ENV") != "production" or os.getenv("WEB_PUBLIC_ORIGIN", "").startswith("https://")


@app.middleware("http")
async def security_headers(request: Request, call_next):
    started = time.monotonic()
    try:
        response = await call_next(request)
    except Exception as exc:
        record_request(500, int((time.monotonic() - started) * 1000), exc)
        raise
    record_request(response.status_code, int((time.monotonic() - started) * 1000))
    response.headers.setdefault("X-Content-Type-Options", "nosniff")
    response.headers.setdefault("X-Frame-Options", "DENY")
    response.headers.setdefault("Referrer-Policy", "no-referrer")
    response.headers.setdefault("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
    response.headers.setdefault(
        "Content-Security-Policy",
        "default-src 'self'; img-src 'self' data:; script-src 'self'; style-src 'self' 'unsafe-inline'; "
        "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'",
    )
    return response


def seed_initial_admin():
    username = os.getenv("INITIAL_ADMIN_USERNAME", "")
    password = os.getenv("INITIAL_ADMIN_PASSWORD", "")
    if not username or not password:
        return
    with connect() as conn:
        existing = one(conn, "SELECT * FROM users WHERE role = 'admin' LIMIT 1")
        if existing:
            return
        conn.execute(
            """
            INSERT INTO users(
              id, username, password_hash, display_name, role, active, must_change_password,
              can_manage_accounts, can_issue_manager_invites, can_view_system_status,
              can_view_detailed_metrics, can_manage_backups, can_restore_backups
            )
            VALUES (?, ?, ?, ?, 'admin', 1, 1, 1, 1, 1, 1, 1, 1)
            """,
            (str(uuid4()), username, hash_password(password), "系统管理员"),
        )
        conn.commit()


@app.on_event("startup")
def startup():
    init_db()
    seed_initial_admin()


init_db()
seed_initial_admin()

api = FastAPI()
api.include_router(auth.router)
api.include_router(units.router)
api.include_router(products.router)
api.include_router(orders.router)
api.include_router(dashboard.router)
api.include_router(ledger.router)
api.include_router(procurement.router)
api.include_router(system.router)
api.include_router(web_auth.router)
api.include_router(web_auth.mobile_router)
api.include_router(app_update.router)


@api.get("/health")
def health():
    return {"status": "ok"}


@api.head("/health")
def health_head():
    return Response(status_code=200)


@api.get("/health/ready")
def ready():
    if not production_web_origin_ready():
        raise HTTPException(status_code=503, detail="生产环境缺少 HTTPS WEB_PUBLIC_ORIGIN，扫码登录模块未就绪")
    Path(upload_dir()).mkdir(parents=True, exist_ok=True)
    Path(private_upload_dir()).mkdir(parents=True, exist_ok=True)
    with transaction() as conn:
        conn.execute("CREATE TEMP TABLE IF NOT EXISTS readiness_probe(value INTEGER)")
        conn.execute("INSERT INTO readiness_probe(value) VALUES (1)")
        conn.execute("DELETE FROM readiness_probe")
    for root in (Path(upload_dir()), Path(private_upload_dir())):
        probe = root / ".ready"
        probe.write_text("ok")
        probe.unlink(missing_ok=True)
    return {"status": "ready"}


@api.head("/health/ready")
def ready_head():
    return Response(status_code=200)


app.mount("/api/v1", api)
app.include_router(unit_web.router)
app.include_router(web_router)
Path(upload_dir()).mkdir(parents=True, exist_ok=True)
Path(private_upload_dir()).mkdir(parents=True, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=upload_dir()), name="uploads")
app.mount("/admin-assets", StaticFiles(directory=str((Path(__file__).resolve().parent / "static" / "admin"))), name="admin-assets")
app.mount("/unit-assets", StaticFiles(directory=str((Path(__file__).resolve().parent / "static" / "unit"))), name="unit-assets")
