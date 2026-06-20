<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import * as negocioService from '../services/negocioService'
import { clp } from '../composables/useCurrency'
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

const seleccion = ref({ servicioId: null, bloqueId: null })
const form = ref({
  nombreContacto: '',
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

async function cargarDisponibilidad() {
  if (!negocio.value) return
  bloques.value = await negocioService.disponibilidad(slug.value, mes.value)
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
    error.value = e.response?.data?.message || 'No se pudo enviar la solicitud'
    if (e.response?.status === 409) await cargarDisponibilidad()
  } finally {
    enviando.value = false
  }
}

// Recargar al cambiar de slug (navegación entre negocios) o al volver a entrar.
watch(slug, async () => {
  if (slug.value) {
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
})
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

    <div v-else-if="negocio" class="max-w-2xl mx-auto p-5 space-y-6">
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

      <!-- Mensaje de retorno Mercado Pago -->
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
        class="bg-error-container text-on-error-container font-bold p-4 rounded-2xl text-center shadow-soft"
      >
        El pago no se pudo procesar o fue cancelado. Por favor, intenta nuevamente más tarde.
      </div>

      <!-- éxito -->
      <div v-if="exito" class="glass-card rounded-3xl shadow-lifted p-8 text-center space-y-3 relative overflow-hidden">
        <img
          :src="charCake"
          alt=""
          class="w-24 h-24 object-contain mx-auto drop-shadow-xl character-img animate-floating"
          aria-hidden="true"
        />
        <h2 class="font-display font-extrabold text-2xl text-primary">¡Solicitud #{{ exito.id }} enviada!</h2>
        <p class="font-medium text-on-surface-variant text-sm">
          El negocio recibió tu solicitud y te contactará pronto con la cotización. Tu fecha queda en espera por 48
          horas.
        </p>
      </div>

      <template v-else>
        <!-- 1. Catálogo -->
        <section class="space-y-3">
          <h2 class="font-display font-bold text-lg flex items-center gap-2 text-on-surface">
            <span
              class="w-7 h-7 rounded-full bg-primary-container text-on-primary-container text-sm font-bold flex items-center justify-center shrink-0"
              >1</span
            >
            Elige un servicio
          </h2>
          <ul class="grid gap-3 sm:grid-cols-2">
            <li v-for="s in negocio.servicios" :key="s.id">
              <button
                class="w-full text-left bg-surface-lowest rounded-3xl shadow-soft p-5 border-2 transition-all hover:shadow-lifted hover:-translate-y-0.5"
                :class="
                  seleccion.servicioId === s.id ? 'border-primary ring-4 ring-primary-fixed' : 'border-transparent'
                "
                @click="seleccion.servicioId = s.id"
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

        <!-- 2. Fecha -->
        <section class="space-y-3">
          <div class="flex items-center justify-between gap-2">
            <h2 class="font-display font-bold text-lg flex items-center gap-2 text-on-surface">
              <span
                class="w-7 h-7 rounded-full bg-primary-container text-on-primary-container text-sm font-bold flex items-center justify-center shrink-0"
                >2</span
              >
              Elige fecha y hora
            </h2>
            <input
              v-model="mes"
              type="month"
              aria-label="Mes"
              class="bg-surface-high rounded-full px-4 py-2 text-sm font-bold text-on-surface border-none focus:outline-none focus:ring-2 focus:ring-primary-container cursor-pointer"
              @change="cargarDisponibilidad"
            />
          </div>
          <p
            v-if="!bloques.length"
            class="font-medium text-on-surface-variant text-sm bg-surface-container rounded-2xl px-4 py-6 text-center"
          >
            Sin horarios disponibles este mes — prueba el siguiente.
          </p>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="b in bloques"
              :key="b.id"
              class="chip-festivo shadow-soft hover:shadow-lifted"
              :class="
                seleccion.bloqueId === b.id
                  ? 'bg-secondary-container text-on-primary border-secondary-container'
                  : 'bg-surface-lowest text-on-surface border-transparent'
              "
              @click="seleccion.bloqueId = b.id"
            >
              {{ b.fecha }} · {{ b.horaInicio.slice(0, 5) }}
            </button>
          </div>
        </section>

        <!-- 3. Formulario -->
        <section v-if="seleccion.servicioId && seleccion.bloqueId" class="space-y-3">
          <h2 class="font-display font-bold text-lg flex items-center gap-2 text-on-surface">
            <span
              class="w-7 h-7 rounded-full bg-primary-container text-on-primary-container text-sm font-bold flex items-center justify-center shrink-0"
              >3</span
            >
            Tus datos
          </h2>
          <p
            v-if="clienteAuth.autenticado"
            class="flex items-center gap-2 text-sm font-semibold text-on-secondary-container bg-secondary-fixed rounded-2xl px-4 py-2"
          >
            <span class="material-symbols-outlined text-[18px]">badge</span>
            Reservando como <strong>{{ clienteAuth.nombre || clienteAuth.email }}</strong> — completa tu teléfono
          </p>
          <form class="card-festiva grid gap-3 sm:grid-cols-2" @submit.prevent="enviar">
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
              <label for="f-telefono" class="block text-xs font-bold text-on-surface-variant mb-1"
                >Teléfono (WhatsApp)</label
              >
              <input
                id="f-telefono"
                v-model="form.telefono"
                required
                maxlength="30"
                placeholder="Teléfono (WhatsApp)"
                class="input-festivo"
              />
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
                >Autorizo al negocio a usar mis datos de contacto para gestionar esta solicitud (Ley 21.719). Puedes
                pedir su eliminación cuando quieras.</span
              >
            </label>
            <ErrorBanner :mensaje="error" class="sm:col-span-2" />
            <BaseButton
              variante="primario"
              type="submit"
              :cargando="enviando"
              :deshabilitado="enviando"
              class="sm:col-span-2 py-3"
            >
              {{ enviando ? 'Enviando…' : 'Solicitar cotización' }}
            </BaseButton>
          </form>
        </section>
      </template>

      <footer class="text-center text-xs font-medium text-outline pt-2 pb-8">Hecho con ReservaKids</footer>
    </div>
  </main>
</template>
