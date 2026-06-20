<script setup>
defineProps({
  mensaje: { type: String, required: true },
  icono: { type: String, default: 'inbox' },
})
</script>

<template>
  <div class="flex flex-col items-center justify-center py-12 gap-3 text-center">
    <span class="material-symbols-outlined text-5xl text-on-surface-variant/40">{{ icono }}</span>
    <p class="text-sm font-medium text-on-surface-variant max-w-xs">{{ mensaje }}</p>
    <slot name="acciones" />
  </div>
</template>
