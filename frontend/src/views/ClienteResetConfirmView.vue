<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as clienteService from '../services/clienteService'
import bgV2 from '../assets/login-bg-v2.webp'
import charHat from '../assets/char-hat.png'
import BaseButton from '../components/BaseButton.vue'
import BaseToast from '../components/BaseToast.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const route = useRoute()
const router = useRouter()

// RNF-07: el token viaja en fragment (#token=...) para no quedar en logs del proxy
const token = computed(() => {
  const hash = window.location.hash.substring(1)
  const params = new URLSearchParams(hash)
  return params.get('token') || ''
})
const password = ref('')
const error = ref('')
const cargando = ref(false)
const toastExito = ref('')

async function confirmar() {
  error.value = ''
  cargando.value = true
  try {
    await clienteService.confirmarReset(token.value, password.value)
    toastExito.value = 'Contraseña actualizada. Inicia sesión con la nueva.'
    setTimeout(() => router.push('/clientes/entrar'), 2000)
  } catch (e) {
    error.value = e.response?.data?.message || 'El enlace es inválido o ya venció'
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
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Nueva contraseña</h1>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft space-y-4">
        <form class="space-y-4" @submit.prevent="confirmar">
          <p class="font-medium text-on-surface-variant text-center">Escribe tu contraseña nueva.</p>
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="cr-pass">Contraseña nueva (mín. 8)</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >lock_reset</span
              >
              <input
                id="cr-pass"
                v-model="password"
                type="password"
                required
                minlength="8"
                placeholder="••••••••"
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
            {{ cargando ? 'Guardando…' : 'Guardar contraseña' }}
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

    <BaseToast :mensaje="toastExito" tipo="exito" @cerrar="toastExito = ''" />
  </main>
</template>
