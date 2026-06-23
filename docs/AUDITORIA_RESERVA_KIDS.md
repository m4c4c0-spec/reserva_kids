# Auditoría integral — ReservaKids

**Fecha:** 2026-06-19 · **Autor:** asesor de programación (revisión de arquitectura, clean code y ciberseguridad) · **Modo:** solo lectura (no se modificó código).

> Este documento es una auditoría **independiente** del estado actual del repositorio. Complementa y actualiza a [`REVISION_ARQUITECTURA.md`](REVISION_ARQUITECTURA.md) (2026-06-11), que está desactualizada: cita Flyway V1–V7 (hoy hay V17), `ExpiracionService` (ya refactorizado en `CicloReservaJobs` + `MantenimientoJobs`) y no cubría frontend ni ciberseguridad en profundidad.
>
> Todas las citas siguen el formato `archivo:linea` para navegar al punto exacto.

---

## Resumen ejecutivo

| Eje | Veredicto | Nota |
|---|---|---|
| **Arquitectura** | ✅ Apropiada para el nicho | Onion de paquetes (no de dependencias): defendible a esta escala. 1 violación de capa real (`TenantService` → `CredentialCipher`). |
| **Clean code** | ⚠️ Deuda concentrada, base sana | `ReservaService`/`NotificacionAdapter` crecidos; duplicación auth cliente/dueño; 0 tests frontend. Controllers delgados, 72 tests backend cubriendo lo crítico. |
| **Ciberseguridad** | ⚠️ Base sólida + 2 fallas críticas actuales | JWT, refresh HttpOnly, bcrypt, cifrado AES-GCM, multi-tenant y webhook HMAC bien hechos. Pero hay trabajo en progreso **sin commitear** que rompe 3 rutas del router y el form de Mercado Pago. |

### Riesgos priorizados (10 principales)

| # | Sev | Hallazgo | Ubicación |
|---|-----|----------|-----------|
| 1 | 🔴 CRÍTICO | Router referencia 3 vistas inexistentes → rutas rotas / build falla en runtime | `frontend/src/router/index.js:27,29,30` |
| 2 | 🔴 CRÍTICO | `BaseInput` usa `modeloValue` pero se consume con `v-model` → credenciales de Mercado Pago no se enlazan | `frontend/src/components/BaseInput.vue:3,11` · `frontend/src/views/ConfiguracionView.vue:91,102` |
| 3 | 🟠 ALTO | Sin CSP ni headers de seguridad en el frontend servido por Caddy | `docs/MANUAL_DESPLIEGUE.md:105-128` · `frontend/index.html` |
| 4 | 🟠 ALTO | Vite 6.0.7 con CVEs High de dev-server + `host:true` expone el dev server a la LAN | `frontend/vite.config.js:11` · `frontend/package.json:21` |
| 5 | 🟠 ALTO | `PublicSiteView` lee `route.params.slug` que la ruta catch-all no provee → "Negocio no encontrado" perpetuo | `frontend/src/views/PublicSiteView.vue:15` · `frontend/src/router/index.js:32` |
| 6 | 🟡 MEDIO | Access token en `sessionStorage` → robo por XSS; sin CSP que lo mitigue | `frontend/src/stores/auth.js:12,24` · `frontend/src/stores/clienteAuth.js:13,27` |
| 7 | 🟡 MEDIO | Token de reset de contraseña viaja en query string (historial / `Referer` a terceros) | `frontend/src/views/ResetPasswordView.vue:14,39` |
| 8 | 🟡 MEDIO | CSRF deshabilitado pese a cookie de refresh; el comentario justificativo quedó obsoleto (solo `SameSite=Lax` mitiga) | `backend/.../security/SecurityConfig.java:40` · `application.yml:58` |
| 9 | 🟡 MEDIO | Webhook MP: si el tenant no setea `mpWebhookSecret`, no se valida la firma → se confía en re-consulta a MP | `backend/.../web/WebhookController.java:90-96` |
| 10 | 🟡 MEDIO | Rate limiting en memoria (no distribuido); 30 req/min/IP algo generoso para fuerza bruta de login | `backend/.../security/RateLimitFilter.java:24,36` |

**Puntos fuertes a preservar** (no tocar): refresh token opaco + SHA-256 en cookie HttpOnly+Secure+SameSite (fuera del alcance de JS); 0 usos de `v-html`/`innerHTML` en el frontend; bcrypt(10); JWT HS256 con validación `iss/aud/exp`; cifrado AES-256-GCM de credenciales MP en reposo; comparación de firma HMAC en tiempo constante; anti-enumeración en login/reset; secretos solo por variables de entorno con defaults vacíos fail-fast; aislamiento multi-tenant estricto en la capa web; invariantes críticas en la BD (índice único parcial anti doble-reserva, CHECKs); 72 tests cubriendo estados/concurrencia/webhook.

---

## §1 Contexto y nicho

**Producto:** SaaS multi-tenant de reservas para negocios de cumpleaños infantiles (Victoria, Malleco y La Araucanía, Chile). Catálogo de servicios, calendario de disponibilidad, solicitudes de cotización/reserva, registro de señas, notificaciones por email + links `wa.me`, y (en P1) pago online vía Mercado Pago Checkout Pro. Cumple la **Ley 21.719** (protección de datos personales: consentimiento expreso, anonimización por inactividad y a demanda, offboarding de negocio con export + purga física).

**Escala real:** 1 VPS (Docker Compose) o alternativa Vercel + Railway. Bus factor 1 (un único mantenedor). Equipo pequeño. No hay requisitos de baja latencia, alto throughput ni concurrencia masiva — es un sistema transaccional con cumplimiento legal y dinero (señas/pagos) involucrado.

**Stack elegido:**

| Capa | Tecnología | Idoneidad para el nicho |
|---|---|---|
| Backend | Java 21 + Spring Boot 3.5.6 | ✅ Ecosistema maduro para transacciones + seguridad + jobs programados; mucha documentación y mantenedores disponibles en Chile. |
| BD | PostgreSQL 16 + Flyway (V1–V17) | ✅ Invariantes en la BD (índices parciales, CHECKs) sobreviven a cualquier reescritura del código. La decisión más valiosa del proyecto. |
| Frontend | Vue 3 + Vite + Tailwind 4 + Pinia | ✅ Curva baja, bundle pequeño (~56 kB gzip), mobile-first. |
| Auth | Spring Security + JWT (HS256) + refresh opaco en cookie HttpOnly | ✅ Modelo estándar y bien probado. |
| Notificaciones | SMTP (Brevo/Resend, degrada a log) + `wa.me` | ✅ Pragmático; el puerto `NotificacionPort` permite cambiar de canal sin tocar casos de uso. |
| Pagos | Mercado Pago Checkout Pro | ✅ Único procesador con adopción masiva en Chile. |
| Hosting | Docker Compose (VPS) + Caddy (TLS automático) | ✅ Proporcionado; microservicios serían absurdos a esta escala. |

**Veredicto de idoneidad:** el stack es **proporcionado y correcto** para el nicho. No hay sobre-ingeniería (salvo la aspiración Onion, ver §2) ni sub-ingeniería. Las revisiones a 1/2/5 años (`REVISION_FALLAS_*.md`) confirman que **ninguna de las ~40 fallas encontradas fue causada por la estructura del código**: el cuello de botella es proceso (ventanas de mantenimiento, deploy, negocio), no arquitectura.

---

## §2 Arquitectura

### 2.1 Lo que dice ser vs lo que es

**Lo que dice ser** (README, SDLC, `MANUAL_MIGRACION.md`): Onion / Puertos y Adaptadores con regla de dependencias "siempre hacia adentro" y un dominio que "no importa nada del framework".

**Lo que es, medido en el código:**

| Afirmación | Realidad |
|---|---|
| "El dominio no depende de nada" | **18 de 23 archivos de `domain/` importan `jakarta.persistence` o `org.springframework`.** Las entidades son `@Entity` JPA con Lombok; los 13 repositorios extienden `JpaRepository` y contienen ~19 queries JPQL (`@Query`/`@Modifying`). Solo son puros los 2 enums (`EstadoReserva`, `EstadoBloque`) y las 3 excepciones de dominio. |
| "Puertos y Adaptadores" | Hay **4 puertos verdaderos** (`NotificacionPort`, `TokenPort`, `NotificacionWhatsappPort`, `PasarelaPagoPort`) con sus adaptadores en `infrastructure/`. Los 13 repositorios son puertos *de nombre*: su contrato está definido en términos de Spring Data. |
| "Aplicación = casos de uso" | Cierto en responsabilidad, pero acoplada a Spring (`@Service`, `@Transactional`, `@Value`, `@Scheduled`). |
| Capa web/infra | ✅ Correcta: controllers delgados, DTOs separados de entidades (la API nunca expone JPA), errores mapeados en un solo `GlobalExceptionHandler`. |

**Veredicto honesto:** es una **Onion de paquetes, no de dependencias**. La dirección `application → domain` e `infrastructure → application` se respeta; la única flecha invertida — pero estructural — es `domain → Spring Data/JPA`. Es el trade-off estándar del ecosistema Spring.

### 2.2 ¿Importa? Análisis del trade-off

Para este proyecto, **el acoplamiento elegido es defendible**. Un dominio 100% puro exigiría entidades de dominio + entidades JPA + mappers + interfaces de repositorio en dominio con implementaciones adapter — aproximadamente **duplicar las ~3.000 LOC de backend** para que un mantenedor único gane una pureza que nunca va a cobrar (nadie va a cambiar Postgres por Mongo).

**Lo que sí importa** es la consecuencia sobre la migración: el `MANUAL_MIGRACION.md` da a entender que el dominio se preserva tal cual — **falso**. Lo portable de este sistema no es el código de dominio (está soldado a JPA): son los **contratos** (esquema SQL, máquina de estados, algoritmo anti doble-reserva, API REST, tabla de seguridad). El manual ya fue corregido en la Sesión 6 (`REVISION_ARQUITECTURA.md:28`).

### 2.3 Lo que está bien y hay que conservar

1. **Las invariantes críticas viven en la BD, no en el código** (ver §8.1): índice único parcial anti doble-reserva, `UNIQUE (tenant_id, fecha, hora_inicio)`, CHECKs de estado. Sobreviven a cualquier reescritura.
2. **La lógica de dominio crítica es pura de facto**: `EstadoReserva.puedeTransicionarA()` (`backend/.../domain/model/EstadoReserva.java:25-33`), `Cliente.normalizarTelefono()/anonimizar()` (`backend/.../domain/model/Cliente.java:30-42,79-85`), `Reserva.transicionarA()` (`backend/.../domain/model/Reserva.java:96-102`) — funciones sin I/O, testeadas exhaustivamente. `EstadoReservaTest` funciona como especificación ejecutable.
3. **Esquema versionado (Flyway V1–V17) como fuente de verdad** + `ddl-auto: validate` (`application.yml:10`) + contrato REST documentado y estable.
4. **Los 4 puertos reales están donde duelen**: notificaciones (canal cambiará: SMTP→Resend→WhatsApp API), tokens (formato podría cambiar), pasarela de pago (MP→otro), WhatsApp (stub→API). Puertos donde hay volatilidad real, acoplamiento donde hay estabilidad real — eso es criterio, no dogma.
5. **Errores uniformes y manejo de carreras centralizado** (`GlobalExceptionHandler`: TOCTOU → 409, optimistic locking → 409, `DataIntegrityViolationException` → 409 sin filtrar constraints).

### 2.4 Violación de capa real (la única)

`backend/src/main/java/cl/reservakids/application/usecase/TenantService.java:42` inyecta directamente `cl.reservakids.infrastructure.security.CredentialCipher` (una clase de infraestructura) en un servicio de aplicación. El cifrado debería estar detrás de un puerto definido en `application` o `domain`, y `CredentialCipher` debería implementarlo. Esto rompe la regla de dependencias Onion (las capas internas no conocen a las externas).

**Fix propuesto:** crear `application/port/CredentialCipherPort` (o mover a `domain/port`) con `cifrar`/`descifrar`; `CredentialCipher` lo implementa; `TenantService` y `MercadoPagoAdapter` dependen de la interfaz.

### 2.5 Inconsistencias menores de estructura

- **Estados como `String` en `Tenant`/`Pago` vs `enum` en `Reserva`/`Bloque`** — dos convenciones para el mismo concepto.
- **`Clock` inyectado en `ClienteService`/`DisponibilidadService`/jobs vs `OffsetDateTime.now()` directo en `AuthService`/`ReservaService`** — la mitad del código es testeable en el tiempo, la otra mitad no.
- **`pago` sin `tenant_id`**: el aislamiento multi-tenant de pagos es indirecto (vía subquery de reserva, `PagoRepository.java:34-38`). Correcto hoy porque `registrarPago()` valida la reserva primero, pero es la única tabla donde el aislamiento depende de disciplina de código y no de una columna.
- **`reserva.comentarios` es un campo multipropósito** (notas del cliente + `[Cancelación]` + `[Expiración]` + `[Cierre del negocio]`): un event-log en texto plano. La falla 3.2 lo hizo seguro legalmente (`V6`); estructuralmente, una tabla `reserva_evento` sería lo limpio — no paga a esta escala.
- **Ubicación inconsistente de los puertos**: `PasarelaPagoPort` vive en `domain/repository/`; `TokenPort`, `NotificacionPort`, `NotificacionWhatsappPort` viven en `application/usecase/`. No hay un paquete `domain/port` o `application/port` coherente.

### 2.6 Comparativa de alternativas

| Opción | Veredicto |
|---|---|
| **Quedarse con la Onion-pragmática actual** | ✅ **Recomendado.** Los problemas reales se corrigen *dentro* de esta arquitectura por una fracción del costo de cualquier migración. |
| Onion/Hexagonal purista (dominio sin JPA, mappers) | ❌ Duplica LOC y mantenimiento para un beneficio teórico. El único escenario que lo justificaría — cambiar el motor de persistencia — no está en ningún horizonte. |
| Monolito modular por features (`reservas/`, `auth/`, `tenants/`…) | ⚖️ Neutro hoy (14 entidades caben en la cabeza); reconsiderar solo si el código triplica su tamaño (v2 con multi-sucursal + reportes). |
| Microservicios | ❌ Absurdo a esta escala: multiplicaría infra, observabilidad y modos de falla para un sistema de 1 VPS y bus factor 1. |

---

## §3 Clean code — backend

### 3.1 Aspectos positivos

- **Controllers delgados**: los 13 controllers de `infrastructure/web/` solo parsean input, delegan a services y mapean output. Ej: `ReservaController.java:22-64` no contiene lógica de negocio.
- **Transacciones bien delimitadas**: `@Transactional` en cada método que escribe; `readOnly=true` en consultas (`ReservaService.java:122`, `TenantService.java:46`, `DisponibilidadService.java:44`, `DirectorioService.java:19`, `CalendarioService.java:28,36`). Uso deliberado y documentado de `noRollbackFor=BadCredentialsException.class` en `refresh` (`AuthService.java:117`, `ClienteAuthService.java:98`) para commitear la revocación de tokens robados.
- **DTos separados de entidades**: la API nunca expone JPA; 8 contenedores de `record` en `application/dto/` con Bean Validation.
- **Errores uniformes**: `{timestamp, status, error, message}` vía `GlobalExceptionHandler.java:25-96`.
- **72 tests** (16 archivos: 11 unit + 5 integración con Testcontainers) cubriendo la lógica crítica: máquina de estados, concurrencia anti doble-reserva (10 hilos), webhook MP, rotación de refresh con detección de reuso, anonimización Ley 21.719. Ver §8.2.

### 3.2 Deuda concentrada

#### GOD class: `ReservaService` (303 líneas, 10 dependencias inyectadas)
`backend/.../application/usecase/ReservaService.java:30-39` orquesta reserva + pago + notificación + pasarela + webhook. `procesarWebhookPago` (`:244-277`) mezcla parseo, idempotencia, registro de pago y notificación en un solo método.

**Recomendación:** separar `PagoService` y un `WebhookHandler`. No es urgente — todavía es cohesivo (todo es ciclo de reserva) — pero es el siguiente candidato a partirse si crece.

#### `NotificacionAdapter` (261 líneas, 3 métodos casi idénticos)
`backend/.../infrastructure/adapter/NotificacionAdapter.java:160-206` (`enviar`), `:125-158` (`enviarReset`), `:208-244` (`enviarCitaConfirmada`) repiten el mismo patrón try/catch + contador de fallos + log. **Duplicación evidente.**

**Recomendación:** extraer un método `enviarConManejoErrores(JavaMailSender, Runnable envio, String contexto)` que centralice el try/catch + `fallosConsecutivos` + `ultimoError`/`ultimoFalloEn` + log.

#### `WebhookController.recibirWebhook` (~100 líneas, anidamiento profundo)
`backend/.../infrastructure/web/WebhookController.java:34-133` hace parseo de body + validación de firma + consulta MP + registro de pago en un solo método con anidamiento profundo.

**Recomendación:** extraer `parseTipo`, `parseDataId`, `procesarPago`.

#### Duplicación DRY entre `AuthService` y `ClienteAuthService`
- `sha256()` **duplicado idéntico**: `AuthService.java:231-238` y `ClienteAuthService.java:188-195`.
- `normalizarEmail()` **duplicado idéntico**: `AuthService.java:64-66` y `ClienteAuthService.java:51-53`.
- Patrón `generarTokenPlano()`/`emitirTokens()` duplicado: `AuthService.java:215-229` y `ClienteAuthService.java:168-186`.
- Patrón `ejecutarTrasCommit` (AFTER_COMMIT + `CompletableFuture.runAsync`) duplicado: `NotificacionAdapter.java:112-123` y `WhatsappStubAdapter.java:74-85`.
- Patrón `buscar(tenantId, id)` + `findByIdAndTenantId(...).orElseThrow(RecursoNoEncontradoException)` repetido en `ReservaService.java:291-294`, `ServicioService.java:48-51`, `CalendarioService.java:68-70`, `ClienteService.java:35-36`.

**Recomendación:** extraer un `PasswordResetService` compartido (el `PLAN_MEJORA_FRONTEND.MD` §B2 ya lo propone como "Opción A: recomendada") y un util `HashUtil`/`EmailUtil`. El `PLAN_MEJORA_FRONTEND.MD:565` lo anticipa.

#### Comentarios `{@link ExpiracionService}` rotos (bug de docs)
La clase `ExpiracionService` fue renombrada/dividida en `CicloReservaJobs` + `MantenimientoJobs` (commit `4246519`), pero 3 javadocs siguen apuntando al nombre viejo:
- `TenantService.java:26` — `{@link ExpiracionService#purgarTenantsCerrados()}` (roto; vive en `MantenimientoJobs`).
- `ClienteService.java:22` — `{@link ExpiracionService#anonimizarInactivos()}` (roto; vive en `MantenimientoJobs`).
- `ReservaService.java:189` — "El barrido diario de ExpiracionService cubre..." (roto; vive en `CicloReservaJobs`).

Confirmado por `target/surefire-reports/TEST-cl.reservakids.application.ExpiracionServiceTest.xml` — un test histórico con ese nombre que ya no existe en `src/test`. Estos `{@link}` rotos rompen el javadoc y confunden a los lectores de la arquitectura. **Prioridad de fix alta (barato).**

#### Magic numbers hardcoded
- `RateLimitFilter.java:24` — `MAX_POR_MINUTO = 30` y `:62` — `10_000` (umbral de purga). El 30 no es configurable; debería serlo para tuning de prod.
- `SecurityConfig.java:34` — `BCryptPasswordEncoder(10)` — el 10 está hardcoded (OWASP 2023+ recomienda 12 para entornos con más CPU).
- `ReservaController.java:28` — `PageRequest.of(page, Math.min(size, 100))` — el 100 (tope de página) es magic number.
- `CitaTexto.java:14-15` — el patrón `"EEEE d 'de' MMMM, HH:mm"` y el `Locale` hardcoded.
- `MantenimientoJobs.java:60,80,107,125` — los cron `"0 30 4 * * *"` etc. están inline; sin documentación de por qué esas horas.
- `AuthService.java:183`, `ClienteAuthService.java:136` — `new byte[48]` para tokens (384 bits). Razonable pero sin constante nominal.

#### Manejo de excepciones
- `MercadoPagoAdapter.java:87` — `throw new RuntimeException("Error al comunicarse con Mercado Pago", e)`. Excepción genérica que se mapea a 500. En `ReservaService.cotizar` se captura y degrada (`:168-171`), pero `AgendaService.java:116` no la captura — una caída de MP en el agendamiento revierte toda la TX (comportamiento deseado según el comentario `:114-115`, pero el `RuntimeException` filtra el mensaje original en logs).
- `AuthService.java:106,130,131,177,210` y `TenantService` usan `orElseThrow()` sin mensaje — el `NoSuchElementException` no tiene handler específico en `GlobalExceptionHandler` y cae al 500 genérico. Estos casos "imposibles" (tenant desaparecido entre query y uso) deberían tener un handler explícito o lanzar `IllegalStateException` con contexto.
- `WebhookController.java:126` — `catch (Exception e)` muy amplio; captura incluso `NullPointerException` y los transforma en 500. Necesario porque MP reintenta ante 500, pero oculta bugs.

#### Otros menores
- `RefreshTokenCliente.java:36` — `private boolean revocado;` **sin `= false`** (a diferencia de `RefreshToken.java:32` que sí lo tiene). Inofensivo (boolean default false) pero **inconsistente**.
- `Reserva.java:67` — `private EstadoReserva estado = EstadoReserva.PENDIENTE;` con default en la entidad; pero `AgendaService.java:105` setea `PENDIENTE_PAGO` explícitamente y `crearSolicitudPublica` no setea estado (usa el default). Mezclar defaults de entidad con asignación explícita es frágil.
- `NotificacionAdapter.java:163` — `.map(u -> u.getEmail())` en vez de `Usuario::getEmail` (method reference) — inconsistencia de estilo.

---

## §4 Clean code — frontend

### 4.1 Estructura

```
frontend/src/
├── main.js             # Arranque: createApp + Pinia + router
├── App.vue             # Solo <RouterView/> (3 líneas)
├── style.css           # Design system Tailwind 4 (@theme) + utilidades
├── api/                # Clientes HTTP axios con interceptores
│   ├── client.js           # Dueño (auth Bearer + refresh)
│   └── clienteClient.js    # Cliente/apoderado (sesión separada)
├── stores/             # Pinia (auth)
│   ├── auth.js             # Store del dueño
│   └── clienteAuth.js      # Store del cliente
├── services/           # Capa fina de endpoints (1 archivo por dominio)
├── composables/        # useAsync, useBaseURL, useCurrency, useDuration
├── components/         # 10 componentes base (design system)
└── views/              # 16 vistas
```

### 4.2 Duplicación estructural (DRY)

- `api/client.js` y `api/clienteClient.js` son **prácticamente idénticos** (`client.js:1-47` vs `clienteClient.js:1-43`): misma `baseURL`, mismo interceptor de request, mismo interceptor de response con el mismo patrón `refreshing`/`_retry`. Solo cambian el store y la ruta de redirección. **Falta una fábrica** `createAuthClient(store, loginRoute)`.
- `stores/auth.js` y `stores/clienteAuth.js` también son simétricos (`auth.js:1-54` vs `clienteAuth.js:1-58`). Mismo patrón, distintas claves/endpoints.
- Bloques `catch (e) { error.value = e.response?.data?.message || '...' }` repetidos en 8+ vistas. Centralizar en el composable o en un handler global.

### 4.3 Cero tests, sin lint, sin `npm audit` en CI

- **Sin tests frontend**: no existe `*.spec.js`, ni Vitest, ni carpeta `tests/`. `PLAN_MEJORA_FRONTEND.MD` §3.1 los planifica (Sprint 3).
- **Sin lint/format**: `package.json:6-10` solo tiene `dev`/`build`/`preview`. No hay ESLint, Prettier ni script de lint.
- **Sin `npm audit` en CI**: `.github/workflows/ci.yml:41-44` solo ejecuta `npm ci` + `npm run build`. La mitigación es Dependabot semanal (`.github/dependabot.yml:11-15`), pero no hay bloqueo de merge por vulnerabilidades.

### 4.4 Bugs funcionales (ver §6 para los críticos)

- `BaseInput.vue:3,11` declara prop `modeloValue` y emite `update:modeloValue`, pero se consume como `<BaseInput v-model="token">` (`ConfiguracionView.vue:91,102`). En Vue 3, `v-model="x"` se traduce a `:modelValue` + `@update:modelValue`, que **no coinciden**. Resultado: los campos de Access Token y Webhook Secret de Mercado Pago **no se enlazan**. Es el único uso de `BaseInput` en todo el proyecto. **Fix: renombrar a `modelValue` o usar `v-model:modeloValue`.**
- `clienteClient.js:35` llama `auth.logout()` (petición al backend) mientras `client.js:37` llama `auth.logoutLocal()`. En el path de "refresh fallido tras 401" hacer un `logout()` puede fallar de nuevo y demorar la expulsión; conviene `logoutLocal()` para coherencia.
- `ServerErrorView.vue` existe pero **no está registrado en ninguna ruta** (huérfano).
- Sin guard de "ya autenticado" que redirija a `/panel` o `/clientes` si un usuario logueado entra a `/login` o `/clientes/entrar`.
- `useBaseURL.js:1` exporta una constante, no un composable (mal nombrado: no es `use*` que retorna reactive). Debería ser `apiBase.js`.
- `DashboardLayout.vue:5,22,28,49,70` usa `api` directamente en vez de un service — lógica de API fuera de la capa services.
- `LoginView.vue:30-65` incluye **lógica de confetti con Canvas 2D y `requestAnimationFrame`** acoplada al formulario de auth. Extraer a composable `useConfetti`. El cleanup de `animationId` está bien (`:65`).

### 4.5 Accesibilidad (a11y)

**Bueno:**
- Componentes base con roles/aria: `BaseModal.vue:23-25` (`role="dialog"`, `aria-modal`, `:aria-label`), `BaseToast.vue:45-46`, `ErrorBanner.vue:11-12` (`role="alert"`, `aria-live="polite"`), `LoadingSpinner.vue:8`, `BasePagination.vue:14-15,21,34`, `DashboardLayout.vue:103,172`.
- Imágenes decorativas con `alt=""` + `aria-hidden="true"` (`LandingPageView.vue:35-37`, `PublicSiteView.vue:79,86,105`).
- `lang="es"` correcto (`index.html:2`).

**Deuda:**
- **Muchos inputs sin `<label>` asociado.** `PublicSiteView.vue:175-187` (form de solicitud: nombre, teléfono, email, niños, comuna, comentarios) usan solo `placeholder` como etiqueta — `placeholder` no sustituye al label. Igual en `SolicitudesView.vue:118-135`, `ServiciosView.vue:55-64`, `CalendarioView.vue:51-60`, `DashboardLayout.vue:197`. `BaseInput.vue` tampoco genera `<label>`. `LoginView.vue` y `ClienteLoginView.vue` sí tienen `<label for>` correctos — inconsistencia.
- `BaseModal.vue` no atrapa foco (`focus trap`) ni restaura foco al cerrar — `role="dialog"`+`aria-modal` sin trap es incompleto. Sin gestión de `Esc` para cerrar (solo click en backdrop/cancelar).
- Botones-icono sin `aria-label` en algunos casos (`DashboardLayout.vue:146-148` botón salir solo icono).

### 4.6 Responsive / PWA

- **No es PWA.** `frontend/public/` está **vacío**: sin `manifest.webmanifest`, sin `service-worker.js`, sin `favicon`, sin `robots.txt`. No hay `vite-plugin-pwa` en dependencias. `PLAN_MEJORA_FRONTEND.MD` §3.2 lo planifica (Sprint 3).
- **Responsive: sí, mobile-first razonable.** Tailwind 4 con breakpoints `sm/md/lg` usados consistentemente. Sidebar fija en `md:` y bottom-nav + top-bar en móvil (`DashboardLayout.vue:86,132,134,159,164,171`). `viewport` correcto (`index.html:5`).
- Fuentes de Google Fonts bloquean el render (`<link rel="stylesheet">` sin `media swap`/`preload` en `index.html:9-16`) — impacto CLS/perf en móvil con red lenta.
- **Sin pin de versión de Node** (no existe `.nvmrc`/`.node-version`/`mise.toml`). El CI usa Node 22 (`.github/workflows/ci.yml:38`) pero localmente no hay candado → riesgo de "frontend que no compila". Recomendar añadir `.nvmrc` con `22`.

---

## §5 Ciberseguridad

### 5.1 Autenticación y JWT

| Aspecto | Detalle | Cita |
|---|---|---|
| Algoritmo | **HS256** (simétrico). `Keys.hmacShaKeyFor(secret.getBytes(UTF_8))` deriva la clave del secreto. | `JwtService.java:35` |
| Secreto | Desde `app.jwt.secret` (`${JWT_SECRET:}`). **Validación de longitud mínima 32 chars** al construir; falla `IllegalStateException` si no se setea o mide <32. | `JwtService.java:32-34` · `application.yml:46` |
| Issuer | `reservakids` (configurable). **Validado con `requireIssuer`** al parsear. | `JwtService.java:29,47,76` |
| Audience | `reservakids-web`. **Validado con `requireAudience`**. | `JwtService.java:30,48,77` |
| Expiración access | 15 min (`app.jwt.access-minutes`, default 15). `parseSignedClaims` verifica `exp` automáticamente (jjwt). | `JwtService.java:31,38,49,74-80` · `application.yml:49` |
| Refresh | **Token opaco** (no JWT), 48 bytes aleatorios (`SecureRandom`), persistido como **SHA-256** (nunca en claro). 7 días (`app.jwt.refresh-days`). | `AuthService.java:216-225` · `RefreshToken.java:25-26` |
| Claims | `sub`=usuarioId, `tenantId`, `rol`. Token de cliente: `sub`=cuentaId, `rol=CLIENTE`, `email`, **sin tenantId**. | `JwtService.java:50-51,58-70` |
| Rotación refresh | El refresh usado se revoca y se emite uno nuevo. Reusar uno **ya rotado** revoca toda la familia (detección de robo, OWASP). | `AuthService.java:121-124` · `ClienteAuthService.java:102-105` |
| `ClassCastException` Integer/Long | `tenantId` se lee como `Number` (no `Long`) para evitar el cast que Jackson hace con Integer pequeños. | `JwtService.java:84-87` |

**Observación:** HS256 con secreto compartido es aceptable para una sola API, pero no permite rotación independiente de claves de firma vs. cifrado. Considerar RS256/EdDSA si se prevén múltiples consumidores. El comentario en `application.yml:96-98` reconoce esto y recomienda `CRED_ENC_KEY` dedicada en producción.

### 5.2 Hash de contraseñas

- **BCrypt con strength 10.** `new BCryptPasswordEncoder(10)`. | `SecurityConfig.java:34`
- Hasheo al registrar (`AuthService.java:93`, `ClienteAuthService.java:79`) y al resetear (`AuthService.java:211`, `ClienteAuthService.java:164`). Verificación con `passwordEncoder.matches()`.
- Columna `password_hash VARCHAR(100)` (`V1__esquema_inicial.sql:16`) — suficiente para hash BCrypt de 60 chars.
- **Anti-enumeración en login:** mensaje idéntico "Credenciales inválidas" tanto si el email no existe como si la password falla (`AuthService.java:102,104`, `ClienteAuthService.java:87,89`).
- **Recomendación:** strength 10 es el mínimo razonable hoy; OWASP 2023+ recomienda 12 para entornos con más CPU. No es vulnerabilidad, es oportunidad de hardening.

### 5.3 CORS

| Aspecto | Detalle | Cita |
|---|---|---|
| Orígenes | Solo los de `app.cors.allowed-origins` (default `http://localhost:5173`). **No wildcard.** | `SecurityConfig.java:63` · `application.yml:52` |
| Métodos | `GET, POST, PUT, DELETE, OPTIONS` | `SecurityConfig.java:64` |
| Headers | Solo `Authorization`, `Content-Type` | `SecurityConfig.java:65` |
| Credenciales | **`allowCredentials=true`** — necesario para la cookie de refresh HttpOnly. | `SecurityConfig.java:68` |
| Path | Registrado solo para `/api/**` | `SecurityConfig.java:71` |

**Correcto:** `allowCredentials=true` + origen exacto (no `*`) es la combinación segura.

### 5.4 Rate limiting

- **Existe**, implementado con filtro propio `RateLimitFilter.java:22`.
- **Política:** ventana fija de **30 req/min/IP** (`MAX_POR_MINUTO=30`, `:24`).
- **Alcance:** solo `/api/public/`, `/api/auth/`, `/api/cliente-auth/` (`shouldNotFilter`, `:39-43`). Las rutas autenticadas del panel NO tienen rate limiting.
- **Almacenamiento:** `ConcurrentHashMap` en memoria (`:36`) — **no distribuido**. El propio comentario admite: "suficiente para una instancia; migrar a bucket4j/Redis si se escala" (`:19`).
- **Detrás de proxy:** usa la **ÚLTIMA** IP de `X-Forwarded-For` (la del proxy de confianza), no la primera (falsificable por el cliente). Activado solo con `app.security.trust-proxy=true`. | `RateLimitFilter.java:68-79`
- **Test de bypass:** `RateLimitFilterTest.java:20` verifica específicamente que variar la primera IP de XFF no evade el límite. Buen test de regresión.

**Riesgo:** en despliegue multi-instancia sin sticky sessions, el límite se cuenta por nodo. Para login, 30/min/IP es relativamente generoso para fuerza bruta — aunque BCrypt + enumeración silenciosa mitigan. Recomendar bajar a 10-15/min/IP para `/api/auth/login` y `/api/cliente-auth/login` específicamente, y hacerlo configurable.

### 5.5 Cifrado de credenciales en reposo (`CRED_ENC_KEY`)

- **Implementación:** `CredentialCipher.java:31`, AES-256-GCM (`AES/GCM/NoPadding`, `:34`), IV aleatorio de 12 bytes por mensaje (`:35`), tag de 128 bits (`:36`).
- **Formato:** `enc:v1:<base64(iv|ciphertext|tag)>` (`:33,71`).
- **Clave:** `CRED_ENC_KEY` (base64 de 32 bytes exactos, `:46-48`). **Si no se define, se deriva vía SHA-256 de `JWT_SECRET`** (`:49-53`) con un `log.warn`.
- **Compatibilidad:** `decrypt` tolera valores legados en texto plano (sin prefijo) y los devuelve tal cual (`:79-81`) — tokens guardados antes de V10 siguen funcionando y se re-cifran al re-guardar.
- **Uso:** cifra `mpAccessToken` y `mpWebhookSecret` del tenant al guardar (`TenantService.java:67,69`); descifra solo al usar (`MercadoPagoAdapter.java:77`, `WebhookController.java:91,101`).
- **Migración:** `V10__cifrar_credenciales_mercadopago.sql` amplía columnas y añade `mp_webhook_secret`.

**Riesgo menor:** derivar la clave de cifrado de `JWT_SECRET` cuando `CRED_ENC_KEY` está vacía vincula dos secretos que deberían rotarse independientemente. Asegurar que en prod `CRED_ENC_KEY` esté seteada. No hay mecanismo de re-cifrado masivo tras rotación de `CRED_ENC_KEY`.

### 5.6 Multi-tenant y riesgo de cross-tenant leakage

**Patrón correcto predominante:** cada query de negocio incluye `tenantId` y los repositorios exponen métodos `...ByIdAndTenantId(...)`. Los controllers extraen `tenantId` del **principal JWT** (`principal.tenantId()`), nunca del body o query.

Tests que verifican el aislamiento: `ReservaServiceTest.java:140-145` (`cotizarSoloReservasDelTenant`), `ClienteServiceTest.java:77-81` (`soloClientesDelTenant`).

**Riesgos / puntos de atención:**

1. **`ReservaService.respuesta()` y `notificarCitaConfirmada()`** usan `clienteRepository.findById(reserva.getClienteId())` **sin** `tenantId` (`ReservaService.java:282,299`). No es fuga directa porque la `reserva` ya se obtuvo vía `findByIdAndTenantId` (tenant-scoped) y el `clienteId` viene de ahí, pero **rompe el patrón defense-in-depth**: si una reserva tuviera un `clienteId` huérfano/apuntando a otro tenant, se leería. Recomendar usar `findByIdAndTenantId` para consistencia.

2. **El webhook público toma `tenantId` del path:** `WebhookController.java:33-35` (`@PathVariable Long tenantId`). Es por diseño (MP no conoce al usuario autenticado), y la validación de firma HMAC (`:90-96`) acota la falsificación. Sin `mpWebhookSecret` configurado, la única defensa es re-consultar el pago a MP (`:100-105`).

3. **`Pago` no tiene `tenant_id`** — se asocia vía `reserva_id` (`Pago.java:25-26`, `V1__esquema_inicial.sql:84-91`). Las queries de pago siempre van a través de `reservaId` que ya está tenant-scoped. El export de tenant usa subquery `WHERE p.reservaId IN (SELECT r.id FROM Reserva r WHERE r.tenantId = :tenantId)` (`PagoRepository.java:34-38`). Correcto.

4. **`CuentaCliente` y `RefreshTokenCliente` son globales** (no tenant-scoped) por diseño — el cliente (apoderado) no pertenece a ningún negocio. El JWT de cliente **no lleva `tenantId`** (`JwtService.java:58-70`). El área `/api/cliente/**` solo expone el directorio y el agendamiento, donde el `tenantId` se obtiene del `slug` del negocio (`AgendaService.java:42`). Correcto.

5. **Los jobs `MantenimientoJobs` y `CicloReservaJobs` operan sobre TODOS los tenants** (queries sin `tenantId`). Es por diseño (jobs administrativos globales), pero significa que un bug en esos jobs afecta a todos los tenants — verificar que las transiciones respeten el tenant de cada reserva (lo hacen: `cancelarPorExpiracion` usa `reserva.getTenantId()`, `CicloReservaJobs.java:125`).

**Veredicto multi-tenant:** aislamiento sólido en la capa web y en la mayoría de repositorios. El punto 1 es el único hallazgo real de hardening (no vulnerabilidad confirmada).

### 5.7 Validación de input

- **Bean Validation (jakarta.validation) extensivo** en todos los DTOs de entrada:
  - `AuthDtos.java:13-17`: `@NotBlank`, `@Size(max=120)`, `@Pattern(regexp="[a-z0-9-]{3,60}")` para slug, `@Email`, `@Size(min=8,max=72)` para password.
  - `ReservaDtos.java:11-22`: `@NotNull`, `@NotBlank`, `@Size(max=...)`, `@Min/@Max`, `@AssertTrue` para consentimiento Ley 21.719.
  - `PagoRequest` valida `tipo` con `@Pattern(regexp="ABONO|DEVOLUCION")` (`ReservaDtos.java:35`).
  - `HorarioAtencionDtos.java:17,22`: `@Min(1)@Max(7)` para día, `@Min(5)@Max(120)` para intervalo.
  - Todos los controllers usan `@Valid`.
- **Manejo de errores de validación:** `GlobalExceptionHandler.java:49-55` devuelve 400 con detalle campo-mensaje.
- **Sanitización de texto libre:** **NO hay sanitización XSS explícita** (sin `@SafeHtml`, sin OWASP sanitizer, sin Jsoup). Se confía en que la API consume/sirve JSON (no HTML server-side). **Si el frontend renderiza `comentarios`, `nombre`, `descripcion` con `v-html` o `innerHTML, hay riesgo XSS almacenado.** Verificado: el frontend **no usa `v-html`** (0 coincidencias), así que hoy no es explotable. Los campos de texto libre (`comentarios` hasta 2000 chars, `descripcion` hasta 2000) son el principal vector si cambia el frontend.
- **SQL injection:** las queries JPQL usan **parámetros nombrados** (`:tenantId`, `:fecha`) — seguras. La única native query (`ReservaRepository.java:45`, `pg_advisory_xact_lock`) usa `:tenantId` y `:fecha` con `cast(... AS text)` y parámetros bind. **No hay SQL injection.**
- **Normalización server-side:** emails normalizados a trim+lower (`AuthService.java:64-66`, `ClienteAuthService.java:51-53`); teléfonos a E.164 chileno (`Cliente.normalizarTelefono`, `Cliente.java:30-42`). El registro de cliente valida el teléfono contra `56\d{9}` (`ClienteAuthService.java:62-66`).
- **Precio/duración recalculados server-side:** `AgendaService.java:65-66` recalcula `duracionTotal` y `total` desde el catálogo, **nunca del cliente** — evita manipulación de precios.

### 5.8 Manejo de secretos

- **No hay secretos hardcodeados** en código Java (verificado).
- Todos los secretos provienen de **variables de entorno con defaults vacíos**: `DB_PASSWORD` (`application.yml:7`), `MAIL_PASSWORD` (`:19`), `JWT_SECRET` (`:46`), `CRED_ENC_KEY` (`:99`), `MAIL_USERNAME` (`:18`).
- **Defaults vacíos intencionalmente seguros:** `JWT_SECRET` vacío por defecto → `IllegalStateException` al arrancar si no se setea o si mide <32 chars. Fuerza configuración en producción.
- **El test usa un secret de 64 bytes hardcodeado** (`application-test.yml:14`: `test-secret-de-64-bytes-...`) — aceptable para test, no viaja a prod.
- **Dockerfile** no introduce secretos.
- **Riesgo:** no se detecta gestión de secretos centralizada (sin Vault/Spring Cloud Config). Asumir gestión por entorno del orquestador. Documentar el rotado de `JWT_SECRET` (invalida todos los tokens) y `CRED_ENC_KEY` (invalida credenciales MP descifrables — no hay mecanismo de re-cifrado masivo tras rotación).

### 5.9 Cookies de refresh token

| Flag | Valor | Cita |
|---|---|---|
| `HttpOnly` | **`true`** (siempre) | `RefreshCookieService.java:38,48` |
| `Secure` | configurable (`app.cookies.secure`, default `true`) | `:39,49` · `application.yml:56` |
| `SameSite` | configurable (`app.cookies.same-site`, default `Lax`) | `:62` · `application.yml:58` |
| `Path` | `/api` (restringido) | `:24,40,50` |
| `Max-Age` | `refreshDays * 86400` (7 días) | `:41` |

**Dos cookies separadas:** `rk_refresh` (dueño) y `rk_cliente_refresh` (cliente) (`:20-21`). El logout las borra con `Max-Age=0` (`:46-53`).

**Correcto y bien pensado.** HttpOnly cierra el robo vía XSS; SameSite=Lax + CORS restrictivo + `Content-Type: application/json` (que exige preflight) mitiga CSRF. En dev (HTTP) `Secure` debe ser `false` o el navegador descarta la cookie — el comentario lo avisa (`application.yml:54-55`).

### 5.10 Webhooks de Mercado Pago — validación de firma

- **Implementación:** `WebhookController.java:141-164`, método `firmaValida`.
- **Algoritmo:** HMAC-SHA256 con el `mpWebhookSecret` del tenant (descifrado al validar, `:91`).
- **Manifiesto firmado:** `id:<dataId>;request-id:<xRequestId>;ts:<ts>;` (`:151-152`). `dataId` se normaliza a minúsculas.
- **Comparación en tiempo constante:** `MessageDigest.isEqual(...)` (`:158-159`) — **no** `String.equals`. Correcto contra timing attacks.
- **Parseo del header `x-signature`:** formato `ts=<ts>,v1=<hex>`, split por comas (`:167-175`).
- **Fallo cerrado:** cualquier excepción en la validación → `return false` (`:160-163`) → HTTP 401.
- **Idempotencia:** `pagoRepository.existsByReferenciaExterna(paymentId)` antes de procesar (`ReservaService.java:249`).
- **Anti-retry infinito:** IDs no numéricos y referencias no numéricas devuelven 200 "ignorado" para que MP no reintente (`WebhookController.java:74-77,119-122`).
- **Tests:** `WebhookMercadoPagoIT.java:71-125` cubre firma inválida (401), topic no-payment (200), tenant sin MP (400), tenant inexistente (400), data.id no numérico (200).

**Riesgo:** el webhook es **opcional por tenant**. Si `mpWebhookSecret` es null, **no se valida la firma** y se cae al fallback de re-consultar el pago a MP (`WebhookController.java:90-96`). Un tenant sin secreto configurado acepta webhooks forjados que solo se filtran por la re-consulta a MP. **Recomendar exigir el secreto en producción** (validar al guardar la configuración, o rechazar webhooks sin firma siempre).

### 5.11 Manejo de errores — filtrado de info sensible

- **Stacktraces nunca expuestos:** `application.yml:29` `server.error.include-stacktrace: never`.
- **Handler genérico:** `GlobalExceptionHandler.java:88-92` devuelve `"Error interno del servidor"` sin detalles para `Exception.class` (loguea el stacktrace internamente).
- **Actuator limitado:** solo `/actuator/health` expuesto (`application.yml:36`), sin detalles. Health de mail deshabilitado (`:38-42`).
- **Errores de parseo:** `MethodArgumentTypeMismatchException`, `DateTimeParseException`, `HttpMessageNotReadableException` → 400 con mensaje genérico "Parámetro o cuerpo de la petición inválido" (`GlobalExceptionHandler.java:61-65`) — evita filtrar el stacktrace en el endpoint público (los bots los gatillan).
- **`DataIntegrityViolationException`:** 409 "Conflicto con un registro existente" — no filtra el constraint ni la tabla (`:72-75`).
- **Estado SMTP:** `NotificacionAdapter.estadoEnvios()` (`:87-91`) expone `fallosConsecutivos`, `ultimoFalloEn`, `smtpConfigurado` — **NO** expone `ultimoError` (que puede contener datos de otro tenant). El comentario `:74-77` lo justifica explícitamente. Es la revisión **S2** (ver §5.13).
- **Logs con datos sensibles:** revisado — no se loguean tokens, passwords ni hashes. `NotificacionAdapter.java:132-133` loguea el link de reset **solo** cuando no hay SMTP (MVP); con SMTP configurado el enlace no se loguea (`:131` comentario: "es una credencial").

### 5.12 Seguridad frontend

#### Almacenamiento del JWT y riesgo XSS
- **Access token (15 min) en `sessionStorage`:** `stores/auth.js:12,24`; `stores/clienteAuth.js:13,27`. `sessionStorage` no mitiga XSS (igual que `localStorage`); solo reduce la persistencia a la pestaña. Cualquier XSS lo roba. **Mitigación vigente:** TTL 15 min. **No hay CSP** (ver abajo) como defensa en profundidad.
- **Refresh token (7 días):** **NO se guarda en JS.** Vive en una **cookie HttpOnly** gestionada por el backend. El store lo declara explícitamente (`auth.js:10-11`, `clienteAuth.js:8-10`). **Es el punto fuerte del diseño**: el token sensible de largo TTL está fuera del alcance de JS → robo por XSS de refresh no es posible.

#### `v-html` / render de HTML crudo
- Búsqueda exhaustiva de `v-html`, `innerHTML`, `dangerouslySetInnerHTML`, `eval(`, `new Function`, `document.write` en `frontend/src/` → **0 coincidencias.** Todo el contenido del backend se renderiza con `{{ }}` (interpolación texto, auto-escapado por Vue). [BUENO].
- Único render de texto del usuario: `SolicitudesView.vue:111` `{{ r.comentarios }}` con `whitespace-pre-line` — escapado, seguro.
- `window.location.href = data.initPoint` (`AgendarView.vue:100`) — redirección a Mercado Pago. `initPoint` proviene del backend (no del usuario). [BAJO] si el backend se compromete sería open-redirect; validar que el backend solo devuelva URLs `https://*.mercadopago.cl|com`.

#### Secretos en el bundle
- Única variable de entorno inyectada al bundle: `VITE_API_URL` (`api/client.js:8`, `api/clienteClient.js:6`, `stores/auth.js:5`, `stores/clienteAuth.js:5`, `composables/useBaseURL.js:1`). Es un valor público (URL de la API). [BUENO].
- **No se exponen** `JWT_SECRET`, `CRED_ENC_KEY`, `MAIL_*`, etc. en el frontend (esas son de backend, en `.env` raíz).
- El Access Token de Mercado Pago se captura en `ConfiguracionView.vue:91` (`v-model="token"`, `type="password"`) y se envía al backend (`configuracionService.js:3-6`); no se persiste en el frontend. [BUENO] — **pero hoy no funciona por el bug de `modeloValue`** (ver §6).

#### Tokens en URL (reset password)
- `ResetPasswordView.vue:14` — `const token = computed(() => route.query.token || '')` y se envía en el body del POST (`:39`). El token viaja en la **query string** del enlace (`?token=...`).
  - **[MEDIO]** El token queda en historial del navegador, `Referer` a terceros, y logs del server/proxy. Recomendación estándar: usar fragment (`#token=`) que no se envía al server, o un flow donde el usuario pegue el token, o un token de un solo uso con TTL corto (TTL=30 min, `.env.example:32` — correcto). El backend invalida tras un uso.
- Para clientes, `clienteService.js:10-15` define el contrato, pero las vistas `ClienteResetView`/`ClienteResetConfirmView` **no existen** (ver §6), así que el flow de reset de cliente está incompleto en el frontend.

#### CSP y headers de seguridad
- **No hay meta tag CSP** en `index.html:1-22` ni en `dist/index.html`.
- **No hay headers de seguridad** en el servidor que sirve el frontend (Caddy). El `Caddyfile` en `docs/MANUAL_DESPLIEGUE.md:105-128` solo define `encode gzip`, `reverse_proxy` y `file_server` — **sin** `header Content-Security-Policy`, `X-Frame-Options`/`frame-ancestors`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`. Caddy gestiona TLS y HSTS implícito solo con cierta config; aquí no se observa.
  - **[ALTO]** **Ausencia de CSP**: cualquier inyección de script (paquete npm comprometido, dependencia supply-chain) se ejecuta sin restricciones y puede leer `sessionStorage` (access token). Recomendado:
    ```
    Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' https://fonts.googleapis.com 'unsafe-inline'; font-src https://fonts.gstatic.com; img-src 'self' data:; connect-src 'self' https://*.mercadopago.cl; frame-ancestors 'none'
    ```
  - **[MEDIO]** Sin `X-Content-Type-Options: nosniff`, sin `Referrer-Policy` (el token de reset en query se filtra vía Referer), sin `frame-ancestors` (clickjacking).
- Las fuentes se cargan desde `fonts.googleapis.com` / `fonts.gstatic.com` (`index.html:7-16`) — conexión a terceros; una CSP estricta debe allowlistearlos.

#### Validación de formularios
- Validación **solo HTML5 nativa** (`required`, `type="email"`, `minlength`, `pattern`, `min`/`max`). Ejemplos: `LoginView.vue:131` `pattern="[a-z0-9-]{3,60}"` para el slug; `:152` `minlength="8"` password; `ResetPasswordView.vue:68` `minlength="8"`; `PublicSiteView.vue:181` `min="1"`, `:189` checkbox `required` (consentimiento Ley 21.719).
- **[MEDIO]** **No hay validación en JS ni mensajes de error por campo** (salvo el banner genérico `ErrorBanner`). El usuario puede evadir la validación HTML5 (editar DOM); el backend debe validar todo (lo hace). No hay lib de validación (VeeValidate, Zod, Yup).
- **[BAJO]** Teléfono sin pattern (`ClienteLoginView.vue:86`, `PublicSiteView.vue:177`) — el backend normaliza, pero el frontend no previene el problema de formatos `+56` vs `9...` documentado en `docs/REVISION_FALLAS_2_ANOS.md:73`.

#### CSRF protection
- El backend **deshabilita CSRF explícitamente**: `SecurityConfig.java:40` `csrf(csrf -> csrf.disable())` con comentario "API stateless con Bearer token, sin cookies de sesión".
- **Pero el refresh token SÍ viaja en cookie HttpNow** (`RefreshCookieService.java:38-62`, `application.yml:53-58`), por lo que el comentario quedó **desactualizado** respecto al diseño actual (commit `dd50ab4` "refresh token en cookie HttpOnly").
- Mitigación vigente: `SameSite=Lax` (`application.yml:58`) → los POST cross-site **no** envían la cookie, por lo que `/auth/refresh` y `/auth/logout` (POST) están protegidos a nivel navegador moderno. El access token va en header `Authorization` (no en cookie), así que las operaciones del panel no son CSRF-elegibles.
- **[MEDIO]** **No hay defensa en profundidad** (sin CSRF token ni `SameSite=Strict` ni double-submit). `SameSite=Lax` no protege a navegadores antiguos que ignoran el atributo, ni a subdomain attacks. Recomendar: mantener `Lax` y **actualizar el comentario de `SecurityConfig.java:40`** para reflejar que ahora hay cookies; considerar `SameSite=Strict` para `/auth/refresh` si la UX lo permite, o un header `X-Requested-With`+validación server.

### 5.13 Etiquetas de revisión de seguridad (S1, S2, S3)

El código usa etiquetas `S1`/`S2`/`S3` para marcar revisiones de seguridad aplicadas:

| Etiqueta | Significado | Estado | Cita |
|---|---|---|---|
| **S1** | Cifrado de credenciales MP en reposo (AES-256-GCM) | ✅ Implementado | `CredentialCipher.java:16` · `V10__cifrar_credenciales_mercadopago.sql` |
| **S2** | Filtrado del estado del canal SMTP: `EstadoEnvios` NO expone `ultimoError` (que puede contener datos de otro tenant) | ✅ Implementado | `NotificacionAdapter.java:76,81-91` |
| **S3** | Validación de firma HMAC del webhook de MP | ✅ Implementado (opcional por tenant) | `WebhookController.java:86,136` · `Tenant.java:55` |

**Hallazgo S2:** el comentario en `NotificacionAdapter.java:76` dice "S2: `/api/sistema/notificaciones` lo ve cualquier usuario autenticado" — esto está **obsoleto** porque `SecurityConfig.java:52` ahora exige `hasRole("DUENO")` para `/api/**`. Solo los dueños ven el estado. Actualizar el comentario.

**Hallazgo S2 (violación de capa):** `SistemaController.java:19` inyecta `NotificacionAdapter` (una clase concreta de infra) directamente en vez de un puerto. Acoplamiento directo a la implementación. Recomendar definir un `EstadoNotificacionesPort` o mover `EstadoEnvios` a application y exponerlo vía un puerto.

### 5.14 Dependencias con CVE conocidos

Verificado contra la GitHub Advisory Database a la fecha del informe. Las versiones están en rangos `^` (package-lock v3 confirma las versiones base).

| Paquete | Versión | CVE | Severidad | Nota |
|---|---|---|---|---|
| **vite** (devDep) | ^6.0.7 | `CVE-2026-39363` Arbitrary File Read via Dev Server WebSocket · `CVE-2026-39364` `server.fs.deny` bypassed with queries · `CVE-2026-39365` Path Traversal en `.map` de deps optimizadas · `CVE-2026-53571` `server.fs.deny` bypass on Windows | High | Afectan al **dev server**. **Agravante:** `vite.config.js:11` usa `host: true` → el dev server escucha en `0.0.0.0` (red local). Un atacante en la LAN del desarrollador podría leer archivos del host. |
| **axios** (dep) | ^1.7.9 | `CVE-2025-27152` leak del header `Authorization` al seguir redirects cross-site (corregido en `1.8.0`) | Low/Medium | El frontend llama a un único origen propio, por lo que la exposición práctica es baja, pero el paquete está fuera de la rama parchada. |
| vue, vue-router, pinia, tailwindcss, @vitejs/plugin-vue | versiones actuales | sin CVE relevantes conocidos | — | Verificar con `npm audit`. |

**Backend:** Spring Boot 3.5.6, jjwt 0.12.6, Mercado Pago SDK 2.1.26 — verificar con `mvn dependency:tree` + Dependabot (activo semanal, `.github/dependabot.yml`).

**Recomendación:** actualizar Vite a la última 6.x parchada (o 7.x), quitar `host: true` o limitarlo a `127.0.0.1` salvo necesidad explícita, actualizar axios a ≥1.8.0, añadir `npm audit` al CI.

---

## §6 Hallazgos críticos actuales (trabajo en progreso sin commitear)

El `git status` muestra trabajo en progreso **no commiteado** que rompe funcionalidad existente:

```
M backend/.../application/usecase/ClienteAuthService.java
M backend/.../application/usecase/NotificacionPort.java
M backend/.../domain/model/PasswordResetToken.java
M backend/.../domain/repository/PasswordResetTokenRepository.java
M frontend/src/router/index.js
?? backend/src/main/resources/db/migration/V17__reset_token_cliente.sql
?? docs/PLAN_MEJORA_FRONTEND.MD
?? frontend/src/views/LandingPageView.vue
?? frontend/src/views/NotFoundView.vue
?? frontend/src/views/ServerErrorView.vue
```

Parece corresponder al **Sprint 2** del `PLAN_MEJORA_FRONTEND.MD` (reset de contraseña de clientes + vistas nuevas), a medio aplicar.

### 6.1 🔴 Router referencia 3 vistas inexistentes

`frontend/src/router/index.js:27,29,30` (modificado, sin commitear) referencia:
- `../views/ClienteHistorialView.vue` (ruta `/clientes/reservas`).
- `../views/ClienteResetView.vue` (ruta `/clientes/reset`).
- `../views/ClienteResetConfirmView.vue` (ruta `/clientes/reset/confirmar`).

Confirmado con `ls frontend/src/views/`: **ninguna de esas 3 existe**. El dynamic `import()` de un módulo inexistente lanza un error en runtime y rompe esas rutas. El guard `requiereCliente` deja pasar a `/clientes/reset` y `/clientes/reset/confirmar` (no tienen el meta, son públicas), lo cual es intencional, pero el destino no existe.

**Fix:** crear las 3 vistas (siguiendo `PLAN_MEJORA_FRONTEND.MD` §2.2 y §2.3) **o** revertir el cambio del router hasta que existan. No commitear el router en este estado.

### 6.2 🔴 `BaseInput` roto con `v-model` → credenciales de Mercado Pago no se enlazan

`frontend/src/components/BaseInput.vue:3,11` declara prop `modeloValue` y emite `update:modeloValue`, pero se consume como `<BaseInput v-model="token" ...>` en `ConfiguracionView.vue:91,102`. En Vue 3, `v-model="x"` se traduce a `:modelValue` + `@update:modelValue`, que **no coinciden** con `modeloValue`. Resultado: los campos de Access Token y Webhook Secret de Mercado Pago **no se enlazan** — el input no refleja ni actualiza la variable, y `guardarToken` enviaría `undefined`/vacío. Es el único uso de `BaseInput` en todo el proyecto.

**Fix:** renombrar `modeloValue` → `modelValue` y `update:modeloValue` → `update:modelValue` en `BaseInput.vue`. Cambio mínimo y aislado.

### 6.3 🟠 `PublicSiteView` lee `route.params.slug` que la catch-all no provee

`PublicSiteView.vue:15` hace `const slug = route.params.slug`, pero la ruta catch-all `/:pathMatch(.*)*` (`router/index.js:32`) **no define `slug`** → será `undefined`. El service `negocioService.catalogo(undefined)` terminaría en `GET /api/public/undefined`. El `DashboardLayout` enlaza la página pública como `/${auth.slug}` (`DashboardLayout.vue:97,142`), que cae en esta catch-all. En vue-router 4, esa ruta expone los segmentos en `route.params.pathMatch` (array), no `slug`. La vista siempre mostraría "Negocio no encontrado" salvo que exista un rewrite de servidor no visible.

**Fix:** añadir una ruta dedicada `/:slug` antes del catch-all, o leer `route.params.pathMatch[0]` con fallback.

---

## §7 Plan de acción priorizado (más barato → más caro)

### P0 — Críticos, bloquean funcionalidad (hoy)

1. **Fix `BaseInput`** (`modeloValue` → `modelValue`). 1 línea × 2. Restaura el form de Mercado Pago.
2. **Completar o revertir el router**: crear `ClienteHistorialView.vue`, `ClienteResetView.vue`, `ClienteResetConfirmView.vue` (Sprint 2 §2.2/§2.3) **o** quitar las 3 rutas hasta que existan. No commitear el router roto.
3. **Fix `PublicSiteView`** `slug` indefinido: ruta `/:slug` dedicada o leer `pathMatch[0]`.

### P1 — Hardening de seguridad (esta semana)

4. **Headers de seguridad en Caddy**: `Content-Security-Policy` (ver §5.12), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer` (mitiga el token en query), `frame-ancestors 'none'`. Editar `docs/MANUAL_DESPLIEGUE.md:105-128` y el Caddyfile real.
5. **Actualizar Vite** a última 6.x parchada (o 7.x); quitar `host: true` o limitar a `127.0.0.1`. Actualizar axios a ≥1.8.0.
6. **Añadir `npm audit` al CI** (`.github/workflows/ci.yml`) con fallo en high/critical.
7. **Exigir `mpWebhookSecret` en producción**: validar al guardar la configuración del tenant, o rechazar webhooks sin firma siempre (no solo re-consulta a MP).
8. **Actualizar comentario obsoleto de CSRF** en `SecurityConfig.java:40` (ahora hay cookies HttpOnly).
9. **Actualizar comentario obsoleto de S2** en `NotificacionAdapter.java:76` (ahora exige `hasRole("DUENO")`).

### P2 — Clean code / deuda técnica (próxima ventana de mantenimiento)

10. **Fix javadocs rotos** `{@link ExpiracionService}` en `TenantService.java:26`, `ClienteService.java:22`, `ReservaService.java:189` → `MantenimientoJobs`/`CicloReservaJobs`. Barato.
11. **Extraer `PasswordResetService` compartido** (drí `sha256()`/`normalizarEmail()` duplicados entre `AuthService` y `ClienteAuthService`). `PLAN_MEJORA_FRONTEND.MD` §B2 "Opción A" ya lo propone.
12. **Refactor `NotificacionAdapter`**: extraer `enviarConManejoErrores(...)` para eliminar los 3 métodos casi idénticos.
13. **Separar `ReservaService`** en `PagoService` + `WebhookHandler` si sigue creciendo.
14. **Extraer fábrica `createAuthClient(store, loginRoute)`** para eliminar la duplicación `client.js`/`clienteClient.js`.
15. **Fix `clienteClient.js:35`**: `logout()` → `logoutLocal()` para coherencia con `client.js:37`.
16. **Mover `CredentialCipher` detrás de un puerto** para cerrar la violación de capa de `TenantService.java:42`.
17. **Hacer configurable `MAX_POR_MINUTO`** y bajar a 10-15/min/IP para `/api/auth/login` y `/api/cliente-auth/login`.
18. **Usar `findByIdAndTenantId` en `ReservaService.respuesta()`/`notificarCitaConfirmada()`** (defense-in-depth, §5.6 punto 1).
19. **Añadir `.nvmrc` con `22`** para fijar la versión de Node local.
20. **Añadir `<label>` a los inputs** de `PublicSiteView`, `SolicitudesView`, `ServiciosView`, `CalendarioView`, `DashboardLayout` (a11y).

### P3 — Cobertura de tests (próximo sprint)

21. **Tests de autorización `SecurityConfig`**: verificar que un token de rol `CLIENTE` no pueda acceder a `/api/reservas` (la regla `hasRole("DUENO")` de `SecurityConfig.java:52` no tiene test).
22. **Test directo de `CredentialCipher`**: rotación, descifrado de legados en texto plano, IV único por mensaje.
23. **Tests frontend (Vitest)**: `BaseInput` (v-model!), `BaseToast`, `BaseModal`, `useAsync`, stores de auth. `PLAN_MEJORA_FRONTEND.MD` §3.1 lo planifica.
24. **Tests de controllers** (capa web): códigos HTTP, roles, seteo de cookies.

### P4 — Mejoras de fondo (no urgentes)

25. **Considerar `SameSite=Strict` para `/auth/refresh`** si la UX lo permite, o header `X-Requested-With`+validación server (defensa en profundidad CSRF).
26. **Considerar bcrypt strength 12** (OWASP 2023+).
27. **Token de reset en fragment `#token=`** en vez de query string (mitiga historial/Referer).
28. **PWA** (manifest + service worker + icons). `PLAN_MEJORA_FRONTEND.MD` §3.2.
29. **SEO**: meta tags dinámicos en `PublicSiteView` y `LandingPageView` vía `@vueuse/head`. `PLAN_MEJORA_FRONTEND.MD` §3.3.
30. **Unificar `Clock`** en todos los servicios (hoy la mitad usa `OffsetDateTime.now()` directo).
31. **Mover estados `String` a `enum`** en `Tenant`/`Pago` para consistencia con `Reserva`/`Bloque`.

---

## §8 Anexos

### 8.1 Migraciones Flyway (V1–V17)

Ubicación: `backend/src/main/resources/db/migration/` (17 archivos). `spring.flyway.enabled: true` (`application.yml:13`), `ddl-auto: validate` (`application.yml:10`) — Hibernate no toca el esquema, lo gobierna Flyway.

| Archivo | Descripción |
|---|---|
| `V1__esquema_inicial.sql` | Esquema base: `tenant`, `usuario`, `refresh_token`, `servicio`, `cliente`, `bloque_disponible`, `reserva`, `pago`. **Índice único parcial** `ux_reserva_bloque_activa` sobre `reserva(bloque_id) WHERE estado IN ('PENDIENTE','COTIZADA','CONFIRMADA')` — barrera BD anti doble-reserva. CHECK `ck_bloque_horas` (`hora_fin > hora_inicio`). |
| `V2__pagos_estado_cotizada_y_ley_21719.sql` | Pago como libro contable: `tipo` (ABONO/DEVOLUCION) con CHECK, `estado` (PENDIENTE/CONFIRMADO/RECHAZADO) con CHECK, `referencia_externa` (unique index parcial para idempotencia de webhooks), `registrado_por`. `reserva.cotizada_en` con backfill. Ley 21.719: `cliente.consentimiento_en`, `ultima_actividad_en`, `anonimizado_en`. |
| `V3__normalizar_telefonos.sql` | Limpia teléfonos históricos: `regexp_replace` a dígitos, prefija `56` a celulares de 9 dígitos. Excluye anonimizados. |
| `V4__reserva_optimistic_locking.sql` | `reserva.version BIGINT NOT NULL DEFAULT 0` para `@Version`. |
| `V5__offboarding_tenant.sql` | `tenant.cerrado_en`; CHECK `ck_tenant_estado IN ('ACTIVO','SUSPENDIDO','CERRADO')`. |
| `V6__anonimizar_comentarios_residuales.sql` | Reemplaza `comentarios` de reservas de clientes ya anonimizados con `[anonimizado]` (corrige residuo Ley 21.719). Incrementa `version` a mano. |
| `V7__password_reset.sql` | Tabla `password_reset_token` (UUID PK, `token_hash` unique, `expira_en`, `usado`). |
| `V8__integracion_mercadopago.sql` | `tenant.mp_access_token`, `reserva.mp_preference_id`, `reserva.mp_init_point`. (Aún en texto plano — V10 lo cifra.) |
| `V9__normalizar_emails_usuario.sql` | `UPDATE usuario SET email = lower(trim(email))`. |
| `V10__cifrar_credenciales_mercadopago.sql` | Amplía `mp_access_token` a VARCHAR(512); añade `mp_webhook_secret VARCHAR(512)`. (El cifrado lo hace la app vía `CredentialCipher`.) |
| `V11__cuenta_cliente.sql` | Tabla `cuenta_cliente` (global, sin tenant) — login de apoderados. |
| `V12__telefono_cuenta_cliente.sql` | `cuenta_cliente.telefono VARCHAR(20)` (nullable). |
| `V13__horario_atencion.sql` | `tenant.intervalo_min` (default 30); tabla `horario_atencion` (CHECK `dia_semana BETWEEN 1 AND 7`, CHECK `hora_cierre > hora_apertura`). **Seed** de horario L-V 09-18, Sáb 10-14 para tenants ACTIVOS. |
| `V14__cita_por_hora.sql` | `reserva.servicio_id` y `bloque_id` pasan a nullable; añade `inicio`, `fin`, `cuenta_cliente_id`. Tabla `reserva_servicio` (snapshot de nombre/precio/duración). Índice parcial `idx_reserva_tenant_inicio`. |
| `V15__cita_concurrencia.sql` | Reemplaza el índice anterior por `idx_reserva_tenant_inicio_fin` para acelerar la consulta de solapamiento. (El advisory lock vive en código, `ReservaRepository.java:45`.) |
| `V16__refresh_token_cliente.sql` | Tabla `refresh_token_cliente` (UUID PK, `cuenta_cliente_id`, `token_hash`, `expira_en`, `revocado`). |
| `V17__reset_token_cliente.sql` | `password_reset_token.cuenta_cliente_id` (nullable); `usuario_id` pasa a nullable; índice parcial. Soporta reset de apoderados. (Sin commitear.) |

**Observaciones:** numeración secuencial correcta, sin huecos. Nombres descriptivos con sufijo semántico. Backfills idempotentes. Uso correcto de CHECK constraints y índices parciales. `V13` hace `INSERT ... SELECT` sobre tenants existentes — migración con datos, asegurar que corre en ventana de bajo tráfico. `V17` sin commitear (ver §6).

### 8.2 Cobertura de tests

**72 métodos `@Test`** en **16 archivos** (11 unitarios `*Test` + 5 de integración `*IT`).

| Archivo | `@Test` | Tipo | Cubre |
|---|---|---|---|
| `application/AuthServiceTest.java` | 12 | Unit (Mockito) | refresh rotación, reuso de revocado, expirados, logout sin principal, login de tenant no activo, reset password (solicitud silenciosa, anti-enumeración, confirmación, token usado/expirado). |
| `application/ReservaServiceTest.java` | 10 | Unit | solicitud pública toma bloque, webhook cita aprobada, conflicto 409, seña>total, aislamiento multi-tenant, confirmar, devolución>pagado, listar sin N+1, realizar, cancelar. |
| `domain/EstadoReservaTest.java` | 6 | Unit (puro) | máquina de estados: flujo feliz, cita por hora, cancelación desde cualquier activo, no saltar estados, finales inmutables, transición inválida lanza. |
| `integration/WebhookMercadoPagoIT.java` | 6 | IT (Testcontainers) | firma inválida 401, topic no-payment 200, tenant sin MP 400, inexistente 400, data.id no numérico 200. |
| `integration/HorarioAtencionIT.java` | 4 | IT | horas libres reflejan horario, día sin horario vacío, duración>franja vacío. |
| `application/ClienteServiceTest.java` | 4 | Unit | anonimiza sin reservas activas, rechaza con activas, idempotente, solo tenant propio. |
| `application/DisponibilidadServiceTest.java` | 4 | Unit (Clock fixed) | genera slots según duración, resta ocupadas, duración 0, fecha pasada. |
| `application/MantenimientoJobsTest.java` | 5 | Unit | purga refresh, anonimiza inactivos, limpia bloques, purga tenants cerrados (verifica orden FK con `inOrder`), idempotencia. |
| `application/CicloReservaJobsTest.java` | 5 | Unit | cancela pendientes, citas sin pago, cotizadas sin respuesta, marca realizadas, sin vencidas no-op. |
| `application/TenantServiceTest.java` | 5 | Unit | cerrar cancela+revoca+export, rechazado con confirmadas, rechazado sin slug, doble cierre, export incluye colecciones. |
| `integration/AgendaConcurrenciaIT.java` | 2 | IT (hilos) | solo una cita gana la misma hora (advisory lock). |
| `integration/ReservaConcurrenciaIT.java` | 2 | IT (10 hilos) | solo una solicitud gana el bloque (UPDATE condicionado + índice único). |
| `integration/ContextoIT.java` | 2 | IT | contexto carga y migraciones corren. |
| `domain/ClienteTest.java` | 3 | Unit (puro) | normalización de teléfonos chilenos. |
| `infrastructure/RateLimitFilterTest.java` | 2 | Unit (MockMvc filters) | bypass con XFF falsificada no evade, IPs distintas usan buckets distintos. |
| `application/ClienteAuthServiceTest.java` | 2 | Unit | login emite tokens, refresh rotado revoca familia. |

**Configuración de build:** `maven-surefire` (`pom.xml:135-141`) excluye `**/*IT.java` (unit con `mvn test`); `maven-failsafe` (`pom.xml:143-160`) incluye `**/*IT.java` (integración con `mvn verify`). Perfil `test` con Testcontainers (`jdbc:tc:postgresql:16-alpine`).

**Cobertura — qué falta:**
- **Sin tests unitarios:** `CalendarioService`, `ServicioService`, `HorarioAtencionService`, `DirectorioService`, `AgendaService` (solo IT de concurrencia, no unit), `CitaTexto`.
- **Sin tests:** `JwtService`, `JwtAuthFilter`, `RefreshCookieService`, `CredentialCipher` (sin test directo del cifrado/descifrado), `SecurityConfig` (sin test de autorización por rol), `NotificacionAdapter`, `MercadoPagoAdapter`, `GlobalExceptionHandler`.
- **Sin tests de controllers** (capa web) salvo los IT que usan MockMvc (`WebhookMercadoPagoIT`). No hay tests de `AuthController`, `ReservaController`, etc. que verifiquen los códigos HTTP, los roles, ni el seteo de cookies.
- **Sin test de seguridad end-to-end:** no se prueba que un token de rol `CLIENTE` no pueda acceder a `/api/reservas` (la regla `hasRole("DUENO")` de `SecurityConfig.java:52` no tiene test).
- **Cero tests frontend.**
- La concurrencia y el webhook (los puntos más riesgosos) **sí están bien cubiertos** con IT reales contra PostgreSQL.

### 8.3 Variables de entorno

Ubicación: `/home/c0cus/Documentos/reserva_kids/.env.example` (83 líneas, placeholders) + `.env` local (no trackeado, `.gitignore:12`).

| Grupo | Variables |
|---|---|
| PostgreSQL | `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` |
| Backend/DB | `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| JWT/Cifrado | `JWT_SECRET` (obligatorio, ≥32 chars), `CRED_ENC_KEY` (vacía = deriva de `JWT_SECRET`), `JWT_ACCESS_MINUTES`, `JWT_REFRESH_DAYS` |
| CORS | `CORS_ALLOWED_ORIGINS` |
| Cookies | `COOKIE_SECURE`, `COOKIE_SAME_SITE` |
| Reset/frontend | `FRONTEND_URL`, `RESET_PASSWORD_MINUTOS`, `API_URL` |
| Email | `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_FROM` |
| Negocio/expiraciones | `RESERVA_EXPIRACION_HORAS`, `COTIZACION_EXPIRACION_DIAS`, `CITA_PAGO_EXPIRACION_MIN`, `WHATSAPP_ENABLED`, `RETENCION_CLIENTE_MESES`, `TENANT_PURGA_DIAS`, `RCLONE_REMOTE` (opcional) |
| Infra | `APP_TIMEZONE`, `TRUST_PROXY` |
| **Frontend (única que llega al bundle)** | `VITE_API_URL` |

**Observaciones de seguridad:**
- `.env` fuera de git; `.env.example` solo con placeholders. [BUENO].
- `VITE_API_URL` es la **única** variable del frontend. Cualquier `VITE_*` se inyecta al bundle y es **pública** — no poner secretos con prefijo `VITE_`. Actualmente correcto.
- `docker-compose.yml:8,40,41` usa `${POSTGRES_PASSWORD:?...}` y `${JWT_SECRET:?...}` (fail-fast si faltan).
- `COOKIE_SECURE=false` en `.env.example:27` (dev HTTP) pero `application.yml:56` defaultea a `true` — coherente: dev lo overridea a false, prod usa true.

### 8.4 Stack y dependencias clave

**Backend** (`backend/pom.xml`): Java 21, Spring Boot 3.5.6, Spring Security, jjwt 0.12.6, Spring Data JPA + Flyway, PostgreSQL driver, Mercado Pago SDK 2.1.26, spring-boot-starter-mail, spring-boot-starter-actuator, Lombok, Testcontainers + Mockito + JUnit 5.

**Frontend** (`frontend/package.json`): Vue 3.5.13, Vue Router 4.5.0, Pinia 2.3.0, Axios 1.7.9, Vite 6.0.7, Tailwind CSS 4.0.0 (vía `@tailwindcss/vite`), `@vitejs/plugin-vue` 5.2.1.

**CI** (`.github/workflows/ci.yml`): jobs paralelos `backend` (setup-java 21 + `mvn test` + `package`) y `frontend` (`npm ci` + `vite build`, Node 22) en push a `main` y PRs. Dependabot semanal (npm/maven) y mensual (actions/docker).

---

*Fin del documento. Esta auditoría es de solo lectura: no se creó, modificó ni eliminó ningún archivo de código durante su elaboración.*
