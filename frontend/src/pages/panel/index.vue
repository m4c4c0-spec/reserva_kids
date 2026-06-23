<script setup>
definePageMeta({
  layout: 'panel',
  requiereAuth: true,
  middleware: () => navigateTo('/panel/solicitudes', { replace: true }),
})
</script>
<template><div /></template>
