<script setup>
import { ref } from 'vue'
import * as clienteService from '../services/clienteService'
import bgV2 from '../assets/login-bg-v2.webp'
import charHat from '../assets/char-hat.png'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const email = ref('')
const enviado = ref(false)
const error = ref('')
const cargando = ref(false)

async function solicitar() {
  error.value = ''
  cargando.value = true
  try {
    await clienteService.solicitarReset(email.value)
    enviado.value = true
  } catch {
    error.value = 'Error de conexión, intenta de nuevo'
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <main
    class="min-h-screen bg-surface bg-cover bg-center flex flex-col justify-center items-center p-5 relative overflow-hidden"
    :style="{ backgroundImage: `url(${bgV2})` }"
  >
    <div class="w-full max-w-md relative z-10">
      <div class="text-center mb-8">
        <div class="flex justify-center mb-4 h-28">
          <img
            :src="charHat"
            alt=""
            class="w-28 h-28 object-contain drop-shadow-xl character-img animate-floating"
            aria-hidden="true"
          />
        </div>
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Recuperar contraseña</h1>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft space-y-4">
        <p
          v-if="enviado"
          class="text-sm font-semibold text-on-secondary-container bg-secondary-fixed rounded-xl px-3 py-2 text-center"
        >
          Si el correo existe, te enviamos un enlace para crear una contraseña nueva. Revisa tu bandeja (vence en 30
          minutos).
        </p>
        <form v-else class="space-y-4" @submit.prevent="solicitar">
          <p class="font-medium text-on-surface-variant text-center">Te enviaremos un enlace al correo de tu cuenta.</p>
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="cr-email">Email</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >mail</span
              >
              <input
                id="cr-email"
                v-model="email"
                type="email"
                required
                placeholder="tu@correo.com"
                class="input-festivo input-festivo--con-icono !py-3"
              />
            </div>
          </div>
          <ErrorBanner :mensaje="error" />
          <BaseButton
            variante="primario"
            type="submit"
            :cargando="cargando"
            :deshabilitado="cargando"
            class="w-full py-3"
          >
            {{ cargando ? 'Enviando…' : 'Enviar enlace' }}
          </BaseButton>
        </form>

        <RouterLink
          to="/clientes/entrar"
          class="block w-full font-medium text-primary hover:text-primary-container text-center transition-colors"
        >
          Volver a entrar
        </RouterLink>
      </div>
    </div>
  </main>
</template>
