import { createAuthClient } from './createAuthClient'
import { useAuthStore } from '../stores/auth'

const api = createAuthClient(useAuthStore, '/login')

export default api
