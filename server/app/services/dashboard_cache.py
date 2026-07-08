from __future__ import annotations

import copy
import time
from threading import Lock
from typing import Callable


_LOCK = Lock()
_CACHE: dict[tuple, tuple[float, dict]] = {}
_TTL_SECONDS = 10


def get_dashboard_cached(key: tuple, factory: Callable[[], dict]) -> dict:
    now = time.monotonic()
    with _LOCK:
        cached = _CACHE.get(key)
        if cached and now - cached[0] <= _TTL_SECONDS:
            return copy.deepcopy(cached[1])
    value = factory()
    with _LOCK:
        _CACHE[key] = (now, copy.deepcopy(value))
    return value


def invalidate_dashboard_cache():
    with _LOCK:
        _CACHE.clear()
