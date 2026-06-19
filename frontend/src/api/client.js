import axios from 'axios'
import { useAuthStore } from '../stores/auth'
import router from '../router'

const api = axios.create({
  // En dev VITE_API_URL no está seteada → base relativa '/api', que el proxy de Vite
  // reenvía al backend (mismo origen, sin CORS). En prod VITE_API_URL trae el dominio.
  baseURL: (import.meta.env.VITE_API_URL || '') + '/api',
  // Refresh token en cookie HttpOnly: el navegador la envía al /refresh solo con credenciales.
  withCredentials: true,
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
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
    const auth = useAuthStore()
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
        router.push('/login')
      }
    }
    return Promise.reject(error)
  },
)

export default api
