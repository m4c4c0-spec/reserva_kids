<script setup>
import { ref, onMounted } from 'vue'
import * as calendarioService from '../services/calendarioService'
import { useAsync } from '../composables/useAsync'
import BaseButton from '../components/BaseButton.vue'
import BaseModal from '../components/BaseModal.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import StatusBadge from '../components/StatusBadge.vue'
import EmptyState from '../components/EmptyState.vue'

const hoy = new Date()
const mes = ref(`${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`)
const bloques = ref([])
const nuevo = ref({ fecha: '', horaInicio: '', horaFin: '' })
const eliminandoId = ref(null)

const { cargando, error: errorCarga, ejecutar: cargar } = useAsync(async () => {
  bloques.value = await calendarioService.listarBloques(mes.value)
})

const { error: errorCrear, ejecutar: ejecutarCrear } = useAsync(async () => {
  await calendarioService.crearBloque(nuevo.value)
  nuevo.value = { fecha: '', horaInicio: '', horaFin: '' }
  await cargar()
})

async function confirmarEliminacion() {
  await calendarioService.eliminarBloque(eliminandoId.value)
  eliminandoId.value = null
  await cargar()
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

    <form @submit.prevent="ejecutarCrear"
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
      <BaseButton variante="primario" type="submit">
        <span class="material-symbols-outlined text-[20px]">add</span> Agregar bloque
      </BaseButton>
    </form>

    <ErrorBanner :mensaje="errorCarga || errorCrear" />
    <LoadingSpinner v-if="cargando" />

    <ul v-else class="grid gap-3 sm:grid-cols-3">
      <li v-for="b in bloques" :key="b.id"
          class="bg-surface-lowest rounded-2xl shadow-soft border border-outline-variant/20 p-4 flex items-center justify-between hover:shadow-lifted transition-shadow">
        <div>
          <p class="font-display font-bold text-sm text-on-surface">{{ b.fecha }}</p>
          <p class="text-xs font-bold text-outline">{{ b.horaInicio.slice(0, 5) }}–{{ b.horaFin.slice(0, 5) }}</p>
        </div>
        <div class="flex items-center gap-2">
          <StatusBadge :estado="b.estado" />
          <button v-if="b.estado === 'DISPONIBLE'" @click="eliminandoId = b.id"
                  class="text-error hover:bg-error-container rounded-full w-7 h-7 flex items-center justify-center transition-colors">
            <span class="material-symbols-outlined text-[18px]">close</span>
          </button>
        </div>
      </li>
    </ul>

    <EmptyState v-if="!cargando && !bloques.length"
                mensaje="Sin bloques este mes. Agrega bloques para que tus clientes puedan reservar."
                icono="calendar_month" />

    <BaseModal
      :visible="!!eliminandoId"
      titulo="Eliminar bloque"
      @cerrar="eliminandoId = null"
      @cancelar="eliminandoId = null"
      @confirmar="confirmarEliminacion"
    >
      <p class="text-sm font-medium text-on-surface-variant">
        ¿Eliminar este bloque de disponibilidad?
      </p>
    </BaseModal>
  </section>
</template>
