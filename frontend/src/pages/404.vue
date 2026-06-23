<script setup>
import charBalloon from '../assets/char-balloon.png'

definePageMeta({ layout: 'default', name: 'not-found' })
</script>

<template>
  <main class="min-h-screen bg-surface flex flex-col items-center justify-center p-5 text-center">
    <img
      :src="charBalloon"
      alt=""
      class="w-32 h-32 object-contain drop-shadow-xl character-img animate-floating mb-6"
      aria-hidden="true"
    />
    <h1 class="font-display font-extrabold text-5xl text-primary mb-2">404</h1>
    <p class="font-display font-bold text-xl text-on-surface mb-1">Página no encontrada</p>
    <p class="font-medium text-on-surface-variant text-sm mb-8 max-w-sm">
      La página que buscas no existe o fue movida.
    </p>
    <NuxtLink to="/" class="btn-festivo btn-festivo--primario">Volver al inicio</NuxtLink>
  </main>
</template>
