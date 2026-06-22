<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useHead } from '@vueuse/head'
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

const route = useRoute()
// slug reactivo: si se navega entre negocios sin remontar el componente
// (mismo componente, distinta ruta /:slug), recargamos el catálogo.
const slug = computed(() => route.params.slug)
const clienteAuth = useClienteAuthStore()

const negocio = ref(null)
const noExiste = ref(false)
const bloques = ref([])
const hoy = new Date()
const mes = ref(`${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`)

// SEO: meta tags dinámicos derivados del negocio cargado (no hace fetch extra).
const seoTitle = computed(() =>
  negocio.value ? `${negocio.value.nombre} — ReservaKids` : 'ReservaKids — Reserva de cumpleaños',
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
const servicioSeleccionado = computed(() => negocio.value?.servicios.find((s) => s.id === seleccion.value.servicioId))
const diaSeleccionado = ref(null) // fecha 'YYYY-MM-DD' para mostrar bloques de ese día
const haptics = useHaptics()

// ── Acciones con feedback háptico ──
function elegirServicio(id) { seleccion.value.servicioId = id; haptics.tap() }
function elegirDia(fecha, cupos, pasada) { if (pasada || cupos <= 0) return; diaSeleccionado.value = fecha; haptics.tap() }
function elegirBloque(id) { seleccion.value.bloqueId = id; haptics.tap() }

// ── Calendario térmico: agrupar bloques por día ──
const bloquesPorDia = computed(() => {
  const mapa = {}
  for (const b of bloques.value) {
    const fecha = b.fecha // '2026-06-22'
    if (!mapa[fecha]) mapa[fecha] = []
    mapa[fecha].push(b)
  }
  return mapa
})

// Número de bloques disponibles por día (para el color térmico)
const cuposPorDia = computed(() => {
  const mapa = {}
  for (const b of bloques.value) {
    const fecha = b.fecha
    mapa[fecha] = (mapa[fecha] || 0) + 1
  }
  return mapa
})

// Cabecera del calendario: L M M J V S D
const diasSemana = ['L', 'M', 'M', 'J', 'V', 'S', 'D']

// Días de la grilla del mes
const grillaMes = computed(() => {
  const [anio, nMes] = mes.value.split('-').map(Number)
  const primerDia = new Date(anio, nMes - 1, 1)
  const ultimoDia = new Date(anio, nMes, 0)
  const totalDias = ultimoDia.getDate()
  // getDay(): 0=Dom, 1=Lun... → empezar en Lunes: si es Domingo (0) → 6 espacios
  let inicioSemana = primerDia.getDay()
  if (inicioSemana === 0) inicioSemana = 7
  inicioSemana = inicioSemana - 1 // 0=Lun, 6=Dom

  const grilla = []
  for (let i = 0; i < inicioSemana; i++) grilla.push(null) // celdas vacías antes del día 1
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
  if (cupos >= 4) return 'bg-[#bbf7d0] text-green-900 hover:bg-[#86efac]'     // verde: muchos cupos
  if (cupos >= 2) return 'bg-[#fde68a] text-amber-900 hover:bg-[#fcd34d]'      // amarillo: pocos
  if (cupos >= 1) return 'bg-[#fed7aa] text-orange-900 hover:bg-[#fdba74]'     // naranja: últimos
    return 'bg-surface-highest text-on-surface-variant/30 cursor-default'          // gris: sin cupos
}

const bloquesDelDia = computed(() => {
  if (!diaSeleccionado.value) return []
  return bloquesPorDia.value[diaSeleccionado.value] || []
})

// ── Wizard/Stepper: 1 = Elige la fiesta, 2 = Elige la fecha, 3 = Tus datos ──
const paso = ref(1)
const pasos = [
  { n: 1, titulo: 'Elige la fiesta', subtitulo: 'Elige una opción de celebración' },
  { n: 2, titulo: 'Elige la fecha', subtitulo: 'Elige el día y la hora' },
  { n: 3, titulo: 'Tus datos', subtitulo: 'Cuéntanos quién eres y dónde' },
]
const pasoActual = computed(() => pasos[paso.value - 1])

function irAPaso(n) {
  // Solo se puede avanzar a un paso si sus requisitos previos están cumplidos
  if (n === 2 && !seleccion.value.servicioId) return
  if (n === 3 && (!seleccion.value.servicioId || !seleccion.value.bloqueId)) return
  paso.value = n
}

// Al cambiar de servicio o bloque hacia atrás (editar), mantenemos el paso al que se vuelve
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

// ── Formateo automático de RUT (12.345.678-5) y teléfono (+56 9 XXXX XXXX) ──
function formatearRut(raw) {
  if (!raw) return ''
  // Solo dígitos + K/k final; el dígito verificador va separado por '-'
  const valor = raw.replace(/[^\dKk]/g, '').toUpperCase()
  if (valor.length <= 1) return valor
  const cuerpo = valor.slice(0, -1).replace(/\D/g, '')
  const dv = valor.slice(-1)
  // Agrupar el cuerpo de a 3 desde la izquierda: 12.345.678
  let conPuntos = cuerpo
  for (let i = cuerpo.length - 3; i > 0; i -= 3) {
    conPuntos = conPuntos.slice(0, i) + '.' + conPuntos.slice(i)
  }
  return conPuntos + '-' + dv
}

function onInputRut(e) {
  const formateado = formatearRut(e.target.value)
  form.value.rut = formateado
  e.target.value = formateado // fuerza el binding visual del input sin v-model
}

const TELEFONO_DIGITOS_MAX = 11 // 56 + 9 dígitos del móvil

function formatearTelefono(raw) {
  if (!raw) return ''
  let digitos = raw.replace(/\D/g, '')
  if (digitos.startsWith('00')) digitos = digitos.substring(2)
  if (digitos.startsWith('56') && digitos.length > 4) {
    // ya vino con prefijo de país
    const resto = digitos.substring(2)
    if (resto.length === 9 && resto.startsWith('9')) {
      return `+56 9 ${resto.substring(1, 5)} ${resto.substring(5)}`
    }
  }
  // sin prefijo: si son 9 dígitos y empiezan con 9 → móvil chileno
  if (digitos.length === 9 && digitos.startsWith('9')) {
    return `+56 9 ${digitos.substring(1, 5)} ${digitos.substring(5)}`
  }
  // si son 8 dígitos → fijo chileno
  if (digitos.length === 8) {
    return `+56 ${digitos.substring(0, 1)} ${digitos.substring(1, 5)} ${digitos.substring(5)}`
  }
  // fallback: lo que escribió, cortado
  return '+' + digitos.substring(0, TELEFONO_DIGITOS_MAX)
}

function onInputTelefono(e) {
  // Limita los dígitos primero, luego formatea; el backend normaliza de todas formas.
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
  } catch {
    noExiste.value = true
  } finally {
    cargando.value = false
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

// Botón de Whatsapp flotante "¿Dudas? Habla con el dueño"
const whatsappUrl = computed(() => {
  const tel = negocio.value?.whatsapp
  if (!tel) return ''
  const texto = `Hola ${negocio.value?.nombre || ''}, quiero consultar sobre una reserva de cumpleaños`
  return `https://wa.me/${tel}?text=${encodeURIComponent(texto)}`
})

// Recargar al cambiar de slug (navegación entre negocios) o al volver a entrar.
watch(slug, async () => {
  if (slug.value) {
    paso.value = 1
    seleccion.value = { servicioId: null, bloqueId: null }
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
  // Vibración al llegar con pago exitoso desde Mercado Pago
  if (route.query.pago === 'exito') haptics.success()
})

// Vibrar al completar la solicitud
watch(exito, (v) => { if (v) haptics.celebrate() })

// Stepper siguiente/atrás (no envía el form salvo en el paso 3)
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

// Acción de la barra inferior: en pasos 1-2 avanza, en paso 3 envía el formulario
function accionBarra() {
  haptics.tap()
  if (paso.value < 3) siguientePaso()
  else document.getElementById('form-reserva')?.requestSubmit()
}

// Texto y enable de la barra inferior según el paso
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

      <!-- Mensaje de retorno Mercado Pago / Khipu -->
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

      <!-- éxito -->
      <div v-if="exito" class="glass-card rounded-3xl shadow-lifted p-8 text-center space-y-3 relative overflow-hidden">
        <img
          :src="charCake"
          alt=""
          class="w-24 h-24 object-contain mx-auto drop-shadow-xl character-img animate-floating"
          aria-hidden="true"
        />
        <h2 class="font-display font-extrabold text-2xl text-primary">¡Listo! Le pedimos precio al salón</h2>
        <p class="font-medium text-on-surface-variant text-sm">
          Le avisamos a <strong>{{ negocio.nombre }}</strong> que quieres celebrar ahí. Te van a escribir pronto para
          confirmar el precio y la fecha. Tu fecha queda reservada por 48 horas.
        </p>
      </div>

      <template v-else>
        <!-- Barra de progreso (Wizard) -->
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
          <!-- barra horizontal de progreso -->
          <div class="h-1.5 rounded-full bg-surface-container mt-2 overflow-hidden">
            <div
              class="h-full bg-primary rounded-full transition-all duration-300"
              :style="{ width: `${(paso / 3) * 100}%` }"
            ></div>
          </div>
        </nav>

        <!-- Paso 1: Elige la fiesta -->
        <section v-show="paso === 1" class="space-y-3">
          <ul class="grid gap-3 sm:grid-cols-2">
            <li v-for="s in negocio.servicios" :key="s.id">
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
        </section>

        <!-- Paso 2: Calendario térmico -->
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

          <!-- Esqueleto de carga del calendario -->
          <div v-if="cargandoBloques" class="space-y-2">
            <div class="grid grid-cols-7 gap-1">
              <span v-for="n in 35" :key="n" class="aspect-square rounded-lg bg-[#e0e0e0] animate-pulse" />
            </div>
          </div>

          <!-- Calendario térmico -->
          <div v-else class="space-y-2">
            <!-- Días de la semana -->
            <div class="grid grid-cols-7 gap-1">
              <span v-for="d in diasSemana" :key="d"
                class="text-center text-[11px] font-extrabold text-on-surface-variant py-1">{{ d }}</span>
            </div>
            <!-- Grilla de días -->
            <div class="grid grid-cols-7 gap-1">
              <div v-for="(celda, i) in grillaMes" :key="i">
                <div v-if="!celda" class="aspect-square rounded-lg" />
                <button
                  v-else
                  class="w-full aspect-square rounded-lg flex flex-col items-center justify-center text-sm font-bold transition-all"
                  :class="[
                    colorTermico(celda.cupos, celda.pasada),
                    diaSeleccionado === celda.fecha ? 'ring-2 ring-primary ring-offset-1 scale-105 shadow-md' : '',
                    celda.cupos > 0 && !celda.pasada ? 'cursor-pointer active:scale-95' : ''
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
            <!-- Leyenda de colores -->
            <div class="flex items-center justify-center gap-3 text-[10px] font-semibold text-on-surface-variant pt-1">
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-[#bbf7d0]" /> Libre</span>
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-[#fde68a]" /> Pocos cupos</span>
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-[#fed7aa]" /> Últimos</span>
              <span class="flex items-center gap-1"><span class="w-3 h-3 rounded-sm bg-surface-highest" /> Lleno</span>
            </div>
          </div>

          <!-- Bloques de hora del día seleccionado -->
          <div v-if="diaSeleccionado && bloquesDelDia.length" class="space-y-2 pt-2">
            <p class="text-sm font-bold text-on-surface">
              {{ diaSeleccionado }} · elige la hora
            </p>
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

        <!-- Paso 3: Tus datos -->
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
            <label class="flex items-start gap-2 text-xs font-medium text-on-surface-variant sm:col-span-2">
              <input v-model="form.aceptaDatos" type="checkbox" required class="mt-0.5 w-4 h-4 accent-[#b5007d]" />
              <span
                >Autorizo al negocio a usar mis datos de contacto para gestionar esta solicitud. Ver
                <RouterLink to="/privacidad" class="text-secondary underline">política de privacidad</RouterLink>
                (Ley 21.719).</span
              >
            </label>
            <ErrorBanner :mensaje="error" class="sm:col-span-2" />
          </form>
        </section>
      </template>

      <footer class="text-center text-xs font-medium text-outline pt-2 pb-8">Hecho con ReservaKids</footer>
    </div>

    <!-- Botón flotante de ayuda por WhatsApp -->
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

    <!-- Sticky Bottom Bar (navegación del wizard / acción final) -->
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
