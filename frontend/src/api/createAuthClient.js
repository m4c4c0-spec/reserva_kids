import axios from 'axios'
import { createDeduplicatedClient } from './createRequestDeduplicator'
import { generarTraceParent, generarRequestId } from '../composables/tracing'

export function createAuthClient(useStore, loginRoute) {
  const api = axios.create({
    baseURL: '/api',
    withCredentials: true,
    headers: { 'X-Requested-With': 'XMLHttpRequest' },
    timeout: 30_000,
  })

  api.interceptors.request.use((config) => {
    const auth = useStore()
    if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
    // W3C Trace Context: propaga el traceparent desde el frontend
    try {
      config.headers['traceparent'] = generarTraceParent()
      config.headers['X-Request-ID'] = generarRequestId()
    } catch {
      /* SSR o crypto no disponible */
    }
    return config
  })

  let refreshing = null
  api.interceptors.response.use(
    (res) => res,
    async (error) => {
      if (axios.isCancel(error)) {
        return Promise.reject(error)
      }
      const auth = useStore()
      const original = error.config
      if (error.response?.status === 401 && auth.accessToken && !original._retry) {
        original._retry = true
        try {
          refreshing ??= auth.refresh()
          await refreshing
          refreshing = null
          return api(original)
        } catch {
          refreshing = null
          auth.logoutLocal()
          if (import.meta.client) {
            window.location.href = loginRoute
          }
        }
      }
      return Promise.reject(error)
    },
  )

  return createDeduplicatedClient(api)
}
