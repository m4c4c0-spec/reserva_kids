<script setup>
definePageMeta({ layout: 'panel', requiereAuth: true })

import { ref, computed, onMounted, nextTick } from 'vue'
import api from '../../api/client'
import * as reservaService from '../../services/reservaService'
import { clp } from '../../composables/useCurrency'
import { useAsync } from '../../composables/useAsync'
import BaseButton from '../../components/BaseButton.vue'
import BaseModal from '../../components/BaseModal.vue'
import BasePagination from '../../components/BasePagination.vue'
import BaseToast from '../../components/BaseToast.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import StatusBadge from '../../components/StatusBadge.vue'
import EmptyState from '../../components/EmptyState.vue'
import OnboardingBanner from '../../components/OnboardingBanner.vue'

const reservas = ref([])
const filtro = ref('')
const busqueda = ref('')
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

const cajaDiaria = ref(null)
const cargandoCaja = ref(true)
const fechaCaja = ref(new Date().toISOString().slice(0, 10))

const invitadosAbiertos = ref({})
const nuevoInvitado = ref({ nombre: '', email: '', telefono: '' })
const errorInvitado = ref('')

const origin = computed(() => (process.client ? window.location.origin : ''))

const ESTADOS = ['', 'PENDIENTE', 'COTIZADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA']

const {
  cargando,
  error: errorCarga,
  ejecutar: cargar,
} = useAsync(async () => {
  const data = await reservaService.listar(filtro.value, paginaActual.value, 20, busqueda.value)
  reservas.value = data.content
  totalPaginas.value = data.totalPages
})

let debounceBusqueda = null
function onBuscar() {
  clearTimeout(debounceBusqueda)
  debounceBusqueda = setTimeout(() => {
    paginaActual.value = 0
    cargar()
  }, 350)
}

const errorAccion = ref(null)
const accionEnCurso = ref(null)
const toast = ref({ mensaje: '', tipo: 'exito' })
const confirmacion = ref(null)

const enCurso = (clave) => accionEnCurso.value === clave

function notificar(mensaje, tipo = 'exito') {
  toast.value = { mensaje: '', tipo }
  nextTick(() => (toast.value = { mensaje, tipo }))
}

async function correr(clave, fn, exitoMsg) {
  errorAccion.value = null
  accionEnCurso.value = clave
  try {
    await fn()
    await cargar()
    if (exitoMsg) notificar(exitoMsg)
  } catch (e) {
    errorAccion.value = e.response?.data?.message || 'No se pudo completar la acción.'
  } finally {
    accionEnCurso.value = null
  }
}

const confirmTexto = computed(() => {
  const c = confirmacion.value
  if (!c) return { titulo: '', cuerpo: '', boton: '' }
  return c.tipo === 'confirmar'
    ? {
        titulo: 'Confirmar reserva',
        cuerpo: `Vas a confirmar la solicitud #${c.reserva.id}. El horario quedará reservado para este cliente y nadie más podrá tomarlo.`,
        boton: 'Sí, confirmar',
      }
    : {
        titulo: 'Marcar como realizada',
        cuerpo: `Vas a marcar la solicitud #${c.reserva.id} como realizada. Esto cierra la reserva y no se puede deshacer.`,
        boton: 'Sí, marcar realizada',
      }
})

function cambiarPagina(p) {
  paginaActual.value = p
  cargar()
}

function reiniciarPagina() {
  paginaActual.value = 0
  cargar()
}

const seniaInvalida = computed(
  () =>
    cotizacion.value.totalClp != null &&
    cotizacion.value.seniaClp != null &&
    cotizacion.value.seniaClp > cotizacion.value.totalClp,
)

async function cotizar(r) {
  if (seniaInvalida.value) {
    errorAccion.value = 'La seña no puede ser mayor que el total.'
    return
  }
  await correr(
    `cotizar-${r.id}`,
    async () => {
      await reservaService.cotizar(r.id, cotizacion.value)
      cotizando.value = null
    },
    'Cotización enviada al cliente.',
  )
}

function pedirConfirmacion(tipo, reserva) {
  confirmacion.value = { tipo, reserva }
}

async function ejecutarConfirmacion() {
  const c = confirmacion.value
  if (!c) return
  confirmacion.value = null
  if (c.tipo === 'confirmar') {
    await correr(`confirmar-${c.reserva.id}`, () => reservaService.confirmar(c.reserva.id), 'Reserva confirmada.')
  } else {
    await correr(
      `realizar-${c.reserva.id}`,
      () => reservaService.realizar(c.reserva.id),
      'Reserva marcada como realizada.',
    )
  }
}

function abrirCancelacion(r) {
  cancelandoId.value = r.id
  motivoCancelacion.value = ''
}

async function confirmarCancelacion() {
  const id = cancelandoId.value
  await correr(
    `cancelar-${id}`,
    async () => {
      await reservaService.cancelar(id, motivoCancelacion.value)
      cancelandoId.value = null
    },
    'Reserva cancelada.',
  )
}

async function registrarPago(r) {
  await correr(
    `pago-${r.id}`,
    async () => {
      await reservaService.registrarPago(r.id, pago.value)
      pagando.value = null
      pago.value = { montoClp: null, medio: 'TRANSFERENCIA', comprobanteUrl: '' }
    },
    'Pago registrado.',
  )
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
  cargarCaja()
})

async function cargarCaja() {
  try {
    cajaDiaria.value = await reservaService.caja(fechaCaja.value)
  } finally {
    cargandoCaja.value = false
  }
}

async function cargarInvitados(r) {
  try {
    r._invitados = await reservaService.listarInvitados(r.id)
  } catch {
    r._invitados = []
  }
}

async function agregarInvitado(r) {
  const { nombre, email, telefono } = nuevoInvitado.value
  if (!nombre.trim()) return
  try {
    await reservaService.agregarInvitado(r.id, {
      nombre: nombre.trim(),
      email: email || null,
      telefono: telefono || null,
    })
    nuevoInvitado.value = { nombre: '', email: '', telefono: '' }
    await cargarInvitados(r)
  } catch (e) {
    errorInvitado.value = e.response?.data?.message || 'Error al agregar invitado'
  }
}

async function eliminarInvitado(r, invitadoId) {
  try {
    await reservaService.eliminarInvitado(r.id, invitadoId)
    await cargarInvitados(r)
  } catch (e) {
    errorInvitado.value = e.response?.data?.message || 'Error al eliminar invitado'
  }
}
</script>

<template>
  <section class="space-y-5">
    <OnboardingBanner />
    <div class="flex items-center justify-between">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Solicitudes</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Gestiona las reservas de tus clientes.</p>
      </div>
      <div class="flex items-center gap-2 w-full sm:w-auto">
        <div class="relative flex-1 sm:flex-initial">
          <span
            class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]"
            >search</span
          >
          <input
            v-model="busqueda"
            type="search"
            placeholder="Buscar cliente o #reserva…"
            aria-label="Buscar por nombre de cliente o número de reserva"
            class="bg-surface-high rounded-full pl-10 pr-4 py-2 text-sm font-medium text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container w-full sm:w-56"
            @input="onBuscar"
          />
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
    </div>

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

    <div
      v-if="!cargandoCaja && cajaDiaria"
      class="card-festiva bg-amber-50/40 border border-amber-200 p-4 rounded-2xl mb-6"
    >
      <div class="flex items-center justify-between mb-3">
        <div class="flex items-center gap-2">
          <span class="material-symbols-outlined text-[20px] text-amber-700">point_of_sale</span>
          <h3 class="font-bold text-sm text-amber-900">Caja diaria — {{ fechaCaja }}</h3>
        </div>
        <input v-model="fechaCaja" type="date" class="input-festivo !w-40 text-sm" @change="cargarCaja" />
      </div>
      <div class="grid grid-cols-2 md:grid-cols-4 gap-3 text-center">
        <div>
          <p class="text-xs font-bold text-amber-700">Reservas hoy</p>
          <p class="font-display font-extrabold text-xl text-on-surface">{{ cajaDiaria.totalReservas }}</p>
          <p class="text-[11px] font-medium text-outline">({{ cajaDiaria.reservasConfirmadas }} confirmadas)</p>
        </div>
        <div>
          <p class="text-xs font-bold text-amber-700">Citas hoy</p>
          <p class="font-display font-extrabold text-xl text-on-surface">{{ cajaDiaria.citasAgendadas }}</p>
        </div>
        <div>
          <p class="text-xs font-bold text-amber-700">Recaudado hoy</p>
          <p class="font-display font-extrabold text-xl text-green-700">{{ clp(cajaDiaria.totalRecaudadoHoy) }}</p>
          <p class="text-[11px] font-medium text-outline">{{ cajaDiaria.pagosHoy }} pagos</p>
        </div>
        <div>
          <p class="text-xs font-bold text-amber-700">Saldo pendiente</p>
          <p class="font-display font-extrabold text-xl text-red-600">{{ clp(cajaDiaria.saldoPendienteTotal) }}</p>
          <p class="text-[11px] font-medium text-outline">Seña prom. {{ clp(cajaDiaria.seniaPromedio) }}</p>
        </div>
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

        <form
          v-if="cotizando === r.id"
          class="flex flex-wrap gap-2 items-end bg-primary-container/15 border border-primary-container rounded-2xl p-3"
          @submit.prevent="cotizar(r)"
        >
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
          <BaseButton variante="primario" :cargando="enCurso(`cotizar-${r.id}`)" :deshabilitado="seniaInvalida"
            >Enviar cotización</BaseButton
          >
          <p v-if="seniaInvalida" class="w-full text-xs font-semibold text-error">
            La seña no puede ser mayor que el total.
          </p>
        </form>

        <form
          v-if="pagando === r.id"
          class="flex flex-wrap gap-2 items-end bg-secondary-container/15 border border-secondary-container rounded-2xl p-3"
          @submit.prevent="registrarPago(r)"
        >
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
          <BaseButton variante="secundario" :cargando="enCurso(`pago-${r.id}`)">Registrar seña</BaseButton>
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
          <BaseButton
            v-if="r.estado === 'COTIZADA'"
            :cargando="enCurso(`confirmar-${r.id}`)"
            @click="pedirConfirmacion('confirmar', r)"
            >Confirmar</BaseButton
          >
          <BaseButton
            v-if="r.estado === 'CONFIRMADA'"
            :cargando="enCurso(`realizar-${r.id}`)"
            @click="pedirConfirmacion('realizar', r)"
            >Marcar realizada</BaseButton
          >
          <button
            v-if="['PENDIENTE', 'COTIZADA', 'CONFIRMADA'].includes(r.estado)"
            class="text-error font-bold px-2 hover:underline"
            @click="abrirCancelacion(r)"
          >
            Cancelar
          </button>
        </div>

        <div
          v-if="['CONFIRMADA', 'REALIZADA', 'COTIZADA'].includes(r.estado)"
          class="border-t border-outline-variant/20 pt-3 mt-3"
        >
          <button
            class="flex items-center gap-1 text-xs font-bold text-secondary hover:underline"
            @click="invitadosAbiertos[r.id] = !invitadosAbiertos[r.id]"
          >
            <span class="material-symbols-outlined text-[16px]">group</span>
            Invitados {{ invitadosAbiertos[r.id] ? '▲' : '▼' }}
          </button>
          <div v-if="invitadosAbiertos[r.id]" class="mt-2 space-y-2">
            <div class="flex gap-2">
              <input
                v-model="nuevoInvitado.nombre"
                placeholder="Nombre"
                class="input-festivo !flex-1 text-xs"
                maxlength="120"
              />
              <input
                v-model="nuevoInvitado.email"
                placeholder="Email"
                type="email"
                class="input-festivo !flex-1 text-xs"
                maxlength="160"
              />
              <BaseButton variante="secundario" class="text-xs !py-1" @click="agregarInvitado(r)">+ Agregar</BaseButton>
            </div>
            <div v-if="r._invitados?.length" class="space-y-1">
              <p
                v-for="inv in r._invitados"
                :key="inv.id"
                class="text-xs flex items-center justify-between bg-surface-container rounded-lg px-2 py-1"
              >
                <span class="font-medium text-on-surface">{{ inv.nombre }}</span>
                <span class="flex items-center gap-2">
                  <span
                    :class="{
                      'text-green-700': inv.estado === 'CONFIRMADO',
                      'text-red-500': inv.estado === 'RECHAZADO',
                      'text-outline': inv.estado === 'PENDIENTE',
                    }"
                    class="font-bold"
                  >
                    {{ inv.estado === 'CONFIRMADO' ? '✓' : inv.estado === 'RECHAZADO' ? '✗' : '⋯' }}
                  </span>
                  <button class="text-error text-[11px] hover:underline" @click="eliminarInvitado(r, inv.id)">
                    Quitar
                  </button>
                </span>
              </p>
            </div>
            <p class="text-[11px] font-medium text-outline">Link: {{ origin }}/invitacion/...</p>
          </div>
        </div>
      </li>
    </ul>

    <EmptyState
      v-if="!cargando && !reservas.length"
      :mensaje="busqueda ? 'Ningún resultado para tu búsqueda.' : 'No hay solicitudes.'"
      icono="inbox"
    />

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

    <BaseModal
      :visible="!!confirmacion"
      :titulo="confirmTexto.titulo"
      @cerrar="confirmacion = null"
      @cancelar="confirmacion = null"
      @confirmar="ejecutarConfirmacion"
    >
      <p class="text-sm font-medium text-on-surface-variant">{{ confirmTexto.cuerpo }}</p>
      <template #acciones>
        <button
          class="px-4 py-2 rounded-full font-bold text-sm border-2 border-outline-variant text-on-surface-variant hover:bg-surface-container transition-colors"
          @click="confirmacion = null"
        >
          Cancelar
        </button>
        <button
          class="px-4 py-2 rounded-full font-bold text-sm bg-primary-container text-on-primary-container hover:bg-primary-fixed shadow-md transition-all active:scale-95"
          @click="ejecutarConfirmacion"
        >
          {{ confirmTexto.boton }}
        </button>
      </template>
    </BaseModal>

    <BaseToast :mensaje="toast.mensaje" :tipo="toast.tipo" @cerrar="toast.mensaje = ''" />
  </section>
</template>
