<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { useClienteAuthStore } from '../stores/clienteAuth'
import bgV2 from '../assets/login-bg-v2.webp'
import charCake from '../assets/char-cake.png'

const router = useRouter()
const auth = useClienteAuthStore()
const baseURL = (import.meta.env.VITE_API_URL || '') + '/api'

const negocios = ref([])
const cargando = ref(true)
const error = ref('')

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    const { data } = await axios.get(`${baseURL}/cliente/negocios`, {
      headers: { Authorization: `Bearer ${auth.accessToken}` },
    })
    negocios.value = data
  } catch (e) {
    if (e.response?.status === 401) {
      // sesión vencida → de vuelta al login de cliente
      auth.logout()
      router.push('/clientes/entrar')
      return
    }
    error.value = 'No se pudieron cargar los negocios, intenta de nuevo'
  } finally {
    cargando.value = false
  }
}

function salir() {
  auth.logout()
  router.push('/clientes/entrar')
}

onMounted(cargar)
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed"
        :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="max-w-2xl mx-auto p-5 space-y-6">
      <header class="flex items-center justify-between pt-6">
        <div>
          <h1 class="font-display font-extrabold text-2xl md:text-3xl text-primary tracking-tight">
            Hola{{ auth.nombre ? `, ${auth.nombre}` : '' }} 👋
          </h1>
          <p class="font-medium text-on-surface-variant text-sm mt-0.5">Elige un negocio con horarios disponibles</p>
        </div>
        <button @click="salir"
                class="flex items-center gap-1 text-sm font-bold text-on-surface-variant hover:text-primary transition-colors">
          <span class="material-symbols-outlined text-[20px]">logout</span> Salir
        </button>
      </header>

      <p v-if="error" class="text-sm font-semibold text-on-error-container bg-error-container rounded-xl px-3 py-2">{{ error }}</p>

      <p v-if="cargando" class="font-medium text-on-surface-variant text-center py-10">Cargando negocios…</p>

      <ul v-else-if="negocios.length" class="grid gap-4 sm:grid-cols-2">
        <li v-for="n in negocios" :key="n.slug">
          <RouterLink :to="`/clientes/agendar/${n.slug}`"
                      class="block bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-5 hover:shadow-lifted hover:-translate-y-0.5 transition-all group">
            <div class="flex items-center gap-3">
              <div class="w-12 h-12 rounded-2xl bg-primary-container flex items-center justify-center shrink-0">
                <span class="material-symbols-outlined text-on-primary-container">storefront</span>
              </div>
              <div class="min-w-0">
                <h2 class="font-display font-bold text-on-surface truncate">{{ n.nombre }}</h2>
                <p class="text-xs font-bold text-secondary flex items-center gap-1">
                  Agendar <span class="material-symbols-outlined text-[16px] group-hover:translate-x-0.5 transition-transform">arrow_forward</span>
                </p>
              </div>
            </div>
          </RouterLink>
        </li>
      </ul>

      <div v-else class="text-center bg-surface-container rounded-3xl px-5 py-10">
        <img :src="charCake" alt="" class="w-24 h-24 object-contain mx-auto drop-shadow-xl character-img" />
        <p class="font-display font-bold text-on-surface mt-2">Aún no hay negocios con horarios disponibles</p>
        <p class="font-medium text-on-surface-variant text-sm mt-1">Vuelve pronto: los negocios publican sus fechas seguido.</p>
      </div>
    </div>
  </main>
</template>
