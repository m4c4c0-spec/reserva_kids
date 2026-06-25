#!/bin/sh
set -u

BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
BACKUP_HOUR="${BACKUP_HOUR:-3}"   # hora UTC del backup diario (0-23)

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

# Quita ceros a la izquierda para que sh no interprete "08"/"09" como octal.
dec() { v=${1#0}; echo "${v:-0}"; }

# Segundos hasta la proxima ocurrencia de BACKUP_HOUR:00:00 UTC.
seconds_until_next_run() {
  now=$(( $(dec "$(date -u +%H)") * 3600 + $(dec "$(date -u +%M)") * 60 + $(dec "$(date -u +%S)") ))
  target=$(( $(dec "$BACKUP_HOUR") * 3600 ))
  if [ "$now" -lt "$target" ]; then
    echo $(( target - now ))
  else
    echo $(( 86400 - now + target ))
  fi
}

# Modo "backup": ejecucion puntual (util para invocacion manual).
if [ "${1:-}" = "backup" ]; then
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Ejecutando backup puntual..."
  do_backup
  exit $?
fi

echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Iniciando backup inicial..."
do_backup || echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Backup inicial fallo — reintentando en la proxima ejecucion programada"

echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Scheduler activo: backup diario a las $(printf '%02d' "$BACKUP_HOUR"):00 UTC"

# Bucle propio: sin cron daemon (evita 'setpgid: Operation not permitted' y reinicios).
while true; do
  sleep "$(seconds_until_next_run)"
  echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Ejecutando backup programado..."
  do_backup || echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] Backup programado fallo — se reintenta mañana"
done
