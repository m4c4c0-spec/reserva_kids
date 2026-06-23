<script setup>
defineProps({
  variante: { type: String, default: 'primario' },
  deshabilitado: { type: Boolean, default: false },
  cargando: { type: Boolean, default: false },
})
</script>

<template>
  <button
    :disabled="deshabilitado || cargando"
    class="btn-festivo"
    :class="{
      'btn-festivo--primario': variante === 'primario',
      'btn-festivo--secundario': variante === 'secundario',
      'btn-festivo--peligro': variante === 'peligro',
    }"
  >
    <span v-if="cargando" class="material-symbols-outlined text-base animate-spin">progress_activity</span>
    <slot />
  </button>
</template>
