<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useClienteAuthStore } from '../stores/clienteAuth'
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import charHat from '../assets/char-hat.png'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const router = useRouter()
const auth = useClienteAuthStore()

const modo = ref('login')
const nombre = ref('')
const email = ref('')
const telefono = ref('')
const password = ref('')
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
        nombre: nombre.value,
        email: email.value,
        telefono: telefono.value,
        password: password.value,
      })
    }
    router.push('/clientes')
  } catch (e) {
    error.value = e.response?.data?.message || 'Error de conexión'
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
        <div class="flex justify-center items-end mb-4 gap-3 h-28">
          <img :src="charBalloon" alt="" class="w-24 h-24 object-contain drop-shadow-xl character-img animate-floating" aria-hidden="true" />
          <img :src="charHat" alt="" class="w-24 h-24 object-contain drop-shadow-xl character-img animate-floating-delayed" aria-hidden="true" />
        </div>
        <h1 class="font-display font-extrabold text-3xl md:text-4xl tracking-tight text-primary">
          Reserva tu hora
        </h1>
        <p class="text-on-surface-variant font-medium mt-1">
          {{ modo === 'login' ? 'Entra para agendar tus servicios' : 'Crea tu cuenta para agendar' }}
        </p>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft">
        <form @submit.prevent="enviar" class="space-y-4">
          <div v-if="modo === 'registro'">
            <label class="block text-sm font-bold text-on-surface mb-1" for="nombre">Tu nombre</label>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">person</span>
              <input id="nombre" v-model="nombre" required maxlength="120" placeholder="Cómo te llamas"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="email">Email</label>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">mail</span>
              <input id="email" v-model="email" type="email" required placeholder="tu@correo.com"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
          </div>

          <div v-if="modo === 'registro'">
            <label class="block text-sm font-bold text-on-surface mb-1" for="telefono">Celular</label>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">smartphone</span>
              <input id="telefono" v-model="telefono" type="tel" required maxlength="20" placeholder="+56 9 1234 5678"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
            <p class="text-xs text-on-surface-variant mt-1">Te confirmamos y recordamos tu hora por WhatsApp.</p>
          </div>

          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="password">Contraseña</label>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">lock</span>
              <input id="password" v-model="password" type="password" required minlength="8" placeholder="••••••••"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
          </div>

          <ErrorBanner :mensaje="error" />

          <BaseButton variante="primario" type="submit" :cargando="cargando" :deshabilitado="cargando" class="w-full py-3">
            {{ cargando ? 'Enviando…' : modo === 'login' ? 'Iniciar Sesión' : 'Crear cuenta' }}
            <span class="material-symbols-outlined ml-2" style="font-size: 18px">arrow_forward</span>
          </BaseButton>
        </form>

        <div class="mt-6 flex flex-col items-center gap-3">
          <p class="font-medium text-on-surface-variant">
            {{ modo === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?' }}
            <button @click="modo = modo === 'login' ? 'registro' : 'login'"
                    class="font-bold text-secondary hover:text-secondary-container transition-colors">
              {{ modo === 'login' ? 'Regístrate' : 'Entra' }}
            </button>
          </p>
          <RouterLink to="/login" class="text-sm font-medium text-on-surface-variant hover:text-primary transition-colors">
            ¿Tienes un negocio? Entra como dueño
          </RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>
