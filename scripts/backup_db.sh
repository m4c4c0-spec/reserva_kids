#!/usr/bin/env bash
# Backup diario de PostgreSQL (RNF-03: backups diarios verificados).
# Uso:    ./scripts/backup_db.sh [directorio_destino]
# Cron:   0 3 * * * /ruta/reserva_kids/scripts/backup_db.sh /var/backups/reservakids
#
# Copia offsite (falla #7, revisión a 2 años): si RCLONE_REMOTE está definido, el dump
# se sube a un remoto rclone (Backblaze B2/S3) — un backup en el mismo disco del VPS
# muere junto con el disco. Configurar una vez con `rclone config`.
#
# Restore (ENSAYAR cada 6 meses — ver docs/OPERACION.md; un backup no probado es una esperanza):
#   zcat backups/reservakids_FECHA.sql.gz | docker compose exec -T db \
#       sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
set -euo pipefail

cd "$(dirname "$0")/.."

DEST="${1:-./backups}"
RETENCION_DIAS=14
FECHA="$(date +%Y%m%d_%H%M%S)"
ARCHIVO="$DEST/reservakids_$FECHA.sql.gz"

mkdir -p "$DEST"

# pg_dump dentro del contenedor db de docker compose
docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' | gzip > "$ARCHIVO"

# Verificación: el dump debe descomprimir y contener el esquema
if ! gzip -t "$ARCHIVO" || ! zcat "$ARCHIVO" | head -50 | grep -q "PostgreSQL database dump"; then
    echo "ERROR: backup corrupto: $ARCHIVO" >&2
    exit 1
fi

# Retención
find "$DEST" -name 'reservakids_*.sql.gz' -mtime "+$RETENCION_DIAS" -delete

echo "OK: $ARCHIVO ($(du -h "$ARCHIVO" | cut -f1))"

# Copia offsite (falla #7): falla con ruido si rclone no está instalado o el remoto no existe —
# mejor un cron en rojo hoy que descubrir en el desastre que nunca se subió nada.
if [[ -n "${RCLONE_REMOTE:-}" ]]; then
    rclone copy "$ARCHIVO" "$RCLONE_REMOTE"
    echo "OK offsite: $RCLONE_REMOTE/$(basename "$ARCHIVO")"
fi
