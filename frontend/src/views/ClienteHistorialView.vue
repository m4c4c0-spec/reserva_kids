<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import * as clienteService from '../services/clienteService'
import { useClienteAuthStore } from '../stores/clienteAuth'
import { clp } from '../composables/useCurrency'
import { useAsync } from '../composables/useAsync'
import bgV2 from '../assets/login-bg-v2.webp'
import BasePagination from '../components/BasePagination.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'

const router = useRouter()
const auth = useClienteAuthStore()

const reservas = ref([])
const filtro = ref('')
const paginaActual = ref(0)
const totalPaginas = ref(0)

const ESTADOS = ['', 'PENDIENTE', 'PENDIENTE_PAGO', 'COTIZADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA']

const {
  cargando,
  error: errorCarga,
  ejecutar: cargar,
} = useAsync(async () => {
  const data = await clienteService.listarReservas(filtro.value, paginaActual.value)
  reservas.value = data.content
  totalPaginas.value = data.totalPages
})

function cambiarPagina(p) {
  paginaActual.value = p
  cargar()
}

function reiniciarPagina() {
  paginaActual.value = 0
  cargar()
}

async function salir() {
  await auth.logout()
  router.push('/clientes/entrar')
}

onMounted(cargar)
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed" :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="max-w-2xl mx-auto p-5 space-y-6">
      <header class="flex items-center justify-between pt-6">
        <div>
          <h1 class="font-display font-extrabold text-2xl md:text-3xl text-primary tracking-tight">Mis reservas</h1>
          <p class="font-medium text-on-surface-variant text-sm mt-0.5">Sigue el estado de tus solicitudes.</p>
        </div>
        <button
          class="flex items-center gap-1 text-sm font-bold text-on-surface-variant hover:text-primary transition-colors"
          @click="salir"
        >
          <span class="material-symbols-outlined text-[20px]">logout</span> Salir
        </button>
      </header>

      <div class="flex items-center justify-between gap-2">
        <RouterLink
          to="/clientes/negocios"
          class="text-sm font-bold text-secondary hover:text-secondary-container transition-colors flex items-center gap-1"
        >
          <span class="material-symbols-outlined text-[20px]">add</span> Agendar otra hora
        </RouterLink>
        <select
          v-model="filtro"
          aria-label="Filtrar reservas por estado"
          class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer"
          @change="reiniciarPagina"
        >
          <option v-for="e in ESTADOS" :key="e" :value="e">{{ e || 'Todas' }}</option>
        </select>
      </div>

      <ErrorBanner :mensaje="errorCarga" />
      <LoadingSpinner v-if="cargando" />

      <ul v-else-if="reservas.length" class="space-y-4">
        <li v-for="r in reservas" :key="r.id" class="card-festiva space-y-2">
          <div class="flex items-center justify-between">
            <p class="font-display font-bold text-lg text-on-surface">Reserva #{{ r.id }}</p>
            <StatusBadge :estado="r.estado" />
          </div>
          <p class="text-sm font-medium text-on-surface-variant">
            {{ new Date(r.creadaEn).toLocaleString('es-CL') }}
          </p>
          <p v-if="r.comentarios" class="text-sm font-medium text-outline whitespace-pre-line">{{ r.comentarios }}</p>
          <p v-if="r.totalClp != null" class="text-sm font-medium text-on-surface bg-surface-low rounded-xl px-3 py-2">
            Total {{ clp(r.totalClp) }}
          </p>
          <a
            v-if="r.linkWhatsApp"
            :href="r.linkWhatsApp"
            target="_blank"
            class="inline-block bg-[#dcfce7] text-green-800 font-bold rounded-full px-4 py-1.5 text-sm hover:shadow-md transition-all"
            >WhatsApp</a
          >
        </li>
      </ul>

      <EmptyState v-else mensaje="No tienes reservas todavía." icono="calendar_today" />

      <BasePagination :pagina-actual="paginaActual" :total-paginas="totalPaginas" @cambiar-pagina="cambiarPagina" />
    </div>
  </main>
</template>
