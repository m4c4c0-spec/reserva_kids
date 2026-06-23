import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useAuthStore } from '../../src/stores/auth'

vi.mock('axios', () => ({
  default: { post: vi.fn() },
}))

describe('useAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    axios.post.mockReset()
  })

  it('arranca sin sesión', () => {
    const auth = useAuthStore()
    expect(auth.autenticado).toBe(false)
    expect(auth.accessToken).toBeNull()
  })

  it('login guarda tokens y datos en el store y sessionStorage', async () => {
    axios.post.mockResolvedValue({
      data: { accessToken: 'tok-1', slug: 'fiestas', nombreNegocio: 'Fiestas Kids' },
    })
    const auth = useAuthStore()
    await auth.login('x@y.cl', 'pass1234')
    expect(auth.accessToken).toBe('tok-1')
    expect(auth.slug).toBe('fiestas')
    expect(auth.nombreNegocio).toBe('Fiestas Kids')
    expect(auth.autenticado).toBe(true)
    expect(sessionStorage.getItem('rk_access')).toBe('tok-1')
    expect(sessionStorage.getItem('rk_slug')).toBe('fiestas')
    expect(sessionStorage.getItem('rk_nombre')).toBe('Fiestas Kids')
  })

  it('logoutLocal limpia el store y sessionStorage', () => {
    const auth = useAuthStore()
    auth.guardar({ accessToken: 'tok-2', slug: 's', nombreNegocio: 'N' })
    auth.logoutLocal()
    expect(auth.autenticado).toBe(false)
    expect(auth.accessToken).toBeNull()
    expect(sessionStorage.getItem('rk_access')).toBeNull()
  })

  it('refresh llama a /auth/refresh y guarda los nuevos tokens', async () => {
    axios.post.mockResolvedValue({
      data: { accessToken: 'tok-3', slug: 's2', nombreNegocio: 'N2' },
    })
    const auth = useAuthStore()
    await auth.refresh()
    expect(auth.accessToken).toBe('tok-3')
    expect(axios.post).toHaveBeenCalledWith(
      expect.stringContaining('/auth/refresh'),
      null,
      expect.objectContaining({ withCredentials: true }),
    )
  })
})
