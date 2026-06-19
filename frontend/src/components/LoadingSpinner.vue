<script setup>
defineProps({
  mensaje: { type: String, default: 'Cargando...' }
})
</script>

<template>
  <div class="flex flex-col items-center justify-center py-12 gap-3" role="status" aria-live="polite">
    <span class="material-symbols-outlined text-4xl text-primary animate-spin">progress_activity</span>
    <p class="text-sm font-medium text-on-surface-variant">{{ mensaje }}</p>
  </div>
</template>
