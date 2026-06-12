<script setup>
import { ref, onMounted } from 'vue'
import api from '../api/client'

const servicios = ref([])
const editando = ref(null) // null | {} (nuevo) | servicio
const error = ref('')

const vacio = () => ({ nombre: '', descripcion: '', precioClp: null, duracionMin: null, capacidad: null, activo: true })

async function cargar() {
  const { data } = await api.get('/servicios')
  servicios.value = data
}

async function guardar() {
  error.value = ''
  try {
    if (editando.value.id) {
      await api.put(`/servicios/${editando.value.id}`, editando.value)
    } else {
      await api.post('/servicios', editando.value)
    }
    editando.value = null
    await cargar()
  } catch (e) {
    error.value = e.response?.data?.message || 'Error al guardar'
  }
}

async function desactivar(servicio) {
  if (!confirm(`¿Desactivar "${servicio.nombre}"?`)) return
  await api.delete(`/servicios/${servicio.id}`)
  await cargar()
}

const clp = (n) => n?.toLocaleString('es-CL', { style: 'currency', currency: 'CLP' })

onMounted(cargar)
</script>

<template>
  <section class="space-y-4">
    <div class="flex items-center justify-between">
      <h2 class="text-xl font-bold">Mis servicios</h2>
      <button @click="editando = vacio()"
              class="bg-violet-600 text-white rounded-lg px-4 py-2 text-sm font-semibold">+ Nuevo</button>
    </div>

    <form v-if="editando" @submit.prevent="guardar" class="bg-white rounded-xl shadow p-4 grid gap-3 sm:grid-cols-2">
      <input v-model="editando.nombre" required maxlength="120" placeholder="Nombre del servicio/paquete"
             class="border rounded-lg px-3 py-2 sm:col-span-2" />
      <textarea v-model="editando.descripcion" maxlength="2000" placeholder="Descripción"
                class="border rounded-lg px-3 py-2 sm:col-span-2" rows="2"></textarea>
      <input v-model.number="editando.precioClp" required type="number" min="0" placeholder="Precio (CLP)"
             class="border rounded-lg px-3 py-2" />
      <input v-model.number="editando.duracionMin" type="number" min="1" placeholder="Duración (min)"
             class="border rounded-lg px-3 py-2" />
      <input v-model.number="editando.capacidad" type="number" min="1" placeholder="Capacidad (niños)"
             class="border rounded-lg px-3 py-2" />
      <label class="flex items-center gap-2 text-sm">
        <input v-model="editando.activo" type="checkbox" /> Visible en la página pública
      </label>
      <p v-if="error" class="text-sm text-red-600 sm:col-span-2">{{ error }}</p>
      <div class="flex gap-2 sm:col-span-2">
        <button class="bg-violet-600 text-white rounded-lg px-4 py-2 text-sm">Guardar</button>
        <button type="button" @click="editando = null" class="border rounded-lg px-4 py-2 text-sm">Cancelar</button>
      </div>
    </form>

    <ul class="grid gap-3 sm:grid-cols-2">
      <li v-for="s in servicios" :key="s.id"
          class="bg-white rounded-xl shadow p-4 flex flex-col gap-1"
          :class="{ 'opacity-50': !s.activo }">
        <div class="flex justify-between items-start">
          <h3 class="font-semibold">{{ s.nombre }}</h3>
          <span class="text-violet-700 font-bold">{{ clp(s.precioClp) }}</span>
        </div>
        <p class="text-sm text-gray-500">{{ s.descripcion }}</p>
        <p class="text-xs text-gray-400">
          <span v-if="s.duracionMin">{{ s.duracionMin }} min · </span>
          <span v-if="s.capacidad">hasta {{ s.capacidad }} niños · </span>
          {{ s.activo ? 'Activo' : 'Inactivo' }}
        </p>
        <div class="flex gap-2 mt-2">
          <button @click="editando = { ...s }" class="text-sm text-violet-600 hover:underline">Editar</button>
          <button v-if="s.activo" @click="desactivar(s)" class="text-sm text-red-500 hover:underline">Desactivar</button>
        </div>
      </li>
    </ul>
    <p v-if="!servicios.length && !editando" class="text-gray-500 text-sm">
      Aún no tienes servicios. Crea el primero para que aparezca en tu página pública.
    </p>
  </section>
</template>
