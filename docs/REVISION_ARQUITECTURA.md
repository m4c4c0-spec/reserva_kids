# Revisión de arquitectura — ReservaKids

**Fecha:** 2026-06-11 (Sesión 6) · **Preguntas guía:** *¿la arquitectura es lo que dice ser? ¿dónde están sus fallas estructurales? ¿se puede migrar a otra arquitectura o a otro lenguaje?*

Complementa las revisiones de fallas ([1 año](REVISION_FALLAS_1_ANO.md) · [2 años](REVISION_FALLAS_2_ANOS.md) · [5 años](REVISION_FALLAS_5_ANOS.md)): aquellas miran el *comportamiento* del sistema en el tiempo; esta mira su *estructura*.

---

## 1. Lo que dice ser vs lo que es

**Lo que dice ser** (README, SDLC, `MANUAL_MIGRACION.md` §1): Onion / Puertos y Adaptadores con regla de dependencias "siempre hacia adentro" y un dominio que "no importa nada del framework".

**Lo que es, medido en el código:**

| Afirmación | Realidad |
|---|---|
| "El dominio no depende de nada" | **18 de 23 archivos de `domain/` importan `jakarta.persistence` o `org.springframework`.** Las entidades son entidades JPA con Lombok; los repositorios extienden `JpaRepository` y contienen **19 queries JPQL** (`@Query`/`@Modifying`). Solo son puros los 2 enums (`EstadoReserva`, `EstadoBloque`) y las 3 excepciones. |
| "Puertos y Adaptadores" | Hay exactamente **2 puertos verdaderos** (`NotificacionPort`, `TokenPort`) con sus adaptadores en infraestructura. Los 9 repositorios son puertos *de nombre*: su contrato está definido en términos de Spring Data. |
| "Aplicación = casos de uso" | Cierto en responsabilidad, pero acoplada a Spring (`@Service`, `@Transactional`, `@Value`, `@Scheduled`). |
| Capa web/infra | ✅ Correcta: controllers delgados, DTOs separados de entidades (la API nunca expone JPA), excepciones mapeadas en un solo lugar. |

**Veredicto honesto: es una Onion de *paquetes*, no de *dependencias*.** La dirección application → domain e infrastructure → application se respeta; la única flecha invertida — pero es estructural — es domain → Spring Data/JPA.

### ¿Importa? Análisis del trade-off

Para este proyecto, **el acoplamiento elegido es defendible**: un dominio 100% puro exigiría entidades de dominio + entidades JPA + mappers + interfaces de repositorio en dominio con implementaciones adapter — aproximadamente **duplicar las ~3.000 LOC** para que un mantenedor único gane una pureza que nunca va a cobrar (nadie va a cambiar Postgres por Mongo). Es el trade-off estándar del ecosistema Spring y fue la decisión correcta.

Lo que **sí** importa es la consecuencia sobre la migración: el manual da a entender que el dominio se preserva tal cual — **falso**. Lo portable de este sistema no es el código de dominio (está soldado a JPA): son los **contratos** (esquema SQL, máquina de estados, algoritmo anti doble-reserva, API REST, tabla de seguridad). El manual se corrige en esta sesión (§1, nota de honestidad).

## 2. Lo que está bien y hay que conservar

1. **Las invariantes críticas viven en la BD, no en el código**: índice único parcial anti doble-reserva, `UNIQUE (tenant_id, fecha, hora_inicio)`, CHECKs de estado. Sobreviven a cualquier reescritura — es la decisión arquitectónica más valiosa del proyecto.
2. **La lógica de dominio crítica es pura de facto**: `EstadoReserva.puedeTransicionarA()`, `Cliente.normalizarTelefono()/anonimizar()`, `Reserva.transicionarA()` — funciones sin I/O, testeadas exhaustivamente. `EstadoReservaTest` funciona como especificación ejecutable.
3. **Esquema versionado (Flyway V1–V7) como fuente de verdad** + contrato REST documentado y estable.
4. **Los 2 puertos reales están donde duelen**: notificaciones (canal cambiará: SMTP→Resend→WhatsApp API) y tokens (formato podría cambiar). Puertos donde hay volatilidad real, acoplamiento donde hay estabilidad real — eso es criterio, no dogma.
5. **Errores uniformes y manejo de carreras centralizado** (`GlobalExceptionHandler`: TOCTOU → 409, optimistic locking → 409).

## 3. Fallas estructurales (en orden de importancia)

### A1 🔴 Las garantías más importantes del sistema no se prueban en CI
Los 49 tests son unitarios con mocks. Eso significa que **nunca se ejecutan en CI**: las 19 queries JPQL (un typo en un `@Query` explota en runtime, no en `mvn test`), las migraciones V1–V7 (validadas a mano contra Docker en las Sesiones 5–6 — disciplina, no automatización), el índice único parcial y el `UPDATE` condicionado (el corazón del producto, RNF-05), y el mapeo JPA completo (`ddl-auto: validate` solo corre al arrancar). **Es la falla estructural #1 y la más barata de corregir**: Testcontainers con un test de contexto + un test de concurrencia del bloque (N hilos → 1 reserva) cubriría el 80% del riesgo con ~2 archivos.

### A2 🟡 `ExpiracionService` es un "god job" por acreción
Nació con 1 job; tras tres revisiones de fallas tiene **7 jobs y 10 dependencias** (expiración de reservas, cierre, anonimización, 3 purgas, limpieza). Cada falla nueva le agregó un cron. No es un bug — es el patrón de crecimiento que en el año 3–4 lo vuelve intocable. Partirlo en `CicloReservaJobs` (expirar/realizar — dominio del negocio) y `MantenimientoJobs` (purgas/limpieza — higiene de datos) en la próxima ventana semestral.

### A3 🟡 Inconsistencias menores que confunden al sucesor
- **Estados como `String` en `Tenant`/`Pago` vs `enum` en `Reserva`/`Bloque`** — dos convenciones para el mismo concepto.
- **`Clock` inyectado en `ClienteService`/`ExpiracionService` vs `OffsetDateTime.now()` directo en `AuthService`/`ReservaService`** — la mitad del código es testeable en el tiempo, la otra mitad no.
- **`pago` sin `tenant_id`**: el aislamiento multi-tenant de pagos es indirecto (vía subquery de reserva). Correcto hoy porque `registrarPago()` valida la reserva primero, pero es la única tabla donde el aislamiento depende de disciplina de código y no de una columna.
- **`reserva.comentarios` es un campo multipropósito** (notas del cliente + `[Cancelación]` + `[Expiración]` + `[Cierre del negocio]`): un event-log en texto plano. La falla 3.2 lo hizo seguro legalmente; estructuralmente, una tabla `reserva_evento` sería lo limpio — **no paga a esta escala**, anotar para v2 si los reportes (RF-10) lo piden.

### A4 🟢 `AuthService` creciendo (7 dependencias, 6 casos de uso)
Registro, login, refresh, logout, reset×2. Todavía cohesivo (todo es auth), pero es el siguiente candidato a partirse (`PasswordResetService`) si crece más.

## 4. ¿Migrar a otra arquitectura?

| Opción | Veredicto |
|---|---|
| **Quedarse con la Onion-pragmática actual** | ✅ **Recomendado.** Los problemas reales (A1–A3) se corrigen *dentro* de esta arquitectura por una fracción del costo de cualquier migración. |
| Onion/Hexagonal purista (dominio sin JPA, mappers) | ❌ Duplica LOC y mantenimiento para un beneficio teórico. El único escenario que lo justificaría — cambiar el motor de persistencia — no está en ningún horizonte. |
| Monolito modular por features (`reservas/`, `auth/`, `tenants/`…) | ⚖️ Neutro hoy (8 entidades caben en la cabeza); reconsiderar solo si el código triplica su tamaño (v2 con pagos online + reportes + multi-sucursal). |
| Microservicios | ❌ Absurdo a esta escala: multiplicaría infra, observabilidad y modos de falla para un sistema de 1 VPS y bus factor 1. Ninguna falla de las revisiones se resuelve con red entre procesos. |

**La arquitectura no es el cuello de botella del proyecto** — las revisiones a 1/2/5 años lo demuestran: ninguna de las ~40 fallas encontradas fue causada por la estructura del código. El cuello de botella es proceso (ventanas de mantenimiento, deploy, negocio).

## 5. ¿Migrar a otro lenguaje/stack?

**Sí, es viable — y este proyecto está inusualmente bien preparado**, por tres activos que la mayoría de los proyectos no tiene:

1. **El esquema SQL versionado es la fuente de verdad** y es 100% portable (las invariantes van en la BD, §2.1).
2. **El contrato REST está documentado y el frontend solo depende de él** — Vue no cambia ni una línea si §5 del manual se respeta.
3. **`MANUAL_MIGRACION.md` + los tests de dominio como especificación ejecutable** (portar `EstadoReservaTest` primero).

**Correcciones al manual hechas en esta sesión:** (a) nota de honestidad en §1 — el dominio Java **no se copia**, se reimplementa desde los contratos (está soldado a JPA); (b) contrato REST actualizado con los endpoints de las Sesiones 6 (reset de contraseña, export/cierre de tenant, anonimización, estado de notificaciones); (c) esquema de referencia V1–V7.

**Estimación de esfuerzo** (una persona que domine el stack destino): ~3.000 LOC de backend cuyo conocimiento ya está destilado en contratos → **1–2 semanas full-time** para FastAPI/NestJS/Go siguiendo el checklist §7, siendo el test de concurrencia del bloque el único punto técnicamente delicado. El costo real no es escribirlo: es volver a ganar la confianza que dan 49 tests y 6 sesiones de revisiones — por eso la regla de la revisión a 5 años (falla 5.2) sigue vigente: **migrar de lenguaje es el plan B si la deuda de majors se vuelve impagable, no una mejora en sí misma.**

## 6. Acciones derivadas (de más barata a más cara)

1. ✅ **Hecho (Sesión 6):** corregir `MANUAL_MIGRACION.md` (honestidad §1 + contrato actualizado).
2. **Testcontainers (A1)** — próxima sesión de código: 1 test de arranque de contexto + migraciones + 1 test de concurrencia RNF-05. Es la mayor ganancia de confianza por línea de código disponible en el proyecto.
3. **Partir `ExpiracionService` (A2)** y unificar `Clock` (A3) — ventana de mantenimiento de julio 2026.
4. **No hacer:** Onion purista, modular, microservicios, reescritura en otro lenguaje (§4–§5: el plan B existe y está documentado; ejecutarlo hoy sería pagar el costo sin la causa).
