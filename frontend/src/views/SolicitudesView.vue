<script setup>
import { ref, onMounted } from 'vue'
import api from '../api/client'

const reservas = ref([])
const filtro = ref('')
const error = ref('')
const cotizando = ref(null) // reserva en cotización
const cotizacion = ref({ totalClp: null, seniaClp: null })
const pagando = ref(null)
const pago = ref({ montoClp: null, medio: 'TRANSFERENCIA', comprobanteUrl: '' })

const ESTADOS = ['', 'PENDIENTE', 'COTIZADA', 'CONFIRMADA', 'REALIZADA', 'CANCELADA']
// Chips de estado con la paleta festiva del diseño
const colorEstado = {
  PENDIENTE: 'bg-tertiary-fixed text-on-tertiary-fixed',
  COTIZADA: 'bg-secondary-fixed text-on-secondary-container',
  CONFIRMADA: 'bg-primary-fixed text-on-primary-container',
  REALIZADA: 'bg-[#dcfce7] text-green-800',
  CANCELADA: 'bg-surface-highest text-on-surface-variant',
}

async function cargar() {
  const { data } = await api.get('/reservas', { params: filtro.value ? { estado: filtro.value } : {} })
  reservas.value = data.content
}

async function accion(fn) {
  error.value = ''
  try {
    await fn()
    await cargar()
  } catch (e) {
    error.value = e.response?.data?.message || 'Error en la operación'
  }
}

const cotizar = (r) =>
  accion(async () => {
    await api.put(`/reservas/${r.id}/cotizar`, cotizacion.value)
    cotizando.value = null
  })
const confirmar = (r) => accion(() => api.put(`/reservas/${r.id}/confirmar`))
// Falla #2 (revisión 2 años): cierre manual; el job diario cubre las olvidadas
const realizar = (r) => accion(() => api.put(`/reservas/${r.id}/realizar`))
const cancelar = (r) => {
  const motivo = prompt('Motivo de cancelación (opcional):')
  if (motivo === null) return
  accion(() => api.put(`/reservas/${r.id}/cancelar`, { motivo }))
}
const registrarPago = (r) =>
  accion(async () => {
    await api.post(`/reservas/${r.id}/pagos`, pago.value)
    pagando.value = null
    pago.value = { montoClp: null, medio: 'TRANSFERENCIA', comprobanteUrl: '' }
  })

const clp = (n) => n?.toLocaleString('es-CL', { style: 'currency', currency: 'CLP' })

onMounted(cargar)
</script>

<template>
  <section class="space-y-5">
    <div class="flex items-center justify-between gap-2">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Solicitudes</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Gestiona las reservas de tus clientes.</p>
      </div>
      <select v-model="filtro" @change="cargar"
              class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer">
        <option v-for="e in ESTADOS" :key="e" :value="e">{{ e || 'Todas' }}</option>
      </select>
    </div>
    <p v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2">{{ error }}</p>

    <ul class="space-y-4">
      <li v-for="r in reservas" :key="r.id"
          class="bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-5 space-y-2 hover:shadow-lifted transition-shadow">
        <div class="flex items-center justify-between">
          <p class="font-display font-bold text-lg text-on-surface">Solicitud #{{ r.id }}</p>
          <span class="text-xs font-bold rounded-full px-3 py-1" :class="colorEstado[r.estado]">{{ r.estado }}</span>
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

        <!-- formulario de cotización -->
        <form v-if="cotizando === r.id" @submit.prevent="cotizar(r)" class="flex flex-wrap gap-2 items-end">
          <input v-model.number="cotizacion.totalClp" type="number" min="0" required placeholder="Total CLP"
                 class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium w-32 focus:outline-none focus:border-secondary" />
          <input v-model.number="cotizacion.seniaClp" type="number" min="0" required placeholder="Seña CLP"
                 class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 text-sm font-medium w-32 focus:outline-none focus:border-secondary" />
          <button class="bg-secondary-container text-on-secondary-container rounded-full px-4 py-2 text-sm font-bold shadow-md hover:shadow-lifted active:scale-95 transition-all">Enviar cotización</button>
        </form>

        <!-- formulario de pago -->
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
          <button class="bg-[#dcfce7] text-green-800 rounded-full px-4 py-2 text-sm font-bold shadow-md hover:shadow-lifted active:scale-95 transition-all">Registrar seña</button>
        </form>

        <div class="flex flex-wrap gap-2 pt-1 text-sm">
          <a v-if="r.linkWhatsApp" :href="r.linkWhatsApp" target="_blank"
             class="bg-[#dcfce7] text-green-800 font-bold rounded-full px-4 py-1.5 hover:shadow-md transition-all">WhatsApp 💬</a>
          <a v-if="r.estado === 'COTIZADA' && r.mpInitPoint" :href="r.mpInitPoint" target="_blank"
             class="bg-secondary-fixed text-on-secondary-container font-bold rounded-full px-4 py-1.5 hover:shadow-md transition-all">Copiar Link de Pago 🔗</a>
          <button v-if="r.estado === 'PENDIENTE'" @click="cotizando = cotizando === r.id ? null : r.id"
                  class="border-2 border-outline-variant font-bold text-on-surface-variant rounded-full px-4 py-1.5 hover:bg-surface-low transition-colors">Cotizar</button>
          <button v-if="['COTIZADA', 'CONFIRMADA'].includes(r.estado)"
                  @click="pagando = pagando === r.id ? null : r.id"
                  class="border-2 border-outline-variant font-bold text-on-surface-variant rounded-full px-4 py-1.5 hover:bg-surface-low transition-colors">Registrar pago</button>
          <button v-if="r.estado === 'COTIZADA'" @click="confirmar(r)"
                  class="bg-primary-container text-on-primary-container font-bold rounded-full px-4 py-1.5 shadow-md hover:bg-primary-fixed hover:shadow-lifted active:scale-95 transition-all">Confirmar</button>
          <button v-if="r.estado === 'CONFIRMADA'" @click="realizar(r)"
                  class="bg-[#dcfce7] text-green-800 font-bold rounded-full px-4 py-1.5 shadow-md hover:shadow-lifted active:scale-95 transition-all">Marcar realizada 🎉</button>
          <button v-if="['PENDIENTE', 'COTIZADA', 'CONFIRMADA'].includes(r.estado)" @click="cancelar(r)"
                  class="text-error font-bold px-2 hover:underline">Cancelar</button>
        </div>
      </li>
    </ul>
    <p v-if="!reservas.length" class="font-medium text-on-surface-variant text-sm bg-surface-container rounded-3xl px-5 py-8 text-center">
      🎈 No hay solicitudes{{ filtro ? ` en estado ${filtro}` : '' }}.
    </p>
  </section>
</template>
