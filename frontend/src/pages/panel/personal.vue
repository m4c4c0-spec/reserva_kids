<script setup>
definePageMeta({ layout: 'panel', requiereAuth: true })

import { ref, onMounted } from 'vue'
import * as personalService from '../../services/personalService'
import { useAsync } from '../../composables/useAsync'
import BaseButton from '../../components/BaseButton.vue'
import BaseModal from '../../components/BaseModal.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const personal = ref([])
const roles = ref([])
const creando = ref(null)
const eliminandoId = ref(null)
const linkCopiado = ref(false)

async function copiarLinkStaff() {
  try {
    await navigator.clipboard.writeText(`${window.location.origin}/staff/entrar`)
    linkCopiado.value = true
    setTimeout(() => (linkCopiado.value = false), 2000)
  } catch {
    linkCopiado.value = false
  }
}

const vacio = () => ({ nombre: '', email: '', telefono: '', rolPersonalId: null, password: '' })

const {
  cargando,
  error: errorCarga,
  ejecutar: cargar,
} = useAsync(async () => {
  personal.value = await personalService.listar()
})

const { error: errorGuardar, ejecutar: ejecutarGuardar } = useAsync(async () => {
  await personalService.crear(creando.value)
  creando.value = null
  await cargar()
})

async function eliminar() {
  await personalService.eliminar(eliminandoId.value)
  eliminandoId.value = null
  await cargar()
}

onMounted(async () => {
  await cargar()
  try {
    roles.value = await personalService.listarRoles()
  } catch {
    /* roles not available (403 for staff without permission, etc.) */
  }
})
</script>

<template>
  <section class="space-y-5">
    <div class="flex items-center justify-between">
      <div>
        <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Mi personal</h2>
        <p class="font-medium text-on-surface-variant text-sm mt-0.5">
          Define roles con permisos específicos: quién ve solo eventos, quién puede cotizar o ver la caja.
        </p>
      </div>
      <BaseButton variante="primario" @click="creando = vacio()">
        <span class="material-symbols-outlined text-[20px]">person_add</span> Nuevo
      </BaseButton>
    </div>

    <div class="card-festiva flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-secondary-fixed/40">
      <div class="flex items-start gap-3">
        <span class="material-symbols-outlined text-secondary mt-0.5">share</span>
        <div>
          <p class="font-display font-bold text-on-surface">Link de acceso para tu equipo</p>
          <p class="text-sm font-medium text-on-surface-variant">
            Pásales este link a tus animadores y recepción. Entren, inicien sesión y vean su calendario.
          </p>
        </div>
      </div>
      <button
        class="shrink-0 inline-flex items-center gap-2 px-4 py-2.5 rounded-full bg-secondary text-on-secondary font-bold text-sm hover:shadow-md transition-all"
        @click="copiarLinkStaff"
      >
        <span class="material-symbols-outlined text-[18px]">{{ linkCopiado ? 'check' : 'content_copy' }}</span>
        {{ linkCopiado ? '¡Copiado!' : 'Copiar link' }}
      </button>
    </div>

    <form v-if="creando" class="card-festiva grid gap-3 sm:grid-cols-2" @submit.prevent="ejecutarGuardar">
      <input
        v-model="creando.nombre"
        required
        maxlength="120"
        placeholder="Nombre"
        aria-label="Nombre del integrante"
        class="input-festivo"
      />
      <select v-model="creando.rolPersonalId" aria-label="Rol" class="input-festivo">
        <option :value="null" disabled>Selecciona un rol</option>
        <option v-for="r in roles" :key="r.id" :value="r.id">{{ r.nombre }}</option>
      </select>
      <input
        v-model="creando.email"
        required
        type="email"
        maxlength="160"
        placeholder="Email (será su usuario)"
        aria-label="Email"
        class="input-festivo"
      />
      <input
        v-model="creando.telefono"
        maxlength="20"
        placeholder="WhatsApp (opcional)"
        aria-label="Teléfono WhatsApp"
        class="input-festivo"
      />
      <input
        v-model="creando.password"
        required
        type="password"
        minlength="8"
        maxlength="72"
        placeholder="Contraseña inicial (mín. 8)"
        aria-label="Contraseña inicial"
        class="input-festivo sm:col-span-2"
      />
      <ErrorBanner :mensaje="errorGuardar" class="sm:col-span-2" />
      <div class="flex gap-2 sm:col-span-2">
        <BaseButton variante="primario" type="submit" :deshabilitado="!creando.rolPersonalId"> Crear </BaseButton>
        <BaseButton variante="secundario" type="button" @click="creando = null">Cancelar</BaseButton>
      </div>
    </form>

    <LoadingSpinner v-if="cargando" />

    <ul v-else class="grid gap-4 sm:grid-cols-2">
      <li
        v-for="p in personal"
        :key="p.id"
        class="card-festiva flex flex-col gap-1"
        :class="{ 'opacity-50': !p.activo }"
      >
        <div class="flex justify-between items-start gap-2">
          <h3 class="font-display font-bold text-on-surface">{{ p.nombre }}</h3>
          <span class="text-secondary font-bold text-sm whitespace-nowrap">{{ p.rolNombre }}</span>
        </div>
        <p class="text-sm font-medium text-on-surface-variant">{{ p.email }}</p>
        <p class="text-xs font-bold text-outline">
          <span v-if="p.telefono">{{ p.telefono }} · </span>
          {{ p.activo ? 'Activo' : 'Inactivo' }}
        </p>
        <div class="flex gap-3 mt-2">
          <button v-if="p.activo" class="text-sm font-bold text-error hover:underline" @click="eliminandoId = p.id">
            Quitar
          </button>
        </div>
      </li>
    </ul>

    <EmptyState
      v-if="!cargando && !personal.length && !creando"
      mensaje="Aún no tienes personal. Crea roles con permisos granulares: Limpieza (solo ve horas), Animador (detalle de eventos), Gerente (finanzas y cotizaciones)."
      icono="groups"
    />

    <ErrorBanner :mensaje="errorCarga" />

    <BaseModal
      :visible="!!eliminandoId"
      titulo="Quitar del personal"
      @cerrar="eliminandoId = null"
      @cancelar="eliminandoId = null"
      @confirmar="eliminar"
    >
      <p class="text-sm font-medium text-on-surface-variant">
        ¿Quitar a esta persona? Perderá el acceso al calendario, pero no se borra su historial.
      </p>
    </BaseModal>
  </section>
</template>
