#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 5 ]]; then
  echo "Usage: $0 <phase-name> <users> <spawn-rate> <duration> <metrics-extra-seconds>" >&2
  echo "Example: $0 20-users 20 2 10m 60" >&2
  exit 2
fi

PHASE_NAME="$1"
USERS="$2"
SPAWN_RATE="$3"
DURATION="$4"
METRICS_EXTRA_SECONDS="$5"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT_DIR="$ROOT_DIR/reports/load/$PHASE_NAME"
mkdir -p "$OUT_DIR"

case "$DURATION" in
  *m) DURATION_SECONDS=$((${DURATION%m} * 60)) ;;
  *s) DURATION_SECONDS=${DURATION%s} ;;
  *) DURATION_SECONDS=$DURATION ;;
esac

METRICS_DURATION=$((DURATION_SECONDS + METRICS_EXTRA_SECONDS))

if [[ -z "${LOADTEST_USER_PASSWORD:-}" ]]; then
  echo "LOADTEST_USER_PASSWORD is required" >&2
  exit 2
fi

export LOADTEST_ADMIN_PASSWORD="${LOADTEST_ADMIN_PASSWORD:-$LOADTEST_USER_PASSWORD}"
export LOADTEST_UNIT_PASSWORD="${LOADTEST_UNIT_PASSWORD:-$LOADTEST_USER_PASSWORD}"
export LOADTEST_EXPECTED_ENVIRONMENT="${LOADTEST_EXPECTED_ENVIRONMENT:-loadtest}"
export LOADTEST_EXPECTED_NAMESPACE="${LOADTEST_EXPECTED_NAMESPACE:-LOADTEST}"
export LOADTEST_ADMIN_USER_COUNT="${LOADTEST_ADMIN_USER_COUNT:-15}"
export LOADTEST_UNIT_USER_COUNT="${LOADTEST_UNIT_USER_COUNT:-40}"
export LOADTEST_MAX_ORDERS_PER_USER="${LOADTEST_MAX_ORDERS_PER_USER:-2}"

PYTHON_BIN="${LOADTEST_PYTHON_BIN:-/tmp/smart-procurement-loadtest-venv/bin/python}"

"$PYTHON_BIN" "$ROOT_DIR/tests/load/collect_high_load_metrics.py" \
  --out "$OUT_DIR/server-metrics.csv" \
  --duration "$METRICS_DURATION" \
  --interval 5 &
METRICS_PID=$!

cleanup() {
  if kill -0 "$METRICS_PID" >/dev/null 2>&1; then
    kill "$METRICS_PID" >/dev/null 2>&1 || true
    wait "$METRICS_PID" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

/tmp/smart-procurement-loadtest-venv/bin/locust \
  -f "$ROOT_DIR/tests/load/locustfile.py" \
  --host http://127.0.0.1:18080 \
  --headless \
  -u "$USERS" \
  -r "$SPAWN_RATE" \
  -t "$DURATION" \
  --html "$OUT_DIR/report.html" \
  --csv "$OUT_DIR/locust" \
  --csv-full-history

wait "$METRICS_PID" || true
trap - EXIT
