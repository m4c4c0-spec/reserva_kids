# Revisión de fallas a 5 años — ReservaKids

**Fecha:** 2026-06-11 · **Pregunta guía:** *¿por qué fallaría este proyecto en cada uno de los próximos cinco años de operación real (junio 2026 → junio 2031)?*

Continúa [`REVISION_FALLAS_1_ANO.md`](REVISION_FALLAS_1_ANO.md) y [`REVISION_FALLAS_2_ANOS.md`](REVISION_FALLAS_2_ANOS.md). Aquellas encontraron bugs y procesos desconectados — y se corrigieron. A este horizonte el patrón cambia otra vez: **casi ninguna falla nueva es de código**. Son fechas de expiración del stack, mitades operativas que nunca se ejecutan, decisiones de negocio pospuestas y un mantenedor único cuya vida avanza más rápido que el proyecto. El código aguanta cinco años; el proceso alrededor del código es lo que está en duda.

> **Actualización (Sesión 6, 2026-06-11):** las fallas **3.3 y 3.2 están implementadas** — diseñadas iterando año a año contra el resto de las fallas de este documento (ver tablas de cobertura en [`BITACORA.md`](BITACORA.md) Pasos 28 y 30, y el runbook §7 de [`OPERACION.md`](OPERACION.md)). 44/44 tests; migraciones V5 y V6 (esta última validada contra Postgres 16 real). El diagnóstico original se conserva abajo; el estado vigente está en la tabla final.

Severidad: 🔴 puede matar el proyecto · 🟡 daño acotado o acumulativo · 🟢 vigilar.

---

## Año 1 — jun 2026 → jun 2027 · *«Lo operativo pendiente se cobra»*

El código del MVP está corregido y testeado; las fallas de este año son las mitades operativas que las revisiones anteriores dejaron marcadas ⏳ y dos huecos nuevos.

### 1.1 🔴 El repositorio no tiene NI UN COMMIT
- **Síntoma:** `git init` está hecho pero `git log` está vacío: todo el proyecto (3.800 LOC + docs) vive sin trackear en un solo disco. Un `rm` equivocado, un fallo de disco o un editor corrupto borran 4 sesiones de trabajo sin posibilidad de recuperación. Es la falla #7 de backups (a 2 años) aplicada al propio código, **y ya está ocurriendo**.
- **Cuándo:** hoy. No es proyección.
- **Acción:** `git add -A && git commit` + remoto en GitHub (el CI ya lo espera: dispara en `push: main`). Una hora de trabajo elimina el mayor riesgo de todo este documento.

### 1.2 🔴 Los ⏳ del deploy no se ejecutan y diciembre 2026 llega igual
- **Síntoma:** las correcciones de las revisiones previas tienen mitad código (✅ hecha) y mitad operativa (⏳): `rclone config` en el VPS, uptime monitor externo, SPF/DKIM, página de política de privacidad. Sin ellas, las fallas #3, #7 y #8 (2 años) siguen vivas en la práctica aunque el código esté corregido. La **Ley 21.719 entra en vigencia plena en diciembre 2026** — a 6 meses — y la política de privacidad pública es requisito, no adorno.
- **Cuándo:** el día del primer deploy; legal desde dic-2026.
- **Acción:** los runbooks ya existen (`OPERACION.md` §3, §4, §6). Convertirlos en checklist bloqueante del deploy: no hay piloto sin los 4 ítems.

### 1.3 🔴 No existe recuperación de contraseña
- **Síntoma:** `AuthService` expone registrar/login/refresh/logout — nada más. El primer dueño que olvide su clave pierde el acceso a su negocio; el "soporte" es el mantenedor haciendo `UPDATE usuario SET password_hash=...` por SSH contra producción, a mano, sin auditoría. Con 5–10 tenants es cuestión de meses, y cada incidente erosiona la confianza en el panel.
- **Cuándo:** primer olvido real — estadísticamente dentro del año 1.
- **Acción:** flujo de reset por email (token de un solo uso, 30 min, tabla propia o reutilizar la maquinaria de `refresh_token`). El canal email ya existe (`NotificacionAdapter`). Mientras no exista: runbook escrito del reset manual para no improvisarlo.

### 1.4 🟡 Spring Boot 3.4.1 ya está fuera de soporte OSS
- **Síntoma:** la línea 3.4 cerró su soporte OSS en dic-2025 — **el proyecto nace en una versión sin parches**. La línea 3.5 (última 3.x) cierra durante este año; Boot 4 / Framework 7 ya es la corriente. El primer CVE de severidad alta sin backport a 3.4 fuerza una migración bajo presión.
- **Cuándo:** latente ya; crítico al primer CVE explotable (impredecible, históricamente 1–2/año en el ecosistema Spring).
- **Acción:** subir a 3.5.x ahora (cambio menor, lo propone Dependabot) y agendar el salto a Boot 4 en la primera ventana semestral (`OPERACION.md` §1) — en frío es un fin de semana; en caliente, una crisis.

### 1.5 🟢 Tokens en `sessionStorage`: la condición de migrar ya se cumple
- **Síntoma:** el riesgo aceptado a 1 año decía "cookies HttpOnly al tener dominio propio en producción". Al desplegar el piloto la condición se cumple y la deuda queda sin dueño: un XSS (un solo paquete npm comprometido del frontend) exfiltra sesiones.
- **Cuándo:** aceptable durante el año 1; deuda real desde que hay dominio.
- **Acción:** migrar refresh a cookie HttpOnly+SameSite en v1.1; access puede seguir en memoria.

---

## Año 2 — jun 2027 → jun 2028 · *«El producto cobra o muere»*

Cubierto en profundidad por la revisión a 2 años (fallas #1–#12, corregidas). Lo que queda vivo en este horizonte es el modelo de negocio y lo que arrastra.

### 2.1 🔴 La columna `tenant.plan` sigue decorativa y el costo sale del bolsillo
- **Síntoma:** ya señalado (falla #10 a 2 años) con plazo "antes de v1.2": sin límites por plan, sin cobro (RF-13), VPS+dominio+email pagados por un estudiante. Dos años de operación gratis crean un precedente: introducir precio después es perder tenants que entraron gratis. El escenario (b) de aquella revisión — tenants existentes ya sobre los límites — se agrava cada mes que pasa.
- **Cuándo:** la decisión vence con v1.2; el costo de postergarla es compuesto.
- **Acción:** decidir con los criterios ya documentados en `OPERACION.md` §5. Si la respuesta es "esto es un proyecto de título, no un negocio", también es una decisión válida — pero tomarla explícita cambia todo lo demás de este documento (ver 5.1).

### 2.2 🟡 Mercado Pago (P1) entra: el riesgo que la V2 preparó se vuelve real
- **Síntoma:** la tabla `pago` ya soporta idempotencia (`referencia_externa` única) y estados PENDIENTE/CONFIRMADO/RECHAZADO ✅ — pero la integración trae fallas que el esquema no cubre: webhooks que llegan antes que la redirección del usuario, conciliación mensual MP-vs-panel que nadie hace, API de MP que depreca versiones (~cada 2 años), y disputas/contracargos de señas que hoy no tienen estado en el modelo.
- **Cuándo:** desde el primer mes con pagos reales.
- **Acción:** al implementar P1: webhook como única fuente de verdad (la redirección solo informa), job de conciliación mensual (listar pagos MP vs `pago` local), y documentar el flujo de contracargo aunque sea manual.

### 2.3 🟡 Cobrar en Chile = SII: boleta electrónica e inicio de actividades
- **Síntoma:** si 2.1 se resuelve cobrando, cada suscripción exige boleta/factura electrónica (SII) e inicio de actividades — trámites con plazos propios que no aparecen en ningún runbook. Cobrar "mientras tanto sin boleta" es contingencia tributaria personal del mantenedor.
- **Cuándo:** el mes del primer cobro real.
- **Acción:** trámite antes del primer cobro; los emisores de boleta electrónica con API (SII gratuito, o Bsale/Nubox) se integran después — lo legal primero.

### 2.4 🟡 El salto Boot 3→4 ya no es opcional
- **Síntoma:** continuación de 1.4: en este año todas las líneas 3.x quedan sin soporte OSS. El salto toca Framework 7, posibles cambios en Spring Security y compatibilidad de jjwt. Dependabot lo propone como un bump más, pero es migración manual con su checklist.
- **Cuándo:** ventana de enero o julio 2027 — la que tenga más holgura académica.
- **Acción:** ya prevista en `OPERACION.md` §1; este documento solo le pone fecha límite: **antes de jun-2028**.

---

## Año 3 — jun 2028 → jun 2029 · *«El suelo se mueve: EOLs y la primera auditoría de datos»*

### 3.1 🔴 PostgreSQL 16 muere en noviembre 2028 y el volumen no se migra solo
- **Síntoma:** ya anticipado a 2 años (falla #6); ahora tiene trimestre exacto. `pg_upgrade` o dump/restore del volumen `pgdata` con datos de producción multi-tenant, **jamás ensayado**. El modo de falla no es "Postgres deja de funcionar" — es que sigue funcionando sin parches y la migración se posterga indefinidamente porque todo "anda bien"… hasta el primer CVE de Postgres explotable desde la red (y el puerto 5432 del compose está publicado al host — cerrar eso antes, ver README).
- **Cuándo:** EOL nov-2028; la migración debe ensayarse en la ventana de jul-2028.
- **Acción:** ensayo en local con un dump real: `pg_dump` (16) → restore en `postgres:18-alpine` → `mvn test` contra la nueva. El ensayo semestral de restore (ya obligatorio por `OPERACION.md` §3) es el momento natural: mismo dump, dos pájaros.

### 3.2 ✅ IMPLEMENTADA — La anonimización era incompleta: los comentarios de reserva conservaban datos personales
- **Síntoma:** `Cliente.anonimizar()` limpia nombre/teléfono/email del cliente ✅ — pero `ReservaService.crearSolicitudPublica()` escribe `[Contacto: <nombre>]` en `reserva.comentarios` cuando el nombre difiere, y `cancelar()` agrega motivos de cancelación en texto libre (que en la práctica incluirán nombres, teléfonos "me ubica al 9...", direcciones). Esas filas **sobreviven a la anonimización**: el derecho de supresión queda cumplido a medias. Invisible durante años; lo revela la primera solicitud de supresión examinada con cuidado — o la primera fiscalización de la Agencia de Protección de Datos, ya operativa desde dic-2026.
- **Cuándo:** el bug existe desde hoy; el riesgo legal madura con el volumen de datos — a 3 años hay miles de reservas con comentarios.
- **Acción:** (a) `anonimizar()` debe también limpiar/ofuscar los comentarios de las reservas del cliente (`UPDATE reserva SET comentarios = '[anonimizado]' WHERE cliente_id = ?` para las no activas, o regex sobre los marcadores `[Contacto: …]`); (b) migración correctiva para los ya anonimizados; (c) regla de diseño hacia adelante: **ningún dato personal en campos de texto libre** — si se necesita, columna propia que la anonimización conozca.
- **Corrección aplicada (Sesión 6, 2026-06-11):** las tres acciones — (a) `ReservaRepository.anonimizarComentariosDeCliente()` reemplaza los comentarios **enteros** (no regex: lo que escribió el apoderado también es dato personal) con `version + 1` manual (los UPDATE JPQL no pasan por `@Version`) e idempotencia por cláusula `<> placeholder` (falla 4.4); llamado desde los dos caminos de anonimización (`ClienteService` a demanda y `ExpiracionService.anonimizarInactivos()` mensual); (b) `V6__anonimizar_comentarios_residuales.sql` corrige a los ya anonimizados — **validada contra `postgres:16-alpine` real**; (c) regla documentada en `Reserva.COMENTARIOS_ANONIMIZADOS`. Seguro como bulk: ambos caminos exigen "sin reservas activas", así que toda reserva tocada está en estado terminal. Junto con la purga de la 3.3 (que ya cubría a los tenants cerrados), la supresión queda completa. Tests: 44/44.

### 3.3 ✅ IMPLEMENTADA — No existía offboarding de tenant: los negocios que cierran dejaban datos huérfanos
- **Síntoma:** no hay en el código ninguna forma de suspender, exportar ni eliminar un tenant (`grep` de baja/suspensión: cero usos). A 3 años, algún negocio piloto habrá cerrado o abandonado la plataforma. Sus datos — y los de **sus** clientes apoderados — quedan en producción para siempre: el job de anonimización por inactividad cubre a los clientes, pero el tenant (usuarios, servicios, email del dueño) no expira nunca. Además, sin suspensión, un tenant moroso (cuando exista cobro, 2.1) no se puede cortar sin borrarlo.
- **Cuándo:** primer churn real de un tenant — años 2–3.
- **Acción:** v1.2: estado del tenant `ACTIVO/SUSPENDIDO/CERRADO` (el finder público ya filtra por estado ✅, la base existe), export de datos por tenant (JSON/CSV — también es el insumo del cierre responsable, 5.1) y purga diferida (90 días tras CERRADO).
- **Corrección aplicada (Sesión 6, 2026-06-11):** exactamente lo anterior, iterando cada pieza contra las demás fallas del documento — `V5__offboarding_tenant.sql` (estados con CHECK + `cerrado_en`), `GET /api/tenant/export` y `POST /api/tenant/cerrar` (confirmación por slug; rechaza con CONFIRMADAS porque devolver señas es decisión humana; cancela PENDIENTE/COTIZADA liberando bloques; revoca sesiones; devuelve el export final), login/refresh bloqueados si el tenant no está ACTIVO, job mensual `purgarTenantsCerrados()` (purga física en orden FK tras `TENANT_PURGA_DIAS`=90 — supresión real Ley 21.719 que además elimina el residuo de la falla 3.2 para estos tenants, reduce BD/backups (4.2) y es idempotente ante `@Scheduled` duplicado (4.4)), botones de export/cierre en el panel y runbook `OPERACION.md` §7 (suspensión por morosidad, reapertura por arrepentimiento, cierre del servicio completo → adelanta la mitad de 5.1). Tests: `TenantServiceTest` (5), purga (2), auth por estado (2).

### 3.4 🟡 El VPS también envejece: distro y Docker
- **Síntoma:** un Ubuntu 24.04 instalado en 2026 sale de soporte estándar en 2029; Docker Engine y Compose saltan majors. El `do-release-upgrade` de un VPS en producción con la BD dentro es exactamente el tipo de tarea que se posterga hasta que un `apt upgrade` rompe algo.
- **Cuándo:** ventanas de 2028–2029.
- **Acción:** la estrategia barata no es upgrade in-place: es VPS nuevo + restore del backup offsite + cambio de DNS — que además **es el ensayo de disaster recovery** que `OPERACION.md` ya exige. Matar dos pájaros, otra vez.

### 3.5 🟡 La disciplina de las ventanas semestrales decae justo cuando más se necesita
- **Síntoma:** la mitigación central de toda la deuda de stack (fallas #6 a 2 años, 1.4, 2.4, 3.1) es una regla operativa: ventana de mantenimiento en enero y julio. Para 2028–2029 el mantenedor está titulado y probablemente con trabajo: la 5ª y 6ª ventana compiten contra un empleo full-time. Sin ellas, todas las fallas de stack de este documento se acumulan en silencio.
- **Cuándo:** progresivo desde 2028.
- **Acción:** bajar el costo de cada ventana hasta que sea trivial: mergear Dependabot al día (no en lotes), CI verde como única validación, y un recordatorio de calendario externo (no mental). Una ventana de 2 horas se hace; una de 2 días se posterga.

---

## Año 4 — jun 2029 → jun 2030 · *«Envejecimiento silencioso»*

### 4.1 🟡 Java 21 → 25: la última pata del stack original expira
- **Síntoma:** Temurin 21 se acerca al fin de su soporte premier (~2029-2030); Java 25 LTS (sept-2025) es el destino natural. El salto es el más suave de todos (el código no usa nada exótico), pero toca `pom.xml`, el `Dockerfile` (imagen base) y el runner del CI a la vez — y para entonces nadie recuerda dónde están las tres referencias.
- **Cuándo:** ventana de 2029.
- **Acción:** trivial si las ventanas corren (3.5). Anotar las 3 ubicaciones de la versión de Java en `OPERACION.md` §1 hoy, que cuesta una línea.

### 4.2 🟡 Cuatro años de datos: el restore se vuelve lento y la retención no tiene política
- **Síntoma:** `reserva`, `pago` y `cliente` acumulan 4 años. Postgres lo maneja sin drama a esta escala — el problema es alrededor: el dump diario engorda, el **tiempo de restore** crece (la ventana de recuperación ante desastre se alarga sin que nadie la mida), y no existe política de retención de reservas históricas: ¿se guardan para siempre? Si hay pagos, el SII espera 6 años de respaldo contable; la Ley 21.719 empuja a minimizar. Nadie ha escrito el número.
- **Cuándo:** acumulativo; la pregunta de retención vence cuando 2.1/2.3 introduzcan dinero real.
- **Acción:** medir el tiempo de restore en cada ensayo semestral (un número en la bitácora); definir retención de reservas: propuesta — conservar 6 años (alineado a SII), luego borrar; los agregados para reportes (RF-10) se precalculan antes de purgar.

### 4.3 🟡 El frontend deja de compilar tras 18 meses sin tocarlo
- **Síntoma:** el clásico de los proyectos Node dormidos: Vite y Tailwind habrán saltado 2+ majors, Pinia ya va en 3.x, y el `npm ci` de 2030 sobre un `package-lock.json` de 2028 falla por engines de Node o peer-deps. El backend Java envejece con dignidad; el frontend se pudre rápido. El día que haya que tocar una vista con urgencia (un texto legal, un precio), el build no funciona y el fix de 5 minutos toma una tarde.
- **Cuándo:** tras cualquier pausa larga de mantenimiento — probable entre 2029 y 2030.
- **Acción:** el CI ya compila el frontend en cada push ✅ — el truco es que el cron de Dependabot mantenga al menos un push al mes para que el CI detecte la pudrición temprano. Si se decide congelar el producto, congelar también Node con `.nvmrc`/mise y documentar la versión exacta que compila.

### 4.4 🟢 Si hay que escalar a 2ª instancia, dos minas señaladas en 2026 siguen enterradas
- **Síntoma:** los riesgos aceptados a 1 año con condición "al escalar a 2+ instancias": rate limit en memoria (cada instancia con su bucket → límite efectivo x N) y `@Scheduled` duplicado (la expiración corre dos veces; es idempotente, pero la anonimización y las notificaciones podrían duplicar efectos). Si el año 4 trae crecimiento real, la condición se cumple y nadie releerá aquel documento.
- **Cuándo:** solo si hay éxito comercial (que sería buen problema).
- **Acción:** ya documentada en 2026: bucket4j+Redis y ShedLock. Este documento solo reitera dónde está enterrada la mina.

---

## Año 5 — jun 2030 → jun 2031 · *«Sucesión o cierre responsable»*

### 5.1 🔴 Bus factor 1 terminal: el proyecto o se transfiere o se cierra — y no hay plan de cierre
- **Síntoma:** a 5 años el mantenedor tiene una carrera. Los tres destinos posibles: (a) el SaaS genera ingresos que justifican su mantención — requiere que 2.1 se haya resuelto hace 3 años; (b) se transfiere/vende — `MANUAL_MIGRACION.md` ayuda con el código, pero no existe documentación de traspaso operativo (accesos VPS, DNS, SII, claves rclone, cuenta MP); (c) se cierra — y **no existe plan de cierre responsable**: los tenants tienen su operación dentro (calendario, clientes, libro de pagos) y la Ley 21.719 exige disposición correcta de los datos. Apagar el VPS sin más convierte un proyecto digno en un incidente.
- **Cuándo:** la decisión madura entre los años 3 y 5; el plan debe existir antes de necesitarse.
- **Acción:** escribir hoy el runbook de cierre (1 página en `OPERACION.md`): aviso a tenants con 90 días, export de datos por tenant (el mismo de 3.3), ventana de descarga, borrado certificado de BD y backups offsite, liberación del dominio. Lo que se escribe en frío se ejecuta bien en caliente — es la lección repetida de todas las revisiones anteriores.
- **Avance (Sesión 6):** el runbook de cierre del servicio ya existe (`OPERACION.md` §7, escrito junto con la 3.3 — usa su export self-service como mecanismo). Sigue pendiente la documentación de **traspaso** operativo (accesos VPS/DNS/SII/rclone/MP) para el destino (b).

### 5.2 🔴 Deuda de majors compuesta: si las ventanas no corrieron, reescribir es más barato que migrar
- **Síntoma:** el escenario integral de fracaso técnico: las ventanas semestrales (3.5) dejaron de ejecutarse en 2028. Para 2031 el delta acumulado es Boot 3.4→5.x (dos majors), PG 16→19, Java 21→25, frontend que no compila (4.3). Ninguna pieza falló — pero el costo de ponerse al día supera el costo de reescribir, y `MANUAL_MIGRACION.md` pasa de "plan B" a "plan A". Es la muerte por deuda anticipada a 2 años, consumada.
- **Cuándo:** consecuencia, no evento: se decide por omisión entre 2028 y 2030.
- **Acción:** no hay corrección de código posible — la mitigación es 3.5 (ventanas baratas y disciplinadas) más una regla de alarma: **si pasan 2 ventanas seguidas sin ejecutarse, activar 5.1** (decidir transferir o cerrar) en vez de dejar que la entropía decida.

### 5.3 🟡 El mercado decide: saturación regional y competencia sin moat técnico
- **Síntoma:** el nicho (cumpleaños infantiles en Victoria/Malleco/Araucanía) es deliberadamente pequeño — decenas de negocios, no miles. A 5 años, o se saturó (crecer = salir de la región = competir con Agendapro/Reservo y los booking genéricos, que tienen equipos), o los tenants existentes son leales por la relación personal, no por la tecnología. Ninguna falla técnica de este documento importa si esto no se responde.
- **Cuándo:** la señal estará en los números de los años 2–3 (tenants activos, reservas/mes).
- **Acción:** es decisión de negocio, no de código. La única recomendación técnica: mantener el costo operativo cerca de cero (1 VPS, jobs, sin servicios pagados) para que "mantenerlo chico y rentable" sea un final feliz disponible.

### 5.4 🟢 Higiene criptográfica a 5 años
- **Síntoma:** si el runbook de rotación de `JWT_SECRET` (`OPERACION.md` §2) nunca se ejecutó, el mismo secreto HS256 tiene 5 años y ha vivido en N backups y 2 VPS. bcrypt strength 10 sigue siendo razonable en 2031; HS256 también — el riesgo no es el algoritmo sino la vida del secreto.
- **Cuándo:** acumulativo.
- **Acción:** rotar el secreto en cada migración de VPS (3.4 la fuerza naturalmente) — regla simple que se acopla a un evento existente en vez de exigir disciplina nueva.

---

## Síntesis — los 3 modos de muerte a 5 años

1. **Muerte operativa temprana (años 1–2):** el código está corregido pero sus mitades operativas (⏳) nunca se ejecutan: sin commit, sin backup offsite configurado, sin monitor, sin política de privacidad en dic-2026. El proyecto muere "sano" — con todos los tests en verde. Es el modo más probable y el más barato de prevenir: **todo lo crítico del año 1 cuesta menos de un día de trabajo.**
2. **Muerte económica (años 2–3):** `tenant.plan` sigue decorativo, el costo sale del bolsillo del mantenedor, y la decisión de cobrar —cada vez más cara de tomar— se posterga hasta que el interés personal se agota. El proyecto no falla: se abandona.
3. **Muerte por entropía (años 3–5):** las ventanas semestrales decaen con la vida del mantenedor; los EOL (Boot, PG16 nov-2028, distro, Node) se acumulan en silencio hasta que migrar cuesta más que reescribir, y no hay plan de sucesión ni de cierre. La mitigación no es técnica: es la regla de alarma de 5.2 y el runbook de cierre de 5.1, escritos en frío.

**El patrón de fondo:** las revisiones a 1 y 2 años encontraron fallas que se arreglaban con código, y se arreglaron. A 5 años quedan exactamente tres fallas de código (1.3 reset de contraseña, 3.2 anonimización incompleta, 3.3 offboarding de tenant — **3.2 y 3.3 ya implementadas en Sesión 6; solo queda 1.3**) — todo lo demás es calendario, dinero y disciplina. El proyecto ya es mejor que su proceso; el trabajo de los próximos 5 años es que el proceso lo alcance.

## Tabla resumen

| Año | # | Falla | Sev. | Tipo |
|---|---|---|---|---|
| 1 | 1.1 | Repositorio sin ningún commit | 🔴 | Operativa — **hoy** |
| 1 | 1.2 | ⏳ del deploy sin ejecutar; Ley 21.719 dic-2026 | 🔴 | Operativa/legal |
| 1 | 1.3 | Sin recuperación de contraseña | 🔴 | **Código** |
| 1 | 1.4 | Boot 3.4.1 ya sin soporte OSS | 🟡 | Stack |
| 1 | 1.5 | Tokens en sessionStorage con dominio propio | 🟢 | Deuda aceptada que venció |
| 2 | 2.1 | `plan` decorativo; costos del bolsillo | 🔴 | Negocio |
| 2 | 2.2 | Mercado Pago: conciliación, contracargos | 🟡 | Código futuro (P1) |
| 2 | 2.3 | SII: boleta electrónica al cobrar | 🟡 | Legal |
| 2 | 2.4 | Salto Boot 3→4 impostergable | 🟡 | Stack |
| 3 | 3.1 | PostgreSQL 16 EOL nov-2028, migración sin ensayar | 🔴 | Stack |
| 3 | 3.2 | Anonimización no limpia `reserva.comentarios` | ✅ | **Implementada** (Sesión 6) |
| 3 | 3.3 | Sin offboarding/export/purga de tenant | ✅ | **Implementada** (Sesión 6) |
| 3 | 3.4 | Distro del VPS y Docker envejecen | 🟡 | Infra |
| 3 | 3.5 | Disciplina de ventanas semestrales decae | 🟡 | Proceso |
| 4 | 4.1 | Java 21 → 25 | 🟡 | Stack |
| 4 | 4.2 | Restore lento; retención de datos sin política | 🟡 | Datos/legal |
| 4 | 4.3 | Frontend deja de compilar tras pausa larga | 🟡 | Stack |
| 4 | 4.4 | Minas del escalado (rate limit, @Scheduled) | 🟢 | Deuda condicional |
| 5 | 5.1 | Sin plan de sucesión ni cierre responsable | 🟡 | Parcial: runbook de cierre ✅ (§7, Sesión 6); falta doc de traspaso operativo |
| 5 | 5.2 | Deuda de majors compuesta → reescritura | 🔴 | Consecuencia de 3.5 |
| 5 | 5.3 | Saturación del nicho regional | 🟡 | Negocio |
| 5 | 5.4 | Secreto JWT de 5 años sin rotar | 🟢 | Higiene |

**Acciones inmediatas derivadas (orden de costo/beneficio):** (1) commit inicial + remoto — hoy; (2) checklist bloqueante de deploy con los ⏳; (3) flujo de reset de contraseña; ~~(4) fix de anonimización en comentarios~~ ✅ hecho (3.2, Sesión 6 — `V6` + ambos caminos de anonimización); ~~(5) runbook de cierre~~ ✅ hecho con la 3.3 (`OPERACION.md` §7).
