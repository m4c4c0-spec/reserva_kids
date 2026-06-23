import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { useClienteAuthStore } from '../../src/stores/clienteAuth'

vi.mock('axios', () => ({
  default: { post: vi.fn() },
}))

describe('useClienteAuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    axios.post.mockReset()
  })

  it('arranca sin sesión', () => {
    const auth = useClienteAuthStore()
    expect(auth.autenticado).toBe(false)
    expect(auth.accessToken).toBeNull()
  })

  it('login guarda datos del cliente en el store y sessionStorage', async () => {
    axios.post.mockResolvedValue({
      data: { accessToken: 'cli-tok', email: 'c@y.cl', nombre: 'Ana', telefono: '+569123' },
    })
    const auth = useClienteAuthStore()
    await auth.login('c@y.cl', 'pass1234')
    expect(auth.accessToken).toBe('cli-tok')
    expect(auth.email).toBe('c@y.cl')
    expect(auth.nombre).toBe('Ana')
    expect(auth.telefono).toBe('+569123')
    expect(auth.autenticado).toBe(true)
    expect(sessionStorage.getItem('rk_cli_access')).toBe('cli-tok')
    expect(sessionStorage.getItem('rk_cli_email')).toBe('c@y.cl')
  })

  it('logoutLocal limpia el store y sessionStorage', () => {
    const auth = useClienteAuthStore()
    auth.guardar({ accessToken: 't', email: 'e', nombre: 'n', telefono: 't' })
    auth.logoutLocal()
    expect(auth.autenticado).toBe(false)
    expect(auth.accessToken).toBeNull()
    expect(sessionStorage.getItem('rk_cli_access')).toBeNull()
  })

  it('refresh llama a /cliente-auth/refresh y guarda los nuevos tokens', async () => {
    axios.post.mockResolvedValue({
      data: { accessToken: 'cli-tok-2', email: 'c2@y.cl', nombre: 'Ana2', telefono: '+569999' },
    })
    const auth = useClienteAuthStore()
    await auth.refresh()
    expect(auth.accessToken).toBe('cli-tok-2')
    expect(axios.post).toHaveBeenCalledWith(
      expect.stringContaining('/cliente-auth/refresh'),
      null,
      expect.objectContaining({ withCredentials: true }),
    )
  })
})
