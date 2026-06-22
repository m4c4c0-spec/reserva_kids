<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useHead } from '@vueuse/head'
import * as negocioService from '../services/negocioService'
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import LoadingSpinner from '../components/LoadingSpinner.vue'
import ErrorBanner from '../components/ErrorBanner.vue'

// A4: directorio PÚBLICO, sin login. El apoderado puede mirar negocios y entrar
// a la página pública de cada uno (precios + disponibilidad) sin crear cuenta.
const router = useRouter()

useHead({
  title: 'Negocios de cumpleaños — ReservaKids',
  meta: [
    {
      name: 'description',
      content: 'Mira los negocios de cumpleaños infantiles disponibles y reserva la fiesta de tu hijo o hija.',
    },
  ],
})

const negocios = ref([])
const cargando = ref(true)
const error = ref('')
const busqueda = ref('')

const filtrados = computed(() => {
  const q = busqueda.value.trim().toLowerCase()
  if (!q) return negocios.value
  return negocios.value.filter((n) => n.nombre.toLowerCase().includes(q))
})

onMounted(async () => {
  try {
    negocios.value = await negocioService.listarNegociosPublico()
  } catch {
    error.value = 'No pudimos cargar los negocios. Intenta de nuevo en un momento.'
  } finally {
    cargando.value = false
  }
})
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center bg-fixed" :style="{ backgroundImage: `url(${bgV2})` }">
    <div class="max-w-2xl mx-auto p-5 space-y-6">
      <header class="text-center pt-8 pb-2">
        <div class="flex justify-center mb-2 h-24">
          <img
            :src="charBalloon"
            alt=""
            class="w-24 h-24 object-contain drop-shadow-xl character-img animate-floating"
            aria-hidden="true"
          />
        </div>
        <h1 class="font-display font-extrabold text-3xl md:text-4xl tracking-tight text-primary">
          Elige un negocio
        </h1>
        <p class="font-medium text-on-surface-variant mt-1">
          Mira los precios y horarios y reserva la fiesta — sin crear cuenta.
        </p>
      </header>

      <div class="relative">
        <span
          class="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-on-surface-variant text-[20px]"
          >search</span
        >
        <input
          v-model="busqueda"
          type="search"
          placeholder="Buscar por nombre…"
          aria-label="Buscar negocio por nombre"
          class="input-festivo !pl-11"
        />
      </div>

      <LoadingSpinner v-if="cargando" mensaje="Cargando negocios…" />
      <ErrorBanner v-else :mensaje="error" />

      <ul v-if="!cargando && filtrados.length" class="space-y-3">
        <li v-for="n in filtrados" :key="n.slug">
          <button
            type="button"
            class="w-full text-left bg-surface-lowest rounded-2xl shadow-soft border-2 border-outline-variant/20 p-5 flex items-center gap-4 transition-all hover:shadow-lifted hover:-translate-y-0.5 hover:border-primary/50 focus:outline-none focus:ring-4 focus:ring-primary-container"
            @click="router.push(`/${n.slug}`)"
          >
            <div class="w-12 h-12 rounded-xl bg-primary-container flex items-center justify-center shrink-0">
              <span class="material-symbols-outlined text-on-primary-container">storefront</span>
            </div>
            <div class="min-w-0 flex-1">
              <h2 class="font-display font-bold text-on-surface truncate">{{ n.nombre }}</h2>
              <p class="text-xs font-bold text-on-surface-variant">Ver precios y horarios</p>
            </div>
            <span class="material-symbols-outlined text-primary">arrow_forward</span>
          </button>
        </li>
      </ul>

      <div
        v-else-if="!cargando && !error"
        class="text-center bg-surface-container rounded-3xl px-5 py-10"
      >
        <span class="material-symbols-outlined text-4xl text-on-surface-variant">storefront</span>
        <p class="font-display font-bold text-on-surface mt-2">
          {{ busqueda ? 'Ningún negocio coincide con tu búsqueda' : 'Aún no hay negocios disponibles' }}
        </p>
      </div>

      <footer class="text-center pt-2 pb-8">
        <RouterLink to="/" class="text-sm font-bold text-on-surface-variant hover:text-primary transition-colors">
          ← Volver al inicio
        </RouterLink>
      </footer>
    </div>
  </main>
</template>
