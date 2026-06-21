<script setup>
import { ref, onMounted } from 'vue'
import { adminService } from '../services/adminService'
import EmptyState from '../components/EmptyState.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const items = ref([])
const cargando = ref(false)
const err = ref('')

const ETIQUETA = {
  SUSPENDER_NEGOCIO: 'Suspendió un negocio',
  REACTIVAR_NEGOCIO: 'Reactivó un negocio',
  CREAR_ADMIN: 'Dio de alta un admin',
}

function fmt(iso) {
  return new Date(iso).toLocaleString('es-CL', { dateStyle: 'short', timeStyle: 'short' })
}

async function cargar() {
  cargando.value = true
  err.value = ''
  try {
    items.value = await adminService.auditoria(100)
  } catch (e) {
    err.value = e.response?.data?.message || 'No se pudo cargar la bitácora'
  } finally {
    cargando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="mb-6">
      <h2 class="font-display font-extrabold text-2xl text-on-surface">Bitácora</h2>
      <p class="text-sm font-medium text-on-surface-variant">Acciones recientes de los administradores.</p>
    </div>

    <ErrorBanner :mensaje="err" />
    <LoadingSpinner v-if="cargando" mensaje="Cargando bitácora…" />
    <EmptyState v-else-if="!items.length" icono="history" mensaje="Aún no hay acciones registradas." />

    <ul v-else class="space-y-2">
      <li v-for="(it, i) in items" :key="i" class="glass-card rounded-2xl p-4 shadow-soft flex items-start gap-3">
        <span class="material-symbols-outlined text-on-surface-variant mt-0.5">manage_history</span>
        <div class="min-w-0 flex-1">
          <p class="font-bold text-on-surface text-sm">{{ ETIQUETA[it.accion] || it.accion }}</p>
          <p v-if="it.detalle" class="text-sm text-on-surface-variant truncate">{{ it.detalle }}</p>
          <p class="text-xs font-medium text-outline mt-1">{{ it.adminEmail }} · {{ fmt(it.creadoEn) }}</p>
        </div>
      </li>
    </ul>
  </div>
</template>
