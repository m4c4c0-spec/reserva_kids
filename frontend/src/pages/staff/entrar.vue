<script setup>
import { ref } from 'vue'
import { useStaffStore } from '../../stores/staff'
import bgV2 from '../../assets/login-bg-v2.webp'
import charBalloon from '../../assets/char-balloon.png'
import charHat from '../../assets/char-hat.png'
import BaseButton from '../../components/BaseButton.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'

definePageMeta({ layout: 'default', soloInvitados: 'staff' })

const store = useStaffStore()
const router = useRouter()
const email = ref('')
const password = ref('')
const error = ref('')
const enviando = ref(false)
const verPass = ref(false)
const cooldown = ref(false) // anti fuerza-bruta: bloquea reintentos por 2.5s tras error

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

async function entrar() {
  if (cooldown.value) return
  error.value = ''
  enviando.value = true
  try {
    await store.login(email.value, password.value)
    router.push('/staff/calendario')
  } catch (e) {
    error.value = e.response?.data?.message || 'Credenciales inválidas'
    cooldown.value = true
    setTimeout(() => (cooldown.value = false), 2500)
  } finally {
    enviando.value = false
  }
}
</script>

<template>
  <main
    class="min-h-screen bg-surface bg-cover bg-center bg-fixed flex flex-col justify-center items-center p-5 relative overflow-hidden"
    :style="{ backgroundImage: `url(${bgV2})` }"
  >
    <div
      class="absolute -top-10 -left-10 w-48 h-48 rounded-full bg-primary-container/30 blur-3xl pointer-events-none"
    ></div>
    <div
      class="absolute bottom-10 -right-10 w-56 h-56 rounded-full bg-secondary-container/30 blur-3xl pointer-events-none"
    ></div>

    <div class="w-full max-w-md relative z-10">
      <div class="text-center mb-8">
        <div class="flex justify-center items-end mb-4 gap-3 h-28">
          <div
            class="character-wrapper animate-floating"
            :class="{ 'character-look-down': reaccion === 'mirar', 'character-cover-eyes': reaccion === 'taparse' }"
          >
            <img
              :src="charBalloon"
              alt=""
              class="w-24 h-24 object-contain drop-shadow-xl character-img"
              aria-hidden="true"
            />
          </div>
          <div
            class="character-wrapper animate-floating-delayed"
            :class="{ 'character-look-down': reaccion === 'mirar', 'character-cover-eyes': reaccion === 'taparse' }"
          >
            <img
              :src="charHat"
              alt=""
              class="w-24 h-24 object-contain drop-shadow-xl character-img"
              aria-hidden="true"
            />
          </div>
        </div>
        <p class="font-display font-bold text-sm tracking-widest uppercase text-secondary mb-2">Acceso a staff</p>
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Equipo del salón</h1>
        <p class="text-on-surface-variant font-medium mt-1 text-sm">Mira tu calendario y las reservas del día</p>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft">
        <form class="space-y-4" @submit.prevent="entrar">
          <div>
            <label for="email" class="block text-sm font-bold text-on-surface mb-1">Email</label>
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
                placeholder="tu@email.com"
                class="input-festivo input-festivo--con-icono !py-3"
                @focus="mirar"
                @blur="reposo"
              />
            </div>
          </div>
          <div>
            <label for="password" class="block text-sm font-bold text-on-surface mb-1">Contraseña</label>
            <div class="relative">
              <span
                class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none"
                >lock</span
              >
              <input
                id="password"
                v-model="password"
                :type="verPass ? 'text' : 'password'"
                required
                minlength="8"
                placeholder="••••••••"
                class="input-festivo input-festivo--con-icono !py-3 !pr-11"
                @focus="taparse"
                @blur="reposo"
              />
              <button
                type="button"
                class="absolute inset-y-0 right-3 flex items-center text-on-surface-variant hover:text-on-surface"
                :aria-label="verPass ? 'Ocultar contraseña' : 'Mostrar contraseña'"
                @click="verPass = !verPass"
              >
                <span class="material-symbols-outlined">{{ verPass ? 'visibility_off' : 'visibility' }}</span>
              </button>
            </div>
          </div>
          <ErrorBanner :mensaje="error" />
          <BaseButton
            variante="primario"
            type="submit"
            :cargando="enviando"
            :deshabilitado="cooldown"
            class="w-full py-3"
          >
            {{ enviando ? 'Entrando…' : 'Entrar' }}
            <span class="material-symbols-outlined ml-2" style="font-size: 18px">arrow_forward</span>
          </BaseButton>
        </form>
      </div>
    </div>
  </main>
</template>
