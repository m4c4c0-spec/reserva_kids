# Checklist de credenciales — Primer despliegue a producción

> Auditoría del `.env` realizada el 2026-06-24. Estado del stack local: 100% operativo
> (11 contenedores sanos vía Docker Compose). Este checklist cubre lo que falta/está mal
> configurado **antes de exponer la primera versión a internet**.

## ⚠️ Condición crítica de despliegue

Todo lo de abajo solo aplica si despliegas con:

```bash
./deploy-prod.sh
# (equivale a: docker compose -f docker-compose.yml -f compose.prod.yml up -d --build)
```

Si arrancas solo con `docker-compose.yml`, te quedas en **modo dev**: cookies inseguras,
Postgres/Grafana expuestos a internet y sin HTTPS.

---

## 🔴 Bloqueantes reales — corregir A MANO en `.env`

| # | Variable | Valor actual | Acción |
|---|---|---|---|
| 1 | `DOMAIN` | `reservas.tudominio.cl` (placeholder) | Poner el dominio real. Es la raíz: `deploy-prod.sh` hace `sed` de FRONTEND_URL/API_URL/CORS a `https://$DOMAIN` y genera el Caddyfile con él. Sin esto no hay certificado TLS ni webhooks válidos. |
| 2 | `MAIL_FROM` | `onboarding@resend.dev` | Es el remitente **sandbox compartido** de Resend: solo entrega a tu propio email. Reset de contraseña y avisos **nunca llegan a clientes reales**. → Verificar dominio en Resend y usar `no-reply@tudominio.cl`. (La API key `MAIL_PASSWORD` `re_…` sí es válida ✓.) **`deploy-prod.sh` NO corrige esto.** |
| 3 | `ADMIN_BOOTSTRAP_PASSWORD` | parece `admin…234` (débil) | Generar fuerte: `openssl rand -base64 18`. Es el admin de plataforma. |
| 4 | `RESERVAKIDS_SINGLE_TENANT_ADMIN_PASSWORD` | parece `dulce…2026` (predecible) | Generar fuerte. Es el login del dueño del negocio. |

## 🟡 Verificar en consolas externas (no es el `.env`, pero rompe el login)

| # | Servicio | Qué verificar |
|---|---|---|
| 5 | Google OAuth | Credenciales presentes ✓, pero **registrar el redirect URI de producción** (`https://reservas.tudominio.cl/oauth2/callback`) en Google Cloud Console. Si no → `redirect_uri_mismatch`. |
| 6 | WhatsApp | `WHATSAPP_TOKEN` (44 chars) huele a **token temporal de 24h** de la app de prueba de Meta (los permanentes de *System User* son mucho más largos). `WHATSAPP_PHONE_ID` parece número de prueba. Verificar token permanente, o poner `WHATSAPP_ENABLED=false` si aún no se usa. |
| 7 | `VITE_API_URL` | `http://localhost:8080` — `deploy-prod.sh` **NO** lo toca (es var de build del frontend). Verificar cómo se construye el frontend en prod; si se hornea en el build debe ser `https://tudominio.cl`. |

## 🟢 Bien configurado / no tocar

- `JWT_SECRET` (64 bytes) y `CRED_ENC_KEY` (AES-256, 32 bytes) — **fuertes** ✓.
- **MercadoPago / Khipu**: NO van en `.env` por diseño — se guardan **cifrados por-tenant en la BD** (con `CRED_ENC_KEY`) desde el panel. No falta nada ✓.
- Autocorregidos por `deploy-prod.sh` + overlay `compose.prod.yml`:
  `FRONTEND_URL`, `API_URL`, `CORS_ALLOWED_ORIGINS` (sed localhost→`https://$DOMAIN`),
  `COOKIE_SECURE=true`, `TRUST_PROXY=true`, `GRAFANA_ADMIN_PASSWORD` (autogenerada),
  y `DB_URL` (el contenedor usa `db:5432` hardcodeado; el `localhost` del `.env` se ignora).

---

## Resumen mínimo para el primer deploy

1. Editar en `.env`: `DOMAIN` real, `MAIL_FROM` con dominio verificado en Resend, las 2 contraseñas de admin.
2. Registrar el redirect URI de Google y validar el token de WhatsApp.
3. Ejecutar `./deploy-prod.sh` (el resto lo resuelve el script).

## Contexto relacionado

- Fix del contenedor `backup` (crash-loop por `dcron`/`setpgid`) resuelto el 2026-06-24:
  `backup/entrypoint.sh` ahora usa un scheduler propio en `sh` (sin daemon cron).
- Manual de despliegue completo: [`MANUAL_DESPLIEGUE.md`](MANUAL_DESPLIEGUE.md).
