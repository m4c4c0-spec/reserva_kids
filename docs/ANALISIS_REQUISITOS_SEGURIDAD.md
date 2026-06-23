# Análisis de Requisitos de Seguridad — ReservaKids

**Fecha:** 2026-06-22
**Alcance:** (1) Aplicar el principio *evitar la seguridad por oscuridad*; (2) análisis
de requisitos de seguridad (roles/permisos, enfoque y herramientas de pruebas, impacto
de las funciones de usuario en front/back); (3) revisión de los cambios recientes hechos
en paralelo (refactor de seguridad/observabilidad).
**Método:** cada afirmación se verifica contra el código fuente, citando `archivo:línea`.

---

## 1. Principio: evitar la seguridad por oscuridad

> *La seguridad de una aplicación no debe depender del secreto de su diseño o
> implementación.* La oscuridad es un control débil — sobre todo si es el único.

**Veredicto: ReservaKids NO depende de la oscuridad.** Su seguridad descansa en
controles reales y verificables; el código podría ser open source (como Linux, el
ejemplo citado) sin que ello debilite la protección. Evidencia:

| Lo que NO hace (anti-oscuridad) | Evidencia |
|---|---|
| No esconde secretos en el código ni en nombres/rutas engañosas | Barrido de `src/main/java`: **0 secretos hardcodeados**; todos vienen de variables de entorno (`@Value("${app.jwt.secret}")` `JwtService:28`, DB/`CRED_ENC_KEY` vía `.env`) |
| No confía en que el atacante no conozca los endpoints | Autorización explícita por ruta y rol; lo no declarado se deniega (`SecurityConfig:68` `anyRequest().denyAll()`) |
| No usa "rutas ocultas" como control de acceso | Toda ruta protegida exige rol verificado por firma JWT (`hasRole` `:61-67`) |
| No oculta el motivo como única defensa de auth | Mensaje genérico al usuario **+** detalle real solo en log interno (`GlobalExceptionHandler` `credenciales()`) — esto es anti-enumeración, no oscuridad |

**Dónde sí se mantienen secretos (correcto):** el secreto está en las **claves**, no
en el diseño. `JWT_SECRET` (firma HS256, validado ≥32 chars `JwtService:32`), contraseñas
con **bcrypt ≥12 rounds** (`SecurityConfig:38`), y credenciales de Mercado Pago cifradas
en reposo con **AES-256-GCM** e IV aleatorio (`CredentialCipher`). Mantener *claves*
secretas es correcto; mantener el *diseño* secreto sería el error — y no ocurre.

**Los controles reales que sostienen la seguridad** (no la oscuridad):
- **Arquitectura/RBAC:** 3 roles con firma criptográfica (§2).
- **Comunicaciones:** CORS restrictivo a un origen (`SecurityConfig:78-89`), cookies
  `HttpOnly+Secure+SameSite` para el refresh (`RefreshCookieService`), headers de
  seguridad (`SecurityHeadersFilter`), CSP (`index.html`).
- **Políticas de contraseña/sesión:** bcrypt 12, access 15 min, refresh 7 días con
  rotación y revocación de familia ante reúso.
- **Auditoría periódica:** bitácora append-only `audit_event` (RNF-07) consultable.

---

## 2. Análisis de requisitos de seguridad

### 2.1 Roles, límites y permisos (app + backend)

Tres roles, aislados por firma JWT (`rol` claim → `ROLE_<rol>` en `JwtAuthFilter:33`)
y autorizados server-side por ruta (`SecurityConfig:56-68`). El frontend **replica** la
segregación como defensa en profundidad, pero **el backend es el control real**.

| Rol | Identidad en el token | Puede (backend) | NO puede / límites |
|---|---|---|---|
| **DUENO** | `sub`=usuarioId, `tenantId` presente, `rol=DUENO` | Gestionar SOLO su negocio: reservas, servicios, calendario, config, su bitácora (`/api/**`, `hasRole("DUENO")` `:67`) | Tocar otro tenant (toda query filtra por `tenantId` del token); entrar a `/api/admin/**` ni `/api/cliente/**` |
| **CLIENTE** | `sub`=cuentaClienteId, **sin** `tenantId`, `rol=CLIENTE` | Ver directorio, agendar, ver sus reservas (`/api/cliente/**`, `hasRole("CLIENTE")` `:63`) | Tocar `/api/**` de negocio (antes un token cliente llegaba con `tenantId=null` → ahora **denegado por rol**, ver §3) |
| **ADMIN** | `sub`=adminId, **sin** `tenantId`, `rol=ADMIN` | Gobierno cross-tenant: suspender/reactivar negocios, métricas, auditoría de plataforma (`/api/admin/**` `:61`) | Sin auto-registro (`ADMIN_BOOTSTRAP_*` por env); no opera como dueño/cliente |
| **Anónimo (público)** | sin token | Catálogo, disponibilidad, crear solicitud, directorio público (`/api/public/**` permitAll `:59`) | Cualquier acción autenticada; el `denyAll` final cierra lo no declarado |

**Invariantes de aislamiento (no dependen de la UI):**
- Multi-tenancy: el `tenantId` sale **del token firmado**, nunca del request → un dueño
  no puede leer/escribir datos de otro negocio aunque manipule el body.
- Segregación de rol en BD: `audit_event`/reservas/etc. siempre acotadas por `tenantId`.

### 2.2 Enfoque y herramientas de pruebas de seguridad

Para alcanzar un buen nivel dado el stack (Java 21/Spring Boot 3 + Vue 3):

| Tipo | Qué busca | Herramientas sugeridas | Estado actual |
|---|---|---|---|
| **SAST** | Bugs/inyección/secretos en código | SonarQube (plugin disponible), Semgrep, SpotBugs + **FindSecBugs** | Parcial (Sonar disponible) |
| **Dependencias (SCA)** | CVEs en libs | OWASP Dependency-Check, Dependabot, `npm audit` | ✅ `npm audit` en CI; falta backend SCA |
| **Secrets scanning** | Claves filtradas en git | gitleaks / trufflehog (pre-commit + CI) | ⚠️ recomendado añadir |
| **DAST** | Vulns en runtime (XSS, headers, auth) | OWASP ZAP contra la app levantada | ⚠️ recomendado |
| **Authz / IDOR-BOLA** | Acceso cross-tenant / cross-rol | Tests dirigidos (intentar leer otro `tenantId`, usar token cliente en `/api/**`) | ✅ base en los `*IT` multi-tenant; ampliar casos negativos |
| **Pruebas de concurrencia** | Doble-reserva / TOCTOU | Los `*IT` de Testcontainers (RNF-05) | ✅ existen |
| **Checklist manual** | Cobertura sistemática | OWASP **ASVS** L1/L2, OWASP **Top 10** | ⚠️ formalizar |
| **Auditoría periódica** | No-repudio, detección | Revisar `audit_event` + pentest puntual | ✅ bitácora lista; falta cadencia |

> Prioridad barata/alta: (1) gitleaks en CI, (2) OWASP Dependency-Check del backend,
> (3) un job de ZAP baseline contra el contenedor.

### 2.3 Impacto de las funciones de usuario en la seguridad (front vs back)

Principio operativo: **toda función de usuario se valida en el backend; el frontend solo
mejora la UX.** Si una verificación vive *solo* en el front, no es un control de seguridad.

| Función de usuario | Riesgo si solo se controla en front | Control real (backend) |
|---|---|---|
| Login / sesión | Robo de token, escalada | Firma JWT + exp/iss/aud (`JwtService.validar`), refresh HttpOnly con rotación |
| Crear/gestionar reserva | Manipular precio/tenant/estado | `tenantId` del token; máquina de estados pura; índice anti doble-reserva en BD |
| Cotizar / registrar seña | Montos/devoluciones inválidas | Validaciones de dominio (`@Valid`, reglas de negocio), idempotencia de pago |
| Reserva pública (sin login) | Inyección, spam, XSS almacenado | `HtmlSanitizer` (server-side) + rate limiting por ruta + `@Valid` |
| Navegar entre roles | Ver pantallas/datos de otro rol | `SecurityConfig` por rol; el front (`router.beforeEach`) solo evita el 403 visible |
| Ver bitácora / auditoría | Leer auditoría de otro tenant | `SistemaController.auditoria` acota a `principal.tenantId()` + límite 1–200 |

---

## 3. Revisión de los cambios recientes (refactor en paralelo)

Todos los cambios revisados **mejoran** la postura de seguridad y, de hecho, son ejemplos
de los principios de §1 (controles reales, no oscuridad). Resumen con veredicto:

| Cambio | Qué hace | Veredicto |
|---|---|---|
| **RBAC por rol** (`SecurityConfig:64-67`) | `/api/**` pasó de `authenticated()` a `hasRole("DUENO")` | ✅ **Corrige escalada**: un token CLIENTE ya no alcanza endpoints de negocio. Control real server-side |
| **Segregación cross-rol en el router** (`router/index.js:90-95`) | Un rol no navega rutas de otro aunque tenga sesión | ✅ Defensa en profundidad; el comentario reconoce *"el backend ya lo rechaza"* → NO se apoya en el front como control (anti-oscuridad correcto) |
| **Anti-enumeración en auth** (`GlobalExceptionHandler.credenciales`) | Mensaje genérico "Credenciales inválidas" + detalle solo en log | ✅ Evita filtrar si el email existe / tenant suspendido / token reusado |
| **Bitácora `AuditPort`/`AuthEventPort` + `audit_event`** | Traza append-only de QUIÉN/QUÉ/CUÁNDO (RNF-07) | ✅ Habilita la *auditoría periódica* que pide el principio. Append-only (solo INSERT) |
| **`/auditoria` del dueño** (`SistemaController`) | DUENO lee su propia bitácora | ✅ Acotado a su `tenantId` + límite 1–200 (sin enumeración ni DoS por `limite` enorme) |
| **`MdcFilter`** (X-Request-ID) | Correlation ID en logs | ✅ Trazabilidad/forense; no expone datos sensibles |
| **`SecurityHeadersFilter`** | nosniff, X-Frame-Options DENY, Referrer-Policy | ✅ Defensa en profundidad aun sin el proxy Caddy |
| **`HtmlSanitizer`** (Jsoup `Safelist.none()`) | Limpia HTML antes de persistir | ✅ Anti-XSS almacenado server-side (no confía en el front) |
| **CSP** (`index.html`) | Restringe orígenes de script/estilo/fuente | ✅ Capa anti-XSS; `script-src 'self'` (sin `unsafe-inline`) |
| **Rate limiting DB-backed** (`RateLimitFilter`) | Conteo in-memory + persistencia entre reinicios | ✅ Mitiga fuerza bruta/abuso; ⚠️ ver hallazgo H2 |

### Hallazgos honestos y su resolución

| # | Sev. | Hallazgo | Estado |
|---|---|---|---|
| **H2** | 🟡 Media | Rate limit contaba **in-memory por instancia** (límite efectivo N×configurado con varias réplicas) | ✅ **Resuelto.** Contador **atómico en BD** (`INSERT … ON CONFLICT … RETURNING`, `RateLimitBucketRepositoryImpl`), autoritativo y compartido entre instancias; *fail-open* si la BD cae. Verificado contra Postgres real: 30×200 + 429 exacto |
| **H4** | 🟢 Baja | JWT HS256 sin mecanismo de rotación de clave | ✅ **Resuelto.** `JwtService` valida con clave primaria y, ante fallo de **firma**, con `app.jwt.secret-previous` (opcional) → rota `JWT_SECRET` sin invalidar tokens vivos. Firma siempre con la primaria |
| **H5** | 🟢 Baja | Faltaba SCA de backend, secrets-scanning y DAST en CI | ✅ **Parcial.** Añadido job `seguridad` en CI: **gitleaks** (secretos, incl. historial) + **Trivy** (deps vulnerables + secretos, gate CRITICAL). DAST/ZAP queda como mejora futura (requiere app levantada en CI) |
| **H1** | 🟢 Baja | CSP `style-src 'unsafe-inline'` | 🟡 **Aceptado (trade-off).** Lo exigen el HMR de Vite (dev) y los `:style` de Vue. Lo importante (`script-src 'self'`, sin inline de script) ya está. Endurecer rompería dev; el Caddy de prod puede aplicar una CSP más estricta |
| **H3** | 🟢 Baja | `/api/public/negocios` expone slug+nombre sin auth | 🟢 **Aceptado (por diseño).** Es el catálogo público; DTO mínimo (`NegocioResumen` = slug + nombre), sin datos sensibles |

---

## 4. Conclusión

ReservaKids **cumple el principio de no depender de la seguridad por oscuridad**: su
protección está en controles verificables (RBAC firmado, aislamiento multi-tenant desde
el token, bcrypt, cifrado en reposo, CSP/headers/CORS, sanitización, rate limiting y
bitácora), no en ocultar el diseño o el código. Los cambios recientes **refuerzan** esa
postura (corrigen una escalada de rol, añaden no-repudio y anti-enumeración). Lo pendiente
es **proceso de pruebas** (SCA/DAST/secrets en CI, checklist ASVS y cadencia de auditoría),
no rediseño.

---

*Relacionado: [`ANALISIS_COMERCIAL.md`](ANALISIS_COMERCIAL.md),
[`VALIDACION_UX.md`](VALIDACION_UX.md), `REVISION_ARQUITECTURA.md`.*
