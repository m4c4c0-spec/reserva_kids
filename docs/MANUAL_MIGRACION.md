# Manual de migración — ReservaKids

Especificación **independiente del lenguaje** para reimplementar el sistema en otro stack (Python, Node/TypeScript, Go, C#, etc.) sin releer todo el código. Lo que define al sistema no es Java: son los **contratos** de este manual. Si tu nueva implementación cumple las secciones 2–7, es un reemplazo válido.

---

## 1. Arquitectura a preservar (Onion / Puertos y Adaptadores)

```
┌─ Infraestructura ─────────────────────────────────────────────┐
│  HTTP/REST · Repositorios (BD) · Email · WhatsApp links · JWT │
│   ┌─ Aplicación ───────────────────────────────────────────┐  │
│   │  Casos de uso: CrearReserva, Cotizar, Confirmar,       │  │
│   │  Cancelar, RegistrarPago, GestionarCalendario, Auth,   │  │
│   │  ExpirarPendientes                                     │  │
│   │   ┌─ Dominio ──────────────────────────────────────┐   │  │
│   │   │  Entidades + máquina de estados + puertos      │   │  │
│   │   │  (NO depende de framework, BD ni HTTP)         │   │  │
│   │   └────────────────────────────────────────────────┘   │  │
│   └────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────┘
```

Regla de dependencias: **siempre hacia adentro**. El dominio no importa nada del framework.

### Equivalencias por stack

| Componente (Java actual) | Python | Node/TS | Go | C#/.NET |
|---|---|---|---|---|
| Spring Boot (web) | FastAPI / Flask | NestJS / Fastify | chi / echo | ASP.NET Core |
| Spring Data JPA | SQLAlchemy | Prisma / TypeORM | sqlc / pgx | EF Core |
| Flyway | Alembic | Prisma Migrate / node-pg-migrate | golang-migrate | EF Migrations |
| Spring Security + jjwt | python-jose + passlib | jsonwebtoken + bcrypt | golang-jwt + bcrypt | ASP.NET Identity/JWT Bearer |
| Bean Validation (DTOs) | Pydantic | zod / class-validator | validator | DataAnnotations |
| `@Scheduled` (expiración) | APScheduler / celery beat | node-cron / BullMQ | time.Ticker / gocron | IHostedService |
| `JavaMailSender` | smtplib / resend-py | nodemailer / resend | net/smtp | MailKit |
| JUnit 5 + Mockito | pytest + unittest.mock | vitest/jest | testing + testify | xUnit + Moq |

El **frontend Vue y el esquema PostgreSQL no necesitan cambios**: consumen/exponen los mismos contratos.

---

## 2. Modelo de datos (fuente de verdad: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`)

Entidades: `tenant`, `usuario`, `refresh_token`, `servicio`, `cliente`, `bloque_disponible`, `reserva`, `pago`.

Relaciones: todo cuelga de `tenant` (multi-tenant por columna `tenant_id`); `reserva` referencia `cliente`, `servicio` y `bloque_disponible`; `pago` referencia `reserva`.

**Invariantes que la BD debe garantizar en cualquier stack:**
1. Índice único parcial — un bloque solo puede tener UNA reserva activa:
   ```sql
   CREATE UNIQUE INDEX ux_reserva_bloque_activa ON reserva(bloque_id)
       WHERE estado IN ('PENDIENTE','COTIZADA','CONFIRMADA');
   ```
2. `UNIQUE (tenant_id, fecha, hora_inicio)` en `bloque_disponible`.
3. `usuario.email` único global; `tenant.slug` único global.
4. `refresh_token.token_hash` = SHA-256, nunca el token en claro.
5. `cliente` = solo el adulto contratante (Ley 19.628 — jamás datos de niños).

Si migras la BD junto al lenguaje, reusa el SQL tal cual; Flyway/Alembic/golang-migrate consumen el mismo archivo con mínimos ajustes.

---

## 3. Máquina de estados de la reserva (regla de dominio central)

```
PENDIENTE → COTIZADA → CONFIRMADA → REALIZADA
    └──────────┴────────────┴──→ CANCELADA   (desde cualquier estado activo)
```

- Estados finales (`REALIZADA`, `CANCELADA`) son inmutables.
- No se pueden saltar estados (PENDIENTE → CONFIRMADA es inválido).
- Implementar como función pura `puedeTransicionar(desde, hacia)` en el dominio y **testearla exhaustivamente** (ver `EstadoReservaTest` como especificación ejecutable).

Estados del bloque, sincronizados por los casos de uso:
`DISPONIBLE → EN_ESPERA` (al crear solicitud) `→ CONFIRMADO` (al confirmar) `→ DISPONIBLE` (al cancelar/expirar).

---

## 4. Algoritmo anti doble-reserva (RNF-05 — el corazón del producto)

En la transacción de "crear solicitud pública", en este orden:

```
1. tenant   ← buscar por slug, estado ACTIVO            (404 si no)
2. servicio ← buscar por id + tenant_id, activo          (404 si no)
3. UPDATE bloque_disponible SET estado='EN_ESPERA'
   WHERE id=? AND tenant_id=? AND estado='DISPONIBLE'
   → si filas_afectadas == 0 ⇒ ABORTAR con 409
4. cliente  ← upsert por (tenant_id, telefono)
5. INSERT reserva (estado PENDIENTE)
   → si viola ux_reserva_bloque_activa ⇒ 409 (rollback total, paso 3 incluido)
6. notificar (email + wa.me) — fuera de la ruta crítica (async),
   y un fallo de notificación NUNCA aborta la reserva
```

Todo dentro de **una transacción**. La doble barrera (UPDATE condicionado + índice único) debe sobrevivir a la migración: es lo que promete el producto (riesgo #4 del SDLC). Escribir un test de concurrencia antes de lanzar.

**Expiración**: job cada 15 min — `PENDIENTE` con `creada_en < now() - 48h` ⇒ `CANCELADA` + bloque a `DISPONIBLE`. Idempotente.

---

## 5. Contrato REST (el frontend depende de esto, no lo cambies)

```
# Público (sin auth, rate-limited 30 req/min/IP)
GET  /api/public/{slug}                       → {nombre, slug, servicios[]}
GET  /api/public/{slug}/disponibilidad?mes=YYYY-MM → bloques DISPONIBLE futuros
POST /api/public/{slug}/reservas              → 201 reserva | 409 conflicto

# Auth
POST /api/auth/register {nombreNegocio, slug, email, password} → 201 tokens
POST /api/auth/login    {email, password}                      → tokens
POST /api/auth/refresh  {refreshToken}        → tokens nuevos (rotación)
POST /api/auth/logout                          → 204 (revoca refresh tokens)
  tokens = {accessToken, refreshToken, slug, nombreNegocio}

# Panel (Bearer JWT)
GET/POST       /api/servicios     · PUT/DELETE /api/servicios/{id}
GET/POST       /api/calendario/bloques?mes=   · DELETE /api/calendario/bloques/{id}
GET            /api/reservas?estado=&page=&size=   (paginado)
PUT            /api/reservas/{id}/cotizar   {totalClp, seniaClp}
PUT            /api/reservas/{id}/confirmar
PUT            /api/reservas/{id}/cancelar  {motivo?}
POST           /api/reservas/{id}/pagos     {montoClp, medio, comprobanteUrl?}
```

- Errores **uniformes**: `{timestamp, status, error, message}` con 400/401/403/404/409/429/500. El 409 es semántico: "bloque tomado" — el frontend recarga disponibilidad al recibirlo.
- `ReservaResponse` incluye campos calculados: `pagadoClp` (SUM de pagos), `saldoClp` (total − pagado) y `linkWhatsApp` (`https://wa.me/{telefono_solo_digitos}?text={mensaje urlencoded}`).
- DTOs con validación de entrada (longitudes/máximos como en `application/dto/`).

---

## 6. Requisitos de seguridad (RNF-02 — no negociables en ningún stack)

| Requisito | Valor |
|---|---|
| Hash de contraseñas | bcrypt ≥ 10 rounds |
| Access token | JWT firmado (HS256, secreto ≥ 32 chars), TTL 15 min, verificar `iss`+`aud`+`exp`+firma |
| Claims del access | `sub` = usuario_id, `tenantId`, `rol` |
| Refresh token | opaco aleatorio (≥ 48 bytes CSPRNG), TTL 7 días, **rotación en cada uso**, persistir solo SHA-256, revocable |
| Multi-tenant | el `tenant_id` sale SIEMPRE del token verificado, jamás del body/query; toda query lo filtra |
| Queries | siempre parametrizadas (ORM o placeholders) |
| Secretos | solo variables de entorno (`.env` fuera del repo) |
| Rate limiting | endpoints públicos y de auth (30/min/IP de referencia) |
| CORS | restrictivo al origen del frontend |
| Usuario de BD | mínimo privilegio (sin SUPERUSER/CREATEDB) |

---

## 7. Checklist de migración

1. [ ] Levantar PostgreSQL con el **mismo** `V1__esquema_inicial.sql` (adaptar al gestor de migraciones del nuevo stack).
2. [ ] Implementar dominio puro: enums + `puedeTransicionar` + entidades. Portar primero `EstadoReservaTest` — es la especificación.
3. [ ] Implementar casos de uso en el orden del flujo: Auth → Servicios → Calendario → CrearSolicitudPublica (§4) → Cotizar/Confirmar/Cancelar/Pagos → Expiración.
4. [ ] Exponer el contrato REST de §5 **byte-compatible** (mismos paths, payloads y códigos).
5. [ ] Cumplir la tabla de seguridad de §6.
6. [ ] Portar los tests de `ReservaServiceTest` (conflicto 409, aislamiento de tenant, sincronía bloque-reserva).
7. [ ] **Test de concurrencia**: N requests paralelos al mismo bloque ⇒ exactamente 1 reserva creada.
8. [ ] Apuntar el frontend (`VITE_API_URL`) al nuevo backend — no requiere cambios si §5 se respetó.
9. [ ] Verificación E2E: registro → servicio → bloque → solicitud pública → cotizar → pago → confirmar → cancelar (libera bloque).
10. [ ] Adaptar `Dockerfile` del backend y el job `backend` de `.github/workflows/ci.yml`; `scripts/backup_db.sh` no cambia.

### Trampas conocidas
- **No** mover la unicidad del bloque a lógica de aplicación: sin el índice único parcial hay carrera entre el SELECT y el INSERT.
- El UPDATE del paso 3 (§4) y el INSERT deben compartir transacción: si el INSERT falla, el bloque debe volver a `DISPONIBLE` por rollback.
- El refresh token se rota: si el cliente reusa uno viejo debe recibir 401 (señal de robo).
- `CLP` es entero (sin decimales) en todo el sistema.
- Zona horaria: timestamps en `TIMESTAMPTZ` (UTC en BD); el frontend formatea con `es-CL`.
