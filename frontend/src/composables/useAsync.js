import { ref } from 'vue'

export function useAsync(fn) {
  const cargando = ref(false)
  const error = ref(null)

  async function ejecutar(...args) {
    cargando.value = true
    error.value = null
    try {
      return await fn(...args)
    } catch (e) {
      error.value = e.response?.data?.message || 'Error en la operación'
      throw e
    } finally {
      cargando.value = false
    }
  }

  return { cargando, error, ejecutar }
}
