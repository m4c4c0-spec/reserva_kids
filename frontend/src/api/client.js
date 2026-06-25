import { createAuthClient } from './createAuthClient'
import { useAuthStore } from '../stores/auth'

const api = createAuthClient(useAuthStore, '/negocios_duenos')

export default api
