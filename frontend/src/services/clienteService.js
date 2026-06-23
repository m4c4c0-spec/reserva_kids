import api from '../api/clienteClient'

export async function listarReservas(estado, page = 0, size = 20) {
  const params = { page, size }
  if (estado) params.estado = estado
  const { data } = await api.get('/cliente/reservas', { params })
  return data
}

export async function solicitarReset(email) {
  await api.post('/cliente-auth/reset/solicitar', { email })
}

export async function confirmarReset(token, nuevaPassword) {
  await api.post('/cliente-auth/reset/confirmar', { token, nuevaPassword })
}
