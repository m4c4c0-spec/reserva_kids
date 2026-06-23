// Plugin Nuxt client-side: Real User Monitoring (Web Vitals)
// Mide LCP, FCP, CLS, TTFB y los envía al backend vía sendBeacon.

export default defineNuxtPlugin(() => {
  if (typeof window === 'undefined' || !window.PerformanceObserver) return

  const RUM_ENDPOINT = '/api/public/rum'

  function enviar(nombre, valor, attr = {}) {
    try {
      const payload = JSON.stringify({ nombre, valor, atributos: attr, ts: Date.now() })
      if (navigator.sendBeacon) {
        navigator.sendBeacon(RUM_ENDPOINT, new Blob([payload], { type: 'application/json' }))
      }
    } catch { /* fire-and-forget */ }
  }

  new PerformanceObserver((list) => {
    const entries = list.getEntries()
    if (entries.length > 0) {
      const lcp = entries[entries.length - 1]
      enviar('LCP', Math.round(lcp.startTime), { element: lcp.element?.tagName || 'unknown' })
    }
  }).observe({ type: 'largest-contentful-paint', buffered: true })

  new PerformanceObserver((list) => {
    const entries = list.getEntriesByName('first-contentful-paint')
    if (entries.length > 0) enviar('FCP', Math.round(entries[0].startTime))
  }).observe({ type: 'paint', buffered: true })

  let cls = 0
  new PerformanceObserver((list) => {
    for (const entry of list.getEntries()) {
      if (!entry.hadRecentInput) cls += entry.value
    }
    enviar('CLS', Math.round(cls * 1000) / 1000)
  }).observe({ type: 'layout-shift', buffered: true })

  setTimeout(() => {
    const nav = performance.getEntriesByType('navigation')[0]
    if (nav) enviar('TTFB', Math.round(nav.responseStart - nav.requestStart))
  }, 1000)
})
