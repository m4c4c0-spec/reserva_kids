<script setup>
import { ref, onMounted } from 'vue'
import * as configuracionService from '../services/configuracionService'
import BaseButton from '../components/BaseButton.vue'
import BaseInput from '../components/BaseInput.vue'
import BaseToast from '../components/BaseToast.vue'
import ErrorBanner from '../components/ErrorBanner.vue'
import LoadingSpinner from '../components/LoadingSpinner.vue'

const token = ref('')
const webhookSecret = ref('')
const toastMp = ref('')
const errorMp = ref('')

const diasSemana = [
  { value: 1, label: 'Lunes' },
  { value: 2, label: 'Martes' },
  { value: 3, label: 'Miércoles' },
  { value: 4, label: 'Jueves' },
  { value: 5, label: 'Viernes' },
  { value: 6, label: 'Sábado' },
  { value: 7, label: 'Domingo' },
]
const horario = ref({ intervaloMin: 30, franjas: [] })
const cargandoHorario = ref(true)
const toastHorario = ref('')
const errorHorario = ref('')

onMounted(async () => {
  try {
    const data = await configuracionService.cargarHorario()
    horario.value = data
  } catch (e) {
    errorHorario.value = e.response?.data?.message || 'No se pudo cargar el horario'
  } finally {
    cargandoHorario.value = false
  }
})

async function guardarToken() {
  errorMp.value = ''
  try {
    await configuracionService.guardarConfig({
      mpAccessToken: token.value,
      mpWebhookSecret: webhookSecret.value || null,
    })
    toastMp.value = 'Token guardado correctamente'
  } catch (e) {
    errorMp.value = e.response?.data?.message || 'Error al guardar el token'
  }
}

function agregarFranja() {
  horario.value.franjas.push({ diaSemana: 1, horaApertura: '09:00', horaCierre: '18:00' })
}

function eliminarFranja(index) {
  horario.value.franjas.splice(index, 1)
}

async function guardarHorario() {
  errorHorario.value = ''
  try {
    await configuracionService.guardarHorario(horario.value)
    toastHorario.value = 'Horario guardado correctamente'
  } catch (e) {
    errorHorario.value = e.response?.data?.message || 'Error al guardar el horario'
  }
}
</script>

<template>
  <div class="space-y-8">
    <div>
      <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Configuración</h2>
      <p class="font-medium text-on-surface-variant text-sm mt-0.5">Ajusta los parámetros técnicos de tu negocio.</p>
    </div>

    <!-- Mercado Pago -->
    <div class="card-festiva !p-6 max-w-2xl">
      <h3 class="font-display font-bold text-on-surface mb-2">Integración con Mercado Pago</h3>
      <p class="font-medium text-on-surface-variant text-sm mb-4">
        Para que tus clientes puedan pagar las señas de las reservas online, necesitamos conectarnos con tu cuenta de
        Mercado Pago.
        <br />
        Ingresa a <strong>Tus Integraciones > Credenciales de Producción</strong> en Mercado Pago y copia tu
        <em>Access Token</em>.
      </p>

      <form class="space-y-4" @submit.prevent="guardarToken">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Access Token (Producción)</label>
          <BaseInput v-model="token" tipo="password" requerido placeholder="APP_USR-..." icono="key" />
        </div>

        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">
            Secreto de firma del webhook <span class="font-medium text-on-surface-variant">(opcional)</span>
          </label>
          <p class="text-xs font-medium text-on-surface-variant mb-1">
            En Mercado Pago: <strong>Tus Integraciones > Webhooks > Firma secreta</strong>. Si lo configuras, validamos
            que las notificaciones de pago vengan realmente de MP.
          </p>
          <BaseInput
            v-model="webhookSecret"
            tipo="password"
            placeholder="Déjalo vacío para no cambiarlo"
            icono="verified_user"
          />
        </div>

        <ErrorBanner :mensaje="errorMp" />
        <BaseToast :mensaje="toastMp" tipo="exito" @cerrar="toastMp = ''" />

        <BaseButton variante="primario" type="submit">Guardar Configuración</BaseButton>
      </form>
    </div>

    <!-- Horario de atención -->
    <div class="card-festiva !p-6 max-w-2xl">
      <h3 class="font-display font-bold text-on-surface mb-2">Horario de atención</h3>
      <p class="font-medium text-on-surface-variant text-sm mb-4">
        Define cuándo los clientes pueden agendar citas por hora y cada cuántos minutos se ofrecen los slots.
      </p>

      <LoadingSpinner v-if="cargandoHorario" mensaje="Cargando horario…" />
      <form v-else class="space-y-4" @submit.prevent="guardarHorario">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Intervalo entre horas (minutos)</label>
          <input
            v-model.number="horario.intervaloMin"
            type="number"
            min="5"
            max="120"
            required
            class="input-festivo !w-32"
          />
          <p class="text-xs font-medium text-on-surface-variant mt-1">Ej: 30 ofrece horas a las 09:00, 09:30, 10:00…</p>
        </div>

        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <label class="block text-sm font-bold text-on-surface">Franjas semanales</label>
            <button
              type="button"
              class="text-sm font-bold text-secondary hover:text-secondary-container flex items-center gap-1"
              @click="agregarFranja"
            >
              <span class="material-symbols-outlined text-[18px]">add</span> Agregar franja
            </button>
          </div>

          <div
            v-for="(f, i) in horario.franjas"
            :key="i"
            class="flex flex-wrap items-end gap-2 bg-surface p-3 rounded-2xl border border-outline-variant/20"
          >
            <select v-model.number="f.diaSemana" required class="input-festivo">
              <option v-for="d in diasSemana" :key="d.value" :value="d.value">{{ d.label }}</option>
            </select>
            <input v-model="f.horaApertura" type="time" required class="input-festivo" />
            <span class="text-on-surface-variant font-bold">a</span>
            <input v-model="f.horaCierre" type="time" required class="input-festivo" />
            <button
              type="button"
              class="text-error hover:bg-error-container rounded-full w-8 h-8 flex items-center justify-center transition-colors"
              @click="eliminarFranja(i)"
            >
              <span class="material-symbols-outlined text-[18px]">close</span>
            </button>
          </div>

          <p
            v-if="!horario.franjas.length"
            class="text-sm font-medium text-on-surface-variant bg-surface-container rounded-xl px-4 py-3"
          >
            Sin franjas configuradas. Agrega al menos una para que aparezcas en el directorio de clientes.
          </p>
        </div>

        <ErrorBanner :mensaje="errorHorario" />
        <BaseToast :mensaje="toastHorario" tipo="exito" @cerrar="toastHorario = ''" />

        <BaseButton variante="primario" type="submit" :deshabilitado="!horario.franjas.length">
          Guardar Horario
        </BaseButton>
      </form>
    </div>
  </div>
</template>
