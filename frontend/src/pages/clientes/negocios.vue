<script setup>
definePageMeta({ layout: 'default', requiereCliente: true })

useSeoMeta({
  title: 'Elige un negocio',
  description: 'Explora los negocios disponibles y agenda tu cita en pocos pasos.',
  robots: 'noindex',
})

import { ref, onMounted } from 'vue'
import * as negocioService from '../../services/negocioService'
import { useClienteAuthStore } from '../../stores/clienteAuth'
import bgV2 from '../../assets/login-bg-v2.webp'
import charCake from '../../assets/char-cake.png'
import ErrorBanner from '../../components/ErrorBanner.vue'
import LoadingSpinner from '../../components/LoadingSpinner.vue'
import EmptyState from '../../components/EmptyState.vue'

const router = useRouter()
const auth = useClienteAuthStore()

const negocios = ref([])
const cargando = ref(true)
const error = ref('')

async function cargar() {
  cargando.value = true
  error.value = ''
  try {
    negocios.value = await negocioService.listarNegocios()
  } catch (e) {
    if (e.response?.status === 401) {
      auth.logout()
      router.push('/clientes/entrar')
      return
    }
    error.value = 'No se pudieron cargar los negocios, intenta de nuevo'
  } finally {
    cargando.value = false
  }
}

async function salir() {
  await auth.logout()
  router.push('/clientes/entrar')
}

onMounted(cargar)
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed" :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="max-w-2xl mx-auto p-5 space-y-6">
      <header class="flex items-center justify-between pt-6">
        <div>
          <h1 class="font-display font-extrabold text-2xl md:text-3xl text-primary tracking-tight">
            Hola{{ auth.nombre ? `, ${auth.nombre}` : '' }}
          </h1>
          <p class="font-medium text-on-surface-variant text-sm mt-0.5">Elige un negocio con horarios disponibles</p>
        </div>
        <button
          class="flex items-center gap-1 text-sm font-bold text-on-surface-variant hover:text-primary transition-colors"
          @click="salir"
        >
          <span class="material-symbols-outlined text-[20px]">logout</span> Salir
        </button>
      </header>

      <ErrorBanner :mensaje="error" />
      <LoadingSpinner v-if="cargando" />

      <ul v-else-if="negocios.length" class="grid gap-4 sm:grid-cols-2">
        <li v-for="n in negocios" :key="n.slug">
          <NuxtLink :to="`/clientes/agendar/${n.slug}`" class="card-festiva card-festiva--interactiva block group">
            <div class="flex items-center gap-3">
              <div class="w-12 h-12 rounded-2xl bg-primary-container flex items-center justify-center shrink-0">
                <span class="material-symbols-outlined text-on-primary-container">storefront</span>
              </div>
              <div class="min-w-0">
                <h2 class="font-display font-bold text-on-surface truncate">{{ n.nombre }}</h2>
                <p class="text-xs font-bold text-secondary flex items-center gap-1">
                  Agendar
                  <span class="material-symbols-outlined text-[16px] group-hover:translate-x-0.5 transition-transform"
                    >arrow_forward</span
                  >
                </p>
              </div>
            </div>
          </NuxtLink>
        </li>
      </ul>

      <EmptyState v-else mensaje="Aún no hay negocios con horarios disponibles. Vuelve pronto." icono="storefront">
        <template #acciones>
          <img :src="charCake" alt="" class="w-24 h-24 object-contain mt-2 character-img" aria-hidden="true" />
        </template>
      </EmptyState>
    </div>
  </main>
</template>
