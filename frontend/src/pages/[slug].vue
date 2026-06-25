<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import * as negocioService from '../services/negocioService'
import { clp } from '../composables/useCurrency'
import { useHaptics } from '../composables/useHaptics'
import { useClienteAuthStore } from '../stores/clienteAuth'
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import charCake from '../assets/char-cake.png'
import BaseButton from '../components/BaseButton.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

definePageMeta({ layout: 'default' })

const route = useRoute()
const runtimeConfig = useRuntimeConfig()

const slug = computed(
  () => (process.client && window.__SINGLE_TENANT_SLUG__) || runtimeConfig.public.singleTenantSlug || route.params.slug,
)
const clienteAuth = useClienteAuthStore()

const esSingleTenant = !!((process.client && window.__SINGLE_TENANT_SLUG__) || runtimeConfig.public.singleTenantSlug)
const negocio = ref(null)
const noExiste = ref(false)
const bloques = ref([])
const hoy = new Date()
const mes = ref(`${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`)

const seoTitle = computed(() =>
  negocio.value
    ? negocio.value.tituloPagina || `${negocio.value.nombre} \u2014 DulceVida`
    : 'DulceVida \u2014 Reserva de cumpleaños',
)
const seoDescription = computed(() =>
  negocio.value
    ? `Reserva tu cumpleaños infantil en ${negocio.value.nombre}. Elige un servicio, fecha y hora.`
    : 'Plataforma de reservas para cumpleaños infantiles en Chile.',
)
useHead({
  title: seoTitle,
  meta: [
    { name: 'description', content: seoDescription },
    { property: 'og:title', content: seoTitle },
    { property: 'og:description', content: seoDescription },
    { property: 'og:type', content: 'website' },
  ],
})

const seleccion = ref({ servicioId: null, bloqueId: null })
const adicionalesSeleccionados = ref([])
const servicioSeleccionado = computed(() => negocio.value?.servicios.find((s) => s.id === seleccion.value.servicioId))
const serviciosPrincipales = computed(() => (negocio.value?.servicios || []).filter((s) => !s.esAdicional))
const serviciosAdicionales = computed(() => (negocio.value?.servicios || []).filter((s) => s.esAdicional))
const diaSeleccionado = ref(null)
const haptics = useHaptics()

function elegirServicio(id) {
  seleccion.value.servicioId = id
  haptics.tap()
}
function elegirDia(fecha, cupos, pasada) {
  if (pasada || cupos <= 0) return
  diaSeleccionado.value = fecha
  haptics.tap()
}
function elegirBloque(id) {
  seleccion.value.bloqueId = id
  haptics.tap()
}

const bloquesPorDia = computed(() => {
  const mapa = {}
  for (const b of bloques.value) {
    const fecha = b.fecha
    if (!mapa[fecha]) mapa[fecha] = []
    mapa[fecha].push(b)
  }
  return mapa
})

const cuposPorDia = computed(() => {
  const mapa = {}
  for (const b of bloques.value) {
    const fecha = b.fecha
    mapa[fecha] = (mapa[fecha] || 0) + 1
  }
  return mapa
})

const diasSemana = ['L', 'M', 'M', 'J', 'V', 'S', 'D']

const grillaMes = computed(() => {
  const [anio, nMes] = mes.value.split('-').map(Number)
  const primerDia = new Date(anio, nMes - 1, 1)
  const ultimoDia = new Date(anio, nMes, 0)
  const totalDias = ultimoDia.getDate()
  let inicioSemana = primerDia.getDay()
  if (inicioSemana === 0) inicioSemana = 7
  inicioSemana = inicioSemana - 1

  const grilla = []
  for (let i = 0; i < inicioSemana; i++) grilla.push(null)
  for (let d = 1; d <= totalDias; d++) {
    const fechaStr = `${anio}-${String(nMes).padStart(2, '0')}-${String(d).padStart(2, '0')}`
    const cupos = cuposPorDia.value[fechaStr] || 0
    const pasada = new Date(anio, nMes - 1, d) < new Date(hoy.getFullYear(), hoy.getMonth(), hoy.getDate())
    grilla.push({ dia: d, fecha: fechaStr, cupos, pasada })
  }
  return grilla
})

function colorTermico(cupos, pasada) {
  if (pasada) return 'bg-surface-high text-on-surface-variant/30'
  if (cupos >= 4) return 'bg-[#bbf7d0] text-green-900 hover:bg-[#86efac]'
  if (cupos >= 2) return 'bg-[#fde68a] text-amber-900 hover:bg-[#fcd34d]'
  if (cupos >= 1) return 'bg-[#fed7aa] text-orange-900 hover:bg-[#fdba74]'
  return 'bg-surface-highest text-on-surface-variant/30 cursor-default'
}

const bloquesDelDia = computed(() => {
  if (!diaSeleccionado.value) return []
  return bloquesPorDia.value[diaSeleccionado.value] || []
})

const paso = ref(1)
const pasos = [
  { n: 1, titulo: 'Elige la fiesta', subtitulo: 'Elige una opción de celebración' },
  { n: 2, titulo: 'Elige la fecha', subtitulo: 'Elige el día y la hora' },
  { n: 3, titulo: 'Tus datos', subtitulo: 'Cuéntanos quién eres y dónde' },
]
const pasoActual = computed(() => pasos[paso.value - 1])

function irAPaso(n) {
  if (n === 2 && !seleccion.value.servicioId) return
  if (n === 3 && (!seleccion.value.servicioId || !seleccion.value.bloqueId)) return
  paso.value = n
}

watch(
  () => seleccion.value.servicioId,
  (v) => {
    if (!v) paso.value = 1
  },
)
watch(
  () => seleccion.value.bloqueId,
  (v) => {
    if (!v && paso.value === 3) paso.value = 2
  },
)

function formatearRut(raw) {
  if (!raw) return ''
  const valor = raw.replace(/[^\dKk]/g, '').toUpperCase()
  if (valor.length <= 1) return valor
  const cuerpo = valor.slice(0, -1).replace(/\D/g, '')
  const dv = valor.slice(-1)
  let conPuntos = cuerpo
  for (let i = cuerpo.length - 3; i > 0; i -= 3) {
    conPuntos = conPuntos.slice(0, i) + '.' + conPuntos.slice(i)
  }
  return conPuntos + '-' + dv
}

function onInputRut(e) {
  const formateado = formatearRut(e.target.value)
  form.value.rut = formateado
  e.target.value = formateado
}

const TELEFONO_DIGITOS_MAX = 11

function formatearTelefono(raw) {
  if (!raw) return ''
  let digitos = raw.replace(/\D/g, '')
  if (digitos.startsWith('00')) digitos = digitos.substring(2)
  if (digitos.startsWith('56') && digitos.length > 4) {
    const resto = digitos.substring(2)
    if (resto.length === 9 && resto.startsWith('9')) {
      return `+56 9 ${resto.substring(1, 5)} ${resto.substring(5)}`
    }
  }
  if (digitos.length === 9 && digitos.startsWith('9')) {
    return `+56 9 ${digitos.substring(1, 5)} ${digitos.substring(5)}`
  }
  if (digitos.length === 8) {
    return `+56 ${digitos.substring(0, 1)} ${digitos.substring(1, 5)} ${digitos.substring(5)}`
  }
  return '+' + digitos.substring(0, TELEFONO_DIGITOS_MAX)
}

function onInputTelefono(e) {
  const soloDigitos = e.target.value.replace(/\D/g, '').substring(0, TELEFONO_DIGITOS_MAX)
  const formateado = formatearTelefono(soloDigitos)
  form.value.telefono = formateado
  e.target.value = formateado
}

const form = ref({
  nombreContacto: '',
  rut: '',
  telefono: '',
  email: '',
  numNinos: null,
  comuna: '',
  comentarios: '',
  aceptaDatos: false,
  aceptaPoliticas: false,
})
const enviando = ref(false)
const error = ref('')
const exito = ref(null)
const cargando = ref(true)

async function cargarNegocio() {
  cargando.value = true
  noExiste.value = false
  negocio.value = null
  bloques.value = []
  try {
    negocio.value = await negocioService.catalogo(slug.value)
    if (negocio.value) aplicarMarcaBlanca(negocio.value)
  } catch {
    noExiste.value = true
  } finally {
    cargando.value = false
  }
}

let pixelInyectado = false
function aplicarMarcaBlanca(n) {
  if (!process.client) return
  if (n.colorPrimario) {
    document.documentElement.style.setProperty('--color-primary', n.colorPrimario)
    document.documentElement.style.setProperty('--color-primary-fixed', n.colorPrimario)
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', n.colorPrimario)
  }
  if (n.metaPixelId && /^\d+$/.test(n.metaPixelId) && !pixelInyectado) {
    pixelInyectado = true
    /* eslint-disable */
    !function(f,b,e,v,n,t,s){if(f.fbq)return;n=f.fbq=function(){n.callMethod?n.callMethod.apply(n,arguments):n.queue.push(arguments)};if(!f._fbq)f._fbq=n;n.push=n;n.loaded=!0;n.version='2.0';n.queue=[];t=b.createElement(e);t.async=!0;t.src=v;s=b.getElementsByTagName(e)[0];s.parentNode.insertBefore(t,s)}(window,document,'script','https://connect.facebook.net/en_US/fbevents.js')
    /* eslint-enable */
    window.fbq('init', n.metaPixelId)
    window.fbq('track', 'PageView')
  }
}

const cargandoBloques = ref(false)
async function cargarDisponibilidad() {
  if (!negocio.value) return
  cargandoBloques.value = true
  diaSeleccionado.value = null
  seleccion.value.bloqueId = null
  try {
    bloques.value = await negocioService.disponibilidad(slug.value, mes.value)
  } finally {
    cargandoBloques.value = false
  }
}

async function enviar() {
  error.value = ''
  enviando.value = true
  try {
    const data = await negocioService.crearSolicitud(slug.value, {
      ...seleccion.value,
      ...form.value,
      numNinos: form.value.numNinos || null,
      email: form.value.email || null,
      adicionalIds: adicionalesSeleccionados.value,
    })
    exito.value = data
  } catch (e) {
    const tecnico = e.response?.data?.message
    error.value =
      tecnico ||
      'No se pudo enviar. Revisa tu conexión y los datos, y vuelve a intentarlo. Si sigue fallando, escríbele al salón por WhatsApp.'
    if (e.response?.status === 409) await cargarDisponibilidad()
  } finally {
    enviando.value = false
  }
}

const whatsappUrl = computed(() => {
  const tel = negocio.value?.whatsapp
  if (!tel) return ''
  const texto = `Hola ${negocio.value?.nombre || ''}, quiero consultar sobre una reserva de cumpleaños`
  return `https://wa.me/${tel}?text=${encodeURIComponent(texto)}`
})

const staffLista = computed(() => negocio.value?.staff || [])

watch(slug, async () => {
  if (slug.value) {
    paso.value = 1
    seleccion.value = { servicioId: null, bloqueId: null }
    adicionalesSeleccionados.value = []
    exito.value = null
    await cargarNegocio()
    if (negocio.value) await cargarDisponibilidad()
  }
})

onMounted(async () => {
  if (clienteAuth.autenticado) {
    form.value.nombreContacto = clienteAuth.nombre || ''
    form.value.email = clienteAuth.email || ''
  }
  await cargarNegocio()
  if (negocio.value) await cargarDisponibilidad()
  if (route.query.pago === 'exito') haptics.success()
})

const HOLD_MS = 48 * 60 * 60 * 1000
const holdExpira = ref(null)
const ahoraMs = ref(Date.now())
let tickTimer = null

const cuentaRegresiva48h = computed(() => {
  if (!holdExpira.value) return ''
  const ms = holdExpira.value - ahoraMs.value
  if (ms <= 0) return 'expirada'
  const h = Math.floor(ms / 3600000)
  const m = Math.floor((ms % 3600000) / 60000)
  return h > 0 ? `${h} h ${m} min` : `${m} min`
})

watch(exito, (v) => {
  if (!v) return
  haptics.celebrate()
  holdExpira.value = Date.now() + HOLD_MS
  ahoraMs.value = Date.now()
  if (!tickTimer)
    tickTimer = setInterval(() => {
      ahoraMs.value = Date.now()
    }, 30000)
})

onUnmounted(() => {
  if (tickTimer) clearInterval(tickTimer)
})

const puedeAvanzar = computed(() => {
  if (paso.value === 1) return !!seleccion.value.servicioId
  if (paso.value === 2) return !!seleccion.value.bloqueId
  return true
})

function siguientePaso() {
  if (paso.value < 3) irAPaso(paso.value + 1)
}
function atrasPaso() {
  if (paso.value > 1) paso.value = paso.value - 1
}

function accionBarra() {
  haptics.tap()
  if (paso.value < 3) siguientePaso()
  else document.getElementById('form-reserva')?.requestSubmit()
}

const barraTexto = computed(() => {
  if (enviando.value) return 'Enviando…'
  if (paso.value === 3) return 'Pedir precio al salón'
  return 'Continuar'
})
const barraDeshabilitada = computed(() => enviando.value || (paso.value < 3 && !puedeAvanzar.value))
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed" :style="{ backgroundImage: `url(${bgV2})` }">
    <LoadingSpinner v-if="cargando" />

    <div v-else-if="noExiste" class="min-h-screen flex flex-col items-center justify-center p-10 text-center">
      <img
        :src="charBalloon"
        alt=""
        class="w-32 h-32 object-contain drop-shadow-xl character-img animate-floating"
        aria-hidden="true"
      />
      <p class="mt-4 font-display font-bold text-2xl text-on-surface">Negocio no encontrado</p>
      <p class="mt-1 font-medium text-on-surface-variant text-sm max-w-xs">
        Puede que el enlace esté mal escrito o que el negocio ya no esté disponible.
      </p>
      <div class="mt-6 flex flex-col sm:flex-row items-center gap-3">
        <BaseButton variante="primario" class="!rounded-full px-6 py-3" @click="cargarNegocio"> Reintentar </BaseButton>
        <NuxtLink v-if="!esSingleTenant" to="/negocios" class="font-bold text-secondary underline underline-offset-4">
          Ver todos los negocios
        </NuxtLink>
      </div>
    </div>

    <div
      v-else-if="negocio"
      class="max-w-2xl mx-auto p-6 space-y-6 bg-surface/60 backdrop-blur-xl rounded-t-3xl sm:rounded-3xl shadow-xl mt-4 mb-24 border border-white/20"
    >
      <header class="text-center pt-8 pb-2 relative">
        <div class="flex justify-center mb-2 h-28">
          <img
            :src="charBalloon"
            alt=""
            class="w-28 h-28 object-contain drop-shadow-xl character-img animate-floating"
            aria-hidden="true"
          />
        </div>
        <h1 class="font-display font-extrabold text-3xl md:text-4xl tracking-tight text-primary">
          {{ negocio.nombre }}
        </h1>
        <p class="font-medium text-on-surface-variant mt-1">Cotiza y reserva tu cumpleaños en minutos</p>
      </header>

      <div v-if="staffLista.length" class="bg-surface-lowest/80 rounded-2xl p-5 border border-outline-variant/10">
        <h2 class="font-display font-bold text-sm text-on-surface mb-3 flex items-center gap-2">
          <span class="material-symbols-outlined text-primary">groups</span>
          Nuestro equipo
        </h2>
        <div class="flex flex-wrap gap-3">
          <div
            v-for="(persona, i) in staffLista"
            :key="i"
            class="flex items-center gap-3 bg-surface-container rounded-xl px-4 py-2.5 border border-outline-variant/10"
          >
            <div class="w-9 h-9 rounded-full bg-primary-container flex items-center justify-center shrink-0">
              <span class="material-symbols-outlined text-on-primary-container text-base">person</span>
            </div>
            <div class="min-w-0">
              <p class="font-bold text-sm text-on-surface truncate">{{ persona.nombre }}</p>
              <p class="text-xs font-semibold text-primary">{{ persona.rol }}</p>
            </div>
          </div>
        </div>
      </div>

      <div
        v-if="route.query.pago === 'exito'"
        class="bg-[#dcfce7] text-green-800 font-bold p-4 rounded-2xl text-center shadow-soft"
      >
        ¡Pago de seña exitoso! La reserva será confirmada pronto.
      </div>
      <div
        v-else-if="route.query.pago === 'pendiente'"
        class="bg-tertiary-fixed text-on-tertiary-fixed font-bold p-4 rounded-2xl text-center shadow-soft"
      >
        El pago está pendiente de confirmación. Te avisaremos cuando se acredite.
      </div>
      <div
        v-else-if="route.query.pago === 'fallo'"
        class="bg-error-container text-on-error-container p-4 rounded-2xl text-center shadow-soft space-y-1"
      >
        <p class="font-bold">El pago no pasó, pero no te preocupes: no te hemos cobrado.</p>
        <p class="font-medium text-sm">
          Intenta con otra tarjeta o paga por transferencia. Si te trancas, escríbenos por WhatsApp.
        </p>
      </div>

      <div v-if="exito" class="glass-card rounded-3xl shadow-lifted p-8 text-center space-y-3 relative overflow-hidden">
        <img
          :src="charCake"
          alt=""
          class="w-24 h-24 object-contain mx-auto drop-shadow-xl character-img animate-floating"
          aria-hidden="true"
        />
        <h2 class="font-display font-extrabold text-2xl text-primary">¡Listo! Le pedimos precio al salón</h2>
        <p class="font-medium text-on-surface-variant text-sm">
          Le avisamos a <strong>{{ negocio.nombre }}</strong> que quieres celebrar ahí. Te van a escribir pronto por
          WhatsApp para confirmar el precio y la fecha.
        </p>
        <div
          v-if="cuentaRegresiva48h && cuentaRegresiva48h !== 'expirada'"
          class="inline-flex items-center gap-2 bg-tertiary-fixed text-on-tertiary-fixed font-bold rounded-full px-4 py-2 text-sm"
        >
          <span class="material-symbols-outlined text-[18px]">hourglass_top</span>
          Tu fecha queda reservada por <strong>{{ cuentaRegresiva48h }}</strong>
        </div>
        <p v-else-if="cuentaRegresiva48h === 'expirada'" class="text-sm font-semibold text-on-surface-variant">
          El plazo de 48 h venció — escríbele al salón por WhatsApp para retomar tu reserva.
        </p>
      </div>

      <template v-else>
        <nav aria-label="Progreso de la reserva" class="px-1 pt-2">
          <div class="flex items-center justify-between gap-1" role="list">
            <button
              v-for="p in pasos"
              :key="p.n"
              type="button"
              role="listitem"
              class="flex-1 flex flex-col items-center gap-1 group focus:outline-none"
              :aria-current="paso === p.n ? 'step' : undefined"
              @click="irAPaso(p.n)"
            >
              <span
                class="w-9 h-9 rounded-full flex items-center justify-center font-display font-extrabold text-sm transition-all border-2"
                :class="
                  paso > p.n
                    ? 'bg-primary text-on-primary border-primary'
                    : paso === p.n
                      ? 'bg-primary-container text-on-primary-container border-primary ring-4 ring-primary-fixed'
                      : 'bg-surface-lowest text-outline border-outline-variant/40'
                "
              >
                <span v-if="paso > p.n" class="material-symbols-outlined text-base">check</span>
                <span v-else>{{ p.n }}</span>
              </span>
              <span
                class="text-[11px] font-bold text-center leading-tight"
                :class="paso >= p.n ? 'text-on-surface' : 'text-outline'"
                >{{ p.titulo }}</span
              >
            </button>
          </div>
          <p class="text-center text-xs font-semibold text-on-surface-variant mt-2">
            Paso {{ paso }} de 3 · {{ pasoActual.subtitulo }}
          </p>
          <div class="h-1.5 rounded-full bg-surface-container mt-2 overflow-hidden">
            <div
              class="h-full bg-primary rounded-full transition-all duration-300"
              :style="{ width: `${(paso / 3) * 100}%` }"
            ></div>
          </div>
        </nav>

        <section v-show="paso === 1" class="space-y-3">
          <ul class="grid gap-3 sm:grid-cols-2">
            <li v-for="s in serviciosPrincipales" :key="s.id">
              <button
                class="w-full text-left bg-surface-lowest rounded-3xl shadow-soft p-5 border-2 transition-all hover:shadow-lifted hover:-translate-y-0.5"
                :class="
                  seleccion.servicioId === s.id ? 'border-primary ring-4 ring-primary-fixed' : 'border-transparent'
                "
                @click="elegirServicio(s.id)"
              >
                <div class="flex justify-between items-start gap-2">
                  <h3 class="font-display font-bold text-on-surface">{{ s.nombre }}</h3>
                  <span class="text-primary font-display font-extrabold whitespace-nowrap">{{ clp(s.precioClp) }}</span>
                </div>
                <p class="text-sm font-medium text-on-surface-variant mt-1">{{ s.descripcion }}</p>
                <p class="text-xs font-bold text-outline mt-1">
                  <span v-if="s.duracionMin">{{ s.duracionMin }} min</span>
                  <span v-if="s.capacidad"> · hasta {{ s.capacidad }} niños</span>
                </p>
              </button>
            </li>
          </ul>

          <div v-if="serviciosAdicionales.length" class="mt-4">
            <h3 class="font-display font-bold text-on-surface mb-2 text-sm">Agregá extras</h3>
            <ul class="grid gap-2 sm:grid-cols-2">
              <li v-for="a in serviciosAdicionales" :key="a.id">
                <label
                  class="flex items-center gap-3 bg-surface-lowest rounded-2xl p-3 border-2 border-transparent hover:border-primary/30 transition-colors cursor-pointer"
                >
                  <input
                    v-model="adicionalesSeleccionados"
                    type="checkbox"
                    :value="a.id"
                    class="accent-primary w-4 h-4"
                  />
                  <div class="flex-1">
                    <span class="font-bold text-sm text-on-surface">{{ a.nombre }}</span>
                    <span class="text-primary font-extrabold text-sm ml-2">{{ clp(a.precioClp) }}</span>
                  </div>
                </label>
              </li>
            </ul>
          </div>
        </section>

        <section v-show="paso === 2" class="space-y-3">
          <div class="flex items-center justify-between gap-2">
            <h2 class="font-display font-bold text-lg text-on-surface">Elige el día</h2>
            <input
              v-model="mes"
              type="month"
              aria-label="Mes"
              class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer"
              @change="cargarDisponibilidad"
            />
          </div>

          <div v-if="cargandoBloques" class="space-y-2">
            <div class="grid grid-cols-7 gap-1">
              <span v-for="n in 35" :key="n" class="aspect-square rounded-lg bg-[#e0e0e0] animate-pulse" />
            </div>
          </div>

          <div v-else class="space-y-2">
            <div class="grid grid-cols-7 gap-1">
              <span
                v-for="d in diasSemana"
                :key="d"
                class="text-center text-[11px] font-extrabold text-on-surface-variant py-1"
                >{{ d }}</span
              >
            </div>
            <div class="grid grid-cols-7 gap-1">
              <div v-for="(celda, i) in grillaMes" :key="i">
                <div v-if="!celda" class="aspect-square rounded-lg" />
                <button
                  v-else
                  class="w-full aspect-square rounded-lg flex flex-col items-center justify-center text-sm font-bold transition-all"
                  :class="[
                    colorTermico(celda.cupos, celda.pasada),
                    diaSeleccionado === celda.fecha ? 'ring-2 ring-primary ring-offset-1 scale-105 shadow-md' : '',
                    celda.cupos > 0 && !celda.pasada ? 'cursor-pointer active:scale-95' : '',
                  ]"
                  :disabled="celda.cupos <= 0 || celda.pasada"
                  @click="elegirDia(celda.fecha, celda.cupos, celda.pasada)"
                >
                  <span>{{ celda.dia }}</span>
                  <span v-if="celda.cupos > 0 && !celda.pasada" class="text-[9px] leading-none mt-0.5">
                    {{ celda.cupos === 1 ? 'último' : celda.cupos + ' hrs' }}
                  </span>
                </button>
              </div>
            </div>
            <div class="flex items-center justify-center gap-3 text-[10px] font-semibold text-on-surface-variant pt-1">
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-[#bbf7d0]" /> Libre</span>
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-[#fde68a]" /> Pocos cupos</span>
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-[#fed7aa]" /> Últimos</span>
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-surface-highest" /> Lleno</span>
            </div>
          </div>

          <div v-if="diaSeleccionado && bloquesDelDia.length" class="space-y-2 pt-2">
            <p class="text-sm font-bold text-on-surface">{{ diaSeleccionado }} · elige la hora</p>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="b in bloquesDelDia"
                :key="b.id"
                class="chip-festivo rounded-full px-5 py-2.5 font-bold transition-all shadow-md active:scale-95"
                :class="
                  seleccion.bloqueId === b.id
                    ? 'bg-primary text-on-primary ring-2 ring-primary shadow-lg scale-105'
                    : 'bg-surface-lowest text-on-surface border-transparent hover:shadow-lifted'
                "
                @click="elegirBloque(b.id)"
              >
                {{ b.horaInicio.slice(0, 5) }}
              </button>
            </div>
          </div>

          <p
            v-if="!cargandoBloques && !Object.keys(cuposPorDia).length"
            class="font-medium text-on-surface-variant text-sm bg-surface-container rounded-2xl px-4 py-6 text-center"
          >
            Sin horarios disponibles este mes — prueba el siguiente mes.
          </p>
        </section>

        <section v-show="paso === 3" class="space-y-3">
          <p
            v-if="clienteAuth.autenticado"
            class="flex items-center gap-2 text-sm font-semibold text-on-secondary-container bg-secondary-fixed rounded-2xl px-4 py-2"
          >
            <span class="material-symbols-outlined text-[18px]">badge</span>
            Reservando como <strong>{{ clienteAuth.nombre || clienteAuth.email }}</strong> — completa tu teléfono
          </p>
          <form id="form-reserva" class="card-festiva grid gap-3 sm:grid-cols-2" @submit.prevent="enviar">
            <div>
              <label for="f-nombre" class="block text-xs font-bold text-on-surface-variant mb-1">Tu nombre</label>
              <input
                id="f-nombre"
                v-model="form.nombreContacto"
                required
                maxlength="120"
                placeholder="Tu nombre"
                class="input-festivo"
              />
            </div>
            <div>
              <label for="f-rut" class="block text-xs font-bold text-on-surface-variant mb-1">RUT</label>
              <input
                id="f-rut"
                :value="form.rut"
                required
                maxlength="15"
                placeholder="12.345.678-5"
                autocomplete="off"
                class="input-festivo"
                @input="onInputRut"
              />
              <p class="text-[11px] font-medium text-outline mt-0.5">Se pone los puntos y el guion solito.</p>
            </div>
            <div>
              <label for="f-telefono" class="block text-xs font-bold text-on-surface-variant mb-1"
                >Teléfono (WhatsApp)</label
              >
              <input
                id="f-telefono"
                :value="form.telefono"
                required
                maxlength="20"
                inputmode="tel"
                placeholder="+56 9 1234 5678"
                autocomplete="off"
                class="input-festivo"
                @input="onInputTelefono"
              />
              <p class="text-[11px] font-medium text-outline mt-0.5">Escribe los números y lo armamos solo.</p>
            </div>
            <div>
              <label for="f-email" class="block text-xs font-bold text-on-surface-variant mb-1">Email (opcional)</label>
              <input
                id="f-email"
                v-model="form.email"
                type="email"
                placeholder="Email (opcional)"
                class="input-festivo"
              />
            </div>
            <div>
              <label for="f-ninos" class="block text-xs font-bold text-on-surface-variant mb-1">Nº de niños</label>
              <input
                id="f-ninos"
                v-model.number="form.numNinos"
                type="number"
                min="1"
                placeholder="Nº de niños"
                class="input-festivo"
              />
            </div>
            <div class="sm:col-span-2">
              <label for="f-comuna" class="block text-xs font-bold text-on-surface-variant mb-1">Comuna</label>
              <input id="f-comuna" v-model="form.comuna" maxlength="80" placeholder="Comuna" class="input-festivo" />
            </div>
            <div class="sm:col-span-2">
              <label for="f-comentarios" class="block text-xs font-bold text-on-surface-variant mb-1"
                >Comentarios</label
              >
              <textarea
                id="f-comentarios"
                v-model="form.comentarios"
                maxlength="2000"
                rows="3"
                placeholder="Tema del cumpleaños, dirección, etc."
                class="input-festivo"
              ></textarea>
            </div>

            <div
              v-if="negocio.politicasCancelacion"
              class="sm:col-span-2 bg-surface-container rounded-2xl px-4 py-3 text-xs font-medium text-on-surface-variant max-h-32 overflow-y-auto"
            >
              <strong class="text-on-surface">Políticas de cancelación:</strong>
              <p class="mt-1 whitespace-pre-wrap">{{ negocio.politicasCancelacion }}</p>
            </div>
            <label
              v-if="negocio.politicasCancelacion"
              class="flex items-start gap-2 text-xs font-medium text-on-surface-variant sm:col-span-2"
            >
              <input v-model="form.aceptaPoliticas" type="checkbox" required class="mt-0.5 w-4 h-4 accent-primary" />
              <span
                >Acepto las políticas de cancelación del negocio. Entiendo que la seña puede no ser reembolsable según
                estas condiciones.</span
              >
            </label>

            <label class="flex items-start gap-2 text-xs font-medium text-on-surface-variant sm:col-span-2">
              <input v-model="form.aceptaDatos" type="checkbox" required class="mt-0.5 w-4 h-4 accent-primary" />
              <span
                >Autorizo al negocio a usar mis datos de contacto para gestionar esta solicitud. Ver
                <NuxtLink to="/privacidad" class="text-secondary underline">política de privacidad</NuxtLink>
                (Ley 21.719).</span
              >
            </label>
            <ErrorBanner :mensaje="error" class="sm:col-span-2" />
          </form>
        </section>
      </template>

      <footer class="text-center text-xs font-medium text-outline pt-2 pb-8">Hecho con DulceVida</footer>
    </div>

    <a
      v-if="whatsappUrl && !exito"
      :href="whatsappUrl"
      target="_blank"
      rel="noopener noreferrer"
      class="fixed bottom-24 right-4 z-50 flex items-center gap-2 bg-[#25D366] text-white font-bold pl-3 pr-4 py-3 rounded-full shadow-lifted hover:shadow-lifted hover:scale-105 transition-all"
      aria-label="¿Dudas? Habla con el dueño por WhatsApp"
    >
      <span class="material-symbols-outlined text-xl">support_agent</span>
      <span class="hidden sm:inline text-sm">¿Dudas? Habla con el dueño</span>
      <span class="sm:hidden text-sm">Ayuda</span>
    </a>

    <transition
      enter-active-class="transition ease-out duration-300"
      enter-from-class="transform translate-y-full opacity-0"
      enter-to-class="transform translate-y-0 opacity-100"
      leave-active-class="transition ease-in duration-200"
      leave-from-class="transform translate-y-0 opacity-100"
      leave-to-class="transform translate-y-full opacity-0"
    >
      <div
        v-if="negocio && !exito"
        class="fixed bottom-0 left-0 right-0 p-4 bg-surface/80 backdrop-blur-xl border-t border-white/20 shadow-[0_-10px_40px_rgba(0,0,0,0.1)] z-40"
      >
        <div class="max-w-2xl mx-auto flex items-center justify-between gap-4">
          <div class="flex-1 min-w-0">
            <p v-if="servicioSeleccionado" class="font-bold text-sm text-on-surface truncate">
              {{ servicioSeleccionado?.nombre }}
            </p>
            <p v-else-if="paso === 1" class="font-bold text-sm text-on-surface-variant">Paso 1: elige tu fiesta</p>
            <p v-else class="font-bold text-sm text-on-surface-variant truncate">{{ pasoActual.titulo }}</p>
            <p v-if="servicioSeleccionado" class="font-extrabold text-primary">
              {{ clp(servicioSeleccionado?.precioClp || 0) }}
            </p>
          </div>

          <div class="flex items-center gap-2">
            <BaseButton
              v-if="paso > 1"
              variante="secundario"
              type="button"
              class="py-3 px-5 shadow-md !rounded-full"
              @click="atrasPaso"
            >
              Atrás
            </BaseButton>

            <BaseButton
              variante="primario"
              type="button"
              :cargando="enviando"
              :deshabilitado="barraDeshabilitada"
              class="py-3 px-8 shadow-lg text-lg !rounded-full"
              @click="accionBarra"
            >
              {{ barraTexto }}
            </BaseButton>
          </div>
        </div>
      </div>
    </transition>
  </main>
</template>
