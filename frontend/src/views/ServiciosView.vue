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
  <section class="space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Mis servicios</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">Los paquetes que ofreces en tu página pública.</p>
      </div>
      <button @click="editando = vacio()"
              class="bg-primary text-on-primary rounded-full px-5 py-2.5 text-sm font-bold shadow-soft hover:shadow-lifted hover:-translate-y-0.5 active:translate-y-0 transition-all flex items-center gap-1">
        <span class="material-symbols-outlined text-[20px]">add</span> Nuevo
      </button>
    </div>

    <form v-if="editando" @submit.prevent="guardar"
          class="bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-5 grid gap-3 sm:grid-cols-2">
      <input v-model="editando.nombre" required maxlength="120" placeholder="Nombre del servicio/paquete"
             class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium sm:col-span-2 focus:outline-none focus:border-secondary transition-colors" />
      <textarea v-model="editando.descripcion" maxlength="2000" placeholder="Descripción"
                class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium sm:col-span-2 focus:outline-none focus:border-secondary transition-colors" rows="2"></textarea>
      <input v-model.number="editando.precioClp" required type="number" min="0" placeholder="Precio (CLP)"
             class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium focus:outline-none focus:border-secondary transition-colors" />
      <input v-model.number="editando.duracionMin" type="number" min="1" placeholder="Duración (min)"
             class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium focus:outline-none focus:border-secondary transition-colors" />
      <input v-model.number="editando.capacidad" type="number" min="1" placeholder="Capacidad (niños)"
             class="border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium focus:outline-none focus:border-secondary transition-colors" />
      <label class="flex items-center gap-2 text-sm font-bold text-on-surface-variant">
        <input v-model="editando.activo" type="checkbox" class="accent-[#b5007d] w-4 h-4" /> Visible en la página pública
      </label>
      <p v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2 sm:col-span-2">{{ error }}</p>
      <div class="flex gap-2 sm:col-span-2">
        <button class="bg-primary-container text-on-primary-container rounded-full px-5 py-2 text-sm font-bold shadow-md hover:bg-primary-fixed hover:shadow-lifted active:scale-95 transition-all">Guardar</button>
        <button type="button" @click="editando = null"
                class="border-2 border-outline-variant text-on-surface-variant rounded-full px-5 py-2 text-sm font-bold hover:bg-surface-low transition-colors">Cancelar</button>
      </div>
    </form>

    <ul class="grid gap-4 sm:grid-cols-2">
      <li v-for="s in servicios" :key="s.id"
          class="bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-5 flex flex-col gap-1 hover:shadow-lifted transition-shadow"
          :class="{ 'opacity-50': !s.activo }">
        <div class="flex justify-between items-start gap-2">
          <h3 class="font-display font-bold text-on-surface">{{ s.nombre }}</h3>
          <span class="text-primary font-display font-extrabold whitespace-nowrap">{{ clp(s.precioClp) }}</span>
        </div>
        <p class="text-sm font-medium text-on-surface-variant">{{ s.descripcion }}</p>
        <p class="text-xs font-bold text-outline">
          <span v-if="s.duracionMin">{{ s.duracionMin }} min · </span>
          <span v-if="s.capacidad">hasta {{ s.capacidad }} niños · </span>
          {{ s.activo ? 'Activo' : 'Inactivo' }}
        </p>
        <div class="flex gap-3 mt-2">
          <button @click="editando = { ...s }" class="text-sm font-bold text-secondary hover:underline">Editar</button>
          <button v-if="s.activo" @click="desactivar(s)" class="text-sm font-bold text-error hover:underline">Desactivar</button>
        </div>
      </li>
    </ul>
    <p v-if="!servicios.length && !editando"
       class="font-medium text-on-surface-variant text-sm bg-surface-container rounded-3xl px-5 py-8 text-center">
      🎂 Aún no tienes servicios. Crea el primero para que aparezca en tu página pública.
    </p>
  </section>
</template>
