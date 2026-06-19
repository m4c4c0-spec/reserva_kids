export function clp(valor) {
  return (valor || 0).toLocaleString('es-CL', { style: 'currency', currency: 'CLP' })
}
