<script setup>
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useConfetti } from '../composables/useConfetti'
import axios from 'axios'
import charBalloon from '../assets/char-balloon.png'
import charCake from '../assets/char-cake.png'
import bgV2 from '../assets/login-bg-v2.webp'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const router = useRouter()
const auth = useAuthStore()
const { canvasRef: confettiCanvas, lanzar: lanzarConfetti } = useConfetti()

const modo = ref('login')
const email = ref('')
const password = ref('')
const nombreNegocio = ref('')
const slug = ref('')
const error = ref('')
const cargando = ref(false)

// V27: magic link — login sin contraseña (para adultos mayores que olvidan/resetean claves)
const magicEnviado = ref(false)

const withCreds = { withCredentials: true, headers: { 'X-Requested-With': 'XMLHttpRequest' } }
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'

const emailYaRegistrado = computed(() =>
  modo.value === 'registro' && error.value === 'El email ya está registrado',
)

async function pedirMagicLink() {
  error.value = ''
  if (!email.value) {
    error.value = 'Escribe tu correo para enviarte el enlace.'
    return
  }
  cargando.value = true
  try {
    await auth.pedirMagicLink(email.value)
    magicEnviado.value = true
  } catch (e) {
    error.value = e.response?.data?.message || 'Error de conexión, intenta de nuevo'
  } finally {
    cargando.value = false
  }
}

async function recuperarAcceso() {
  cargando.value = true
  error.value = ''
  try {
    await axios.post(`${baseURL}/auth/reset/solicitar`, { email: email.value }, withCreds)
  } catch {
    // 204 always, ignore
  } finally {
    cargando.value = false
  }
  modo.value = 'login'
  error.value = ''
  magicEnviado.value = true
}

const reaccion = ref('')
const mirar = () => {
  reaccion.value = 'mirar'
}
const taparse = () => {
  reaccion.value = 'taparse'
}
const reposo = () => {
  reaccion.value = ''
}

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
    lanzarConfetti()
    setTimeout(() => router.push('/panel'), 900)
  } catch (e) {
    error.value = e.response?.data?.message || 'Error de conexión'
    cargando.value = false
  }
}
</script>

<template>
  <main
    class="min-h-screen bg-surface bg-cover bg-center flex flex-col justify-center items-center p-5 relative overflow-hidden"
    :style="{ backgroundImage: `url(${bgV2})` }"
  >
    <canvas ref="confettiCanvas" class="fixed inset-0 pointer-events-none z-50"></canvas>

    <div class="w-full max-w-md relative z-10">
      <div class="text-center mb-8 relative">
        <div class="flex justify-center items-end mb-4 gap-4 h-32">
          <div
            class="character-wrapper animate-floating"
            :class="{ 'character-look-down': reaccion === 'mirar', 'character-cover-eyes': reaccion === 'taparse' }"
          >
            <img
              :src="charBalloon"
              alt="Personaje globo"
              class="w-32 h-32 object-contain drop-shadow-xl character-img"
            />
          </div>
          <div
            class="character-wrapper animate-floating-delayed"
            :class="{ 'character-look-down': reaccion === 'mirar', 'character-cover-eyes': reaccion === 'taparse' }"
          >
            <img :src="charCake" alt="Personaje torta" class="w-32 h-32 object-contain drop-shadow-xl character-img" />
          </div>
        </div>
        <h1 class="font-display font-extrabold text-4xl leading-tight tracking-tight text-primary">
          ¡Bienvenido a ReservaKids!
        </h1>
        <p class="text-on-surface-variant font-medium mt-1">
          {{ modo === 'login' ? '¡Que empiece la fiesta!' : 'Crea la cuenta de tu negocio' }}
        </p>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft relative">
        <form class="space-y-4" @submit.prevent="enviar">
          <template v-if="modo === 'registro'">
            <div>
              <label class="block text-sm font-bold text-on-surface mb-1" for="negocio">Nombre del negocio</label>
              <div class="relative">
                <span
                  class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                  >storefront</span
                >
                <input
                  id="negocio"
                  v-model="nombreNegocio"
                  required
                  maxlength="120"
                  placeholder="Fiestas Pepito"
                  class="input-festivo input-festivo--con-icono !py-3"
                  @focus="mirar"
                  @blur="reposo"
                />
              </div>
            </div>
            <div>
              <label class="block text-sm font-bold text-on-surface mb-1" for="slug">Identificador (slug)</label>
              <div class="relative">
                <span
                  class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                  >link</span
                >
                <input
                  id="slug"
                  v-model="slug"
                  required
                  pattern="[a-z0-9-]{3,60}"
                  placeholder="fiestas-pepito"
                  class="input-festivo input-festivo--con-icono !py-3"
                  @focus="mirar"
                  @blur="reposo"
                />
              </div>
            </div>
          </template>

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
                @focus="mirar"
                @blur="reposo"
              />
            </div>
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
                @focus="taparse"
                @blur="reposo"
              />
            </div>
          </div>

          <ErrorBanner :mensaje="error" />

          <template v-if="emailYaRegistrado">
            <div class="rounded-xl bg-secondary-container/20 border border-secondary-container p-4 text-center">
              <p class="text-sm font-medium text-on-secondary-container mb-3">
                ¿El negocio ya está creado y olvidaste la contraseña?
              </p>
              <button
                type="button"
                class="inline-flex items-center gap-2 px-4 py-2 rounded-full bg-secondary-container text-on-secondary-container font-bold text-sm hover:bg-secondary-container/70 transition-colors"
                :disabled="cargando"
                @click="recuperarAcceso()"
              >
                <span class="material-symbols-outlined text-[18px]">mail</span>
                Recuperar acceso
              </button>
            </div>
          </template>

          <div class="pt-2">
            <BaseButton
              variante="primario"
              type="submit"
              :cargando="cargando"
              :deshabilitado="cargando"
              class="w-full py-3"
            >
              {{ cargando ? 'Enviando…' : modo === 'login' ? 'Iniciar Sesión' : 'Registrar negocio' }}
              <span class="material-symbols-outlined ml-2" style="font-size: 18px">arrow_forward</span>
            </BaseButton>
          </div>
        </form>

        <div class="mt-6 flex flex-col items-center gap-3">
          <!-- V27: magic link — sin contraseña, ideal para adultos mayores -->
          <div v-if="modo === 'login' && !magicEnviado" class="w-full space-y-2">
            <BaseButton variante="secundario" type="button" class="w-full py-3" @click="pedirMagicLink">
              <span class="material-symbols-outlined" style="font-size: 20px">mail</span>
              Enviarme un enlace al correo
            </BaseButton>
            <p class="text-xs font-medium text-on-surface-variant text-center px-2">
              ¿No recuerdas tu contraseña? Te mandamos un enlace a tu correo; lo tocas y entras directo al panel.
            </p>
          </div>
          <div v-if="modo === 'login' && magicEnviado" class="w-full space-y-2">
            <p
              class="text-sm font-semibold text-on-secondary-container bg-secondary-fixed rounded-xl px-3 py-2 text-center"
            >
              Si el correo está registrado, te enviamos un enlace. Revisa tu bandeja (vence en 10 minutos).
            </p>
            <button
              class="text-sm font-bold text-secondary hover:underline"
              type="button"
              @click="magicEnviado = false"
            >
              Volver al inicio de sesión
            </button>
          </div>

          <RouterLink
            v-if="modo === 'login'"
            to="/reset"
            class="font-medium text-primary hover:text-primary-container transition-colors"
          >
            ¿Olvidaste tu contraseña?
          </RouterLink>
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
            to="/clientes/entrar"
            class="text-sm font-medium text-on-surface-variant hover:text-primary transition-colors border-t border-outline-variant/30 pt-3 w-full text-center"
          >
            ¿Buscas reservar una fiesta? Entra como cliente
          </RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>
