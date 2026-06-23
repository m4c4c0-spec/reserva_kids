<script setup>
definePageMeta({ layout: 'admin', requiereAdmin: true })

import { ref, onMounted } from 'vue'
import { adminService } from '../../services/adminService'
import { clp } from '../../composables/useCurrency'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'

const m = ref(null)
const cargando = ref(false)
const err = ref('')

async function cargar() {
  cargando.value = true
  err.value = ''
  try {
    m.value = await adminService.metricas()
  } catch (e) {
    err.value = e.response?.data?.message || 'No se pudieron cargar las métricas'
  } finally {
    cargando.value = false
  }
}

onMounted(cargar)
</script>

<template>
  <div>
    <div class="mb-6">
      <h2 class="font-display font-extrabold text-2xl text-on-surface">Métricas</h2>
      <p class="text-sm font-medium text-on-surface-variant">Visión global de la plataforma.</p>
    </div>

    <ErrorBanner :mensaje="err" />
    <LoadingSpinner v-if="cargando" mensaje="Cargando métricas…" />

    <div v-else-if="m" class="space-y-6">
      <!-- Negocios -->
      <section>
        <h3 class="text-xs font-bold uppercase tracking-wide text-on-surface-variant mb-2">Negocios</h3>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-primary">{{ m.negociosTotal }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Total</div>
          </div>
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-on-surface">{{ m.negociosActivos }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Activos</div>
          </div>
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-on-error-container">{{ m.negociosSuspendidos }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Suspendidos</div>
          </div>
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-outline">{{ m.negociosCerrados }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Cerrados</div>
          </div>
        </div>
      </section>

      <!-- Actividad -->
      <section>
        <h3 class="text-xs font-bold uppercase tracking-wide text-on-surface-variant mb-2">Actividad</h3>
        <div class="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-on-surface">{{ m.reservasTotal }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Reservas totales</div>
          </div>
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-primary">{{ m.reservasActivas }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Reservas en curso</div>
          </div>
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-3xl font-display font-extrabold text-on-surface">{{ m.apoderados }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Apoderados</div>
          </div>
          <div class="glass-card rounded-2xl p-4 shadow-soft">
            <div class="text-2xl font-display font-extrabold text-secondary">{{ clp(m.recaudadoSenasClp) }}</div>
            <div class="text-xs font-bold text-on-surface-variant mt-1">Señas recaudadas</div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>
