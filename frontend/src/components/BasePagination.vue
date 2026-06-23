<script setup>
defineProps({
  paginaActual: { type: Number, required: true },
  totalPaginas: { type: Number, required: true },
})

const emit = defineEmits(['cambiarPagina'])
</script>

<template>
  <nav
    v-if="totalPaginas > 1"
    class="flex items-center justify-center gap-2 mt-4"
    role="navigation"
    aria-label="Paginación"
  >
    <button
      :disabled="paginaActual <= 0"
      class="p-2 rounded-full hover:bg-surface-container transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
      aria-label="Página anterior"
      @click="emit('cambiarPagina', paginaActual - 1)"
    >
      <span class="material-symbols-outlined text-base">chevron_left</span>
    </button>

    <span class="text-sm font-medium text-on-surface-variant px-2">
      Página {{ paginaActual + 1 }} de {{ totalPaginas }}
    </span>

    <button
      :disabled="paginaActual >= totalPaginas - 1"
      class="p-2 rounded-full hover:bg-surface-container transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
      aria-label="Página siguiente"
      @click="emit('cambiarPagina', paginaActual + 1)"
    >
      <span class="material-symbols-outlined text-base">chevron_right</span>
    </button>
  </nav>
</template>
