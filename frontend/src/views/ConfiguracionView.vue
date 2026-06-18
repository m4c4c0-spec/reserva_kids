<script setup>
import { ref } from 'vue'
import api from '../api/client'

const token = ref('')
const webhookSecret = ref('') // S3: secreto de firma del webhook (opcional)
const guardado = ref(false)
const error = ref('')

async function guardarToken() {
  try {
    error.value = ''
    await api.put('/tenant/configuracion', {
      mpAccessToken: token.value,
      // se manda solo si el dueño lo escribió: vacío no borra el secreto ya guardado
      mpWebhookSecret: webhookSecret.value || null,
    })
    guardado.value = true
    setTimeout(() => { guardado.value = false }, 3000)
  } catch (e) {
    error.value = e.response?.data?.message || 'Error al guardar el token'
  }
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <h2 class="font-display font-bold text-2xl md:text-3xl text-on-surface">Configuración</h2>
      <p class="font-medium text-on-surface-variant text-sm mt-0.5">Ajusta los parámetros técnicos de tu negocio.</p>
    </div>

    <div class="bg-surface-lowest p-6 rounded-3xl shadow-soft border border-outline-variant/20 max-w-2xl">
      <h3 class="font-display font-bold text-on-surface mb-2">Integración con Mercado Pago</h3>
      <p class="font-medium text-on-surface-variant text-sm mb-4">
        Para que tus clientes puedan pagar las señas de las reservas online, necesitamos conectarnos con tu cuenta de Mercado Pago.
        <br>
        Ingresa a <strong>Tus Integraciones > Credenciales de Producción</strong> en Mercado Pago y copia tu <em>Access Token</em>.
      </p>

      <form @submit.prevent="guardarToken" class="space-y-4">
        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">Access Token (Producción)</label>
          <div class="relative">
            <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">key</span>
            <input v-model="token" type="password" required placeholder="APP_USR-..."
                   class="w-full pl-11 pr-3 py-2.5 border-2 border-surface-highest bg-surface rounded-xl text-sm font-medium placeholder-outline focus:outline-none focus:border-secondary transition-colors">
          </div>
        </div>

        <div>
          <label class="block text-sm font-bold text-on-surface mb-1">
            Secreto de firma del webhook <span class="font-medium text-on-surface-variant">(opcional)</span>
          </label>
          <p class="text-xs font-medium text-on-surface-variant mb-1">
            En Mercado Pago: <strong>Tus Integraciones > Webhooks > Firma secreta</strong>.
            Si lo configuras, validamos que las notificaciones de pago vengan realmente de MP.
          </p>
          <div class="relative">
            <span class="material-symbols-outlined absolute inset-y-0 left-3 flex items-center text-on-surface-variant pointer-events-none">verified_user</span>
            <input v-model="webhookSecret" type="password" placeholder="Déjalo vacío para no cambiarlo"
                   class="w-full pl-11 pr-3 py-2.5 border-2 border-surface-highest bg-surface rounded-xl text-sm font-medium placeholder-outline focus:outline-none focus:border-secondary transition-colors">
          </div>
        </div>

        <div v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2">{{ error }}</div>
        <div v-if="guardado" class="text-sm font-semibold text-green-800 bg-[#dcfce7] rounded-xl px-3 py-2">Token guardado correctamente 🎉</div>

        <button type="submit"
                class="bg-primary-container text-on-primary-container px-5 py-2.5 rounded-full text-sm font-bold shadow-md hover:bg-primary-fixed hover:shadow-lifted active:scale-95 transition-all">
          Guardar Configuración
        </button>
      </form>
    </div>
  </div>
</template>
