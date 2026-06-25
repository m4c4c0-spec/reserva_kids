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
        'Encuentra salones infantiles con precios y horas al instante, o publica tu negocio y recibe reservas sin el caos del WhatsApp. Señas online con Mercado Pago.',
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

const pasosDuenos = [
  { num: 1, titulo: 'Crea tu escaparate', desc: 'Publica paquetes con precios y horarios en 10 minutos.' },
  { num: 2, titulo: 'Recibe reservas', desc: 'Los papás eligen día y hora solos. Vos cotizás con un clic.' },
  { num: 3, titulo: 'Cobrá la seña', desc: 'Pago online. Fecha reservada. Vos te enfocás en la fiesta.' },
]

const pasosPapas = [
  { num: 1, titulo: 'Elegí la fiesta', desc: 'Mirá paquetes, precios y fotos. Sin preguntar nada.' },
  { num: 2, titulo: 'Elegí día y hora', desc: 'Horas libres a la vista. Reservá en dos toques.' },
  { num: 3, titulo: 'Pagá la seña', desc: 'Pago seguro online. Te confirmamos por WhatsApp.' },
]

const flipped = ref(false)
function toggleFlip() {
  flipped.value = !flipped.value
}

const pasos = computed(() => (flipped.value ? pasosDuenos : pasosPapas))
const tituloPasos = computed(() => (flipped.value ? 'De WhatsApps a fiestas confirmadas' : 'Reservá en tres pasos'))
const subtituloPasos = computed(() =>
  flipped.value ? 'Publicá tu salón y recibí reservas online' : 'Así de simple es reservar',
)

const confianza = [
  { icon: 'verified_user', texto: 'Pagos seguros con Mercado Pago' },
  { icon: 'chat', texto: 'Confirmación por WhatsApp' },
  { icon: 'shield_lock', texto: 'Datos protegidos · Ley 21.719' },
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
    class="relative min-h-screen bg-surface bg-cover bg-scroll md:bg-fixed"
    :style="{ backgroundImage: `url(${bgV2})`, paddingBottom: 'env(safe-area-inset-bottom, 0px)' }"
  >
    <!-- Overlay de contraste: la foto de fondo puede bajar el contraste del texto (WCAG).
         Esta capa lo asegura sin tapar las secciones (van en z superior por orden DOM). -->
    <div class="absolute inset-0 bg-surface/70 pointer-events-none" aria-hidden="true" />

    <!-- ═══ HERO ═══ -->
    <section class="relative overflow-hidden pt-6 pb-2 md:pt-10 md:pb-6">
      <div
        class="absolute -top-20 -left-20 w-64 h-64 rounded-full bg-primary-container/20 blur-3xl pointer-events-none"
      />
      <div
        class="absolute top-10 -right-20 w-72 h-72 rounded-full bg-secondary-container/20 blur-3xl pointer-events-none"
      />
      <div
        class="absolute bottom-0 left-1/2 -translate-x-1/2 w-96 h-48 rounded-full bg-tertiary-container/10 blur-3xl pointer-events-none"
      />

      <div class="max-w-4xl mx-auto px-5 text-center relative z-10">
        <!-- Characters floating -->
        <div class="flex justify-center items-end gap-2 md:gap-4 mb-4">
          <img
            :src="charBalloon"
            alt=""
            class="w-16 h-16 md:w-24 md:h-24 object-contain drop-shadow-xl animate-floating"
            aria-hidden="true"
          />
          <img
            :src="charCake"
            alt=""
            class="w-20 h-20 md:w-28 md:h-28 object-contain drop-shadow-xl animate-floating-delayed"
            aria-hidden="true"
          />
          <img
            :src="charHat"
            alt=""
            class="w-16 h-16 md:w-24 md:h-24 object-contain drop-shadow-xl animate-floating"
            aria-hidden="true"
          />
        </div>

        <span
          class="inline-block font-display font-extrabold text-[10px] md:text-xs tracking-[0.2em] uppercase bg-primary-container text-on-primary-container px-4 py-1.5 rounded-full mb-4 shadow-soft"
        >
          Reservas de cumpleaños infantiles
        </span>

        <h1
          class="font-display font-extrabold text-3xl md:text-5xl lg:text-6xl tracking-tight text-on-surface leading-[1.05] drop-shadow-sm max-w-3xl mx-auto"
        >
          La fiesta de tu hijo,
          <span class="text-primary">resérvala en minutos</span>
        </h1>

        <p class="font-medium text-base md:text-lg text-on-surface-variant mt-4 max-w-xl mx-auto leading-relaxed">
          Encontrá salones infantiles con precios y horas a la vista. Sin llamar, sin esperar respuesta.
        </p>
      </div>
    </section>

    <!-- ═══ FLIP CARD: PAPÁS / DUEÑOS ═══ -->
    <section class="max-w-lg mx-auto px-5 py-6 md:py-10 relative z-10">
      <div class="flip-card mx-auto" :class="{ 'flip-card--flipped': flipped }">
        <div class="flip-card__inner">
          <!-- FRONT: Quiero reservar -->
          <div
            class="flip-card__front bg-surface-lowest rounded-3xl border-2 border-primary/20 p-6 md:p-8 flex flex-col items-center text-center gap-4 shadow-soft"
            :inert="flipped"
          >
            <span
              class="absolute top-0 inset-x-0 h-1 bg-gradient-to-r from-primary via-secondary to-primary rounded-t-3xl"
            />
            <span class="w-16 h-16 rounded-2xl bg-primary-container flex items-center justify-center">
              <span class="material-symbols-outlined text-3xl text-on-primary-container">celebration</span>
            </span>
            <div>
              <h2 class="font-display font-extrabold text-xl md:text-2xl text-on-surface mb-1">Quiero reservar</h2>
              <p class="text-sm font-medium text-on-surface-variant leading-relaxed">{{ textoReservar }}</p>
            </div>
            <button
              class="inline-flex items-center gap-1.5 font-bold text-sm text-primary hover:gap-2 transition-all mt-auto rounded-full px-6 py-3 bg-primary-container/30 hover:bg-primary-container/50"
              @click.stop="router.push(rutaReservar)"
            >
              {{ isSingleTenant ? 'Ver horarios disponibles' : 'Explorar salones' }}
              <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
            </button>
          </div>

          <!-- BACK: Soy dueño -->
          <div
            class="flip-card__back bg-gradient-to-br from-dueno to-dueno-bright rounded-3xl border-2 border-dueno/50 p-6 md:p-8 flex flex-col items-center text-center gap-4 shadow-soft"
            :inert="!flipped"
          >
            <span
              class="absolute top-0 inset-x-0 h-1 bg-gradient-to-r from-yellow-400 via-orange-400 to-yellow-400 rounded-t-3xl"
            />
            <span class="w-16 h-16 rounded-2xl bg-white/15 flex items-center justify-center">
              <span class="material-symbols-outlined text-3xl text-white">storefront</span>
            </span>
            <div>
              <h2 class="font-display font-extrabold text-xl md:text-2xl text-white mb-1">Soy dueño</h2>
              <p class="text-sm font-medium text-white/60 leading-relaxed">
                Gestioná tu salón: publicá paquetes, recibí reservas y cobrá señas online.
              </p>
            </div>
            <button
              class="inline-flex items-center gap-1.5 font-bold text-sm text-yellow-400 hover:text-yellow-300 hover:gap-2 transition-all mt-auto rounded-full px-6 py-3 bg-white/10 hover:bg-white/20"
              @click.stop="router.push('/negocios_duenos')"
            >
              Entrar a mi panel
              <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Flip trigger -->
      <button
        class="flex items-center justify-center gap-1.5 mx-auto mt-4 text-xs font-bold text-on-surface-variant/50 hover:text-primary transition-colors group min-h-[44px] px-4"
        @click="toggleFlip"
      >
        <span class="material-symbols-outlined text-[16px] group-hover:animate-pulse">{{
          flipped ? 'celebration' : 'storefront'
        }}</span>
        {{ flipped ? '¿Querés reservar una fiesta?' : '¿Tenés un salón? Entrá acá' }}
        <span
          class="material-symbols-outlined text-[16px] transition-transform duration-300"
          :class="{ 'rotate-180': flipped }"
          >swap_horiz</span
        >
      </button>

      <!-- Trust badges -->
      <div class="flex flex-wrap items-center justify-center gap-x-6 gap-y-2 mt-5">
        <div
          v-for="c in confianza"
          :key="c.icon"
          class="flex items-center gap-1.5 text-xs font-bold text-on-surface-variant/70"
        >
          <span class="material-symbols-outlined text-[16px] text-primary/60">{{ c.icon }}</span>
          {{ c.texto }}
        </div>
      </div>
    </section>

    <!-- ═══ LINK INPUT ═══ -->
    <section class="max-w-xl mx-auto px-5 pb-10 md:pb-14">
      <div class="bg-surface-lowest/70 backdrop-blur-sm rounded-2xl border border-outline-variant/10 p-4 md:p-5">
        <p class="text-xs font-bold text-on-surface-variant text-center mb-3 flex items-center justify-center gap-1.5">
          <span class="material-symbols-outlined text-[16px]">link</span>
          ¿Ya conocés un salón? Pegá su link o escribí el nombre
        </p>
        <form class="flex flex-col xs:flex-row items-stretch xs:items-center gap-2" @submit.prevent="irAlLink">
          <div class="relative flex-1">
            <span
              class="material-symbols-outlined absolute left-3.5 top-1/2 -translate-y-1/2 text-on-surface-variant/40 text-[18px]"
              >search</span
            >
            <input
              v-model="linkInput"
              type="text"
              placeholder="fiestas-pepito o dulcevida.cl/..."
              aria-label="Buscar un salón por link o nombre"
              class="w-full pl-10 pr-4 py-3 rounded-xl bg-surface-lowest border-2 border-outline-variant/20 text-sm font-medium text-on-surface placeholder:text-outline/50 focus:outline-none focus:ring-2 focus:ring-primary-container focus:border-primary/40 transition-all"
            />
          </div>
          <button
            type="submit"
            :disabled="!linkInput.trim()"
            class="shrink-0 px-5 py-3 rounded-xl bg-primary text-on-primary font-bold text-sm flex items-center justify-center gap-1.5 transition-all hover:shadow-lg disabled:opacity-30 disabled:cursor-not-allowed min-h-[44px]"
          >
            Ir
            <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
          </button>
        </form>
      </div>
    </section>

    <!-- ═══ STEPS ═══ -->
    <section
      class="bg-gradient-to-b from-surface-low/40 to-surface-low/80 py-14 md:py-20 border-y border-outline-variant/10"
    >
      <div class="max-w-4xl mx-auto px-5">
        <div class="text-center mb-10">
          <p class="font-display font-bold text-xs tracking-widest uppercase text-secondary mb-2">
            {{ flipped ? 'Para dueños de salón' : 'Así de simple' }}
          </p>
          <h2 class="font-display font-extrabold text-2xl md:text-3xl text-on-surface">
            {{ tituloPasos }}
          </h2>
          <p class="font-medium text-sm text-on-surface-variant mt-1">{{ subtituloPasos }}</p>
        </div>
        <div class="grid gap-6 md:grid-cols-3 relative">
          <div
            class="hidden md:block absolute top-7 left-[18%] right-[18%] h-0.5 bg-gradient-to-r from-primary/20 via-primary/40 to-primary/20"
          />
          <div v-for="p in pasos" :key="p.num" class="text-center relative flex flex-col items-center">
            <span
              class="relative z-10 w-14 h-14 rounded-2xl bg-primary text-on-primary font-display font-extrabold text-xl flex items-center justify-center shadow-lifted mb-4"
            >
              {{ p.num }}
            </span>
            <h3 class="font-display font-bold text-base text-on-surface mb-1.5">{{ p.titulo }}</h3>
            <p class="text-sm font-medium text-on-surface-variant leading-relaxed max-w-[220px]">{{ p.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ═══ FOOTER ═══ -->
    <footer class="bg-surface-lowest/60 border-t border-outline-variant/10 py-8">
      <div class="max-w-5xl mx-auto px-5">
        <div class="flex flex-col md:flex-row items-center justify-between gap-6 mb-6">
          <div class="text-center md:text-left">
            <p class="font-display font-extrabold text-lg text-primary tracking-tight">DulceVida</p>
            <p class="text-xs font-medium text-on-surface-variant mt-0.5">Reservas de cumpleaños infantiles en Chile</p>
          </div>
          <nav class="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-sm font-medium">
            <NuxtLink to="/privacidad" class="text-on-surface-variant hover:text-primary transition-colors"
              >Privacidad</NuxtLink
            >
            <NuxtLink
              to="/negocios_duenos"
              class="text-on-surface-variant hover:text-primary font-bold transition-colors"
            >
              <span class="material-symbols-outlined align-middle text-[16px] mr-1">storefront</span>
              Acceso dueños
            </NuxtLink>
            <NuxtLink to="/clientes/entrar" class="text-on-surface-variant hover:text-primary transition-colors">
              <span class="material-symbols-outlined align-middle text-[16px] mr-1">person</span>
              Acceso clientes
            </NuxtLink>
            <NuxtLink
              to="/admin/login"
              class="text-on-surface-variant/40 hover:text-primary/60 transition-colors text-xs"
            >
              Admin
            </NuxtLink>
          </nav>
        </div>
        <p class="text-center text-[11px] text-outline/50">
          © {{ new Date().getFullYear() }} DulceVida · Todos los derechos reservados
        </p>
      </div>
    </footer>
  </main>
</template>
