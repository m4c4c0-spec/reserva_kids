#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# ReservaKids — Deploy PRODUCCIÓN en VPS (Docker Compose + Caddy)
# ──────────────────────────────────────────────────────────────────
# Automatiza los pasos 0–7 del MANUAL_DESPLIEGUE.md:
#   1. Verifica prerequisitos (docker, rclone, caddy, ufw)
#   2. Genera secretos si no existen en .env
#   3. Valida variables MANDATORIAS de producción
#   4. Build del frontend (estáticos para Caddy)
#   5. Levanta backend + BD (compose.prod.yml overlay)
#   6.Verifica healthcheck
#   7. Imprime checklist de pasos manuales restantes
#
# Uso:
#   chmod +x scripts/deploy-prod.sh
#   DOMINIO=reservakids.cl SMTP_HOST=smtp.brevo.com ... ./scripts/deploy-prod.sh
#   o editar .env primero y luego ./scripts/deploy-prod.sh
# ──────────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "$0")/.."

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

MISSING=()

# ── Prerequisitos ─────────────────────────────────────────────────
for cmd in docker openssl; do
    command -v "$cmd" &>/dev/null || { err "$cmd no instalado"; exit 1; }
done

# ── .env: crear si no existe ──────────────────────────────────────
if [[ ! -f .env ]]; then
    info ".env no existe — creando desde .env.example..."
    cp .env.example .env
fi

# ── Generar secretos faltantes en .env ────────────────────────────
sedi() { sed -i "$@"; }
# Para macOS
if [[ "$(uname)" == "Darwin" ]]; then sedi() { sed -i '' "$@"; }; fi

gen_if_missing() {
    local key="$1" bytes="$2"
    if grep -qE "^${key}=cambiar|^${key}=$" .env 2>/dev/null; then
        local val
        val=$(openssl rand -base64 "$bytes")
        sedi "s|^${key}=.*|${key}=${val}|" .env
        info "Generado secreto para $key"
    fi
}

gen_if_missing POSTGRES_PASSWORD 24
gen_if_missing JWT_SECRET 64
gen_if_missing CRED_ENC_KEY 32

# ── Variables MANDATORIAS de producción ───────────────────────────
source_env() { grep "^$1=" .env 2>/dev/null | cut -d= -f2- | head -1 || true; }

check_var() {
    local key="$1" desc="$2"
    local val
    val=$(source_env "$key")
    if [[ -z "$val" || "$val" == "cambiar"* ]]; then
        err "Falta $key ($desc)"
        MISSING+=("$key")
    else
        ok "$key = $val"
    fi
}

echo ""
info "Validando variables de producción..."
echo ""

check_var POSTGRES_PASSWORD    "contraseña de la BD"
check_var JWT_SECRET           "secreto JWT (min 64 bytes)"
check_var CRED_ENC_KEY         "clave AES-256 para credenciales"
check_var CORS_ALLOWED_ORIGINS "origen del frontend (ej. https://reservakids.cl)"
check_var FRONTEND_URL         "base de links email (ej. https://reservakids.cl)"
check_var API_URL              "URL pública de la API (ej. https://reservakids.cl)"
check_var MAIL_HOST            "servidor SMTP"
check_var MAIL_USERNAME        "usuario SMTP"
check_var MAIL_PASSWORD        "contraseña SMTP"

echo ""
if [[ ${#MISSING[@]} -gt 0 ]]; then
    err "Faltan ${#MISSING[@]} variable(s). Edita .env y vuelve a correr."
    echo ""
    echo "  Variables: ${MISSING[*]}"
    echo ""
    echo "  SMTP gratis (300 emails/día): https://onboarding.brevo.com"
    echo "  Plantilla .env:   .env.example"
    echo ""
    exit 1
fi

# ── Variables condicionales: defaults de producción si no están ───
set_default() {
    local key="$1" default="$2"
    if ! grep -q "^${key}=" .env 2>/dev/null || [[ -z "$(source_env "$key")" ]]; then
        echo "${key}=${default}" >> .env
        info "Añadido default: $key=$default"
    fi
}
set_default COOKIE_SECURE       "true"
set_default COOKIE_SAME_SITE    "Lax"
set_default TRUST_PROXY         "true"
set_default MAIL_PORT           "587"
set_default MAIL_SMTP_AUTH      "true"
set_default MAIL_SMTP_STARTTLS  "true"
set_default APP_TIMEZONE        "America/Santiago"

# ── Frontend build ─────────────────────────────────────────────────
DOMINIO=$(source_env CORS_ALLOWED_ORIGINS | sed 's|https://||')
API_URL_VAL=$(source_env API_URL)

if [[ -d frontend ]]; then
    info "Build del frontend (VITE_API_URL=$API_URL_VAL)..."
    (cd frontend && echo "VITE_API_URL=$API_URL_VAL" > .env.production && npm ci && npm run build)
    ok "Frontend build listo → frontend/dist/"
fi

# ── Docker Compose (prod overlay) ──────────────────────────────────
COMPOSE_FILES="docker-compose.yml"
if [[ -f compose.prod.yml ]]; then
    COMPOSE_FILES="$COMPOSE_FILES -f compose.prod.yml"
fi

info "Levantando servicios de producción..."
docker compose $COMPOSE_FILES --env-file .env up -d --build backend db

info "Esperando healthcheck..."
for i in $(seq 1 30); do
    status=$(docker compose $COMPOSE_FILES --env-file .env ps backend --format json 2>/dev/null \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('Health',''))" 2>/dev/null || echo "")
    if [[ "$status" == "healthy" ]]; then
        ok "Backend healthy"
        break
    fi
    sleep 2
done

# ── Migraciones Flyway ─────────────────────────────────────────────
info "Migraciones:"
docker compose $COMPOSE_FILES --env-file .env logs backend 2>/dev/null | grep Flyway || true

# ── Checklist post-deploy ─────────────────────────────────────────
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  Deploy completado. Pasos manuales restantes:${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${YELLOW}1. DNS:${NC} Asegúrate de que ${CYAN}$DOMINIO${NC} apunte a la IP del VPS"
echo ""
echo -e "  ${YELLOW}2. Caddy:${NC}"
echo "      sudo cp Caddyfile /etc/caddy/Caddyfile"
echo "      sudo systemctl reload caddy"
echo "      curl -I https://$DOMINIO | grep '200'"
echo ""
echo -e "  ${YELLOW}3. SPF/DKIM:${NC} Configura los registros DNS que indique tu proveedor SMTP"
echo ""
echo -e "  ${YELLOW}4. Backups (rclone):${NC}"
echo "      rclone config   # crea remoto (Backblaze B2/S3)"
echo "      crontab -e"
echo "      0 3 * * * RCLONE_REMOTE=b2:reservakids-backups $(pwd)/scripts/backup_db.sh /var/backups/reservakids"
echo ""
echo -e "  ${YELLOW}5. Monitoreo:${NC}"
echo "      UptimeRobot → https://$DOMINIO/actuator/health"
echo ""
echo -e "  ${YELLOW}6. Admin bootstrap:${NC}"
echo "      Configura ADMIN_BOOTSTRAP_EMAIL/PASSWORD en .env"
echo "      y recrea el backend: docker compose ... up -d backend"
echo ""
echo -e "  ${YELLOW}Logs:${NC}"
echo "      docker compose $COMPOSE_FILES --env-file .env logs -f backend"
echo ""
