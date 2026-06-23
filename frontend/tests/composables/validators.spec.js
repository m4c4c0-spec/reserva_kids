import { describe, it, expect } from 'vitest'
import { esRutValido, formatearRut, esTelefonoChilenoValido } from '../../src/composables/validators'

describe('esRutValido', () => {
  it('acepta RUT válido con y sin formato', () => {
    expect(esRutValido('11.111.111-1')).toBe(true)
    expect(esRutValido('111111111')).toBe(true)
  })

  it('acepta dígito verificador K', () => {
    expect(esRutValido('1.000.005-K')).toBe(true)
    expect(esRutValido('1000005k')).toBe(true)
  })

  it('rechaza dígito verificador incorrecto', () => {
    expect(esRutValido('11.111.111-2')).toBe(false)
  })

  it('rechaza vacío o incompleto', () => {
    expect(esRutValido('')).toBe(false)
    expect(esRutValido(null)).toBe(false)
    expect(esRutValido('1')).toBe(false)
  })
})

describe('formatearRut', () => {
  it('agrega puntos y guión', () => {
    expect(formatearRut('111111111')).toBe('11.111.111-1')
    expect(formatearRut('44444444K')).toBe('44.444.444-K')
  })
})

describe('esTelefonoChilenoValido', () => {
  it('acepta celular de 9 dígitos partiendo en 9', () => {
    expect(esTelefonoChilenoValido('912345678')).toBe(true)
    expect(esTelefonoChilenoValido('+56 9 1234 5678')).toBe(true)
  })

  it('acepta con código país 56', () => {
    expect(esTelefonoChilenoValido('56912345678')).toBe(true)
    expect(esTelefonoChilenoValido('0056912345678')).toBe(true)
  })

  it('rechaza fijos, cortos o vacíos', () => {
    expect(esTelefonoChilenoValido('221234567')).toBe(false)
    expect(esTelefonoChilenoValido('12345')).toBe(false)
    expect(esTelefonoChilenoValido('')).toBe(false)
  })
})
