#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────
# ReservaKids — Deploy DEMO en un solo comando
# ──────────────────────────────────────────────────────────────────
# Levanta el stack completo (BD, backend, frontend dev, Mailpit)
# con TODO generado automáticamente: secretos, admin, email.
# CERO dependencias externas — todo local.
#
# Uso:
#   chmod +x scripts/deploy-demo.sh
#   ./scripts/deploy-demo.sh
#
# Al terminar:
#   ./scripts/deploy-demo.sh --down   # apaga y limpia
# ──────────────────────────────────────────────────────────────────
set -euo pipefail
cd "$(dirname "$0")/.."

# ── Colores ───────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; NC='\033[0m'
info()  { echo -e "${BLUE}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()   { echo -e "${RED}[ERROR]${NC} $*"; }

# ── Apagar stack demo ─────────────────────────────────────────────
if [[ "${1:-}" == "--down" ]]; then
    info "Apagando stack demo..."
    docker compose --env-file .env.demo down -v 2>/dev/null || true
    ok "Stack apagado y volúmenes eliminados"
    exit 0
fi

# ── Prerequisitos ─────────────────────────────────────────────────
for cmd in docker openssl; do
    if ! command -v "$cmd" &>/dev/null; then
        err "$cmd no está instalado. Instálalo primero."
        exit 1
    fi
done

if ! docker compose version &>/dev/null; then
    err "docker compose no disponible. Instala Docker Compose v2."
    exit 1
fi

# ── Generar secretos ──────────────────────────────────────────────
info "Generando secretos para demo..."

ENV_FILE=".env.demo"

PG_PASS=$(openssl rand -base64 24)
JWT_SECRET=$(openssl rand -base64 64)
CRED_KEY=$(openssl rand -base64 32)

# ── Escribir .env.demo ────────────────────────────────────────────
info "Escribiendo $ENV_FILE ..."

cat > "$ENV_FILE" <<EOF
# ── Generado por deploy-demo.sh ── $(date)
# DEMO: cero dependencias externas. Todo es local.

# PostgreSQL
POSTGRES_DB=reservakids
POSTGRES_USER=reservakids_app
POSTGRES_PASSWORD=$PG_PASS

# Backend: apunta al contenedor 'db' del compose
DB_URL=jdbc:postgresql://db:5432/reservakids
DB_USER=reservakids_app
DB_PASSWORD=$PG_PASS

# JWT
JWT_SECRET=$JWT_SECRET
JWT_ACCESS_MINUTES=15
JWT_REFRESH_DAYS=7

# Cifrado de credenciales (MP, Khipu) en BD
CRED_ENC_KEY=$CRED_KEY

# CORS — frontend de Vite en dev
CORS_ALLOWED_ORIGINS=http://localhost:5173

# Cookie HttpOnly: false porque dev corre en HTTP (localhost)
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax

# Links en emails de reset / magic link
FRONTEND_URL=http://localhost:5173

# API URL (base de webhook MP — en demo no se usa)
API_URL=http://localhost:8080

# Email: Mailpit del compose captura todo (ver http://localhost:8025)
MAIL_HOST=mailpit
MAIL_PORT=1025
MAIL_USERNAME=
MAIL_PASSWORD=
MAIL_SMTP_AUTH=false
MAIL_SMTP_STARTTLS=false
MAIL_FROM=no-reply@reservakids.cl

# Expiración
RESERVA_EXPIRACION_HORAS=48
COTIZACION_EXPIRACION_DIAS=14
CITA_PAGO_EXPIRACION_MIN=30

# Reset + magic link
RESET_PASSWORD_MINUTOS=30
MAGIC_LINK_MINUTOS=10

# WhatsApp: stub (solo log)
WHATSAPP_ENABLED=false

# Ley 21.719
RETENCION_CLIENTE_MESES=24
TENANT_PURGA_DIAS=90

# Zona horaria
APP_TIMEZONE=America/Santiago

# No estamos detrás de reverse proxy
TRUST_PROXY=false

# Rate limiting (valores de dev)
RATE_LIMIT_PUBLIC=60
RATE_LIMIT_AUTH=30
RATE_LIMIT_CLIENTE_AUTH=30
RATE_LIMIT_ADMIN_AUTH=20
RATE_LIMIT_PANEL=120
RATE_LIMIT_WEBHOOK=60

# Khipu
KHIPU_API_BASE=https://api.khipu.com
KHIPU_WEBHOOK_SIG_MAX_AGE_MINUTES=5
MP_WEBHOOK_SIG_MAX_AGE_MINUTES=5

# ── Bootstrap: admin de plataforma ──
# Al primer arranque se crea este admin (si no existe ya).
# Cambia el email si quieres tus propias credenciales.
ADMIN_BOOTSTRAP_EMAIL=admin@reservakids.demo
ADMIN_BOOTSTRAP_PASSWORD=demo123
ADMIN_BOOTSTRAP_NOMBRE=Administrador
EOF

ok "$ENV_FILE creado"

# ── Docker Compose ─────────────────────────────────────────────────
info "Descargando imágenes y construyendo backend..."
docker compose --env-file "$ENV_FILE" pull db mailpit 2>/dev/null || true
docker compose --env-file "$ENV_FILE" build backend

info "Levantando servicios (BD + Mailpit + Backend)..."
docker compose --env-file "$ENV_FILE" up -d

# ── Esperar healthcheck ────────────────────────────────────────────
info "Esperando a que el backend esté healthy (puede tardar ~60s)..."
for i in $(seq 1 30); do
    status=$(docker compose --env-file "$ENV_FILE" ps backend --format json 2>/dev/null \
        | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('Health',''))" 2>/dev/null || echo "")
    if [[ "$status" == "healthy" ]]; then
        ok "Backend healthy tras ${i}x2 segundos"
        break
    fi
    sleep 2
done

if [[ "$status" != "healthy" ]]; then
    warn "Backend aún no está healthy. Revisa los logs:"
    warn "  docker compose --env-file .env.demo logs backend"
fi

# ── Frontend ───────────────────────────────────────────────────────
if [[ -f frontend/package.json ]]; then
    info "Instalando dependencias del frontend..."
    (cd frontend && npm ci 2>/dev/null || npm install)
    ok "Frontend listo"
else
    warn "No se encontró frontend/package.json. Saltando frontend."
fi

# ── Resumen ────────────────────────────────────────────────────────
echo ""
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  DEMO ReservaKids v0.2 lista${NC}"
echo -e "${GREEN}═══════════════════════════════════════════════════${NC}"
echo ""
echo -e "  ${CYAN}Frontend:${NC}   cd frontend && npm run dev"
echo -e "               → http://localhost:5173"
echo ""
echo -e "  ${CYAN}Backend:${NC}    http://localhost:8080"
echo -e "  ${CYAN}Actuator:${NC}   http://localhost:8080/actuator/health"
echo -e "  ${CYAN}Mailpit UI:${NC} http://localhost:8025   (todos los correos capturados)"
echo ""
echo -e "  ${CYAN}Admin plataforma:${NC}"
echo -e "      Email:    admin@reservakids.demo"
echo -e "      Password: demo123"
echo ""
echo -e "  ${YELLOW}Flujo demo desde cero:${NC}"
echo "  1. Abre http://localhost:5173"
echo "  2. El admin inicia sesión en /admin/login"
echo "  3. Registra un negocio desde /registro (o POST /api/public/auth/registro)"
echo "  4. El dueño configura servicios + horarios + bloques"
echo "  5. Cliente público solicita cotización → el dueño cotiza"
echo "  6. Cliente se registra y agenda una cita (sin pasarela: link de pago ausente)"
echo ""
echo -e "  ${YELLOW}Ver correos:${NC} http://localhost:8025 (reset, magic link, notificaciones)"
echo ""
echo -e "  ${YELLOW}Logs:${NC}"
echo "      docker compose --env-file .env.demo logs -f backend"
echo ""
echo -e "  ${YELLOW}Apagar:${NC}"
echo "      ./scripts/deploy-demo.sh --down"
echo ""
