#!/usr/bin/env bash
set -euo pipefail

REMOTE="${REMOTE:-aliyun-procurement}"
REMOTE_DIR="${REMOTE_DIR:-/opt/smart-procurement}"

ssh "$REMOTE" "mkdir -p ${REMOTE_DIR}/{app,data,uploads,private_uploads,backups,logs} ${REMOTE_DIR}/private_uploads/shipping"
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
  tar -C "$(dirname "$0")" \
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
ssh "$REMOTE" "cd ${REMOTE_DIR}/app && if ! grep -q '^PRIVATE_UPLOAD_DIR=' .env; then printf '\nPRIVATE_UPLOAD_DIR=/app/private_uploads\n' >> .env; fi"
ssh "$REMOTE" "cd ${REMOTE_DIR}/app && docker compose up -d --build && docker compose ps"
