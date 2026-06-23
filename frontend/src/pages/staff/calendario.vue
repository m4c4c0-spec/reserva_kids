<script setup>
import { ref, computed, onMounted } from 'vue'
import { useStaffStore } from '../../stores/staff'

definePageMeta({ layout: 'default', requiereStaff: true })

const store = useStaffStore()
const router = useRouter()
const fecha = ref(new Date().toISOString().slice(0, 10))
const eventos = ref([])
const cargando = ref(true)
const error = ref('')

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    const baseURL = (import.meta.env.VITE_API_URL || import.meta.env.NUXT_PUBLIC_API_URL || '') + '/api'
    const resp = await fetch(`${baseURL}/staff/eventos?fecha=${fecha.value}`, {
      headers: { Authorization: `Bearer ${store.accessToken}` },
    })
    if (!resp.ok) throw new Error('Error al cargar eventos')
    eventos.value = await resp.json()
  } catch (e) {
    error.value = e.message
  } finally {
    cargando.value = false
  }
}

function salir() {
  store.logout()
  router.push('/staff/entrar')
}

const fechaFormateada = computed(() => {
  const [y, m, d] = fecha.value.split('-')
  return `${d}/${m}/${y}`
})

onMounted(() => {
  if (!store.autenticado) {
    router.push('/staff/entrar')
    return
  }
  cargar()
})
</script>

<template>
  <main class="min-h-screen bg-surface p-4">
    <header class="max-w-2xl mx-auto flex items-center justify-between mb-6">
      <div>
        <h1 class="font-display font-bold text-xl text-on-surface">Calendario Staff</h1>
        <p class="text-sm font-medium text-on-surface-variant">{{ store.nombre }} · {{ store.rol }}</p>
      </div>
      <div class="flex items-center gap-2">
        <input v-model="fecha" type="date" class="input-festivo !w-40 text-sm" @change="cargar" />
        <BaseButton variante="secundario" @click="salir">Salir</BaseButton>
      </div>
    </header>

    <LoadingSpinner v-if="cargando" />
    <ErrorBanner v-else-if="error" :mensaje="error" />

    <div v-else-if="!eventos.length" class="max-w-2xl mx-auto text-center bg-surface-lowest rounded-3xl p-10 shadow-sm">
      <span class="material-symbols-outlined text-5xl text-outline">event_busy</span>
      <p class="mt-4 font-display font-bold text-lg text-on-surface">Sin eventos el {{ fechaFormateada }}</p>
      <p class="text-sm font-medium text-on-surface-variant mt-1">Día libre. ¡A descansar!</p>
    </div>

    <ul v-else class="max-w-2xl mx-auto space-y-3">
      <li v-for="e in eventos" :key="e.id" class="bg-surface-lowest rounded-2xl p-4 shadow-sm border border-outline-variant/10 flex items-center gap-4">
        <div class="w-14 h-14 rounded-xl bg-primary-container flex items-center justify-center flex-shrink-0">
          <span class="font-display font-extrabold text-lg text-on-primary-container">{{ e.hora }}</span>
        </div>
        <div class="flex-1 min-w-0">
          <p class="font-bold text-on-surface">Fiesta infantil</p>
          <p class="text-sm font-medium text-on-surface-variant">👶 {{ e.numNinos || '—' }} niños<span v-if="e.comuna"> · {{ e.comuna }}</span></p>
        </div>
        <span class="px-2 py-1 rounded-full text-[10px] font-extrabold" :class="e.estado === 'CONFIRMADA' ? 'bg-green-100 text-green-800' : 'bg-blue-100 text-blue-800'">{{ e.estado }}</span>
      </li>
    </ul>
  </main>
</template>
