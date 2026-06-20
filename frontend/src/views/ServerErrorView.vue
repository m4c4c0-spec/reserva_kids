<script setup>
import charCake from '../assets/char-cake.png'
import BaseButton from '../components/BaseButton.vue'
</script>

<template>
  <main class="min-h-screen bg-surface flex flex-col items-center justify-center p-5 text-center">
    <img :src="charCake" alt="" class="w-28 h-28 object-contain drop-shadow-xl character-img mb-6" aria-hidden="true" />
    <h1 class="font-display font-extrabold text-4xl text-primary mb-2">Algo salió mal</h1>
    <p class="font-medium text-on-surface-variant text-sm mb-8 max-w-sm">
      Ocurrió un error inesperado. Intenta recargar la página.
    </p>
    <BaseButton variante="primario" @click="$router.go(0)"> Recargar </BaseButton>
  </main>
</template>
