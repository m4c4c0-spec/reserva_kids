/**
 * Feedback háptico (vibración) para interacciones táctiles en celulares.
 * Usa navigator.vibrate (Android). En iOS/Safari es no-op inofensivo.
 *
 * Patrones:
 *   tap()      → 10ms  (selección de elemento)
 *   success()  → [10, 50, 10, 50, 10] (doble pulso: acción completada)
 *   celebrate()→ [15, 40, 15, 40, 15, 40, 15] (triple pulso: logro)
 */
export function useHaptics() {
  const vibrate = (pattern) => {
    try { navigator.vibrate?.(pattern) } catch { /* no-op */ }
  }

  return {
    /** Toque sutil al seleccionar una píldora, tarjeta o botón. */
    tap: () => vibrate(10),

    /** Doble pulso: envío exitoso, pago confirmado. */
    success: () => vibrate([10, 50, 10, 50, 10]),

    /** Triple pulso: celebración (reserva exitosa). */
    celebrate: () => vibrate([15, 40, 15, 40, 15, 40, 15]),
  }
}
