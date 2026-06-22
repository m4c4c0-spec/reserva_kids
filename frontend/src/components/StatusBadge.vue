<script setup>
// Traduce el enum técnico del backend a una etiqueta humana en español.
// Antes mostraba el enum crudo en MAYÚSCULAS (CONFIRMADA, EN_ESPERA…), jerga
// para el usuario objetivo. Mapea estados de reserva y de bloque de agenda.
defineProps({
  estado: { type: String, required: true },
})

const colores = {
  // Reserva
  PENDIENTE: 'bg-tertiary-fixed text-on-tertiary-fixed',
  PENDIENTE_PAGO: 'bg-tertiary-fixed text-on-tertiary-fixed',
  COTIZADA: 'bg-secondary-fixed text-on-secondary-container',
  CONFIRMADA: 'bg-primary-fixed text-on-primary-container',
  REALIZADA: 'bg-surface-container text-on-surface',
  CANCELADA: 'bg-error-container text-on-error-container',
  // Bloque de agenda
  DISPONIBLE: 'bg-[#dcfce7] text-green-800',
  EN_ESPERA: 'bg-tertiary-fixed text-on-tertiary-fixed',
  CONFIRMADO: 'bg-primary-fixed text-on-primary-container',
}

// Etiquetas en lenguaje llano (lo que ve el dueño).
const etiquetas = {
  PENDIENTE: 'Nueva solicitud',
  PENDIENTE_PAGO: 'Esperando pago',
  COTIZADA: 'Cotizada',
  CONFIRMADA: 'Confirmada',
  REALIZADA: 'Realizada',
  CANCELADA: 'Cancelada',
  DISPONIBLE: 'Disponible',
  EN_ESPERA: 'En espera',
  CONFIRMADO: 'Reservado',
}
</script>

<template>
  <span
    class="inline-block text-xs font-bold rounded-full px-3 py-1"
    :class="colores[estado] || 'bg-surface-highest text-on-surface-variant'"
  >
    {{ etiquetas[estado] || estado }}
  </span>
</template>
