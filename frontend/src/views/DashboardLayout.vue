<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api/client'
import BaseModal from '../components/BaseModal.vue'
import BaseToast from '../components/BaseToast.vue'

const router = useRouter()
const auth = useAuthStore()

const tabs = [
  { to: '/panel/solicitudes', label: 'Solicitudes', icon: 'forum' },
  { to: '/panel/servicios', label: 'Servicios', icon: 'celebration' },
  { to: '/panel/calendario', label: 'Calendario', icon: 'calendar_month' },
  { to: '/panel/configuracion', label: 'Configuración', icon: 'settings' },
]

const avisoEmail = ref(null)
onMounted(async () => {
  try {
    const { data } = await api.get('/sistema/notificaciones')
    if (data.fallosConsecutivos > 0) avisoEmail.value = data
  } catch {
    /* sin aviso si el endpoint no responde */
  }
})

async function salir() {
  try {
    await api.post('/auth/logout')
  } catch {
    /* token ya inválido */
  }
  auth.logoutLocal()
  router.push('/login')
}

function descargarJson(data) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `reservakids-${auth.slug}-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
}

const toastMensaje = ref('')
const toastTipo = ref('exito')

// Exportar datos
async function exportarDatos() {
  try {
    const { data } = await api.get('/tenant/export')
    descargarJson(data)
    toastMensaje.value = 'Datos exportados correctamente'
    toastTipo.value = 'exito'
  } catch (e) {
    toastMensaje.value = e.response?.data?.message || 'No se pudieron exportar los datos'
    toastTipo.value = 'error'
  }
}

// Cerrar negocio
const modalCerrar = ref(false)
const slugConfirmacion = ref('')

function abrirCerrarNegocio() {
  slugConfirmacion.value = ''
  modalCerrar.value = true
}

async function confirmarCierre() {
  try {
    const { data } = await api.post('/tenant/cerrar', { slugConfirmacion: slugConfirmacion.value })
    descargarJson(data)
    toastMensaje.value = 'Negocio cerrado. Se descargó una copia de todos tus datos.'
    toastTipo.value = 'exito'
    auth.logoutLocal()
    router.push('/login')
  } catch (e) {
    toastMensaje.value = e.response?.data?.message || 'No se pudo cerrar el negocio'
    toastTipo.value = 'error'
  }
}
</script>

<template>
  <div class="min-h-screen bg-surface flex">
    <!-- Sidebar (desktop) -->
    <aside class="hidden md:flex flex-col h-screen p-4 fixed left-0 top-0 w-64 bg-surface-low z-40">
      <div class="flex items-center gap-3 mb-8 px-3 pt-4">
        <div class="w-10 h-10 rounded-xl bg-primary-container flex items-center justify-center shadow-soft">
          <span class="material-symbols-outlined text-on-primary-container">celebration</span>
        </div>
        <div class="min-w-0">
          <h1 class="font-display font-bold text-primary text-lg leading-tight truncate">{{ auth.nombreNegocio }}</h1>
          <p class="text-xs font-bold text-on-surface-variant">ReservaKids</p>
        </div>
      </div>

      <a
        :href="`/${auth.slug}`"
        target="_blank"
        class="w-full bg-primary text-on-primary font-bold py-3 px-4 rounded-full shadow-soft hover:shadow-lifted hover:-translate-y-0.5 active:translate-y-0 transition-all duration-200 mb-6 flex items-center justify-center gap-1 text-sm"
      >
        <span class="material-symbols-outlined text-[20px]">open_in_new</span>
        Ver mi página
      </a>

      <nav class="flex-1 space-y-1" role="navigation" aria-label="Navegación principal">
        <RouterLink
          v-for="t in tabs"
          :key="t.to"
          :to="t.to"
          class="flex items-center gap-4 text-on-surface-variant px-4 py-3 hover:bg-surface-highest rounded-xl transition-all font-bold text-sm"
          active-class="!bg-primary-container !text-on-primary-container shadow-sm"
        >
          <span class="material-symbols-outlined">{{ t.icon }}</span>
          <span>{{ t.label }}</span>
        </RouterLink>
      </nav>

      <div class="mt-auto pt-6 border-t border-outline-variant/30 space-y-1">
        <button
          class="w-full flex items-center gap-4 text-on-surface-variant px-4 py-2.5 hover:bg-surface-highest rounded-xl transition-all font-bold text-sm"
          @click="exportarDatos"
        >
          <span class="material-symbols-outlined">download</span>
          Exportar mis datos
        </button>
        <button
          class="w-full flex items-center gap-4 text-on-surface-variant px-4 py-2.5 hover:bg-error-container hover:text-on-error-container rounded-xl transition-all font-bold text-sm"
          @click="abrirCerrarNegocio"
        >
          <span class="material-symbols-outlined">door_open</span>
          Cerrar negocio
        </button>
        <button
          class="w-full flex items-center gap-4 text-on-surface-variant px-4 py-2.5 hover:bg-surface-highest rounded-xl transition-all font-bold text-sm"
          @click="salir"
        >
          <span class="material-symbols-outlined">logout</span>
          Salir
        </button>
      </div>
    </aside>

    <!-- Contenido -->
    <div class="flex-1 md:ml-64 flex flex-col min-h-screen">
      <!-- Top bar (móvil) -->
      <header
        class="md:hidden sticky top-0 z-40 bg-surface-low/90 backdrop-blur-sm px-5 py-3 flex items-center justify-between"
      >
        <div class="flex items-center gap-2 min-w-0">
          <div class="w-9 h-9 rounded-xl bg-primary-container flex items-center justify-center shrink-0">
            <span class="material-symbols-outlined text-on-primary-container text-[20px]">celebration</span>
          </div>
          <h1 class="font-display font-bold text-primary truncate">{{ auth.nombreNegocio }}</h1>
        </div>
        <div class="flex items-center gap-1">
          <a
            :href="`/${auth.slug}`"
            target="_blank"
            aria-label="Ver mi página pública"
            class="p-2 rounded-full hover:bg-surface-highest transition-colors active:scale-95"
          >
            <span class="material-symbols-outlined text-primary">open_in_new</span>
          </a>
          <button
            aria-label="Salir"
            class="p-2 rounded-full hover:bg-surface-highest transition-colors active:scale-95"
            @click="salir"
          >
            <span class="material-symbols-outlined text-primary">logout</span>
          </button>
        </div>
      </header>

      <div
        v-if="avisoEmail"
        class="bg-tertiary-fixed text-on-tertiary-fixed text-sm font-semibold px-4 py-2 text-center flex items-center justify-center gap-2"
      >
        <span class="material-symbols-outlined text-[18px]">warning</span>
        Los avisos por email están fallando ({{ avisoEmail.fallosConsecutivos }} seguidos) — revisa las solicitudes
        directamente en el panel y la configuración SMTP.
      </div>

      <main class="flex-1 w-full max-w-5xl mx-auto p-5 md:p-8 pb-28 md:pb-8">
        <RouterView />
      </main>

      <!-- Offboarding (móvil) -->
      <footer class="md:hidden text-center text-xs font-medium text-outline py-3 space-x-2 pb-24">
        <button class="underline hover:text-primary" @click="exportarDatos">Exportar mis datos</button>
        <span>·</span>
        <button class="underline hover:text-error" @click="abrirCerrarNegocio">Cerrar negocio definitivamente</button>
      </footer>

      <!-- Bottom nav (móvil) -->
      <nav
        class="md:hidden fixed bottom-0 inset-x-0 z-40 bg-surface-low/95 backdrop-blur-sm border-t border-outline-variant/30 px-2 pt-2 pb-3 flex justify-around"
        role="navigation"
        aria-label="Navegación móvil"
      >
        <RouterLink
          v-for="t in tabs"
          :key="t.to"
          :to="t.to"
          class="flex flex-col items-center gap-0.5 px-3 py-1.5 rounded-2xl text-on-surface-variant transition-all"
          active-class="!bg-primary-container !text-on-primary-container shadow-sm"
        >
          <span class="material-symbols-outlined">{{ t.icon }}</span>
          <span class="text-[11px] font-bold">{{ t.label }}</span>
        </RouterLink>
      </nav>
    </div>

    <!-- Modal cerrar negocio -->
    <BaseModal
      :visible="modalCerrar"
      titulo="Cerrar negocio"
      @cerrar="modalCerrar = false"
      @cancelar="modalCerrar = false"
      @confirmar="confirmarCierre"
    >
      <p class="text-sm font-medium text-on-surface-variant mb-3">
        Esto cancelará tus solicitudes abiertas, ocultará tu página pública y bloqueará el acceso. Tus datos se
        eliminarán definitivamente en 90 días.
      </p>
      <p class="text-sm font-bold text-on-surface mb-2">
        Para confirmar, escribe el identificador de tu negocio: <span class="text-primary">{{ auth.slug }}</span>
      </p>
      <input
        v-model="slugConfirmacion"
        :placeholder="auth.slug"
        aria-label="Identificador del negocio"
        class="input-festivo"
      />
    </BaseModal>

    <BaseToast :mensaje="toastMensaje" :tipo="toastTipo" @cerrar="toastMensaje = ''" />
  </div>
</template>
