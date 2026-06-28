#!/usr/bin/env bash
set -euo pipefail

DATABASE_PATH="${DATABASE_PATH:-/app/data/smart_procurement.db}"
UPLOAD_DIR="${UPLOAD_DIR:-/app/uploads}"
BACKUP_DIR="${BACKUP_DIR:-/app/backups}"
LOG_FILE="${BACKUP_DIR}/backup.log"
STAMP="$(date +%Y%m%d%H%M%S)"

mkdir -p "$BACKUP_DIR"
{
  echo "[$(date -Iseconds)] backup started"
  if [[ ! -f "$DATABASE_PATH" ]]; then
    echo "database not found: $DATABASE_PATH" >&2
    exit 1
  fi
  sqlite3 "$DATABASE_PATH" ".backup '${BACKUP_DIR}/smart_procurement_${STAMP}.db'"
  tar -C "$(dirname "$UPLOAD_DIR")" -czf "${BACKUP_DIR}/uploads_${STAMP}.tar.gz" "$(basename "$UPLOAD_DIR")"
  find "$BACKUP_DIR" -type f \( -name 'smart_procurement_*.db' -o -name 'uploads_*.tar.gz' \) -mtime +14 -delete
  echo "[$(date -Iseconds)] backup finished"
} >>"$LOG_FILE" 2>&1
