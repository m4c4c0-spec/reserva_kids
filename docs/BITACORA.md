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

### Paso 33b — Primer commit del repositorio

Tras cerrar las fallas de código de la revisión a 5 años, el MVP completo (Sesiones 1–6 +
fallas 3.3/3.2/1.3) se versionó por primera vez en git:

- `3395599` **Commit inicial: ReservaKids Digital — MVP completo**
- `7a6e3fc` Falla 1.3 (recuperación de contraseña por email)
- `93401d7` Docs: marcar 1.3 implementada en la revisión a 5 años y README

---

## 2026-06-18 — Sesión 7: Pasarela de pago, cuentas de apoderado y pivote a citas por hora

> Cambio de rumbo del producto: de "reservas de cumpleaños por bloques" a **agendamiento de
> citas por hora multi-servicio** con pago en línea. Migraciones **V8–V14**.

### Paso 34 — Integración de Mercado Pago (pasarela de pago)

- `V8__integracion_mercadopago.sql` + `V10__cifrar_credenciales_mercadopago.sql`: credenciales
  del tenant **cifradas en reposo** (no se guardan en claro), patrón coherente con el SHA-256
  de los refresh tokens.
- Adaptador de pago saliente (puerto `PasarelaPagoPort`); creación de preferencia de pago y
  recepción de confirmación por **webhook**.
- `375210d` fix(email): **Mailpit** como buzón SMTP en desarrollo, configuración de
  SMTP/claves y endurecimiento para **no filtrar errores entre tenants**.
- Commits: `5215288` (pagos + cifrado), `375210d` (email/dev).

### Paso 35 — Cuentas de apoderado (cliente con login propio)

- `V11__cuenta_cliente.sql` + `V12__telefono_cuenta_cliente.sql`: el apoderado deja de ser un
  registro pasivo y pasa a tener **cuenta con login propio**, separada del usuario-negocio.
- Directorio público de negocios; registro con teléfono y pantalla de bienvenida.
- `V9__normalizar_emails_usuario.sql`: normalización de emails para login consistente.
- Commits: `65ee6d0` (cuentas de apoderado + directorio), `8b4290c` (registro con teléfono).

### Paso 36 — Rediseño Festive (UI) y reescritura del frontend

- `f6d2280` feat(ui): **rediseño Festive** del login (fondo V2), vista de configuración del
  negocio y wiring del frontend con los nuevos endpoints.
- `6f38887` docs: revisión de arquitectura a 5 años, manual de despliegue y README actualizados.

### Paso 37 — Pivote a agendamiento por hora con pago (Fase 4)

| Pieza | Detalle |
|---|---|
| `V13__horario_atencion.sql` | Disponibilidad calculada a partir del **horario de atención** del negocio, en lugar de bloques precreados |
| `V14__cita_por_hora.sql` | Modelo de **cita por hora** con estado `PENDIENTE_PAGO` |
| Wizard de cliente | Elegir servicio(s) → día → hora |
| Agendar con pago | Crea la cita, redirige a Mercado Pago y **confirma por webhook** |

- Commits: `9b43be7`, `c28a5d8` (horario + wizard), `72919d3` (modelo de cita), `26bd98c`
  (agendar con pago + webhook).
- **Merges (gitflow):** `feature/agendamiento-pago-webhook` → `produccion` (`81651af`) →
  `main` (`4dc14cc`).

---

## 2026-06-19 — Sesión 8: Notificación de cita, concurrencia y endurecimiento

> Migraciones **V15–V16**. Refactor de jobs y de frontend; subida de Spring Boot.

### Paso 38 — Notificación de cita y concurrencia

| Pieza | Detalle / falla que cubre |
|---|---|
| `a934956` Notificación de cita | Confirmación al apoderado por **WhatsApp y correo** (AFTER_COMMIT) |
| `V15__cita_concurrencia.sql` (`5e8a5f9`) | Anti doble-reserva a nivel de cita con **advisory lock** de Postgres (segunda barrera RNF-05 trasladada al nuevo modelo) |
| `V16__refresh_token_cliente.sql` (`b0f08f2`) | **Refresh token con rotación** para las cuentas de apoderado (mismo modelo que el usuario-negocio) |
| `03e1093` Horario de atención | API de horario + seed por defecto |

### Paso 39 — Refactors y seguridad

- `4246519` refactor(jobs): separar `ExpiracionService` en **`CicloReservaJobs`** y
  **`MantenimientoJobs`** (preámbulo de la descomposición posterior por acoplamiento).
- `984e53c` + `2c9745a` refactor(frontend): componentes base, services y composables; migración
  de las vistas restantes.
- `dd50ab4` feat(security): **refresh token en cookie HttpOnly** (SameSite=Lax), saca el token
  del alcance de JS.
- `d56cb65` chore(deps): **Spring Boot 3.5.6**.
- `bc86449` feat(legal): página de **política de privacidad**.
- `4e71300` test(integracion): tests de integración con **Testcontainers**.

### Paso 40 — Merges (gitflow)

- `feature/notificacion-cita` → `produccion` (`8de3c8d`) → `main` (`ee2a984`).

---

## 2026-06-20 — Sesión 9: Auditoría SonarQube + reset de contraseña de cliente

> Auditoría de deuda técnica y seguridad con **SonarQube Community** (Docker, `:9000`),
> escaneo Maven del backend. Migración **V17**. Detalle ampliado en la nota de proyecto.

### Paso 41 — Reset de contraseña para cuentas de apoderado

- `6deea1f` feat(auth): **reset de contraseña para clientes** (`V17__reset_token_cliente.sql`),
  reutilizando la maquinaria del reset del usuario-negocio (UUID + SHA-256, un solo uso, 30 min,
  204 siempre anti-enumeración). Email AFTER_COMMIT con enlace a `/clientes/reset/confirmar`;
  sin SMTP degrada a log.

### Paso 42 — Auditoría SonarQube: seguridad, acoplamiento, complejidad y code smells

Resultado de `2b9c838` refactor(backend) — **todo verificado con 58 tests unitarios verdes**:

| Hallazgo | Acción |
|---|---|
| **Hotspot CSRF** (`SecurityConfig`) | Revisado **SEGURO** (Bearer + STATELESS + refresh cookie HttpOnly + CORS restrictivo); comentario reescrito y marcado SAFE. Security Review **E → A** |
| **3× self-invocation `@Transactional`** (S6809) | Extraídos helpers privados sin `@Transactional` (`confirmarInterno`, `registrarPagoInterno`, `construirExport`); **3 → 0**, sin cambio de comportamiento |
| **2 bugs** `WebhookController` (S6863) | El `return 200 "ignorado"` vivía dentro de un `catch` (2xx que enmascaraba errores). Helper `parsearLongOrNull`; el 200 deliberado (para que MP no reintente) queda fuera del catch. **Fiabilidad C → A, 0 bugs** |
| **Acoplamiento ≥9 deps** | `MantenimientoJobs` (11) **eliminado** → `TokenMantenimientoJobs`/`ClienteRetencionJobs`/`BloqueMantenimientoJobs`/`TenantPurgaJobs`. `AuthService` 9→7 (extraído `PasswordResetService` + `AuthCrypto`). `TenantService` 10→8 (extraído `TenantExportService`). `ReservaService`/`AgendaService` no tocados (cohesivos) |
| **Complejidad cognitiva** (S3776) | `WebhookController.recibirWebhook` 27→<15 y `DisponibilidadService.horasLibres` 20→<15 (métodos extraídos). **S3776 = 0** |
| **Code smells 56 → 0** | Ternarios anidados, literales duplicados (constantes), regex `\D`, import/excepción genérica, naming, dead-store; + **Quality Profile "ReservaKids Java"** con 6 reglas desactivadas y justificadas (S8688/S8692/S5778/S8694/S2143/S1135) |

**Estado final del backend:** Quality Gate **OK** · 4 calificaciones en **A** ·
**0 bugs · 0 vulnerabilidades · 0 hotspots · 0 code smells · deuda 0 min**.

### Paso 43 — Verificación y release (gitflow)

- `mvn test` (contenedor Maven): **58 tests verdes**; `npm run build` OK (~55.x kB gzip).
- 3 commits lógicos en `feature/calidad-y-reset-cliente`: `6deea1f` (reset cliente),
  `2b9c838` (calidad SonarQube), `75adc24` (refactor frontend, tooling, CI y docs).
- **Merges:** `feature/calidad-y-reset-cliente` → `produccion` (`0e84bf4`) → `main` (`2a586d5`).
- **CI en GitHub** (`2a586d5`): ✅ success — Backend 32s, Frontend 21s.

---

## 2026-06-20 (mediodía) — Sesión 10: Tenant scoping en lookups de cliente (§5.6)

### Paso 44 — Fix de aislamiento multi-tenant

- `7b211fe` fix(security): **tenant scoping en los lookups de cliente** (§5.6) — los lookups
  pasan a usar `findByIdAndTenantId`, cerrando una vía por la que el `tenantId` no se
  verificaba en algunos accesos a cliente; además se corrigieron las **referencias de jobs**
  tras el split de `ExpiracionService` (Sesión 8/9).
- `ddd2867` test(reserva): alinear los stubs de test con `findByIdAndTenantId`.

### Paso 45 — Merges, push y CI

- **Merges:** `fix/tenant-scoping-cliente-refs` → `produccion` (`4e640cd`) → `main` (`a27c026`).
- Las 3 ramas (`fix/...`, `produccion`, `main`) **pusheadas y sincronizadas** con `origin`.
- **CI en GitHub** (`a27c026`): ✅ success (40s).

> Estado al cierre del 2026-06-20: working tree limpio, sin ramas pendientes de mergear a
> `main`, sin stashes. Migraciones al día en **V17**.

---

## 2026-06-20 (tarde) — Sesión 11: Cierre de sprints pendientes + PWA + release v0.1.0

> Auditoría independiente (`AUDITORIA_RESERVA_KIDS.md`) detectó que los Sprints 2 y 3 del
> `PLAN_MEJORA_FRONTEND.MD` estaban a medio aplicar: los métodos de servicio existían pero
> no estaban cableados a los controllers, faltaban tests, y no había PWA ni SEO. Esta sesión
> cierra esos pendientes y versiona el resultado como **v0.1.0**.

### Paso 46 — Backend: endpoints del Sprint 2 cableados

Los métodos `solicitarResetPassword`/`confirmarResetPassword` existían en `ClienteAuthService`
y la query de historial era trivial, pero **no estaban expuestos** en los controllers:

| Cambio | Archivo |
|---|---|
| `POST /api/cliente-auth/reset/solicitar` + `/confirmar` (204 siempre, anti-enumeración) | `ClienteAuthController` |
| `GET /api/cliente/reservas` (rol CLIENTE, `cuentaClienteId` del JWT, cruza tenants) | `DirectorioController` |
| `listarReservasDeCliente` (anti-N+1, mismo patrón que `listar()`) | `ReservaService` |
| `findByCuentaClienteId[AndEstado]OrderByCreadaEnDesc` | `ReservaRepository` |

### Paso 47 — Tests backend (+8 unit, +3 IT)

| Test | Cubre |
|---|---|
| `ClienteReservaServiceTest` (4) | Scoping por `cuentaClienteId`, filtro por estado, página vacía, anti-N+1 |
| `PasswordResetClienteTest` (4) | Solicitud silenciosa, token de un solo uso, revocación de sesiones |
| `PublicControllerIT` (3) | Catálogo, 404 slug inexistente, horas libres (IT con Testcontainers) |

### Paso 48 — Frontend: PWA completa + iOS/Apple tags + SEO

**PWA** (Sprint 3 §3.2): el panel del negocio (`/panel/**`) es instalable como app nativa
sin tiendas. `vite-plugin-pwa` con `autoUpdate`, manifest con `display_override` + iconos
`any`/`maskable` (192/512), service worker que precachea el shell (62 entries, 705 KiB) sin
tocar las respuestas de la API.

**iOS/Apple** (meta tags que iOS no lee del manifest): `apple-mobile-web-app-capable`,
`apple-touch-icon` 180×180 sin alpha, `viewport-fit=cover`. Safe-area insets en top-bar y
bottom-nav del `DashboardLayout` para respetar notch y home indicator.

**Prompt de instalación** (`usePwaInstall` composable): captura `beforeinstallprompt`
(Chrome/Android/Edge), botón "Instalar app" en sidebar y top-bar. iOS no dispara el evento
→ instalación manual vía "Compartir → Añadir a pantalla de inicio" (documentado).

**SEO** (Sprint 3 §3.3): `@vueuse/head` con meta tags dinámicos por negocio en
`PublicSiteView` y estáticos en `LandingPageView`.

### Paso 49 — Tests frontend (+13, total 45)

- `BaseButton.spec` (8): variantes, disabled, loading, spinner.
- `useDuration.spec` (5): formato horas/minutos/mixto, null/negativo.

### Paso 50 — Documentación

- `MANUAL_DESPLIEGUE.md`: sección "Instalación como app móvil (PWA)" con pasos para
  iOS/Android/escritorio, requisitos servidos, comportamiento de actualizaciones y qué
  NO es la PWA (sin push nativo, sin offline de API, sin tiendas).

### Paso 51 — Release v0.1.0

3 commits lógicos en `main` siguiendo conventional commits:

- `cdaa420` feat(cliente): historial de reservas y reset de contraseña para apoderados
- `8784d0a` feat(pwa): PWA completa con instalación iOS/Android/escritorio
- `10c157d` docs(deploy): sección de instalación PWA en manual de despliegue

Tag **v0.1.0**. Versiones alineadas: `pom.xml` `0.1.0`, `package.json` `0.1.0`.

### Paso 52 — Verificación final

- `mvn test`: **66 tests verdes** (58 previos + 8 nuevos).
- `npm run test:run`: **45 tests verdes** (9 archivos, 2 nuevos).
- `npm run build` (con `VITE_API_URL` de producción): **62 entries PWA precache, 705 KiB**.
- `npm run lint`: sin errores.
- Build de producción generado en `frontend/dist/` listo para servir por Caddy.

---

## 2026-06-22 — Sesión 12: CI verde + Testcontainers + Trivy

> Reparación completa del pipeline de CI que fallaba en backend, frontend y seguridad.

### Paso 53 — Fixes de tests de integración

| Falla | Root cause | Fix |
|---|---|---|
| Docker Engine 29 exige API ≥1.40, pero Testcontainers 1.21.3 forzaba 1.32 | Incompatibilidad de cliente Docker | `pom.xml`: subir `<testcontainers.version>` a `1.21.4` |
| `ReservaRepository.bloquearDia` con `@Modifying` + `SELECT pg_advisory_xact_lock` → SQLState 0100E | `pg_advisory_xact_lock` retorna `void`, Hibernate lo interpreta como "resultado inesperado" | Quitar `@Modifying`, cambiar retorno a `Object` |
| `CsrfProtectionIT.refreshConOriginMaliciosoDebeFallar` fallaba por esperar mensaje JSON de CsrfFilter | CORS bloquea antes que CsrfFilter → no hay body | Solo aserta `status().isForbidden()` |
| `WebhookMercadoPagoIT.tenantRateLimitExcedidoDevuelve429` llamaba a MP por firma válida | El `x-request-id` del loop coincidía con la firma precalculada | Cambiar `x-request-id` a `rate-limit-<i>` para invalidar firma |
| 128 tests locales (94 unitarios + 34 integración), `BUILD SUCCESS` | | |

### Paso 54 — Fix de seguridad (Trivy)

- `.github/workflows/ci.yml`: `aquasecurity/trivy-action@v0.30.0` referenciaba `aquasecurity/setup-trivy@v0.2.2`, cuyo tag fue eliminado/hackeado → `Unable to resolve action`. Subido a `v0.36.0` que pinnea setup-trivy por SHA.
- CI verde completo: Backend 1m42s, Seguridad 27s, Frontend 19s.

### Paso 55 — Merges (gitflow)

- `fix/ci-verde-it-restantes` → `produccion` → `main`
- `fix/ci-trivy-setup` → `produccion` → `main`
- Todas las ramas pusheadas y sincronizadas.

---

## 2026-06-22 (tarde) — Sesión 13: Provisión Automática Single-Tenant + Docker runtime injection

> El modelo "Licencia Exclusiva" ($650.000+) requiere que el backend provisione el negocio
> automáticamente al iniciar el contenedor, y que el frontend inyecte el slug en runtime sin
> recompilar la imagen Docker por cliente.

### Paso 56 — Bootstrapper automático de negocio

| Pieza | Detalle |
|---|---|
| `application.yml` bloque `app.single-tenant.*` | 5 propiedades mapeadas a `RESERVAKIDS_SINGLE_TENANT_*`: `enabled`, `slug`, `nombre`, `admin-email`, `admin-password` |
| `SingleTenantBootstrapper.java` | `ApplicationRunner` idempotente: verifica `enabled=true` y `tenantRepository.count() == 0`, luego llama `AuthService.registrar()` que crea Tenant + Usuario + Horario por Defecto |
| `docker-compose.yml` backend service | 5 env vars ST con defaults vacíos; el bootstrapper solo actúa si todas están presentes |

### Paso 57 — Inyección runtime del slug en el frontend

**Problema:** `VITE_*` env vars de Vite se incrustan en el bundle JS durante `vite build`. No se pueden cambiar sin recompilar.

**Solución:** patrón `window.__SINGLE_TENANT_SLUG__`:

| Pieza | Detalle |
|---|---|
| `frontend/index.html` | `<script>window.__SINGLE_TENANT_SLUG__ = "";</script>` — placeholder antes de `#app` |
| `frontend/entrypoint.sh` | `sed` reemplaza el placeholder con `$VITE_SINGLE_TENANT_SLUG` al iniciar nginx |
| `frontend/Dockerfile` | Multistage: `node:22-alpine` build + `nginx:alpine` serve con entrypoint |
| `frontend/nginx.conf` | SPA routing + CSP + gzip + headers de seguridad + `no-cache` en index.html |
| `router/index.js` | `isSingleTenant` lee `window.__SINGLE_TENANT_SLUG__` con fallback a `import.meta.env` (dev mode) |
| `PublicSiteView.vue` + `AgendarView.vue` | Priorizan `window.__SINGLE_TENANT_SLUG__` → `import.meta.env.*` → `route.params.slug` |

### Paso 58 — Reverse proxy (Caddy) en docker-compose

- `Caddyfile.docker`: reverse proxy `:80` → `frontend:80` y `backend:8080` con CSP, TLS-ready
- Servicio `caddy` en `docker-compose.yml` con `caddy:alpine`, puertos 80/443, volumen `caddy_data`
- Arquitectura: Cliente → Caddy:80 → nginx:80 (estáticos) / backend:8080 (API)

### Paso 59 — Documentación

- `PROVISIONAMIENTO_AUTOMATICO.md` reescrito con diagrama de contenedores, flujo Docker, modo híbrido dev.

---

## 2026-06-23 (mañana) — Sesión 14: Marca Blanca + Meta Pixel + Privacidad + Backups

> 5 features orientadas a la experiencia del cliente final (dueño del salón) y al blindaje legal
> y operativo del modelo de licencias exclusivas.

### Paso 60 — Marca Blanca Visual

| Pieza | Detalle |
|---|---|
| `V28__marca_blanca_pixel.sql` | `ALTER TABLE tenant ADD color_primario VARCHAR(7) DEFAULT '#b5007d'`, `titulo_pagina VARCHAR(120)`, `meta_pixel_id VARCHAR(50)` |
| `Tenant.java` | `colorPrimario`, `tituloPagina`, `metaPixelId` |
| `TenantDtos.ActualizarConfigRequest` | Extendido con los 3 nuevos campos + validación hex en `TenantService.guardarConfiguracion()` |
| `PublicController.catalogo` | Expone `colorPrimario`, `tituloPagina`, `metaPixelId` en la respuesta JSON |
| `PublicSiteView.aplicarMarcaBlanca()` | Inyecta `--color-primary` en `:root`, actualiza `<meta name="theme-color">`, setea `document.title` |
| `ConfiguracionView.vue` | Sección "Marca blanca" con color picker, input hex, previsualización, título de pestaña |
| `vite.config.js` + `index.html` | `theme_color` cambiado de `#7C4DFF` a `#b5007d` |
| `PublicSiteView.vue` checkbox | `accent-[#b5007d]` → `accent-primary` (respeta el color del tenant) |

### Paso 61 — Meta Pixel (Facebook/Instagram)

- `PublicSiteView.aplicarMarcaBlanca()`: inyecta dinámicamente `fbq('init', pixelId)` si `negocio.metaPixelId` existe.
- CSP actualizado en **4 archivos** (`index.html`, `nginx.conf`, `Caddyfile`, `Caddyfile.docker`): `script-src` permite `https://connect.facebook.net`, `connect-src` e `img-src` permiten `https://www.facebook.com`.

### Paso 62 — Check de Privacidad (Ley 19.628)

- `AgendarView.vue` paso 3 (pago): checkbox `aceptaPrivacidad` con enlace a `/privacidad`. `pagar()` bloquea el pago si no está marcado.
- Ya existía en `PublicSiteView.vue` (formulario de cotización pública).

### Paso 63 — Backups Automáticos (pg_dump diario 03:00 AM)

| Pieza | Detalle |
|---|---|
| `backup/Dockerfile` | Alpine 3.21 + `postgresql16-client` + `dcron` + `gzip` |
| `backup/entrypoint.sh` | Backup inmediato al iniciar + cron `0 3 * * *` (3 AM UTC), verificación con `zgrep`, retención 14 días, idempotente |
| `docker-compose.yml` | Servicio `backup` + volumen `backups` |

### Paso 64 — Validación

- `mvn verify`: **128 tests verdes** (94 unitarios + 34 integración).
- `docker compose up --wait`: 6/6 healthy (db, mailpit, backend, frontend, caddy, backup).
- Backups verificados: "Backup OK: 12970 bytes".

---

## 2026-06-23 (mañana, continuación) — Sesión 15: Saldos + Legal + RSVP + Upsells + Caja

> 5 features que completan el ciclo operativo del dueño: cobranza post-seña, blindaje legal,
> captación de clientes por RSVP, venta cruzada y cierre de caja diario.

### Paso 65 — Flyway V29

`V29__saldos_legal_rsvp_upsells_caja.sql`:

| Migración | Tabla / columna |
|---|---|
| `politicas_cancelacion TEXT` | `tenant` |
| `es_adicional BOOLEAN DEFAULT false` | `servicio` |
| `politicas_aceptadas_en TIMESTAMPTZ` | `reserva` |
| `invitado` (nueva tabla) | `id`, `tenant_id`, `reserva_id`, `nombre`, `email`, `telefono`, `estado`, `token`, `comentarios`, `creado_en` |

### Paso 66 — Escudo Legal (Políticas de Cancelación)

| Archivo | Cambio |
|---|---|
| `Tenant.java` | `politicasCancelacion` (TEXT) |
| `TenantDtos.ActualizarConfigRequest` | `politicasCancelacion` |
| `TenantService.guardarConfiguracion()` | Guarda/borra políticas |
| `Reserva.java` | `politicasAceptadasEn` timestamp |
| `PublicController.catalogo` | Expone `politicasCancelacion` |
| `ConfiguracionView.vue` | Textarea "Políticas de cancelación" (máx 5000 chars) |
| `PublicSiteView.vue` | Muestra políticas (si existen) + checkbox `aceptaPoliticas` obligatorio antes de enviar |

### Paso 67 — RSVP / Invitados

| Pieza | Detalle |
|---|---|
| `Invitado.java` | Entidad con `nombre`, `email`, `telefono`, `estado` (PENDIENTE/CONFIRMADO/RECHAZADO), `token` único (64 chars hex) |
| `InvitadoService.java` | `agregar()` genera token criptográfico, `confirmarRsrv(token)` / `rechazarRsrv(token)` idempotentes, `resumen()` cuenta por estado |
| `InvitadoController.java` | `GET/POST /api/reservas/{id}/invitados`, `DELETE /api/reservas/{id}/invitados/{invId}`, `GET .../invitados/resumen` |
| `RsvpPublicController.java` | `GET /api/public/invitacion/{token}`, `POST .../confirmar`, `POST .../rechazar` — sin auth |
| `RsvpView.vue` | Página pública con nombre del invitado, botones "¡Voy a ir!" / "No voy a poder", campo de comentarios opcional |
| `router/index.js` | Ruta `/invitacion/:token` |
| `SolicitudesView.vue` | Sección "Invitados" expandible en cada reserva: formulario para agregar + lista con estado de RSVPs + botón quitar |

### Paso 68 — Upsells (Venta Cruzada)

| Archivo | Cambio |
|---|---|
| `Servicio.java` | `esAdicional` boolean (default false) |
| `ServicioDtos.java` | `ServicioRequest.esAdicional`, `ServicioResponse.esAdicional` |
| `ServicioService.java` | `aplicar()` maneja `esAdicional` |
| `ServiciosView.vue` | Checkbox "Servicio adicional" en formulario + badge "Adicional" en cards |
| `PublicSiteView.vue` | `serviciosPrincipales` (filtra `!esAdicional`) como opciones principales; `serviciosAdicionales` como checkboxes en sección "Agregá extras" |

### Paso 69 — Cierre de Caja Diario

| Pieza | Detalle |
|---|---|
| `CajaDtos.CajaDiariaResponse` | `fecha`, `totalReservas`, `reservasConfirmadas`, `citasAgendadas`, `pagosHoy`, `totalRecaudadoHoy`, `saldoPendienteTotal`, `seniaPromedio` |
| `CajaService.java` | Filtra reservas del día por `inicio` o `creadaEn` en zona `America/Santiago`, cruza con `PagoRepository.totalesPagadosPorReserva()` |
| `SistemaController.java` | `GET /api/sistema/caja?fecha=YYYY-MM-DD` |
| `SolicitudesView.vue` | Widget "Caja diaria" con selector de fecha, 4 KPIs: reservas hoy, citas hoy, recaudado hoy, saldo pendiente |

### Paso 70 — Saldos Pendientes (ya existía parcialmente)

- `ReservaResponse.saldoClp` ya se computaba como `totalClp - pagadoClp` (`ReservaDtos.java:47`).
- Nueva exposición en el widget de Caja: `saldoPendienteTotal` y `seniaPromedio`.
- El saldo por reserva ya se destacaba en `SolicitudesView` con `<strong class="text-primary">Saldo {{ clp(r.saldoClp) }}</strong>`.

### Paso 71 — Validación

- `mvn verify`: **128 tests verdes** (94 unitarios + 34 integración).
- `docker compose up --wait`: 6/6 healthy.
- Backup diario: "Backup OK: 13290 bytes" (incluye nuevas tablas).

---

## 2026-06-23 (mediodía) — Sesión 16: Staff + Inventario + Google Calendar Feed

> 3 features: roles de personal sin acceso a finanzas, gestión de stock físico para upsells,
> y sincronización con Google Calendar vía feed iCal público.

### Paso 72 — Flyway V30

`V30__staff_inventario_calendar_feed.sql`:

| Migración | Detalle |
|---|---|
| `staff` (nueva tabla) | `id`, `tenant_id`, `nombre`, `email` (UNIQUE), `password_hash`, `telefono`, `rol` (default 'ANIMADOR'), `activo`, `whatsapp_recordatorio`, `creado_en` |
| `servicio.stock` | `INTEGER` (nullable — null = stock infinito) |
| Índices | `idx_staff_tenant`, `idx_invitado_token`, `idx_invitado_reserva` |

### Paso 73 — Gestión de Personal (Staff)

**Backend — modelo:**
| Pieza | Detalle |
|---|---|
| `Staff.java` | Entidad con `tenantId`, `email`, `passwordHash`, `nombre`, `telefono`, `rol` (String libre, default ANIMADOR), `activo`, `whatsappRecordatorio` |
| `StaffRepository.java` | `findByEmail`, `findByTenantIdAndActivoTrueAndWhatsappRecordatorioTrue`, etc. |
| `StaffService.java` | `login()` con bcrypt, `crear()`, `eliminar()` (soft-delete), `eventosDelDia()` (solo CONFIRMADA/REALIZADA), `staffConWhatsapp()` |

**Backend — auth:**
| Pieza | Detalle |
|---|---|
| `JwtService.emitirStaff()` | Emite JWT con claims `tenantId`, `rol=STAFF`, `nombre` — access 15 min |
| `JwtService.emitirRefreshStaff()` | Refresh 30 días con rol STAFF |
| `SecurityConfig.java` | Permite `POST /api/staff/login` sin auth, resto de `/api/staff/**` requiere `ROLE_STAFF` |
| `StaffController.java` | `POST /api/staff/login` → `StaffTokenResponse`, `GET /api/staff/eventos?fecha=YYYY-MM-DD` → solo `id`, `hora`, `numNinos`, `comuna`, `estado` **(sin precios, sin métricas, sin datos sensibles del negocio)** |

**Backend — WhatsApp automático al staff:**
| Pieza | Detalle |
|---|---|
| `NotificacionWhatsappPort.recordatorioStaff()` | Nuevo método en el puerto |
| `WhatsappStubAdapter.recordatorioStaff()` | Stub → log; real → Meta Cloud API v22.0 |
| `RecordatorioJobs.recordatorioStaffViernes()` | `@Scheduled(cron = "0 0 23 * * FRI")` — cada viernes 23:00 UTC (19:00 CLT): para cada tenant activo, busca eventos del sábado, construye detalle y envía WhatsApp al staff que tenga `whatsappRecordatorio=true` y `telefono` no nulo |

**Frontend:**
| Pieza | Detalle |
|---|---|
| `stores/staff.js` | Pinia store con `login(email, password)`, `logout()`, `accessToken`, datos del staff |
| `StaffLoginView.vue` | `/staff/entrar` — login con email/password, fondo decorativo |
| `StaffCalendarView.vue` | `/staff/calendario` — selector de fecha, lista de eventos con hora grande + niños + comuna + badge de estado **(cero referencias a precios o finanzas)** |
| `router/index.js` | Rutas `/staff/entrar` y `/staff/calendario` con guards `soloInvitados: 'staff'` / `requiereStaff` y redirecciones cruzadas entre roles |

### Paso 74 — Inventario (Stock)

| Archivo | Cambio |
|---|---|
| `Servicio.java` | `stock` (Integer nullable — null = infinito) |
| `ServicioDtos.java` | `ServicioRequest.stock` (`@Min(0)`), `ServicioResponse.stock` |
| `ServicioService.aplicar()` | `req.stock()` — 0 se guarda como null |
| `PublicController.catalogo` | `.filter(s -> s.stock() == null \|\| s.stock() > 0)` — si stock llega a 0, el servicio desaparece del frontend público automáticamente |
| `ServiciosView.vue` | Campo "Stock disponible" (solo visible si `esAdicional=true`), badge `Stock: N` en la card del servicio |

### Paso 75 — Google Calendar Feed (iCal público)

| Pieza | Detalle |
|---|---|
| `PublicController.feedIcal()` | `GET /api/public/{slug}/calendar.ics` — sin auth, público |
| Contenido | `VCALENDAR` con `METHOD:PUBLISH`, `X-WR-CALNAME:{tenant.nombre}`, `REFRESH-INTERVAL:PT1H` |
| VEVENTs | Todas las reservas CONFIRMADA con `inicio != null`; `UID:{id}@reservakids.cl`, `DTSTART/DTEND` en `America/Santiago`, `SUMMARY:Fiesta infantil`, `DESCRIPTION:{N} niños - {comuna}` |
| **Sin precios, sin nombres de cliente, sin datos sensibles** | |
| Uso | El dueño pega `https://reservas-salonfantasia.cl/api/public/salon-fantasia/calendar.ics` en Google Calendar → "Desde URL" → se sincroniza automáticamente cada hora |

### Paso 76 — Validación final

- `mvn verify`: **128 tests verdes** (94 unitarios + 34 integración).
- `npm run build`: OK (~1038 KiB PWA precache, 74 entries).
- `docker compose up --wait`: 6/6 healthy.
- Staff login endpoint responde correctamente (400 si credenciales inválidas).
- iCal feed retorna `VCALENDAR` válido con VEVENTs.
- Backup diario: "Backup OK: 13569 bytes" (incluye tabla staff).

---

> **Estado al cierre del 2026-06-23:** working tree limpio. Migraciones al día en **V30**.
> Stack operativo completo: 6 servicios Docker (db, mailpit, backend, frontend, caddy, backup).
> 128 tests backend verdes. Frontend PWA funcional con 4 roles de acceso (DUENO, ADMIN, CLIENTE, STAFF).

---

## Sesión 2026-06-23 (tarde) — Migración Nuxt 3 SSR + capacidades nuevas + readiness de deploy

> **Corrección al cierre previo:** el bloque anterior ("working tree limpio, V30, frontend Vite/PWA")
> quedó desfasado. Esta sesión cierra una migración grande que NO estaba commiteada.

### Frontend — migración Vite SPA → **Nuxt 3 SSR**
- `vue-router` + estáticos `dist/` → Nuxt 3 con file-based routing (`src/pages`), `layouts/`,
  middleware global (`auth.global.ts`, `single-tenant.global.ts`).
- Dos unidades de deploy SSR (`frontend-public` :3000 / `frontend-panel` :3001) ruteadas por Caddy.
- Single-tenant ahora por env runtime (`NUXT_PUBLIC_SINGLE_TENANT_SLUG`), no por build.
- Eliminado el código muerto de la migración: `src/views/` (legacy) y duplicados `frontend/pages/`, `frontend/app.vue`.

### Backend — capacidades nuevas
- RBAC (roles/permisos, `PermissionEvaluator` + `@PreAuthorize`), Staff, Invitados/RSVP, Caja,
  OAuth2 SSO (Google), sync Google Calendar + feed iCal público, Idempotency-Key, monitor de
  fallos de webhook, bootstrap single-tenant. Migraciones **V28..V34**.

### Deploy / seguridad
- `deploy-prod.sh` reescrito para Nuxt SSR (Docker construye; levanta db+backend+2 front SSR+backup).
- **Fix de exposición crítico:** Compose fusiona listas de `ports`; sin `!override` los binds a
  `127.0.0.1` de `compose.prod.yml` no quitaban los `0.0.0.0` del base → Postgres/backend/Grafana
  quedaban expuestos en el VPS. Añadido `!override`; observabilidad y Grafana (sin admin anónimo) en loopback.
- `docker-compose.yml`: quitado `depends_on: mailpit` del backend (invalidaba el proyecto en prod).
- Fix test `ReservaServiceTest` (stub `whatsapp.linkWhatsApp`). **`mvn verify`: 98 unit + 34 IT verdes.**

### CI / PR
- PR #14 contra `main`. Fixes de CI: `package-lock.json` resincronizado (`npm ci` fallaba) y
  permisos `pull-requests: read` para gitleaks (fallaba con 403).

### Estado real al cierre
- Migraciones al día en **V34**. Backend `mvn verify` verde. Frontend Nuxt SSR construye.
- Pendiente operativo: comprar dominio del cliente y ejecutar `deploy-prod.sh` en el VPS.
