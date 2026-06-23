import api from '../api/client'

export async function listar() {
  const { data } = await api.get('/personal')
  return data
}

export async function crear(datos) {
  const { data } = await api.post('/personal', datos)
  return data
}

export async function actualizar(id, datos) {
  const { data } = await api.put(`/personal/${id}`, datos)
  return data
}

export async function eliminar(id) {
  await api.delete(`/personal/${id}`)
}

export async function listarRoles() {
  const { data } = await api.get('/roles')
  return data
}

export async function listarPermisos() {
  const { data } = await api.get('/roles/permisos')
  return data
}

export async function crearRol(datos) {
  const { data } = await api.post('/roles', datos)
  return data
}

export async function actualizarRol(id, datos) {
  const { data } = await api.put(`/roles/${id}`, datos)
  return data
}

export async function eliminarRol(id) {
  await api.delete(`/roles/${id}`)
}
