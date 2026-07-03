#!/usr/bin/env bash
set -euo pipefail

REMOTE="${REMOTE:-aliyun-procurement}"
REMOTE_DIR="${REMOTE_DIR:-/opt/smart-procurement}"

ssh "$REMOTE" "mkdir -p ${REMOTE_DIR}/{app,web,data,uploads,private_uploads,backups,logs} ${REMOTE_DIR}/private_uploads/shipping"
if command -v rsync >/dev/null 2>&1 && ssh "$REMOTE" "command -v rsync >/dev/null 2>&1"; then
  rsync -az --delete \
    --exclude '.env' \
    --exclude '.venv' \
    --exclude 'data' \
    --exclude 'uploads' \
    --exclude 'private_uploads' \
    --exclude 'backups' \
    --exclude 'logs' \
    --exclude '__pycache__' \
    --exclude '.pytest_cache' \
    "$(dirname "$0")/" "$REMOTE:${REMOTE_DIR}/app/"
else
  COPYFILE_DISABLE=1 tar -C "$(dirname "$0")" \
    --exclude './.env' \
    --exclude './.venv' \
    --exclude './data' \
    --exclude './uploads' \
    --exclude './private_uploads' \
    --exclude './backups' \
    --exclude './logs' \
    --exclude './__pycache__' \
    --exclude './.pytest_cache' \
    -czf - . | ssh "$REMOTE" "tar -xzf - -C ${REMOTE_DIR}/app"
fi
WEB_DIST="$(cd "$(dirname "$0")/.." && pwd)/web/dist"
if [ -d "$WEB_DIST" ]; then
  if command -v rsync >/dev/null 2>&1 && ssh "$REMOTE" "command -v rsync >/dev/null 2>&1"; then
    rsync -az --delete "$WEB_DIST/" "$REMOTE:${REMOTE_DIR}/web/"
  else
    COPYFILE_DISABLE=1 tar -C "$WEB_DIST" -czf - . | ssh "$REMOTE" "rm -rf ${REMOTE_DIR}/web/* && tar -xzf - -C ${REMOTE_DIR}/web"
  fi
else
  echo "web/dist not found, skipping web static upload" >&2
fi
ssh "$REMOTE" "cd ${REMOTE_DIR}/app && if ! grep -q '^PRIVATE_UPLOAD_DIR=' .env; then printf '\nPRIVATE_UPLOAD_DIR=/app/private_uploads\n' >> .env; fi"
ssh "$REMOTE" "cd ${REMOTE_DIR}/app && docker compose up -d --build && docker compose ps"
