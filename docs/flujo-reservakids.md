# Journey Map — ReservaKids

> Mapa de experiencia del flujo de conversión central (reserva de cumpleaños infantil).
> Anclado al producto real: `PublicSiteView`, `EstadoReserva`, `RecordatorioJobs`, `NotificacionWhatsappPort`.
> Leyenda de evidencia: `[código]` = evidente en el producto · `[hipótesis]` = a validar con investigación.

## 1. Alcance (scope)

| Elemento | Definición |
|---|---|
| **Persona** | **Carolina, 34** — mamá organizando el cumpleaños de su hijo (6 años). Móvil-first, llega desde Instagram/WhatsApp, poco tiempo, sensible al precio, ansiosa por "cerrar" la fecha antes de que se la ganen. |
| **Objetivo** | Reservar y dejar confirmado un cumpleaños infantil en un salón específico. |
| **Disparador (start)** | Ve el link del salón (bio de Instagram, story, recomendación por WhatsApp) y entra al sitio público `/:slug`. |
| **Éxito (end)** | Reserva en estado **CONFIRMADA** (seña pagada) + recordatorio recibido → asiste al evento (**REALIZADA**). |
| **Horizonte temporal** | ~2 a 6 semanas desde el primer clic hasta el evento; el tramo crítico de decisión es de **minutos a 48 h**. |

> ⚠️ **Nota de validez:** mapa anclado al código real, pero las **emociones y varios pain points son hipótesis** derivadas del flujo, no de investigación con usuarias reales. Validar con 5–8 entrevistas/tests moderados.

## 2. Etapas y máquina de estados

```
Descubrimiento → Exploración → Solicitud → Espera/Cotización → Pago seña → Pre-evento → El cumpleaños
                                  │              │                  │            │
   (sin estado)            PENDIENTE  ──→   COTIZADA      ──→   CONFIRMADA ──→ REALIZADA
                          (hold 48 h)   (link de pago)      (seña pagada)   (recordatorio 24 h)
```

## 3. El mapa, capa por capa

### Etapa 1 — Descubrimiento
- **Acciones:** clic en link de Instagram/WhatsApp; aterriza en el sitio del salón.
- **Touchpoints:** sitio público `/:slug`, header con marca blanca (color, logo, título SEO), personaje animado.
- **Emoción:** 🙂 4/5 — curiosidad, expectativa. `[hipótesis]`
- **Pain points:**
  - Si el slug no existe → pantalla "Negocio no encontrado" sin salida ni CTA de recuperación. `[código]`
  - Carga inicial: spinner de página completa bloquea todo hasta tener el catálogo. `[código]`
- **Oportunidades:** prueba social arriba (reseñas/fotos de fiestas pasadas); en "no encontrado", ofrecer buscar/WhatsApp del salón.

### Etapa 2 — Exploración / Cotización implícita
- **Acciones:** Paso 1 "Elige la fiesta": compara servicios principales, mira extras (upsells); Paso 2 "Elige la fecha": calendario térmico (verde/amarillo/naranja/lleno) + bloques de hora.
- **Touchpoints:** wizard 3 pasos, calendario térmico, chips de hora, barra inferior fija con precio del servicio.
- **Emoción:** 😀 4/5 en buen mes; 😟 2/5 si su mes está lleno. `[hipótesis]`
- **Pain points:**
  - El precio del servicio se muestra, pero **el total real lo cotiza el salón después** → expectativa de precio puede chocar con la cotización. `[código]`
  - Los **extras (adicionales) tienen checkbox pero no se suman ni se envían** en la solicitud — UI sugiere que se agregan, el back no los recibe. `[código]` ← **bug de expectativa**
  - "Sin horarios este mes" → solo invita a probar el mes siguiente, sin lista de espera. `[código]`
- **Oportunidades:** cerrar el loop de los extras (sumar al carrito/solicitud o quitar el checkbox); CTA "avísame si se libera una fecha".

### Etapa 3 — Solicitud (PENDIENTE)
- **Acciones:** Paso 3 "Tus datos": nombre, RUT, teléfono WhatsApp, email opcional, nº niños, comuna, comentarios, acepta políticas + datos (Ley 21.719). Botón **"Pedir precio al salón"**.
- **Touchpoints:** formulario con auto-formato de RUT y teléfono, banner de error, checkboxes de consentimiento.
- **Emoción:** 😐 3/5 — fricción del formulario vs. ansiedad por asegurar la fecha. `[hipótesis]`
- **Pain points:**
  - Formulario relativamente largo en móvil justo antes de la conversión. `[hipótesis]`
  - Mensaje de éxito promete "te van a escribir pronto" + "fecha reservada por 48 h" — **dependencia de que el dueño responda a tiempo**; sin auto-respuesta ni cuenta regresiva visible. `[código]`
  - En error 409 (cupo tomado mientras llenaba) se recarga disponibilidad, pero pierde contexto del bloque elegido. `[código]`
- **Oportunidades:** mostrar **temporizador visible del hold de 48 h**; confirmación inmediata por WhatsApp al cliente (hoy el WhatsApp automático al cliente es post-pago); guardar progreso del formulario.

### Etapa 4 — Espera y cotización (PENDIENTE → COTIZADA)
- **Acciones:** espera respuesta; recibe cotización del dueño (total + seña sugerida + link de pago).
- **Touchpoints:** WhatsApp del salón, link de pago (Mercado Pago / Khipu).
- **Emoción:** 😬 2/5 — la **zona de mayor abandono**: silencio = ansiedad/duda. `[hipótesis]`
- **Pain points:**
  - Tiempo de respuesta depende 100% del dueño; sin SLA visible ni recordatorio si no contesta. `[código]`
  - Si falla la generación del link, el dueño debe cobrar la seña por fuera → fricción y desconfianza. `[código]`
- **Oportunidades:** auto-mensaje "estamos preparando tu precio"; recordatorio al dueño si la solicitud lleva X horas sin cotizar; expiración clara de la cotización.

### Etapa 5 — Pago de la seña (COTIZADA → CONFIRMADA)
- **Acciones:** paga la seña; vuelve al sitio con `?pago=exito|pendiente|fallo`.
- **Touchpoints:** pasarela, banner de retorno en el sitio, **WhatsApp de confirmación automático** (`confirmacionReserva`), feedback háptico de celebración.
- **Emoción:** 😄 5/5 si paga OK; 😞 2/5 si falla. `[código]` (estados manejados)
- **Pain points:**
  - En `pago=fallo` el copy es tranquilizador y ofrece WhatsApp ✅, pero no reintenta el link automáticamente. `[código]`
  - `pago=pendiente` deja a la usuaria en limbo hasta el webhook. `[código]`
- **Oportunidades:** botón "reintentar pago" en fallo; estado en vivo del pago pendiente.

### Etapa 6 — Pre-evento (CONFIRMADA)
- **Acciones:** revisa su reserva en el historial de cliente; recibe **recordatorio 24 h antes** (email + WhatsApp).
- **Touchpoints:** `/clientes/reservas` (historial), email recordatorio, WhatsApp recordatorio con servicios + hora.
- **Emoción:** 🙂 4/5 — tranquilidad, anticipación. `[código]`
- **Pain points:**
  - El recordatorio **WhatsApp solo se envía si el cliente tiene email cargado** (`procesarCita` corta si email vacío) — el WhatsApp queda acoplado al email. `[código]` ← revisar
  - El recordatorio de cumpleaños (`procesarCumpleano`) **solo manda email, no WhatsApp**. `[código]`
- **Oportunidades:** desacoplar WhatsApp del email; sumar WhatsApp al recordatorio de cumpleaños; permitir saldo pendiente/indicaciones (dirección, qué llevar) en el recordatorio.

### Etapa 7 — El cumpleaños y después (REALIZADA)
- **Acciones:** asiste; idealmente deja reseña / vuelve a reservar.
- **Touchpoints:** —(no hay touchpoint post-evento en el producto). `[código]`
- **Emoción:** 🎉 5/5 si todo salió bien. `[hipótesis]`
- **Pain points:** **no existe loop de post-evento**: ni reseña, ni "reserva el próximo", ni referidos. `[código]`
- **Oportunidades:** mensaje post-evento pidiendo reseña/fotos; cupón de recompra; programa de referidos (cierra el ciclo hacia Descubrimiento de la próxima familia).

## 4. Curva emocional

```
5 │🙂        😀                              😄              🎉
4 │  ●────●                          ●(pago)        ●────●
3 │            😐(solicitud)
2 │                  😬────────────😬          😞(fallo)
1 │
  └─Descubr.─Explor.─Solicitud─Espera/Cotiz.─Pago─Pre-evento─Evento
                              ▲ VALLE CRÍTICO ▲
```

El **valle de la espera (Etapas 3→4)** es donde se pierde la reserva: el momentum emocional cae justo cuando la conversión depende de un humano (el dueño) fuera del control de la usuaria.

## 5. Oportunidades priorizadas

**Score = Frecuencia × Severidad × Solvabilidad** (cada uno 1–5; máx 125)

| # | Oportunidad | Etapa | Frec | Sev | Solv | **Score** |
|---|---|---|---|---|---|---|
| 1 | **Cerrar el loop de la espera de 48 h**: temporizador visible + auto-mensaje WhatsApp al cliente + recordatorio al dueño | 3–4 | 5 | 5 | 4 | **100** |
| 2 | **Arreglar los extras/adicionales** (se eligen pero no se envían) | 2 | 4 | 4 | 5 | **80** |
| 3 | **Desacoplar recordatorio WhatsApp del email** + WhatsApp en cumpleaños | 6 | 4 | 3 | 5 | **60** |
| 4 | **Loop post-evento** (reseña + recompra + referidos) | 7 | 5 | 4 | 3 | **60** |
| 5 | "Reintentar pago" en `pago=fallo` | 5 | 2 | 3 | 5 | **30** |
| 6 | Recuperación en "Negocio no encontrado" / lista de espera por fecha | 1–2 | 2 | 2 | 4 | **16** |

**Recomendación:** atacar **#1 y #2 primero**. #1 es el mayor punto de fuga de conversión (valle emocional + dependencia humana); #2 es un bug de expectativa de bajo costo y alta confianza.

## 5.b Estado de implementación (2026-06-23)

| # | Estado | Cambios |
|---|---|---|
| 1 | ✅ Hecho | Acuse inmediato al cliente (`whatsapp.solicitudRecibida`) + aviso al **dueño** (fix de bug: `nuevaSolicitudDueno` enviaba al cliente, ahora usa `tenant.telefonoContacto`) + cuenta regresiva de 48 h en la pantalla de éxito de `PublicSiteView`. |
| 2 | ✅ Hecho | Frontend envía `adicionalIds`; backend (`SolicitudPublicaRequest`, `ReservaService.resolverExtras`) los valida contra el catálogo y los anexa a los comentarios para que el dueño los vea al cotizar. |
| 3 | ✅ Hecho | `RecordatorioJobs`: el WhatsApp ya no depende del email; el cumpleaños también recibe recordatorio por WhatsApp. |
| 6 | ✅ Hecho | Pantalla "Negocio no encontrado" con botón **Reintentar** y enlace al directorio (multi-tenant). |
| 5 | ⏸️ Diferido | "Reintentar pago" requiere persistir/recuperar el link de pago en el retorno `pago=fallo` (hoy no está client-side). Pendiente de diseño. |
| 4 | ⏸️ Diferido | Loop post-evento (reseñas + recompra + referidos) es una feature completa (esquema BD, endpoints, emails, UI). Necesita su propia spec. |

## 6. Qué validar antes de tratar esto como verdad

5–8 entrevistas/tests moderados con mamás que reservaron (o abandonaron):
1. ¿Cuánto esperaron la cotización y qué sintieron en el silencio? (valida el valle de Etapa 4)
2. ¿Entendieron que el precio mostrado **no** era el final? (valida Etapa 2)
3. ¿Intentaron agregar extras y esperaban verlos en la solicitud? (valida el bug #2)
4. ¿El "reservada por 48 h" generó urgencia o confusión?
