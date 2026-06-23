<script setup>
import { computed, ref } from 'vue'
import api from '../api/client'
import charHat from '../assets/char-hat.png'
import bgV2 from '../assets/login-bg-v2.webp'
import BaseButton from '../components/BaseButton.vue'
import BaseToast from '../components/BaseToast.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

definePageMeta({ layout: 'default' })

const route = useRoute()
const router = useRouter()

const token = computed(() => {
  if (!process.client) return ''
  const hash = window.location.hash.substring(1)
  const params = new URLSearchParams(hash)
  return params.get('token') || ''
})
const email = ref('')
const password = ref('')
const enviado = ref(false)
const error = ref('')
const cargando = ref(false)
const toastExito = ref('')

async function solicitar() {
  error.value = ''
  cargando.value = true
  try {
    await api.post('/auth/reset/solicitar', { email: email.value })
    enviado.value = true
  } catch {
    error.value = 'Error de conexión, intenta de nuevo'
  } finally {
    cargando.value = false
  }
}

async function confirmar() {
  error.value = ''
  cargando.value = true
  try {
    await api.post('/auth/reset/confirmar', { token: token.value, nuevaPassword: password.value })
    toastExito.value = 'Contraseña actualizada. Inicia sesión con la nueva.'
    setTimeout(() => router.push('/login'), 2000)
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
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Recuperar contraseña</h1>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft space-y-4">
        <form v-if="token" class="space-y-4" @submit.prevent="confirmar">
          <p class="font-medium text-on-surface-variant text-center">Escribe tu contraseña nueva.</p>
          <div class="relative">
            <span
              class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
              >lock_reset</span
            >
            <input
              v-model="password"
              type="password"
              required
              minlength="8"
              placeholder="Contraseña nueva (mín. 8)"
              class="input-festivo input-festivo--con-icono !py-3"
            />
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

        <template v-else>
          <p
            v-if="enviado"
            class="text-sm font-semibold text-on-secondary-container bg-secondary-fixed rounded-xl px-3 py-2 text-center"
          >
            Si el correo existe, te enviamos un enlace para crear una contraseña nueva. Revisa tu bandeja (vence en 30
            minutos).
          </p>
          <form v-else class="space-y-4" @submit.prevent="solicitar">
            <p class="font-medium text-on-surface-variant text-center">
              Te enviaremos un enlace al correo de tu cuenta.
            </p>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >mail</span
              >
              <input
                v-model="email"
                type="email"
                required
                placeholder="Email de tu cuenta"
                class="input-festivo input-festivo--con-icono !py-3"
              />
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
        </template>

        <NuxtLink
          to="/login"
          class="block w-full font-medium text-primary hover:text-primary-container text-center transition-colors"
        >
          Volver al inicio de sesión
        </NuxtLink>
      </div>
    </div>

    <BaseToast :mensaje="toastExito" tipo="exito" @cerrar="toastExito = ''" />
  </main>
</template>
