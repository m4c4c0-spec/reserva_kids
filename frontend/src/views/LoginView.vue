<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

const modo = ref('login') // 'login' | 'registro'
const email = ref('')
const password = ref('')
const nombreNegocio = ref('')
const slug = ref('')
const error = ref('')
const cargando = ref(false)

async function enviar() {
  error.value = ''
  cargando.value = true
  try {
    if (modo.value === 'login') {
      await auth.login(email.value, password.value)
    } else {
      await auth.registrar({
        nombreNegocio: nombreNegocio.value,
        slug: slug.value,
        email: email.value,
        password: password.value,
      })
    }
    router.push('/panel')
  } catch (e) {
    error.value = e.response?.data?.message || 'Error de conexión'
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-violet-50 flex items-center justify-center p-4">
    <div class="w-full max-w-sm bg-white rounded-2xl shadow p-6 space-y-4">
      <h1 class="text-2xl font-bold text-violet-700 text-center">🎈 ReservaKids</h1>
      <p class="text-sm text-gray-500 text-center">
        {{ modo === 'login' ? 'Entra al panel de tu negocio' : 'Crea la cuenta de tu negocio' }}
      </p>

      <form @submit.prevent="enviar" class="space-y-3">
        <template v-if="modo === 'registro'">
          <input v-model="nombreNegocio" required maxlength="120" placeholder="Nombre del negocio"
                 class="w-full border rounded-lg px-3 py-2" />
          <input v-model="slug" required pattern="[a-z0-9-]{3,60}" placeholder="slug (ej: fiestas-pepito)"
                 class="w-full border rounded-lg px-3 py-2" />
        </template>
        <input v-model="email" type="email" required placeholder="Email"
               class="w-full border rounded-lg px-3 py-2" />
        <input v-model="password" type="password" required minlength="8" placeholder="Contraseña (mín. 8)"
               class="w-full border rounded-lg px-3 py-2" />

        <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

        <button :disabled="cargando"
                class="w-full bg-violet-600 hover:bg-violet-700 text-white rounded-lg py-2 font-semibold disabled:opacity-50">
          {{ cargando ? 'Enviando…' : modo === 'login' ? 'Entrar' : 'Registrar negocio' }}
        </button>
      </form>

      <button @click="modo = modo === 'login' ? 'registro' : 'login'"
              class="w-full text-sm text-violet-600 hover:underline">
        {{ modo === 'login' ? '¿No tienes cuenta? Regístrate' : '¿Ya tienes cuenta? Entra' }}
      </button>

      <!-- Falla 1.3 (revisión 5 años): antes, olvidar la clave = perder el acceso al negocio -->
      <RouterLink v-if="modo === 'login'" to="/reset"
                  class="block w-full text-xs text-gray-400 hover:text-violet-600 hover:underline text-center">
        ¿Olvidaste tu contraseña?
      </RouterLink>
    </div>
  </main>
</template>
