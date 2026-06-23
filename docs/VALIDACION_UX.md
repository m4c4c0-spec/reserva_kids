# Validación UX — A1 / A3 / A4 + testeo de flujo

**Fecha:** 2026-06-22
**Alcance:** Validar contra el código real los supuestos A1, A3 y A4 de
[`PERSONAS_UX.md`](PERSONAS_UX.md), testear el flujo end-to-end levantando la app,
y mejorar el selector dueño/cliente de la landing.

> ⚠️ **Método y su límite.** Esto **no** es validación con usuarios reales (no
> reemplaza las entrevistas/tests del plan en `PERSONAS_UX.md §4`). Es:
> 1. **Evaluación heurística** del frontend, citando `archivo:línea`.
> 2. **Testeo funcional** del flujo levantando backend + frontend reales y
>    ejercitando la API.
>
> Valida si el **producto** cumple lo que la persona necesita, no si la persona
> **se comporta** como suponemos (eso sigue requiriendo usuarios). Las claims
> conductuales ("el padre abandona", "la dueña desconfía de la pantalla") quedan
> **pendientes de research**.

---

## Resumen de veredictos

| Supuesto | Veredicto | En una línea |
|---|---|---|
| **A1** — anti-doble-reserva visible al dueño | ⚠️ **Confirmado (gap de UX)** | La garantía existe en BD pero la UI no la comunica; el dueño ve solo un badge en mayúsculas. |
| **A3** — panel dueño mobile-first | ❌ **Refutado (parcial)** | El panel **ya es** mobile-first (bottom-nav, safe-areas, PWA). El riesgo real es de *comprensión*, no de layout. |
| **A4** — precio/disponibilidad sin login | ⚠️ **Confirmado con matiz** | La página pública del negocio sí muestra precio/disponibilidad sin login; pero los accesos desde ReservaKids empujan al cliente a un muro de login. |

---

## A1 — ¿El dueño ve/cree la garantía anti-doble-reserva?

**Veredicto: ⚠️ Confirmado como gap de UX.**

- **La garantía existe y funciona** a nivel de datos: índice único parcial
  `ux_reserva_bloque_activa` + `UPDATE ... WHERE estado=DISPONIBLE` atómico
  (ya documentado en `ANALISIS_COMERCIAL.md §3`). El flujo de pago maneja el
  conflicto 409 recargando horas (`AgendarView.vue:107-112`).
- **Pero la UI no lo comunica.** En el calendario y en solicitudes, el estado se
  muestra solo con `StatusBadge`, que pinta el **enum crudo en mayúsculas**
  (`DISPONIBLE`, `CONFIRMADA`, `COTIZADA`…) sin explicación
  (`StatusBadge.vue:6-13`, `CalendarioView.vue:85`, `SolicitudesView.vue:115`).
  En ningún punto se le dice a la dueña *"este horario quedó bloqueado, nadie más
  puede tomarlo"*.

**Implicación:** la persona Carolina no puede "creerle a la pantalla más que a su
libreta" porque la pantalla no se lo afirma. El backend ya hace el trabajo difícil;
falta **microcopy de confianza** y traducir el enum a lenguaje humano.

> **Pendiente de research:** que la *desconfianza en la pantalla* sea realmente su
> freno (entrevistas, supuesto A1 conductual).

---

## A3 — ¿El panel del dueño es mobile-first?

**Veredicto: ❌ Refutado parcialmente — el producto ya invirtió en móvil.**

Evidencia en `DashboardLayout.vue`:
- Bottom-nav móvil dedicada (`:231-248`), top-bar móvil (`:172-209`).
- Safe-area insets iOS (`env(safe-area-inset-*)`, `:176`, `:234`).
- Instalación PWA (`:124-130`, `usePwaInstall`).
- Botón directo "Ver mi página" / WhatsApp en las solicitudes
  (`SolicitudesView.vue:175-181`).

**El supuesto "el panel no es mobile-first" es falso.** Lo que **sí** queda como
riesgo (y reorienta A3) es la **carga cognitiva**, no el layout:
- Enum en mayúsculas como lenguaje de cara al usuario (ver A1).
- El calendario es una **lista de bloques que la dueña crea a mano**
  (fecha/desde/hasta, `CalendarioView.vue:57-73`), sin vista visual de mes.
  Para baja alfabetización digital, crear "bloques de disponibilidad" es un
  concepto abstracto.
- Formularios con campos numéricos CLP sueltos (`SolicitudesView.vue:128-148`).

> **Reescribir A3** como: *"el panel es responsive ✅, pero su modelo mental
> (bloques, estados en inglés técnico) puede ser demasiado abstracto"* — eso es lo
> que hay que testear con usuarios, no la responsividad.

---

## A4 — ¿El padre ve precio/disponibilidad sin registrarse?

**Veredicto: ⚠️ Confirmado con matiz — depende del punto de entrada.**

Hay **dos** superficies para el cliente:

| Superficie | Ruta | ¿Login? | Qué muestra |
|---|---|---|---|
| Página pública del negocio | `/:slug` → `PublicSiteView` | **No** | Servicios con precio (`:203`), disponibilidad (`:240`), y formulario de cotización sin cuenta |
| Flujo de pago de seña | `/clientes/agendar/:slug` → `AgendarView` | **Sí** (`requiereCliente`) | Selección + pago Mercado Pago |

- Los endpoints `/api/public/{slug}/...` son **realmente públicos** (axios sin
  auth client, `negocioService.js:5-22`). **Testeado funcionando** (ver abajo).
- **El problema real está en los puntos de entrada:** desde la landing,
  "Soy cliente" iba a `/clientes/entrar` (**login**); el directorio
  `/clientes/negocios` exige `requiereCliente` (`router/index.js:35-39`) y
  `listarNegocios()` pega a `/cliente/negocios` autenticado
  (`negocioService.js:38`). **No existe directorio público.**

**Conclusión:** el único camino sin muro es tener el **link directo del negocio**
(su Instagram → `/:slug`). Un padre que llega por ReservaKids.cl chocaba con login.
→ Esto motivó la mejora de la landing (abajo) y deja una recomendación de fondo:
**directorio público sin login** (cambio de backend, fuera de este alcance).

---

## Testeo de flujo end-to-end (app real levantada)

Levantado: `docker compose up db mailpit backend` (API `healthy`) + `npm run dev`
(Vite en :5173). Flujo ejercitado vía API real, **sin token donde corresponde**:

| Paso | Endpoint | Resultado |
|---|---|---|
| Registro dueño | `POST /api/auth/register` | ✅ 201, tenant creado |
| Crear servicio | `POST /api/servicios` (con token) | ✅ "Pack Princesas" $120.000 |
| Crear bloque | `POST /api/calendario/bloques` | ✅ 2026-06-27 15:00–19:00, `DISPONIBLE` |
| **Catálogo público** | `GET /api/public/{slug}` **sin token** | ✅ nombre + servicios + precios |
| **Disponibilidad pública** | `GET /api/public/{slug}/disponibilidad` **sin token** | ✅ devuelve el bloque |
| **Crear solicitud** | `POST /api/public/{slug}/reservas` **sin token** | ✅ 201, genera link `wa.me` |

**El flujo lead (cotización sin cuenta) funciona de punta a punta.** Capturas:
landing nueva, página pública del negocio y login de cliente (en `/tmp/rk_*.png`
durante la sesión).

### Hallazgos extra del testeo

1. 🟠 **Íconos dependen de CDN sin fallback.** Material Symbols se carga solo desde
   `fonts.googleapis.com` (`index.html:25`). Si el CDN va lento/bloqueado o el
   usuario está **offline (¡y la app es PWA!)**, todos los íconos degradan a
   **texto literal** ("mail", "lock", "celebration", "arrow_forward") — confuso
   para un usuario no técnico, justo el perfil objetivo en zonas con conectividad
   pobre. **Recomendación:** auto-hospedar la fuente de íconos o usar SVG inline.
2. 🟢 **Parpadeo "Sin horarios disponibles".** En `PublicSiteView`, la sección de
   bloques no tiene estado de carga: muestra "Sin horarios disponibles este mes"
   mientras el segundo fetch está en vuelo (`PublicSiteView.vue:233-238`). Añadir
   un spinner/skeleton para no asustar al cliente con un falso "no hay nada".

---

## Mejora aplicada: selector dueño/cliente de la landing

**Archivo:** `frontend/src/views/LandingPageView.vue` (hero).

**Antes:** dos botones ("Soy negocio — Entrar" / "Soy cliente — Reservar"), ambos
llevando a formularios de login, sobre una página de marketing SaaS (planes,
"plataforma") que solo le importa al dueño — confuso para un cliente.

**Después** (pensado para baja alfabetización digital):
- Una sola pregunta guía: **"¿Qué quieres hacer?"**
- **Dos tarjetas grandes** con ícono, título en lenguaje llano y una frase:
  - 🎉 *"Quiero reservar una fiesta"* (cliente) → `/clientes/entrar`
  - 🏪 *"Tengo un negocio de fiestas"* (dueño) → `/login`
- Áreas de toque grandes, foco visible (`focus:ring`), jerarquía clara.
- **Pista honesta del camino sin cuenta:** *"¿El negocio te pasó un link? Ábrelo
  directamente para reservar sin crear cuenta."* — surfacea el único acceso sin
  muro en vez de empujar a todos al login.

> Esto mejora la **claridad de la bifurcación**, pero no elimina el muro de login
> del cliente que llega sin link (eso requiere el directorio público — backend).

---

## Recomendaciones priorizadas

| Prioridad | Acción | Origen |
|---|---|---|
| 🔴 Alta | Directorio público de negocios **sin login** (o que "Soy cliente" liste negocios sin registrarse) | A4 |
| 🔴 Alta | Auto-hospedar fuente de íconos (o SVG) — evita texto crudo offline/CDN lento | Testeo |
| 🟠 Media | Traducir enums a lenguaje humano + microcopy "horario bloqueado, nadie más puede tomarlo" | A1 |
| 🟠 Media | Estado de carga en bloques de `PublicSiteView` (quitar parpadeo "Sin horarios") | Testeo |
| 🟢 Baja | Vista de calendario visual (mes) en vez de lista de bloques manual | A3 |
| 🟢 Baja | Validar con usuarios reales las claims conductuales (A1/A4) | Método |

---

## Fixes aplicados (2026-06-22)

| Tema | Cambio | Archivos |
|---|---|---|
| **Íconos (CDN→local)** | Material Symbols **auto-hospedada** y subsetada (~240 KB vs 3,9 MB), `font-display: block`. Se quitó el `<link>` al CDN. Ya no degrada a texto offline. **Verificado por captura.** | `src/assets/material-symbols.css`, `src/assets/fonts/material-symbols-subset.woff2`, `main.js`, `index.html` |
| **A1** anti-doble-reserva visible | `StatusBadge` traduce enums a español llano (`CONFIRMADA`→"Confirmada", `EN_ESPERA`→"En espera"…). Microcopy *"Reservado — nadie más puede tomar este horario"* en calendario y solicitudes. | `components/StatusBadge.vue`, `views/CalendarioView.vue`, `views/SolicitudesView.vue` |
| **A3** carga cognitiva | "Calendario"→"Mi disponibilidad"; "bloque"→"horario"; textos guía en lenguaje llano; empty-state explicativo. | `views/CalendarioView.vue` |
| **A4** directorio público | Endpoint público `GET /api/public/negocios` (reusa `DirectorioService`, solo slug+nombre) + vista `DirectorioPublicoView` + ruta `/negocios`. La tarjeta "Quiero reservar" ya **no** va al login sino al directorio. | `web/PublicDirectorioController.java`, `views/DirectorioPublicoView.vue`, `router/index.js`, `services/negocioService.js`, `views/LandingPageView.vue` |
| Parpadeo "Sin horarios" | Estado de carga (`cargandoBloques`) en la página pública. | `views/PublicSiteView.vue` |

### A4 verificado end-to-end ✅

El refactor en paralelo (puertos `AuditPort`/`AuthEventPort`, `MdcFilter`, CSP,
`HtmlSanitizer`) ya está completo. Su código principal compila; solo **los tests**
quedaron desfasados de las firmas nuevas. Se corrigieron (ver abajo) y el backend
compila de nuevo. Con la imagen reconstruida:

- `GET /api/public/negocios` **sin token** devuelve el directorio de negocios. ✅
- Frontend `/negocios` renderiza el directorio (íconos como glifos, sin login) y
  enlaza a la página pública de cada negocio. **Verificado por captura.**

#### Fix de compilación de los tests (refactor `usuarioId` para bitácora)

El refactor insertó `Long usuarioId` (actor para la bitácora `AuditPort`) en varios
métodos y nuevas deps (`AuditPort`, `HtmlSanitizer`). Los **tests** no se habían
actualizado. Correcciones:

| Archivo | Cambio |
|---|---|
| `ReservaServiceTest` | `@Mock AuditPort` + `@Spy HtmlSanitizer`; firmas `cotizar/confirmar/realizar/cancelar` con `usuarioId` |
| `TenantServiceTest` | `@Mock AuditPort`; `cerrar(tenantId, usuarioId, req)` |
| `RateLimitFilterTest` | `new RateLimitFilter(mock(RateLimitBucketRepository))` (el conteo sigue in-memory) |
| `AgendaConcurrenciaIT`, `HorarioAtencionIT`, `PublicControllerIT` | `guardar(tenantId, 1L, req)` (actor_id es NOT NULL sin FK → literal válido) |

> Nota: `mvn compile` **no** revela estos errores (son de `src/test`); usar
> `mvn test-compile` / `mvn verify`. Y `target/` tenía artefactos de *root* de builds
> en contenedor → `sudo rm -rf backend/target` antes de compilar local.

---

*Personas y supuestos: [`PERSONAS_UX.md`](PERSONAS_UX.md). Contexto de negocio:
[`ANALISIS_COMERCIAL.md`](ANALISIS_COMERCIAL.md).*
