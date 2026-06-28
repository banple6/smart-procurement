import os
from pathlib import Path
from uuid import uuid4

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .database import connect, init_db, one, upload_dir
from .routers import auth, dashboard, ledger, orders, products, units
from .security import hash_password

app = FastAPI(title="智慧后勤采购 API", version="1.0.0")


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
            INSERT INTO users(id, username, password_hash, display_name, role, active, must_change_password)
            VALUES (?, ?, ?, ?, 'admin', 1, 1)
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


@api.get("/health")
def health():
    return {"status": "ok"}


app.mount("/api/v1", api)
Path(upload_dir()).mkdir(parents=True, exist_ok=True)
app.mount("/uploads", StaticFiles(directory=upload_dir()), name="uploads")

