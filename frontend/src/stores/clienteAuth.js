import { defineStore } from 'pinia'
import axios from 'axios'

const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }

export const useClienteAuthStore = defineStore('clienteAuth', {
  state: () => {
    const config = useRuntimeConfig?.()
    const baseURL = (config?.public?.apiUrl || '') + '/api'

    return {
      baseURL,
      accessToken: process.client ? sessionStorage.getItem('rk_cli_access') || null : null,
      email: process.client ? sessionStorage.getItem('rk_cli_email') || null : null,
      nombre: process.client ? sessionStorage.getItem('rk_cli_nombre') || null : null,
      telefono: process.client ? sessionStorage.getItem('rk_cli_telefono') || null : null,
    }
  },
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(data) {
      this.accessToken = data.accessToken
      this.email = data.email
      this.nombre = data.nombre
      this.telefono = data.telefono
      if (process.client) {
        sessionStorage.setItem('rk_cli_access', data.accessToken)
        sessionStorage.setItem('rk_cli_email', data.email)
        sessionStorage.setItem('rk_cli_nombre', data.nombre || '')
        sessionStorage.setItem('rk_cli_telefono', data.telefono || '')
      }
    },
    async login(email, password) {
      const { data } = await axios.post(`${this.baseURL}/cliente-auth/login`, { email, password }, withCreds)
      this.guardar(data)
    },
    async registrar(payload) {
      const { data } = await axios.post(`${this.baseURL}/cliente-auth/register`, payload, withCreds)
      this.guardar(data)
    },
    async oauth2Login(provider, code, redirectUri, state) {
      const { data } = await axios.post(
        `${this.baseURL}/cliente-auth/oauth2/${provider}`,
        { code, redirectUri, state },
        withCreds,
      )
      this.guardar(data)
    },
    async oauth2AuthorizeUrl(provider) {
      const { data } = await axios.get(`${this.baseURL}/cliente-auth/oauth2/${provider}/authorize`)
      return data.authorizeUrl
    },
    async refresh() {
      const { data } = await axios.post(`${this.baseURL}/cliente-auth/refresh`, null, withCreds)
      this.guardar(data)
    },
    async logout() {
      try {
        await axios.post(`${this.baseURL}/cliente-auth/logout`, null, withCreds)
      } catch {
        /* token ya inválido */
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.accessToken = this.email = this.nombre = this.telefono = null
      if (process.client) {
        sessionStorage.removeItem('rk_cli_access')
        sessionStorage.removeItem('rk_cli_email')
        sessionStorage.removeItem('rk_cli_nombre')
        sessionStorage.removeItem('rk_cli_telefono')
      }
    },
  },
})
