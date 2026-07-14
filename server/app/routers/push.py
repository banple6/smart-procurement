from uuid import uuid4

from fastapi import APIRouter, Depends, HTTPException, Query

from ..database import connect, one, transaction
from ..dependencies import current_user
from ..schemas import PushDeviceRegister


router = APIRouter(prefix="/push", tags=["push"])


@router.post("/devices/register")
def register_device(body: PushDeviceRegister, user=Depends(current_user)):
    with transaction() as conn:
        conn.execute(
            """
            UPDATE push_devices
            SET active = 0, unbound_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE installation_id = ? AND user_id != ? AND active = 1
            """,
            (body.installation_id, user["id"]),
        )
        existing = one(
            conn,
            "SELECT * FROM push_devices WHERE registration_id = ?",
            (body.registration_id,),
        )
        if existing:
            conn.execute(
                """
                UPDATE push_devices
                SET user_id = ?, installation_id = ?, platform = ?, app_version = ?, active = 1,
                    session_version = ?, last_seen_at = CURRENT_TIMESTAMP, bound_at = CURRENT_TIMESTAMP,
                    unbound_at = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                (
                    user["id"], body.installation_id, body.platform, body.app_version,
                    int(user.get("session_version") or 1), existing["id"],
                ),
            )
            device_id = existing["id"]
        else:
            device_id = str(uuid4())
            conn.execute(
                """
                INSERT INTO push_devices(
                  id, user_id, registration_id, installation_id, platform, app_version,
                  active, session_version, last_seen_at
                ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, CURRENT_TIMESTAMP)
                """,
                (
                    device_id, user["id"], body.registration_id, body.installation_id,
                    body.platform, body.app_version, int(user.get("session_version") or 1),
                ),
            )
    return {"ok": True, "device_id": device_id, "message": "订单通知设备已登记"}


@router.delete("/devices/current")
def unbind_current_device(
    installation_id: str = Query(min_length=8, max_length=80),
    user=Depends(current_user),
):
    with transaction() as conn:
        conn.execute(
            """
            UPDATE push_devices
            SET active = 0, unbound_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
            WHERE user_id = ? AND installation_id = ? AND active = 1
            """,
            (user["id"], installation_id),
        )
    return {"ok": True, "message": "订单通知设备已解绑"}


@router.post("/events/{event_id}/opened")
def mark_event_opened(event_id: str, user=Depends(current_user)):
    with transaction() as conn:
        event = one(conn, "SELECT * FROM push_outbox WHERE event_id = ?", (event_id,))
        if not event:
            return {"ok": True}
        allowed = event["recipient_scope"] == "admins" and user["role"] == "admin"
        allowed = allowed or (
            event["recipient_scope"] == "unit"
            and user["role"] == "unit_user"
            and user.get("unit_id") == event.get("recipient_unit_id")
        )
        if not allowed:
            raise HTTPException(status_code=403, detail="订单不存在或无权查看")
        conn.execute(
            """
            UPDATE push_deliveries
            SET status = 'opened', opened_at = COALESCE(opened_at, CURRENT_TIMESTAMP), updated_at = CURRENT_TIMESTAMP
            WHERE event_id = ? AND device_id IN (
              SELECT id FROM push_devices WHERE user_id = ?
            )
            """,
            (event_id, user["id"]),
        )
    return {"ok": True}
