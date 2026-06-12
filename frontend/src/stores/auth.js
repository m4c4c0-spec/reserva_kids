import { defineStore } from 'pinia'
import axios from 'axios'

const baseURL = (import.meta.env.VITE_API_URL || 'http://localhost:8080') + '/api'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    accessToken: sessionStorage.getItem('rk_access') || null,
    refreshToken: sessionStorage.getItem('rk_refresh') || null,
    slug: sessionStorage.getItem('rk_slug') || null,
    nombreNegocio: sessionStorage.getItem('rk_nombre') || null,
  }),
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(tokens) {
      this.accessToken = tokens.accessToken
      this.refreshToken = tokens.refreshToken
      this.slug = tokens.slug
      this.nombreNegocio = tokens.nombreNegocio
      sessionStorage.setItem('rk_access', tokens.accessToken)
      sessionStorage.setItem('rk_refresh', tokens.refreshToken)
      sessionStorage.setItem('rk_slug', tokens.slug)
      sessionStorage.setItem('rk_nombre', tokens.nombreNegocio)
    },
    async login(email, password) {
      const { data } = await axios.post(`${baseURL}/auth/login`, { email, password })
      this.guardar(data)
    },
    async registrar(payload) {
      const { data } = await axios.post(`${baseURL}/auth/register`, payload)
      this.guardar(data)
    },
    async refresh() {
      const { data } = await axios.post(`${baseURL}/auth/refresh`, {
        refreshToken: this.refreshToken,
      })
      this.guardar(data)
    },
    logoutLocal() {
      this.accessToken = this.refreshToken = this.slug = this.nombreNegocio = null
      sessionStorage.clear()
    },
  },
})
