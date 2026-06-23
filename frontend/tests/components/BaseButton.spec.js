import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import BaseButton from '../../src/components/BaseButton.vue'

describe('BaseButton', () => {
  it('renderiza el texto del slot', () => {
    const wrapper = mount(BaseButton, { slots: { default: 'Guardar' } })
    expect(wrapper.text()).toContain('Guardar')
  })

  it('aplica la clase de variante primaria por defecto', () => {
    const wrapper = mount(BaseButton)
    expect(wrapper.find('button').classes()).toContain('btn-festivo--primario')
  })

  it('aplica la clase secundaria cuando variante="secundario"', () => {
    const wrapper = mount(BaseButton, { props: { variante: 'secundario' } })
    expect(wrapper.find('button').classes()).toContain('btn-festivo--secundario')
  })

  it('aplica la clase de peligro cuando variante="peligro"', () => {
    const wrapper = mount(BaseButton, { props: { variante: 'peligro' } })
    expect(wrapper.find('button').classes()).toContain('btn-festivo--peligro')
  })

  it('deshabilita el botón cuando deshabilitado=true', () => {
    const wrapper = mount(BaseButton, { props: { deshabilitado: true } })
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('deshabilita el botón cuando cargando=true', () => {
    const wrapper = mount(BaseButton, { props: { cargando: true } })
    expect(wrapper.find('button').attributes('disabled')).toBeDefined()
  })

  it('muestra el spinner cuando cargando=true', () => {
    const wrapper = mount(BaseButton, { props: { cargando: true } })
    expect(wrapper.find('.material-symbols-outlined').exists()).toBe(true)
  })

  it('no muestra el spinner cuando cargando=false', () => {
    const wrapper = mount(BaseButton, { props: { cargando: false } })
    expect(wrapper.find('.material-symbols-outlined').exists()).toBe(false)
  })
})
