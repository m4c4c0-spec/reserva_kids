<script setup>
// Falla 1.3 (revisión 5 años): recuperación de contraseña.
// Sin token en la URL: pide el email (la respuesta es 204 SIEMPRE — anti-enumeración).
// Con ?token=...: formulario de contraseña nueva (enlace de un solo uso, vence en 30 min).
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api/client'

const route = useRoute()
const router = useRouter()

const token = computed(() => route.query.token || '')
const email = ref('')
const password = ref('')
const enviado = ref(false)
const error = ref('')
const cargando = ref(false)

async function solicitar() {
  error.value = ''
  cargando.value = true
  try {
    await api.post('/auth/reset/solicitar', { email: email.value })
    enviado.value = true // mismo mensaje exista o no el email
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
    alert('Contraseña actualizada. Inicia sesión con la nueva.')
    router.push('/login')
  } catch (e) {
    error.value = e.response?.data?.message || 'El enlace es inválido o ya venció'
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-violet-50 flex items-center justify-center p-4">
    <div class="w-full max-w-sm bg-white rounded-2xl shadow p-6 space-y-4">
      <h1 class="text-2xl font-bold text-violet-700 text-center">🔑 Recuperar contraseña</h1>

      <!-- Paso 2: con token en la URL, definir la contraseña nueva -->
      <form v-if="token" @submit.prevent="confirmar" class="space-y-3">
        <p class="text-sm text-gray-500 text-center">Escribe tu contraseña nueva.</p>
        <input v-model="password" type="password" required minlength="8"
               placeholder="Contraseña nueva (mín. 8)" class="w-full border rounded-lg px-3 py-2" />
        <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
        <button :disabled="cargando"
                class="w-full bg-violet-600 hover:bg-violet-700 text-white rounded-lg py-2 font-semibold disabled:opacity-50">
          {{ cargando ? 'Guardando…' : 'Guardar contraseña' }}
        </button>
      </form>

      <!-- Paso 1: pedir el enlace por email -->
      <template v-else>
        <p v-if="enviado" class="text-sm text-green-700 text-center">
          Si el correo existe, te enviamos un enlace para crear una contraseña nueva.
          Revisa tu bandeja (vence en 30 minutos).
        </p>
        <form v-else @submit.prevent="solicitar" class="space-y-3">
          <p class="text-sm text-gray-500 text-center">
            Te enviaremos un enlace al correo de tu cuenta.
          </p>
          <input v-model="email" type="email" required placeholder="Email de tu cuenta"
                 class="w-full border rounded-lg px-3 py-2" />
          <p v-if="error" class="text-sm text-red-600">{{ error }}</p>
          <button :disabled="cargando"
                  class="w-full bg-violet-600 hover:bg-violet-700 text-white rounded-lg py-2 font-semibold disabled:opacity-50">
            {{ cargando ? 'Enviando…' : 'Enviar enlace' }}
          </button>
        </form>
      </template>

      <RouterLink to="/login" class="block w-full text-sm text-violet-600 hover:underline text-center">
        Volver al inicio de sesión
      </RouterLink>
    </div>
  </main>
</template>
