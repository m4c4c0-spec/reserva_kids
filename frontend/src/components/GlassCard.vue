<script setup>
defineProps({
  clase: { type: String, default: '' }
})
</script>

<template>
  <div
    class="glass-card rounded-3xl shadow-soft border border-outline-variant/20 p-5"
    :class="clase"
  >
    <slot />
  </div>
</template>
