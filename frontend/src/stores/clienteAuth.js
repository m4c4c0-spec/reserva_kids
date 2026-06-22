import { defineStore } from 'pinia'
import axios from 'axios'

// dev: '' → '/api' relativo (proxy de Vite); prod: VITE_API_URL con el dominio
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'
const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }

// Cuenta de cliente (apoderado): sesión SEPARADA de la del dueño (otras claves de
// sessionStorage) para que ambas puedan coexistir sin pisarse. El refresh token vive
// en una cookie HttpOnly propia (rk_cliente_refresh), fuera del alcance del JS.
export const useClienteAuthStore = defineStore('clienteAuth', {
  state: () => ({
    accessToken: sessionStorage.getItem('rk_cli_access') || null,
    email: sessionStorage.getItem('rk_cli_email') || null,
    nombre: sessionStorage.getItem('rk_cli_nombre') || null,
    telefono: sessionStorage.getItem('rk_cli_telefono') || null,
  }),
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(data) {
      this.accessToken = data.accessToken
      this.email = data.email
      this.nombre = data.nombre
      this.telefono = data.telefono
      sessionStorage.setItem('rk_cli_access', data.accessToken)
      sessionStorage.setItem('rk_cli_email', data.email)
      sessionStorage.setItem('rk_cli_nombre', data.nombre || '')
      sessionStorage.setItem('rk_cli_telefono', data.telefono || '')
    },
    async login(email, password) {
      const { data } = await axios.post(`${baseURL}/cliente-auth/login`, { email, password }, withCreds)
      this.guardar(data)
    },
    async registrar(payload) {
      const { data } = await axios.post(`${baseURL}/cliente-auth/register`, payload, withCreds)
      this.guardar(data)
    },
    async refresh() {
      // Sin body y sin el interceptor del api: el refresh token va en la cookie HttpOnly
      // (withCredentials). Usar axios directo evita un loop si el refresh responde 401.
      const { data } = await axios.post(`${baseURL}/cliente-auth/refresh`, null, withCreds)
      this.guardar(data)
    },
    async logout() {
      try {
        await axios.post(`${baseURL}/cliente-auth/logout`, null, withCreds)
      } catch {
        /* token ya inválido */
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.accessToken = this.email = this.nombre = this.telefono = null
      sessionStorage.removeItem('rk_cli_access')
      sessionStorage.removeItem('rk_cli_email')
      sessionStorage.removeItem('rk_cli_nombre')
      sessionStorage.removeItem('rk_cli_telefono')
    },
  },
})
