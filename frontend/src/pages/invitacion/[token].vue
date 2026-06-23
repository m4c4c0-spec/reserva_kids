<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const route = useRoute()
const token = route.params.token
const invitacion = ref(null)
const cargando = ref(true)
const error = ref('')
const comentarios = ref('')
const enviado = ref(false)
const accion = ref('')

const API = (import.meta.env.VITE_API_URL || import.meta.env.NUXT_PUBLIC_API_URL || '') + '/api'

async function cargar() {
  try {
    const { data } = await axios.get(`${API}/public/invitacion/${token}`)
    invitacion.value = data
  } catch {
    error.value = 'Invitación no encontrada o expirada'
  } finally {
    cargando.value = false
  }
}

async function responder(confirmar) {
  accion.value = confirmar ? 'confirmar' : 'rechazar'
  try {
    await axios.post(`${API}/public/invitacion/${token}/responder`, {
      confirmar,
      comentarios: comentarios.value || undefined,
    })
    enviado.value = true
  } catch {
    error.value = 'No se pudo enviar la respuesta. Intenta de nuevo.'
  } finally {
    accion.value = ''
  }
}

onMounted(cargar)

definePageMeta({ layout: 'default' })
</script>

<template>
  <main class="min-h-screen bg-surface flex flex-col items-center justify-center p-5 text-center">
    <LoadingSpinner v-if="cargando" />

    <div v-else-if="error && !enviado" class="max-w-md space-y-4">
      <span class="material-symbols-outlined text-5xl text-outline">error_outline</span>
      <p class="font-display font-bold text-xl text-on-surface">{{ error }}</p>
      <BaseButton variante="primario" @click="navigateTo('/')">Ir al inicio</BaseButton>
    </div>

    <div v-else-if="enviado" class="max-w-md space-y-4">
      <span class="material-symbols-outlined text-5xl text-primary">celebration</span>
      <p class="font-display font-bold text-2xl text-on-surface">¡Gracias!</p>
      <p class="font-medium text-on-surface-variant">Tu respuesta fue registrada.</p>
      <BaseButton variante="primario" @click="navigateTo('/')">Ir al inicio</BaseButton>
    </div>

    <div v-else-if="invitacion" class="max-w-md space-y-6 bg-surface-lowest rounded-3xl shadow-lifted p-8 border border-outline-variant/20">
      <div>
        <h1 class="font-display font-extrabold text-2xl text-on-surface">{{ invitacion.nombreNegocio }}</h1>
        <p class="font-medium text-on-surface-variant mt-1">Te invita a su celebración</p>
      </div>

      <div class="bg-surface-container rounded-2xl p-4 text-left space-y-2 text-sm font-medium text-on-surface-variant">
        <p><strong class="text-on-surface">Fecha:</strong> {{ invitacion.fecha }}</p>
        <p v-if="invitacion.hora"><strong class="text-on-surface">Hora:</strong> {{ invitacion.hora }}</p>
        <p v-if="invitacion.direccion"><strong class="text-on-surface">Dirección:</strong> {{ invitacion.direccion }}</p>
      </div>

      <div>
        <label for="comentarios" class="block text-xs font-bold text-on-surface-variant mb-1">Comentarios (opcional)</label>
        <textarea id="comentarios" v-model="comentarios" rows="3" maxlength="500" placeholder="Alergias, transporte, etc." class="input-festivo" />
      </div>

      <div class="flex gap-3">
        <BaseButton variante="secundario" class="flex-1" :deshabilitado="!!accion" @click="responder(false)">
          No puedo
        </BaseButton>
        <BaseButton variante="primario" class="flex-1" :cargando="accion === 'confirmar'" :deshabilitado="!!accion" @click="responder(true)">
          ¡Voy!
        </BaseButton>
      </div>
    </div>
  </main>
</template>
