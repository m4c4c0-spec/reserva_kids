import { defineStore } from 'pinia'
import axios from 'axios'

// dev: '' → '/api' relativo (proxy de Vite); prod: VITE_API_URL con el dominio
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'
const withCreds = { withCredentials: true }

// Administrador de plataforma: sesión SEPARADA de dueño y apoderado (claves propias de
// sessionStorage) para que las tres puedan coexistir. El refresh token vive en una cookie
// HttpOnly propia (rk_admin_refresh), fuera del alcance del JS. No hay auto-registro:
// el primer admin se crea por bootstrap (env) en el backend.
export const useAdminAuthStore = defineStore('adminAuth', {
  state: () => ({
    accessToken: sessionStorage.getItem('rk_adm_access') || null,
    email: sessionStorage.getItem('rk_adm_email') || null,
    nombre: sessionStorage.getItem('rk_adm_nombre') || null,
  }),
  getters: {
    autenticado: (s) => !!s.accessToken,
  },
  actions: {
    guardar(data) {
      this.accessToken = data.accessToken
      this.email = data.email
      this.nombre = data.nombre
      sessionStorage.setItem('rk_adm_access', data.accessToken)
      sessionStorage.setItem('rk_adm_email', data.email || '')
      sessionStorage.setItem('rk_adm_nombre', data.nombre || '')
    },
    async login(email, password) {
      const { data } = await axios.post(`${baseURL}/admin-auth/login`, { email, password }, withCreds)
      this.guardar(data)
    },
    async refresh() {
      // Sin body y sin el interceptor del api: el refresh token va en la cookie HttpOnly
      // (withCredentials). Usar axios directo evita un loop si el refresh responde 401.
      const { data } = await axios.post(`${baseURL}/admin-auth/refresh`, null, withCreds)
      this.guardar(data)
    },
    async logout() {
      try {
        await axios.post(`${baseURL}/admin-auth/logout`, null, withCreds)
      } catch {
        /* token ya inválido */
      }
      this.logoutLocal()
    },
    logoutLocal() {
      this.accessToken = this.email = this.nombre = null
      sessionStorage.removeItem('rk_adm_access')
      sessionStorage.removeItem('rk_adm_email')
      sessionStorage.removeItem('rk_adm_nombre')
    },
  },
})
