# Bitácora de desarrollo — ReservaKids

Registro paso a paso de lo realizado. Complementa el SDLC (`ReservaKids_SDLC.md.pdf`).

---

## 2026-06-10 — Sesión 1: Reestructuración completa al stack definitivo

### Paso 0 — Estado inicial
- Existía un prototipo Flask mínimo (MVC en Python): `controllers/services_controller.py`, `models/service.py`, `views/services_view.py` — solo CRUD de servicios, sin auth, sin BD definida.
- Se movió a `legacy/` como referencia (no se borra historia).
- El PDF de requerimientos se movió a `docs/`.
- Se inicializó repo git (rama `main`).

### Paso 1 — Estructura monorepo
```
backend/   → Java 21 + Spring Boot 3, Onion Architecture (SDLC §4.5, RNF-07)
frontend/  → Vue 3 + Vite + Tailwind CSS 4
docs/      → SDLC + esta bitácora + manual de migración
scripts/   → operación (backups)
```
Raíz: `docker-compose.yml` (PostgreSQL 16 + API), `.env.example` (variables documentadas, sin secretos — RNF-02b), `.gitignore`, `README.md`.

### Paso 2 — Esquema de BD (Flyway `V1__esquema_inicial.sql`)
Tablas según ER §4.2: `tenant`, `usuario`, `refresh_token`, `servicio`, `cliente`, `bloque_disponible`, `reserva`, `pago`.

Decisiones clave:
- **RNF-05 (anti doble-reserva)** con doble barrera:
  1. `UPDATE bloque_disponible SET estado='EN_ESPERA' WHERE id=? AND tenant_id=? AND estado='DISPONIBLE'` — verificando filas afectadas (0 filas ⇒ 409).
  2. Índice único parcial: `CREATE UNIQUE INDEX ux_reserva_bloque_activa ON reserva(bloque_id) WHERE estado IN ('PENDIENTE','COTIZADA','CONFIRMADA')`.
- `cliente` solo guarda datos del **adulto contratante** (riesgo #9 del SDLC, Ley 19.628 — nunca datos de menores).
- `refresh_token` guarda solo el **SHA-256** del token, jamás el token en claro.
- `ddl-auto: validate` — el esquema lo gobierna Flyway, nunca Hibernate.

### Paso 3 — Backend Onion (paquete `cl.reservakids`)
- **`domain/`** (no depende de nada):
  - `model/` — entidades JPA + enums. `EstadoReserva` implementa la máquina de estados §4.3 (`puedeTransicionarA`); `Reserva.transicionarA()` la hace cumplir lanzando `TransicionInvalidaException`.
  - `repository/` — puertos (interfaces Spring Data; la implementación la genera el framework en runtime, fuera del dominio).
  - `exception/` — `RecursoNoEncontradoException` (404), `ConflictoBloqueException` (409), `TransicionInvalidaException` (409).
- **`application/`** — casos de uso §4.5: `AuthService`, `ServicioService`, `CalendarioService`, `ReservaService`; DTOs (records con Bean Validation); puertos salientes `TokenPort` y `NotificacionPort`.
- **`infrastructure/`** — `web/` (controllers REST + `GlobalExceptionHandler` con errores uniformes `{timestamp,status,error,message}`), `security/`, `adapter/`.

### Paso 4 — Seguridad (RNF-02)
- bcrypt strength 10.
- JWT HS256 verificado con `iss/aud/exp`; access 15 min; claims `tenantId` y `rol`.
- Refresh 7 días **con rotación**: cada uso revoca el token anterior; logout revoca todos.
- **Aislamiento multi-tenant**: el `tenantId` siempre sale del JWT (`AuthPrincipal`), nunca del request; todos los repos exponen métodos `...AndTenantId`.
- Rate limiting 30 req/min/IP en `/api/public/**` y `/api/auth/**` (ventana fija en memoria; migrar a bucket4j/Redis si hay más de una instancia).
- CORS restrictivo al origen del frontend; API stateless sin cookies (CSRF deshabilitado de forma justificada).

### Paso 5 — Frontend Vue 3
- Rutas: `/{slug}` mini-sitio público (RF-03: catálogo → fecha → formulario RF-05), `/login` (login+registro), `/panel/*` protegido (solicitudes RF-06/07, servicios RF-02, calendario RF-04).
- Pinia para auth (tokens en `sessionStorage`); interceptor axios que ante un 401 hace refresh con rotación y reintenta una vez.
- UI en español, mobile-first (RNF-01), Tailwind 4.

### Paso 6 — Verificación
- `mvn test` (en contenedor `maven:3.9-eclipse-temurin-21`, sin Java local): **BUILD SUCCESS — 5/5 tests** de la máquina de estados.
- `npm run build`: OK (~55 kB gzip).

### Commit inicial
Todo lo anterior quedó como commit inicial en `main`.

---

## 2026-06-10 — Sesión 2: Pendientes del plan de sprints

### Paso 7 — Email transaccional real (sprint 5, RF-08)
- Se agregó `spring-boot-starter-mail`; SMTP configurable por entorno (`MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` en `.env.example` — compatible con Brevo capa gratis o Resend).
- `NotificacionAdapter` ahora envía email real al dueño (primer usuario del tenant) con los datos de la solicitud + link `wa.me` pre-armado.
- **Degradación elegante**: si `MAIL_HOST` está vacío no existe el bean `JavaMailSender` (`ObjectProvider`) y se loguea en su lugar; un fallo de SMTP jamás rompe el flujo de reserva (try/catch + log).
- `@Async` (+`@EnableAsync`): el email no bloquea la respuesta HTTP del cliente.

### Paso 8 — Expiración automática 48 h (criterio de aceptación RF-05)
- `ExpiracionService` con `@Scheduled` cada 15 min (+`@EnableScheduling`): solicitudes `PENDIENTE` con más de `RESERVA_EXPIRACION_HORAS` (48 por defecto) se cancelan vía la máquina de estados, se anota `[Expiración]` en comentarios y el bloque vuelve `EN_ESPERA → DISPONIBLE`.
- Configurable por entorno; idempotente; opera sobre todos los tenants.

### Paso 9 — Tests de aplicación (sprint 6, RNF-07)
- `ReservaServiceTest` (Mockito): flujo feliz de solicitud pública, conflicto de bloque (RNF-05 ⇒ 409 sin efectos colaterales), seña > total rechazada, aislamiento multi-tenant, confirmar marca bloque `CONFIRMADO`, cancelar libera bloque.
- `ExpiracionServiceTest`: cancela vencidas y libera bloques; sin vencidas no toca nada.

### Paso 10 — CI (sprint 1 pendiente)
- `.github/workflows/ci.yml`: jobs paralelos `backend` (setup-java 21 + `mvn test` + `package`) y `frontend` (`npm ci` + `vite build`) en push a `main` y PRs.

### Paso 11 — Backups diarios (RNF-03)
- `scripts/backup_db.sh`: `pg_dump` vía docker compose, gzip, **verificación** del dump (integridad + cabecera), retención 14 días. Ejemplo de cron en el propio script.

### Paso 12 — Herramientas locales
- Se instalaron Java 21 (Temurin) y Maven vía `mise` para desarrollo local sin Docker.

---

## 2026-06-10 — Sesión 3: Revisión de fallas a 1 año

Se auditó el código con la pregunta *"¿qué falla de aquí a un año de operación real?"*. Resultado: **8 fallas encontradas y corregidas** + 7 riesgos aceptados conscientemente. Detalle completo (síntoma, cuándo explotaría, corrección) en [`REVISION_FALLAS_1_ANO.md`](REVISION_FALLAS_1_ANO.md). Resumen:

| # | Falla | Corrección |
|---|---|---|
| 1 | `refresh_token` crece sin límite | Purga diaria 04:30 (`purgarInvalidos`) |
| 2 | "Hoy" en UTC ≠ hora chilena (bloques/disponibilidad corridos de noche) | Bean `Clock` con `APP_TIMEZONE=America/Santiago` |
| 3 | Rate limit colapsaría tras el proxy (1 bucket para todos → 429 global) | `X-Forwarded-For` con `TRUST_PROXY=true` |
| 4 | Negocio podía registrarse con slug `panel`/`login` → página inaccesible | `SLUGS_RESERVADOS` en el registro |
| 5 | `?mes=2026-13` o `?estado=FOO` → 500 + stacktrace (ruido de bots) | Handler → 400 uniforme |
| 6 | `ClassCastException` Integer/Long al validar JWT → nadie se autentica | Lectura `Number.longValue()` |
| 7 | Email fantasma si la transacción hace rollback | Envío AFTER_COMMIT (`TransactionSynchronization`) |
| 8 | Dependencias sin parchar (CVEs) en proyecto unipersonal | Dependabot semanal (maven/npm) y mensual (actions/docker) |

Cambios de configuración: `APP_TIMEZONE` y `TRUST_PROXY` en `.env.example` y `docker-compose.yml`. Test nuevo: purga de tokens en `ExpiracionServiceTest`.

### Pendientes conocidos (post-MVP, §10 del SDLC)
- v1.1: recordatorios automáticos, reportes, ficha de cliente (RF-09/10/12).
- v1.2: pago online de señas (RF-11, Mercado Pago Checkout Pro) + planes con límites (RF-13).
- v2: API oficial WhatsApp, directorio por comuna (RF-14).
- Operación: captcha en el endpoint público (mencionado en §6), deploy productivo (VPS + Caddy o Vercel+Railway).

---

## 2026-06-10 — Sesión 4: Revisión de fallas a 2 años — implementación completa

Pregunta guía: *"¿por qué fallaría el proyecto de aquí a dos años de operación real?"*. El análisis
completo (12 fallas, síntoma + cuándo + corrección) está en [`REVISION_FALLAS_2_ANOS.md`](REVISION_FALLAS_2_ANOS.md);
los procedimientos operativos resultantes, en [`OPERACION.md`](OPERACION.md).

### Paso 13 — Estado heredado: una sesión anterior quedó a medias (y el repo no compilaba)

La migración `V2__pagos_estado_cotizada_y_ley_21719.sql` y parte del dominio existían, pero:
- `ReservaService` usaba `OffsetDateTime` **sin el import** → error de compilación.
- `ReservaController.registrarPago` no pasaba el `usuarioId` que el servicio ya exigía → error de compilación.
- `ReservaServiceTest.solicitud()` pasaba 8 argumentos a un record de 9 (`aceptaDatos`) → error de compilación.
- El backend exigía `aceptaDatos=true` (`@AssertTrue`) pero el formulario público no lo enviaba →
  **toda solicitud pública habría fallado con 400**.
- Los finders de cotizadas/anonimización existían pero **ningún job los invocaba**.

Todo lo anterior quedó corregido en esta sesión.

### Paso 14 — Jobs de ciclo de vida (`ExpiracionService`, ahora con el `Clock` del negocio)

| Job | Cuándo | Qué hace |
|---|---|---|
| `expirarPendientes()` | cada 15 min | (ya existía) PENDIENTE > 48 h → CANCELADA, bloque liberado |
| `expirarCotizadas()` 🆕 | cada hora | COTIZADA > `COTIZACION_EXPIRACION_DIAS` (14) → CANCELADA, bloque liberado (falla #1) |
| `realizarConcluidas()` 🆕 | diario 04:15 | CONFIRMADA con bloque pasado → REALIZADA (falla #2) |
| `purgarRefreshTokens()` | diario 04:30 | (ya existía) limpia tokens revocados/expirados |
| `anonimizarInactivos()` 🆕 | mensual día 1 05:00 | Ley 21.719: clientes sin actividad > `RETENCION_CLIENTE_MESES` (24) se anonimizan; se salta a quien tenga reservas activas (falla #4) |
| `limpiarBloquesPasados()` 🆕 | lunes 04:45 | elimina bloques DISPONIBLE pasados sin reservas que los referencien (falla #11) |

Queries nuevas: `ReservaRepository.findByEstadoConBloqueAnterior()` (join reserva–bloque) y
`BloqueDisponibleRepository.eliminarPasadosSinReserva()` (DELETE con NOT EXISTS).

### Paso 15 — REALIZADA alcanzable (falla #2)

- `ReservaService.realizar()` + `PUT /api/reservas/{id}/realizar`.
- Botón **"Marcar realizada 🎉"** en `SolicitudesView` para reservas CONFIRMADA.
- El barrido diario cubre las que el dueño olvide cerrar. Sin esto, los clientes quedaban
  "con reservas activas" para siempre y la anonimización jamás habría corrido.

### Paso 16 — Ley 21.719 completa (falla #4)

- Supresión a demanda: `ClienteService.anonimizar()` (idempotente, 400 si hay reservas activas,
  aislamiento por tenant) + `POST /api/clientes/{id}/anonimizar`.
- Checkbox de consentimiento obligatorio en el formulario público (`aceptaDatos`), que además
  repara el 400 heredado del paso 13.
- `linkWhatsApp()` devuelve `null` para clientes anonimizados (su placeholder no es un teléfono).

### Paso 17 — Observabilidad (fallas #3 y #8)

- `spring-boot-starter-actuator`; `/actuator/health` público **sin detalles** (permitido en
  `SecurityConfig`, expuesto en `application.yml`) — para healthcheck de Docker y uptime monitor.
- Healthcheck del servicio `backend` en `docker-compose.yml` (wget cada 30 s): una JVM muerta
  ahora se reinicia sola.
- `NotificacionAdapter` cuenta **fallos consecutivos de SMTP** (con último error y fecha);
  `GET /api/sistema/notificaciones` lo expone al panel; `DashboardLayout` muestra un aviso ámbar
  si hay fallos. La degradación "elegante" del email dejó de ser invisible.

### Paso 18 — Teléfonos E.164 (falla #9)

- `Cliente.normalizarTelefono()`: solo dígitos; `9XXXXXXXX` → `569XXXXXXXX`.
- `ReservaService` busca y guarda clientes con el teléfono normalizado.
- `V3__normalizar_telefonos.sql` limpia los datos existentes (excluye anonimizados).

### Paso 19 — Backups offsite + runbooks (fallas #6, #7, #10, #12)

- `backup_db.sh`: si `RCLONE_REMOTE` está definido, sube el dump (falla con ruido si no puede);
  instrucciones de restore en el propio script.
- Nuevo [`OPERACION.md`](OPERACION.md): ventana de mantenimiento semestral (enero/julio, incluye
  ensayo de restore), runbook de rotación de `JWT_SECRET`, configuración del uptime monitor,
  decisión pendiente de planes (RF-13) con plazo, y guía Ley 21.719 para el dueño.

### Paso 20 — Configuración nueva

`COTIZACION_EXPIRACION_DIAS=14`, `RETENCION_CLIENTE_MESES=24`, `RCLONE_REMOTE` (opcional) —
documentadas en `.env.example` y `docker-compose.yml`.

### Paso 21 — Verificación

- `mvn test` (contenedor `maven:3.9-eclipse-temurin-21`): **BUILD SUCCESS — 23/23 tests**
  (5 dominio + 7 reserva + 7 expiración + 4 cliente; eran 14).
- `npm run build`: OK (~56 kB gzip).

---

## 2026-06-10 — Sesión 5: Revisión de código (bugs de correctitud) + manual de despliegue

Tercera pasada de auditoría, esta vez sobre la **lógica actual** (no riesgos a futuro):
8 fallos reales encontrados y corregidos. Lo que se revisó y resultó sano: JWT (iss/aud/exp),
aislamiento multi-tenant, anti doble-reserva, bcrypt, hash de refresh tokens, interceptor del
frontend, precedencia de rutas.

### Paso 22 — Fixes de seguridad

| # | Fallo | Parche |
|---|---|---|
| 1 | **Bypass del rate limit**: con `TRUST_PROXY=true` se tomaba la *primera* IP de `X-Forwarded-For` — falsificable por el cliente (el proxy solo appendea la real al final). Cada request con XFF inventado estrenaba bucket | `RateLimitFilter.ipCliente()` toma la **última** IP (la del proxy de confianza). Test: `RateLimitFilterTest` (31 requests con XFF falsificado variable → 429) |
| 2 | **Reuso de refresh robado sin alarma**: usar un token ya rotado devolvía 401 simple; es evidencia de robo (OWASP) | `AuthService.refresh()` busca también revocados (`findByTokenHash`); si está revocado → `revocarTodosDeUsuario` (familia completa). `noRollbackFor=BadCredentialsException` para que la revocación se commitee aunque respondamos 401 |
| 6 | **Logout fantasma**: `/api/auth/**` es permitAll → logout con access vencido llegaba sin principal y respondía 204 **sin revocar nada** | El logout acepta body opcional `{refreshToken}` y revoca por hash; el frontend lo envía al salir |

### Paso 23 — Fixes de integridad de datos

| # | Fallo | Parche |
|---|---|---|
| 3 | **Lost update job-vs-panel**: el job de expiración leía una COTIZADA vencida, el dueño la confirmaba un instante después, y el flush del job pisaba la confirmación → reserva CANCELADA con bloque CONFIRMADO huérfano | `@Version` en `Reserva` (migración `V4__reserva_optimistic_locking.sql`); el perdedor lanza `OptimisticLockingFailureException` → 409 en la API (handler nuevo); los jobs reintentan en su siguiente pasada |
| 4 | **Devolución sin tope**: se podía devolver más de lo pagado → `totalPagado` negativo, saldo > total | `registrarPago()` valida `totalPagado >= monto` para devoluciones. Test: `devolucionMayorAlPagadoRechazada` |
| 5 | **Constraint → 500**: las carreras TOCTOU (dos registros con el mismo slug entre el `exists` y el `save`; eliminar un bloque recién tomado por una solicitud pública) terminaban en 500 con stacktrace | `GlobalExceptionHandler`: `DataIntegrityViolationException` → 409 "Conflicto con un registro existente" |

### Paso 24 — Fixes menores

| # | Fallo | Parche |
|---|---|---|
| 7 | **N+1 en la bandeja**: 2 queries por reserva (pagado + cliente) = 41 por página de 20 | `listar()` ahora hace 3 queries: página + `totalesPagadosPorReserva(ids)` (SUM agrupada) + `findAllById(clienteIds)`. Test: `listarNoHaceConsultasPorReserva` |
| 8 | **Prefijo internacional `00`**: `0056912345678` quedaba con 13 dígitos → wa.me roto | `normalizarTelefono()` quita el `00` inicial. Test de dominio: `ClienteTest` |

### Paso 25 — Manual de despliegue ([`MANUAL_DESPLIEGUE.md`](MANUAL_DESPLIEGUE.md)) 🆕

Guía completa VPS + Docker Compose + Caddy: DNS, `.env` de producción (con la advertencia de
`TRUST_PROXY`), build estático del frontend servido por Caddy (mismo origen, sin CORS), TLS
automático, cierre de puertos internos (8080/5432 solo en localhost), SPF/DKIM, cron de backups
con offsite, monitoreo, **checklist post-despliegue de 12 puntos** (incluye probar el 429 del
rate limit y un restore real), procedimiento de actualización, y la alternativa Vercel+Railway
con sus limitaciones (jobs `@Scheduled` vs. escalado a cero).

### Paso 26 — Verificación

- `mvn test` (contenedor `maven:3.9-eclipse-temurin-21`): **BUILD SUCCESS — 35/35 tests**
  (23 previos + 5 auth + 3 cliente dominio + 2 rate limit + 2 reserva nuevos).
- `npm run build`: OK (~56 kB gzip).

## 2026-06-11 — Sesión 6: Revisión de fallas a 5 años + offboarding de tenant (falla 3.3)

### Paso 27 — Revisión de fallas a 5 años ([`REVISION_FALLAS_5_ANOS.md`](REVISION_FALLAS_5_ANOS.md)) 🆕

Proyección año a año (jun 2026 → jun 2031): 21 fallas organizadas por el año en que muerden.
Solo 3 son de código (1.3 sin reset de contraseña, 3.2 anonimización incompleta en
`reserva.comentarios`, 3.3 sin offboarding de tenant); el resto es calendario (EOLs del stack),
dinero (plan decorativo) y proceso (bus factor 1, ventanas de mantenimiento, plan de cierre).

### Paso 28 — Implementación de la falla 3.3: offboarding de tenant

Diseñada **iterando año a año contra todas las fallas de la revisión** para que cada pieza
cubra (o no empeore) las demás:

| Pieza | Fallas que cubre |
|---|---|
| Estados `ACTIVO/SUSPENDIDO/CERRADO` (`V5__offboarding_tenant.sql`, CHECK + `cerrado_en`) | 3.3 núcleo · SUSPENDIDO = mecanismo de morosidad que el cobro necesitará (2.1/#10) |
| `GET /api/tenant/export` — JSON completo del tenant (`TenantService.exportar`) | Portabilidad Ley 21.719 · agnóstico del motor (sobrevive a PG16→18, 3.1) · insumo del cierre responsable del servicio (5.1) |
| `POST /api/tenant/cerrar` — confirmación por slug, rechaza con CONFIRMADAS, cancela PENDIENTE/COTIZADA liberando bloques, revoca sesiones, devuelve export final | 3.3 · anti cierre accidental · las señas (dinero) exigen decisión humana |
| Login/refresh bloqueados si el tenant no está ACTIVO (`AuthService.verificarTenantOperativo`); el refresh de un tenant cerrado se quema al usarse (noRollbackFor) | 3.3 · la página pública ya filtraba por estado ACTIVO ✅ |
| Job mensual `purgarTenantsCerrados()` (día 2, 05:30): purga física tras `TENANT_PURGA_DIAS` (90), orden FK pago→reserva→bloque→cliente→servicio→tokens→usuario→tenant | Supresión real Ley 21.719: borra también el residuo de comentarios (3.2) para estos tenants · reduce BD/backups (4.2) · idempotente ante `@Scheduled` duplicado (4.4) · cero disciplina humana (3.5) |
| Panel: pie de página "Exportar mis datos" + "Cerrar negocio definitivamente" (descarga la última copia automáticamente) | Self-service: no depende del operador (bus factor 1, 5.1) |
| Runbook `OPERACION.md` §7: suspensión SQL, reapertura por arrepentimiento, cierre del servicio completo | 5.1 escrito en frío |

Pendiente consciente: la falla 3.2 sigue abierta para tenants **activos** (la purga solo la
resuelve para cerrados) y la 1.3 (reset de contraseña) sigue sin implementar.

### Paso 29 — Verificación

- `mvn test` (contenedor `maven:3.9-eclipse-temurin-21`): **BUILD SUCCESS — 44/44 tests**
  (35 previos + 5 `TenantServiceTest` + 2 purga en `ExpiracionServiceTest` + 2 auth por estado).
- `npm run build`: OK (~55.6 kB gzip).

### Paso 30 — Implementación de la falla 3.2: la anonimización no limpiaba `reserva.comentarios`

Bug legal vigente (no proyección): `Cliente.anonimizar()` borraba nombre/teléfono/email,
pero los comentarios de sus reservas — "[Contacto: <nombre>]", motivos de cancelación,
texto del apoderado — sobrevivían a la supresión. Iterado contra las demás fallas:

| Pieza | Fallas que cubre |
|---|---|
| `ReservaRepository.anonimizarComentariosDeCliente()` — UPDATE masivo que reemplaza los comentarios ENTEROS por `[anonimizado]` (no regex por marcadores: lo que escribió el apoderado también es dato personal); `version + 1` a mano (los JPQL UPDATE no pasan por `@Version`); cláusula `<> placeholder` lo hace idempotente | 3.2 núcleo · 4.4 (idempotencia ante jobs duplicados) · 4.2 (una query, no N+1) |
| Llamado desde los DOS caminos: `ClienteService.anonimizar()` (a demanda) y `ExpiracionService.anonimizarInactivos()` (job mensual) | 3.2 — la supresión queda completa por ambas vías (Ley 21.719, dic-2026) |
| `V6__anonimizar_comentarios_residuales.sql` — corrige el residuo de clientes YA anonimizados (`UPDATE ... FROM`, validada contra Postgres 16 real con datos de prueba) | 3.2 retroactiva · de paso validó V1–V5 en cadena |
| Regla de diseño documentada en `Reserva.COMENTARIOS_ANONIMIZADOS`: ningún dato personal nuevo en texto libre; si se necesita, columna propia que la anonimización conozca | Previene reintroducir la falla (años 4–5) |
| Complemento de la 3.3: la purga ya resolvía esto para tenants CERRADOS; ahora también está resuelto para tenants ACTIVOS — la 3.2 queda cerrada entera | 3.2 + 3.3 |

Seguro como bulk update: ambos caminos exigen "sin reservas activas" antes de anonimizar,
así que todas las reservas tocadas están en estado terminal (nadie más las edita).

### Paso 31 — Verificación

- `mvn test`: **BUILD SUCCESS — 44/44** (assertions nuevas en `ClienteServiceTest` y
  `ExpiracionServiceTest`: limpia comentarios del anonimizado, nunca del que sigue activo).
- `V6` ensayada contra `postgres:16-alpine` con datos reales: reserva del anonimizado →
  `[anonimizado]` + version 0→1; reserva del cliente activo → intacta.

### Paso 32 — Implementación de la falla 1.3: recuperación de contraseña

Antes: olvidar la clave = perder el acceso al negocio (el "soporte" era un UPDATE manual
por SSH). Iterado contra las demás fallas, reusando maquinaria ya probada:

| Pieza | Diseño / fallas que cubre |
|---|---|
| `password_reset_token` (V7): UUID + SHA-256 del token + un solo uso + 30 min | Mismo modelo de seguridad del refresh token (año 1): en BD nunca vive el token en claro |
| `POST /api/auth/reset/solicitar` — **204 SIEMPRE** | Anti-enumeración de cuentas (endpoint público); el rate limit de `/api/auth/**` ya lo cubre ✅ |
| Tenant SUSPENDIDO/CERRADO no recibe resets (silencioso) | Integra con la 3.3 — un negocio cerrado no "resucita" por reset |
| Pedir un reset nuevo invalida los anteriores; confirmar revoca TODAS las sesiones | Si alguien robó la clave vieja, el cambio lo expulsa (reusa `revocarTodosDeUsuario`) |
| Email AFTER_COMMIT vía `NotificacionPort.resetPassword()`; sin SMTP degrada a log (único canal MVP — con SMTP el enlace JAMÁS se loguea: es una credencial) | Patrón del adaptador (falla #7 año 1: el token debe existir en BD antes de que llegue el enlace) |
| Purga diaria en el job de las 04:30 + purga del tenant cerrado | 4.2 (tablas no crecen) · 4.4 (idempotente) |
| `"reset"` agregado a `SLUGS_RESERVADOS` | ¡La falla #4 del año 1 reaparecía!: un negocio con slug `reset` rompería la ruta nueva |
| Frontend: `/reset` (pedir enlace / definir clave nueva) + "¿Olvidaste tu contraseña?" en login | Self-service: no depende del mantenedor (bus factor 1) |

### Paso 33 — Verificación

- `mvn test`: **BUILD SUCCESS — 49/49** (+5 de reset en `AuthServiceTest`, purgas verificadas).
- Cadena V1–V7 ensayada completa contra `postgres:16-alpine`.
- `npm run build`: OK (~55.7 kB gzip).
