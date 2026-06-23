#!/bin/sh
set -eu

SLUG="${VITE_SINGLE_TENANT_SLUG:-}"

if [ -n "$SLUG" ]; then
  echo "Single-Tenant: slug '${SLUG}' configurado via NUXT_PUBLIC_SINGLE_TENANT_SLUG"
  export NUXT_PUBLIC_SINGLE_TENANT_SLUG="$SLUG"
else
  echo "Single-Tenant: modo multi-tenant (sin slug configurado)"
fi

exec node /app/.output/server/index.mjs
