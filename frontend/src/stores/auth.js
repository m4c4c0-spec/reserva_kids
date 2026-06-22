import { defineStore } from 'pinia'
import axios from 'axios'

// dev: '' → '/api' relativo (proxy de Vite); prod: VITE_API_URL con el dominio
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'
const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }

export const useAuthStore = defineStore('auth', {
  state: () => ({
    // El refresh token NO se guarda aquí: vive en una cookie HttpOnly que el JS no
    // puede leer (mitigación de robo por XSS). Solo persiste el access token (corto).
    accessToken: sessionStorage.getItem('rk_access') || null,
    slug: sessionStorage.getItem('rk_slug') || null,
    nombreNegocio: sessionStorage.getItem('rk_nombre') || null,
  }),
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(tokens) {
      this.accessToken = tokens.accessToken
      this.slug = tokens.slug
      this.nombreNegocio = tokens.nombreNegocio
      sessionStorage.setItem('rk_access', tokens.accessToken)
      sessionStorage.setItem('rk_slug', tokens.slug)
      sessionStorage.setItem('rk_nombre', tokens.nombreNegocio)
    },
    async login(email, password) {
      // withCredentials envía/recibe la cookie HttpOnly del refresh token.
      const { data } = await axios.post(`${baseURL}/auth/login`, { email, password }, withCreds)
      this.guardar(data)
    },
    async registrar(payload) {
      const { data } = await axios.post(`${baseURL}/auth/register`, payload, withCreds)
      this.guardar(data)
    },
    /** V27: login con magic link — el token viajó por email y arrive en el fragment de la URL. */
    async entrarConMagicLink(token) {
      const { data } = await axios.post(`${baseURL}/auth/magic/entrar`, { token }, withCreds)
      this.guardar(data)
    },
    /** V27: pedir que envíen un magic link al correo (login sin contraseña). */
    async pedirMagicLink(email) {
      await axios.post(`${baseURL}/auth/magic/solicitar`, { email }, withCreds)
    },
    async refresh() {
      // Sin body y sin el interceptor del api: el refresh token va en la cookie HttpOnly
      // (withCredentials). Usar axios directo evita un loop si el refresh responde 401.
      const { data } = await axios.post(`${baseURL}/auth/refresh`, null, withCreds)
      this.guardar(data)
    },
    async logout() {
      try {
        await axios.post(`${baseURL}/auth/logout`, null, withCreds)
      } catch {
        /* token ya inválido */
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.accessToken = this.slug = this.nombreNegocio = null
      sessionStorage.removeItem('rk_access')
      sessionStorage.removeItem('rk_slug')
      sessionStorage.removeItem('rk_nombre')
    },
  },
})
