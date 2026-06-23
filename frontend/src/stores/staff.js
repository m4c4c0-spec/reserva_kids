import { defineStore } from 'pinia'
import { ref } from 'vue'
import axios from 'axios'

export const useStaffStore = defineStore('staff', () => {
  const config = useRuntimeConfig?.()
  const baseURL = (config?.public?.apiUrl || '') + '/api'

  const autenticado = ref(false)
  const staffId = ref(null)
  const nombre = ref('')
  const rol = ref('')
  const tenantId = ref(null)
  const accessToken = ref('')
  const refreshToken = ref('')

  async function login(email, password) {
    const { data } = await axios.post(`${baseURL}/staff/login`, { email, password })
    autenticado.value = true
    staffId.value = data.staffId
    nombre.value = data.nombre
    rol.value = data.rol
    tenantId.value = data.tenantId
    accessToken.value = data.accessToken
    refreshToken.value = data.refreshToken
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
  }

  return { autenticado, staffId, nombre, rol, tenantId, accessToken, refreshToken, login, logout }
})
