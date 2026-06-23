<script setup>
import { ref } from 'vue'
import axios from 'axios'

const props = defineProps({
  type: { type: String, required: true }, // 'dueno' | 'cliente' | 'admin'
})

const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'
const cargando = ref(null)

const providers = [
  {
    key: 'google',
    label: 'Google',
    iconBg: 'bg-[#4285F4]',
    iconLetter: 'G',
  },
  {
    key: 'microsoft',
    label: 'Microsoft',
    iconBg: 'bg-[#00A4EF]',
    iconLetter: 'M',
  },
  {
    key: 'apple',
    label: 'Apple',
    iconBg: 'bg-[#000000]',
    iconLetter: 'A',
  },
]

async function iniciar(provider) {
  cargando.value = provider
  try {
    const prefix = props.type === 'dueno' ? 'auth' : props.type === 'admin' ? 'admin-auth' : 'cliente-auth'
    const { data } = await axios.get(`${baseURL}/${prefix}/oauth2/${provider}/authorize?type=${props.type}`)
    if (data.authorizeUrl) {
      window.location.href = data.authorizeUrl
    }
  } catch {
    // El proveedor no está configurado; el botón no debería mostrarse
  } finally {
    cargando.value = null
  }
}
</script>

<template>
  <div class="space-y-2 pt-2">
    <div class="flex items-center gap-3">
      <div class="flex-1 h-px bg-outline-variant/40"></div>
      <span class="text-xs font-medium text-on-surface-variant">o continúa con</span>
      <div class="flex-1 h-px bg-outline-variant/40"></div>
    </div>
    <div class="flex gap-2 justify-center">
      <button
        v-for="p in providers"
        :key="p.key"
        type="button"
        :disabled="!!cargando"
        :title="'Ingresar con ' + p.label"
        class="flex items-center justify-center gap-2 px-4 py-2.5 rounded-xl border border-outline-variant bg-surface-container-low hover:bg-surface-container transition-colors disabled:opacity-50"
        @click="iniciar(p.key)"
      >
        <span
          :class="p.iconBg"
          class="inline-flex items-center justify-center w-5 h-5 rounded-full text-white text-xs font-bold leading-none"
        >{{ p.iconLetter }}</span>
        <span class="text-sm font-medium text-on-surface">{{ p.label }}</span>
      </button>
    </div>
  </div>
</template>
