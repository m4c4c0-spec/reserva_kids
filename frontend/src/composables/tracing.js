/**
 * Trazabilidad distribuida W3C Trace Context desde el frontend.
 *
 * Propaga el traceparent header en cada petición HTTP para que el backend
 * (Micrometer Tracing + OTel) continúe la traza iniciada en el navegador.
 *
 * En SSR (Nuxt server-side) crypto no está disponible, se degrada silenciosamente.
 */

let traceIdActual = null
let requestIdActual = null

function hexAleatorio(bytes) {
  if (typeof crypto === 'undefined' || !crypto.getRandomValues) return null
  return Array.from(crypto.getRandomValues(new Uint8Array(bytes)))
    .map((b) => b.toString(16).padStart(2, '0'))
    .join('')
}

/** Genera un traceparent W3C para iniciar o continuar una traza. */
export function generarTraceParent() {
  if (!traceIdActual) {
    const hex = hexAleatorio(16)
    if (!hex) return null
    traceIdActual = hex
  }
  const spanHex = hexAleatorio(8)
  if (!spanHex) return null
  return `00-${traceIdActual}-${spanHex}-01`
}

/** Genera un X-Request-ID único por petición (correlation ID). */
export function generarRequestId() {
  const hex = hexAleatorio(16)
  if (!hex) return null
  requestIdActual = hex
  return requestIdActual
}

/** Reinicia el traceId (útil en logout o cambio de sesión). */
export function resetTraceId() {
  traceIdActual = null
  requestIdActual = null
}
