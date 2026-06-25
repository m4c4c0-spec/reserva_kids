#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if [ ! -f .env ]; then
  echo "ERROR: No existe .env en la raíz del proyecto"
  exit 1
fi

set -a; . ./.env; set +a

PHONE_ID="${WHATSAPP_PHONE_ID:-}"
TOKEN="${WHATSAPP_TOKEN:-}"
ENABLED="${WHATSAPP_ENABLED:-false}"
TO="${1:-}"

echo "═══════════════════════════════════════════════"
echo "  Prueba WhatsApp — ReservaKids"
echo "═══════════════════════════════════════════════"
echo ""
echo "  Enabled:   $ENABLED"
echo "  Phone ID:  ${PHONE_ID:-<vacío>}"
echo "  Token:     ${TOKEN:+${TOKEN:0:10}...}"
echo ""

if [ "$ENABLED" != "true" ]; then
  echo "WHATSAPP_ENABLED no es 'true'. Activa WhatsApp en .env:"
  echo "  WHATSAPP_ENABLED=true"
  exit 1
fi

if [ -z "$PHONE_ID" ] || [ -z "$TOKEN" ]; then
  echo "Faltan WHATSAPP_PHONE_ID o WHATSAPP_TOKEN en .env"
  echo "Obtenlos desde: Meta Business Suite > WhatsApp > API Setup"
  exit 1
fi

if [ -z "$TO" ]; then
  echo "Uso: $0 <numero_con_codigo_pais>"
  echo "Ejemplo: $0 56912345678"
  echo ""
  echo "El numero debe incluir codigo de pais sin el signo +"
  exit 1
fi

TO_CLEAN=$(echo "$TO" | tr -cd '0-9')

echo "Enviando mensaje de prueba a +${TO_CLEAN} ..."
echo ""

PAYLOAD=$(python3 -c "
import json, os
print(json.dumps({
    'messaging_product': 'whatsapp',
    'to': os.environ['WA_TO'],
    'type': 'text',
    'text': {'body': 'ReservaKids - Mensaje de prueba. Si recibes esto, la configuracion de WhatsApp es correcta.'}
}))
" WA_TO="$TO_CLEAN")

RESPONSE=$(curl -s -w "\n%{http_code}" \
  -X POST \
  "https://graph.facebook.com/v22.0/${PHONE_ID}/messages" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

HTTP_CODE=$(echo "$RESPONSE" | tail -1)
BODY=$(echo "$RESPONSE" | head -n -1)

echo "HTTP Status: $HTTP_CODE"
echo "Response: $BODY"
echo ""

if [ "$HTTP_CODE" -ge 200 ] && [ "$HTTP_CODE" -lt 300 ]; then
  echo "WHATSAPP ENVIADO correctamente a +${TO_CLEAN}"
  echo "Revisa tu WhatsApp."
else
  echo "FALLO al enviar WhatsApp (HTTP $HTTP_CODE)"
  echo ""
  echo "Posibles causas:"
  echo "  - Token expirado o invalido"
  echo "  - Phone ID incorrecto"
  echo "  - El numero destino no tiene WhatsApp"
  echo "  - La cuenta de WhatsApp Business no esta aprobada"
  exit 1
fi
