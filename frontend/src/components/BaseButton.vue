<script setup>
defineProps({
  variante: { type: String, default: 'primario' },
  deshabilitado: { type: Boolean, default: false },
  cargando: { type: Boolean, default: false }
})
</script>

<template>
  <button
    :disabled="deshabilitado || cargando"
    class="inline-flex items-center justify-center gap-2 rounded-full px-4 py-2 font-bold text-sm transition-all active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100"
    :class="{
      'bg-primary-container text-on-primary-container shadow-md hover:bg-primary-fixed hover:shadow-lifted': variante === 'primario',
      'border-2 border-outline-variant text-on-surface-variant hover:bg-surface-container': variante === 'secundario',
      'bg-error-container text-on-error-container shadow-md hover:shadow-lifted': variante === 'peligro'
    }"
  >
    <span v-if="cargando" class="material-symbols-outlined text-base animate-spin">progress_activity</span>
    <slot />
  </button>
</template>
