import { describe, it, expect, vi } from 'vitest'
import { useAsync } from '../../src/composables/useAsync'

describe('useAsync', () => {
  it('devuelve el resultado y deja cargando en false tras éxito', async () => {
    const { cargando, error, ejecutar } = useAsync(async () => 42)
    const promise = ejecutar()
    expect(cargando.value).toBe(true)
    const resultado = await promise
    expect(resultado).toBe(42)
    expect(cargando.value).toBe(false)
    expect(error.value).toBeNull()
  })

  it('setea error con el mensaje del backend y re-lanza', async () => {
    const { error, ejecutar } = useAsync(async () => {
      throw { response: { data: { message: 'falló el servidor' } } }
    })
    await expect(ejecutar()).rejects.toThrow()
    expect(error.value).toBe('falló el servidor')
  })

  it('usa mensaje genérico si la respuesta no trae message', async () => {
    const { error, ejecutar } = useAsync(async () => {
      throw new Error('x')
    })
    await expect(ejecutar()).rejects.toThrow()
    expect(error.value).toBe('Error en la operación')
  })

  it('pasa argumentos a la función', async () => {
    const fn = vi.fn(async (a, b) => a + b)
    const { ejecutar } = useAsync(fn)
    const r = await ejecutar(2, 3)
    expect(fn).toHaveBeenCalledWith(2, 3)
    expect(r).toBe(5)
  })
})
