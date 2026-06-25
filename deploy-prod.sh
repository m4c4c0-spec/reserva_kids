#!/usr/bin/env bash
# ── DulceVida — deploy a VPS (Caddy en el host + Docker compose) ──
#
# Uso:
#   1. Clonar el repo en el VPS:  git clone <repo> && cd reserva_kids
#   2. Copiar .env.example → .env y editar con secrets reales + DOMAIN
#   3. ./deploy-prod.sh            (primer deploy o updates posteriores)
#
# Requisitos del VPS: Docker, docker compose v2, Caddy (apt), git.
# El script es idempotente: sirve para instalar desde cero o para publicar
# una nueva versión tras `git pull`.
set -euo pipefail

cd "$(dirname "$0")"

# ─── Colores ───
ROJO='\033[0;31m'; VERDE='\033[0;32m'; AMAR='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
ok()    { printf "${VERDE}✔${NC} %s\n" "$1"; }
warn()  { printf "${AMAR}⚠${NC} %s\n" "$1"; }
err()   { printf "${ROJO}✘${NC} %s\n" "$1" >&2; }
info()  { printf "${CYAN}ℹ${NC} %s\n" "$1"; }

# ─── Cargar .env ───
if [ ! -f .env ]; then
  err "No existe .env. Copia .env.example y edita los secrets:"
  err "  cp .env.example .env && nano .env"
  exit 1
fi
set -a; . ./.env; set +a

# ─── Validaciones críticas ───
falta=0
[ -z "${DOMAIN:-}" ]                  && { err "DOMAIN no definido en .env (ej: reservas.fiestasglobito.cl)"; falta=1; }
[ -z "${POSTGRES_PASSWORD:-}" ]       && { err "POSTGRES_PASSWORD no definido en .env"; falta=1; }
[ -z "${JWT_SECRET:-}" ]              && { err "JWT_SECRET no definido en .env"; falta=1; }
[ "${POSTGRES_PASSWORD:-}" = "cambiar-en-produccion" ] && { err "POSTGRES_PASSWORD sigue siendo el default — cámbialo"; falta=1; }
[ "${JWT_SECRET:-}" = "cambiar-por-secreto-de-64-bytes-minimo" ] && { err "JWT_SECRET sigue siendo el default — cámbialo"; falta=1; }
if [[ "${DOMAIN:-}" =~ [/\|\&\;] ]]; then { err "DOMAIN contiene caracteres invalidos: $DOMAIN"; falta=1; } fi
if ! command -v caddy &>/dev/null; then { err "Caddy no instalado. Ejecuta: sudo bash scripts/setup-vps.sh"; falta=1; } fi
if [ "$falta" = 1 ]; then exit 1; fi

# ─── Auto-generar GRAFANA_ADMIN_PASSWORD si está vacía (y persistirla) ───
if [ -z "${GRAFANA_ADMIN_PASSWORD:-}" ]; then
  GRAFANA_ADMIN_PASSWORD="$(openssl rand -base64 18 | tr -d '/+=' | cut -c1-20)"
  if grep -q '^GRAFANA_ADMIN_PASSWORD=' .env; then
    sed -i "s|^GRAFANA_ADMIN_PASSWORD=.*|GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}|" .env
  else
    echo "GRAFANA_ADMIN_PASSWORD=${GRAFANA_ADMIN_PASSWORD}" >> .env
  fi
  set -a; . ./.env; set +a
  warn "GRAFANA_ADMIN_PASSWORD autogenerada y guardada en .env (acceso por túnel SSH al puerto 3002)"
fi

# ─── Asegurar que las URLs de prod apunten al dominio (si el usuario no las seteó) ───
# Solo avisamos si quedan en localhost — el usuario puede querer overrides manuales.
if [[ "${FRONTEND_URL:-}" == http://localhost* ]]; then
  warn "FRONTEND_URL sigue en localhost — sed a https://${DOMAIN}"
  sed -i "s|^FRONTEND_URL=.*|FRONTEND_URL=https://${DOMAIN}|" .env
  set -a; . ./.env; set +a
fi
if [[ "${API_URL:-}" == http://localhost* ]]; then
  warn "API_URL sigue en localhost — sed a https://${DOMAIN} (la API se sirve en /api/* vía Caddy)"
  sed -i "s|^API_URL=.*|API_URL=https://${DOMAIN}|" .env
  set -a; . ./.env; set +a
fi
if [[ "${CORS_ALLOWED_ORIGINS:-}" == http://localhost* ]]; then
  warn "CORS_ALLOWED_ORIGINS sigue en localhost — sed a https://${DOMAIN}"
  sed -i "s|^CORS_ALLOWED_ORIGINS=.*|CORS_ALLOWED_ORIGINS=https://${DOMAIN}|" .env
  set -a; . ./.env; set +a
fi

# ─── Caddyfile en el host ───
info "Generando /etc/caddy/Caddyfile para ${DOMAIN}…"
CADDY_TMP="$(mktemp)"
sed "s/TU_DOMINIO/${DOMAIN}/g" Caddyfile > "$CADDY_TMP"
if [ -w /etc/caddy ] || sudo -n true 2>/dev/null; then
  sudo cp "$CADDY_TMP" /etc/caddy/Caddyfile
  sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
  sudo systemctl reload caddy || sudo systemctl restart caddy
  ok "Caddy recargado (HTTPS auto con Let's Encrypt)"
else
  warn "No hay sudo sin password. Caddyfile generado en ./Caddyfile.generated"
  warn "Cópialo manualmente:  sudo cp Caddyfile.generated /etc/caddy/Caddyfile && sudo systemctl reload caddy"
  cp "$CADDY_TMP" Caddyfile.generated
fi
rm -f "$CADDY_TMP"

# ─── Docker compose (solo servicios core de prod; sin mailpit ni observabilidad) ───
info "Construyendo y levantando el stack de producción…"
docker compose \
  -f docker-compose.yml \
  -f compose.prod.yml \
  up -d --build \
  db backend frontend-public frontend-panel backup

# ─── Esperar healthchecks ───
info "Esperando a que el backend esté sano…"
for i in $(seq 1 40); do
  if docker compose -f docker-compose.yml -f compose.prod.yml ps backend | grep -q "healthy"; then
    ok "Backend healthy"
    break
  fi
  [ "$i" = 40 ] && { err "Backend no alcanzo 'healthy' en 80s — revisa: docker compose logs backend"; exit 1; }
  sleep 2
done

# ─── Resumen ───
echo ""
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo -e "${VERDE}  DulceVida desplegado en https://${DOMAIN}${NC}"
echo -e "${CYAN}════════════════════════════════════════════════════════════${NC}"
echo ""
echo "  Sitio público (papás):     https://${DOMAIN}"
echo "  Panel del dueño:           https://${DOMAIN}/negocios_duenos"
echo "  Acceso clientes:           https://${DOMAIN}/clientes/entrar"
echo "  Staff:                     https://${DOMAIN}/staff/entrar"
echo "  Admin (vos):               https://${DOMAIN}/admin/login"
echo ""
if [ "${RESERVAKIDS_SINGLE_TENANT_ENABLED:-false}" = "true" ]; then
  echo "  Negocio (single-tenant):   ${RESERVAKIDS_SINGLE_TENANT_NOMBRE:-?}"
  echo "  Slug:                      ${RESERVAKIDS_SINGLE_TENANT_SLUG:-?}"
  echo "  Admin del negocio:         ${RESERVAKIDS_SINGLE_TENANT_ADMIN_EMAIL:-?}"
  echo "  (la contraseña es RESERVAKIDS_SINGLE_TENANT_ADMIN_PASSWORD en .env)"
  echo ""
fi
echo "  Próximos pasos:"
echo "    1. Entra al panel con el email/password del dueño"
echo "    2. Carga tus servicios y horarios desde /panel/servicios y /panel/calendario"
echo "    3. Comparte el link https://${DOMAIN} con tus clientes"
echo ""
echo "  Para actualizar tras un cambio de código:"
echo "    git pull && ./deploy-prod.sh"
echo ""
