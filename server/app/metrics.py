import sqlite3
import threading
import time
from collections import deque
from dataclasses import dataclass


@dataclass(frozen=True)
class RequestMetric:
    timestamp: float
    status_code: int
    latency_ms: int
    sqlite_locked: bool = False


_metrics: deque[RequestMetric] = deque(maxlen=20000)
_lock = threading.Lock()
STARTED_AT = time.time()


def record_request(status_code: int, latency_ms: int, exc: BaseException | None = None):
    sqlite_locked = isinstance(exc, sqlite3.OperationalError) and "locked" in str(exc).lower()
    item = RequestMetric(
        timestamp=time.time(),
        status_code=int(status_code),
        latency_ms=max(0, int(latency_ms)),
        sqlite_locked=sqlite_locked,
    )
    with _lock:
        _metrics.append(item)


def request_snapshot(window_seconds: int = 300) -> dict:
    now = time.time()
    cutoff = now - window_seconds
    day_cutoff = now - 24 * 3600
    with _lock:
        recent = [item for item in _metrics if item.timestamp >= cutoff]
        day = [item for item in _metrics if item.timestamp >= day_cutoff]
    latencies = sorted(item.latency_ms for item in recent)
    request_count = len(recent)
    error_count = sum(1 for item in recent if item.status_code >= 400)
    average_latency = int(sum(latencies) / request_count) if request_count else 0
    p95_latency = 0
    if latencies:
        index = min(len(latencies) - 1, int(round(len(latencies) * 0.95 + 0.499)) - 1)
        p95_latency = latencies[max(0, index)]
    return {
        "request_count_5m": request_count,
        "average_latency_ms": average_latency,
        "p95_latency_ms": p95_latency,
        "error_count_5m": error_count,
        "error_rate_percent": round((error_count / request_count) * 100, 2) if request_count else 0,
        "sqlite_lock_count_24h": sum(1 for item in day if item.sqlite_locked),
    }


def uptime_seconds() -> int:
    return max(0, int(time.time() - STARTED_AT))
