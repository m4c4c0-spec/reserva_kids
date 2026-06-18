import { defineStore } from 'pinia'
import axios from 'axios'

// dev: '' → '/api' relativo (proxy de Vite); prod: VITE_API_URL con el dominio
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'

// Cuenta de cliente (apoderado): sesión SEPARADA de la del dueño (otras claves de
// sessionStorage) para que ambas puedan coexistir sin pisarse.
export const useClienteAuthStore = defineStore('clienteAuth', {
  state: () => ({
    accessToken: sessionStorage.getItem('rk_cli_access') || null,
    email: sessionStorage.getItem('rk_cli_email') || null,
    nombre: sessionStorage.getItem('rk_cli_nombre') || null,
  }),
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(data) {
      this.accessToken = data.accessToken
      this.email = data.email
      this.nombre = data.nombre
      sessionStorage.setItem('rk_cli_access', data.accessToken)
      sessionStorage.setItem('rk_cli_email', data.email)
      sessionStorage.setItem('rk_cli_nombre', data.nombre || '')
    },
    async login(email, password) {
      const { data } = await axios.post(`${baseURL}/cliente-auth/login`, { email, password })
      this.guardar(data)
    },
    async registrar(payload) {
      const { data } = await axios.post(`${baseURL}/cliente-auth/register`, payload)
      this.guardar(data)
    },
    logout() {
      this.accessToken = this.email = this.nombre = null
      sessionStorage.removeItem('rk_cli_access')
      sessionStorage.removeItem('rk_cli_email')
      sessionStorage.removeItem('rk_cli_nombre')
    },
  },
})
