#!/bin/sh
set -u

BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

do_backup() {
  FILENAME="${BACKUP_DIR}/reservakids_$(date +%Y%m%d_%H%M%S).sql.gz"
  pg_dump -h "$PGHOST" -U "$PGUSER" -d "$PGDATABASE" --no-owner --no-acl | gzip > "$FILENAME"
  if gzip -t "$FILENAME" && zgrep -q "PostgreSQL database dump" "$FILENAME" 2>/dev/null; then
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Backup OK: $FILENAME ($(wc -c < "$FILENAME") bytes)"
  else
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] ERROR: backup corrupto en $FILENAME" >&2
    rm -f "$FILENAME"
    return 1
  fi
  find "$BACKUP_DIR" -name 'reservakids_*.sql.gz' -mtime "+${RETENTION_DAYS}" -delete 2>/dev/null || true
}

echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Iniciando backup inicial..."
do_backup || echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Backup inicial fallo — reintentando en la proxima ejecucion programada"

echo "0 3 * * * /entrypoint.sh backup" > /var/spool/cron/crontabs/root
chmod 600 /var/spool/cron/crontabs/root
echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Backup programado: todos los dias a las 03:00 AM UTC"

if [ "${1:-}" = "backup" ]; then
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Ejecutando backup programado..."
  do_backup || true
  exit 0
fi

exec crond -f -d 8
