<script setup>
import { ref } from 'vue'
import { useStaffStore } from '../../stores/staff'
import bgV2 from '../../assets/login-bg-v2.webp'
import charBalloon from '../../assets/char-balloon.png'

definePageMeta({ layout: 'default', soloInvitados: 'staff' })

const store = useStaffStore()
const router = useRouter()
const email = ref('')
const password = ref('')
const error = ref('')
const enviando = ref(false)

async function entrar() {
  error.value = ''
  enviando.value = true
  try {
    await store.login(email.value, password.value)
    router.push('/staff/calendario')
  } catch (e) {
    error.value = e.response?.data?.message || 'Credenciales inválidas'
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed flex items-center justify-center p-6" :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="w-full max-w-md bg-surface-lowest/90 backdrop-blur-xl rounded-3xl shadow-lifted p-8 border border-white/20 space-y-6">
      <div class="text-center">
        <img :src="charBalloon" alt="" class="w-20 h-20 object-contain mx-auto drop-shadow-xl animate-floating" />
        <h1 class="font-display font-extrabold text-2xl text-on-surface mt-3">Staff</h1>
        <p class="font-medium text-on-surface-variant text-sm mt-1">Calendario del equipo</p>
      </div>

      <form class="space-y-4" @submit.prevent="entrar">
        <div>
          <label for="email" class="block text-sm font-bold text-on-surface-variant mb-1">Email</label>
          <input id="email" v-model="email" type="email" required placeholder="tu@email.com" class="input-festivo" />
        </div>
        <div>
          <label for="password" class="block text-sm font-bold text-on-surface-variant mb-1">Contraseña</label>
          <input id="password" v-model="password" type="password" required minlength="8" placeholder="••••••••" class="input-festivo" />
        </div>
        <ErrorBanner :mensaje="error" />
        <BaseButton variante="primario" type="submit" :cargando="enviando" class="w-full">Entrar</BaseButton>
      </form>
    </div>
  </main>
</template>
