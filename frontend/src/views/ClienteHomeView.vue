<script setup>
import { useRouter } from 'vue-router'
import { useClienteAuthStore } from '../stores/clienteAuth'
import bgV2 from '../assets/login-bg-v2.webp'
import charHat from '../assets/char-hat.png'

const router = useRouter()
const auth = useClienteAuthStore()

// Primer nombre para un saludo más cercano (el resto del nombre se omite).
const primerNombre = (auth.nombre || '').trim().split(/\s+/)[0] || ''

function salir() {
  auth.logout()
  router.push('/clientes/entrar')
}
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed"
        :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="max-w-2xl mx-auto p-5 space-y-8">
      <header class="flex items-center justify-end pt-6">
        <button @click="salir"
                class="flex items-center gap-1 text-sm font-bold text-on-surface-variant hover:text-primary transition-colors">
          <span class="material-symbols-outlined text-[20px]">logout</span> Salir
        </button>
      </header>

      <section class="text-center">
        <img :src="charHat" alt="" class="w-28 h-28 object-contain mx-auto drop-shadow-xl character-img animate-floating" />
        <h1 class="font-display font-extrabold text-3xl md:text-4xl text-primary tracking-tight mt-2">
          ¡Hola{{ primerNombre ? `, ${primerNombre}` : '' }}! 👋
        </h1>
        <p class="text-on-surface-variant font-medium mt-2 max-w-md mx-auto">
          Bienvenido. Elige un negocio, arma tu combo de servicios y agenda el día y la hora que
          más te acomode. Te confirmamos por WhatsApp y correo.
        </p>
      </section>

      <div class="glass-card rounded-3xl p-6 shadow-soft space-y-4">
        <button @click="router.push('/clientes/negocios')"
                class="w-full flex justify-center items-center py-4 px-4 rounded-full shadow-md font-bold text-on-primary-container bg-primary-container hover:bg-primary-fixed hover:shadow-lifted focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-container transition-all duration-200 active:scale-95">
          <span class="material-symbols-outlined mr-2">event_available</span>
          Agendar una hora
        </button>
        <p class="text-center text-sm font-medium text-on-surface-variant">
          Verás solo los negocios con horarios disponibles.
        </p>
      </div>
    </div>
  </main>
</template>
