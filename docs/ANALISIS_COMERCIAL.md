# Análisis de Preparación Comercial — ReservaKids

**Fecha:** 2026-06-21
**Alcance:** Estructura, Clean Code, aptitud de la base de datos y causas reales del estancamiento del proyecto.
**Método:** afirmaciones verificadas contra el código fuente (no contra suposiciones). Cada hallazgo cita `archivo:línea`.

> **Nota sobre la versión anterior de este documento.** La edición previa listaba 8 "fallas críticas" que, al contrastarlas con el código, resultaron en su mayoría **inexistentes** (mostraba snippets que no están en el repo). Este documento la reemplaza con un análisis medido. El análisis estructural honesto de referencia es [`REVISION_ARQUITECTURA.md`](REVISION_ARQUITECTURA.md).

---

## RESUMEN EJECUTIVO

ReservaKids es un MVP **bien construido** para un SaaS multi-tenant: arquitectura Onion pragmática, esquema versionado con invariantes en la BD, manejo explícito de concurrencia e idempotencia, cumplimiento de Ley 21.719 y seguridad sólida (JWT con rotación, rate limiting por ruta, webhook con HMAC, credenciales cifradas en reposo).

**La calidad del código no es el cuello de botella del proyecto.** Las fallas técnicas reales son pocas, baratas y se corrigen dentro de la arquitectura actual. Si el proyecto no despegó comercialmente, la causa es de **proceso y negocio** (ver §5), no de ingeniería.

---

## 1. ESTRUCTURA Y ARQUITECTURA — sólida

### 1.1 Fortalezas (verificadas)
- Onion en paquetes: `domain → application → infrastructure`. Controllers delgados, DTOs nunca exponen entidades JPA, excepciones mapeadas en un único `GlobalExceptionHandler`.
- Multi-tenancy estricto: toda query filtra por `tenant_id`; 3 roles (dueño, cliente, admin).
- Ciclo de vida completo: máquina de estados de reserva pura y testeada, jobs de expiración/anonimización/purga, offboarding con export + purga diferida.

### 1.2 Matiz honesto (no es falla)
Es una **Onion de paquetes, no de dependencias**: el dominio importa JPA/Spring y los "puertos" son en su mayoría `JpaRepository`. Para ~6.300 LOC con un mantenedor único, **es la decisión correcta**: la pureza total duplicaría el código sin beneficio real (no hay horizonte de cambiar Postgres por otro motor). Detalle en `REVISION_ARQUITECTURA.md §1`.

### 1.3 Nit estructural real
`TenantService.java:36` inyecta la clase concreta `CredentialCipher` (infraestructura) en vez de un puerto. Es la única fuga de capa. Impacto: cosmético; opcional extraer `CredentialPort`.

---

## 2. CLEAN CODE — bueno, con inconsistencias menores

**Bueno:** comentarios que explican el *porqué* y no el *qué*; lógica de dominio pura y testeada (`EstadoReserva.transicionarA`, `Cliente.normalizarTelefono`); idempotencia y carreras tratadas explícitamente (`ReservaService:104-110`, `:293`).

**Inconsistencias reales (cosméticas):**
- `estado` es `enum` en `Reserva` pero `String` en `Tenant.java:38` y `Pago.java:38` — dos convenciones para el mismo concepto.
- `Clock` inyectado en unos servicios (`AgendaService`) y `OffsetDateTime.now()` directo en otros (`ReservaService`) — mitad del código testeable en el tiempo, mitad no.
- `reserva.comentarios` es multipropósito (notas + `[Cancelación]` + `[Contacto]`): event-log en texto plano. A esta escala no paga normalizarlo a una tabla `reserva_evento`; anotar para v2 si los reportes lo exigen.

---

## 3. BASE DE DATOS — apta para el dominio

19 migraciones Flyway versionadas; `ddl-auto: validate` (el esquema lo gobierna Flyway, nunca Hibernate). Las invariantes críticas viven en la BD, que es lo más valioso del diseño:

- Anti doble-reserva: índice único parcial `ux_reserva_bloque_activa` sobre estados activos (`V1:81`) + `UPDATE ... WHERE estado=DISPONIBLE` atómico (`ReservaService:54`).
- `UNIQUE (tenant_id, fecha, hora_inicio)`, CHECKs de horas/montos, FKs íntegras.
- Optimistic locking (`@Version` en `Reserva`) + advisory lock por día en agenda (`AgendaService:70`).
- Idempotencia de pagos: `ux_pago_ref_externa` único parcial (`V2:16`).

**Limitaciones = escala futura, no aptitud:** sin particionado de `reserva`/`bloque_disponible`, HikariCP con defaults, sin caché. Irrelevantes hasta tener tráfico sostenido; abordar cuando exista la métrica que lo justifique, no antes.

---

## 4. AFIRMACIONES DE LA VERSIÓN ANTERIOR — verificadas

El documento previo marcaba como críticas varias fallas que **no existen en el código**:

| Afirmación previa | Realidad verificada |
|---|---|
| Webhook sin UNIQUE constraint en BD | **Falso** — `ux_pago_ref_externa` (`V2:16`) |
| AgendaService sin lock de concurrencia | **Falso** — `bloquearDia` + re-verificación (`AgendaService:70-75`) |
| N+1 en listados de reservas | **Falso** — resuelto a 3 queries (`ReservaService:117-140`) |
| DTOs sin validación | **Falso** — los 9 DTOs usan `jakarta.validation`; todos los controllers `@Valid` |
| Reserva sin optimistic locking | **Falso** — `@Version` en `Reserva.java` |
| Rate limiting solo en endpoints públicos | **Falso** — `panel-max-por-minuto: 60` (`application.yml:108`) |
| Sin `npm audit` / sin tests de frontend en CI | **Falso** — `ci.yml` corre audit; hay 10 specs Vitest |

**Lección:** validar siempre los hallazgos contra el código antes de planificar trabajo a partir de ellos.

---

## 5. FALLAS REALES (pocas, baratas) y CAUSA DEL ESTANCAMIENTO

### 5.1 Fallas técnicas, por importancia
1. **Las garantías críticas ahora sí se ejecutan en CI.** Antes `ci.yml` corría `mvn test` (solo Surefire), dejando los `*IT` con Testcontainers (arranque de contexto, migraciones, concurrencia RNF-05) fuera del pipeline. **Corregido en esta sesión: `mvn verify`.**
2. God-job por acreción: los jobs de mantenimiento acumulan responsabilidades; partir en `CicloReservaJobs` + `MantenimientoJobs` en ventana de mantenimiento.
3. Inconsistencias `enum`/`String` y `Clock` (§2).

### 5.2 Por qué no despegó (diagnóstico honesto)
Como concluye `REVISION_ARQUITECTURA.md §4`: *ninguna de las ~40 fallas encontradas en las revisiones fue causada por la estructura del código*. El cuello de botella es **proceso y negocio**:
- **Bus factor 1** — un solo desarrollador; deploy manual; sin staging; sin rollback automático.
- **Sin validación de mercado / distribución** — la integración de pago existe, pero no hay señal de tracción, adquisición de clientes ni monetización validada.

El producto está bien construido; lo que faltó es **operación sostenible y go-to-market**, no calidad de ingeniería. Buscar la causa del fracaso en el Clean Code o en la BD sería buscar las llaves bajo el farol.

---

## 6. ACCIONES (de más barata a más cara)
1. ✅ **Hecho:** CI a `mvn verify` (corre los IT de Testcontainers).
2. Partir el god-job y unificar `Clock` (ventana de mantenimiento).
3. Definir un proceso de operación (staging + deploy reproducible) para romper el bus factor 1.
4. **Antes de más ingeniería: validar demanda.** Sin clientes, ninguna mejora técnica cambia el resultado.
