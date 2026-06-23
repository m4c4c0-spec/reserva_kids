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

async function enviar() {
  error.value = ''
  cargando.value = true
  try {
    await auth.login(email.value, password.value)
    router.push('/admin/metricas')
  } catch (e) {
    error.value = e.response?.data?.message || 'Credenciales inválidas'
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <main class="min-h-screen bg-surface flex flex-col justify-center items-center p-5">
    <div class="w-full max-w-md">
      <div class="text-center mb-8">
        <div class="flex justify-center mb-4">
          <div class="w-14 h-14 rounded-2xl bg-primary-container flex items-center justify-center shadow-soft">
            <span class="material-symbols-outlined text-on-primary-container text-3xl">admin_panel_settings</span>
          </div>
        </div>
        <h1 class="font-display font-extrabold text-3xl tracking-tight text-primary">Consola de plataforma</h1>
        <p class="text-on-surface-variant font-medium mt-1">Acceso de administración — ReservaKids</p>
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
                placeholder="ops@reservakids.cl"
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
                type="password"
                required
                autocomplete="current-password"
                placeholder="••••••••"
                class="input-festivo input-festivo--con-icono !py-3"
              />
            </div>
          </div>

          <ErrorBanner :mensaje="error" />

          <BaseButton
            variante="primario"
            type="submit"
            :cargando="cargando"
            :deshabilitado="cargando"
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

      <p class="text-center text-xs font-medium text-outline mt-6">
        Área restringida. Las acciones quedan registradas.
      </p>
    </div>
  </main>
</template>
