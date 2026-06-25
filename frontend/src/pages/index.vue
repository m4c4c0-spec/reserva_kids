<script setup>
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import charCake from '../assets/char-cake.png'
import charHat from '../assets/char-hat.png'

definePageMeta({ layout: 'default' })

useHead({
  title: 'DulceVida — Reservas para cumpleaños infantiles en Chile',
  meta: [
    {
      name: 'description',
      content:
        'Encuentra salones infantiles con precios y horas al instante, o publica tu negocio y recibe reservas sin el caos del WhatsApp.',
    },
    { property: 'og:title', content: 'DulceVida — La fiesta de tu hijo, resérvala en minutos' },
    {
      property: 'og:description',
      content:
        'Salones infantiles con precios y horas a la vista. Reserva en minutos o publica tu negocio y recibe reservas online.',
    },
    { property: 'og:type', content: 'website' },
  ],
})

const router = useRouter()
const { slug: singleTenantSlug, isSingleTenant } = useSingleTenant()

const linkInput = ref('')
const flipped = ref(false)

function toggleFlip() {
  flipped.value = !flipped.value
}

function irAlLink() {
  const raw = linkInput.value.trim()
  if (!raw) return
  let slug = raw
  try {
    const url = new URL(raw.startsWith('http') ? raw : `https://${raw}`)
    slug =
      url.pathname
        .replace(/^\/+|\/+$/g, '')
        .split('/')
        .pop() || raw
  } catch {
    slug = raw
  }
  router.push(`/${slug}`)
}

const pasosPapas = [
  { num: 1, titulo: 'Elegí la fiesta', desc: 'Mirá paquetes, precios y fotos. Sin preguntar nada.' },
  { num: 2, titulo: 'Elegí día y hora', desc: 'Horas libres a la vista. Reservá en dos toques.' },
  { num: 3, titulo: 'Pagá la seña', desc: 'Pago seguro online. Te confirmamos por WhatsApp.' },
]
const pasosDuenos = [
  { num: 1, titulo: 'Crea tu escaparate', desc: 'Publica paquetes con precios y horarios en 10 minutos.' },
  { num: 2, titulo: 'Recibe reservas', desc: 'Los papás eligen día y hora solos. Vos cotizás con un clic.' },
  { num: 3, titulo: 'Cobrá la seña', desc: 'Pago online. Fecha reservada. Vos te enfocás en la fiesta.' },
]
const pasos = computed(() => (flipped.value ? pasosDuenos : pasosPapas))

const confianza = [
  { icon: 'verified_user', texto: 'Pagos seguros' },
  { icon: 'chat', texto: 'Confirmación por WhatsApp' },
  { icon: 'shield_lock', texto: 'Datos protegidos' },
]

const rutaReservar = computed(() => (isSingleTenant.value ? `/clientes/agendar/${singleTenantSlug}` : '/negocios'))
const textoReservar = computed(() =>
  isSingleTenant.value
    ? 'Reservá el cumpleaños de tu hijo o hija en minutos. Mirá precios y horas, elegí y pagá la seña online.'
    : 'Explorá salones infantiles con precios y horarios visibles. Reservá en minutos, sin WhatsApp.',
)
</script>

<template>
  <main
    class="min-h-screen bg-[#fff8f8]"
    :style="{
      backgroundImage: `url(${bgV2})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      backgroundAttachment: 'scroll',
    }"
  >
    <!-- HERO -->
    <section class="relative pt-8 pb-4 md:pt-14 md:pb-8 text-center overflow-hidden">
      <div class="absolute -top-24 -left-24 w-56 h-56 rounded-full bg-pink-200/40 blur-3xl pointer-events-none" />
      <div class="absolute top-16 -right-24 w-64 h-64 rounded-full bg-purple-200/40 blur-3xl pointer-events-none" />
      <div
        class="absolute bottom-0 left-1/2 -translate-x-1/2 w-80 h-40 rounded-full bg-amber-100/30 blur-3xl pointer-events-none"
      />

      <div class="relative z-10 max-w-4xl mx-auto px-5">
        <div class="flex justify-center items-end gap-3 md:gap-5 mb-5">
          <img
            :src="charBalloon"
            alt=""
            class="w-16 h-16 md:w-24 md:h-24 object-contain drop-shadow-xl animate-floating"
          />
          <img
            :src="charCake"
            alt=""
            class="w-20 h-20 md:w-28 md:h-28 object-contain drop-shadow-xl animate-floating-delayed"
          />
          <img :src="charHat" alt="" class="w-16 h-16 md:w-24 md:h-24 object-contain drop-shadow-xl animate-floating" />
        </div>

        <span
          class="inline-block font-display font-extrabold text-[10px] md:text-xs tracking-[0.2em] uppercase bg-[#ff4db8] text-white px-4 py-1.5 rounded-full mb-4 shadow-lg"
        >
          Reservas de cumpleaños infantiles
        </span>

        <h1
          class="font-display font-extrabold text-3xl md:text-5xl lg:text-6xl tracking-tight text-[#25181e] leading-[1.05] max-w-2xl mx-auto"
        >
          La fiesta de tu hijo,
          <span class="text-[#b5007d]">resérvala en minutos</span>
        </h1>
        <p class="font-medium text-base md:text-lg text-[#3a2730] mt-4 max-w-lg mx-auto leading-relaxed">
          Encontrá salones infantiles con precios y horas a la vista. Sin llamar, sin esperar respuesta.
        </p>
      </div>
    </section>

    <!-- FLIP CARD -->
    <section class="max-w-lg mx-auto px-4 py-4 md:py-10 relative z-10">
      <div
        class="relative w-full mx-auto rounded-3xl shadow-xl overflow-hidden border-2 border-pink-100"
        :style="{ height: '320px', perspective: '1200px' }"
      >
        <div
          class="absolute inset-0 transition-transform duration-700 ease-in-out"
          :style="{ transformStyle: 'preserve-3d', transform: flipped ? 'rotateY(180deg)' : 'rotateY(0deg)' }"
        >
          <!-- FRONT -->
          <div
            class="absolute inset-0 flex flex-col items-center text-center gap-4 p-6 md:p-8 rounded-3xl bg-white"
            :style="{ backfaceVisibility: 'hidden', WebkitBackfaceVisibility: 'hidden' }"
          >
            <span
              class="absolute top-0 inset-x-0 h-1 bg-gradient-to-r from-[#b5007d] via-[#ff4db8] to-[#b5007d] rounded-t-3xl"
            />
            <div class="w-16 h-16 rounded-2xl bg-pink-100 flex items-center justify-center mt-4">
              <span class="material-symbols-outlined text-3xl text-[#b5007d]">celebration</span>
            </div>
            <h2 class="font-display font-extrabold text-xl md:text-2xl text-[#25181e]">Quiero reservar</h2>
            <p class="text-sm font-medium text-[#3a2730] leading-relaxed">{{ textoReservar }}</p>
            <button
              class="mt-auto inline-flex items-center gap-2 font-bold text-sm text-[#b5007d] rounded-full px-6 py-3 bg-pink-100 hover:bg-pink-200 transition-colors"
              @click="router.push(rutaReservar)"
            >
              {{ isSingleTenant ? 'Ver horarios disponibles' : 'Explorar salones' }}
              <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
            </button>
          </div>

          <!-- BACK -->
          <div
            class="absolute inset-0 flex flex-col items-center text-center gap-4 p-6 md:p-8 rounded-3xl bg-[#21094e]"
            :style="{ transform: 'rotateY(180deg)', backfaceVisibility: 'hidden', WebkitBackfaceVisibility: 'hidden' }"
          >
            <span
              class="absolute top-0 inset-x-0 h-1 bg-gradient-to-r from-yellow-400 via-orange-400 to-yellow-400 rounded-t-3xl"
            />
            <div class="w-16 h-16 rounded-2xl bg-white/15 flex items-center justify-center mt-4">
              <span class="material-symbols-outlined text-3xl text-white">storefront</span>
            </div>
            <h2 class="font-display font-extrabold text-xl md:text-2xl text-white">Soy dueño</h2>
            <p class="text-sm font-medium text-white/60 leading-relaxed">
              Gestioná tu salón: publicá paquetes, recibí reservas y cobrá señas online.
            </p>
            <button
              class="mt-auto inline-flex items-center gap-2 font-bold text-sm text-yellow-400 hover:text-yellow-300 rounded-full px-6 py-3 bg-white/10 hover:bg-white/20 transition-colors"
              @click="router.push('/negocios_duenos')"
            >
              Entrar a mi panel
              <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Toggle -->
      <button
        class="flex items-center justify-center gap-2 mx-auto mt-5 text-sm font-bold text-[#3a2730]/50 hover:text-[#b5007d] transition-colors min-h-[44px] px-4"
        @click="toggleFlip"
      >
        <span class="material-symbols-outlined text-[18px]">{{ flipped ? 'celebration' : 'storefront' }}</span>
        {{ flipped ? '¿Querés reservar una fiesta?' : '¿Tenés un salón? Entrá acá' }}
        <span
          class="material-symbols-outlined text-[18px] transition-transform duration-300"
          :class="{ 'rotate-180': flipped }"
          >swap_horiz</span
        >
      </button>

      <!-- Trust -->
      <div class="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 mt-5">
        <div v-for="c in confianza" :key="c.icon" class="flex items-center gap-1.5 text-xs font-bold text-[#3a2730]/60">
          <span class="material-symbols-outlined text-[16px] text-[#b5007d]/60">{{ c.icon }}</span>
          {{ c.texto }}
        </div>
      </div>
    </section>

    <!-- LINK INPUT -->
    <div class="max-w-xl mx-auto px-4 pb-10 md:pb-14">
      <div class="bg-white/80 backdrop-blur rounded-2xl border border-pink-50 p-4 md:p-5">
        <p class="text-xs font-bold text-[#3a2730] text-center mb-3 flex items-center justify-center gap-1.5">
          <span class="material-symbols-outlined text-[16px]">link</span>
          ¿Ya conocés un salón? Pegá su link o escribí el nombre
        </p>
        <form class="flex flex-col sm:flex-row gap-2" @submit.prevent="irAlLink">
          <div class="relative flex-1">
            <span
              class="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-[#3a2730]/30 text-[18px]"
              >search</span
            >
            <input
              v-model="linkInput"
              type="text"
              placeholder="fiestas-pepito o dulcevida.cl/..."
              class="w-full pl-10 pr-4 py-3 rounded-xl bg-white border-2 border-pink-100 text-sm font-medium text-[#25181e] placeholder:text-gray-300 focus:outline-none focus:ring-2 focus:ring-pink-200 focus:border-[#b5007d]/40"
            />
          </div>
          <button
            type="submit"
            :disabled="!linkInput.trim()"
            class="shrink-0 px-6 py-3 rounded-xl bg-[#b5007d] text-white font-bold text-sm flex items-center justify-center gap-1.5 hover:shadow-lg disabled:opacity-30 min-h-[44px]"
          >
            Ir
            <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
          </button>
        </form>
      </div>
    </div>

    <!-- STEPS -->
    <section class="bg-gradient-to-b from-pink-50/60 to-pink-50/90 py-14 md:py-20 border-y border-pink-100/50">
      <div class="max-w-4xl mx-auto px-5">
        <div class="text-center mb-10">
          <p class="font-display font-bold text-xs tracking-widest uppercase text-[#b5007d] mb-2">
            {{ flipped ? 'Para dueños de salón' : 'Así de simple' }}
          </p>
          <h2 class="font-display font-extrabold text-2xl md:text-3xl text-[#25181e]">
            {{ flipped ? 'De WhatsApps a fiestas confirmadas' : 'Reservá en tres pasos' }}
          </h2>
          <p class="font-medium text-sm text-[#3a2730] mt-1">
            {{ flipped ? 'Publicá tu salón y recibí reservas online' : 'Así de simple es reservar' }}
          </p>
        </div>
        <div class="grid gap-6 md:grid-cols-3 relative">
          <div
            class="hidden md:block absolute top-7 left-[18%] right-[18%] h-0.5 bg-gradient-to-r from-[#b5007d]/20 via-[#b5007d]/40 to-[#b5007d]/20"
          />
          <div v-for="p in pasos" :key="p.num" class="text-center relative flex flex-col items-center">
            <span
              class="relative z-10 w-14 h-14 rounded-2xl bg-[#b5007d] text-white font-display font-extrabold text-xl flex items-center justify-center shadow-lg mb-4"
            >
              {{ p.num }}
            </span>
            <h3 class="font-display font-bold text-base text-[#25181e] mb-1.5">{{ p.titulo }}</h3>
            <p class="text-sm font-medium text-[#3a2730] leading-relaxed max-w-[220px]">{{ p.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- FOOTER -->
    <footer class="bg-white/70 border-t border-pink-100/30 py-8">
      <div class="max-w-5xl mx-auto px-5">
        <div class="flex flex-col md:flex-row items-center justify-between gap-5 mb-5">
          <div class="text-center md:text-left">
            <p class="font-display font-extrabold text-lg text-[#b5007d] tracking-tight">DulceVida</p>
            <p class="text-xs font-medium text-[#3a2730] mt-0.5">Reservas de cumpleaños infantiles en Chile</p>
          </div>
          <nav class="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-sm font-medium">
            <NuxtLink to="/privacidad" class="text-[#3a2730]/60 hover:text-[#b5007d] transition-colors"
              >Privacidad</NuxtLink
            >
            <NuxtLink to="/negocios_duenos" class="text-[#3a2730]/60 hover:text-[#b5007d] font-bold transition-colors">
              Acceso dueños
            </NuxtLink>
            <NuxtLink to="/clientes/entrar" class="text-[#3a2730]/60 hover:text-[#b5007d] transition-colors">
              Acceso clientes
            </NuxtLink>
            <NuxtLink to="/admin/login" class="text-[#3a2730]/30 hover:text-[#b5007d]/40 transition-colors text-xs"
              >Admin</NuxtLink
            >
          </nav>
        </div>
        <p class="text-center text-[11px] text-gray-300">
          © {{ new Date().getFullYear() }} DulceVida · Todos los derechos reservados
        </p>
      </div>
    </footer>
  </main>
</template>
