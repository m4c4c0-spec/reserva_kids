<script setup>
import { ref, onMounted, computed } from 'vue'
import api from '../api/client'
import { useAuthStore } from '../stores/auth'
import BaseButton from '../components/BaseButton.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const auth = useAuthStore()
const estado = ref(null)
const cargando = ref(true)
const copiado = ref(false)

// Link público del negocio: lo que el dueño comparte por WhatsApp/Instagram para recibir reservas.
const linkPublico = computed(() => (auth.slug ? `${window.location.origin}/${auth.slug}` : ''))
const claveDescartado = computed(() => `rk_onb_compartir_descartado_${auth.slug || ''}`)
const compartirDescartado = ref(false)

const mensajeWhatsApp = computed(
  () =>
    `¡Reserva el cumpleaños en ${auth.nombreNegocio || 'nuestro salón'}! Mira precios y horas disponibles aquí: ${linkPublico.value}`,
)
const whatsappShareUrl = computed(() => `https://wa.me/?text=${encodeURIComponent(mensajeWhatsApp.value)}`)

async function copiarLink() {
  try {
    await navigator.clipboard.writeText(linkPublico.value)
    copiado.value = true
    setTimeout(() => (copiado.value = false), 2000)
  } catch {
    copiado.value = false
  }
}

function descartarCompartir() {
  compartirDescartado.value = true
  try {
    localStorage.setItem(claveDescartado.value, '1')
  } catch {
    // localStorage no disponible (modo privado): se vuelve a mostrar, es aceptable.
  }
}

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
    key: 'horarios',
    hecho: estado.value?.horariosConfigurados,
    titulo: 'Define tus horarios de atención',
    descripcion: 'Marca los días y horas en que recibes fiestas; sin esto los padres no ven horas para reservar.',
    cta: 'Definir horarios',
    to: '/panel/calendario',
    icono: 'schedule',
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
// La tarjeta "comparte tu link" solo aparece cuando el onboarding está completo y el
// dueño no la descartó: es el empujón a la activación real (recibir su 1ª reserva).
const mostrarCompartir = computed(() => completo.value && !compartirDescartado.value && !!linkPublico.value)

async function cargar() {
  cargando.value = true
  try {
    const { data } = await api.get('/sistema/onboarding')
    estado.value = data
    try {
      compartirDescartado.value = localStorage.getItem(claveDescartado.value) === '1'
    } catch {
      compartirDescartado.value = false
    }
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

  <!-- Activación: onboarding completo → empujar a compartir el link y recibir la 1ª reserva. -->
  <section
    v-else-if="mostrarCompartir"
    class="card-festiva !p-6 space-y-4 bg-tertiary-container/30 border-2 border-tertiary/40"
  >
    <div class="flex items-start justify-between gap-3">
      <div class="flex items-start gap-3">
        <span class="material-symbols-outlined text-tertiary !text-4xl shrink-0 drop-shadow" aria-hidden="true"
          >rocket_launch</span
        >
        <div>
          <h2 class="font-display font-extrabold text-xl md:text-2xl text-on-surface leading-tight">
            ¡Tu página está lista! Compártela y recibe tu primera reserva
          </h2>
          <p class="font-medium text-on-surface-variant text-sm mt-1">
            Mándale este link a los padres por WhatsApp o Instagram. Verán tus precios y horas y podrán reservar.
          </p>
        </div>
      </div>
      <button
        class="text-on-surface-variant hover:text-on-surface transition-colors shrink-0"
        aria-label="Ocultar"
        @click="descartarCompartir"
      >
        <span class="material-symbols-outlined">close</span>
      </button>
    </div>

    <div class="flex flex-col sm:flex-row gap-2">
      <div
        class="flex-1 flex items-center gap-2 bg-surface-lowest rounded-2xl px-4 py-3 border border-outline-variant/30 min-w-0"
      >
        <span class="material-symbols-outlined text-outline shrink-0">link</span>
        <span class="font-medium text-on-surface text-sm truncate">{{ linkPublico }}</span>
      </div>
      <div class="flex gap-2">
        <BaseButton variante="secundario" type="button" class="py-2.5 px-4" @click="copiarLink">
          <span class="material-symbols-outlined" style="font-size: 18px">{{
            copiado ? 'check' : 'content_copy'
          }}</span>
          {{ copiado ? 'Copiado' : 'Copiar' }}
        </BaseButton>
        <a :href="whatsappShareUrl" target="_blank" rel="noopener" class="shrink-0">
          <BaseButton variante="primario" type="button" class="py-2.5 px-4">
            <span class="material-symbols-outlined" style="font-size: 18px">share</span>
            WhatsApp
          </BaseButton>
        </a>
      </div>
    </div>
  </section>
</template>
