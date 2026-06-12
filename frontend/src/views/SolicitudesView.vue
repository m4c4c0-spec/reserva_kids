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
const colorEstado = {
  PENDIENTE: 'bg-yellow-100 text-yellow-700',
  COTIZADA: 'bg-blue-100 text-blue-700',
  CONFIRMADA: 'bg-violet-100 text-violet-700',
  REALIZADA: 'bg-green-100 text-green-700',
  CANCELADA: 'bg-gray-100 text-gray-500',
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
  <section class="space-y-4">
    <div class="flex items-center justify-between gap-2">
      <h2 class="text-xl font-bold">Solicitudes</h2>
      <select v-model="filtro" @change="cargar" class="border rounded-lg px-3 py-1 text-sm">
        <option v-for="e in ESTADOS" :key="e" :value="e">{{ e || 'Todas' }}</option>
      </select>
    </div>
    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <ul class="space-y-3">
      <li v-for="r in reservas" :key="r.id" class="bg-white rounded-xl shadow p-4 space-y-2">
        <div class="flex items-center justify-between">
          <p class="font-semibold">Solicitud #{{ r.id }}</p>
          <span class="text-xs rounded-full px-2 py-1" :class="colorEstado[r.estado]">{{ r.estado }}</span>
        </div>
        <p class="text-sm text-gray-600">
          <span v-if="r.numNinos">{{ r.numNinos }} niños · </span>
          <span v-if="r.comuna">{{ r.comuna }} · </span>
          {{ new Date(r.creadaEn).toLocaleString('es-CL') }}
        </p>
        <p v-if="r.comentarios" class="text-sm text-gray-500 whitespace-pre-line">{{ r.comentarios }}</p>
        <p v-if="r.totalClp != null" class="text-sm">
          Total {{ clp(r.totalClp) }} · Seña {{ clp(r.seniaClp) }} ·
          Pagado {{ clp(r.pagadoClp) }} · <strong>Saldo {{ clp(r.saldoClp) }}</strong>
        </p>

        <!-- formulario de cotización -->
        <form v-if="cotizando === r.id" @submit.prevent="cotizar(r)" class="flex flex-wrap gap-2 items-end">
          <input v-model.number="cotizacion.totalClp" type="number" min="0" required placeholder="Total CLP"
                 class="border rounded-lg px-3 py-2 text-sm w-32" />
          <input v-model.number="cotizacion.seniaClp" type="number" min="0" required placeholder="Seña CLP"
                 class="border rounded-lg px-3 py-2 text-sm w-32" />
          <button class="bg-blue-600 text-white rounded-lg px-3 py-2 text-sm">Enviar cotización</button>
        </form>

        <!-- formulario de pago -->
        <form v-if="pagando === r.id" @submit.prevent="registrarPago(r)" class="flex flex-wrap gap-2 items-end">
          <input v-model.number="pago.montoClp" type="number" min="1" required placeholder="Monto CLP"
                 class="border rounded-lg px-3 py-2 text-sm w-32" />
          <select v-model="pago.medio" class="border rounded-lg px-3 py-2 text-sm">
            <option>TRANSFERENCIA</option>
            <option>EFECTIVO</option>
            <option>OTRO</option>
          </select>
          <input v-model="pago.comprobanteUrl" placeholder="URL comprobante (opcional)"
                 class="border rounded-lg px-3 py-2 text-sm flex-1 min-w-40" />
          <button class="bg-green-600 text-white rounded-lg px-3 py-2 text-sm">Registrar seña</button>
        </form>

        <div class="flex flex-wrap gap-2 pt-1 text-sm">
          <a v-if="r.linkWhatsApp" :href="r.linkWhatsApp" target="_blank"
             class="bg-green-100 text-green-700 rounded-lg px-3 py-1">WhatsApp 💬</a>
          <button v-if="r.estado === 'PENDIENTE'" @click="cotizando = cotizando === r.id ? null : r.id"
                  class="border rounded-lg px-3 py-1">Cotizar</button>
          <button v-if="['COTIZADA', 'CONFIRMADA'].includes(r.estado)"
                  @click="pagando = pagando === r.id ? null : r.id"
                  class="border rounded-lg px-3 py-1">Registrar pago</button>
          <button v-if="r.estado === 'COTIZADA'" @click="confirmar(r)"
                  class="bg-violet-600 text-white rounded-lg px-3 py-1">Confirmar</button>
          <button v-if="r.estado === 'CONFIRMADA'" @click="realizar(r)"
                  class="bg-green-600 text-white rounded-lg px-3 py-1">Marcar realizada 🎉</button>
          <button v-if="['PENDIENTE', 'COTIZADA', 'CONFIRMADA'].includes(r.estado)" @click="cancelar(r)"
                  class="text-red-500 px-2">Cancelar</button>
        </div>
      </li>
    </ul>
    <p v-if="!reservas.length" class="text-gray-500 text-sm">No hay solicitudes{{ filtro ? ` en estado ${filtro}` : '' }}.</p>
  </section>
</template>
