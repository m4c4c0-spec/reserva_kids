// Validadores del lado del cliente — espejan las reglas del backend para dar
// feedback inmediato y evitar que el usuario envíe datos que el backend rechazaría.
// RUT: algoritmo Módulo 11 (igual que cl.reservakids.domain.model.RutValidator).
// Teléfono: misma normalización que Cliente.normalizarTelefono (celular chileno).

function calcularDv(numero) {
  let suma = 0
  let factor = 2
  for (let i = numero.length - 1; i >= 0; i--) {
    suma += parseInt(numero[i], 10) * factor
    factor = factor === 7 ? 2 : factor + 1
  }
  const dv = 11 - (suma % 11)
  if (dv === 11) return '0'
  if (dv === 10) return 'K'
  return String(dv)
}

/** Valida un RUT chileno (acepta con o sin puntos/guión). */
export function esRutValido(rut) {
  if (!rut) return false
  const limpio = rut.replace(/[^0-9kK]/g, '').toUpperCase()
  if (limpio.length < 2) return false
  const numero = limpio.slice(0, -1)
  const dv = limpio.slice(-1)
  if (!/^\d+$/.test(numero)) return false
  if (!/^[0-9K]$/.test(dv)) return false
  return dv === calcularDv(numero)
}

/** Formatea un RUT para mostrar: "123456785" → "12.345.678-5". */
export function formatearRut(rut) {
  const limpio = (rut || '').replace(/[^0-9kK]/g, '').toUpperCase()
  if (limpio.length < 2) return limpio
  const numero = limpio.slice(0, -1).replace(/\B(?=(\d{3})+(?!\d))/g, '.')
  return `${numero}-${limpio.slice(-1)}`
}

/** Valida un celular chileno: 9 dígitos partiendo en 9, o con código país 56. */
export function esTelefonoChilenoValido(tel) {
  if (!tel) return false
  let d = tel.replace(/\D/g, '')
  if (d.startsWith('00')) d = d.slice(2)
  return /^9\d{8}$/.test(d) || /^569\d{8}$/.test(d)
}
