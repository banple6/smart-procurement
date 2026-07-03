import argparse
import os
import re
import sys
from uuid import uuid4

from .database import connect, migrate as run_migrations, migration_status as read_migration_status, one, revoke_user_sessions
from .security import hash_password

USERNAME_RE = re.compile(r"^[a-zA-Z0-9_]{4,32}$")


def validate_admin_password(username: str, password: str):
    if len(password) < 8 or not any(ch.isalpha() for ch in password) or not any(ch.isdigit() for ch in password):
        raise ValueError("password must be at least 8 characters and include letters and digits")
    if password.lower() == username.lower():
        raise ValueError("password must not equal username")


def reset_admin_password(username: str, env_var: str = "NEW_ADMIN_PASSWORD") -> bool:
    username = username.strip().lower()
    password = os.getenv(env_var, "")
    validate_admin_password(username, password)
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE lower(username) = lower(?) AND role = 'admin'", (username,))
        if not user:
            return False
        conn.execute(
            """
            UPDATE users
            SET password_hash = ?, must_change_password = 1, password_changed_at = CURRENT_TIMESTAMP,
                failed_login_count = 0, locked_until = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (hash_password(password), user["id"]),
        )
        revoke_user_sessions(conn, user["id"])
        conn.commit()
    print(f"Admin password reset for {username}; password value was not printed.")
    return True


def create_admin(username: str, display_name: str = "系统管理员", env_var: str = "NEW_ADMIN_PASSWORD") -> bool:
    username = username.strip().lower()
    if not USERNAME_RE.fullmatch(username):
        raise ValueError("username must be 4-32 letters, digits, or underscores")
    password = os.getenv(env_var, "")
    validate_admin_password(username, password)
    with connect() as conn:
        existing = one(conn, "SELECT * FROM users WHERE lower(username) = lower(?)", (username,))
        if existing:
            return False
        conn.execute(
            """
            INSERT INTO users(id, username, password_hash, display_name, role, unit_id, active, must_change_password)
            VALUES (?, ?, ?, ?, 'admin', NULL, 1, 1)
            """,
            (str(uuid4()), username, hash_password(password), display_name.strip() or "系统管理员"),
        )
        conn.commit()
    print(f"Admin user created: {username}; password value was not printed.")
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="生鲜后勤 server admin utilities")
    subparsers = parser.add_subparsers(dest="command", required=True)
    reset = subparsers.add_parser("reset-admin-password", help="Reset an admin password from NEW_ADMIN_PASSWORD")
    reset.add_argument("username", help="Admin username to reset")
    create = subparsers.add_parser("create-admin", help="Create an admin user from NEW_ADMIN_PASSWORD")
    create.add_argument("username", help="Admin username to create")
    create.add_argument("--display-name", default="系统管理员", help="Admin display name")
    subparsers.add_parser("migrate", help="Run pending database migrations")
    subparsers.add_parser("migration-status", help="Print database migration status")
    args = parser.parse_args(argv)

    if args.command == "reset-admin-password":
        try:
            if not reset_admin_password(args.username):
                print(f"Admin user not found: {args.username}", file=sys.stderr)
                return 1
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
    elif args.command == "create-admin":
        try:
            if not create_admin(args.username, args.display_name):
                print(f"Admin user already exists: {args.username}", file=sys.stderr)
                return 1
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
    elif args.command == "migrate":
        applied = run_migrations()
        print({"applied_now": applied})
    elif args.command == "migration-status":
        print(read_migration_status())
    return 0


def migrate():
    return run_migrations()


def migration_status():
    return read_migration_status()


if __name__ == "__main__":
    raise SystemExit(main())
