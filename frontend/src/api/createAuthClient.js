import axios from 'axios'
import router from '../router'

// Fábrica de cliente axios con interceptor de Bearer + refresh con rotación.
// Desduplica client.js (dueño) y clienteClient.js (apoderado): solo cambian
// el store de auth y la ruta de login a la que redirigir si la sesión muere.
export function createAuthClient(useStore, loginRoute) {
  const api = axios.create({
    // En dev VITE_API_URL no está seteada → base relativa '/api', que el proxy
    // de Vite reenvía al backend (mismo origen, sin CORS). En prod trae el dominio.
    baseURL: (import.meta.env.VITE_API_URL || '') + '/api',
    // Refresh token en cookie HttpOnly: el navegador la envía al /refresh solo con credenciales.
    withCredentials: true,
  })

  api.interceptors.request.use((config) => {
    const auth = useStore()
    if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
    return config
  })

  // Access token de 15 min: ante un 401 intenta refresh con rotación y reintenta una vez.
  // El refresh token va en cookie HttpOnly (no en el store), por eso basta con haber
  // tenido sesión (accessToken) para intentar el refresh.
  let refreshing = null
  api.interceptors.response.use(
    (res) => res,
    async (error) => {
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
          // Sin esto el usuario quedaba en un panel "roto" en silencio: cada vista
          // fallando con 401 y sin pista de que la sesión murió.
          router.push(loginRoute)
        }
      }
      return Promise.reject(error)
    },
  )

  return api
}
