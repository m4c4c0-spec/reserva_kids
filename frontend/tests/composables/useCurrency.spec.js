import { describe, it, expect } from 'vitest'
import { clp } from '../../src/composables/useCurrency'

const fmt = (n) => n.toLocaleString('es-CL', { style: 'currency', currency: 'CLP' })

describe('useCurrency (clp)', () => {
  it('formatea un monto como pesos chilenos', () => {
    expect(clp(15000)).toBe(fmt(15000))
  })

  it('devuelve $0 para null o undefined', () => {
    expect(clp(null)).toBe(fmt(0))
    expect(clp(undefined)).toBe(fmt(0))
  })

  it('devuelve $0 para 0', () => {
    expect(clp(0)).toBe(fmt(0))
  })
})
