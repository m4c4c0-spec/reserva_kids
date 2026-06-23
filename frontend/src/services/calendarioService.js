import api from '../api/client'

export async function listarBloques(mes) {
  const { data } = await api.get('/calendario/bloques', { params: { mes } })
  return data
}

export async function crearBloque(datos) {
  const { data } = await api.post('/calendario/bloques', datos)
  return data
}

export async function eliminarBloque(id) {
  await api.delete(`/calendario/bloques/${id}`)
}
