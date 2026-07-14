import os
import sqlite3
import time
from datetime import datetime, timedelta, timezone

from ..database import all_rows, connect, one, transaction
from ..services.audit import append_system_event, sanitize_text
from ..services.jpush import JPushClient


RETRY_DELAYS_SECONDS = [30, 120, 600, 1800]


def is_push_schema_not_ready(exc: Exception) -> bool:
    return isinstance(exc, sqlite3.OperationalError) and "no such table: push_" in str(exc).lower()


def recover_stale_events(timeout_seconds: int | None = None) -> int:
    timeout = timeout_seconds or int(os.getenv("JPUSH_PROCESSING_TIMEOUT_SECONDS", "300"))
    with transaction() as conn:
        cursor = conn.execute(
            """
            UPDATE push_outbox
            SET status = 'retry', next_attempt_at = CURRENT_TIMESTAMP, processing_started_at = NULL,
                updated_at = CURRENT_TIMESTAMP, last_error = '推送任务处理超时，已恢复'
            WHERE status = 'processing'
              AND processing_started_at IS NOT NULL
              AND processing_started_at <= datetime('now', ?)
            """,
            (f"-{timeout} seconds",),
        )
        return cursor.rowcount


def claim_next_event() -> dict | None:
    with transaction() as conn:
        event = one(
            conn,
            """
            SELECT * FROM push_outbox
            WHERE status IN ('pending', 'retry')
              AND (next_attempt_at IS NULL OR next_attempt_at <= CURRENT_TIMESTAMP)
            ORDER BY id
            LIMIT 1
            """,
        )
        if not event:
            return None
        cursor = conn.execute(
            """
            UPDATE push_outbox
            SET status = 'processing', processing_started_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status IN ('pending', 'retry')
            """,
            (event["id"],),
        )
        if cursor.rowcount != 1:
            return None
        return one(conn, "SELECT * FROM push_outbox WHERE id = ?", (event["id"],))


def eligible_devices(conn, event: dict) -> list[dict]:
    if event["recipient_scope"] == "admins":
        return all_rows(
            conn,
            """
            SELECT d.* FROM push_devices d
            JOIN users u ON u.id = d.user_id
            WHERE d.active = 1 AND u.active = 1 AND u.role = 'admin'
              AND d.session_version = u.session_version
            """,
        )
    return all_rows(
        conn,
        """
        SELECT d.* FROM push_devices d
        JOIN users u ON u.id = d.user_id
        JOIN units un ON un.id = u.unit_id
        WHERE d.active = 1 AND u.active = 1 AND u.role = 'unit_user'
          AND un.active = 1 AND u.unit_id = ?
          AND d.session_version = u.session_version
        """,
        (event["recipient_unit_id"],),
    )


def ensure_deliveries(event: dict) -> list[dict]:
    with transaction() as conn:
        for device in eligible_devices(conn, event):
            conn.execute(
                """
                INSERT OR IGNORE INTO push_deliveries(event_id, device_id)
                VALUES (?, ?)
                """,
                (event["event_id"], device["id"]),
            )
        return all_rows(
            conn,
            """
            SELECT pd.*, d.registration_id
            FROM push_deliveries pd
            JOIN push_devices d ON d.id = pd.device_id
            WHERE pd.event_id = ? AND pd.status IN ('pending', 'failed') AND d.active = 1
            ORDER BY pd.id
            """,
            (event["event_id"],),
        )


def process_event(event: dict, client: JPushClient) -> None:
    deliveries = ensure_deliveries(event)
    retryable_failure = False
    for delivery in deliveries:
        result = client.send(delivery["registration_id"], event)
        with transaction() as conn:
            if result.accepted:
                conn.execute(
                    """
                    UPDATE push_deliveries
                    SET status = 'accepted_by_provider', provider_message_id = ?, attempt_count = attempt_count + 1,
                        last_error = '', updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (result.provider_message_id, delivery["id"]),
                )
            elif result.invalid_device:
                conn.execute(
                    """
                    UPDATE push_deliveries
                    SET status = 'invalid_device', attempt_count = attempt_count + 1,
                        last_error = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (sanitize_text(result.error), delivery["id"]),
                )
                conn.execute(
                    """
                    UPDATE push_devices
                    SET active = 0, unbound_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (delivery["device_id"],),
                )
            else:
                retryable_failure = True
                conn.execute(
                    """
                    UPDATE push_deliveries
                    SET status = 'failed', attempt_count = attempt_count + 1,
                        last_error = ?, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    (sanitize_text(result.error), delivery["id"]),
                )

    with transaction() as conn:
        current = one(conn, "SELECT * FROM push_outbox WHERE event_id = ?", (event["event_id"],))
        next_attempt = int(current["attempt_count"]) + 1
        if retryable_failure:
            max_attempts = int(os.getenv("JPUSH_MAX_ATTEMPTS", "5"))
            if next_attempt >= max_attempts:
                conn.execute(
                    """
                    UPDATE push_outbox
                    SET status = 'failed', attempt_count = ?, processing_started_at = NULL,
                        next_attempt_at = NULL, last_error = '推送重试次数已用尽', updated_at = CURRENT_TIMESTAMP
                    WHERE event_id = ?
                    """,
                    (next_attempt, event["event_id"]),
                )
            else:
                delay = RETRY_DELAYS_SECONDS[min(next_attempt - 1, len(RETRY_DELAYS_SECONDS) - 1)]
                retry_at = (datetime.now(timezone.utc) + timedelta(seconds=delay)).strftime("%Y-%m-%d %H:%M:%S")
                conn.execute(
                    """
                    UPDATE push_outbox
                    SET status = 'retry', attempt_count = ?, processing_started_at = NULL,
                        next_attempt_at = ?, last_error = '部分设备推送失败，等待重试', updated_at = CURRENT_TIMESTAMP
                    WHERE event_id = ?
                    """,
                    (next_attempt, retry_at, event["event_id"]),
                )
        else:
            conn.execute(
                """
                UPDATE push_outbox
                SET status = 'sent', processing_started_at = NULL, next_attempt_at = NULL,
                    sent_at = CURRENT_TIMESTAMP, last_error = '', updated_at = CURRENT_TIMESTAMP
                WHERE event_id = ?
                """,
                (event["event_id"],),
            )
            if not deliveries:
                append_system_event(
                    {
                        "action": "PUSH_NO_ELIGIBLE_DEVICE",
                        "object_type": "push_event",
                        "object_id": event["event_id"],
                        "result": "success",
                    }
                )


def run_forever() -> None:
    client = JPushClient.from_env()
    poll_seconds = max(1, int(os.getenv("JPUSH_WORKER_POLL_SECONDS", "5")))
    while True:
        if not client.enabled or not client.app_key or not client.master_secret:
            time.sleep(poll_seconds)
            continue
        try:
            recover_stale_events()
            event = claim_next_event()
        except sqlite3.OperationalError as exc:
            if not is_push_schema_not_ready(exc):
                raise
            time.sleep(poll_seconds)
            continue
        if event:
            try:
                process_event(event, client)
            except Exception as exc:
                append_system_event(
                    {
                        "action": "PUSH_WORKER_ERROR",
                        "object_type": "push_event",
                        "object_id": event.get("event_id", ""),
                        "result": "failure",
                        "error_message": sanitize_text(str(exc)),
                    }
                )
        else:
            time.sleep(poll_seconds)


if __name__ == "__main__":
    run_forever()
