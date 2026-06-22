<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import charHat from '../assets/char-hat.png'
import bgV2 from '../assets/login-bg-v2.webp'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const router = useRouter()
const auth = useAuthStore()

// RNF-07: el token viaja en fragment (#token=...) para que no quede en logs del proxy,
// historial del navegador ni cabeceras Referer.
const token = computed(() => {
  const hash = window.location.hash.substring(1)
  const params = new URLSearchParams(hash)
  return params.get('token') || ''
})

const estado = ref('entrando') // 'entrando' | 'error'
const error = ref('')
const cargando = ref(false)

async function entrar() {
  cargando.value = true
  error.value = ''
  estado.value = 'entrando'
  try {
    await auth.entrarConMagicLink(token.value)
    router.replace('/panel')
  } catch (e) {
    estado.value = 'error'
    error.value = e.response?.data?.message || 'El enlace es inválido o ya venció. Pídelo de nuevo.'
  } finally {
    cargando.value = false
  }
}

onMounted(() => {
  if (token.value) entrar()
  else estado.value = 'error'
})

function pedirOtro() {
  router.push('/login')
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
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Entrando a tu panel…</h1>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft space-y-4">
        <template v-if="estado === 'entrando'">
          <p class="text-center font-medium text-on-surface-variant">
            Estamos validando tu enlace. Te dejamos adentro en un segundo.
          </p>
          <div class="flex justify-center">
            <BaseButton variante="primario" :cargando="true" :deshabilitado="true" class="!bg-transparent !shadow-none">
              Entrando…
            </BaseButton>
          </div>
        </template>

        <template v-else>
          <p class="text-center font-bold text-on-error-container">
            Este enlace ya no sirve. Los enlaces duran 10 minutos y se usan una sola vez.
          </p>
          <ErrorBanner :mensaje="error" />
          <BaseButton variante="primario" type="button" class="w-full py-3" @click="pedirOtro">
            Pedir un enlace nuevo
          </BaseButton>
        </template>
      </div>
    </div>
  </main>
</template>
