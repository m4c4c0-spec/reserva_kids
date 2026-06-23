/**
 * Web Vitals — monitoreo de rendimiento real del usuario (RUM).
 *
 * Mide LCP, FCP, CLS y TTFB y los envía como métricas al backend vía
 * un beacon POST a /api/public/rum (fire-and-forget, no bloquea la navegación).
 *
 * Basado en la API web-vitals de Google Chrome (disponible en Chromium 88+).
 * En navegadores sin soporte, simplemente no hace nada.
 */

const RUM_ENDPOINT = '/api/public/rum'

function enviarMetrica(nombre, valor, atributos = {}) {
  try {
    const payload = JSON.stringify({ nombre, valor, atributos, ts: Date.now() })
    if (navigator.sendBeacon) {
      navigator.sendBeacon(RUM_ENDPOINT, new Blob([payload], { type: 'application/json' }))
    }
  } catch {
    // fire-and-forget: no interrumpir la experiencia del usuario
  }
}

function medirLCP() {
  new PerformanceObserver((list) => {
    const entries = list.getEntries()
    if (entries.length > 0) {
      const lcp = entries[entries.length - 1]
      enviarMetrica('LCP', Math.round(lcp.startTime), {
        element: lcp.element?.tagName || 'unknown',
        url: lcp.url || '',
      })
    }
  }).observe({ type: 'largest-contentful-paint', buffered: true })
}

function medirFCP() {
  new PerformanceObserver((list) => {
    const entries = list.getEntriesByName('first-contentful-paint')
    if (entries.length > 0) {
      enviarMetrica('FCP', Math.round(entries[0].startTime))
    }
  }).observe({ type: 'paint', buffered: true })
}

function medirCLS() {
  let clsValue = 0
  new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (!entry.hadRecentInput) {
        clsValue += entry.value
      }
    }
    enviarMetrica('CLS', Math.round(clsValue * 1000) / 1000)
  }).observe({ type: 'layout-shift', buffered: true })
}

function medirTTFB() {
  const nav = performance.getEntriesByType('navigation')[0]
  if (nav) {
    enviarMetrica('TTFB', Math.round(nav.responseStart - nav.requestStart))
  }
}

/** Activa el monitoreo de Web Vitals. Llamar una vez al montar la app. */
export function iniciarWebVitals() {
  if (typeof window === 'undefined' || !window.PerformanceObserver) return
  try {
    medirLCP()
    medirFCP()
    medirCLS()
    // TTFB se mide con un pequeño retraso para asegurar que la navegación haya terminado
    setTimeout(medirTTFB, 1000)
  } catch {
    // silencioso: el monitoreo nunca debe romper la app
  }
}
