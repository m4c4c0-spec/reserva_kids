<script setup>
definePageMeta({
  layout: 'default',
  middleware: () => {
    if (import.meta.server) return navigateTo('/404', { replace: true })
    return navigateTo('/404', { replace: true, external: false })
  },
})
</script>
<template>
  <div />
</template>
