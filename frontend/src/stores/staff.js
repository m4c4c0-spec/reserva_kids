import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

// Persistencia en sessionStorage para no perder la sesión al recargar (consistente con
// los stores auth/clienteAuth/adminAuth). NOTA: el backend de staff aún no expone un
// endpoint de refresh ni cookie de refresh httpOnly; cuando exista, agregar refresh() aquí.
const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }

function leer(key) {
  return (typeof window !== 'undefined' && sessionStorage.getItem(key)) || ''
}

export const useStaffStore = defineStore('staff', () => {
  const config = useRuntimeConfig?.()
  const baseURL = (config?.public?.apiUrl || '') + '/api'

  const accessToken = ref(leer('rk_staff_access'))
  const refreshToken = ref(leer('rk_staff_refresh'))
  const staffId = ref(leer('rk_staff_id') || null)
  const nombre = ref(leer('rk_staff_nombre'))
  const rol = ref(leer('rk_staff_rol'))
  const tenantId = ref(leer('rk_staff_tenant') || null)
  const autenticado = ref(!!accessToken.value)

  function persistir(data) {
    if (typeof window === 'undefined') return
    sessionStorage.setItem('rk_staff_access', data.accessToken ?? '')
    sessionStorage.setItem('rk_staff_refresh', data.refreshToken ?? '')
    sessionStorage.setItem('rk_staff_id', data.staffId ?? '')
    sessionStorage.setItem('rk_staff_nombre', data.nombre ?? '')
    sessionStorage.setItem('rk_staff_rol', data.rol ?? '')
    sessionStorage.setItem('rk_staff_tenant', data.tenantId ?? '')
  }

  async function login(email, password) {
    const { data } = await axios.post(`${baseURL}/staff/login`, { email, password }, withCreds)
    autenticado.value = true
    staffId.value = data.staffId
    nombre.value = data.nombre
    rol.value = data.rol
    tenantId.value = data.tenantId
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
    persistir(data)
    return data
  }

  function logout() {
    autenticado.value = false
    staffId.value = null
    nombre.value = ''
    rol.value = ''
    tenantId.value = null
    accessToken.value = ''
    refreshToken.value = ''
    if (typeof window !== 'undefined') {
      ;['access', 'refresh', 'id', 'nombre', 'rol', 'tenant'].forEach((k) => sessionStorage.removeItem(`rk_staff_${k}`))
    }
  }

  return { autenticado, staffId, nombre, rol, tenantId, accessToken, refreshToken, login, logout }
})
