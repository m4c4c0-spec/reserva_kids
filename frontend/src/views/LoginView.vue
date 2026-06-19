<script setup>
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import charBalloon from '../assets/char-balloon.png'
import charCake from '../assets/char-cake.png'
import bgV2 from '../assets/login-bg-v2.webp'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

const router = useRouter()
const auth = useAuthStore()

const modo = ref('login')
const email = ref('')
const password = ref('')
const nombreNegocio = ref('')
const slug = ref('')
const error = ref('')
const cargando = ref(false)

const reaccion = ref('')
const mirar = () => { reaccion.value = 'mirar' }
const taparse = () => { reaccion.value = 'taparse' }
const reposo = () => { reaccion.value = '' }

const confettiCanvas = ref(null)
let animationId = null

function lanzarConfetti() {
  const canvas = confettiCanvas.value
  if (!canvas) return
  canvas.width = window.innerWidth
  canvas.height = window.innerHeight
  const ctx = canvas.getContext('2d')
  const colores = ['#ff4db8', '#ff9cdb', '#fff8f8', '#ffe170', '#4b92fd']
  const particulas = Array.from({ length: 150 }, () => ({
    x: canvas.width / 2,
    y: canvas.height / 2,
    size: Math.random() * 10 + 6,
    color: colores[Math.floor(Math.random() * colores.length)],
    vx: Math.random() * 20 - 10,
    vy: Math.random() * -20 - 5,
    rot: Math.random() * 360,
    vrot: Math.random() * 10 - 5,
  }))
  function animar() {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    for (let i = particulas.length - 1; i >= 0; i--) {
      const p = particulas[i]
      p.x += p.vx; p.y += p.vy; p.vy += 0.5; p.rot += p.vrot
      ctx.save()
      ctx.translate(p.x, p.y)
      ctx.rotate((p.rot * Math.PI) / 180)
      ctx.fillStyle = p.color
      ctx.fillRect(-p.size / 2, -p.size / 2, p.size, p.size)
      ctx.restore()
      if (p.y > canvas.height + 50) particulas.splice(i, 1)
    }
    if (particulas.length > 0) animationId = requestAnimationFrame(animar)
  }
  animar()
}

onBeforeUnmount(() => { if (animationId) cancelAnimationFrame(animationId) })

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
  <main class="min-h-screen bg-surface bg-cover bg-center flex flex-col justify-center items-center p-5 relative overflow-hidden"
        :style="{ backgroundImage: `url(${bgV2})` }">
    <canvas ref="confettiCanvas" class="fixed inset-0 pointer-events-none z-50"></canvas>

    <div class="w-full max-w-md relative z-10">
      <div class="text-center mb-8 relative">
        <div class="flex justify-center items-end mb-4 gap-4 h-32">
          <div class="character-wrapper animate-floating"
               :class="{ 'character-look-down': reaccion === 'mirar', 'character-cover-eyes': reaccion === 'taparse' }">
            <img :src="charBalloon" alt="Personaje globo" class="w-32 h-32 object-contain drop-shadow-xl character-img" />
          </div>
          <div class="character-wrapper animate-floating-delayed"
               :class="{ 'character-look-down': reaccion === 'mirar', 'character-cover-eyes': reaccion === 'taparse' }">
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
        <form @submit.prevent="enviar" class="space-y-4">
          <template v-if="modo === 'registro'">
            <div>
              <label class="block text-sm font-bold text-on-surface mb-1" for="negocio">Nombre del negocio</label>
              <div class="relative">
                <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">storefront</span>
                <input id="negocio" v-model="nombreNegocio" required maxlength="120" placeholder="Fiestas Pepito"
                       @focus="mirar" @blur="reposo"
                       class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
              </div>
            </div>
            <div>
              <label class="block text-sm font-bold text-on-surface mb-1" for="slug">Identificador (slug)</label>
              <div class="relative">
                <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">link</span>
                <input id="slug" v-model="slug" required pattern="[a-z0-9-]{3,60}" placeholder="fiestas-pepito"
                       @focus="mirar" @blur="reposo"
                       class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
              </div>
            </div>
          </template>

          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="email">Email</label>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">mail</span>
              <input id="email" v-model="email" type="email" required placeholder="tu@correo.com"
                     @focus="mirar" @blur="reposo"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
          </div>

          <div>
            <label class="block text-sm font-bold text-on-surface mb-1" for="password">Contraseña</label>
            <div class="relative">
              <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">lock</span>
              <input id="password" v-model="password" type="password" required minlength="8" placeholder="••••••••"
                     @focus="taparse" @blur="reposo"
                     class="block w-full pl-11 pr-3 py-3 border-2 border-surface-highest rounded-xl bg-surface placeholder-outline font-medium focus:outline-none focus:border-secondary transition-colors duration-200" />
            </div>
          </div>

          <ErrorBanner :mensaje="error" />

          <div class="pt-2">
            <BaseButton variante="primario" type="submit" :cargando="cargando" :deshabilitado="cargando" class="w-full py-3">
              {{ cargando ? 'Enviando…' : modo === 'login' ? 'Iniciar Sesión' : 'Registrar negocio' }}
              <span class="material-symbols-outlined ml-2" style="font-size: 18px">arrow_forward</span>
            </BaseButton>
          </div>
        </form>

        <div class="mt-6 flex flex-col items-center gap-3">
          <RouterLink v-if="modo === 'login'" to="/reset"
                      class="font-medium text-primary hover:text-primary-container transition-colors">
            ¿Olvidaste tu contraseña?
          </RouterLink>
          <p class="font-medium text-on-surface-variant">
            {{ modo === 'login' ? '¿No tienes cuenta?' : '¿Ya tienes cuenta?' }}
            <button @click="modo = modo === 'login' ? 'registro' : 'login'"
                    class="font-bold text-secondary hover:text-secondary-container transition-colors">
              {{ modo === 'login' ? 'Regístrate' : 'Entra' }}
            </button>
          </p>
          <RouterLink to="/clientes/entrar"
                      class="text-sm font-medium text-on-surface-variant hover:text-primary transition-colors border-t border-outline-variant/30 pt-3 w-full text-center">
            ¿Buscas reservar una fiesta? Entra como cliente
          </RouterLink>
        </div>
      </div>
    </div>
  </main>
</template>
