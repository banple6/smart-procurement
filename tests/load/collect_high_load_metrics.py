from __future__ import annotations

import argparse
import csv
import json
import shlex
import subprocess
import time
from pathlib import Path


REMOTE_SCRIPT = r"""
python3 - <<'PY'

import json
import os
import subprocess
from pathlib import Path


def read_first(path, default=""):
    try:
        return Path(path).read_text().strip()
    except OSError:
        return default


def meminfo():
    values = {}
    for line in read_first("/proc/meminfo").splitlines():
        name, raw = line.split(":", 1)
        values[name] = int(raw.strip().split()[0])
    return values


def cpu_stat():
    parts = read_first("/proc/stat").splitlines()[0].split()
    names = ["user", "nice", "system", "idle", "iowait", "irq", "softirq", "steal", "guest", "guest_nice"]
    return {name: int(value) for name, value in zip(names, parts[1:])}


def disk_totals():
    reads = writes = 0
    for line in read_first("/proc/diskstats").splitlines():
        parts = line.split()
        if len(parts) < 14:
            continue
        name = parts[2]
        if name.startswith(("loop", "ram")):
            continue
        reads += int(parts[5])
        writes += int(parts[9])
    return {"disk_read_sectors": reads, "disk_write_sectors": writes}


def net_totals():
    rx = tx = 0
    for line in read_first("/proc/net/dev").splitlines()[2:]:
        iface, raw = line.split(":", 1)
        if iface.strip() == "lo":
            continue
        parts = raw.split()
        rx += int(parts[0])
        tx += int(parts[8])
    return {"net_rx_bytes": rx, "net_tx_bytes": tx}


def run_text(command):
    try:
        return subprocess.check_output(command, universal_newlines=True, stderr=subprocess.DEVNULL).strip()
    except Exception:
        return ""


def container_stats(names):
    result = {}
    if not names:
        return result
    output = run_text([
        "docker",
        "stats",
        "--no-stream",
        "--format",
        "{{.Name}}|{{.CPUPerc}}|{{.MemUsage}}|{{.NetIO}}|{{.BlockIO}}|{{.PIDs}}",
        *names,
    ])
    for line in output.splitlines():
        parts = line.split("|")
        if len(parts) != 6:
            continue
        name, cpu, mem, net, block, pids = parts
        prefix = name.replace("-", "_")
        result[f"{prefix}_cpu_percent"] = cpu.replace("%", "")
        result[f"{prefix}_mem_usage"] = mem
        result[f"{prefix}_net_io"] = net
        result[f"{prefix}_block_io"] = block
        result[f"{prefix}_pids"] = pids
    inspect = run_text([
        "docker",
        "inspect",
        "--format",
        "{{.Name}}|{{.RestartCount}}|{{.State.OOMKilled}}",
        *names,
    ])
    for line in inspect.splitlines():
        parts = line.split("|")
        if len(parts) != 3:
            continue
        name, restarts, oom = parts
        prefix = name.strip("/").replace("-", "_")
        result[f"{prefix}_restart_count"] = restarts
        result[f"{prefix}_oom_killed"] = oom
    return result


def count_statuses(access_log):
    counters = {"http_4xx_total": 0, "http_5xx_total": 0}
    try:
        with open(access_log, "r", encoding="utf-8", errors="ignore") as handle:
            for line in handle:
                parts = line.split()
                if len(parts) < 9:
                    continue
                status = parts[8]
                if status.startswith("4"):
                    counters["http_4xx_total"] += 1
                elif status.startswith("5"):
                    counters["http_5xx_total"] += 1
    except OSError:
        pass
    return counters


loadtest_root = os.getenv("LOADTEST_ROOT", "/srv/smart-procurement-loadtest")
loadtest_log = f"{loadtest_root}/logs/access.log"
loadtest_db = f"{loadtest_root}/data/loadtest.db"
containers = [
    "smart-procurement-loadtest-api-1",
    "smart-procurement-loadtest-nginx-1",
    "app-api-1",
    "app-nginx-1",
]
mem = meminfo()
cpu = cpu_stat()
row = {
    "timestamp": run_text(["date", "-Iseconds"]) or run_text(["date", "+%Y-%m-%dT%H:%M:%S%z"]),
    "load_1m": read_first("/proc/loadavg").split()[0],
    "load_5m": read_first("/proc/loadavg").split()[1],
    "load_15m": read_first("/proc/loadavg").split()[2],
    "mem_total_kb": mem.get("MemTotal", 0),
    "mem_available_kb": mem.get("MemAvailable", 0),
    "swap_total_kb": mem.get("SwapTotal", 0),
    "swap_free_kb": mem.get("SwapFree", 0),
    "fd_allocated": read_first("/proc/sys/fs/file-nr").split()[0],
    "nginx_80_established": run_text(["sh", "-lc", "ss -tan state established '( sport = :80 )' | tail -n +2 | wc -l"]) or "0",
    "nginx_18080_established": run_text(["sh", "-lc", "ss -tan state established '( sport = :18080 )' | tail -n +2 | wc -l"]) or "0",
    "loadtest_wal_bytes": Path(f"{loadtest_db}-wal").stat().st_size if Path(f"{loadtest_db}-wal").exists() else 0,
    "database_locked_total": run_text(["sh", "-lc", "docker logs smart-procurement-loadtest-api-1 2>&1 | grep -c 'database is locked' || true"]) or "0",
    "rollback_total": run_text(["sh", "-lc", "docker logs smart-procurement-loadtest-api-1 2>&1 | grep -ci 'rollback' || true"]) or "0",
    "write_failure_total": run_text(["sh", "-lc", "docker logs smart-procurement-loadtest-api-1 2>&1 | grep -Eci 'write.*fail|failed.*write' || true"]) or "0",
}
row.update({f"cpu_{key}": value for key, value in cpu.items()})
row.update(disk_totals())
row.update(net_totals())
row.update(count_statuses(loadtest_log))
row.update(container_stats(containers))
print(json.dumps(row, ensure_ascii=False, sort_keys=True))
PY
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Collect ECS/loadtest metrics through SSH into CSV.")
    parser.add_argument("--ssh-host", default="aliyun-procurement")
    parser.add_argument("--out", required=True)
    parser.add_argument("--duration", type=int, required=True, help="Duration in seconds")
    parser.add_argument("--interval", type=int, default=5, help="Sampling interval in seconds")
    parser.add_argument("--loadtest-root", default="/srv/smart-procurement-loadtest")
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    output = Path(args.out)
    output.parent.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + args.duration
    fieldnames: list[str] | None = None
    with output.open("w", newline="", encoding="utf-8") as handle:
        writer = None
        while time.monotonic() < deadline:
            proc = subprocess.run(
                ["ssh", args.ssh_host, f"export LOADTEST_ROOT={shlex.quote(args.loadtest_root)}; {REMOTE_SCRIPT}"],
                text=True,
                capture_output=True,
                check=False,
            )
            if proc.returncode != 0:
                row = {"timestamp": time.strftime("%Y-%m-%dT%H:%M:%S%z"), "collector_error": proc.stderr.strip()}
            else:
                row = json.loads(proc.stdout.strip())
                row["collector_error"] = ""
            if fieldnames is None:
                fieldnames = sorted(row)
                writer = csv.DictWriter(handle, fieldnames=fieldnames, extrasaction="ignore")
                writer.writeheader()
            writer.writerow(row)
            handle.flush()
            time.sleep(args.interval)


if __name__ == "__main__":
    main()
