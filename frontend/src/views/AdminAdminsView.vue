<script setup>
import { ref, onMounted } from 'vue'
import { adminService } from '../services/adminService'
import BaseButton from '../components/BaseButton.vue'
import BaseModal from '../components/BaseModal.vue'
import BaseToast from '../components/BaseToast.vue'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const admins = ref([])
const cargando = ref(false)
const err = ref('')

const modal = ref(false)
const form = ref({ nombre: '', email: '', password: '' })
const errForm = ref('')
const guardando = ref(false)

const toastMensaje = ref('')
const toastTipo = ref('exito')

function fmt(iso) {
  return iso ? new Date(iso).toLocaleDateString('es-CL', { dateStyle: 'short' }) : '—'
}

async function cargar() {
  cargando.value = true
  err.value = ''
  try {
    admins.value = await adminService.listarAdmins()
  } catch (e) {
    err.value = e.response?.data?.message || 'No se pudieron cargar los administradores'
  } finally {
    cargando.value = false
  }
}

function abrir() {
  form.value = { nombre: '', email: '', password: '' }
  errForm.value = ''
  modal.value = true
}

async function crear() {
  errForm.value = ''
  guardando.value = true
  try {
    await adminService.crearAdmin(form.value)
    modal.value = false
    toastTipo.value = 'exito'
    toastMensaje.value = 'Administrador creado.'
    await cargar()
  } catch (e) {
    errForm.value = e.response?.data?.message || 'No se pudo crear el administrador'
  } finally {
    guardando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="mb-6 flex items-center justify-between gap-3">
      <div>
        <h2 class="font-display font-extrabold text-2xl text-on-surface">Administradores</h2>
        <p class="text-sm font-medium text-on-surface-variant">Quién puede gobernar la plataforma.</p>
      </div>
      <BaseButton variante="primario" class="shrink-0" @click="abrir">
        <span class="material-symbols-outlined text-[18px]">person_add</span>
        Nuevo
      </BaseButton>
    </div>

    <ErrorBanner :mensaje="err" />
    <LoadingSpinner v-if="cargando" mensaje="Cargando administradores…" />
    <EmptyState v-else-if="!admins.length" icono="shield_person" mensaje="No hay administradores." />

    <ul v-else class="space-y-2">
      <li v-for="a in admins" :key="a.id" class="glass-card rounded-2xl p-4 shadow-soft flex items-center gap-3">
        <div class="w-9 h-9 rounded-full bg-primary-container flex items-center justify-center shrink-0">
          <span class="material-symbols-outlined text-on-primary-container text-[20px]">shield_person</span>
        </div>
        <div class="min-w-0 flex-1">
          <p class="font-bold text-on-surface text-sm truncate">{{ a.nombre }}</p>
          <p class="text-xs text-on-surface-variant truncate">{{ a.email }}</p>
        </div>
        <span class="text-xs font-medium text-outline shrink-0">desde {{ fmt(a.creadoEn) }}</span>
      </li>
    </ul>

    <BaseModal
      :visible="modal"
      titulo="Nuevo administrador"
      @cerrar="modal = false"
      @cancelar="modal = false"
      @confirmar="crear"
    >
      <form class="space-y-3" @submit.prevent="crear">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1" for="adm-nombre">Nombre</label>
          <input id="adm-nombre" v-model="form.nombre" required maxlength="120" class="input-festivo" />
        </div>
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1" for="adm-email">Email</label>
          <input id="adm-email" v-model="form.email" type="email" required class="input-festivo" />
        </div>
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1" for="adm-pass">Contraseña</label>
          <input id="adm-pass" v-model="form.password" type="password" required minlength="8" class="input-festivo" />
        </div>
        <ErrorBanner :mensaje="errForm" />
      </form>
    </BaseModal>

    <BaseToast :mensaje="toastMensaje" :tipo="toastTipo" @cerrar="toastMensaje = ''" />
  </div>
</template>
