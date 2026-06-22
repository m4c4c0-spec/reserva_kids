import axios from 'axios'
import api from '../api/clienteClient'
import { BASE_URL } from '../composables/apiBase'

export async function catalogo(slug) {
  const { data } = await axios.get(`${BASE_URL}/public/${slug}`)
  return data
}

export async function disponibilidad(slug, mes) {
  const { data } = await axios.get(`${BASE_URL}/public/${slug}/disponibilidad`, { params: { mes } })
  return data
}

export async function horas(slug, fecha, duracion) {
  const { data } = await axios.get(`${BASE_URL}/public/${slug}/horas`, { params: { fecha, duracion } })
  return data
}

export async function crearSolicitud(slug, datos) {
  const { data } = await axios.post(`${BASE_URL}/public/${slug}/reservas`, datos)
  return data
}

export async function listarNegocios() {
  const { data } = await api.get('/cliente/negocios')
  return data
}

// A4: directorio público, sin login (axios directo, sin cliente autenticado).
export async function listarNegociosPublico() {
  const { data } = await axios.get(`${BASE_URL}/public/negocios`)
  return data
}

export async function agendar(slug, datos) {
  const { data } = await api.post(`/cliente/agendar/${slug}`, datos)
  return data
}
