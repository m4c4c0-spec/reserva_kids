import { describe, it, expect } from 'vitest'
import { duracion } from '../../src/composables/useDuration'

describe('useDuration (duracion)', () => {
  it('formatea minutos sin hora', () => {
    expect(duracion(30)).toBe('30min')
    expect(duracion(45)).toBe('45min')
  })

  it('formatea horas exactas', () => {
    expect(duracion(60)).toBe('1h')
    expect(duracion(120)).toBe('2h')
  })

  it('formatea horas con minutos', () => {
    expect(duracion(90)).toBe('1h 30min')
    expect(duracion(150)).toBe('2h 30min')
  })

  it('devuelve 0min para null, undefined o cero', () => {
    expect(duracion(null)).toBe('0min')
    expect(duracion(undefined)).toBe('0min')
    expect(duracion(0)).toBe('0min')
  })

  it('devuelve 0min para valores negativos', () => {
    expect(duracion(-10)).toBe('0min')
  })
})
