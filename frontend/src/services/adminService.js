import adminApi from '../api/adminClient'

// Consola de plataforma (F2 backend): gobierno de negocios cross-tenant.
export const adminService = {
  metricas() {
    return adminApi.get('/admin/metricas').then((r) => r.data)
  },
  listarNegocios({ estado, q } = {}) {
    const params = {}
    if (estado) params.estado = estado
    if (q) params.q = q
    return adminApi.get('/admin/negocios', { params }).then((r) => r.data)
  },
  detalle(id) {
    return adminApi.get(`/admin/negocios/${id}`).then((r) => r.data)
  },
  suspender(id) {
    return adminApi.post(`/admin/negocios/${id}/suspender`).then((r) => r.data)
  },
  reactivar(id) {
    return adminApi.post(`/admin/negocios/${id}/reactivar`).then((r) => r.data)
  },
  auditoria(limite = 50) {
    return adminApi.get('/admin/auditoria', { params: { limite } }).then((r) => r.data)
  },
  listarAdmins() {
    return adminApi.get('/admin/administradores').then((r) => r.data)
  },
  crearAdmin(payload) {
    return adminApi.post('/admin/administradores', payload).then((r) => r.data)
  },
}
