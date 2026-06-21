# Análisis de Seguridad, UX y Despliegue — ReservaKids

**Fecha:** 2026-06-20  
**Alcance:** Revisión de vulnerabilidades (inyecciones, autenticación, autorización), evaluación de flujo de usuario, y opciones de despliegue.

---

## 1. ANÁLISIS DE SEGURIDAD

### 1.1 Inyección SQL — ✅ RIESGO BAJO

**Veredicto: Protegido.** El proyecto usa exclusivamente JPA/Hibernate con queries parametrizadas.

| Evidencia | Detalle |
|---|---|
| Spring Data JPA | Todos los repositorios extienden `JpaRepository` — los métodos `findByX` generan queries parametrizadas automáticamente |
| `@Query` JPQL | Las 29 queries con `@Query` usan JPQL con parámetros nombrados (`:tenantId`, `:fecha`, `:estado`) — **no hay concatenación de strings** |
| `nativeQuery` | Solo 1 query nativa: `SELECT pg_advisory_xact_lock(...)` en `ReservaRepository:48` — los parámetros se castean con `cast(:tenantId AS text)`, que es seguro |
| `GlobalExceptionHandler` | Captura `DataIntegrityViolationException` → 409 sin exponer stacktrace |

**No se encontró ningún caso de concatenación de input del usuario en queries SQL.**

### 1.2 Cross-Site Scripting (XSS) — ⚠️ RIESGO BAJO-MEDIO

**Veredicto: Vue 3 auto-escapa por defecto, pero hay vectores residuales.**

#### ✅ Protegido
- **No se encontró `v-html`, `innerHTML` ni `dangerouslySetInnerHTML`** en ningún componente `.vue`
- Vue 3 escapa automáticamente todo contenido interpolado con `{{ }}` y `v-bind`
- Los datos del backend (nombre de negocio, comentarios, nombre de cliente) se renderizan con interpolación segura:
  ```vue
  {{ negocio.nombre }}      <!-- escapado automático -->
  {{ r.comentarios }}       <!-- escapado automático -->
  ```
- **CSP header configurado** en el Caddyfile de producción: `script-src 'self'` — bloquea scripts inline de terceros

#### ⚠️ Vectores residuales identificados

| # | Vector | Archivo | Severidad | Detalle |
|---|---|---|---|---|
| 1 | **Email HTML sin sanitizar** | `NotificacionAdapter.java:220-238` | Baja | Los comentarios del usuario viajan tal cual en el cuerpo del email al dueño. Si un atacante envía `<script>` en `comentarios`, el email lo contendrá. La mayoría de clientes de email modernos no ejecutan JS, pero algunos clientes web antiguos podrían interpretar HTML malicioso |
| 2 | **WhatsApp link con datos inyectados** | `NotificacionAdapter.java:295-302` | Baja | El nombre del cliente se inserta en el mensaje de WhatsApp: `"Hola %s! Te escribo..."`. Si alguien registra un nombre con caracteres especiales, el link `wa.me/...?text=` podría malformarse. `URLEncoder.encode` mitiga esto parcialmente |
| 3 | **Reset token en query string** | `NotificacionAdapter.java:134,169` | Baja | El token de reset viaja como `?token=...` en el email. Esto aparece en logs del proxy, historial del navegador y referrer headers. Ya está mitigado con `Referrer-Policy: no-referrer` en el Caddyfile |
| 4 | **Mensajes de error exponiendo datos** | `GlobalExceptionHandler.java:64` | Baja | `"Parámetro o la petición inválido"` es genérico ✅, pero `BadCredentialsException` devuelve `e.getMessage()` directamente (línea 85) — si Spring genera un mensaje con email del usuario, se expone en la respuesta JSON |

### 1.3 CSRF (Cross-Site Request Forgery) — ✅ RIESGO BAJO

**Veredicto: Protegido por diseño.**

| Mecanismo | Estado |
|---|---|
| CSRF deshabilitado explícitamente | `SecurityConfig.java:48` — `csrf.disable()` |
| Justificación válida | Sesión STATELESS + autenticación por Bearer token en `Authorization` header — un sitio de terceros no puede adjuntar este header |
| Refresh token en cookie | HttpOnly + SameSite=Lax — el navegador no la envía cross-site |
| Endpoints JSON | `Content-Type: application/json` exige preflight CORS, que el CORS restrictivo rechaza |
| **Sin formularios HTML** | Toda la comunicación es JSON vía Axios — no hay formularios `<form>` que un atacante pueda falsificar |

### 1.4 Autenticación — ✅ SÓLIDA

| Aspecto | Implementación | Evaluación |
|---|---|---|
| Password hashing | bcrypt strength 10 (`SecurityConfig.java:33`) | ✅ Adecuado |
| JWT firmado | HS256 con secret ≥32 chars (`JwtService.java:32-33`) | ✅ Bien |
| Access token TTL | 15 minutos | ✅ Corto, reduce ventana de exposición |
| Refresh token | 7 días con rotación + revocación en BD | ✅ Anti-robo implementado |
| Detección de reuso | Token reusado → revoca todas las sesiones del usuario | ✅ Excelente |
| Refresh en cookie | HttpOnly + SameSite=Lax | ✅ XSS no puede leerlo |
| Anti-enumeración | Reset de contraseña → 204 siempre | ✅ Correcto |
| ClassCastException en JWT | Lectura tolerante con `(Number) claims.get("tenantId")` (`JwtService.java:100-103`) | ✅ Corregido |
| Logout con access vencido | Acepta refresh por cookie incluso sin JWT válido (`AuthController.java:66-74`) | ✅ Corregido |

### 1.5 Autorización — ✅ SÓLIDA

| Aspecto | Implementación | Evaluación |
|---|---|---|
| Aislamiento multi-tenant | Toda query filtrada por `tenant_id` | ✅ Estricto |
| Roles separados | `DUENO` (panel), `CLIENTE` (directorio), `ADMIN` (consola) | ✅ RBAC |
| Defense-in-depth | `findByIdAndTenantId()` incluso cuando el ID ya viene de una query tenant-scoped (`ReservaService.java:329,358`) | ✅ Doble verificación |
| Tenant estado | Login/refresh bloqueados si tenant no está ACTIVO | ✅ Correcto |
| Endpoints protegidos | `/api/**` → `hasRole("DUENO")`, `/api/cliente/**` → `hasRole("CLIENTE")`, `/api/admin/**` → `hasRole("ADMIN")` | ✅ Correcto |

### 1.6 Rate Limiting — ⚠️ ACEPTABLE PARA MVP

| Aspecto | Detalle |
|---|---|
| Implementación | Bucket en memoria por IP, 30 req/minuto |
| Cobertura | Solo `/api/public/**`, `/api/auth/**`, `/api/cliente-auth/**`, `/api/admin-auth/**` |
| Proxy | Lee última IP de `X-Forwarded-For` cuando `TRUST_PROXY=true` |
| **Limitación 1** | No sobrevive reinicios (en memoria) |
| **Limitación 2** | No funciona con 2+ instancias (cada una tiene su bucket) |
| **Limitación 3** | No aplica a endpoints autenticados del panel (`/api/reservas/**`, `/api/servicios/**`, etc.) — un DUENO autenticado puede hacer requests ilimitados |
| **Limitación 4** | UUID como IP: si un usuario rota su IP (VPN/Tor), obtiene un bucket nuevo |

### 1.7 Otras vulnerabilidades potenciales

| # | Tipo | Severidad | Detalle | Ubicación |
|---|---|---|---|---|
| 1 | **IDOR en webhook MP** | 🟡 Media | El endpoint `/api/public/webhooks/mercadopago/{tenantId}` acepta cualquier `tenantId` en la URL. Un atacante podría enviar webhooks falsos a tenants arbitrarios. Mitigado por: (a) validación de firma `x-signature` (si está configurada), (b) re-consulta del pago real a la API de MP. Sin firma, un atacante podría forzar consultas a la API de MP contra un tenant | `WebhookController.java:38` |
| 2 | **Información expuesta en emails** | 🟡 Media | El email de nueva solicitud incluye nombre, teléfono, email, comuna y comentarios del cliente completo. Si el email del dueño es comprometido, todos los datos de clientes quedan expuestos | `NotificacionAdapter.java:220-238` |
| 3 | **No hay validación de tamaño de payload** | 🟡 Baja | Spring Boot tiene un default de 2MB, pero no hay configuración explícita. Un atacante podría enviar payloads grandes a endpoints públicos | Global |
| 4 | **Slugs reservados** | ✅ Corregido | `SLUGS_RESERVADOS` validado en `AuthService.registrar()` | `AuthService.java` |
| 5 | **CORS con allowCredentials** | 🟢 Info | `config.setAllowCredentials(true)` con orígenes específicos — correcto, pero asegura que `allowedOrigins` nunca sea `*` en producción | `SecurityConfig.java:78` |

---

## 2. EVALUACIÓN DE FLUJO DE USUARIO (UX)

### 2.1 Flujo Público — Crear Solicitud de Cumpleaños

```
Landing → /{slug} → Elegir servicio → Elegir fecha/hora → Formulario → Enviar → Éxito
```

| Aspecto | Evaluación | Detalle |
|---|---|---|
| **Claridad** | ✅ Buena | 3 pasos numerados visualmente (1-2-3), cada sección con título descriptivo |
| **Progreso** | ✅ Visible | El usuario ve qué paso está completando |
| **Feedback** | ✅ Bueno | LoadingSpinner durante carga, ErrorBanner para errores, pantalla de éxito con número de reserva |
| **Accesibilidad** | ⚠️ Parcial | Labels en inputs ✅, pero los iconos de Material Symbols tienen `aria-hidden="true"` sin alternativa textual para el contenido visual |
| **Formulario** | ✅ Bien diseñado | Checkbox de consentimiento obligatorio, campos con maxlength, email con type="email" |
| **Auto-rellenado** | ✅ Bueno | Si el cliente está autenticado, pre-rellena nombre y email |
| **Conflicto de bloque** | ✅ Bueno | Si alguien más tomó el bloque (409), recarga la disponibilidad y muestra error claro |

### 2.2 Flujo de Agendamiento por Hora (Cliente Autenticado)

```
Directorio → /clientes/agendar/{slug} → Servicios → Día/Hora → Pago → Mercado Pago
```

| Aspecto | Evaluación | Detalle |
|---|---|---|
| **Progreso** | ✅ Excelente | Indicador de 3 pasos con numeración visual |
| **Resumen persistente** | ✅ Excelente | Barra inferior fija con total CLP y duración total |
| **Selección múltiple** | ✅ Bueno | Puede combinar servicios, suma precio y duración automáticamente |
| **Horas inteligentes** | ✅ Excelente | Solo muestra horas que calzan con la duración total necesaria |
| **Pago** | ✅ Claro | Resumen completo antes de pagar, botón con candado y monto |
| **Conflicto de hora** | ✅ Bueno | Si la hora ya fue tomada (409), vuelve al paso 2 y recarga |
| **Retroceso** | ✅ Bueno | Botón "Atrás" en cada paso |
| **Sin validación de teléfono** | ⚠️ | No hay validación de formato de teléfono en el registro de cliente — el usuario puede ingresar un teléfono inválido |

### 2.3 Panel del Negocio (Dueño)

```
Login → /panel/solicitudes → Ver/Cotizar/Confirmar/Realizar/Cancelar
```

| Aspecto | Evaluación | Detalle |
|---|---|---|
| **Listado** | ✅ Bueno | Paginación, filtro por estado, tarjetas con información clara |
| **Acciones contextuales** | ✅ Excelente | Los botones aparecen solo según el estado de la reserva |
| **Cotización** | ⚠️ Confuso | El formulario de cotización aparece inline al hacer clic en "Cotizar", pero no hay indicación visual de que se abrió — podría pasar desapercibido |
| **Cancelar** | ✅ Bueno | Modal de confirmación con campo de motivo |
| **WhatsApp** | ✅ Bueno | Link directo pre-armado con mensaje contextual |
| **Link de pago** | ✅ Bueno | Se muestra cuando la reserva está COTIZADA y tiene mpInitPoint |
| **Sin búsqueda** | ⚠️ | No hay búsqueda por nombre de cliente o número de reserva — con muchas solicitudes, encontrar una específica es tedioso |
| **Sin notificaciones en tiempo real** | ⚠️ | El panel no refresca automáticamente — hay que recargar manualmente para ver nuevas solicitudes |

### 2.4 Problemas UX Identificados

| # | Problema | Severidad | Impacto | Recomendación |
|---|---|---|---|---|
| 1 | **No hay confirmación antes de acciones destructivas** (cancelar reserva) | 🟡 Media | El modal existe ✅, pero "Confirmar" y "Realizar" no tienen confirmación — un clic accidental marca una reserva como realizada sin chance de deshacer | Agregar modal de confirmación para "Confirmar" y "Realizar" |
| 2 | **Sin indicador de carga en acciones individuales** | 🟡 Media | Al cotizar, confirmar o cancelar, no hay spinner en el botón específico — el usuario no sabe si la acción se está procesando | Agregar estado de carga por acción |
| 3 | **Mensajes de error genéricos** | 🟡 Media | "No se pudo enviar la solicitud" no indica si fue error de red, bloque tomado, o validación | Mostrar el mensaje específico del backend |
| 4 | **Sin toast de éxito** | 🟢 Baja | Tras cotizar/confirmar/cancelar, no hay feedback visual de éxito — solo se recarga la lista | Agregar toast/notification de éxito |
| 5 | **Formulario de cotización inline** | 🟢 Baja | Aparece sin transición ni highlight — fácil de pasar por alto | Agregar animación o fondo destacado |
| 6 | **Sin validación de senia ≤ total** | 🟢 Baja | El frontend no valida que la seña no supere el total — el backend sí lo hace, pero el usuario recibe el error después de enviar | Validar en el frontend antes de enviar |
| 7 | **No hay preview del mensaje de WhatsApp** | 🟢 Baja | El dueño no sabe qué mensaje se enviará hasta que hace clic | Mostrar preview del mensaje |
| 8 | **Sin dark mode** | 🟢 Baja | El panel es brillante — uso nocturno puede ser incómodo | Considerar dark mode para v1.1 |

---

## 3. OPCIONES DE DESPLIEGUE

### 3.1 Opción A: VPS + Docker Compose + Caddy (Recomendada)

**Costo:** ~USD 5-10/mes (VPS pequeño)

```
┌─────────────────────────────────────────┐
│              VPS (Ubuntu 24.04)          │
│                                          │
│  ┌──────────┐    ┌──────────────────┐   │
│  │  Caddy   │───▶│  docker compose   │   │
│  │  (TLS)   │    │                   │   │
│  │          │    │  ┌─────────────┐  │   │
│  │ :80/:443 │    │  │  Backend    │  │   │
│  └──────────┘    │  │  (:8080)    │  │   │
│                  │  └──────┬──────┘  │   │
│                  │         │         │   │
│                  │  ┌──────▼──────┐  │   │
│                  │  │  Postgres   │  │   │
│                  │  │  (:5432)    │  │   │
│                  │  └─────────────┘  │   │
│                  └──────────────────┘   │
│                                          │
│  Frontend: estático servido por Caddy    │
│  Backups: pg_dump + rclone → B2/S3       │
└─────────────────────────────────────────┘
```

**Pros:**
- Control total del stack
- TLS automático con Caddy
- Backups completos con pg_dump
- Un solo servidor = sin complejidad distribuida
- Manual de despliegue ya escrito (`docs/MANUAL_DESPLIEGUE.md`)

**Contras:**
- Requiere administrar un servidor (updates, seguridad)
- Escalar horizontalmente requiere re-arquitectura (rate limit en memoria, @Scheduled duplicado)
- Un solo punto de fallo

**Pasos:** Ver `docs/MANUAL_DESPLIEGUE.md` §0-§10

### 3.2 Opción B: Vercel (Frontend) + Railway/Render (Backend + DB)

**Costo:** ~USD 10-20/mes

```
┌──────────────┐      ┌──────────────────┐
│   Vercel     │      │   Railway/Render  │
│              │      │                   │
│  Frontend    │─────▶│  Backend (Docker) │
│  (estático)  │ HTTPS│                   │
│              │      │  ┌─────────────┐  │
└──────────────┘      │  │  Postgres   │  │
                      │  │  (plugin)   │  │
                      │  └─────────────┘  │
                      └──────────────────┘
```

**Pros:**
- Sin administración de servidor
- Deploy automático desde git push
- TLS, CDN, escalado automático del frontend
- Railway maneja backups de Postgres

**Contras:**
- Más caro (~USD 10-20 vs 5-10)
- CORS entre orígenes distintos (requiere configuración adicional)
- Los jobs `@Scheduled` requieren que la instancia no "duerma" (plan serverless escala a cero)
- Rate limit en memoria asume 1 sola instancia
- Menos control sobre la infraestructura

**Configuración adicional necesaria:**
- `CORS_ALLOWED_ORIGINS=https://<app>.vercel.app`
- `TRUST_PROXY=true` (Railway pone proxy delante)
- `VITE_API_URL=https://<api>.railway.app`

### 3.3 Opción C: Google Cloud Run + Neon Postgres

**Costo:** ~USD 5-15/mes (pay-per-use)

```
┌──────────────────┐      ┌──────────────────┐
│  Cloud Run       │      │  Neon Postgres    │
│                  │      │                   │
│  Backend (Docker)│─────▶│  Serverless DB    │
│  (auto-scaling)  │      │  (branching)      │
└────────┬─────────┘      └──────────────────┘
         │
┌────────▼─────────┐
│  Vercel / Cloud  │
│  Storage (front) │
└──────────────────┘
```

**Pros:**
- Escalado automático (incluye a 0 cuando no hay tráfico)
- Neon ofrece branching de BD (útil para testing)
- Pay-per-use (solo pagas cuando hay requests)
- Template de deploy ya existe (`backend/deploy/cloudrun.env.example.yaml`)

**Contras:**
- **Los jobs `@Scheduled` NO funcionan bien con scale-to-zero** — Cloud Run necesita un scheduler externo (Cloud Scheduler) para despertar la instancia
- Cold starts (~1-3 segundos) en la primera request tras inactividad
- Neon tiene scale-to-zero que también afecta a los jobs
- Complejidad de configuración mayor

### 3.4 Opción D: VPS tradicional sin Docker

**Costo:** ~USD 5/mes

```
┌─────────────────────────────────────────┐
│              VPS                         │
│                                          │
│  Caddy ──▶ Java JAR (systemd)            │
│            Postgres (apt)                │
│            Frontend (estático)           │
└─────────────────────────────────────────┘
```

**Pros:**
- Más barato (sin overhead de Docker)
- Menos capas de abstracción
- Fácil de debuggear

**Contras:**
- Sin aislamiento de contenedores
- Deploy manual (copiar JAR, reiniciar servicio)
- Sin healthcheck automático del backend
- Migraciones de Flyway requieren gestión manual
- **No recomendado** para este proyecto

### 3.5 Comparación de opciones

| Criterio | A: VPS+Docker | B: Vercel+Railway | C: Cloud Run+Neon | D: VPS sin Docker |
|---|---|---|---|---|
| **Costo mensual** | $5-10 | $10-20 | $5-15 | $5 |
| **Complejidad** | Media | Baja | Alta | Media |
| **Administración** | Media | Mínima | Media-Alta | Media-Alta |
| **Escalabilidad** | Baja | Media | Alta | Baja |
| **Backups** | Manual (script) | Automático (Railway) | Automático (Neon) | Manual |
| **TLS** | Automático (Caddy) | Automático | Automático | Manual (Caddy) |
| **Jobs @Scheduled** | ✅ Funcionan | ⚠️ Sin sleep | ⚠️ Necesita scheduler | ✅ Funcionan |
| **CI/CD** | Manual (git pull) | Automático (git push) | Automático (Cloud Build) | Manual |
| **Recomendado para** | **MVP / Piloto** | Equipo pequeño | Escala media | No recomendado |

### 3.6 Recomendación

**Para el estado actual del proyecto (MVP, bus factor 1, sin tráfico masivo):**

> **Opción A: VPS + Docker Compose + Caddy**

Razones:
1. El manual de despliegue ya está escrito y probado
2. Los jobs `@Scheduled` funcionan sin configuración adicional
3. Un solo servidor = un solo punto de administración
4. Costo mínimo (~USD 5-10/mes)
5. Backups offsite ya implementados (`backup_db.sh` + rclone)
6. PWA funciona correctamente con HTTPS de Caddy

**Cuando escalar a Opción B o C:**
- Cuando haya 10+ tenants activos
- Cuando el tráfico justifique 2+ instancias
- Cuando se resuelvan las deudas de escalado (bucket4j+Redis, ShedLock)

---

## 4. RESUMEN DE HALLAZGOS

### Seguridad

| Categoría | Estado | Riesgo |
|---|---|---|
| Inyección SQL | ✅ Protegido | Bajo |
| XSS | ✅ Protegido (auto-escape Vue 3) | Bajo |
| CSRF | ✅ Protegido por diseño | Bajo |
| Autenticación | ✅ Sólida | Bajo |
| Autorización | ✅ Multi-tenant estricto | Bajo |
| Rate Limiting | ⚠️ Aceptable para MVP | Medio (si escala) |
| Webhook MP | ⚠️ IDOR mitigado | Medio |
| Datos en emails | ⚠️ Información completa | Medio |

### UX

| Área | Evaluación |
|---|---|
| Flujo público de reserva | ✅ Bueno — claro, progresivo, con feedback |
| Agendamiento por hora | ✅ Excelente — pasos claros, resumen persistente |
| Panel del negocio | ✅ Bueno — acciones contextuales, pero sin búsqueda ni refresh automático |
| Accesibilidad | ⚠️ Parcial — labels presentes, pero falta ARIA en algunos elementos |
| Feedback de errores | ⚠️ Genérico — mensajes del backend no siempre se propagan |

### Despliegue

| Opción | Recomendación |
|---|---|
| VPS + Docker + Caddy | ✅ **Recomendada para MVP** |
| Vercel + Railway | ⚠️ Buena alternativa si no se quiere administrar servidor |
| Cloud Run + Neon | ⚠️ Para cuando se necesite escalado automático |
| VPS sin Docker | ❌ No recomendado |
