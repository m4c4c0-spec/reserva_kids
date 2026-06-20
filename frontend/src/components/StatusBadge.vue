<script setup>
defineProps({
  estado: { type: String, required: true },
})

const colores = {
  PENDIENTE: 'bg-tertiary-fixed text-on-tertiary-fixed',
  PENDIENTE_PAGO: 'bg-tertiary-fixed text-on-tertiary-fixed',
  COTIZADA: 'bg-secondary-fixed text-on-secondary-container',
  CONFIRMADA: 'bg-primary-fixed text-on-primary-container',
  REALIZADA: 'bg-surface-container text-on-surface',
  CANCELADA: 'bg-error-container text-on-error-container',
}
</script>

<template>
  <span
    class="inline-block text-xs font-bold rounded-full px-3 py-1"
    :class="colores[estado] || 'bg-surface-highest text-on-surface-variant'"
  >
    {{ estado }}
  </span>
</template>
