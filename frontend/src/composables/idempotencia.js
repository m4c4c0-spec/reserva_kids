/**
 * Utilidad de idempotencia para prevenir operaciones duplicadas en mutaciones.
 * Genera un UUID v4 y lo adjunta como header X-Idempotency-Key.
 *
 * El backend (IdempotencyFilter) registra la clave y rechaza reenvíos con 409 Conflict.
 * La clave se genera una sola vez por operación lógica (ej. al hacer clic en "Reservar").
 */

let cachedKey = null

export function generarIdempotencyKey() {
  if (cachedKey) return cachedKey
  cachedKey = crypto.randomUUID()
  return cachedKey
}

export function regenerarIdempotencyKey() {
  cachedKey = crypto.randomUUID()
  return cachedKey
}

export function headersConIdempotencia(headers = {}) {
  return {
    ...headers,
    'X-Idempotency-Key': generarIdempotencyKey(),
  }
}

/**
 * Limpia la clave cacheada. Llamar después de que la operación termine (éxito o error)
 * para que una nueva operación (ej. otro "Reservar") genere una clave nueva.
 */
export function resetIdempotencyKey() {
  cachedKey = null
}
