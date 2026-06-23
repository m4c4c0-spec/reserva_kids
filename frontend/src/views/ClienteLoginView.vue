<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useClienteAuthStore } from '../stores/clienteAuth'
import { esRutValido, esTelefonoChilenoValido, formatearRut } from '../composables/validators'
import axios from 'axios'
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import charHat from '../assets/char-hat.png'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const router = useRouter()
const auth = useClienteAuthStore()

const modo = ref('login')
const nombre = ref('')
const rut = ref('')
const email = ref('')
const telefono = ref('')
const password = ref('')
const error = ref('')
const cargando = ref(false)
const resetEnviado = ref(false)
const enviandoReset = ref(false)

const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'

// Validación inline: solo se muestra una vez que el usuario escribió algo (no en vacío).
const rutInvalido = computed(() => modo.value === 'registro' && !!rut.value && !esRutValido(rut.value))
const telInvalido = computed(
  () => modo.value === 'registro' && !!telefono.value && !esTelefonoChilenoValido(telefono.value),
)
const registroInvalido = computed(
  () => modo.value === 'registro' && (!esRutValido(rut.value) || !esTelefonoChilenoValido(telefono.value)),
)

function formatearRutEnBlur() {
  if (rut.value && esRutValido(rut.value)) rut.value = formatearRut(rut.value)
}

async function enviar() {
  error.value = ''
  resetEnviado.value = false
  if (registroInvalido.value) {
    error.value = !esRutValido(rut.value)
      ? 'Revisa tu RUT: parece incompleto o el dígito verificador no calza.'
      : 'Revisa tu celular: debe ser un número chileno válido (9 dígitos).'
    return
  }
  cargando.value = true
  try {
    if (modo.value === 'login') {
      await auth.login(email.value, password.value)
    } else {
      await auth.registrar({
        nombre: nombre.value,
        rut: rut.value,
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

async function recuperarContrasena() {
  enviandoReset.value = true
  try {
    await axios.post(`${baseURL}/cliente-auth/reset/solicitar`, { email: email.value }, withCreds)
    resetEnviado.value = true
    error.value = ''
  } catch {
    resetEnviado.value = false
  } finally {
    enviandoReset.value = false
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
        <div class="flex justify-center items-end mb-4 gap-3 h-28">
          <img
            :src="charBalloon"
            alt=""
            class="w-24 h-24 object-contain drop-shadow-xl character-img animate-floating"
            aria-hidden="true"
          />
          <img
            :src="charHat"
            alt=""
            class="w-24 h-24 object-contain drop-shadow-xl character-img animate-floating-delayed"
            aria-hidden="true"
          />
        </div>
        <h1 class="font-display font-extrabold text-3xl md:text-4xl tracking-tight text-primary">Reserva tu hora</h1>
        <p class="text-on-surface-variant font-medium mt-1">
          {{ modo === 'login' ? 'Entra para agendar tus servicios' : 'Crea tu cuenta para agendar' }}
        </p>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft">
        <form class="space-y-4" @submit.prevent="enviar">
          <div v-if="modo === 'registro'">
            <label class="block text-sm font-bold text-on-surface mb-1" for="nombre">Tu nombre</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >person</span
              >
              <input
                id="nombre"
                v-model="nombre"
                required
                maxlength="120"
                placeholder="Cómo te llamas"
                class="input-festivo input-festivo--con-icono !py-3"
              />
            </div>
          </div>

          <div v-if="modo === 'registro'">
            <label class="block text-sm font-bold text-on-surface mb-1" for="rut">RUT</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >badge</span
              >
              <input
                id="rut"
                v-model="rut"
                required
                maxlength="15"
                placeholder="12.345.678-5"
                :aria-invalid="rutInvalido"
                class="input-festivo input-festivo--con-icono !py-3"
                @blur="formatearRutEnBlur"
              />
            </div>
            <p v-if="rutInvalido" class="text-xs font-semibold text-error mt-1">
              RUT inválido: revisa el dígito verificador.
            </p>
            <p v-else class="text-xs text-on-surface-variant mt-1">
              Verificamos tu identidad. No compartimos tus datos.
            </p>
          </div>

          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="email">Email</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >mail</span
              >
              <input
                id="email"
                v-model="email"
                type="email"
                required
                placeholder="tu@correo.com"
                class="input-festivo input-festivo--con-icono !py-3"
              />
            </div>
          </div>

          <div v-if="modo === 'registro'">
            <label class="block text-sm font-bold text-on-surface mb-1" for="telefono">Celular</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >smartphone</span
              >
              <input
                id="telefono"
                v-model="telefono"
                type="tel"
                required
                maxlength="20"
                placeholder="+56 9 1234 5678"
                :aria-invalid="telInvalido"
                class="input-festivo input-festivo--con-icono !py-3"
              />
            </div>
            <p v-if="telInvalido" class="text-xs font-semibold text-error mt-1">
              Celular inválido: debe tener 9 dígitos y partir en 9.
            </p>
            <p v-else class="text-xs text-on-surface-variant mt-1">Te confirmamos y recordamos tu hora por WhatsApp.</p>
          </div>

          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="password">Contraseña</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >lock</span
              >
              <input
                id="password"
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

          <div v-if="resetEnviado" class="rounded-xl bg-tertiary-container/30 border border-tertiary-container p-4">
            <p class="text-sm font-bold text-on-tertiary-container mb-1">Link enviado</p>
            <p class="text-xs text-on-tertiary-container">
              Revisa tu correo <strong>{{ email }}</strong
              >. Entra con el enlace y cambia tu contraseña.
            </p>
          </div>

          <div v-if="!resetEnviado" class="text-center">
            <button
              type="button"
              class="text-sm font-medium text-on-surface-variant hover:text-secondary transition-colors disabled:opacity-30"
              :disabled="!email || enviandoReset"
              @click="recuperarContrasena()"
            >
              <span class="material-symbols-outlined text-[16px] align-middle mr-1">mail</span>
              {{ enviandoReset ? 'Enviando…' : '¿Olvidaste tu contraseña?' }}
            </button>
          </div>

          <BaseButton
            variante="primario"
            type="submit"
            :cargando="cargando"
            :deshabilitado="cargando || registroInvalido"
            class="w-full py-3"
          >
            {{ cargando ? 'Enviando…' : modo === 'login' ? 'Iniciar Sesión' : 'Crear cuenta' }}
            <span class="material-symbols-outlined ml-2" style="font-size: 18px">arrow_forward</span>
          </BaseButton>
        </form>

        <div class="mt-6 flex flex-col items-center gap-3">
          <p class="font-medium text-on-surface-variant">
            {{ modo === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?' }}
            <button
              class="font-bold text-secondary hover:text-secondary-container transition-colors"
              @click="modo = modo === 'login' ? 'registro' : 'login'"
            >
              {{ modo === 'login' ? 'Regístrate' : 'Entra' }}
            </button>
          </p>
          <RouterLink
            to="/login"
            class="text-sm font-medium text-on-surface-variant hover:text-primary transition-colors"
          >
            ¿Tienes un negocio? Entra como dueño
          </RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>
