<script setup>
import { ref } from 'vue'
import axios from 'axios'

const props = defineProps({
  type: { type: String, required: true }, // 'dueno' | 'cliente' | 'admin'
})

const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'
const cargando = ref(false)

async function iniciarGoogle() {
  cargando.value = true
  try {
    const prefix = props.type === 'dueno' ? 'auth' : props.type === 'admin' ? 'admin-auth' : 'cliente-auth'
    const { data } = await axios.get(`${baseURL}/${prefix}/oauth2/google/authorize?type=${props.type}`)
    if (data.authorizeUrl) {
      window.location.href = data.authorizeUrl
    }
  } catch {
    // Error al iniciar
  } finally {
    cargando.value = false
  }
}
</script>

<template>
  <div class="space-y-3 pt-2 w-full">
    <div class="flex items-center gap-3">
      <div class="flex-1 h-px bg-outline-variant/40"></div>
      <span class="text-xs font-medium text-on-surface-variant">o continúa con</span>
      <div class="flex-1 h-px bg-outline-variant/40"></div>
    </div>
    
    <button
      type="button"
      :disabled="cargando"
      title="Ingresar con Google"
      class="w-full flex items-center justify-center gap-3 px-4 py-3 rounded-xl border border-outline-variant bg-surface-lowest hover:bg-surface-container-low hover:border-outline transition-colors disabled:opacity-50 relative overflow-hidden"
      @click="iniciarGoogle"
    >
      <svg class="w-5 h-5 shrink-0" viewBox="0 0 48 48" aria-hidden="true">
        <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"></path>
        <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"></path>
        <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"></path>
        <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"></path>
        <path fill="none" d="M0 0h48v48H0z"></path>
      </svg>
      <span class="text-sm font-bold text-on-surface">Continuar con Google</span>
    </button>
  </div>
</template>
