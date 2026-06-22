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

const {
  cargando,
  error: errorCarga,
  ejecutar: cargar,
} = useAsync(async () => {
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
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Mi disponibilidad</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">
          Marca los días y horas en que puedes hacer fiestas. Tus clientes solo pueden reservar dentro de estos horarios.
        </p>
      </div>
      <input
        v-model="mes"
        type="month"
        aria-label="Mes"
        class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer"
        @change="cargar"
      />
    </div>

    <form class="card-festiva flex flex-wrap gap-3 items-end" @submit.prevent="ejecutarCrear">
      <label class="text-sm font-bold text-on-surface-variant"
        >Fecha
        <input v-model="nuevo.fecha" type="date" required class="input-festivo mt-1" />
      </label>
      <label class="text-sm font-bold text-on-surface-variant"
        >Desde
        <input v-model="nuevo.horaInicio" type="time" required class="input-festivo mt-1" />
      </label>
      <label class="text-sm font-bold text-on-surface-variant"
        >Hasta
        <input v-model="nuevo.horaFin" type="time" required class="input-festivo mt-1" />
      </label>
      <BaseButton variante="primario" type="submit">
        <span class="material-symbols-outlined text-[20px]">add</span> Agregar horario
      </BaseButton>
    </form>

    <ErrorBanner :mensaje="errorCarga || errorCrear" />
    <LoadingSpinner v-if="cargando" />

    <ul v-else class="grid gap-3 sm:grid-cols-3">
      <li v-for="b in bloques" :key="b.id" class="card-festiva !rounded-2xl space-y-1.5">
        <div class="flex items-center justify-between">
          <div>
            <p class="font-display font-bold text-sm text-on-surface">{{ b.fecha }}</p>
            <p class="text-xs font-bold text-outline">{{ b.horaInicio.slice(0, 5) }}–{{ b.horaFin.slice(0, 5) }}</p>
          </div>
          <div class="flex items-center gap-2">
            <StatusBadge :estado="b.estado" />
            <button
              v-if="b.estado === 'DISPONIBLE'"
              aria-label="Eliminar horario"
              class="text-error hover:bg-error-container rounded-full w-7 h-7 flex items-center justify-center transition-colors"
              @click="eliminandoId = b.id"
            >
              <span class="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>
        </div>
        <!-- A1: hacemos VISIBLE la garantía anti-doble-reserva que ya da la BD. -->
        <p
          v-if="b.estado !== 'DISPONIBLE'"
          class="flex items-center gap-1 text-[11px] font-semibold text-green-800"
        >
          <span class="material-symbols-outlined text-[14px]">shield_person</span>
          Reservado — nadie más puede tomar este horario.
        </p>
      </li>
    </ul>

    <EmptyState
      v-if="!cargando && !bloques.length"
      mensaje="Aún no tienes horarios este mes. Agrega los días y horas en que puedes atender para que tus clientes reserven."
      icono="calendar_month"
    />

    <BaseModal
      :visible="!!eliminandoId"
      titulo="Eliminar bloque"
      @cerrar="eliminandoId = null"
      @cancelar="eliminandoId = null"
      @confirmar="confirmarEliminacion"
    >
      <p class="text-sm font-medium text-on-surface-variant">¿Eliminar este bloque de disponibilidad?</p>
    </BaseModal>
  </section>
</template>
