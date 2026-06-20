<script setup>
defineProps({
  mensaje: { type: String, default: '' },
})
</script>

<template>
  <p
    v-if="mensaje"
    class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2"
    role="alert"
    aria-live="polite"
  >
    {{ mensaje }}
  </p>
</template>
