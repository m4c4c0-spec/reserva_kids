import { mount } from '@vue/test-utils'
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import BaseToast from '../../src/components/BaseToast.vue'

describe('BaseToast', () => {
  beforeEach(() => vi.useFakeTimers())
  afterEach(() => vi.useRealTimers())

  it('no renderiza nada cuando mensaje está vacío', () => {
    const wrapper = mount(BaseToast, { props: { mensaje: '' } })
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('se muestra cuando llega un mensaje y emite cerrar tras la duración', async () => {
    const wrapper = mount(BaseToast, { props: { mensaje: 'Guardado', duracion: 1000 } })
    expect(wrapper.find('[role="alert"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Guardado')

    vi.advanceTimersByTime(1000)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('cerrar')).toBeTruthy()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('el botón cerrar emite el evento inmediatamente y oculta el toast', async () => {
    const wrapper = mount(BaseToast, { props: { mensaje: 'OK', duracion: 5000 } })
    await wrapper.find('button[aria-label="Cerrar"]').trigger('click')
    expect(wrapper.emitted('cerrar')).toBeTruthy()
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('clase de error cuando tipo="error", éxito en caso contrario', () => {
    const err = mount(BaseToast, { props: { mensaje: 'falló', tipo: 'error' } })
    expect(
      err
        .find('[role="alert"]')
        .classes()
        .some((c) => c.includes('error-container')),
    ).toBe(true)

    const ok = mount(BaseToast, { props: { mensaje: 'ok', tipo: 'exito' } })
    expect(
      ok
        .find('[role="alert"]')
        .classes()
        .some((c) => c.includes('tertiary-fixed')),
    ).toBe(true)
  })

  it('tiene aria-live="polite" y role="alert" para accesibilidad', () => {
    const wrapper = mount(BaseToast, { props: { mensaje: 'x' } })
    const el = wrapper.find('[role="alert"]')
    expect(el.attributes('aria-live')).toBe('polite')
  })
})
