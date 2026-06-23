<script setup>
definePageMeta({
  layout: 'admin',
  requiereAdmin: true,
  middleware: () => navigateTo('/admin/metricas', { replace: true }),
})
</script>
<template><div /></template>
