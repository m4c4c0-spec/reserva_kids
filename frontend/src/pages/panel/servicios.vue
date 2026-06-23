<script setup>
definePageMeta({ layout: 'panel', requiereAuth: true })

import { ref, onMounted } from 'vue'
import * as servicioService from '../../services/servicioService'
import { clp } from '../../composables/useCurrency'
import { useAsync } from '../../composables/useAsync'
import BaseButton from '../../components/BaseButton.vue'
import BaseModal from '../../components/BaseModal.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const servicios = ref([])
const editando = ref(null)
const desactivandoId = ref(null)

const vacio = () => ({
  nombre: '',
  descripcion: '',
  precioClp: null,
  duracionMin: null,
  capacidad: null,
  activo: true,
  esAdicional: false,
  stock: null,
})

const {
  cargando,
  error: errorCarga,
  ejecutar: cargar,
} = useAsync(async () => {
  servicios.value = await servicioService.listar()
})

const { error: errorGuardar, ejecutar: ejecutarGuardar } = useAsync(async () => {
  if (editando.value.id) {
    await servicioService.actualizar(editando.value.id, editando.value)
  } else {
    await servicioService.crear(editando.value)
  }
  editando.value = null
  await cargar()
})

async function desactivar() {
  await servicioService.desactivar(desactivandoId.value)
  desactivandoId.value = null
  await cargar()
}

onMounted(cargar)
</script>

<template>
  <section class="space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Mis servicios</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Los paquetes que ofreces en tu página pública.</p>
      </div>
      <BaseButton variante="primario" @click="editando = vacio()">
        <span class="material-symbols-outlined text-[20px]">add</span> Nuevo
      </BaseButton>
    </div>

    <form v-if="editando" class="card-festiva grid gap-3 sm:grid-cols-2" @submit.prevent="ejecutarGuardar">
      <input
        v-model="editando.nombre"
        required
        maxlength="120"
        placeholder="Nombre del servicio/paquete"
        aria-label="Nombre del servicio"
        class="input-festivo sm:col-span-2"
      />
      <textarea
        v-model="editando.descripcion"
        maxlength="2000"
        placeholder="Descripción"
        aria-label="Descripción"
        class="input-festivo sm:col-span-2"
        rows="2"
      ></textarea>
      <input
        v-model.number="editando.precioClp"
        required
        type="number"
        min="0"
        placeholder="Precio (CLP)"
        aria-label="Precio en pesos chilenos"
        class="input-festivo"
      />
      <input
        v-model.number="editando.duracionMin"
        type="number"
        min="1"
        placeholder="Duración (min)"
        aria-label="Duración en minutos"
        class="input-festivo"
      />
      <input
        v-model.number="editando.capacidad"
        type="number"
        min="1"
        placeholder="Capacidad (niños)"
        aria-label="Capacidad máxima de niños"
        class="input-festivo"
      />
      <label class="flex items-center gap-2 text-sm font-bold text-on-surface-variant">
        <input v-model="editando.activo" type="checkbox" class="accent-[#b5007d] w-4 h-4" /> Visible en la página
        pública
      </label>
      <label class="flex items-center gap-2 text-sm font-bold text-on-surface-variant">
        <input v-model="editando.esAdicional" type="checkbox" class="accent-[#b5007d] w-4 h-4" /> Servicio adicional
        (opcional)
      </label>
      <div v-if="editando.esAdicional">
        <label class="block text-sm font-bold text-on-surface-variant mb-1">Stock disponible</label>
        <input
          v-model.number="editando.stock"
          type="number"
          min="0"
          placeholder="Vacío = stock infinito"
          class="input-festivo !w-32"
        />
        <p class="text-xs font-medium text-on-surface-variant mt-1">Se oculta de la página pública al llegar a 0.</p>
      </div>
      <ErrorBanner :mensaje="errorGuardar" class="sm:col-span-2" />
      <div class="flex gap-2 sm:col-span-2">
        <BaseButton variante="primario" type="submit">Guardar</BaseButton>
        <BaseButton variante="secundario" type="button" @click="editando = null">Cancelar</BaseButton>
      </div>
    </form>

    <LoadingSpinner v-if="cargando" />

    <ul v-else class="grid gap-4 sm:grid-cols-2">
      <li
        v-for="s in servicios"
        :key="s.id"
        class="card-festiva card-festiva--interactiva flex flex-col gap-1"
        :class="{ 'opacity-50': !s.activo }"
      >
        <div class="flex justify-between items-start gap-2">
          <h3 class="font-display font-bold text-on-surface">{{ s.nombre }}</h3>
          <span class="text-primary font-display font-extrabold whitespace-nowrap">{{ clp(s.precioClp) }}</span>
        </div>
        <p class="text-sm font-medium text-on-surface-variant">{{ s.descripcion }}</p>
        <p class="text-xs font-bold text-outline">
          <span v-if="s.duracionMin">{{ s.duracionMin }} min · </span>
          <span v-if="s.capacidad">hasta {{ s.capacidad }} niños · </span>
          {{ s.activo ? 'Activo' : 'Inactivo' }}
          <span v-if="s.esAdicional" class="text-secondary font-bold">· Adicional</span>
          <span v-if="s.esAdicional && s.stock != null" class="text-on-surface-variant font-medium"
            >· Stock: {{ s.stock }}</span
          >
        </p>
        <div class="flex gap-3 mt-2">
          <button class="text-sm font-bold text-secondary hover:underline" @click="editando = { ...s }">Editar</button>
          <button v-if="s.activo" class="text-sm font-bold text-error hover:underline" @click="desactivandoId = s.id">
            Desactivar
          </button>
        </div>
      </li>
    </ul>

    <EmptyState
      v-if="!cargando && !servicios.length && !editando"
      mensaje="Aún no tienes servicios. Crea el primero para que aparezca en tu página pública."
      icono="celebration"
    />

    <ErrorBanner :mensaje="errorCarga" />

    <BaseModal
      :visible="!!desactivandoId"
      titulo="Desactivar servicio"
      @cerrar="desactivandoId = null"
      @cancelar="desactivandoId = null"
      @confirmar="desactivar"
    >
      <p class="text-sm font-medium text-on-surface-variant">
        ¿Estás seguro de desactivar este servicio? No aparecerá en tu página pública.
      </p>
    </BaseModal>
  </section>
</template>
