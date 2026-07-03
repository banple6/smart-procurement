import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path


def database_path() -> str:
    return os.getenv("DATABASE_PATH", str(Path(__file__).resolve().parents[1] / "data" / "smart_procurement.db"))


def upload_dir() -> str:
    return os.getenv("UPLOAD_DIR", str(Path(__file__).resolve().parents[1] / "uploads"))


def private_upload_dir() -> str:
    return os.getenv("PRIVATE_UPLOAD_DIR", str(Path(__file__).resolve().parents[1] / "private_uploads"))


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


def table_exists(conn: sqlite3.Connection, table: str) -> bool:
    return one(conn, "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", (table,)) is not None


def revoke_user_sessions(conn: sqlite3.Connection, user_id: str):
    conn.execute(
        "UPDATE sessions SET revoked_at = CURRENT_TIMESTAMP WHERE user_id = ? AND revoked_at IS NULL",
        (user_id,),
    )
    if table_exists(conn, "web_sessions"):
        conn.execute(
            """
            UPDATE web_sessions
            SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = '账号会话已失效'
            WHERE user_id = ? AND revoked_at IS NULL
            """,
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
    if table_exists(conn, "web_sessions"):
        conn.execute(
            """
            UPDATE web_sessions
            SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = '所属单位已停用'
            WHERE user_id IN (SELECT id FROM users WHERE unit_id = ?)
              AND revoked_at IS NULL
            """,
            (unit_id,),
        )


def write_audit(
    conn: sqlite3.Connection,
    actor_id: str | None,
    actor_role: str,
    action: str,
    object_type: str,
    object_id: str = "",
    before_json: str = "",
    after_json: str = "",
    result: str = "success",
):
    from uuid import uuid4

    conn.execute(
        """
        INSERT INTO audit_logs(id, actor_id, actor_role, action, object_type, object_id, before_json, after_json, result)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (str(uuid4()), actor_id, actor_role, action, object_type, object_id, before_json, after_json, result),
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
          password_changed_at TEXT,
          failed_login_count INTEGER NOT NULL DEFAULT 0,
          locked_until TEXT,
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
          reason TEXT NOT NULL DEFAULT '',
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
          mode TEXT NOT NULL DEFAULT '',
          before_quantity TEXT NOT NULL DEFAULT '',
          after_quantity TEXT NOT NULL DEFAULT '',
          reserved_quantity TEXT NOT NULL DEFAULT '',
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
          shipping_note TEXT,
          ship_request_id TEXT,
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

        CREATE TABLE IF NOT EXISTS order_shipping_photos (
          id TEXT PRIMARY KEY,
          order_id TEXT NOT NULL REFERENCES orders(id),
          image_path TEXT NOT NULL,
          thumbnail_path TEXT NOT NULL,
          uploaded_by TEXT NOT NULL REFERENCES users(id),
          uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          source TEXT NOT NULL DEFAULT 'camera',
          mime_type TEXT NOT NULL,
          file_size INTEGER NOT NULL,
          width INTEGER NOT NULL,
          height INTEGER NOT NULL,
          sha256 TEXT NOT NULL
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
    add_column(conn, "users", "password_changed_at TEXT")
    add_column(conn, "users", "failed_login_count INTEGER NOT NULL DEFAULT 0")
    add_column(conn, "users", "locked_until TEXT")
    add_column(conn, "orders", "client_request_id TEXT")
    add_column(conn, "orders", "shipping_note TEXT")
    add_column(conn, "orders", "ship_request_id TEXT")
    add_column(conn, "product_price_logs", "reason TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "mode TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "before_quantity TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "after_quantity TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "reserved_quantity TEXT NOT NULL DEFAULT ''")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_client_request_id ON orders(client_request_id) WHERE client_request_id IS NOT NULL")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_ship_request_id ON orders(ship_request_id) WHERE ship_request_id IS NOT NULL")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_sessions_token_hash ON sessions(token_hash)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_orders_unit_status_created ON orders(unit_id, status, created_at)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_shipping_photos_order_id ON order_shipping_photos(order_id)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users(LOWER(username))")


def apply_shipping_photos_migration(conn: sqlite3.Connection):
    add_column(conn, "orders", "shipping_note TEXT")
    add_column(conn, "orders", "ship_request_id TEXT")
    conn.execute(
        """
        CREATE TABLE IF NOT EXISTS order_shipping_photos (
          id TEXT PRIMARY KEY,
          order_id TEXT NOT NULL REFERENCES orders(id),
          image_path TEXT NOT NULL,
          thumbnail_path TEXT NOT NULL,
          uploaded_by TEXT NOT NULL REFERENCES users(id),
          uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          source TEXT NOT NULL DEFAULT 'camera',
          mime_type TEXT NOT NULL,
          file_size INTEGER NOT NULL,
          width INTEGER NOT NULL,
          height INTEGER NOT NULL,
          sha256 TEXT NOT NULL
        )
        """
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_shipping_photos_order_id ON order_shipping_photos(order_id)")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_orders_ship_request_id ON orders(ship_request_id) WHERE ship_request_id IS NOT NULL")


def apply_product_management_migration(conn: sqlite3.Connection):
    add_column(conn, "product_price_logs", "reason TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "mode TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "before_quantity TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "after_quantity TEXT NOT NULL DEFAULT ''")
    add_column(conn, "inventory_logs", "reserved_quantity TEXT NOT NULL DEFAULT ''")


def apply_account_security_migration(conn: sqlite3.Connection):
    add_column(conn, "users", "password_changed_at TEXT")
    add_column(conn, "users", "failed_login_count INTEGER NOT NULL DEFAULT 0")
    add_column(conn, "users", "locked_until TEXT")
    conn.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username_lower ON users(LOWER(username))")


def apply_procurement_operations_migration(conn: sqlite3.Connection):
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS procurement_settings (
          id INTEGER PRIMARY KEY CHECK(id = 1),
          cutoff_enabled INTEGER NOT NULL DEFAULT 1,
          cutoff_time TEXT NOT NULL DEFAULT '16:00',
          updated_by TEXT REFERENCES users(id),
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS procurement_cutoff_overrides (
          business_date TEXT PRIMARY KEY,
          cutoff_enabled INTEGER NOT NULL DEFAULT 1,
          cutoff_time TEXT NOT NULL DEFAULT '16:00',
          note TEXT NOT NULL DEFAULT '',
          updated_by TEXT REFERENCES users(id),
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS order_item_adjustments (
          id TEXT PRIMARY KEY,
          order_id TEXT NOT NULL REFERENCES orders(id),
          order_item_id TEXT NOT NULL REFERENCES order_items(id),
          product_id TEXT NOT NULL REFERENCES products(id),
          before_actual_quantity TEXT NOT NULL,
          after_actual_quantity TEXT NOT NULL,
          reason TEXT NOT NULL,
          actor_id TEXT NOT NULL REFERENCES users(id),
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS receipt_issues (
          id TEXT PRIMARY KEY,
          order_id TEXT NOT NULL REFERENCES orders(id),
          unit_id TEXT NOT NULL REFERENCES units(id),
          issue_type TEXT NOT NULL,
          description TEXT NOT NULL DEFAULT '',
          status TEXT NOT NULL DEFAULT 'open',
          reported_by TEXT NOT NULL REFERENCES users(id),
          reported_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          resolved_by TEXT REFERENCES users(id),
          resolved_at TEXT,
          resolution_note TEXT NOT NULL DEFAULT ''
        );

        CREATE TABLE IF NOT EXISTS receipt_issue_photos (
          id TEXT PRIMARY KEY,
          issue_id TEXT NOT NULL REFERENCES receipt_issues(id) ON DELETE CASCADE,
          image_path TEXT NOT NULL,
          thumbnail_path TEXT NOT NULL,
          uploaded_by TEXT NOT NULL REFERENCES users(id),
          uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          source TEXT NOT NULL DEFAULT 'camera',
          mime_type TEXT NOT NULL,
          file_size INTEGER NOT NULL,
          width INTEGER NOT NULL,
          height INTEGER NOT NULL,
          sha256 TEXT NOT NULL
        );
        """
    )
    add_column(conn, "order_items", "requested_quantity TEXT")
    add_column(conn, "order_items", "actual_quantity TEXT")
    add_column(conn, "order_items", "adjustment_reason TEXT NOT NULL DEFAULT ''")
    add_column(conn, "order_items", "adjusted_by TEXT REFERENCES users(id)")
    add_column(conn, "order_items", "adjusted_at TEXT")
    conn.execute("UPDATE order_items SET requested_quantity = quantity WHERE requested_quantity IS NULL OR requested_quantity = ''")
    conn.execute("UPDATE order_items SET actual_quantity = quantity WHERE actual_quantity IS NULL OR actual_quantity = ''")
    conn.execute("INSERT OR IGNORE INTO procurement_settings(id, cutoff_enabled, cutoff_time) VALUES (1, 1, '16:00')")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_orders_created_status_unit ON orders(created_at, status, unit_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_order_items_order_product ON order_items(order_id, product_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_receipt_issues_order_status ON receipt_issues(order_id, status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_receipt_issues_unit_status ON receipt_issues(unit_id, status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_receipt_issue_photos_issue_id ON receipt_issue_photos(issue_id)")


def apply_web_qr_login_migration(conn: sqlite3.Connection):
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS web_login_challenges (
          id TEXT PRIMARY KEY,
          qr_token_hash TEXT NOT NULL UNIQUE,
          browser_binding_hash TEXT NOT NULL,
          status TEXT NOT NULL CHECK(status IN ('pending', 'scanned', 'approved', 'rejected', 'consumed')) DEFAULT 'pending',
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          expires_at TEXT NOT NULL,
          scanned_at TEXT,
          approved_at TEXT,
          rejected_at TEXT,
          consumed_at TEXT,
          scanned_by_user_id TEXT REFERENCES users(id),
          approved_by_user_id TEXT REFERENCES users(id),
          app_session_id TEXT,
          browser_user_agent TEXT NOT NULL DEFAULT '',
          browser_name TEXT NOT NULL DEFAULT '',
          browser_os TEXT NOT NULL DEFAULT '',
          browser_ip TEXT NOT NULL DEFAULT '',
          device_name TEXT NOT NULL DEFAULT '',
          app_version TEXT NOT NULL DEFAULT '',
          role_snapshot TEXT NOT NULL DEFAULT '',
          unit_id_snapshot TEXT,
          updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
        );

        CREATE TABLE IF NOT EXISTS web_sessions (
          id TEXT PRIMARY KEY,
          token_hash TEXT NOT NULL UNIQUE,
          user_id TEXT NOT NULL REFERENCES users(id),
          role TEXT NOT NULL,
          unit_id TEXT,
          created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          last_seen_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
          idle_expires_at TEXT NOT NULL,
          absolute_expires_at TEXT NOT NULL,
          revoked_at TEXT,
          revoked_reason TEXT NOT NULL DEFAULT '',
          browser_user_agent TEXT NOT NULL DEFAULT '',
          browser_name TEXT NOT NULL DEFAULT '',
          browser_os TEXT NOT NULL DEFAULT '',
          browser_ip TEXT NOT NULL DEFAULT '',
          source_challenge_id TEXT REFERENCES web_login_challenges(id),
          session_version INTEGER NOT NULL DEFAULT 1
        );
        """
    )
    conn.execute("CREATE INDEX IF NOT EXISTS idx_web_login_challenges_token_hash ON web_login_challenges(qr_token_hash)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_web_login_challenges_binding ON web_login_challenges(browser_binding_hash, status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_web_sessions_token_hash ON web_sessions(token_hash)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_web_sessions_user_active ON web_sessions(user_id, revoked_at)")


def apply_dashboard_overview_indexes_migration(conn: sqlite3.Connection):
    conn.execute("CREATE INDEX IF NOT EXISTS idx_orders_created_at ON orders(created_at)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_orders_unit_id ON orders(unit_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_order_items_product_id ON order_items(product_id)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_receipt_issues_status ON receipt_issues(status)")
    conn.execute("CREATE INDEX IF NOT EXISTS idx_products_supply_status ON products(supply_status)")


def migrate() -> list[str]:
    Path(upload_dir()).mkdir(parents=True, exist_ok=True)
    Path(private_upload_dir()).mkdir(parents=True, exist_ok=True)
    (Path(private_upload_dir()) / "receipt_issues").mkdir(parents=True, exist_ok=True)
    applied: list[str] = []
    with transaction() as conn:
        ensure_core_schema(conn)
        migrations = [
            ("0001_core_security_orders", lambda c: None),
            ("0002_shipping_photos", apply_shipping_photos_migration),
            ("0003_product_management_tasks", apply_product_management_migration),
            ("0004_account_security", apply_account_security_migration),
            ("0005_procurement_operations", apply_procurement_operations_migration),
            ("0006_web_qr_login", apply_web_qr_login_migration),
            ("0007_dashboard_overview_indexes", apply_dashboard_overview_indexes_migration),
        ]
        for version, fn in migrations:
            existing = one(conn, "SELECT version FROM schema_migrations WHERE version = ?", (version,))
            if not existing:
                fn(conn)
                conn.execute("INSERT INTO schema_migrations(version) VALUES (?)", (version,))
                applied.append(version)
    return applied


def migration_status() -> dict:
    with connect() as conn:
        ensure_core_schema(conn)
        rows = all_rows(conn, "SELECT version, applied_at FROM schema_migrations ORDER BY version")
    applied = [row["version"] for row in rows]
    known = [
        "0001_core_security_orders",
        "0002_shipping_photos",
        "0003_product_management_tasks",
        "0004_account_security",
        "0005_procurement_operations",
        "0006_web_qr_login",
        "0007_dashboard_overview_indexes",
    ]
    pending = [version for version in known if version not in applied]
    return {"applied": applied, "pending": pending}


def init_db():
    Path(upload_dir()).mkdir(parents=True, exist_ok=True)
    Path(private_upload_dir()).mkdir(parents=True, exist_ok=True)
    (Path(private_upload_dir()) / "receipt_issues").mkdir(parents=True, exist_ok=True)
    migrate()
