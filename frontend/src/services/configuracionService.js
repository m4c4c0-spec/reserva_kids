import api from '../api/client'

export async function guardarConfig(datos) {
  const { data } = await api.put('/tenant/configuracion', datos)
  return data
}

export async function guardarHorario(datos) {
  const { data } = await api.put('/horario-atencion', datos)
  return data
}

export async function cargarHorario() {
  const { data } = await api.get('/horario-atencion')
  return data
}
