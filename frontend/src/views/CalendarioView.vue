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

// Chips de estado con la paleta festiva del diseño
const colorEstado = {
  DISPONIBLE: 'bg-[#dcfce7] text-green-800',
  EN_ESPERA: 'bg-tertiary-fixed text-on-tertiary-fixed',
  CONFIRMADO: 'bg-primary-fixed text-on-primary-container',
}

onMounted(cargar)
</script>

<template>
  <section class="space-y-5">
    <div class="flex items-center justify-between gap-2">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Calendario</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Tu disponibilidad para fiestas.</p>
      </div>
      <input v-model="mes" type="month" @change="cargar"
             class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer" />
    </div>

    <form @submit.prevent="crear"
          class="bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-5 flex flex-wrap gap-3 items-end">
      <label class="text-sm font-bold text-on-surface-variant">Fecha
        <input v-model="nuevo.fecha" type="date" required
               class="block border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 font-medium text-on-surface mt-1 focus:outline-none focus:border-secondary transition-colors" />
      </label>
      <label class="text-sm font-bold text-on-surface-variant">Desde
        <input v-model="nuevo.horaInicio" type="time" required
               class="block border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 font-medium text-on-surface mt-1 focus:outline-none focus:border-secondary transition-colors" />
      </label>
      <label class="text-sm font-bold text-on-surface-variant">Hasta
        <input v-model="nuevo.horaFin" type="time" required
               class="block border-2 border-surface-highest bg-surface rounded-xl px-3 py-2 font-medium text-on-surface mt-1 focus:outline-none focus:border-secondary transition-colors" />
      </label>
      <button class="bg-primary text-on-primary rounded-full px-5 py-2.5 text-sm font-bold shadow-soft hover:shadow-lifted hover:-translate-y-0.5 active:translate-y-0 transition-all flex items-center gap-1">
        <span class="material-symbols-outlined text-[20px]">add</span> Agregar bloque
      </button>
    </form>
    <p v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2">{{ error }}</p>

    <ul class="grid gap-3 sm:grid-cols-3">
      <li v-for="b in bloques" :key="b.id"
          class="bg-surface-lowest rounded-2xl shadow-soft border border-outline-variant/20 p-4 flex items-center justify-between hover:shadow-lifted transition-shadow">
        <div>
          <p class="font-display font-bold text-sm text-on-surface">{{ b.fecha }}</p>
          <p class="text-xs font-bold text-outline">{{ b.horaInicio.slice(0, 5) }}–{{ b.horaFin.slice(0, 5) }}</p>
        </div>
        <div class="flex items-center gap-2">
          <span class="text-xs font-bold rounded-full px-3 py-1" :class="colorEstado[b.estado]">{{ b.estado }}</span>
          <button v-if="b.estado === 'DISPONIBLE'" @click="eliminar(b)"
                  class="text-error hover:bg-error-container rounded-full w-7 h-7 flex items-center justify-center transition-colors">
            <span class="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>
      </li>
    </ul>
    <p v-if="!bloques.length" class="font-medium text-on-surface-variant text-sm bg-surface-container rounded-3xl px-5 py-8 text-center">
      🗓️ Sin bloques este mes. Agrega bloques para que tus clientes puedan reservar.
    </p>
  </section>
</template>
