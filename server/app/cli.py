import argparse
import os
import sys
from uuid import uuid4

from .database import connect, migrate as run_migrations, migration_status as read_migration_status, one, revoke_user_sessions, table_exists, write_audit
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


def create_system_admin(username: str | None = None, password_env: str = "SYSTEM_ADMIN_PASSWORD") -> bool:
    run_migrations()
    final_username = (username or os.getenv("SYSTEM_ADMIN_USERNAME", "system_admin")).strip()
    display_name = os.getenv("SYSTEM_ADMIN_DISPLAY_NAME", "系统管理员").strip() or "系统管理员"
    password = os.getenv(password_env, "")
    if len(final_username) < 3:
        raise ValueError("system admin username must be at least 3 characters")
    if len(password) < 8:
        raise ValueError(f"{password_env} must be set to at least 8 characters")
    with connect() as conn:
        existing = one(conn, "SELECT id FROM users WHERE username = ?", (final_username,))
        if existing:
            return False
        user_id = str(uuid4())
        conn.execute(
            """
            INSERT INTO users(
              id, username, password_hash, display_name, role, active, must_change_password,
              can_manage_accounts, can_issue_manager_invites, can_view_system_status,
              can_view_detailed_metrics, can_manage_backups, can_restore_backups
            )
            VALUES (?, ?, ?, ?, 'admin', 1, 1, 1, 1, 1, 1, 1, 1)
            """,
            (user_id, final_username, hash_password(password), display_name),
        )
        write_audit(conn, user_id, "admin", "CLI_CREATE_SYSTEM_ADMIN", "user", user_id)
        conn.commit()
    print(f"System admin created for {final_username}; password value was not printed.")
    return True


def cleanup_web_auth_records(challenge_retention_days: int = 7, revoked_session_retention_days: int = 90) -> dict[str, int]:
    if challenge_retention_days < 1 or revoked_session_retention_days < 1:
        raise ValueError("retention days must be positive")
    with connect() as conn:
        if not table_exists(conn, "web_login_challenges") or not table_exists(conn, "web_sessions"):
            return {"revoked_expired_sessions": 0, "deleted_challenges": 0, "deleted_revoked_sessions": 0}
        revoked = conn.execute(
            """
            UPDATE web_sessions
            SET revoked_at = CURRENT_TIMESTAMP, revoked_reason = '会话已过期'
            WHERE revoked_at IS NULL
              AND (CURRENT_TIMESTAMP > idle_expires_at OR CURRENT_TIMESTAMP > absolute_expires_at)
            """
        ).rowcount
        deleted_challenges = conn.execute(
            """
            DELETE FROM web_login_challenges
            WHERE created_at < datetime('now', ?)
              AND (status IN ('rejected', 'consumed') OR CURRENT_TIMESTAMP > expires_at)
            """,
            (f"-{challenge_retention_days} days",),
        ).rowcount
        deleted_sessions = conn.execute(
            """
            DELETE FROM web_sessions
            WHERE revoked_at IS NOT NULL
              AND revoked_at < datetime('now', ?)
            """,
            (f"-{revoked_session_retention_days} days",),
        ).rowcount
        conn.commit()
    return {
        "revoked_expired_sessions": revoked,
        "deleted_challenges": deleted_challenges,
        "deleted_revoked_sessions": deleted_sessions,
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="生鲜后勤 server admin utilities")
    subparsers = parser.add_subparsers(dest="command", required=True)
    reset = subparsers.add_parser("reset-admin-password", help="Reset an admin password from NEW_ADMIN_PASSWORD")
    reset.add_argument("username", help="Admin username to reset")
    create_admin = subparsers.add_parser("create-system-admin", help="Create a system admin from SYSTEM_ADMIN_PASSWORD")
    create_admin.add_argument("username", nargs="?", help="Admin username; defaults to SYSTEM_ADMIN_USERNAME or system_admin")
    subparsers.add_parser("migrate", help="Run pending database migrations")
    subparsers.add_parser("migration-status", help="Print database migration status")
    cleanup = subparsers.add_parser("cleanup-web-auth-records", help="Revoke expired web sessions and prune old QR login records")
    cleanup.add_argument("--challenge-retention-days", type=int, default=7)
    cleanup.add_argument("--revoked-session-retention-days", type=int, default=90)
    args = parser.parse_args(argv)

    if args.command == "reset-admin-password":
        try:
            if not reset_admin_password(args.username):
                print(f"Admin user not found: {args.username}", file=sys.stderr)
                return 1
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
    elif args.command == "create-system-admin":
        try:
            if not create_system_admin(args.username):
                print(f"User already exists: {args.username or os.getenv('SYSTEM_ADMIN_USERNAME', 'system_admin')}", file=sys.stderr)
                return 1
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
    elif args.command == "migrate":
        applied = run_migrations()
        print({"applied_now": applied})
    elif args.command == "migration-status":
        print(read_migration_status())
    elif args.command == "cleanup-web-auth-records":
        try:
            print(
                cleanup_web_auth_records(
                    challenge_retention_days=args.challenge_retention_days,
                    revoked_session_retention_days=args.revoked_session_retention_days,
                )
            )
        except ValueError as exc:
            print(str(exc), file=sys.stderr)
            return 2
    return 0


def migrate():
    return run_migrations()


def migration_status():
    return read_migration_status()


if __name__ == "__main__":
    raise SystemExit(main())
