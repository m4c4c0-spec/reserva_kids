<script setup>
definePageMeta({ layout: 'default', soloInvitados: 'admin' })

import { ref } from 'vue'
import { useAdminAuthStore } from '../../stores/adminAuth'
import BaseButton from '../../components/BaseButton.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import OAuth2Buttons from '../../components/OAuth2Buttons.vue'

const router = useRouter()
const auth = useAdminAuthStore()

const email = ref('')
const password = ref('')
const error = ref('')
const cargando = ref(false)
const verPass = ref(false)
const cooldown = ref(false) // anti fuerza-bruta: bloquea reintentos por 2.5s tras error

async function enviar() {
  if (cooldown.value) return
  error.value = ''
  cargando.value = true
  try {
    await auth.login(email.value, password.value)
    router.push('/admin/metricas')
  } catch (e) {
    error.value = e.response?.data?.message || 'Credenciales inválidas'
    cooldown.value = true
    setTimeout(() => (cooldown.value = false), 2500)
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-surface flex flex-col justify-center items-center p-5 relative overflow-hidden">
    <div
      class="absolute -top-10 -left-10 w-48 h-48 rounded-full bg-primary-container/20 blur-3xl pointer-events-none"
    ></div>
    <div
      class="absolute bottom-10 -right-10 w-56 h-56 rounded-full bg-secondary-container/20 blur-3xl pointer-events-none"
    ></div>

    <div class="w-full max-w-md relative z-10">
      <div class="text-center mb-8">
        <div class="flex justify-center mb-4 relative">
          <div class="absolute inset-0 flex items-center justify-center">
            <div class="w-20 h-20 rounded-full bg-primary/10 animate-ping"></div>
          </div>
          <div class="relative w-16 h-16 rounded-2xl bg-primary flex items-center justify-center shadow-lifted">
            <span class="material-symbols-outlined text-on-primary text-4xl">admin_panel_settings</span>
          </div>
        </div>
        <p class="font-display font-bold text-sm tracking-widest uppercase text-secondary mb-2">
          Consola de plataforma
        </p>
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Acceso restringido</h1>
        <p class="text-on-surface-variant font-medium mt-1">Administración — DulceVida</p>
      </div>

      <div class="glass-card rounded-3xl p-6 shadow-soft">
        <form class="space-y-4" @submit.prevent="enviar">
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
                autocomplete="username"
                placeholder="ops@dulcevida.cl"
                class="input-festivo input-festivo--con-icono !py-3"
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
                :type="verPass ? 'text' : 'password'"
                required
                autocomplete="current-password"
                placeholder="••••••••"
                class="input-festivo input-festivo--con-icono !py-3 !pr-11"
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
            :cargando="cargando"
            :deshabilitado="cargando || cooldown"
            class="w-full py-3"
          >
            {{ cargando ? 'Entrando…' : 'Entrar' }}
            <span class="material-symbols-outlined ml-2" style="font-size: 18px">arrow_forward</span>
          </BaseButton>
        </form>

        <div class="mt-6">
          <OAuth2Buttons type="admin" />
        </div>
      </div>

      <p class="text-center text-xs font-medium text-outline mt-6 flex items-center justify-center gap-1.5">
        <span class="material-symbols-outlined text-[14px]">shield_lock</span>
        Área restringida. Las acciones quedan registradas.
      </p>
    </div>
  </main>
</template>
