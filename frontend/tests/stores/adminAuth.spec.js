import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useAdminAuthStore } from '../../src/stores/adminAuth'

vi.mock('axios', () => ({
  default: { post: vi.fn() },
}))

describe('useAdminAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    axios.post.mockReset()
  })

  it('arranca sin sesión', () => {
    const auth = useAdminAuthStore()
    expect(auth.autenticado).toBe(false)
    expect(auth.accessToken).toBeNull()
  })

  it('login guarda datos del admin en el store y sessionStorage', async () => {
    axios.post.mockResolvedValue({ data: { accessToken: 'adm-tok', email: 'ops@rk.cl', nombre: 'Operador' } })
    const auth = useAdminAuthStore()
    await auth.login('ops@rk.cl', 'pass1234')
    expect(auth.accessToken).toBe('adm-tok')
    expect(auth.email).toBe('ops@rk.cl')
    expect(auth.nombre).toBe('Operador')
    expect(auth.autenticado).toBe(true)
    expect(sessionStorage.getItem('rk_adm_access')).toBe('adm-tok')
    expect(axios.post).toHaveBeenCalledWith(
      expect.stringContaining('/admin-auth/login'),
      { email: 'ops@rk.cl', password: 'pass1234' },
      { withCredentials: true },
    )
  })

  it('logoutLocal limpia el store y sessionStorage', () => {
    const auth = useAdminAuthStore()
    auth.guardar({ accessToken: 't', email: 'e', nombre: 'n' })
    auth.logoutLocal()
    expect(auth.autenticado).toBe(false)
    expect(auth.accessToken).toBeNull()
    expect(sessionStorage.getItem('rk_adm_access')).toBeNull()
  })

  it('refresh llama a /admin-auth/refresh y guarda los nuevos tokens', async () => {
    axios.post.mockResolvedValue({ data: { accessToken: 'adm-tok-2', email: 'ops@rk.cl', nombre: 'Operador' } })
    const auth = useAdminAuthStore()
    await auth.refresh()
    expect(auth.accessToken).toBe('adm-tok-2')
    expect(axios.post).toHaveBeenCalledWith(expect.stringContaining('/admin-auth/refresh'), null, {
      withCredentials: true,
    })
  })
})
