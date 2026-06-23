import { createAuthClient } from './createAuthClient'
import { useAdminAuthStore } from '../stores/adminAuth'

// Cliente axios del admin: mismo interceptor de Bearer + refresh con rotación que
// dueño/apoderado; si la sesión muere redirige al login de admin.
const adminApi = createAuthClient(useAdminAuthStore, '/admin/login')

export default adminApi
