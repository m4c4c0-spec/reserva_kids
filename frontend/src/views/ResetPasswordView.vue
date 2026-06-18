<script setup>
// Falla 1.3 (revisión 5 años): recuperación de contraseña.
// Sin token en la URL: pide el email (la respuesta es 204 SIEMPRE — anti-enumeración).
// Con ?token=...: formulario de contraseña nueva (enlace de un solo uso, vence en 30 min).
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import api from '../api/client'
import charHat from '../assets/char-hat.png'
// Diseño V2: fondo pastel con globos y cabritas (DISENO_LOGIN_V2.png)
import bgV2 from '../assets/login-bg-v2.webp'

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
  <main class="min-h-screen bg-surface bg-cover bg-center flex flex-col justify-center items-center p-5 relative overflow-hidden"
        :style="{ backgroundImage: `url(${bgV2})` }">

    <div class="w-full max-w-md relative z-10">
      <div class="text-center mb-8">
        <div class="flex justify-center mb-4 h-28">
          <img :src="charHat" alt="Personaje gorro de fiesta"
               class="w-28 h-28 object-contain drop-shadow-xl character-img animate-floating" />
        </div>
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Recuperar contraseña</h1>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft space-y-4">
        <!-- Paso 2: con token en la URL, definir la contraseña nueva -->
        <form v-if="token" @submit.prevent="confirmar" class="space-y-4">
          <p class="font-medium text-on-surface-variant text-center">Escribe tu contraseña nueva.</p>
          <div class="relative">
            <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">lock_reset</span>
            <input v-model="password" type="password" required minlength="8" placeholder="Contraseña nueva (mín. 8)"
                   class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
          </div>
          <p v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2">{{ error }}</p>
          <button :disabled="cargando"
                  class="w-full flex justify-center items-center py-3 px-4 rounded-full shadow-md font-bold text-on-primary-container bg-primary-container hover:bg-primary-fixed hover:shadow-lifted transition-all duration-200 active:scale-95 disabled:opacity-50">
            {{ cargando ? 'Guardando…' : 'Guardar contraseña' }}
          </button>
        </form>

        <!-- Paso 1: pedir el enlace por email -->
        <template v-else>
          <p v-if="enviado" class="text-sm font-semibold text-on-secondary-container bg-secondary-fixed rounded-xl px-3 py-2 text-center">
            Si el correo existe, te enviamos un enlace para crear una contraseña nueva.
            Revisa tu bandeja (vence en 30 minutos).
          </p>
          <form v-else @submit.prevent="solicitar" class="space-y-4">
            <p class="font-medium text-on-surface-variant text-center">
              Te enviaremos un enlace al correo de tu cuenta.
            </p>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">mail</span>
              <input v-model="email" type="email" required placeholder="Email de tu cuenta"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
            <p v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2">{{ error }}</p>
            <button :disabled="cargando"
                    class="w-full flex justify-center items-center py-3 px-4 rounded-full shadow-md font-bold text-on-primary-container bg-primary-container hover:bg-primary-fixed hover:shadow-lifted transition-all duration-200 active:scale-95 disabled:opacity-50">
              {{ cargando ? 'Enviando…' : 'Enviar enlace' }}
            </button>
          </form>
        </template>

        <RouterLink to="/login" class="block w-full font-medium text-primary hover:text-primary-container text-center transition-colors">
          Volver al inicio de sesión
        </RouterLink>
      </div>
    </div>
  </main>
</template>
