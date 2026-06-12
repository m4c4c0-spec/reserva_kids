<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'

const route = useRoute()
const slug = route.params.slug
const baseURL = (import.meta.env.VITE_API_URL || 'http://localhost:8080') + '/api'

const negocio = ref(null)
const noExiste = ref(false)
const bloques = ref([])
const hoy = new Date()
const mes = ref(`${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}`)

const seleccion = ref({ servicioId: null, bloqueId: null })
// aceptaDatos: consentimiento expreso Ley 21.719 — el backend lo exige (@AssertTrue)
const form = ref({ nombreContacto: '', telefono: '', email: '', numNinos: null, comuna: '', comentarios: '', aceptaDatos: false })
const enviando = ref(false)
const error = ref('')
const exito = ref(null)

async function cargarNegocio() {
  try {
    const { data } = await axios.get(`${baseURL}/public/${slug}`)
    negocio.value = data
  } catch {
    noExiste.value = true
  }
}

async function cargarDisponibilidad() {
  const { data } = await axios.get(`${baseURL}/public/${slug}/disponibilidad`, { params: { mes: mes.value } })
  bloques.value = data
}

async function enviar() {
  error.value = ''
  enviando.value = true
  try {
    const { data } = await axios.post(`${baseURL}/public/${slug}/reservas`, {
      ...seleccion.value,
      ...form.value,
    })
    exito.value = data
  } catch (e) {
    error.value = e.response?.data?.message || 'No se pudo enviar la solicitud'
    if (e.response?.status === 409) await cargarDisponibilidad() // el bloque ya fue tomado
  } finally {
    enviando.value = false
  }
}

const clp = (n) => n?.toLocaleString('es-CL', { style: 'currency', currency: 'CLP' })

onMounted(async () => {
  await cargarNegocio()
  if (negocio.value) await cargarDisponibilidad()
})
</script>

<template>
  <main class="min-h-screen bg-violet-50">
    <div v-if="noExiste" class="p-10 text-center text-gray-500">Negocio no encontrado 😕</div>

    <div v-else-if="negocio" class="max-w-2xl mx-auto p-4 space-y-6">
      <header class="text-center py-6">
        <h1 class="text-3xl font-bold text-violet-700">🎈 {{ negocio.nombre }}</h1>
        <p class="text-gray-500">Cotiza y reserva tu cumpleaños en minutos</p>
      </header>

      <!-- éxito -->
      <div v-if="exito" class="bg-white rounded-2xl shadow p-6 text-center space-y-2">
        <p class="text-4xl">🎉</p>
        <h2 class="text-xl font-bold">¡Solicitud #{{ exito.id }} enviada!</h2>
        <p class="text-gray-600 text-sm">
          El negocio recibió tu solicitud y te contactará pronto con la cotización.
          Tu fecha queda en espera por 48 horas.
        </p>
      </div>

      <template v-else>
        <!-- 1. Catálogo (RF-03) -->
        <section class="space-y-3">
          <h2 class="font-bold text-lg">1. Elige un servicio</h2>
          <ul class="grid gap-3 sm:grid-cols-2">
            <li v-for="s in negocio.servicios" :key="s.id">
              <button @click="seleccion.servicioId = s.id"
                      class="w-full text-left bg-white rounded-xl shadow p-4 border-2 transition"
                      :class="seleccion.servicioId === s.id ? 'border-violet-500' : 'border-transparent'">
                <div class="flex justify-between">
                  <h3 class="font-semibold">{{ s.nombre }}</h3>
                  <span class="text-violet-700 font-bold">{{ clp(s.precioClp) }}</span>
                </div>
                <p class="text-sm text-gray-500">{{ s.descripcion }}</p>
                <p class="text-xs text-gray-400">
                  <span v-if="s.duracionMin">{{ s.duracionMin }} min</span>
                  <span v-if="s.capacidad"> · hasta {{ s.capacidad }} niños</span>
                </p>
              </button>
            </li>
          </ul>
        </section>

        <!-- 2. Fecha (RF-04) -->
        <section class="space-y-3">
          <div class="flex items-center justify-between">
            <h2 class="font-bold text-lg">2. Elige fecha y hora</h2>
            <input v-model="mes" type="month" @change="cargarDisponibilidad"
                   class="border rounded-lg px-2 py-1 text-sm bg-white" />
          </div>
          <p v-if="!bloques.length" class="text-sm text-gray-500">Sin horarios disponibles este mes — prueba el siguiente.</p>
          <div class="flex flex-wrap gap-2">
            <button v-for="b in bloques" :key="b.id" @click="seleccion.bloqueId = b.id"
                    class="bg-white rounded-lg shadow px-3 py-2 text-sm border-2 transition"
                    :class="seleccion.bloqueId === b.id ? 'border-violet-500' : 'border-transparent'">
              {{ b.fecha }} · {{ b.horaInicio.slice(0, 5) }}
            </button>
          </div>
        </section>

        <!-- 3. Formulario (RF-05) -->
        <section v-if="seleccion.servicioId && seleccion.bloqueId" class="space-y-3">
          <h2 class="font-bold text-lg">3. Tus datos</h2>
          <form @submit.prevent="enviar" class="bg-white rounded-2xl shadow p-4 grid gap-3 sm:grid-cols-2">
            <input v-model="form.nombreContacto" required maxlength="120" placeholder="Tu nombre"
                   class="border rounded-lg px-3 py-2" />
            <input v-model="form.telefono" required maxlength="30" placeholder="Teléfono (WhatsApp)"
                   class="border rounded-lg px-3 py-2" />
            <input v-model="form.email" type="email" placeholder="Email (opcional)"
                   class="border rounded-lg px-3 py-2" />
            <input v-model.number="form.numNinos" type="number" min="1" placeholder="Nº de niños"
                   class="border rounded-lg px-3 py-2" />
            <input v-model="form.comuna" maxlength="80" placeholder="Comuna"
                   class="border rounded-lg px-3 py-2 sm:col-span-2" />
            <textarea v-model="form.comentarios" maxlength="2000" rows="3"
                      placeholder="Comentarios (tema del cumpleaños, dirección, etc.)"
                      class="border rounded-lg px-3 py-2 sm:col-span-2"></textarea>
            <!-- Ley 21.719: consentimiento expreso del titular (falla #4, revisión 2 años) -->
            <label class="flex items-start gap-2 text-xs text-gray-500 sm:col-span-2">
              <input v-model="form.aceptaDatos" type="checkbox" required class="mt-0.5 accent-violet-600" />
              <span>Autorizo al negocio a usar mis datos de contacto para gestionar esta solicitud
                (Ley 21.719). Puedes pedir su eliminación cuando quieras.</span>
            </label>
            <p v-if="error" class="text-sm text-red-600 sm:col-span-2">{{ error }}</p>
            <button :disabled="enviando"
                    class="sm:col-span-2 bg-violet-600 hover:bg-violet-700 text-white rounded-lg py-3 font-semibold disabled:opacity-50">
              {{ enviando ? 'Enviando…' : 'Solicitar cotización 🎉' }}
            </button>
          </form>
        </section>
      </template>
    </div>
  </main>
</template>
