import axios from 'axios'
import { useAuthStore } from '../stores/auth'

const api = axios.create({
  baseURL: (import.meta.env.VITE_API_URL || 'http://localhost:8080') + '/api',
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.accessToken) config.headers.Authorization = `Bearer ${auth.accessToken}`
  return config
})

// Access token de 15 min: ante un 401 intenta refresh con rotación y reintenta una vez
let refreshing = null
api.interceptors.response.use(
  (res) => res,
  async (error) => {
    const auth = useAuthStore()
    const original = error.config
    if (error.response?.status === 401 && auth.refreshToken && !original._retry) {
      original._retry = true
      try {
        refreshing ??= auth.refresh()
        await refreshing
        refreshing = null
        return api(original)
      } catch {
        refreshing = null
        auth.logoutLocal()
      }
    }
    return Promise.reject(error)
  },
)

export default api
