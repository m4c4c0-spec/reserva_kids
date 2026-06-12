<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import api from '../api/client'

const router = useRouter()
const auth = useAuthStore()

// Falla #3 (revisión 2 años): si el email lleva fallos, avisar — antes la degradación era invisible
const avisoEmail = ref(null)
onMounted(async () => {
  try {
    const { data } = await api.get('/sistema/notificaciones')
    if (data.fallosConsecutivos > 0) avisoEmail.value = data
  } catch { /* sin aviso si el endpoint no responde */ }
})

async function salir() {
  // Fix #6: enviar el refresh token — con el access vencido, el backend revoca por hash
  try { await api.post('/auth/logout', { refreshToken: auth.refreshToken }) } catch { /* token ya inválido */ }
  auth.logoutLocal()
  router.push('/login')
}

// Falla 3.3 (revisión 5 años): portabilidad — el dueño descarga TODOS sus datos en JSON
function descargarJson(data) {
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `reservakids-${auth.slug}-${new Date().toISOString().slice(0, 10)}.json`
  a.click()
  URL.revokeObjectURL(url)
}

async function exportarDatos() {
  const { data } = await api.get('/tenant/export')
  descargarJson(data)
}

// Falla 3.3: cierre self-service. El backend exige el slug exacto (anti accidente),
// rechaza si hay CONFIRMADAS con seña pendiente de resolver, y devuelve el export final.
async function cerrarNegocio() {
  const confirmacion = window.prompt(
    `Esto cancelará tus solicitudes abiertas, ocultará tu página pública y bloqueará el acceso.\n` +
    `Tus datos se eliminarán definitivamente en 90 días.\n\n` +
    `Para confirmar, escribe el identificador de tu negocio: ${auth.slug}`)
  if (confirmacion === null) return
  try {
    const { data } = await api.post('/tenant/cerrar', { slugConfirmacion: confirmacion })
    descargarJson(data) // última copia garantizada antes de la purga
    alert('Negocio cerrado. Se descargó una copia de todos tus datos.')
    auth.logoutLocal()
    router.push('/login')
  } catch (e) {
    alert(e.response?.data?.message || 'No se pudo cerrar el negocio')
  }
}
</script>

<template>
  <div class="min-h-screen bg-gray-50 flex flex-col">
    <header class="bg-violet-700 text-white">
      <div class="max-w-5xl mx-auto px-4 py-3 flex items-center justify-between">
        <h1 class="font-bold">🎈 {{ auth.nombreNegocio }}</h1>
        <div class="flex items-center gap-3 text-sm">
          <a :href="`/${auth.slug}`" target="_blank" class="underline hidden sm:inline">Ver mi página</a>
          <button @click="salir" class="bg-violet-900/40 rounded px-3 py-1">Salir</button>
        </div>
      </div>
      <nav class="max-w-5xl mx-auto px-4 flex gap-1 text-sm">
        <RouterLink v-for="t in [
              { to: '/panel/solicitudes', label: 'Solicitudes' },
              { to: '/panel/servicios', label: 'Servicios' },
              { to: '/panel/calendario', label: 'Calendario' },
            ]" :key="t.to" :to="t.to"
            class="px-3 py-2 rounded-t-lg hover:bg-violet-600"
            active-class="bg-gray-50 text-violet-700 font-semibold">
          {{ t.label }}
        </RouterLink>
      </nav>
    </header>

    <div v-if="avisoEmail" class="bg-amber-100 text-amber-800 text-sm px-4 py-2 text-center">
      ⚠️ Los avisos por email están fallando ({{ avisoEmail.fallosConsecutivos }} seguidos) —
      revisa las solicitudes directamente en el panel y la configuración SMTP.
    </div>

    <main class="flex-1 max-w-5xl w-full mx-auto p-4">
      <RouterView />
    </main>

    <!-- Falla 3.3 (revisión 5 años): offboarding self-service — portabilidad y cierre -->
    <footer class="text-center text-xs text-gray-400 py-3 space-x-2">
      <button @click="exportarDatos" class="underline hover:text-violet-600">Exportar mis datos</button>
      <span>·</span>
      <button @click="cerrarNegocio" class="underline hover:text-red-600">Cerrar negocio definitivamente</button>
    </footer>
  </div>
</template>
