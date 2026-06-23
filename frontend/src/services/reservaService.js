import api from '../api/client'
import { headersConIdempotencia, resetIdempotencyKey } from '../composables/idempotencia'

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
  try {
    const { data } = await api.post(`/reservas/${id}/pagos`, datos, {
      headers: headersConIdempotencia(),
    })
    return data
  } finally {
    resetIdempotencyKey()
  }
}

export async function listarInvitados(reservaId) {
  const { data } = await api.get(`/reservas/${reservaId}/invitados`)
  return data
}

export async function resumenInvitados(reservaId) {
  const { data } = await api.get(`/reservas/${reservaId}/invitados/resumen`)
  return data
}

export async function agregarInvitado(reservaId, datos) {
  const { data } = await api.post(`/reservas/${reservaId}/invitados`, datos)
  return data
}

export async function eliminarInvitado(reservaId, invitadoId) {
  await api.delete(`/reservas/${reservaId}/invitados/${invitadoId}`)
}

export async function caja(fecha) {
  const { data } = await api.get('/sistema/caja', { params: { fecha } })
  return data
}
