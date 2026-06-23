import { defineStore } from 'pinia'
import axios from 'axios'

const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }

export const useAuthStore = defineStore('auth', {
  state: () => {
    const config = useRuntimeConfig?.()
    const baseURL = (config?.public?.apiUrl || '') + '/api'

    return {
      baseURL,
      accessToken: process.client ? sessionStorage.getItem('rk_access') || null : null,
      slug: process.client ? sessionStorage.getItem('rk_slug') || null : null,
      nombreNegocio: process.client ? sessionStorage.getItem('rk_nombre') || null : null,
    }
  },
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(tokens) {
      this.accessToken = tokens.accessToken
      this.slug = tokens.slug
      this.nombreNegocio = tokens.nombreNegocio
      if (process.client) {
        sessionStorage.setItem('rk_access', tokens.accessToken)
        sessionStorage.setItem('rk_slug', tokens.slug)
        sessionStorage.setItem('rk_nombre', tokens.nombreNegocio)
      }
    },
    async login(email, password) {
      const { data } = await axios.post(`${this.baseURL}/auth/login`, { email, password }, withCreds)
      this.guardar(data)
    },
    async registrar(payload) {
      const { data } = await axios.post(`${this.baseURL}/auth/register`, payload, withCreds)
      this.guardar(data)
    },
    async entrarConMagicLink(token) {
      const { data } = await axios.post(`${this.baseURL}/auth/magic/entrar`, { token }, withCreds)
      this.guardar(data)
    },
    async pedirMagicLink(email) {
      await axios.post(`${this.baseURL}/auth/magic/solicitar`, { email }, withCreds)
    },
    async oauth2Login(provider, code, redirectUri, state, nombreNegocio, slug) {
      const { data } = await axios.post(
        `${this.baseURL}/auth/oauth2/${provider}`,
        { code, redirectUri, state, nombreNegocio, slug },
        withCreds,
      )
      this.guardar(data)
    },
    async oauth2AuthorizeUrl(provider, type) {
      const { data } = await axios.get(`${this.baseURL}/auth/oauth2/${provider}/authorize?type=${type}`)
      return data.authorizeUrl
    },
    async refresh() {
      const { data } = await axios.post(`${this.baseURL}/auth/refresh`, null, withCreds)
      this.guardar(data)
    },
    async logout() {
      try {
        await axios.post(`${this.baseURL}/auth/logout`, null, withCreds)
      } catch {
        /* token ya inválido */
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.accessToken = this.slug = this.nombreNegocio = null
      if (process.client) {
        sessionStorage.removeItem('rk_access')
        sessionStorage.removeItem('rk_slug')
        sessionStorage.removeItem('rk_nombre')
      }
    },
  },
})
