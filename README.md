# ReservaKids Digital

Plataforma SaaS multi-tenant de reservas para negocios de cumpleaños infantiles (Victoria, Malleco y La Araucanía). Centraliza catálogo de servicios, calendario de disponibilidad, solicitudes de cotización/reserva, registro de señas y notificaciones (email + links WhatsApp `wa.me`).

> Documentación:
> - SDLC completo: [`docs/Diagramas_y_requerimientos_De_reserva_kids/`](docs/Diagramas_y_requerimientos_De_reserva_kids/)
> - Bitácora paso a paso: [`docs/BITACORA.md`](docs/BITACORA.md)
> - Revisiones de fallas: [`docs/REVISION_FALLAS_1_ANO.md`](docs/REVISION_FALLAS_1_ANO.md) · [`docs/REVISION_FALLAS_2_ANOS.md`](docs/REVISION_FALLAS_2_ANOS.md) · [`docs/REVISION_FALLAS_5_ANOS.md`](docs/REVISION_FALLAS_5_ANOS.md)
> - Runbooks de operación: [`docs/OPERACION.md`](docs/OPERACION.md)
> - Manual de despliegue (VPS+Caddy / Vercel+Railway): [`docs/MANUAL_DESPLIEGUE.md`](docs/MANUAL_DESPLIEGUE.md)
> - Manual de migración a otros lenguajes: [`docs/MANUAL_MIGRACION.md`](docs/MANUAL_MIGRACION.md)

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21 + Spring Boot 3 — Onion Architecture |
| BD | PostgreSQL 16 + Flyway (migraciones versionadas) |
| Frontend | Vue 3 + Vite + Tailwind CSS |
| Auth | Spring Security + JWT (access 15 min / refresh 7 días con rotación) |
| Notificaciones | Email SMTP real (Brevo/Resend, degrada a log sin config) + links `wa.me` |
| Jobs | Expiración de solicitudes (48 h) y cotizaciones (14 días), cierre CONFIRMADA→REALIZADA, anonimización Ley 21.719, limpieza de bloques pasados, purga de tokens, purga de tenants cerrados (90 días) |
| Observabilidad | `/actuator/health` + healthcheck Docker + aviso en panel si el email falla |
| CI | GitHub Actions: test → build (backend y frontend) |
| Backups | `scripts/backup_db.sh` — pg_dump diario verificado, retención 14 días |
| Hosting | Docker Compose (VPS) · alternativa: Vercel (front) + Railway/Render (API) |
| Pagos (P1) | Mercado Pago Checkout Pro |

## Estructura

```
reserva_kids/
├── backend/                  # API Spring Boot (Onion)
│   └── src/main/java/cl/reservakids/
│       ├── domain/           # Entidades, reglas, puertos (no depende de nada)
│       ├── application/      # Casos de uso + DTOs
│       └── infrastructure/   # Controllers REST, seguridad, adaptadores
├── frontend/                 # SPA Vue 3 (página pública + panel del negocio)
├── docs/                     # SDLC, diagramas
├── legacy/                   # Prototipo Flask original (referencia)
└── docker-compose.yml
```

## Levantar en local

```bash
cp .env.example .env          # editar secretos (JWT_SECRET, passwords)

# Base de datos + API
docker compose up -d db
cd backend && mvn spring-boot:run   # requiere Java 21 + Maven (ver abajo)

# Frontend
cd frontend && npm install && npm run dev   # http://localhost:5173
```

Sin Java/Maven locales: `docker compose up --build` compila la API dentro del contenedor.
Para instalarlos con [mise](https://mise.jdx.dev): `mise use -g java@temurin-21 maven`.


## Seguridad (RNF-02)

- bcrypt (strength 10) para contraseñas.
- JWT firmado HS256, verificado con `iss/aud/exp`; access 15 min, refresh 7 días con rotación y revocación en BD.
- Detección de reuso de refresh token: usar un token ya rotado revoca todas las sesiones del usuario (anti-robo).
- Optimistic locking (`@Version`) en reservas: las carreras job-vs-panel reciben 409 en vez de pisarse.
- Toda query filtrada por `tenant_id` (aislamiento multi-tenant estricto).
- Queries parametrizadas (JPA), secretos solo por variables de entorno.
- Rate limiting en endpoints públicos; CORS restrictivo.
- Anti doble-reserva (RNF-05): índice único parcial en `reserva(bloque_id)` para estados activos + `UPDATE ... WHERE estado='DISPONIBLE'` verificando filas afectadas.

## API (contrato MVP)

```
# Público (sin auth, rate-limited)
GET  /api/public/{slug}                      → negocio + servicios
GET  /api/public/{slug}/disponibilidad?mes=  → bloques libres
POST /api/public/{slug}/reservas             → crea solicitud PENDIENTE

# Panel negocio (JWT)
POST /api/auth/register | /login | /refresh
POST /api/auth/logout    {refreshToken}   # revoca aunque el access esté vencido
GET/POST/PUT/DELETE /api/servicios
GET/POST/DELETE     /api/calendario/bloques
GET  /api/reservas?estado=PENDIENTE
PUT  /api/reservas/{id}/cotizar    {totalClp, seniaClp}
PUT  /api/reservas/{id}/confirmar
PUT  /api/reservas/{id}/realizar
PUT  /api/reservas/{id}/cancelar   {motivo}
POST /api/reservas/{id}/pagos      {montoClp, medio, comprobanteUrl, tipo}
POST /api/clientes/{id}/anonimizar          # Ley 21.719: derecho de supresión
GET  /api/sistema/notificaciones            # estado del canal de email (panel)
GET  /api/tenant/export                     # offboarding: export JSON completo del negocio
POST /api/tenant/cerrar    {slugConfirmacion}  # cierre self-service; purga física a los 90 días

# Operación (sin auth)
GET  /actuator/health                       # healthcheck Docker / uptime monitor
```

Errores uniformes `{timestamp, status, error, message}` — 409 para conflicto de bloque.

## Máquina de estados de la reserva

`PENDIENTE → COTIZADA → CONFIRMADA → REALIZADA`, con `CANCELADA` desde cualquier estado activo (expira 48 h / rechazo / cliente desiste).

- `PENDIENTE` expira a las 48 h sin cotizar; `COTIZADA` expira a los 14 días sin respuesta (configurable) — en ambos casos el bloque vuelve a `DISPONIBLE`.
- `CONFIRMADA` pasa a `REALIZADA` desde el panel o automáticamente al día siguiente del evento.

## Privacidad (Ley 21.719)

- Solo datos del adulto contratante; consentimiento expreso (checkbox) en el formulario público.
- Anonimización automática tras `RETENCION_CLIENTE_MESES` (24) de inactividad, y a demanda vía `POST /api/clientes/{id}/anonimizar` — limpia también los comentarios de sus reservas (texto libre = dato personal).
- Offboarding de negocio: export JSON completo + cierre self-service; purga física a los `TENANT_PURGA_DIAS` (90).
