<script setup>
definePageMeta({ layout: 'default', requiereCliente: true })

import { ref, computed, watch, onMounted } from 'vue'
import * as negocioService from '../../../services/negocioService'
import { clp } from '../../../composables/useCurrency'
import { duracion } from '../../../composables/useDuration'
import { useClienteAuthStore } from '../../../stores/clienteAuth'
import bgV2 from '../../../assets/login-bg-v2.webp'
import charCake from '../../../assets/char-cake.png'
import BaseButton from '../../../components/BaseButton.vue'
import ErrorBanner from '../../../components/ErrorBanner.vue'
import LoadingSpinner from '../../../components/LoadingSpinner.vue'

const route = useRoute()
const router = useRouter()
const auth = useClienteAuthStore()
const config = useRuntimeConfig()
const slug = (process.client && window.__SINGLE_TENANT_SLUG__) || config.public.singleTenantSlug || route.params.slug

const negocio = ref(null)
const cargando = ref(true)
const noExiste = ref(false)

useSeoMeta({
  title: () => (negocio.value?.nombre ? `Reservar en ${negocio.value.nombre}` : 'Reservar tu cita'),
  description: () =>
    negocio.value?.nombre
      ? `Agenda tu cita en ${negocio.value.nombre}: elige servicios, día y hora, y paga la seña online de forma segura.`
      : 'Agenda tu cita: elige servicios, día y hora, y paga la seña online.',
  robots: 'noindex', // flujo autenticado de agendamiento, no debe indexarse
})

const paso = ref(1)
const pasos = ['Servicios', 'Día y hora', 'Pago']

const seleccionadas = ref(new Set())
function alternar(id) {
  const s = new Set(seleccionadas.value)
  s.has(id) ? s.delete(id) : s.add(id)
  seleccionadas.value = s
}
const serviciosSel = computed(() => (negocio.value?.servicios || []).filter((s) => seleccionadas.value.has(s.id)))
const totalClp = computed(() => serviciosSel.value.reduce((acc, s) => acc + (s.precioClp || 0), 0))
const duracionTotalMin = computed(() => serviciosSel.value.reduce((acc, s) => acc + (s.duracionMin || 0), 0))

// Fecha "hoy" en hora local de Chile (no UTC): evita que tras las 20:00 UTC-4
// new Date() en UTC salte al día siguiente. 'en-CA' da formato YYYY-MM-DD.
const hoyStr = new Date().toLocaleDateString('en-CA', { timeZone: 'America/Santiago' })
const fecha = ref(hoyStr)
const horas = ref([])
const horaSel = ref(null)
const cargandoHoras = ref(false)
const errorHoras = ref('')

async function cargarHoras() {
  horaSel.value = null
  horas.value = []
  errorHoras.value = ''
  if (!duracionTotalMin.value) return
  cargandoHoras.value = true
  try {
    horas.value = await negocioService.horas(slug, fecha.value, duracionTotalMin.value)
  } catch {
    errorHoras.value = 'No se pudieron cargar las horas, intenta de nuevo'
  } finally {
    cargandoHoras.value = false
  }
}

watch(fecha, () => {
  if (paso.value === 2) cargarHoras()
})

const fechaLarga = computed(() =>
  new Date(`${fecha.value}T00:00:00`).toLocaleDateString('es-CL', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }),
)

function aServicios() {
  paso.value = 1
}
function aDiaYHora() {
  if (!seleccionadas.value.size) return
  paso.value = 2
  cargarHoras()
}
function aPago() {
  if (!horaSel.value) return
  paso.value = 3
}

const agendando = ref(false)
const errorPago = ref('')
const aceptaPrivacidad = ref(false)
const redirigiendoMP = ref(false) // aviso antes de salir del sitio hacia Mercado Pago

async function pagar() {
  errorPago.value = ''
  if (!aceptaPrivacidad.value) {
    errorPago.value = 'Debes aceptar la política de privacidad para continuar'
    return
  }
  agendando.value = true
  try {
    const data = await negocioService.agendar(slug, {
      servicioIds: [...seleccionadas.value],
      fecha: fecha.value,
      hora: horaSel.value,
    })
    // Aviso de redirección externa: el usuario ve a dónde va antes de salir del sitio.
    redirigiendoMP.value = true
    await new Promise((r) => setTimeout(r, 1000))
    window.location.href = data.initPoint
  } catch (e) {
    if (e.response?.status === 401) {
      auth.logout()
      router.push('/clientes/entrar')
      return
    }
    if (e.response?.status === 409) {
      errorPago.value = e.response.data?.message || 'Esa hora ya no está disponible'
      paso.value = 2
      cargarHoras()
      return
    }
    errorPago.value = e.response?.data?.message || 'No se pudo iniciar el pago, intenta de nuevo'
  } finally {
    agendando.value = false
  }
}

onMounted(async () => {
  try {
    negocio.value = await negocioService.catalogo(slug)
  } catch {
    noExiste.value = true
  } finally {
    cargando.value = false
  }
})
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed pb-32" :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="max-w-2xl mx-auto p-5 space-y-6">
      <header class="flex items-center justify-between pt-6">
        <button
          class="flex items-center gap-1 text-sm font-bold text-on-surface-variant hover:text-primary transition-colors"
          @click="router.push('/clientes/negocios')"
        >
          <span class="material-symbols-outlined text-[20px]">arrow_back</span> Negocios
        </button>
        <p class="text-sm font-bold text-on-surface-variant truncate">{{ negocio?.nombre }}</p>
      </header>

      <!-- Indicador de pasos -->
      <ol class="flex items-center justify-center gap-2">
        <li v-for="(p, i) in pasos" :key="p" class="flex items-center gap-2">
          <span
            class="flex items-center gap-1.5 text-xs font-bold"
            :class="paso === i + 1 ? 'text-primary' : 'text-on-surface-variant/60'"
          >
            <span
              class="w-6 h-6 rounded-full flex items-center justify-center text-[11px]"
              :class="
                paso >= i + 1
                  ? 'bg-primary-container text-on-primary-container'
                  : 'bg-surface-highest text-on-surface-variant'
              "
            >
              {{ i + 1 }}
            </span>
            <span class="hidden sm:inline">{{ p }}</span>
          </span>
          <span v-if="i < pasos.length - 1" class="w-5 h-0.5 rounded bg-surface-highest"></span>
        </li>
      </ol>

      <LoadingSpinner v-if="cargando" mensaje="Cargando servicios…" />

      <div v-else-if="noExiste" class="text-center bg-surface-container rounded-3xl px-5 py-10">
        <img
          :src="charCake"
          alt=""
          class="w-24 h-24 object-contain mx-auto drop-shadow-xl character-img"
          aria-hidden="true"
        />
        <p class="font-display font-bold text-on-surface mt-2">Este negocio no está disponible</p>
      </div>

      <!-- PASO 1: selección de servicios -->
      <template v-else-if="paso === 1">
        <div>
          <h1 class="font-display font-extrabold text-2xl text-primary tracking-tight">Elige tus servicios</h1>
          <p class="font-medium text-on-surface-variant text-sm mt-0.5">
            Puedes combinar varios; sumamos precio y duración.
          </p>
        </div>

        <ul v-if="negocio?.servicios?.length" class="space-y-3">
          <li v-for="s in negocio.servicios" :key="s.id">
            <button
              type="button"
              class="w-full text-left bg-surface-lowest rounded-2xl border-2 p-4 transition-all flex items-start gap-3"
              :class="
                seleccionadas.has(s.id)
                  ? 'border-primary shadow-lifted'
                  : 'border-outline-variant/20 hover:border-secondary/50'
              "
              @click="alternar(s.id)"
            >
              <span
                class="material-symbols-outlined mt-0.5 shrink-0"
                :class="seleccionadas.has(s.id) ? 'text-primary' : 'text-on-surface-variant/40'"
              >
                {{ seleccionadas.has(s.id) ? 'check_circle' : 'radio_button_unchecked' }}
              </span>
              <div class="min-w-0 flex-1">
                <div class="flex items-baseline justify-between gap-2">
                  <h2 class="font-display font-bold text-on-surface truncate">{{ s.nombre }}</h2>
                  <span class="font-bold text-primary whitespace-nowrap">{{ clp(s.precioClp) }}</span>
                </div>
                <p v-if="s.descripcion" class="text-sm text-on-surface-variant mt-0.5 line-clamp-2">
                  {{ s.descripcion }}
                </p>
                <span class="inline-flex items-center gap-1 text-xs font-bold text-on-surface-variant mt-1">
                  <span class="material-symbols-outlined text-[15px]">schedule</span> {{ duracion(s.duracionMin) }}
                </span>
              </div>
            </button>
          </li>
        </ul>

        <div v-else class="text-center bg-surface-container rounded-3xl px-5 py-10">
          <p class="font-display font-bold text-on-surface">Este negocio aún no publica servicios</p>
        </div>
      </template>

      <!-- PASO 2: día y hora -->
      <template v-else-if="paso === 2">
        <div>
          <h1 class="font-display font-extrabold text-2xl text-primary tracking-tight">Elige día y hora</h1>
          <p class="font-medium text-on-surface-variant text-sm mt-0.5">
            Necesitas {{ duracion(duracionTotalMin) }} en total. Te mostramos solo las horas que calzan.
          </p>
        </div>

        <div class="card-festiva !rounded-2xl !p-4">
          <label class="block text-sm font-bold text-on-surface mb-1" for="fecha">Día</label>
          <input id="fecha" v-model="fecha" type="date" :min="hoyStr" class="input-festivo !py-3" />
          <p class="text-xs font-medium text-on-surface-variant mt-1 capitalize">{{ fechaLarga }}</p>
        </div>

        <div>
          <LoadingSpinner v-if="cargandoHoras" mensaje="Buscando horas…" />
          <ErrorBanner v-else-if="errorHoras" :mensaje="errorHoras" />

          <div v-else-if="horas.length" class="grid grid-cols-3 sm:grid-cols-4 gap-2">
            <button
              v-for="h in horas"
              :key="h"
              type="button"
              class="py-2.5 rounded-xl border-2 font-bold text-sm transition-all"
              :class="
                horaSel === h
                  ? 'border-primary bg-primary-container text-on-primary-container shadow-md'
                  : 'border-outline-variant/20 bg-surface-lowest text-on-surface hover:border-secondary/50'
              "
              @click="horaSel = h"
            >
              {{ h }}
            </button>
          </div>

          <div v-else class="text-center bg-surface-container rounded-3xl px-5 py-10">
            <span class="material-symbols-outlined text-4xl text-on-surface-variant">event_busy</span>
            <p class="font-display font-bold text-on-surface mt-2">No hay horas libres ese día</p>
            <p class="font-medium text-on-surface-variant text-sm mt-1">Prueba con otra fecha.</p>
          </div>
        </div>
      </template>

      <!-- PASO 3: resumen y pago -->
      <template v-else>
        <div>
          <h1 class="font-display font-extrabold text-2xl text-primary tracking-tight">Confirma y paga</h1>
          <p class="font-medium text-on-surface-variant text-sm mt-0.5">Tu hora se confirma al aprobarse el pago.</p>
        </div>

        <div class="card-festiva !rounded-2xl space-y-3">
          <div class="flex items-center gap-2 text-on-surface font-bold">
            <span class="material-symbols-outlined text-primary">event</span>
            <span class="capitalize">{{ fechaLarga }} · {{ horaSel }}</span>
          </div>
          <ul class="divide-y divide-outline-variant/15">
            <li v-for="s in serviciosSel" :key="s.id" class="flex justify-between py-2 text-sm">
              <span class="text-on-surface"
                >{{ s.nombre }} <span class="text-on-surface-variant">· {{ duracion(s.duracionMin) }}</span></span
              >
              <span class="font-bold text-on-surface">{{ clp(s.precioClp) }}</span>
            </li>
          </ul>
          <div class="flex justify-between items-center pt-1">
            <span class="font-bold text-on-surface">Total · {{ duracion(duracionTotalMin) }}</span>
            <span class="font-extrabold text-lg text-primary">{{ clp(totalClp) }}</span>
          </div>
        </div>

        <ErrorBanner :mensaje="errorPago" />

        <label class="flex items-start gap-2 text-xs font-medium text-on-surface-variant">
          <input v-model="aceptaPrivacidad" type="checkbox" required class="mt-0.5 w-4 h-4 accent-primary" />
          <span
            >Autorizo al negocio a tratar mis datos personales (RUT y datos de contacto) para esta reserva, conforme a
            la
            <NuxtLink to="/privacidad" class="text-secondary underline">política de privacidad</NuxtLink>
            (Ley 19.628).</span
          >
        </label>

        <div class="flex items-center gap-3">
          <button
            :disabled="agendando"
            class="py-3 px-4 rounded-full font-bold text-on-surface-variant hover:text-primary transition-colors disabled:opacity-40"
            @click="paso = 2"
          >
            Atrás
          </button>
          <BaseButton
            variante="primario"
            :cargando="agendando"
            :deshabilitado="agendando"
            class="flex-1 py-3.5"
            @click="pagar"
          >
            <span class="material-symbols-outlined mr-2 text-[20px]">lock</span>
            {{ agendando ? 'Redirigiendo…' : `Pagar ${clp(totalClp)}` }}
          </BaseButton>
        </div>
      </template>
    </div>

    <!-- Barra inferior: resumen + acción del paso -->
    <footer
      v-if="(paso === 1 || paso === 2) && !cargando && !noExiste"
      class="fixed bottom-0 inset-x-0 bg-surface-lowest/95 backdrop-blur border-t border-outline-variant/20 shadow-lifted"
    >
      <div class="max-w-2xl mx-auto p-4 flex items-center justify-between gap-4">
        <div>
          <p class="font-extrabold text-lg text-primary leading-tight">{{ clp(totalClp) }}</p>
          <p class="text-xs font-bold text-on-surface-variant flex items-center gap-1">
            <span class="material-symbols-outlined text-[14px]">schedule</span>
            {{ duracionTotalMin ? duracion(duracionTotalMin) : 'Selecciona servicios' }}
            <span v-if="serviciosSel.length"> · {{ serviciosSel.length }} servicio(s)</span>
          </p>
        </div>

        <div class="flex items-center gap-2">
          <button
            v-if="paso === 2"
            class="py-3 px-4 rounded-full font-bold text-on-surface-variant hover:text-primary transition-colors"
            @click="aServicios"
          >
            Atrás
          </button>
          <BaseButton
            v-if="paso === 1"
            variante="primario"
            :deshabilitado="!seleccionadas.size"
            class="py-3 px-6"
            @click="aDiaYHora"
          >
            Continuar <span class="material-symbols-outlined ml-1 text-[18px]">arrow_forward</span>
          </BaseButton>
          <BaseButton v-else variante="primario" :deshabilitado="!horaSel" class="py-3 px-6" @click="aPago">
            Ir a pagar <span class="material-symbols-outlined ml-1 text-[18px]">arrow_forward</span>
          </BaseButton>
        </div>
      </div>
    </footer>
    <Transition enter-active-class="transition-opacity duration-200 ease-out" enter-from-class="opacity-0">
      <div
        v-if="redirigiendoMP"
        class="fixed inset-0 z-50 flex flex-col items-center justify-center gap-4 bg-on-surface/50 backdrop-blur-sm p-6 text-center"
        role="status"
        aria-live="polite"
      >
        <div class="bg-surface-lowest rounded-3xl shadow-lifted px-8 py-7 max-w-sm">
          <LoadingSpinner mensaje="" />
          <p class="font-display font-bold text-lg text-on-surface mt-3">Te llevamos a Mercado Pago</p>
          <p class="text-sm text-on-surface-variant mt-1">
            Vas a salir del sitio para completar el pago de forma segura. No cierres la ventana.
          </p>
        </div>
      </div>
    </Transition>
  </main>
</template>
