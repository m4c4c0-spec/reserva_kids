#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ROJO='\033[0;31m'; VERDE='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'

echo ""
echo -e "${CYAN}═══════════════════════════════════════════════${NC}"
echo -e "${CYAN}  Prueba de Notificaciones — ReservaKids${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════${NC}"
echo ""
echo "  1) Probar SMTP (email)"
echo "  2) Probar WhatsApp"
echo "  3) Probar ambos"
echo "  4) Verificar estado via API (requiere backend corriendo)"
echo ""
read -rp "Opcion [1-4]: " OPT

case "$OPT" in
  1)
    EMAIL_TO="${1:-}"
    if [ -n "$EMAIL_TO" ]; then
      bash scripts/test-smtp.sh "$EMAIL_TO"
    else
      bash scripts/test-smtp.sh
    fi
    ;;
  2)
    WA_NUM="${1:-}"
    if [ -n "$WA_NUM" ]; then
      bash scripts/test-whatsapp.sh "$WA_NUM"
    else
      bash scripts/test-whatsapp.sh
    fi
    ;;
  3)
    echo "--- SMTP ---"
    bash scripts/test-smtp.sh "${1:-}" || true
    echo ""
    echo "--- WhatsApp ---"
    bash scripts/test-whatsapp.sh "${2:-}" || true
    ;;
  4)
    set -a; . ./.env; set +a
    API="${API_URL:-http://localhost:8080}"
    echo "Consultando ${API}/api/sistema/notificaciones ..."
    echo ""
    echo "Necesitas un token de DUENO. Login:"
    echo "  curl -X POST ${API}/api/auth/login -H 'Content-Type: application/json' \\"
    echo "    -d '{\"email\":\"<tu-email>\",\"password\":\"<tu-password>\"}'"
    echo ""
    echo "Luego:"
    echo "  curl ${API}/api/sistema/notificaciones -H 'Authorization: Bearer <token>'"
    ;;
  *)
    echo "Opcion invalida"
    exit 1
    ;;
esac
