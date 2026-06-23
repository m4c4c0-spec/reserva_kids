import api from '../api/client'

export async function guardarConfig(datos) {
  const { data } = await api.put('/tenant/configuracion', datos)
  return data
}

export async function guardarPasarela(datos) {
  const { data } = await api.put('/tenant/configuracion/pasarela', datos)
  return data
}

export async function guardarContacto(telefonoContacto) {
  const { data } = await api.put('/tenant/contacto', { telefonoContacto })
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

// Google Calendar Sync
export async function estadoGoogleCalendar() {
  const { data } = await api.get('/tenant/google-calendar')
  return data
}

export async function conectarGoogleCalendar(code, redirectUri) {
  const { data } = await api.post('/tenant/google-calendar/conectar', { code, redirectUri })
  return data
}

export async function desconectarGoogleCalendar() {
  const { data } = await api.post('/tenant/google-calendar/desconectar')
  return data
}

export async function googleCalendarAuthorizeUrl() {
  const { data } = await api.get('/auth/oauth2/google/authorize?type=dueno')
  return data.authorizeUrl
}
