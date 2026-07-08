from __future__ import annotations

import argparse
import asyncio
import statistics
import time

import httpx


async def worker(client: httpx.AsyncClient, path: str, stop_at: float, latencies: list[float], errors: list[str]):
    while time.monotonic() < stop_at:
        started = time.perf_counter()
        try:
            response = await client.get(path)
            elapsed = (time.perf_counter() - started) * 1000
            latencies.append(elapsed)
            if response.status_code >= 500:
                errors.append(f"HTTP {response.status_code}")
        except Exception as exc:
            errors.append(type(exc).__name__)
        await asyncio.sleep(0.1)


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, int(round((pct / 100) * (len(ordered) - 1))))
    return ordered[index]


async def run(base_url: str, users: int, seconds: int, path: str):
    latencies: list[float] = []
    errors: list[str] = []
    stop_at = time.monotonic() + seconds
    async with httpx.AsyncClient(base_url=base_url, timeout=10) as client:
        await asyncio.gather(*(worker(client, path, stop_at, latencies, errors) for _ in range(users)))
    total = len(latencies)
    print(
        {
            "base_url": base_url,
            "users": users,
            "seconds": seconds,
            "requests": total,
            "rps": round(total / max(seconds, 1), 2),
            "p50_ms": round(statistics.median(latencies), 2) if latencies else 0,
            "p95_ms": round(percentile(latencies, 95), 2),
            "p99_ms": round(percentile(latencies, 99), 2),
            "max_ms": round(max(latencies), 2) if latencies else 0,
            "errors": len(errors),
            "error_types": sorted(set(errors)),
        }
    )


def main():
    parser = argparse.ArgumentParser(description="Compare Uvicorn 1-worker and 2-worker deployments from outside the ECS.")
    parser.add_argument("--base-url", required=True, help="Example: http://127.0.0.1:18080")
    parser.add_argument("--users", type=int, default=30)
    parser.add_argument("--seconds", type=int, default=300)
    parser.add_argument("--path", default="/api/v1/health/ready")
    args = parser.parse_args()
    asyncio.run(run(args.base_url.rstrip("/"), args.users, args.seconds, args.path))


if __name__ == "__main__":
    main()
