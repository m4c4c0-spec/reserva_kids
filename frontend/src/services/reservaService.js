import api from '../api/client'

export async function listar(estado, page = 0, size = 20, q = '') {
  const params = { page, size }
  if (estado) params.estado = estado
  if (q && q.trim()) params.q = q.trim()
  const { data } = await api.get('/reservas', { params })
  return data
}

export async function cotizar(id, datos) {
  const { data } = await api.put(`/reservas/${id}/cotizar`, datos)
  return data
}

export async function confirmar(id) {
  const { data } = await api.put(`/reservas/${id}/confirmar`)
  return data
}

export async function realizar(id) {
  const { data } = await api.put(`/reservas/${id}/realizar`)
  return data
}

export async function cancelar(id, motivo) {
  const { data } = await api.put(`/reservas/${id}/cancelar`, { motivo })
  return data
}

export async function registrarPago(id, datos) {
  const { data } = await api.post(`/reservas/${id}/pagos`, datos)
  return data
}
