<script setup>
import { ref, onMounted } from 'vue'
import api from '../api/client'

const hoy = new Date()
const mes = ref(`${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`)
const bloques = ref([])
const nuevo = ref({ fecha: '', horaInicio: '', horaFin: '' })
const error = ref('')

async function cargar() {
  const { data } = await api.get('/calendario/bloques', { params: { mes: mes.value } })
  bloques.value = data
}

async function crear() {
  error.value = ''
  try {
    await api.post('/calendario/bloques', nuevo.value)
    nuevo.value = { fecha: '', horaInicio: '', horaFin: '' }
    await cargar()
  } catch (e) {
    error.value = e.response?.data?.message || 'Error al crear el bloque'
  }
}

async function eliminar(bloque) {
  if (!confirm(`¿Eliminar bloque del ${bloque.fecha}?`)) return
  try {
    await api.delete(`/calendario/bloques/${bloque.id}`)
    await cargar()
  } catch (e) {
    error.value = e.response?.data?.message || 'No se pudo eliminar'
  }
}

const colorEstado = {
  DISPONIBLE: 'bg-green-100 text-green-700',
  EN_ESPERA: 'bg-yellow-100 text-yellow-700',
  CONFIRMADO: 'bg-violet-100 text-violet-700',
}

onMounted(cargar)
</script>

<template>
  <section class="space-y-4">
    <div class="flex items-center justify-between gap-2">
      <h2 class="text-xl font-bold">Calendario de disponibilidad</h2>
      <input v-model="mes" type="month" @change="cargar" class="border rounded-lg px-3 py-1 text-sm" />
    </div>

    <form @submit.prevent="crear" class="bg-white rounded-xl shadow p-4 flex flex-wrap gap-3 items-end">
      <label class="text-sm">Fecha
        <input v-model="nuevo.fecha" type="date" required class="block border rounded-lg px-3 py-2" />
      </label>
      <label class="text-sm">Desde
        <input v-model="nuevo.horaInicio" type="time" required class="block border rounded-lg px-3 py-2" />
      </label>
      <label class="text-sm">Hasta
        <input v-model="nuevo.horaFin" type="time" required class="block border rounded-lg px-3 py-2" />
      </label>
      <button class="bg-violet-600 text-white rounded-lg px-4 py-2 text-sm font-semibold">+ Agregar bloque</button>
    </form>
    <p v-if="error" class="text-sm text-red-600">{{ error }}</p>

    <ul class="grid gap-2 sm:grid-cols-3">
      <li v-for="b in bloques" :key="b.id" class="bg-white rounded-xl shadow p-3 flex items-center justify-between">
        <div>
          <p class="font-semibold text-sm">{{ b.fecha }}</p>
          <p class="text-xs text-gray-500">{{ b.horaInicio.slice(0, 5) }}–{{ b.horaFin.slice(0, 5) }}</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-xs rounded-full px-2 py-1" :class="colorEstado[b.estado]">{{ b.estado }}</span>
          <button v-if="b.estado === 'DISPONIBLE'" @click="eliminar(b)" class="text-red-400 text-sm">✕</button>
        </div>
      </li>
    </ul>
    <p v-if="!bloques.length" class="text-gray-500 text-sm">
      Sin bloques este mes. Agrega bloques para que tus clientes puedan reservar.
    </p>
  </section>
</template>
