import api from '../api/client'

export async function listar() {
  const { data } = await api.get('/servicios')
  return data
}

export async function crear(datos) {
  const { data } = await api.post('/servicios', datos)
  return data
}

export async function actualizar(id, datos) {
  const { data } = await api.put(`/servicios/${id}`, datos)
  return data
}

export async function desactivar(id) {
  await api.delete(`/servicios/${id}`)
}
