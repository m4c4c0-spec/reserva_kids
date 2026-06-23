# Personas UX — ReservaKids

**Fecha:** 2026-06-22
**Autor:** Investigación UX (toolkit ux-researcher-designer)
**Estado:** 🟡 **PROVISIONAL (proto-personas basadas en supuestos)**

> ⚠️ **Lectura honesta de la confianza.** Estas personas **NO** se derivan de
> research real (entrevistas, analítica, encuestas): a la fecha el proyecto no
> tiene datos de usuarios (ver [`ANALISIS_COMERCIAL.md §5.2`](ANALISIS_COMERCIAL.md):
> *"sin validación de mercado / distribución… no hay señal de tracción"*).
>
> Son **proto-personas**: hipótesis estructuradas, ancladas en el dominio real del
> producto y en su contexto geográfico (Victoria, Malleco y La Araucanía), pensadas
> para **dirigir las primeras entrevistas**, no para reemplazarlas. Cada supuesto
> está etiquetado `[SUPUESTO]` y listado en §3 para que la validación lo refute o
> confirme. **Confianza: Baja.** No usar para decisiones irreversibles de producto
> hasta validar con ≥5 dueños y ≥8 padres reales.
>
> Esto encaja con la acción #4 del análisis comercial: *"antes de más ingeniería,
> validar demanda"*. Estas personas son el instrumento para esa validación.

---

## Tabla de contenido

- [1. Persona primaria — Carolina, la Dueña del negocio](#1-persona-primaria--carolina-la-dueña-del-negocio)
- [2. Persona secundaria — Daniela, la Mamá que reserva](#2-persona-secundaria--daniela-la-mamá-que-reserva)
- [3. Registro de supuestos a validar](#3-registro-de-supuestos-a-validar)
- [4. Plan de validación (cómo pasar de Baja a Alta confianza)](#4-plan-de-validación-cómo-pasar-de-baja-a-alta-confianza)
- [5. Implicaciones de diseño priorizadas](#5-implicaciones-de-diseño-priorizadas)

---

## 1. Persona primaria — Carolina, la Dueña del negocio

> **Por qué es la primaria:** es quien **paga** el SaaS y cuya decisión de adopción
> determina si ReservaKids vive. El diagnóstico comercial señala que el cuello de
> botella es la adquisición de este perfil, no la calidad técnica.

```
============================================================
PERSONA: Carolina, la Dueña del negocio
============================================================

📝 Microempresaria de cumpleaños infantiles que hoy gestiona TODO por
   WhatsApp e Instagram y pierde reservas por desorden, no por falta de demanda.

Arquetipo:  Business owner / Mobile-first (no power user de software)
Cita:       "Si me llaman dos por el mismo sábado, una la pierdo sí o sí.
             Yo no soy de sistemas, necesito algo que no me complique."

👤 Demografía  [SUPUESTO]
  • Edad:               32–45
  • Ubicación:          Ciudades chicas del sur (Victoria, Angol, Temuco)
  • Negocio:            1–3 personas (a menudo ella sola o con su pareja)
  • Rubro:              Salón de eventos / arriendo de juegos / animación
  • Proficiencia tech:  Básica–Intermedia. Experta en WhatsApp/Instagram,
                        ZERO en "paneles de administración"
  • Dispositivo:        Teléfono Android, plan de datos. El PC es secundario.

🧠 Psicográfico
  Motivaciones:  No perder ventas, verse profesional frente a la competencia,
                 dejar de trabajar de noche contestando mensajes
  Valores:       Confianza/cercanía con el cliente, simplicidad, plata en mano
  Actitud:       Escéptica de "sistemas" — la quemaron apps que prometieron y
                 nunca usó. Cree más en el boca a boca que en el marketing.
  Estilo de vida: Ocupada, reactiva, decide rápido, sin tiempo para tutoriales

🎯 Metas y necesidades
  • Tener UN lugar con la agenda real del mes (qué sábado está libre)  [SUPUESTO]
  • No agendar dos fiestas en el mismo bloque (su miedo #1)
  • Cobrar y registrar la SEÑA sin enredos contables
  • Que el cliente vea precios/disponibilidad sin tener que escribirle ella
  • Verse seria y confiable (página propia) sin pagar a un diseñador

😤 Frustraciones  [SUPUESTO — base para entrevistas]
  • La agenda vive en su cabeza + capturas de WhatsApp → doble-reserva
  • Contesta los mismos "¿cuánto vale?" y "¿tienen el 15?" 20 veces al día
  • Pierde el hilo de quién pagó seña y quién no
  • Onboarding de cualquier software le da miedo: "¿y si lo hago mal?"
  • Desconfía de meter datos de menores en un sistema (Ley 21.719)

📊 Comportamiento esperado
  • Entraría 2–4 veces al día, sesiones cortas, desde el teléfono
  • Usaría 3 cosas: ver agenda, confirmar/cancelar reserva, marcar seña
  • NO usaría: reportes, configuración avanzada, exportes
  • Aprende viendo, no leyendo. Necesita un primer "éxito" en <5 min

💡 Implicaciones de diseño
  → Mobile-first REAL: el panel del dueño debe funcionar con una mano
  → Onboarding de 1 fiesta de ejemplo → "agéndala y mira cómo se ve"
  → Anti-doble-reserva visible y explicado (ya existe en BD; mostrarlo)
  → Botón directo a WhatsApp `wa.me` para responder con disponibilidad
  → Lenguaje humano, cero jerga ("solicitudes" → "pedidos de fiesta")
  → Mostrar el estado de la seña de un vistazo (pagada / pendiente)

📈 Datos: Basado en 0 usuarios reales — PROTO-PERSONA
    Confianza: BAJA (provisional)
    Método pendiente: ≥5 entrevistas a dueños + analítica de uso del panel
```

### Escenario clave: "Sábado disputado"

```
Contexto: Martes, 18:00. Le llegan por Instagram dos consultas para el
          mismo sábado de noviembre.
Meta:     No prometer a las dos. Cerrar a la que pague seña primero.

Recorrido HOY (sin ReservaKids):
  1. Revisa capturas de pantalla y su libreta  → no está segura
  2. Le dice "sí" a las dos para no perderlas
  3. Una paga, la otra también quiere → conflicto, mala reputación

Dolores:
  • La verdad de la agenda no existe en ningún lugar único
  • Decide bajo presión, de memoria

Oportunidad ReservaKids:
  • La agenda única + el bloqueo atómico ya garantizan que solo una
    reserva activa por bloque (BD ya lo hace). El reto es de UX/confianza:
    que Carolina CREA en la pantalla más que en su libreta.
```

---

## 2. Persona secundaria — Daniela, la Mamá que reserva

> **Por qué es secundaria:** no paga el SaaS, pero su experiencia de
> cotización/reserva es lo que **convierte** (o no) en el sitio público del
> negocio. Si Daniela abandona, Carolina pierde la venta y culpa a ReservaKids.

```
============================================================
PERSONA: Daniela, la Mamá que reserva
============================================================

📝 Mamá ocupada que organiza el cumpleaños de su hijo entre el trabajo y
   la casa; quiere cotizar y reservar rápido, de noche, desde el celular.

Arquetipo:  Casual / Mobile-first
Cita:       "Le mandé mensaje a tres salones y me respondió uno al otro día.
             Reservé en el primero que me dio precio y fecha al toque."

👤 Demografía  [SUPUESTO]
  • Edad:               28–42
  • Ubicación:          Misma zona que el negocio (cliente local)
  • Rol:                Madre/padre o familiar organizando la fiesta
  • Proficiencia tech:  Intermedia — compra online, usa apps a diario
  • Dispositivo:        Celular, 90%+. Navega de noche cuando los niños duermen
  • Contexto:           Comparando 2–3 negocios a la vez

🧠 Psicográfico
  Motivaciones:  Que la fiesta salga linda, no estresarse, no gastar de más
  Valores:       Rapidez de respuesta, transparencia de precio, confianza
  Actitud:       Impaciente con la fricción; si no hay precio, se va al otro
  Estilo de vida: Multitarea, decide en ventanas de 10 minutos

🎯 Metas y necesidades
  • Ver si la fecha que quiere está disponible — sin tener que preguntar
  • Saber el precio (o un rango) antes de comprometerse
  • Reservar/pedir cotización en pocos pasos, desde el teléfono
  • Recibir confirmación clara de que su pedido llegó
  • Sentir que el negocio es serio y su plata (seña) está segura

😤 Frustraciones  [SUPUESTO — base para test de usabilidad]
  • "Consulta disponibilidad por WhatsApp" y nadie responde hasta mañana
  • Formularios largos que piden datos que no entiende para qué
  • No saber el precio hasta que alguien le escriba
  • Miedo a transferir la seña a un desconocido sin respaldo
  • Dudas sobre qué pasa con los datos de su hijo (menor de edad)

📊 Comportamiento esperado
  • Una sola sesión, objetivo único: cotizar/reservar una fecha
  • Decide en minutos; abandona si hay más de ~3 pasos o piden registro
  • No vuelve si la primera vez fue confusa (no hay segunda oportunidad)

💡 Implicaciones de diseño
  → Disponibilidad y precio VISIBLES sin registro ni "escríbenos"
  → Flujo de reserva corto, sin crear cuenta para pedir cotización
  → Confirmación inmediata + link `wa.me` para hablar con el negocio
  → Señales de confianza: datos del negocio, qué pasa con la seña, RUT
  → Aviso claro y breve de privacidad (Ley 21.719) sin asustar
  → Todo pensado para pulgar: campos grandes, mínimo tipeo
```

### Journey: Reservar de noche desde el sofá

```
Etapa 1: Descubrimiento
  Acción:  Llega al sitio del negocio (Instagram → link en bio)
  Emoción: 🙂 Esperanzada    Pensamiento: "A ver si este sí tiene precios"

Etapa 2: Evaluación
  Acción:  Busca su fecha y el precio
  Emoción: 😀 si los ve / 😕 si dice "consultar"
  Punto crítico: aquí se gana o se pierde la conversión

Etapa 3: Reserva / Cotización
  Acción:  Completa el pedido para su fecha
  Emoción: 😟 ansiosa al poner sus datos / pagar seña
  Punto crítico: fricción del formulario + confianza en la seña

Etapa 4: Confirmación
  Acción:  Recibe confirmación (pantalla + email + link WhatsApp)
  Emoción: 😌 aliviada    Pensamiento: "Listo, una cosa menos"
```

---

## 3. Registro de supuestos a validar

Cada `[SUPUESTO]` de arriba, consolidado y priorizado por riesgo. Un supuesto de
**riesgo alto** es uno que, si es falso, invalida decisiones de producto.

| # | Supuesto | Persona | Riesgo si es falso | Cómo se valida |
|---|----------|---------|--------------------|----------------|
| A1 | El dolor #1 del dueño es la doble-reserva / agenda en la cabeza | Carolina | **Alto** — si el dolor real es otro (p.ej. marketing/captación), el producto ataca el problema equivocado | Entrevistas: "cuéntame la última vez que perdiste o enredaste una reserva" |
| A2 | El dueño gestiona hoy por WhatsApp/Instagram, sin software | Carolina | Medio — cambia el "antes" contra el que competimos | Entrevista contextual: "muéstrame cómo llevas tu agenda" |
| A3 | El dueño es mobile-first y rechaza paneles complejos | Carolina | **Alto** — define toda la estrategia de UI | Observación: pedir que agende una fiesta de prueba |
| A4 | El padre abandona si no ve precio/disponibilidad sin preguntar | Daniela | **Alto** — define el sitio público y la conversión | Test de usabilidad + analítica de embudo |
| A5 | El padre no quiere crear cuenta para cotizar | Daniela | Medio — define el flujo de reserva | Test A/B futuro / test moderado |
| A6 | La seña genera desconfianza y necesita señales de respaldo | Daniela | Medio | Entrevistas post-tarea: "¿transferirías la seña aquí? ¿por qué?" |
| A7 | La privacidad de datos de menores es una preocupación activa | Ambas | Bajo–Medio | Preguntar sin inducir; ver si emerge espontáneamente |

> **Regla:** ningún supuesto de riesgo **Alto** debe convertirse en feature
> grande antes de validarse. Validar A1, A3 y A4 primero — son los baratos de
> testear y los caros de equivocar.

> ✅ **A1/A3/A4 ya pasaron por validación heurística + testeo funcional** (no
> con usuarios todavía): ver [`VALIDACION_UX.md`](VALIDACION_UX.md). Resumen:
> A1 confirmado como gap de UX, A3 refutado en layout (el panel ya es
> mobile-first) y reorientado a carga cognitiva, A4 confirmado con matiz (falta
> directorio público sin login). Las claims **conductuales** siguen pendientes
> de research.

---

## 4. Plan de validación (cómo pasar de Baja a Alta confianza)

Objetivo: reemplazar `[SUPUESTO]` por evidencia con conteo de frecuencia.

### Fase 1 — Dueños (Carolina) · 1–2 semanas
- **Método:** 5–8 entrevistas semiestructuradas (45 min) + observación contextual.
- **Reclutamiento:** dueños de salones/animación de la zona vía Instagram/boca a boca.
- **Guion (no inductivo):**
  - "Camíname por cómo manejas las reservas de un mes típico."
  - "Cuéntame la última vez que algo salió mal con una reserva."
  - "¿Qué pasa cuando dos familias quieren el mismo día?"
  - "¿Has probado algún sistema/app antes? ¿Qué pasó?"
- **Señal de éxito:** ≥3/5 mencionan agenda/doble-reserva espontáneamente (valida A1).

### Fase 2 — Padres (Daniela) · 1 semana
- **Método:** test de usabilidad moderado remoto, 5 participantes, sobre el sitio
  público real o un prototipo.
- **Tarea (escenario, no instrucción):**
  > "Tu hija cumple años el tercer sábado de noviembre. Quieres ver si este
  > negocio está disponible y cuánto costaría. Hazlo como lo harías en tu casa."
- **Métricas:** tasa de finalización >80%, tiempo a "veo precio" <60 s,
  abandono en el formulario, SUS/satisfacción.
- **Señal de éxito:** identificar dónde abandonan (valida A4/A5).

### Fase 3 — Síntesis
- Recodificar estas personas con datos reales: cambiar "Confianza: BAJA" a Media
  (11–30 fuentes) o Alta (31+), agregar citas reales y conteos `X/Y`.
- Actualizar este documento; archivar la versión provisional.

---

## 5. Implicaciones de diseño priorizadas

Cruce de ambas personas. Prioridad = Frecuencia esperada × Severidad × Solucionable.
**Marcadas las que ya están soportadas por el backend** (solo falta exponerlas en UX).

| Prioridad | Implicación | Persona | Estado backend |
|-----------|-------------|---------|----------------|
| 🔴 Alta | Disponibilidad + precio visibles sin "escríbenos" | Daniela | Catálogo + agenda existen; falta UX pública clara |
| 🔴 Alta | Panel del dueño usable con una mano (mobile-first) | Carolina | Por verificar en frontend |
| 🔴 Alta | Anti-doble-reserva **visible y creíble** en la UI | Carolina | ✅ Garantizado en BD (`ux_reserva_bloque_activa`) — falta comunicarlo |
| 🟠 Media | Reserva/cotización en ≤3 pasos, sin crear cuenta | Daniela | Por verificar flujo actual |
| 🟠 Media | Estado de la seña de un vistazo (pagada/pendiente) | Carolina | ✅ Registro de señas existe |
| 🟠 Media | Confirmación inmediata + link `wa.me` | Ambas | ✅ Notificaciones email + `wa.me` existen |
| 🟢 Baja | Onboarding con "fiesta de ejemplo" para el dueño | Carolina | No existe |
| 🟢 Baja | Aviso de privacidad breve (Ley 21.719) sin fricción | Ambas | ✅ Cumplimiento legal existe — falta microcopy |

> **Hallazgo transversal:** buena parte del valor ya está **construido en el
> backend** y el trabajo pendiente es de **UX y comunicación de confianza**, no de
> ingeniería. Coherente con `ANALISIS_COMERCIAL.md`: el cuello de botella es
> negocio/distribución, no código.

---

*Generado con el toolkit `ux-researcher-designer`. Ver
[`ANALISIS_COMERCIAL.md`](ANALISIS_COMERCIAL.md) para el contexto de negocio.*
