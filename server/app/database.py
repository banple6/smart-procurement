import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path


def database_path() -> str:
    return os.getenv("DATABASE_PATH", str(Path(__file__).resolve().parents[1] / "data" / "smart_procurement.db"))


def upload_dir() -> str:
    return os.getenv("UPLOAD_DIR", str(Path(__file__).resolve().parents[1] / "uploads"))


def connect() -> sqlite3.Connection:
    path = Path(database_path())
    path.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(path, timeout=10, check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode = WAL")
    conn.execute("PRAGMA foreign_keys = ON")
    conn.execute("PRAGMA busy_timeout = 10000")
    return conn


@contextmanager
def transaction():
    conn = connect()
    try:
        conn.execute("BEGIN IMMEDIATE")
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def one(conn: sqlite3.Connection, sql: str, params=()):
    row = conn.execute(sql, params).fetchone()
    return dict(row) if row else None


def all_rows(conn: sqlite3.Connection, sql: str, params=()):
    return [dict(row) for row in conn.execute(sql, params).fetchall()]


def column_names(conn: sqlite3.Connection, table: str) -> set[str]:
    return {row["name"] for row in conn.execute(f"PRAGMA table_info({table})")}


def add_column(conn: sqlite3.Connection, table: str, definition: str):
    name = definition.split()[0]
    if name not in column_names(conn, table):
        conn.execute(f"ALTER TABLE {table} ADD COLUMN {definition}")


def revoke_user_sessions(conn: sqlite3.Connection, user_id: str):
    conn.execute(
        "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL",
        (user_id,),
    )


def revoke_unit_sessions(conn: sqlite3.Connection, unit_id: str):
    conn.execute(
        """
        UPDATE sessions
        SET revoked_at = CURRENT_TIMESTAMP
        WHERE user_id IN (SELECT id FROM users WHERE unit_id = ?)
          AND revoked_at IS NULL
        """,
        (unit_id,),
    )


def ensure_core_schema(conn: sqlite3.Connection):
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS schema_migrations (
          version TEXT PRIMARY KEY,
          applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS units (
          id TEXT PRIMARY KEY,
          unit_code TEXT NOT NULL UNIQUE,
          unit_name TEXT NOT NULL,
          default_delivery_point TEXT NOT NULL DEFAULT '',
          active INTEGER NOT NULL DEFAULT 1,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS users (
          id TEXT PRIMARY KEY,
          username TEXT NOT NULL UNIQUE,
          password_hash TEXT NOT NULL,
          display_name TEXT NOT NULL,
          role TEXT NOT NULL CHECK(role IN ('admin', 'unit_user')),
          unit_id TEXT REFERENCES units(id),
          active INTEGER NOT NULL DEFAULT 1,
          must_change_password INTEGER NOT NULL DEFAULT 0,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS products (
          id TEXT PRIMARY KEY,
          product_code TEXT NOT NULL UNIQUE,
          name TEXT NOT NULL,
          category TEXT NOT NULL,
          spec TEXT NOT NULL,
          unit TEXT NOT NULL,
          price_cents INTEGER NOT NULL,
          stock_quantity TEXT NOT NULL DEFAULT '0',
          reserved_quantity TEXT NOT NULL DEFAULT '0',
          min_order_quantity TEXT NOT NULL DEFAULT '1',
          quantity_step TEXT NOT NULL DEFAULT '1',
          warning_quantity TEXT NOT NULL DEFAULT '0',
          origin TEXT NOT NULL DEFAULT '',
          supplier TEXT NOT NULL DEFAULT '',
          shelf_life TEXT NOT NULL DEFAULT '',
          storage_method TEXT NOT NULL DEFAULT '',
          description TEXT NOT NULL DEFAULT '',
          image_path TEXT NOT NULL DEFAULT '',
          supply_status TEXT NOT NULL DEFAULT 'normal',
          active INTEGER NOT NULL DEFAULT 1,
          is_deleted INTEGER NOT NULL DEFAULT 0,
          created_by TEXT REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS product_price_logs (
          id TEXT PRIMARY KEY,
          product_id TEXT NOT NULL REFERENCES products(id),
          old_price_cents INTEGER,
          new_price_cents INTEGER NOT NULL,
          actor_id TEXT REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS inventory_logs (
          id TEXT PRIMARY KEY,
          product_id TEXT NOT NULL REFERENCES products(id),
          order_id TEXT,
          action TEXT NOT NULL,
          quantity TEXT NOT NULL,
          detail TEXT NOT NULL DEFAULT '',
          actor_id TEXT REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS orders (
          id TEXT PRIMARY KEY,
          order_no TEXT NOT NULL UNIQUE,
          unit_id TEXT NOT NULL REFERENCES units(id),
          unit_name_snapshot TEXT NOT NULL,
          delivery_point_snapshot TEXT NOT NULL,
          note TEXT NOT NULL DEFAULT '',
          status TEXT NOT NULL DEFAULT 'pending',
          total_cents INTEGER NOT NULL DEFAULT 0,
          created_by TEXT REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          accepted_at TEXT,
          preparing_at TEXT,
          shipped_at TEXT,
          completed_at TEXT,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS order_items (
          id TEXT PRIMARY KEY,
          order_id TEXT NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
          product_id TEXT NOT NULL REFERENCES products(id),
          product_code_snapshot TEXT NOT NULL,
          product_name_snapshot TEXT NOT NULL,
          category_snapshot TEXT NOT NULL,
          spec_snapshot TEXT NOT NULL,
          unit_snapshot TEXT NOT NULL,
          price_cents_snapshot INTEGER NOT NULL,
          quantity TEXT NOT NULL,
          subtotal_cents INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS order_logs (
          id TEXT PRIMARY KEY,
          order_id TEXT NOT NULL REFERENCES orders(id),
          actor_id TEXT REFERENCES users(id),
          action TEXT NOT NULL,
          old_status TEXT,
          new_status TEXT,
          detail TEXT NOT NULL DEFAULT '',
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS sessions (
          id TEXT PRIMARY KEY,
          token_hash TEXT NOT NULL UNIQUE,
          user_id TEXT NOT NULL REFERENCES users(id),
          expires_at INTEGER NOT NULL,
          revoked_at TEXT,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          last_used_at TEXT,
          client_info TEXT NOT NULL DEFAULT '',
          ip_address TEXT NOT NULL DEFAULT ''
        );

        CREATE TABLE IF NOT EXISTS audit_logs (
          id TEXT PRIMARY KEY,
          actor_id TEXT,
          actor_role TEXT NOT NULL DEFAULT '',
          action TEXT NOT NULL,
          object_type TEXT NOT NULL,
          object_id TEXT NOT NULL DEFAULT '',
          before_json TEXT NOT NULL DEFAULT '',
          after_json TEXT NOT NULL DEFAULT '',
          ip_address TEXT NOT NULL DEFAULT '',
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          result TEXT NOT NULL DEFAULT 'success'
        );

        CREATE TABLE IF NOT EXISTS app_sequences (
          name TEXT PRIMARY KEY,
          value INTEGER NOT NULL DEFAULT 0
        );
        """
    )
    add_column(conn, "units", "address_note TEXT NOT NULL DEFAULT ''")
    add_column(conn, "users", "last_login_at TEXT")
    add_column(conn, "orders", "client_request_id TEXT")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_client_request_id ON orders(client_request_id) WHERE client_request_id IS NOT NULL")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_orders_unit_status_created ON orders(unit_id, status, created_at)")


def migrate() -> list[str]:
    Path(upload_dir()).mkdir(parents=True, exist_ok=True)
    applied: list[str] = []
    with transaction() as conn:
        ensure_core_schema(conn)
        existing = one(conn, "SELECT version FROM schema_migrations WHERE version = ?", ("0001_core_security_orders",))
        if not existing:
            conn.execute("INSERT INTO schema_migrations(version) VALUES (?)", ("0001_core_security_orders",))
            applied.append("0001_core_security_orders")
    return applied


def migration_status() -> dict:
    with connect() as conn:
        ensure_core_schema(conn)
        rows = all_rows(conn, "SELECT version, applied_at FROM schema_migrations ORDER BY version")
    applied = [row["version"] for row in rows]
    pending = [] if "0001_core_security_orders" in applied else ["0001_core_security_orders"]
    return {"applied": applied, "pending": pending}


def init_db():
    Path(upload_dir()).mkdir(parents=True, exist_ok=True)
    migrate()
