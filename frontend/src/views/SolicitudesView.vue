<script setup>
import { ref, onMounted } from 'vue'
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
const exito = ref('')

const ESTADOS = ['', 'PENDIENTE', 'COTIZADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA']

const { cargando, error: errorCarga, ejecutar: cargar } = useAsync(async () => {
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

onMounted(cargar)
</script>

<template>
  <section class="space-y-5">
    <div class="flex items-center justify-between gap-2">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Solicitudes</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Gestiona las reservas de tus clientes.</p>
      </div>
      <select v-model="filtro" @change="paginaActual = 0; cargar()"
              class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer">
        <option v-for="e in ESTADOS" :key="e" :value="e">{{ e || 'Todas' }}</option>
      </select>
    </div>

    <ErrorBanner :mensaje="errorCarga || errorAccion" />

    <LoadingSpinner v-if="cargando" />

    <ul v-else class="space-y-4">
      <li v-for="r in reservas" :key="r.id"
          class="bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-5 space-y-2 hover:shadow-lifted transition-shadow">
        <div class="flex items-center justify-between">
          <p class="font-display font-bold text-lg text-on-surface">Solicitud #{{ r.id }}</p>
          <StatusBadge :estado="r.estado" />
        </div>
        <p class="text-sm font-medium text-on-surface-variant">
          <span v-if="r.numNinos">{{ r.numNinos }} niños · </span>
          <span v-if="r.comuna">{{ r.comuna }} · </span>
          {{ new Date(r.creadaEn).toLocaleString('es-CL') }}
        </p>
        <p v-if="r.comentarios" class="text-sm font-medium text-outline whitespace-pre-line">{{ r.comentarios }}</p>
        <p v-if="r.totalClp != null" class="text-sm font-medium text-on-surface bg-surface-low rounded-xl px-3 py-2">
          Total {{ clp(r.totalClp) }} · Seña {{ clp(r.seniaClp) }} ·
          Pagado {{ clp(r.pagadoClp) }} · <strong class="text-primary">Saldo {{ clp(r.saldoClp) }}</strong>
        </p>

        <form v-if="cotizando === r.id" @submit.prevent="cotizar(r)" class="flex flex-wrap gap-2 items-end">
          <input v-model.number="cotizacion.totalClp" type="number" min="0" required placeholder="Total CLP"
                 class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium w-32 focus:outline-none focus:border-secondary" />
          <input v-model.number="cotizacion.seniaClp" type="number" min="0" required placeholder="Seña CLP"
                 class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium w-32 focus:outline-none focus:border-secondary" />
          <BaseButton variante="primario">Enviar cotización</BaseButton>
        </form>

        <form v-if="pagando === r.id" @submit.prevent="registrarPago(r)" class="flex flex-wrap gap-2 items-end">
          <input v-model.number="pago.montoClp" type="number" min="1" required placeholder="Monto CLP"
                 class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium w-32 focus:outline-none focus:border-secondary" />
          <select v-model="pago.medio"
                  class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium focus:outline-none focus:border-secondary">
            <option>TRANSFERENCIA</option>
            <option>EFECTIVO</option>
            <option>OTRO</option>
          </select>
          <input v-model="pago.comprobanteUrl" placeholder="URL comprobante (opcional)"
                 class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium flex-1 min-w-40 focus:outline-none focus:border-secondary" />
          <BaseButton variante="secundario">Registrar seña</BaseButton>
        </form>

        <div class="flex flex-wrap gap-2 pt-1 text-sm">
          <a v-if="r.linkWhatsApp" :href="r.linkWhatsApp" target="_blank"
             class="bg-[#dcfce7] text-green-800 font-bold rounded-full px-4 py-1.5 hover:shadow-md transition-all">WhatsApp</a>
          <a v-if="r.estado === 'COTIZADA' && r.mpInitPoint" :href="r.mpInitPoint" target="_blank"
             class="bg-secondary-fixed text-on-secondary-container font-bold rounded-full px-4 py-1.5 hover:shadow-md transition-all">Link de Pago</a>
          <button v-if="r.estado === 'PENDIENTE'" @click="cotizando = cotizando === r.id ? null : r.id"
                  class="border-2 border-outline-variant font-bold text-on-surface-variant rounded-full px-4 py-1.5 hover:bg-surface-low transition-colors">Cotizar</button>
          <button v-if="['COTIZADA', 'CONFIRMADA'].includes(r.estado)"
                  @click="pagando = pagando === r.id ? null : r.id"
                  class="border-2 border-outline-variant font-bold text-on-surface-variant rounded-full px-4 py-1.5 hover:bg-surface-low transition-colors">Registrar pago</button>
          <BaseButton v-if="r.estado === 'COTIZADA'" @click="confirmarReserva(r)">Confirmar</BaseButton>
          <BaseButton v-if="r.estado === 'CONFIRMADA'" @click="realizar(r)">Marcar realizada</BaseButton>
          <button v-if="['PENDIENTE', 'COTIZADA', 'CONFIRMADA'].includes(r.estado)" @click="abrirCancelacion(r)"
                  class="text-error font-bold px-2 hover:underline">Cancelar</button>
        </div>
      </li>
    </ul>

    <EmptyState v-if="!cargando && !reservas.length"
                mensaje="No hay solicitudes."
                icono="inbox" />

    <BasePagination
      :paginaActual="paginaActual"
      :totalPaginas="totalPaginas"
      @cambiarPagina="cambiarPagina"
    />

    <BaseModal
      :visible="!!cancelandoId"
      titulo="Cancelar solicitud"
      @cerrar="cancelandoId = null"
      @cancelar="cancelandoId = null"
      @confirmar="confirmarCancelacion"
    >
      <p class="text-sm font-medium text-on-surface-variant mb-3">Motivo de cancelación (opcional):</p>
      <input v-model="motivoCancelacion" placeholder="Ej: Cliente desiste"
             class="w-full border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium text-sm focus:outline-none focus:border-secondary" />
    </BaseModal>
  </section>
</template>
