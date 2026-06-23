import { defineStore } from 'pinia'
import axios from 'axios'

const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }

export const useAdminAuthStore = defineStore('adminAuth', {
  state: () => {
    const config = useRuntimeConfig?.()
    const baseURL = (config?.public?.apiUrl || '') + '/api'

    return {
      baseURL,
      accessToken: process.client ? sessionStorage.getItem('rk_adm_access') || null : null,
      email: process.client ? sessionStorage.getItem('rk_adm_email') || null : null,
      nombre: process.client ? sessionStorage.getItem('rk_adm_nombre') || null : null,
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
      if (process.client) {
        sessionStorage.setItem('rk_adm_access', data.accessToken)
        sessionStorage.setItem('rk_adm_email', data.email || '')
        sessionStorage.setItem('rk_adm_nombre', data.nombre || '')
      }
    },
    async login(email, password) {
      const { data } = await axios.post(`${this.baseURL}/admin-auth/login`, { email, password }, withCreds)
      this.guardar(data)
    },
    async oauth2Login(provider, code, redirectUri, state) {
      const { data } = await axios.post(
        `${this.baseURL}/admin-auth/oauth2/${provider}`,
        { code, redirectUri, state },
        withCreds,
      )
      this.guardar(data)
    },
    async oauth2AuthorizeUrl(provider) {
      const { data } = await axios.get(`${this.baseURL}/admin-auth/oauth2/${provider}/authorize`)
      return data.authorizeUrl
    },
    async refresh() {
      const { data } = await axios.post(`${this.baseURL}/admin-auth/refresh`, null, withCreds)
      this.guardar(data)
    },
    async logout() {
      try {
        await axios.post(`${this.baseURL}/admin-auth/logout`, null, withCreds)
      } catch {
        /* token ya inválido */
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.accessToken = this.email = this.nombre = null
      if (process.client) {
        sessionStorage.removeItem('rk_adm_access')
        sessionStorage.removeItem('rk_adm_email')
        sessionStorage.removeItem('rk_adm_nombre')
      }
    },
  },
})
