<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '../api/client'
import BaseButton from '../components/BaseButton.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const estado = ref(null)
const cargando = ref(true)

const pasos = computed(() => [
  {
    key: 'servicios',
    hecho: estado.value?.servicios > 0,
    titulo: 'Crea tu primer servicio',
    descripcion: 'Agrega al menos un paquete (cumpleaños básico, premium…) para que aparezca en tu página.',
    cta: 'Crear servicio',
    to: '/panel/servicios',
    icono: 'celebration',
  },
  {
    key: 'pasarela',
    hecho: estado.value?.pasarelaConfigurada,
    titulo: 'Conecta tu cobro de seña',
    descripcion: 'Conecta Mercado Pago o Khipu para que los padres paguen la seña online.',
    cta: 'Conectar pago',
    to: '/panel/configuracion',
    icono: 'payments',
  },
])

const completo = computed(() => estado.value?.completo ?? false)
const faltan = computed(() => (estado.value ? pasos.value.filter((p) => !p.hecho) : []))

async function cargar() {
  cargando.value = true
  try {
    const { data } = await api.get('/sistema/onboarding')
    estado.value = data
  } catch {
    estado.value = null
  } finally {
    cargando.value = false
  }
}

function refrescarYnavegar() {
  // El link cargará la nueva ruta; al volver a Solicitudes re-consultamos.
  setTimeout(cargar, 600)
}

onMounted(cargar)
defineExpose({ refrescar: cargar })
</script>

<template>
  <LoadingSpinner v-if="cargando" />
  <section
    v-else-if="estado && !completo"
    class="card-festiva !p-6 md:!p-8 space-y-4 bg-primary-container/30 border-2 border-primary/40"
  >
    <div class="flex items-start gap-4">
      <span class="material-symbols-outlined text-primary !text-5xl shrink-0 drop-shadow" aria-hidden="true"
        >celebration</span
      >
      <div>
        <h2 class="font-display font-extrabold text-2xl md:text-3xl text-primary leading-tight">
          ¡Bienvenido! Hagamos {{ faltan.length }} cosas para empezar a vender
        </h2>
        <p class="font-medium text-on-surface-variant mt-1">
          En menos de 5 minutos tu página estará lista para que los padres te pidan precios.
        </p>
      </div>
    </div>

    <ul class="space-y-3">
      <li
        v-for="p in faltan"
        :key="p.key"
        class="flex flex-col sm:flex-row sm:items-center justify-between gap-3 bg-surface-lowest rounded-2xl p-4 border border-outline-variant/30"
      >
        <div class="flex items-start gap-3">
          <span
            class="w-9 h-9 rounded-full bg-primary-container text-on-primary-container flex items-center justify-center shrink-0"
          >
            <span class="material-symbols-outlined">{{ p.icono }}</span>
          </span>
          <div>
            <p class="font-bold text-on-surface">{{ p.titulo }}</p>
            <p class="text-sm font-medium text-on-surface-variant">{{ p.descripcion }}</p>
          </div>
        </div>
        <RouterLink :to="p.to" class="shrink-0">
          <BaseButton variante="primario" type="button" class="py-2.5 px-5" @click="refrescarYnavegar">
            {{ p.cta }}
            <span class="material-symbols-outlined" style="font-size: 18px">arrow_forward</span>
          </BaseButton>
        </RouterLink>
      </li>
    </ul>
  </section>
</template>
