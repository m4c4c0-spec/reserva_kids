<script setup>
definePageMeta({ layout: 'admin', requiereAdmin: true })

import { ref, onMounted } from 'vue'
import { adminService } from '../../services/adminService'
import StatusBadge from '../../components/StatusBadge.vue'
import EmptyState from '../../components/EmptyState.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import BaseModal from '../../components/BaseModal.vue'
import BaseToast from '../../components/BaseToast.vue'

const FILTROS = [
  { valor: '', label: 'Todos' },
  { valor: 'ACTIVO', label: 'Activos' },
  { valor: 'SUSPENDIDO', label: 'Suspendidos' },
  { valor: 'CERRADO', label: 'Cerrados' },
]

const negocios = ref([])
const cargando = ref(false)
const error = ref('')
const filtro = ref('')
const busqueda = ref('')

const toastMensaje = ref('')
const toastTipo = ref('exito')
const accionando = ref(null)

const modalSuspender = ref(null)
const modalCrear = ref(false)
const creando = ref(false)

const nuevoNegocio = ref({ nombreNegocio: '', slug: '', email: '', password: '' })

async function crearNegocio() {
  creando.value = true
  try {
    const creado = await adminService.crearNegocio({
      nombreNegocio: nuevoNegocio.value.nombreNegocio.trim(),
      slug: nuevoNegocio.value.slug.trim().toLowerCase(),
      email: nuevoNegocio.value.email.trim(),
      password: nuevoNegocio.value.password,
    })
    modalCrear.value = false
    nuevoNegocio.value = { nombreNegocio: '', slug: '', email: '', password: '' }
    toastMensaje.value = `Negocio "${creado.nombre}" creado. El dueño puede iniciar sesión en /negocios_duenos.`
    toastTipo.value = 'exito'
    await cargar()
  } catch (e) {
    toastMensaje.value = e.response?.data?.message || 'No se pudo crear el negocio'
    toastTipo.value = 'error'
  } finally {
    creando.value = false
  }
}

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    negocios.value = await adminService.listarNegocios({ estado: filtro.value, q: busqueda.value })
  } catch (e) {
    error.value = e.response?.data?.message || 'No se pudieron cargar los negocios'
  } finally {
    cargando.value = false
  }
}

function setFiltro(valor) {
  filtro.value = valor
  cargar()
}

let debounce
function buscarDebounced() {
  clearTimeout(debounce)
  debounce = setTimeout(cargar, 300)
}

function reemplazar(actualizado) {
  const i = negocios.value.findIndex((n) => n.id === actualizado.id)
  if (i !== -1) {
    if (filtro.value && actualizado.estado !== filtro.value) negocios.value.splice(i, 1)
    else negocios.value[i] = actualizado
  }
}

async function confirmarSuspender() {
  const negocio = modalSuspender.value
  modalSuspender.value = null
  accionando.value = negocio.id
  try {
    reemplazar(await adminService.suspender(negocio.id))
    toastTipo.value = 'exito'
    toastMensaje.value = `"${negocio.nombre}" suspendido. Su página pública quedó oculta.`
  } catch (e) {
    toastTipo.value = 'error'
    toastMensaje.value = e.response?.data?.message || 'No se pudo suspender'
  } finally {
    accionando.value = null
  }
}

async function reactivar(negocio) {
  accionando.value = negocio.id
  try {
    reemplazar(await adminService.reactivar(negocio.id))
    toastTipo.value = 'exito'
    toastMensaje.value = `"${negocio.nombre}" reactivado.`
  } catch (e) {
    toastTipo.value = 'error'
    toastMensaje.value = e.response?.data?.message || 'No se pudo reactivar'
  } finally {
    accionando.value = null
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="mb-6 flex items-center justify-between gap-4">
      <div>
        <h2 class="font-display font-extrabold text-2xl text-on-surface">Negocios</h2>
        <p class="text-sm font-medium text-on-surface-variant">
          Gobierno de la plataforma: estado y acceso de cada negocio.
        </p>
      </div>
      <button
        class="shrink-0 inline-flex items-center gap-2 px-4 py-2.5 rounded-full bg-primary text-on-primary font-bold text-sm shadow-soft hover:shadow-lifted hover:-translate-y-0.5 transition-all"
        @click="modalCrear = true"
      >
        <span class="material-symbols-outlined text-[20px]">add_circle</span>
        Crear negocio
      </button>
    </div>

    <!-- Filtros + búsqueda -->
    <div class="flex flex-col sm:flex-row sm:items-center gap-3 mb-5">
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="f in FILTROS"
          :key="f.valor"
          class="px-3.5 py-1.5 rounded-full text-sm font-bold transition-all"
          :class="
            filtro === f.valor
              ? 'bg-primary text-on-primary shadow-soft'
              : 'bg-surface-highest text-on-surface-variant hover:bg-surface-container'
          "
          @click="setFiltro(f.valor)"
        >
          {{ f.label }}
        </button>
      </div>
      <div class="relative sm:ml-auto sm:w-64">
        <span
          class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none text-[20px]"
          >search</span
        >
        <input
          v-model="busqueda"
          type="search"
          placeholder="Buscar por nombre o slug"
          aria-label="Buscar negocio por nombre o slug"
          class="input-festivo input-festivo--con-icono !py-2.5 w-full"
          @input="buscarDebounced"
        />
      </div>
    </div>

    <ErrorBanner :mensaje="error" />

    <LoadingSpinner v-if="cargando" mensaje="Cargando negocios…" />

    <EmptyState
      v-else-if="!negocios.length"
      icono="storefront"
      mensaje="No hay negocios que coincidan con el filtro."
    />

    <!-- Tabla (desktop) -->
    <div v-else class="hidden md:block glass-card rounded-2xl overflow-hidden shadow-soft">
      <table class="w-full text-sm">
        <thead class="bg-surface-highest/60 text-on-surface-variant">
          <tr>
            <th class="text-left font-bold px-4 py-3">Negocio</th>
            <th class="text-left font-bold px-4 py-3">Estado</th>
            <th class="text-right font-bold px-4 py-3">Reservas</th>
            <th class="text-right font-bold px-4 py-3">Acción</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="n in negocios" :key="n.id" class="border-t border-outline-variant/20">
            <td class="px-4 py-3">
              <div class="font-bold text-on-surface">{{ n.nombre }}</div>
              <a :href="`/${n.slug}`" target="_blank" class="text-xs text-secondary hover:underline">/{{ n.slug }}</a>
            </td>
            <td class="px-4 py-3"><StatusBadge :estado="n.estado" /></td>
            <td class="px-4 py-3 text-right font-semibold text-on-surface-variant">{{ n.reservas }}</td>
            <td class="px-4 py-3 text-right">
              <button
                v-if="n.estado === 'ACTIVO'"
                class="text-sm font-bold text-on-error-container bg-error-container rounded-full px-3 py-1.5 hover:opacity-90 disabled:opacity-50"
                :disabled="accionando === n.id"
                @click="modalSuspender = n"
              >
                Suspender
              </button>
              <button
                v-else-if="n.estado === 'SUSPENDIDO'"
                class="text-sm font-bold text-on-primary-container bg-primary-container rounded-full px-3 py-1.5 hover:opacity-90 disabled:opacity-50"
                :disabled="accionando === n.id"
                @click="reactivar(n)"
              >
                Reactivar
              </button>
              <span v-else class="text-xs font-medium text-outline">—</span>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- Tarjetas (móvil) -->
    <div v-if="!cargando && negocios.length" class="md:hidden space-y-3">
      <div v-for="n in negocios" :key="n.id" class="glass-card rounded-2xl p-4 shadow-soft">
        <div class="flex items-start justify-between gap-2">
          <div class="min-w-0">
            <div class="font-bold text-on-surface truncate">{{ n.nombre }}</div>
            <a :href="`/${n.slug}`" target="_blank" class="text-xs text-secondary hover:underline">/{{ n.slug }}</a>
          </div>
          <StatusBadge :estado="n.estado" />
        </div>
        <div class="flex items-center justify-between mt-3">
          <span class="text-xs font-medium text-on-surface-variant">{{ n.reservas }} reservas</span>
          <button
            v-if="n.estado === 'ACTIVO'"
            class="text-sm font-bold text-on-error-container bg-error-container rounded-full px-3 py-1.5 disabled:opacity-50"
            :disabled="accionando === n.id"
            @click="modalSuspender = n"
          >
            Suspender
          </button>
          <button
            v-else-if="n.estado === 'SUSPENDIDO'"
            class="text-sm font-bold text-on-primary-container bg-primary-container rounded-full px-3 py-1.5 disabled:opacity-50"
            :disabled="accionando === n.id"
            @click="reactivar(n)"
          >
            Reactivar
          </button>
        </div>
      </div>
    </div>

    <!-- Confirmación de suspensión -->
    <BaseModal
      :visible="!!modalSuspender"
      titulo="Suspender negocio"
      @cerrar="modalSuspender = null"
      @cancelar="modalSuspender = null"
      @confirmar="confirmarSuspender"
    >
      <p class="text-sm font-medium text-on-surface-variant">
        Vas a suspender <span class="font-bold text-on-surface">{{ modalSuspender?.nombre }}</span
        >. Su página pública dejará de verse, no podrá iniciar sesión y se cerrarán sus sesiones activas. Es reversible
        (reactivar).
      </p>
    </BaseModal>

    <!-- Crear negocio -->
    <BaseModal
      :visible="modalCrear"
      titulo="Crear nuevo negocio"
      @cerrar="modalCrear = false"
      @cancelar="modalCrear = false"
    >
      <form class="space-y-3" @submit.prevent="crearNegocio">
        <div>
          <label class="block text-xs font-bold text-on-surface-variant mb-1">Nombre del negocio</label>
          <input
            v-model="nuevoNegocio.nombreNegocio"
            required
            maxlength="120"
            placeholder="Fiestas Pepito"
            class="input-festivo w-full"
          />
        </div>
        <div>
          <label class="block text-xs font-bold text-on-surface-variant mb-1">Identificador (slug)</label>
          <input
            v-model="nuevoNegocio.slug"
            required
            pattern="[a-z0-9-]{3,60}"
            placeholder="fiestas-pepito"
            class="input-festivo w-full"
            @input="nuevoNegocio.slug = nuevoNegocio.slug.replace(/[^a-z0-9-]/g, '')"
          />
          <p class="text-xs text-on-surface-variant mt-0.5">
            Solo minúsculas, números y guiones. URL: dulcevida.cl/<strong>{{
              nuevoNegocio.slug || 'tunegocio'
            }}</strong>
          </p>
        </div>
        <div>
          <label class="block text-xs font-bold text-on-surface-variant mb-1">Email del dueño</label>
          <input
            v-model="nuevoNegocio.email"
            type="email"
            required
            maxlength="160"
            placeholder="dueno@correo.com"
            class="input-festivo w-full"
          />
        </div>
        <div>
          <label class="block text-xs font-bold text-on-surface-variant mb-1">Contraseña inicial</label>
          <input
            v-model="nuevoNegocio.password"
            type="password"
            required
            minlength="8"
            maxlength="72"
            placeholder="Mínimo 8 caracteres"
            class="input-festivo w-full"
          />
        </div>
        <p class="text-xs text-on-surface-variant">
          El dueño podrá iniciar sesión con este email y contraseña en <strong>/negocios_duenos</strong>. Puede
          cambiarla después desde el panel.
        </p>
        <div class="flex justify-end gap-2 pt-2">
          <button
            type="button"
            class="px-4 py-2 rounded-full font-bold text-sm text-on-surface-variant hover:text-on-surface transition-colors"
            @click="modalCrear = false"
          >
            Cancelar
          </button>
          <button
            type="submit"
            :disabled="creando"
            class="px-5 py-2 rounded-full bg-primary text-on-primary font-bold text-sm shadow-soft disabled:opacity-50"
          >
            {{ creando ? 'Creando…' : 'Crear negocio' }}
          </button>
        </div>
      </form>
    </BaseModal>

    <BaseToast :mensaje="toastMensaje" :tipo="toastTipo" @cerrar="toastMensaje = ''" />
  </div>
</template>
