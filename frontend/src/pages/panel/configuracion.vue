<script setup>
definePageMeta({ layout: 'panel', requiereAuth: true })

import { ref, onMounted } from 'vue'
import * as configuracionService from '../../services/configuracionService'
import BaseButton from '../../components/BaseButton.vue'
import BaseInput from '../../components/BaseInput.vue'
import BaseToast from '../../components/BaseToast.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'

const pasarela = ref('MERCADOPAGO')
const token = ref('')
const webhookSecret = ref('')
const khipuApiKey = ref('')
const khipuReceiverId = ref(null)
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

const telefonoContacto = ref('')
const toastContacto = ref('')
const errorContacto = ref('')

const colorPrimario = ref('#b5007d')
const tituloPagina = ref('')
const metaPixelId = ref('')
const politicasCancelacion = ref('')
const toastMarca = ref('')
const errorMarca = ref('')

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
    await configuracionService.guardarPasarela({
      pasarelaPago: pasarela.value,
      mpAccessToken: pasarela.value === 'MERCADOPAGO' ? token.value : null,
      mpWebhookSecret: pasarela.value === 'MERCADOPAGO' ? webhookSecret.value || null : null,
      khipuApiKey: pasarela.value === 'KHIPU' ? khipuApiKey.value : null,
      khipuReceiverId: pasarela.value === 'KHIPU' ? khipuReceiverId.value || null : null,
    })
    toastMp.value = 'Configuración guardada correctamente'
  } catch (e) {
    errorMp.value = e.response?.data?.message || 'Error al guardar la configuración'
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

async function guardarContactoBtn() {
  errorContacto.value = ''
  try {
    await configuracionService.guardarContacto(telefonoContacto.value || null)
    toastContacto.value = 'Teléfono guardado correctamente'
  } catch (e) {
    errorContacto.value = e.response?.data?.message || 'Error al guardar el teléfono'
  }
}

async function guardarMarca() {
  errorMarca.value = ''
  try {
    await configuracionService.guardarPasarela({
      pasarelaPago: pasarela.value,
      mpAccessToken: null,
      mpWebhookSecret: null,
      khipuApiKey: null,
      khipuReceiverId: null,
      colorPrimario: colorPrimario.value,
      tituloPagina: tituloPagina.value || null,
      metaPixelId: metaPixelId.value || null,
      politicasCancelacion: politicasCancelacion.value || null,
    })
    toastMarca.value = 'Marca guardada correctamente'
  } catch (e) {
    errorMarca.value = e.response?.data?.message || 'Error al guardar la marca'
  }
}
</script>

<template>
  <div class="space-y-8">
    <div>
      <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Configuración</h2>
      <p class="font-medium text-on-surface-variant text-sm mt-0.5">Ajusta los parámetros técnicos de tu negocio.</p>
    </div>

    <div class="card-festiva !p-6 max-w-2xl">
      <h3 class="font-display font-bold text-on-surface mb-2">Pago en línea de las señas</h3>
      <p class="font-medium text-on-surface-variant text-sm mb-4">
        Para que tus clientes paguen la seña online. Mercado Pago acepta tarjetas pero cobra comisión porcentual alta;
        Khipu cobra por transferencia bancaria directa (CuentaRUT incluida), con comisión más baja en regiones.
      </p>

      <form class="space-y-4" @submit.prevent="guardarToken">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Pasarela</label>
          <select v-model="pasarela" class="input-festivo">
            <option value="MERCADOPAGO">Mercado Pago (tarjetas)</option>
            <option value="KHIPU">Khipu (transferencias)</option>
          </select>
        </div>

        <div v-if="pasarela === 'MERCADOPAGO'" class="space-y-4">
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1">Access Token (Producción)</label>
            <BaseInput v-model="token" tipo="password" requerido placeholder="APP_USR-..." icono="key" />
          </div>
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1">
              Secreto de firma del webhook <span class="font-medium text-on-surface-variant">(opcional)</span>
            </label>
            <p class="text-xs font-medium text-on-surface-variant mb-1">
              En Mercado Pago: <strong>Tus Integraciones > Webhooks > Firma secreta</strong>. Si lo configuras,
              validamos que las notificaciones de pago vengan realmente de MP.
            </p>
            <BaseInput
              v-model="webhookSecret"
              tipo="password"
              placeholder="Déjalo vacío para no cambiarlo"
              icono="verified_user"
            />
          </div>
        </div>

        <div v-else class="space-y-4">
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1">API key de Khipu</label>
            <p class="text-xs font-medium text-on-surface-variant mb-1">
              En tu cuenta de cobro de Khipu:
              <strong>Opciones de la cuenta > Para integrar Khipu a tu sitio web</strong>. La misma clave se usa para
              crear el cobro y para validar la notificación de pago.
            </p>
            <BaseInput
              v-model="khipuApiKey"
              tipo="password"
              requerido
              placeholder="p. ej. 1a4cbbbeb8bdb7e1d735..."
              icono="key"
            />
          </div>
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1">
              Id de cobrador <span class="font-medium text-on-surface-variant">(opcional)</span>
            </label>
            <BaseInput
              v-model.number="khipuReceiverId"
              tipo="number"
              placeholder="Aparece en tu cuenta de cobro de Khipu"
              icono="badge"
            />
          </div>
          <p class="text-xs font-medium text-on-surface-variant bg-surface-container rounded-xl px-3 py-2">
            El cobro se crea con la URL de notificación ya configurada — no necesitas habilitar nada extra en Khipu. La
            notificación de pago se valida con HMAC y se confirma la reserva automáticamente al conciliarse.
          </p>
        </div>

        <ErrorBanner :mensaje="errorMp" />
        <BaseToast :mensaje="toastMp" tipo="exito" @cerrar="toastMp = ''" />

        <BaseButton variante="primario" type="submit">Guardar Configuración</BaseButton>
      </form>
    </div>

    <div class="card-festiva !p-6 max-w-2xl">
      <h3 class="font-display font-bold text-on-surface mb-2">Botón de ayuda por WhatsApp</h3>
      <p class="font-medium text-on-surface-variant text-sm mb-4">
        Si un padre o abuelo se traba reservando o pagando la seña, verá un botón flotante en tu página pública que abre
        una conversación contigo. Escribe acá el teléfono al que quieres que los contacten.
      </p>

      <form class="space-y-4" @submit.prevent="guardarContactoBtn">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Teléfono de WhatsApp del salón</label>
          <BaseInput v-model="telefonoContacto" tipo="tel" placeholder="+56 9 1234 5678" icono="support_agent" />
          <p class="text-xs font-medium text-on-surface-variant mt-1">
            Déjalo vacío para ocultar el botón. Lo guardamos normalizado (569…).
          </p>
        </div>

        <ErrorBanner :mensaje="errorContacto" />
        <BaseToast :mensaje="toastContacto" tipo="exito" @cerrar="toastContacto = ''" />

        <BaseButton variante="primario" type="submit">Guardar teléfono</BaseButton>
      </form>
    </div>

    <div class="card-festiva !p-6 max-w-2xl">
      <h3 class="font-display font-bold text-on-surface mb-2">Marca blanca de tu salón</h3>
      <p class="font-medium text-on-surface-variant text-sm mb-4">
        Personaliza los colores, el título de la pestaña del navegador y el píxel de Meta para tus anuncios. Tus
        clientes verán el nombre de tu salón en lugar de "ReservaKids".
      </p>

      <form class="space-y-4" @submit.prevent="guardarMarca">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Color principal</label>
          <div class="flex items-center gap-3">
            <input
              v-model="colorPrimario"
              type="color"
              class="w-12 h-10 rounded-lg border-2 border-outline-variant/30 cursor-pointer"
            />
            <input
              v-model="colorPrimario"
              maxlength="7"
              placeholder="#b5007d"
              class="input-festivo !w-28 font-mono"
              pattern="#[0-9a-fA-F]{6}"
            />
            <span
              class="w-10 h-10 rounded-full border-2 border-outline-variant/20 shadow-sm"
              :style="{ backgroundColor: colorPrimario }"
            ></span>
          </div>
          <p class="text-xs font-medium text-on-surface-variant mt-1">
            Formato hex: #rrggbb. Se aplica a botones, títulos y acentos.
          </p>
        </div>

        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Título de la pestaña</label>
          <BaseInput v-model="tituloPagina" placeholder="Salón Fantasía - Reservas" maxlength="120" icono="tab" />
          <p class="text-xs font-medium text-on-surface-variant mt-1">
            Déjalo vacío para mostrar "ReservaKids". Aparece en la pestaña del navegador y en los resultados de Google.
          </p>
        </div>

        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Meta Pixel ID (Facebook/Instagram)</label>
          <BaseInput v-model="metaPixelId" placeholder="1234567890123456" maxlength="50" icono="ads_click" />
          <p class="text-xs font-medium text-on-surface-variant mt-1">
            Solo números. Lo encuentras en Meta Business Suite &gt; Fuentes de datos &gt; Píxeles. Déjalo vacío para no
            instalar el píxel. Mide conversiones de tus anuncios.
          </p>
        </div>

        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Políticas de cancelación</label>
          <textarea
            v-model="politicasCancelacion"
            maxlength="5000"
            rows="4"
            placeholder="Ej: Si cancelas con menos de 48h de anticipación, la seña no será reembolsada. Cambios de fecha sin costo hasta 7 días antes."
            class="input-festivo"
          ></textarea>
          <p class="text-xs font-medium text-on-surface-variant mt-1">
            Se muestra antes de reservar. Déjalo vacío para no mostrar políticas. Protege a tu negocio legalmente (Ley
            19.496 y SERNAC).
          </p>
        </div>

        <ErrorBanner :mensaje="errorMarca" />
        <BaseToast :mensaje="toastMarca" tipo="exito" @cerrar="toastMarca = ''" />

        <BaseButton variante="primario" type="submit">Guardar marca</BaseButton>
      </form>
    </div>

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
