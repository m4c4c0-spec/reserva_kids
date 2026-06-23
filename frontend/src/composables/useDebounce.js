import { ref } from 'vue'

/**
 * Composado para debounce de valores reactivos (búsquedas, filtros).
 *
 * Uso:
 *   const { valorDebounced } = useDebounce(terminoBusqueda, 300)
 *   watch(valorDebounced, (q) => buscar(q))
 */
export function useDebounce(valor, demora = 300) {
  const valorDebounced = ref(valor.value)
  let timer = null

  const actualizar = (nuevoValor) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      valorDebounced.value = nuevoValor
    }, demora)
  }

  // Sincronización inicial
  actualizar(valor.value)

  return { valorDebounced, actualizar }
}
