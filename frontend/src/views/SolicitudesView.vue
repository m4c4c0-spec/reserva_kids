<script setup>
import { ref, onMounted } from 'vue'
import api from '../api/client'
import * as reservaService from '../services/reservaService'
import { clp } from '../composables/useCurrency'
import { useAsync } from '../composables/useAsync'
import BaseButton from '../components/BaseButton.vue'
import BaseModal from '../components/BaseModal.vue'
import BasePagination from '../components/BasePagination.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'
import OnboardingBanner from '../components/OnboardingBanner.vue'

const reservas = ref([])
const filtro = ref('')
const paginaActual = ref(0)
const totalPaginas = ref(0)
const cotizando = ref(null)
const cotizacion = ref({ totalClp: null, seniaClp: null })
const pagando = ref(null)
const pago = ref({ montoClp: null, medio: 'TRANSFERENCIA', comprobanteUrl: '' })
const cancelandoId = ref(null)
const motivoCancelacion = ref('')

const metricas = ref(null)
const cargandoMetricas = ref(true)

const ESTADOS = ['', 'PENDIENTE', 'COTIZADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA']

const {
  cargando,
  error: errorCarga,
  ejecutar: cargar,
} = useAsync(async () => {
  const data = await reservaService.listar(filtro.value, paginaActual.value)
  reservas.value = data.content
  totalPaginas.value = data.totalPages
})

const { error: errorAccion, ejecutar: ejecutarAccion } = useAsync(async (fn) => {
  await fn()
  await cargar()
})

function cambiarPagina(p) {
  paginaActual.value = p
  cargar()
}

function reiniciarPagina() {
  paginaActual.value = 0
  cargar()
}

async function cotizar(r) {
  await ejecutarAccion(async () => {
    await reservaService.cotizar(r.id, cotizacion.value)
    cotizando.value = null
  })
}

async function confirmarReserva(r) {
  await ejecutarAccion(() => reservaService.confirmar(r.id))
}

async function realizar(r) {
  await ejecutarAccion(() => reservaService.realizar(r.id))
}

function abrirCancelacion(r) {
  cancelandoId.value = r.id
  motivoCancelacion.value = ''
}

async function confirmarCancelacion() {
  await ejecutarAccion(async () => {
    await reservaService.cancelar(cancelandoId.value, motivoCancelacion.value)
    cancelandoId.value = null
  })
}

async function registrarPago(r) {
  await ejecutarAccion(async () => {
    await reservaService.registrarPago(r.id, pago.value)
    pagando.value = null
    pago.value = { montoClp: null, medio: 'TRANSFERENCIA', comprobanteUrl: '' }
  })
}

async function cargarMetricas() {
  cargandoMetricas.value = true
  try {
    const { data } = await api.get('/sistema/dashboard')
    metricas.value = data
  } catch (e) {
    console.error('Error cargando métricas:', e)
  } finally {
    cargandoMetricas.value = false
  }
}

onMounted(() => {
  cargar()
  cargarMetricas()
})
</script>

<template>
  <section class="space-y-5">
    <OnboardingBanner />
    <div class="flex items-center justify-between">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Solicitudes</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Gestiona las reservas de tus clientes.</p>
      </div>
      <select
        v-model="filtro"
        aria-label="Filtrar solicitudes por estado"
        class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer"
        @change="reiniciarPagina"
      >
        <option v-for="e in ESTADOS" :key="e" :value="e">{{ e || 'Todas' }}</option>
      </select>
    </div>

    <!-- KPIs / Metrics -->
    <div v-if="!cargandoMetricas && metricas" class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
      <div class="card-festiva flex flex-col justify-between">
        <div class="flex items-center gap-2 mb-2 text-on-surface-variant">
          <span class="material-symbols-outlined text-[20px]">payments</span>
          <h3 class="font-bold text-sm">Señas Recaudadas</h3>
        </div>
        <p class="font-display font-extrabold text-3xl text-primary">{{ clp(metricas.recaudadoSenas || 0) }}</p>
        <p class="text-xs font-semibold text-outline mt-1">Total histórico</p>
      </div>
      <div class="card-festiva flex flex-col justify-between">
        <div class="flex items-center gap-2 mb-2 text-on-surface-variant">
          <span class="material-symbols-outlined text-[20px]">event_available</span>
          <h3 class="font-bold text-sm">Tasa de Ocupación</h3>
        </div>
        <div class="flex items-end gap-2">
          <p class="font-display font-extrabold text-3xl text-primary">{{ metricas.tasaOcupacion }}%</p>
        </div>
        <div class="w-full bg-surface-highest rounded-full h-1.5 mt-2">
          <div class="bg-primary h-1.5 rounded-full" :style="{ width: `${metricas.tasaOcupacion}%` }"></div>
        </div>
        <p class="text-xs font-semibold text-outline mt-1">Este mes</p>
      </div>
      <div class="card-festiva flex flex-col justify-between">
        <div class="flex items-center gap-2 mb-2 text-on-surface-variant">
          <span class="material-symbols-outlined text-[20px]">star</span>
          <h3 class="font-bold text-sm">Servicio Estrella</h3>
        </div>
        <template v-if="metricas.topServicios?.length">
          <p class="font-bold text-on-surface truncate">{{ metricas.topServicios[0].nombreServicio }}</p>
          <p class="text-xs font-semibold text-outline mt-1">{{ metricas.topServicios[0].cantidad }} reservas</p>
        </template>
        <p v-else class="text-sm text-on-surface-variant font-medium">Sin datos aún</p>
      </div>
    </div>

    <ErrorBanner :mensaje="errorCarga || errorAccion" />

    <LoadingSpinner v-if="cargando" />

    <ul v-else class="space-y-4">
      <li v-for="r in reservas" :key="r.id" class="card-festiva card-festiva--interactiva space-y-2">
        <div class="flex items-center justify-between">
          <p class="font-display font-bold text-lg text-on-surface">Solicitud #{{ r.id }}</p>
          <StatusBadge :estado="r.estado" />
        </div>
        <!-- A1: confirmar reserva el horario; lo decimos explícito para dar confianza. -->
        <p v-if="r.estado === 'CONFIRMADA'" class="flex items-center gap-1 text-xs font-semibold text-green-800">
          <span class="material-symbols-outlined text-[15px]">shield_person</span>
          Horario reservado para este cliente — nadie más puede tomarlo.
        </p>
        <p class="text-sm font-medium text-on-surface-variant">
          <span v-if="r.numNinos">{{ r.numNinos }} niños · </span>
          <span v-if="r.comuna">{{ r.comuna }} · </span>
          {{ new Date(r.creadaEn).toLocaleString('es-CL') }}
        </p>
        <p v-if="r.comentarios" class="text-sm font-medium text-outline whitespace-pre-line">{{ r.comentarios }}</p>
        <p v-if="r.totalClp != null" class="text-sm font-medium text-on-surface bg-surface-low rounded-xl px-3 py-2">
          Total {{ clp(r.totalClp) }} · Seña {{ clp(r.seniaClp) }} · Pagado {{ clp(r.pagadoClp) }} ·
          <strong class="text-primary">Saldo {{ clp(r.saldoClp) }}</strong>
        </p>

        <form v-if="cotizando === r.id" class="flex flex-wrap gap-2 items-end" @submit.prevent="cotizar(r)">
          <input
            v-model.number="cotizacion.totalClp"
            type="number"
            min="0"
            required
            placeholder="Total CLP"
            aria-label="Total en pesos chilenos"
            class="input-festivo !w-32"
          />
          <input
            v-model.number="cotizacion.seniaClp"
            type="number"
            min="0"
            required
            placeholder="Seña CLP"
            aria-label="Seña en pesos chilenos"
            class="input-festivo !w-32"
          />
          <BaseButton variante="primario">Enviar cotización</BaseButton>
        </form>

        <form v-if="pagando === r.id" class="flex flex-wrap gap-2 items-end" @submit.prevent="registrarPago(r)">
          <input
            v-model.number="pago.montoClp"
            type="number"
            min="1"
            required
            placeholder="Monto CLP"
            aria-label="Monto del pago en pesos chilenos"
            class="input-festivo !w-32"
          />
          <select v-model="pago.medio" aria-label="Medio de pago" class="input-festivo">
            <option>TRANSFERENCIA</option>
            <option>EFECTIVO</option>
            <option>OTRO</option>
          </select>
          <input
            v-model="pago.comprobanteUrl"
            placeholder="URL comprobante (opcional)"
            aria-label="URL del comprobante de pago"
            class="input-festivo !flex-1 !min-w-40"
          />
          <BaseButton variante="secundario">Registrar seña</BaseButton>
        </form>

        <div class="flex flex-wrap gap-2 pt-1 text-sm">
          <a
            v-if="r.linkWhatsApp"
            :href="r.linkWhatsApp"
            target="_blank"
            class="bg-[#dcfce7] text-green-800 font-bold rounded-full px-4 py-1.5 hover:shadow-md transition-all"
            >WhatsApp</a
          >
          <a
            v-if="r.estado === 'COTIZADA' && r.mpInitPoint"
            :href="r.mpInitPoint"
            target="_blank"
            class="bg-secondary-fixed text-on-secondary-container font-bold rounded-full px-4 py-1.5 hover:shadow-md transition-all"
            >Link de Pago</a
          >
          <button
            v-if="r.estado === 'PENDIENTE'"
            class="border-2 border-outline-variant font-bold text-on-surface-variant rounded-full px-4 py-1.5 hover:bg-surface-low transition-colors"
            @click="cotizando = cotizando === r.id ? null : r.id"
          >
            Cotizar
          </button>
          <button
            v-if="['COTIZADA', 'CONFIRMADA'].includes(r.estado)"
            class="border-2 border-outline-variant font-bold text-on-surface-variant rounded-full px-4 py-1.5 hover:bg-surface-low transition-colors"
            @click="pagando = pagando === r.id ? null : r.id"
          >
            Registrar pago
          </button>
          <BaseButton v-if="r.estado === 'COTIZADA'" @click="confirmarReserva(r)">Confirmar</BaseButton>
          <BaseButton v-if="r.estado === 'CONFIRMADA'" @click="realizar(r)">Marcar realizada</BaseButton>
          <button
            v-if="['PENDIENTE', 'COTIZADA', 'CONFIRMADA'].includes(r.estado)"
            class="text-error font-bold px-2 hover:underline"
            @click="abrirCancelacion(r)"
          >
            Cancelar
          </button>
        </div>
      </li>
    </ul>

    <EmptyState v-if="!cargando && !reservas.length" mensaje="No hay solicitudes." icono="inbox" />

    <BasePagination :pagina-actual="paginaActual" :total-paginas="totalPaginas" @cambiar-pagina="cambiarPagina" />

    <BaseModal
      :visible="!!cancelandoId"
      titulo="Cancelar solicitud"
      @cerrar="cancelandoId = null"
      @cancelar="cancelandoId = null"
      @confirmar="confirmarCancelacion"
    >
      <p class="text-sm font-medium text-on-surface-variant mb-3">Motivo de cancelación (opcional):</p>
      <input
        v-model="motivoCancelacion"
        placeholder="Ej: Cliente desiste"
        aria-label="Motivo de cancelación"
        class="input-festivo"
      />
    </BaseModal>
  </section>
</template>
