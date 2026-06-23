/**
 * Wrapper de Axios que desduplica peticiones en vuelo y permite abortar
 * peticiones obsoletas (útil para búsquedas con debounce y navegación rápida).
 *
 * Uso:
 *   import { createDeduplicatedClient } from './api/createRequestDeduplicator'
 *   const api = createDeduplicatedClient(baseAxiosInstance)
 *   api.get('/endpoint', { params }) // deduplica GETs idénticos en vuelo
 *
 * Desduplicación: si una petición con la misma clave (método + URL + params serializados)
 * ya está en vuelo, se devuelve la misma promesa en vez de lanzar otra petición.
 *
 * AbortSignal: pasa automáticamente un AbortController a cada petición. Si se
 * dispara otra petición con la misma clave antes de que la anterior termine,
 * la anterior se aborta (ahorro de ancho de banda en búsquedas typeahead).
 */

const pendientes = new Map()

function serializar(valor) {
  if (valor === undefined || valor === null) return ''
  if (typeof valor === 'string') return valor
  return JSON.stringify(valor)
}

function claveRequest(config) {
  const url = config.url || ''
  const method = (config.method || 'GET').toUpperCase()
  const params = config.params ? serializar(config.params) : ''
  const data = method !== 'GET' && config.data ? serializar(config.data) : ''
  return `${method}::${url}::${params}::${data}`
}

export function createDeduplicatedClient(axiosInstance) {
  const instance = axiosInstance

  instance.interceptors.request.use((config) => {
    const clave = claveRequest(config)
    const existente = pendientes.get(clave)

    if (existente) {
      // Si hay una petición idéntica en vuelo, abortarla (para búsquedas typeahead)
      // o reutilizarla (para GETs). Para mutaciones siempre lanzamos una nueva
      // porque tienen idempotency key propio.
      const method = (config.method || 'GET').toUpperCase()
      if (method === 'GET') {
        // GET: reutilizar la promesa existente (desduplicación)
        config.adapter = () => existente.promise
        return config
      }
      // POST/PUT/PATCH: abortar la anterior (typeahead, doble submit sin key)
      try { existente.controller.abort() } catch (_) { /* ya abortado */ }
    }

    const controller = new AbortController()
    config.signal = controller.signal

    const wrapper = {
      controller,
      promise: null,
    }

    // Guardamos referencia ANTES de que la promesa se resuelva
    pendientes.set(clave, wrapper)

    return config
  })

  instance.interceptors.response.use(
    (response) => {
      const clave = claveRequest(response.config)
      pendientes.delete(clave)
      return response
    },
    (error) => {
      if (error.config) {
        const clave = claveRequest(error.config)
        pendientes.delete(clave)
      }
      return Promise.reject(error)
    },
  )

  return instance
}

/**
 * Cancela todas las peticiones pendientes del cliente.
 * Útil al cambiar de ruta o desmontar un componente.
 */
export function cancelarPendientes(axiosInstance) {
  for (const [clave, wrapper] of pendientes) {
    try { wrapper.controller.abort() } catch (_) { /* ok */ }
  }
  pendientes.clear()
}
