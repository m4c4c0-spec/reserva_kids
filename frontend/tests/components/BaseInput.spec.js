import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import BaseInput from '../../src/components/BaseInput.vue'

describe('BaseInput', () => {
  it('renderiza el valor de modelValue', () => {
    const wrapper = mount(BaseInput, { props: { modelValue: 'hola' } })
    expect(wrapper.find('input').element.value).toBe('hola')
  })

  it('emite update:modelValue al escribir', async () => {
    const wrapper = mount(BaseInput, { props: { modelValue: '' } })
    await wrapper.find('input').setValue('nuevo')
    expect(wrapper.emitted('update:modelValue')).toBeTruthy()
    expect(wrapper.emitted('update:modelValue')[0]).toEqual(['nuevo'])
  })

  it('respeta tipo, placeholder, requerido y deshabilitado', () => {
    const wrapper = mount(BaseInput, {
      props: { modelValue: '', tipo: 'password', placeholder: 'clave', requerido: true, deshabilitado: true },
    })
    const input = wrapper.find('input')
    expect(input.attributes('type')).toBe('password')
    expect(input.attributes('placeholder')).toBe('clave')
    expect(input.attributes('required')).toBeDefined()
    expect(input.attributes('disabled')).toBeDefined()
  })
})
