#!/usr/bin/env bash
set -euo pipefail

REMOTE="${REMOTE:-aliyun-procurement}"
REMOTE_DIR="${REMOTE_DIR:-/opt/smart-procurement}"

ssh "$REMOTE" "mkdir -p ${REMOTE_DIR}/{app,data,uploads,backups,logs}"
rsync -az --delete \
  --exclude '.env' \
  --exclude '.venv' \
  --exclude '__pycache__' \
  --exclude '.pytest_cache' \
  "$(dirname "$0")/" "$REMOTE:${REMOTE_DIR}/app/"
ssh "$REMOTE" "cd ${REMOTE_DIR}/app && docker compose up -d --build && docker compose ps"
