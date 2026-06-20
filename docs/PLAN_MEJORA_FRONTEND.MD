# PLAN DE MEJORA — Frontend ReservaKids

**Fecha:** 2026-06-18
**Alcance:** Solo frontend. Backend se modifica únicamente para endpoints nuevos estrictamente necesarios.
**Restricción global:** No se agrega lógica que no esté justificada por las features P0/P1 del análisis. No se reescribe lo que funciona.

---

## ÍNDICE

1. [Sprint 1 — Infraestructura](#sprint-1--infraestructura-semanas-1-2)
2. [Sprint 2 — Features Comerciales](#sprint-2--features-comerciales-semanas-3-4)
3. [Sprint 3 — Tests + PWA + SEO + Polish](#sprint-3--tests--pwa--seo--polish-semanas-5-6)
4. [Backend — Endpoints Faltantes](#backend--endpoints-faltantes)
5. [Restricciones Clean Code](#restricciones-clean-code)
6. [Archivos a Crear](#archivos-a-crear)

---

## SPRINT 1 — Infraestructura (Semanas 1-2)

### 1.1 Componentes Reutilizables

**Directorio:** `frontend/src/components/`

#### 1.1.1 `BaseToast.vue`

- **Reemplaza:** `alert()` en `ConfiguracionView.vue:67` y `ResetPasswordView.vue:41`
- **Props:** `mensaje` (String), `tipo` (String: `'exito'` | `'error'`), `duracion` (Number, default 3000)
- **Evento:** `@cerrar`
- **Restricciones Clean Code:**
  - Máximo 1 responsabilidad: mostrar y auto-ocultar. No gestiona estado global.
  - Nombres descriptivos en español (coherencia con el proyecto).
  - Sin lógica de negocio dentro del componente.

#### 1.1.2 `BaseModal.vue`

- **Reemplaza:** `prompt()` en `SolicitudesView.vue:47` y `confirm()`/`prompt()` en `DashboardLayout.vue:57`
- **Props:** `visible` (Boolean), `titulo` (String)
- **Slots:** `default` (contenido), `acciones` (botones)
- **Eventos:** `@confirmar`, `@cancelar`, `@cerrar`
- **Restricciones Clean Code:**
  - No contiene lógica de negocio. Solo layout + emit.
  - Accesibilidad: `role="dialog"`, `aria-modal="true"`, focus trap básico.

#### 1.1.3 `BaseButton.vue`

- **Reemplaza:** Botones inline repetidos en 12 vistas
- **Props:** `variante` (String: `'primario'` | `'secundario'` | `'peligro'`), `deshabilitado` (Boolean), `cargando` (Boolean)
- **Slot:** `default` (texto del botón)
- **Restricciones Clean Code:**
  - Sin lógica. Solo presentación.
  - Las clases Tailwind se definen internamente, no se pasan como prop (encapsulamiento).

#### 1.1.4 `BaseInput.vue`

- **Reemplaza:** Inputs con icono Material repetidos en todas las vistas
- **Props:** `modeloValue` (v-model), `tipo` (String), `placeholder` (String), `icono` (String: nombre de Material Symbol), `requerido` (Boolean), `deshabilitado` (Boolean)
- **Evento:** `@update:modeloValue`
- **Restricciones Clean Code:**
  - Componente controlado. No gestiona su propio estado.
  - Usa `v-model` estándar de Vue 3.

#### 1.1.5 `GlassCard.vue`

- **Reemplaza:** `glass-card rounded-3xl shadow-soft` en 5 vistas
- **Props:** `clase` (String, opcional para overrides)
- **Slot:** `default`
- **Restricciones Clean Code:**
  - Componente puro de presentación. Sin props de negocio.

#### 1.1.6 `ErrorBanner.vue`

- **Reemplaza:** `<p v-if="error" class="text-sm font-semibold text-on-error-container...">` en todas las vistas
- **Props:** `mensaje` (String)
- **Restricciones Clean Code:**
  - No renderiza si `mensaje` es null/undefined/empty.
  - `role="alert"` y `aria-live="polite"` siempre presentes.

#### 1.1.7 `LoadingSpinner.vue`

- **Reemplaza:** Texto "Cargando..." inline en 4 vistas
- **Props:** `mensaje` (String, default `'Cargando...'`)
- **Restricciones Clean Code:**
  - Solo presentación. No decide cuándo mostrar/ocultar.

#### 1.1.8 `StatusBadge.vue`

- **Reemplaza:** `colorEstado` duplicado en `SolicitudesView.vue:15` y `CalendarioView.vue:38`
- **Props:** `estado` (String)
- **Restricciones Clean Code:**
  - El mapa de colores es una constante interna del componente (no se duplica fuera).
  - Si el estado no existe en el mapa, renderiza con color gris por defecto.

#### 1.1.9 `EmptyState.vue`

- **Reemplaza:** Empty states manuales en 6 vistas
- **Props:** `mensaje` (String), `icono` (String, opcional)
- **Slot:** `acciones` (opcional, para CTA)
- **Restricciones Clean Code:**
  - No contiene lógica. Solo layout.

#### 1.1.10 `BasePagination.vue`

- **Props:** `paginaActual` (Number), `totalPaginas` (Number)
- **Evento:** `@cambiarPagina`
- **Restricciones Clean Code:**
  - No gestiona su propio estado. Recibe y emite.
  - Deshabilita botones anterior/siguiente en extremos.

**Esfuerzo estimado:** 3 días

---

### 1.2 Composables

**Directorio:** `frontend/src/composables/`

#### 1.2.1 `useCurrency.js`

- **Elimina duplicación de:** `clp()` en `SolicitudesView.vue:58`, `ServiciosView.vue:37`, `AgendarView.vue:68`, `PublicSiteView.vue:65`
- **Exporta:** `function clp(valor)` — retorna string formateado en CLP
- **Restricciones Clean Code:**
  - Función pura. Sin side effects.
  - Manejo consistente de null/undefined (retorna `'$0'`).
  - Una sola función. No agrupar en objeto innecesariamente.

#### 1.2.2 `useBaseURL.js`

- **Elimina duplicación de:** `(import.meta.env.VITE_API_URL || '') + '/api'` en 6 archivos
- **Exporta:** `const BASE_URL` — string constante
- **Restricciones Clean Code:**
  - Se evalúa una sola vez al importar. No es reactivo.
  - Nombre descriptivo.

#### 1.2.3 `useAsync.js`

- **Elimina duplicación de:** Patrón `cargando + error + try/catch` en todas las vistas
- **Exporta:** `function useAsync(fn)` → `{ cargando, error, ejecutar }`
- **Restricciones Clean Code:**
  - `ejecutar` recibe y retorna los mismos args que `fn`.
  - `error` almacena el string del mensaje, no el objeto Error completo.
  - Un solo composable por operación async. No agrupar múltiples llamadas.

#### 1.2.4 `useDuration.js`

- **Elimina duplicación de:** Formato de duración en `AgendarView.vue:70`
- **Exporta:** `function duracion(minutos)` — retorna string legible (ej: `"2h 30min"`)
- **Restricciones Clean Code:**
  - Función pura. Sin side effects.

**Esfuerzo estimado:** 1 día

---

### 1.3 Services Layer

**Directorio:** `frontend/src/services/`

#### 1.3.1 `reservaService.js`

- **Centraliza llamadas de:** `SolicitudesView.vue`
- **Funciones:** `listar(estado, page, size)`, `cotizar(id, datos)`, `confirmar(id)`, `realizar(id)`, `cancelar(id, motivo)`, `registrarPago(id, datos)`
- **Usa:** `api` de `../api/client` (con interceptor de refresh)
- **Restricciones Clean Code:**
  - Cada función hace exactamente una llamada API.
  - No maneja errores. El componente decide qué mostrar.
  - Retorna la respuesta data directamente (no el objeto axios completo).

#### 1.3.2 `servicioService.js`

- **Centraliza llamadas de:** `ServiciosView.vue`
- **Funciones:** `listar()`, `crear(datos)`, `actualizar(id, datos)`, `desactivar(id)`
- **Restricciones Clean Code:** Igual que 1.3.1.

#### 1.3.3 `calendarioService.js`

- **Centraliza llamadas de:** `CalendarioView.vue`
- **Funciones:** `listarBloques(mes)`, `crearBloque(datos)`, `eliminarBloque(id)`
- **Restricciones Clean Code:** Igual que 1.3.1.

#### 1.3.4 `configuracionService.js`

- **Centraliza llamadas de:** `ConfiguracionView.vue`
- **Funciones:** `guardarConfig(datos)`, `guardarHorario(datos)`, `cargarHorario()`
- **Restricciones Clean Code:** Igual que 1.3.1.

#### 1.3.5 `negocioService.js`

- **Centraliza llamadas de:** `PublicSiteView.vue`, `AgendarView.vue`, `DirectorioView.vue`
- **Funciones:** `catalogo(slug)`, `disponibilidad(slug, mes)`, `horas(slug, fecha, duracion)`, `crearSolicitud(slug, datos)`, `listarNegocios()`, `agendar(slug, datos)`
- **Restricciones Clean Code:**
  - Los endpoints públicos usan `axios` directo (sin auth).
  - Los endpoints de cliente usan `api` de `../api/clienteClient`.

#### 1.3.6 `clienteService.js`

- **Nuevo.** Para historial de reservas, perfil, reset password.
- **Funciones:** `listarReservas(estado, page, size)`, `solicitarReset(email)`, `confirmarReset(token, nuevaPassword)`
- **Restricciones Clean Code:** Igual que 1.3.1.

**Esfuerzo estimado:** 2 días

---

### 1.4 Migración de Vistas Existentes

Cada vista se refactoriza para:

1. Importar componentes de `src/components/` en vez de clases inline
2. Usar composables de `src/composables/` en vez de helpers locales
3. Usar services de `src/services/` en vez de llamadas API inline
4. Reemplazar `alert()` → `BaseToast`, `prompt()`/`confirm()` → `BaseModal`

| Vista | Componentes a usar | Composables | Services |
|---|---|---|---|
| `SolicitudesView` | BaseButton, StatusBadge, BaseModal, ErrorBanner, LoadingSpinner, BasePagination | useAsync, useCurrency | reservaService |
| `ServiciosView` | BaseButton, BaseInput, ErrorBanner, LoadingSpinner, EmptyState | useAsync, useCurrency | servicioService |
| `CalendarioView` | BaseButton, BaseInput, StatusBadge, ErrorBanner, LoadingSpinner | useAsync | calendarioService |
| `ConfiguracionView` | BaseButton, BaseInput, BaseToast, ErrorBanner | useAsync | configuracionService |
| `DashboardLayout` | BaseModal, BaseToast | — | — |
| `PublicSiteView` | GlassCard, BaseButton, BaseInput, ErrorBanner, LoadingSpinner | useAsync, useCurrency | negocioService |
| `AgendarView` | BaseButton, BaseInput, ErrorBanner, LoadingSpinner, EmptyState | useAsync, useCurrency, useDuration | negocioService |
| `DirectorioView` | GlassCard, LoadingSpinner, EmptyState | useAsync | negocioService |
| `LoginView` | GlassCard, BaseButton, BaseInput, ErrorBanner | useAsync | — |
| `ClienteLoginView` | GlassCard, BaseButton, BaseInput, ErrorBanner | useAsync | — |
| `ClienteHomeView` | GlassCard, BaseButton | — | — |
| `ResetPasswordView` | GlassCard, BaseButton, BaseInput, BaseToast, ErrorBanner | useAsync | — |

**Restricción:** No se cambia la lógica de negocio de las vistas. Solo se reorganiza la presentación y se extrae código duplicado.

**Esfuerzo estimado:** 4 días

---

### 1.5 Eliminar Axios Directo (Bug de Seguridad)

| Archivo | Línea | Problema | Fix |
|---|---|---|---|
| `AgendarView.vue` | ~105 | Usa `axios` directo para `pagar()`, saltándose el interceptor de refresh de `clienteClient` | Reemplazar por `negocioService.agendar()` que usa `clienteClient` |
| `stores/auth.js` | ~29 | Usa `axios.post` directo para login | Aceptable para login (no hay token aún), pero documentar con comentario |
| `stores/clienteAuth.js` | ~34 | Usa `axios.post` directo para login | Igual que auth.js |
| `PublicSiteView.vue` | ~4 | Usa `axios` directo | Aceptable para endpoints públicos sin auth |

**Restricción:** Solo se corrige el bug de seguridad (AgendarView). Login y endpoints públicos se documentan pero no se cambian (son comportamientos correctos).

**Esfuerzo:** Incluido en 1.4

---

## SPRINT 2 — Features Comerciales (Semanas 3-4)

### 2.1 Landing Page

**Archivo:** `frontend/src/views/LandingPageView.vue`
**Ruta:** `/` (reemplaza redirect actual a `/login`)

**Secciones:**

1. Hero: tagline + imagen + CTA dual (negocios / clientes)
2. Features: catálogo, calendario, notificaciones, pagos (4 cards)
3. Cómo funciona: 3 pasos numerados
4. Pricing: 3 tiers (Gratis / Pro / Business) — solo visual, sin integración de cobro
5. Footer: links a `/privacidad`, `/login`, `/clientes/entrar`

**Restricciones Clean Code:**

- Componente monolítico justificado: es una página de marketing, no reutilizable.
- Sin lógica de negocio. Solo contenido estático + router links.
- Usa los tokens de diseño existentes (`@theme` en `style.css`).
- No agrega dependencias nuevas.

**Router change en `src/router/index.js`:**

```js
// ANTES:
{ path: '/', redirect: '/login' }

// DESPUÉS:
{ path: '/', component: () => import('../views/LandingPageView.vue') }
```

**Esfuerzo estimado:** 3 días

---

### 2.2 Historial de Reservas del Cliente

**Archivo:** `frontend/src/views/ClienteHistorialView.vue`
**Ruta:** `/clientes/reservas` con `meta: { requiereCliente: true }`

**Contenido:**

- Lista de reservas del cliente autenticado
- Filtro por estado (tabs o select)
- Card por reserva: servicio, fecha, estado (`StatusBadge`), monto, link WhatsApp
- Empty state cuando no hay reservas
- Paginación con `BasePagination`

**Backend requerido:** `GET /api/cliente/reservas` (ver sección [Backend](#backend--endpoints-faltantes))

**Restricciones Clean Code:**

- Usa `useAsync` para la carga de datos.
- Usa `clienteService.listarReservas()` para la llamada API.
- Usa `StatusBadge`, `EmptyState`, `LoadingSpinner`, `BasePagination`.
- Sin lógica duplicada de `clp()` — usa `useCurrency`.

**Esfuerzo estimado:** 2 días (1 backend + 1 frontend)

---

### 2.3 Reset de Contraseña para Clientes

**Archivos:**

- `frontend/src/views/ClienteResetView.vue` — solicitar reset
- `frontend/src/views/ClienteResetConfirmView.vue` — confirmar con token

**Rutas:**

- `/clientes/reset`
- `/clientes/reset/confirmar`

**Backend requerido:** `POST /api/cliente-auth/reset/solicitar` y `POST /api/cliente-auth/reset/confirmar` (ver sección [Backend](#backend--endpoints-faltantes))

**Restricciones Clean Code:**

- Reutiliza los mismos componentes que `ResetPasswordView.vue` (GlassCard, BaseButton, BaseInput, BaseToast, ErrorBanner).
- No duplica lógica de validación. El backend es la fuente de verdad.
- Usa `clienteService.solicitarReset()` y `clienteService.confirmarReset()`.

**Esfuerzo estimado:** 2 días (1 backend + 1 frontend)

---

### 2.4 Loading States Globales

**Vistas sin loading state actualmente:**

- `SolicitudesView` — pantalla en blanco durante `cargar()`
- `ServiciosView` — pantalla en blanco durante `cargar()`
- `CalendarioView` — pantalla en blanco durante `cargar()`
- `PublicSiteView` — sin loading durante `cargarNegocio()`

**Implementación:** `<LoadingSpinner v-if="cargando" />` antes del contenido principal. El ref `cargando` proviene de `useAsync`.

**Restricciones Clean Code:**

- No se agrega lógica nueva. Solo se inserta el componente donde ya existe el patrón de carga.
- `LoadingSpinner` se muestra condicionalmente, nunca oculta contenido.

**Esfuerzo:** Incluido en Sprint 1 (1.4)

---

### 2.5 Paginación en Frontend

**Backend ya soporta:** `ReservaController.listar()` retorna `Page<ReservaResponse>` con `page`/`size`.

**Implementación frontend en:**

- `SolicitudesView.vue` — pasar `page`/`size` a `reservaService.listar()`, renderizar `BasePagination`
- `ClienteHistorialView.vue` — desde el inicio

**Restricciones Clean Code:**

- `BasePagination` no gestiona estado. Recibe `paginaActual` y `totalPaginas`, emite `@cambiarPagina`.
- El componente padre gestiona la página actual y recarga datos.

**Esfuerzo:** 1 día

---

### 2.6 Términos y Privacidad

**Archivo:** `frontend/src/views/PrivacidadView.vue`
**Ruta:** `/privacidad` (ya existe en router)

**Contenido:**

- Política de privacidad (Ley 21.719)
- Derechos del titular (acceso, rectificación, supresión)
- Datos del responsable

**Restricciones Clean Code:**

- Contenido estático. Sin lógica.
- Usa tokens de diseño existentes.

**Esfuerzo:** 0.5 días

---

### 2.7 Vista 404 + Error Server

**Archivos:**

- `frontend/src/views/NotFoundView.vue`
- `frontend/src/views/ServerErrorView.vue`

**Router:** Agregar antes del catch-all `/:slug`:

```js
{ path: '/404', component: () => import('../views/NotFoundView.vue') },
{ path: '/:pathMatch(.*)*', redirect: '/404' }
```

**Restricciones Clean Code:**

- Componentes simples. Solo texto + CTA.
- Sin lógica de negocio.

**Esfuerzo:** 0.5 días

---

## SPRINT 3 — Tests + PWA + SEO + Polish (Semanas 5-6)

### 3.1 Tests Frontend (Vitest)

**Configuración:**

- `npm i -D vitest @vue/test-utils jsdom`
- Crear `vitest.config.js`

**Tests a crear:**

| Test | Archivo | Cubre |
|---|---|---|
| BaseToast | `tests/components/BaseToast.spec.js` | Render, show/hide, auto-dismiss |
| BaseModal | `tests/components/BaseModal.spec.js` | Open/close, slots, eventos |
| BaseButton | `tests/components/BaseButton.spec.js` | Variantes, disabled, loading |
| BaseInput | `tests/components/BaseInput.spec.js` | v-model, icono, validación |
| useCurrency | `tests/composables/useCurrency.spec.js` | Formato CLP, null |
| useAsync | `tests/composables/useAsync.spec.js` | Loading, error, success |
| useDuration | `tests/composables/useDuration.spec.js` | Formato duración |
| auth store | `tests/stores/auth.spec.js` | Login, logout, refresh |
| clienteAuth store | `tests/stores/clienteAuth.spec.js` | Login, logout, refresh |

**Restricciones Clean Code:**

- Un test por comportamiento. No tests que verifiquen implementación interna.
- Nombres descriptivos en español (coherencia con tests backend).
- Tests de componente: verificar render y eventos, no lógica de negocio.
- Tests de composable: verificar outputs para inputs dados.
- Tests de store: verificar transiciones de estado.

**Esfuerzo estimado:** 3 días

---

### 3.2 PWA Support

**Archivos a crear/modificar:**

- `public/manifest.json`
- `vite.config.js` (agregar `vite-plugin-pwa`)
- `public/icons/icon-192.png`, `public/icons/icon-512.png`

**Restricciones Clean Code:**

- Solo se habilita para el panel del negocio (no para la landing page pública).
- Service worker precachea assets estáticos. No cachea respuestas API.
- No se agrega offline mode completo (solo precache de shell).

**Esfuerzo:** 1 día

---

### 3.3 SEO para Páginas Públicas

**Problema:** `PublicSiteView` no tiene `<title>`, `<meta description>`, ni Open Graph tags.

**Solución:** `@vueuse/head` para meta tags dinámicos.

**Restricciones Clean Code:**

- Solo se aplica a `PublicSiteView` y `LandingPageView`.
- Meta tags se derivan de los datos ya cargados (no se hace fetch extra).
- No se agrega SSR. Solo meta tags en el head del SPA.

**Esfuerzo:** 0.5 días

---

### 3.4 Accesibilidad

| Fix | Archivos |
|---|---|
| `aria-live="polite"` en error banners | Todas las vistas |
| `role="alert"` en mensajes de error | Todas las vistas |
| `aria-hidden="true"` en imágenes decorativas | 6 vistas con character images |
| `role="navigation"` + `aria-label` en bottom nav | DashboardLayout |
| `aria-describedby` en form fields | Todas las vistas con formularios |

**Restricciones Clean Code:**

- No se cambia comportamiento visual. Solo atributos ARIA.
- Se aplica durante la migración de vistas (Sprint 1.4), no como tarea separada.

**Esfuerzo:** 1 día

---

## BACKEND — Endpoints Faltantes

### B1. Historial de Reservas del Cliente

**Archivos a modificar:**

- `backend/src/main/java/cl/reservakids/infrastructure/web/ClienteController.java` — agregar endpoint
- `backend/src/main/java/cl/reservakids/application/usecase/ClienteService.java` — agregar método
- `backend/src/main/java/cl/reservakids/domain/repository/ReservaRepository.java` — agregar query

**Endpoint:**

```
GET /api/cliente/reservas
```

- Retorna reservas del cliente autenticado (scoped por `clienteId` del JWT)
- Soporta `?estado=PENDIENTE` como filtro opcional
- Retorna `Page<ReservaResponse>` (reutiliza DTO existente)

**Nuevo query en `ReservaRepository`:**

```java
Page<Reserva> findByClienteIdAndEstadoIn(Long clienteId, Set<EstadoReserva> estados, Pageable pageable);
```

**Nuevo método en `ClienteService`:**

```java
public Page<ReservaResponse> listarReservas(Long clienteId, EstadoReserva estado, Pageable pageable)
```

**Restricciones Clean Code:**

- Respeta el patrón existente: controller inyecta service, service usa repository.
- Multi-tenant: `clienteId` se obtiene del JWT, nunca del request body.
- Reutiliza `ReservaResponse.de()` existente.
- Un solo endpoint. No se crean endpoints auxiliares.

**Esfuerzo:** 0.5 días

---

### B2. Reset de Contraseña para Clientes

**Archivos a modificar:**

- `backend/src/main/java/cl/reservakids/infrastructure/web/ClienteAuthController.java` — agregar endpoints
- `backend/src/main/java/cl/reservakids/application/usecase/ClienteAuthService.java` — agregar lógica

**Endpoints:**

```
POST /api/cliente-auth/reset/solicitar   {email}
POST /api/cliente-auth/reset/confirmar   {token, nuevaPassword}
```

**Implementación:** Reutilizar la lógica de `AuthService.resetSolicitar()` y `AuthService.resetConfirmar()`. La diferencia es buscar en `CuentaCliente` en vez de `Usuario`.

**Opción A (recomendada):** Extraer lógica común a un `PasswordResetService` compartido.
**Opción B:** Duplicar con las diferencias mínimas (buscar cliente vs usuario).

**Restricciones Clean Code:**

- Respeta el patrón existente: controller inyecta service.
- Anti-enumeración: `resetSolicitar` retorna 204 siempre (incluso si el email no existe).
- Token de un solo uso, expira en 30 minutos (mismo comportamiento que reset de dueños).
- `resetConfirmar` revoca todas las sesiones del cliente (mismo comportamiento que reset de dueños).

**Esfuerzo:** 1 día

---

### B3. Tests Backend Adicionales

| Test | Archivo | Cubre |
|---|---|---|
| `ClienteReservaServiceTest.java` | Nuevo | Listar reservas del cliente, filtros por estado |
| `PasswordResetClienteTest.java` | Nuevo | Flujo completo de reset para clientes |
| `PublicControllerIT.java` | Nuevo | Test de integración para endpoints públicos |

**Restricciones Clean Code:**

- Siguen el patrón existente: `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`.
- Nombres en español descriptivos.
- Tests de integración usan `@SpringBootTest` + `@Testcontainers` + `@ActiveProfiles("test")`.
- Factory methods privados para datos de test.

**Esfuerzo:** 1 día

---

## RESTRICCIONES CLEAN CODE

### Principios aplicados en todo el plan

1. **Responsabilidad Única (SRP):** Cada componente, composable y service hace una cosa. `BaseToast` muestra toasts. `useCurrency` formatea moneda. `reservaService` llama a la API de reservas.

2. **Abierto/Cerrado (OCP):** Los componentes se extienden via props y slots, no modificando su código interno. `BaseButton` acepta `variante` para nuevos estilos sin cambiar el componente.

3. **Inversión de Dependencias (DIP):** Los services no dependen de axios directamente. Dependen de la instancia `api` configurada con interceptores. Los componentes no dependen de la implementación del service, solo de su interfaz.

4. **No Repetición (DRY):** `clp()` se define una vez en `useCurrency.js`. `baseURL` se define una vez en `useBaseURL.js`. El patrón `cargando + error` se define una vez en `useAsync.js`.

5. **Principio de Menor Sorpresa:** Nombres en español, coherentes con el resto del proyecto. `BaseToast` hace lo que sugiere su nombre. `StatusBadge` muestra un badge de estado.

6. **YAGNI (No lo vas a necesitar):** No se agrega i18n, dark mode, o analytics en este plan. Son features P2/P3 que no están justificadas para el MVP comercial.

### Restricciones específicas del proyecto

- **No se cambia la arquitectura del frontend.** Se mantiene Vue 3 + Pinia + Vue Router + Tailwind v4.
- **No se agregan dependencias nuevas** excepto: `vitest`, `@vue/test-utils`, `jsdom`, `vite-plugin-pwa`, `@vueuse/head`.
- **No se modifica el backend** excepto para los 2 endpoints nuevos (historial de reservas, reset de contraseña para clientes) y sus tests.
- **No se reescriben vistas que funcionan.** Se refactorizan para usar componentes/composables/services, pero la lógica de negocio permanece igual.
- **No se introduce TypeScript.** Se mantiene JavaScript puro para coherencia con el código existente.
- **No se introduce state management para datos de dominio.** Las reservas, servicios y calendario siguen siendo estado local de cada vista via `ref()`. Pinia solo se usa para auth.

---

## ARCHIVOS A CREAR

```
frontend/src/
├── components/
│   ├── BaseToast.vue
│   ├── BaseModal.vue
│   ├── BaseButton.vue
│   ├── BaseInput.vue
│   ├── GlassCard.vue
│   ├── ErrorBanner.vue
│   ├── LoadingSpinner.vue
│   ├── StatusBadge.vue
│   ├── EmptyState.vue
│   └── BasePagination.vue
├── composables/
│   ├── useCurrency.js
│   ├── useBaseURL.js
│   ├── useAsync.js
│   └── useDuration.js
├── services/
│   ├── reservaService.js
│   ├── servicioService.js
│   ├── calendarioService.js
│   ├── configuracionService.js
│   ├── negocioService.js
│   └── clienteService.js
├── views/
│   ├── LandingPageView.vue
│   ├── NotFoundView.vue
│   ├── ServerErrorView.vue
│   ├── ClienteHistorialView.vue
│   ├── ClienteResetView.vue
│   └── ClienteResetConfirmView.vue
public/
├── manifest.json
├── icons/
│   ├── icon-192.png
│   └── icon-512.png
tests/
├── components/
│   ├── BaseToast.spec.js
│   ├── BaseModal.spec.js
│   ├── BaseButton.spec.js
│   └── BaseInput.spec.js
├── composables/
│   ├── useCurrency.spec.js
│   ├── useAsync.spec.js
│   └── useDuration.spec.js
└── stores/
    ├── auth.spec.js
    └── clienteAuth.spec.js
vitest.config.js
```

**Archivos backend a crear/modificar:**

```
backend/src/main/java/cl/reservakids/
├── infrastructure/web/ClienteController.java          (modificar)
├── application/usecase/ClienteService.java            (modificar)
├── application/usecase/ClienteAuthService.java        (modificar)
├── infrastructure/web/ClienteAuthController.java      (modificar)
└── domain/repository/ReservaRepository.java           (modificar)

backend/src/test/java/cl/reservakids/
├── application/ClienteReservaServiceTest.java         (nuevo)
├── application/PasswordResetClienteTest.java          (nuevo)
└── integration/PublicControllerIT.java                (nuevo)
```

---

## RESUMEN DE ESFUERZO

| Sprint | Foco | Días | Entregable |
|---|---|---|---|
| **Sprint 1** | Infraestructura frontend | 10 | 10 componentes + 4 composables + 6 services + refactor de 12 vistas |
| **Sprint 2** | Features comerciales | 8 | Landing + historial + reset cliente + paginación + 404 + términos |
| **Sprint 3** | Calidad + polish | 5 | 9 tests Vitest + PWA + SEO + accesibilidad |
| **Backend** | Endpoints + tests | 2.5 | 2 endpoints nuevos + 3 tests |

**Total estimado:** 25.5 días de trabajo (5 semanas laborales).
