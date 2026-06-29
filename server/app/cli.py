import argparse
import os
import sys

from .database import connect, migrate as run_migrations, migration_status as read_migration_status, one, revoke_user_sessions
from .security import hash_password


def reset_admin_password(username: str, env_var: str = "NEW_ADMIN_PASSWORD") -> bool:
    password = os.getenv(env_var, "")
    if len(password) < 8:
        raise ValueError(f"{env_var} must be set to at least 8 characters")
    with connect() as conn:
        user = one(conn, "SELECT * FROM users WHERE username = ? AND role = 'admin'", (username,))
        if not user:
            return False
        conn.execute(
            """
            UPDATE users
            SET password_hash = ?, must_change_password = 1, updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """,
            (hash_password(password), user["id"]),
        )
        revoke_user_sessions(conn, user["id"])
        conn.commit()
    print(f"Admin password reset for {username}; password value was not printed.")
    return True


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="生鲜后勤 server admin utilities")
    subparsers = parser.add_subparsers(dest="command", required=True)
    reset = subparsers.add_parser("reset-admin-password", help="Reset an admin password from NEW_ADMIN_PASSWORD")
    reset.add_argument("username", help="Admin username to reset")
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
