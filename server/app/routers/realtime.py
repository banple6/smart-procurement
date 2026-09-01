import asyncio
import json
import logging
import time

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from ..database import connect
from ..dependencies import require_admin_user
from ..services.realtime import resource_revisions


router = APIRouter(prefix="/admin/realtime", tags=["realtime"])
logger = logging.getLogger(__name__)


@router.get("/revisions")
def realtime_revisions(admin=Depends(require_admin_user)):
    with connect() as conn:
        return resource_revisions(conn)


@router.get("/events")
async def realtime_events(request: Request, admin=Depends(require_admin_user)):
    async def stream():
        with connect() as conn:
            previous = resource_revisions(conn)
        last_keepalive = time.monotonic()
        logger.info("realtime_sse_connected admin_id=%s", admin["id"])
        try:
            while not await request.is_disconnected():
                with connect() as conn:
                    current = resource_revisions(conn)
                for resource, revision in current.items():
                    if revision != previous.get(resource, 0):
                        yield "event: resource_changed\n"
                        yield f"data: {json.dumps({'resource': resource, 'revision': revision}, separators=(',', ':'))}\n\n"
                previous = current
                if time.monotonic() - last_keepalive >= 25:
                    yield ": keepalive\n\n"
                    last_keepalive = time.monotonic()
                await asyncio.sleep(1.5)
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception("realtime_sse_unexpected_error admin_id=%s", admin["id"])
        finally:
            logger.info("realtime_sse_disconnected admin_id=%s", admin["id"])

    return StreamingResponse(
        stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )
