# Revisión de fallas a 2 años — ReservaKids

**Fecha:** 2026-06-10 · **Pregunta guía:** *¿por qué fallaría este proyecto de aquí a dos años de operación real (junio 2028)?*

Continúa la revisión a 1 año ([`REVISION_FALLAS_1_ANO.md`](REVISION_FALLAS_1_ANO.md)). A diferencia de aquella, varias fallas de este horizonte no son bugs puntuales sino **procesos que nunca corren, deuda que se acumula y soporte que expira**. La migración `V2__pagos_estado_cotizada_y_ley_21719.sql` preparó el terreno para las fallas #1, #2, #4 y #5, pero la implementación había quedado incompleta.

> **Actualización (Sesión 4, mismo día):** todas las correcciones de código fueron implementadas y testeadas (23/23 tests, build frontend OK) — ver [`BITACORA.md`](BITACORA.md) Sesión 4 y los runbooks operativos en [`OPERACION.md`](OPERACION.md). El texto de cada falla conserva el diagnóstico original; el estado vigente está en la tabla final.

---

## Fallas encontradas

### 1. ✅ CORREGIDA — Cotizaciones que nunca expiran: el calendario se pudre

- **Síntoma a futuro:** una solicitud `PENDIENTE` expira a las 48 h, pero una `COTIZADA` cuyo cliente desapareció retiene su bloque `EN_ESPERA` **para siempre**. Con tenants reales operando 2 años: decenas de sábados "ocupados" por cotizaciones fantasma de hace meses. El dueño pierde ventas sin saberlo, o aprende a desconfiar del calendario y lo gestiona por WhatsApp — el producto se vuelve decorativo.
- **Cuándo:** acumulativo; doloroso a los 6–18 meses de operación con tráfico real.
- **Corrección aplicada (Sesión 4):** `ExpiracionService.expirarCotizadas()` — job horario que cancela las `COTIZADA` con más de `COTIZACION_EXPIRACION_DIAS` (14 por defecto) y devuelve el bloque a `DISPONIBLE`, anotando `[Expiración]` en comentarios. Test: `cancelaCotizadasSinRespuestaYLiberaBloques`.

### 2. ✅ CORREGIDA — El estado `REALIZADA` era inalcanzable: las reservas quedaban "activas" para siempre

- **Síntoma a futuro:** la máquina de estados define `CONFIRMADA → REALIZADA`, pero **no existe ningún endpoint, método de servicio ni job que ejecute esa transición** (`grep REALIZADA` en `web/` y `usecase/` devuelve cero usos). Toda fiesta ya celebrada queda `CONFIRMADA` eternamente. Consecuencias en cascada:
  1. `EstadoReserva.ACTIVOS` incluye `CONFIRMADA` → `existsByClienteIdAndEstadoIn(ACTIVOS)` considera a ese cliente "con reservas activas" **para siempre** → la anonimización por inactividad (falla #4) **nunca podrá ejecutarse** para ningún cliente que haya concretado una fiesta. Incumplimiento estructural de la Ley 21.719.
  2. Los reportes de v1.1 (RF-10) sobre estados serán basura: 0 reservas realizadas en 2 años de operación.
  3. El filtro `?estado=` del panel mezcla lo vigente con lo histórico.
- **Cuándo:** invisible hoy; bloqueaba la falla #4 desde diciembre 2026 y los reportes desde v1.1.
- **Corrección aplicada (Sesión 4):** doble vía — `PUT /api/reservas/{id}/realizar` (botón "Marcar realizada 🎉" en el panel) y barrido diario `ExpiracionService.realizarConcluidas()` (04:15) que cierra las `CONFIRMADA` cuyo bloque ya pasó, usando el `Clock` del negocio. Tests: `realizarCierraUnaReservaConfirmada`, `marcaRealizadasLasConfirmadasConFechaPasada`.

### 3. ✅ CORREGIDA — Degradación silenciosa del email: el canal principal muere y nadie se entera

- **Síntoma a futuro:** `NotificacionAdapter` degrada con elegancia (sin `MAIL_HOST` loguea; un fallo SMTP es try/catch + log). Correcto para no romper la reserva — pero en 2 años *alguna* de estas pasará: la API key de Brevo expira o se revoca, la capa gratis cambia de límites, el dominio sin SPF/DKIM alineado empieza a caer a spam, o el password rota y nadie actualiza el `.env`. Resultado: **el dueño deja de recibir avisos de solicitudes nuevas y no existe ninguna señal de que eso pasó** — ni health check (no hay actuator en el `pom.xml`), ni alerta, ni contador de fallos en el panel. Las solicitudes expiran a las 48 h sin respuesta; los clientes concluyen que el negocio no responde. Es la falla #7 de la revisión a 1 año (email fantasma) invertida: ya no llega un email de más, dejan de llegar todos.
- **Cuándo:** probabilidad por mes baja, casi segura en 24 meses.
- **Corrección aplicada (Sesión 4):** (a) `NotificacionAdapter` cuenta fallos consecutivos y expone `GET /api/sistema/notificaciones`; el panel muestra un aviso ámbar si hay fallos; (b) `spring-boot-starter-actuator` con `/actuator/health` público sin detalles + healthcheck del backend en docker-compose. Operativo pendiente (runbook §4 de `OPERACION.md`): crear el uptime monitor y alinear SPF/DKIM al tener dominio.

### 4. ✅ CORREGIDA — Ley 21.719: la maquinaria de cumplimiento existía pero nunca corría

- **Síntoma a futuro:** la ley entra en **vigencia plena en diciembre 2026** (6 meses desde hoy) con multas administrables por la Agencia de Protección de Datos. El proyecto guarda nombre/teléfono/email de apoderados de toda La Araucanía. Hoy: `cliente.consentimiento_en` se registra ✅, pero la retención por inactividad y el derecho de supresión son letra muerta.
- **Corrección aplicada (Sesión 4)** sobre la base V2 (columnas, `Cliente.anonimizar()`, finders):
  - Job mensual `ExpiracionService.anonimizarInactivos()` (día 1, 05:00): anonimiza clientes sin actividad por `RETENCION_CLIENTE_MESES` (24 por defecto), saltando a quien tenga reservas activas — desbloqueado por la corrección de la falla #2.
  - Supresión a demanda: `ClienteService.anonimizar()` + `POST /api/clientes/{id}/anonimizar` (idempotente; 400 si hay reservas activas; aislado por tenant).
  - Checkbox de consentimiento obligatorio en el formulario público (el backend ya lo exigía con `@AssertTrue` — **sin este parche toda solicitud pública fallaba con 400**, bug heredado de la sesión inconclusa).
  - `linkWhatsApp()` devuelve `null` para clientes anonimizados.
  - Tests: `anonimizaInactivosSinReservasActivas` + `ClienteServiceTest` (4 tests).
- **Pendiente operativo:** página de política de privacidad al tener dominio (runbook §6 de `OPERACION.md`).
- **Cuándo:** riesgo legal desde dic-2026; reputacional desde el primer apoderado que pida ser borrado y no se pueda.

### 5. ✅ CORREGIDA — `pago` no soportaba devoluciones, idempotencia de pasarela ni auditoría

- **Síntoma a futuro:** en 2 años el plan P1 (Mercado Pago) es presente, no futuro. El modelo original de `pago` (solo monto/medio/fecha) obligaba a: borrar o editar filas para registrar una devolución de seña (adiós trazabilidad contable), no tenía cómo deduplicar webhooks de MP (los reenvía duplicados y desordenados → señas dobles) y no registraba quién anotó un pago manual (disputas dueño/cliente sin evidencia).
- **Corrección (V2, implementada):** `tipo` ABONO/DEVOLUCION (libro contable: una devolución es fila nueva, nunca UPDATE), `estado` PENDIENTE/CONFIRMADO/RECHAZADO (manual nace CONFIRMADO; la pasarela usará PENDIENTE), `referencia_externa` con índice único parcial (idempotencia de webhooks), `registrado_por` (auditoría). `PagoRepository.totalPagado()` suma solo CONFIRMADOS y resta devoluciones. `ReservaService.registrarPago()` permite devolución también sobre `CANCELADA` (devolver la seña de un cumpleaños cancelado).

### 6. 🔴 NUEVA — Horizonte de soporte 2026→2028: el stack expira por debajo del proyecto

- **Síntoma a futuro:**
  - **Spring Boot 3.4.1**: el soporte OSS de la línea 3.4 **ya terminó** (dic-2025). Para 2028 el proyecto estará 2+ líneas atrás, con Boot 4 / Framework 7 como corriente. Dependabot propone bumps de versión, pero un salto de major (Boot 3→4) es una migración manual que nadie agenda → llegará el día en que un CVE solo tenga fix en una versión a la que no se puede saltar sin un fin de semana de trabajo… en semana de exámenes (riesgo #5 del SDLC).
  - **PostgreSQL 16**: EOL **noviembre 2028**, justo dentro del horizonte. El volumen `pgdata` no se migra solo: `pg_upgrade` o dump/restore manual, que conviene ensayar antes de que sea urgente.
  - **Frontend**: Vite 6, Tailwind 4, Pinia 2 — para 2028 habrán saltado 1–2 majors cada uno; los majors de Vite suelen romper config.
  - **Java 21 LTS**: ✅ sin riesgo (Temurin lo soporta más allá de 2028).
- **Cuándo:** acumulativo; crítico al primer CVE sin backport.
- **Mitigación:** regla operativa, no código — **ventana de mantenimiento semestral** (enero y julio): checklist completo en `OPERACION.md` §1.

### 7. ✅ CORREGIDA — Backups: a 2 años "aceptado por ahora" se vuelve negligencia

- **Síntoma a futuro:** ya señalado a 1 año (riesgo aceptado "antes del piloto") y sigue igual: `backup_db.sh` deja los dumps **en el mismo disco del VPS** y **jamás se ha ensayado un restore**. En 24 meses de operación multi-tenant la probabilidad de un incidente de disco/proveedor/`rm` equivocado deja de ser despreciable — y el modo de falla es total: mueren los datos de *todos* los negocios a la vez, más sus backups. Un backup no probado no es un backup: es una esperanza.
- **Corrección aplicada (Sesión 4):** `backup_db.sh` sube el dump vía `rclone` si `RCLONE_REMOTE` está definido (falla con ruido si no puede — un cron en rojo es mejor que una falsa sensación de respaldo); runbook de restore en el propio script y ensayo semestral obligatorio en `OPERACION.md` §1/§3. Operativo pendiente: `rclone config` en el VPS al desplegar.

### 8. ✅ CORREGIDA — Cero observabilidad: las caídas las descubrían los clientes

- **Síntoma a futuro:** no hay `spring-boot-starter-actuator`, el servicio `backend` del `docker-compose.yml` no tiene healthcheck (la BD sí), no hay uptime monitor ni alertas. La JVM puede morir por OOM un viernes y el sitio público estar caído todo el fin de semana — el horario exacto en que los apoderados cotizan. A 2 años, las horas de caída no detectada se acumulan directamente como reservas perdidas y churn de tenants.
- **Corrección aplicada (Sesión 4):** actuator (`/actuator/health`, expuesto sin auth y sin detalles) + healthcheck del backend en compose (una JVM muerta ahora se reinicia sola con `restart: unless-stopped`). Operativo pendiente: uptime monitor externo (runbook §4 de `OPERACION.md`).

### 9. ✅ CORREGIDA — Teléfonos sin normalizar: links de WhatsApp rotos e identidad duplicada

- **Síntoma a futuro:** `linkWhatsApp()` limpia a dígitos (`replaceAll("[^0-9]")`) ✅, pero si el apoderado escribe `912345678` (sin `+56`, lo habitual en Chile) el link `wa.me/912345678` apunta a un número inválido/de otro país. Además el cliente se deduplica por `(tenant, telefono)` exacto: `+56912345678`, `56912345678` y `912345678` crean **tres clientes distintos**, fragmentando el historial (RF-12, ficha de cliente) y la base del plazo de retención de la falla #4.
- **Cuándo:** intermitente desde el día 1; como deuda de datos, irreversible-sin-migración a los 2 años.
- **Corrección aplicada (Sesión 4):** `Cliente.normalizarTelefono()` (E.164 chileno sin `+`: `9XXXXXXXX` → `569XXXXXXXX`) aplicado al crear/buscar cliente en `ReservaService`; migración `V3__normalizar_telefonos.sql` limpia los existentes (excluye anonimizados).

### 10. 🟡 NUEVA — La columna `tenant.plan` es decorativa: el modelo de negocio no existe en el código

- **Síntoma a futuro:** `plan` (default `BASICO`) no se lee en ninguna parte: sin límites de servicios/bloques/reservas, sin cobro (RF-13, P1). Dos escenarios de fracaso a 2 años: (a) el SaaS opera gratis indefinidamente y el costo VPS+dominio+email sale del bolsillo de un estudiante hasta que deja de salir; (b) al introducir límites, los tenants existentes ya los superan y la conversión a pago se vuelve una negociación uno a uno.
- **Cuándo:** decisión de negocio para v1.2; el costo de postergarla crece con cada tenant.
- **Estado:** decisión documentada con criterios y fecha límite (antes de v1.2) en `OPERACION.md` §5.

### 11. ✅ CORREGIDA — Crecimiento de datos sin política de archivado

- **Síntoma a futuro:** `bloque_disponible` (uno por slot ofrecido, ~cientos/mes/tenant) y `reserva` crecen sin límite ni partición. A 2 años con 10–20 tenants: decenas de miles de bloques pasados (muchos aún `DISPONIBLE`/`EN_ESPERA`, ruido ya señalado a 1 año), panel listando con `ORDER BY creada_en DESC` sin índice dedicado, backups engordando. Postgres lo aguanta sin drama a esta escala — es ruido, no incendio — pero el barrido de bloques pasados (limpieza v1.1) conviene hacerlo antes de que la tabla sea grande.
- **Corrección aplicada (Sesión 4):** job semanal `ExpiracionService.limpiarBloquesPasados()` (lunes 04:45): elimina bloques `DISPONIBLE` con fecha pasada y **sin ninguna reserva que los referencie** (los referenciados se conservan por la FK y la trazabilidad). `reserva` no se archiva: a esta escala no lo necesita.

### 12. 🟡 NUEVA — `JWT_SECRET` único, eterno y sin procedimiento de rotación

- **Síntoma a futuro:** HS256 con un solo secreto sin `kid`. En 2 años el `.env` habrá pasado por respaldos, copies a otro VPS, quizá un pastebin de debugging. Si hay que rotarlo, hoy el procedimiento es "cambiarlo y echar a todos los usuarios a la vez" — tolerable (sesiones de 15 min + refresh 7 días) pero indocumentado, así que en el momento de pánico no se hará bien.
- **Corrección aplicada (Sesión 4):** runbook de rotación documentado en `OPERACION.md` §2 (cambiar secreto → reiniciar → todas las sesiones mueren a la vez, aceptable a esta escala). Doble secreto (`kid`) solo si algún día hay SSO/terceros.

---

## Por qué fallaría — síntesis (los 3 modos de muerte más probables)

1. **Muerte por silencio (falla #3 + #8):** el email deja de llegar o el servicio se cae fuera de horario; nadie lo detecta; los tenants pierden reservas, culpan a la plataforma y vuelven al cuaderno y WhatsApp. Es el modo más probable porque no requiere que nada "se rompa" — solo que nadie mire.
2. **Muerte legal/confianza (falla #2 + #4):** diciembre 2026 llega en 6 meses; la maquinaria de la Ley 21.719 está construida pero desconectada, y la falla #2 la bloquea estructuralmente. Una denuncia de un apoderado basta.
3. **Muerte por deuda (falla #6 + #7 + bus factor 1):** el mantenedor único entra en práctica/trabajo, Dependabot acumula 30 PRs sin mergear, Boot 3.4 queda sin parches, un disco falla y el backup estaba en el mismo disco. Ninguna de estas mata sola; a 2 años, juntas sí.

## Estado de implementación (tras Sesión 4)

| # | Falla | Estado |
|---|---|---|
| 1 | Cotizaciones no expiran | ✅ Job horario `expirarCotizadas()` + `COTIZACION_EXPIRACION_DIAS` |
| 2 | `REALIZADA` inalcanzable | ✅ `PUT /reservas/{id}/realizar` + barrido diario + botón en panel |
| 3 | Email muere en silencio | ✅ Contador + `GET /api/sistema/notificaciones` + aviso en panel · ⏳ uptime monitor al desplegar |
| 4 | Ley 21.719 sin ejecución | ✅ Job mensual + `POST /clientes/{id}/anonimizar` + checkbox UI · ⏳ política de privacidad con dominio |
| 5 | `pago` sin devoluciones/idempotencia/auditoría | ✅ Corregida (V2 + código; controller ahora pasa `registrado_por`) |
| 6 | Stack fuera de soporte 2026–2028 | ✅ Runbook ventana semestral (`OPERACION.md` §1) — requiere ejecutarse |
| 7 | Backups en el mismo VPS, restore jamás probado | ✅ rclone offsite en el script + runbook restore · ⏳ `rclone config` en el VPS |
| 8 | Sin observabilidad | ✅ Actuator + healthcheck Docker · ⏳ uptime monitor al desplegar |
| 9 | Teléfonos sin normalizar | ✅ `normalizarTelefono()` + migración V3 |
| 10 | `plan` decorativo, sin modelo de ingresos | 📋 Decisión de negocio documentada con plazo (`OPERACION.md` §5) |
| 11 | Datos crecen sin archivado | ✅ Job semanal `limpiarBloquesPasados()` |
| 12 | Rotación de `JWT_SECRET` indocumentada | ✅ Runbook (`OPERACION.md` §2) |

**Verificación:** `mvn test` 23/23 ✅ (eran 14; +9 de los parches y la sesión inconclusa) · `vite build` ✅.
⏳ = paso operativo al momento del deploy, no de código.
