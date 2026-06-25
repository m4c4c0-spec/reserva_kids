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

const features = [
  {
    icon: 'storefront',
    titulo: 'Todo el menú de la fiesta a la vista',
    desc: 'Publica tus paquetes con precios, duración y capacidad. Los papás eligen lo que quieren ver en el cumpleaños, sin tener que escribir para preguntar.',
  },
  {
    icon: 'calendar_month',
    titulo: 'Elige día y hora sin esperar respuesta',
    desc: 'Mira las horas libres ahí mismo y reserva la fecha en un par de toques. Nada de "te aviso vuelta" por WhatsApp.',
  },
  {
    icon: 'notifications_active',
    titulo: 'Menos WhatsApps sueltos, más fiestas confirmadas',
    desc: 'Avisos por WhatsApp y email en cada paso: solicitud nueva, cotización, confirmación. Tú siempre sabes en qué quedó cada reserva.',
  },
  {
    icon: 'payments',
    titulo: 'Reserva tu fecha con una seña',
    desc: 'El cliente paga la seña online con Mercado Pago y la fecha queda guardada. Tú dejas de perder reservas por no responder a tiempo.',
  },
]

const pasosDuenos = [
  {
    num: 1,
    titulo: 'Arma tu escaparate',
    desc: 'Publica tus paquetes con precios, fotos y horarios en 10 minutos. Te queda un link bonito para compartir.',
  },
  {
    num: 2,
    titulo: 'Recibe solicitudes, no WhatsApps',
    desc: 'Los papás eligen hora y dejan sus datos solitos. Tú cotizas y confirmas con un clic desde el panel.',
  },
  {
    num: 3,
    titulo: 'Cobra la seña y a celebrar',
    desc: 'El cliente paga online y la fecha queda reservada. Tú te enfocas en la torta y los globos.',
  },
]

const pasosPapas = [
  {
    num: 1,
    titulo: 'Elige la fiesta',
    desc: 'Mira los paquetes, precios y duración. Sin tener que escribir para preguntar.',
  },
  {
    num: 2,
    titulo: 'Escoge día y hora',
    desc: 'Ve las horas libres ahí mismo y reserva la fecha en un par de toques.',
  },
  {
    num: 3,
    titulo: 'Paga la seña y a celebrar',
    desc: 'Reserva tu fecha con una seña online. Te confirmamos por WhatsApp.',
  },
]

const pasos = computed(() => (isSingleTenant.value ? pasosPapas : pasosDuenos))

const confianza = [
  { icon: 'payments', texto: 'Pagos seguros con Mercado Pago' },
  { icon: 'chat', texto: 'Confirmación por WhatsApp' },
  { icon: 'shield_lock', texto: 'Datos tratados según la Ley 21.719' },
]
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center" :style="{ backgroundImage: `url(${bgV2})` }">
    <section class="relative overflow-hidden">
      <div
        class="absolute -top-10 -left-10 w-40 h-40 rounded-full bg-primary-container/30 blur-3xl pointer-events-none"
      ></div>
      <div
        class="absolute top-20 -right-10 w-48 h-48 rounded-full bg-secondary-container/30 blur-3xl pointer-events-none"
      ></div>

      <div class="max-w-4xl mx-auto px-5 py-14 md:py-20 text-center relative z-10">
        <div class="flex justify-center gap-3 md:gap-6 mb-6">
          <img
            :src="charBalloon"
            alt=""
            class="w-20 h-20 md:w-32 md:h-32 object-contain character-img animate-floating drop-shadow-xl"
            aria-hidden="true"
          />
          <img
            :src="charCake"
            alt=""
            class="w-20 h-20 md:w-32 md:h-32 object-contain character-img animate-floating-delayed drop-shadow-xl"
            aria-hidden="true"
          />
          <img
            :src="charHat"
            alt=""
            class="w-20 h-20 md:w-32 md:h-32 object-contain character-img animate-floating drop-shadow-xl"
            aria-hidden="true"
          />
        </div>

        <p class="font-display font-bold text-sm md:text-base tracking-widest uppercase text-outline mb-2">DulceVida</p>
        <h1
          class="font-display font-extrabold text-4xl md:text-6xl tracking-tight text-primary leading-[1.05] drop-shadow-sm"
        >
          La fiesta de tu hijo,
          <br class="hidden sm:block" />
          <span class="text-secondary">resérvala en minutos</span>
        </h1>
        <p class="font-display font-medium text-lg md:text-xl text-on-surface mt-5 max-w-2xl mx-auto leading-relaxed">
          Encuentra salones infantiles con precios y horas al instante — o publica tu negocio y recibe reservas sin el
          caos del WhatsApp.
        </p>

        <div class="glass-card rounded-3xl shadow-lifted p-4 md:p-6 mt-9 max-w-3xl mx-auto">
          <p class="font-display font-bold text-base md:text-lg text-on-surface mb-4">¿Qué quieres hacer?</p>
          <div class="max-w-xl mx-auto">
            <button
              class="group w-full bg-surface-lowest rounded-3xl shadow-soft border-2 p-6 flex flex-col items-start gap-3 transition-all hover:shadow-lifted hover:-translate-y-1 focus:outline-none focus:ring-4 focus:ring-primary-container relative text-left"
              :class="isSingleTenant ? 'border-primary ring-4 ring-primary/20 shadow-lifted' : 'border-primary/30'"
              @click="router.push(isSingleTenant ? `/clientes/agendar/${singleTenantSlug}` : '/negocios')"
            >
              <span
                v-if="isSingleTenant"
                class="absolute -top-3 left-6 bg-primary text-on-primary text-[11px] font-extrabold uppercase tracking-wider px-3 py-1 rounded-full shadow-soft"
                >Para papás</span
              >
              <div
                class="w-16 h-16 rounded-2xl bg-primary-container flex items-center justify-center transition-transform group-hover:scale-110 group-hover:rotate-3"
              >
                <span class="material-symbols-outlined text-3xl text-on-primary-container">celebration</span>
              </div>
              <h2 class="font-display font-extrabold text-xl text-on-surface">Quiero reservar una fiesta</h2>
              <p class="text-sm font-medium text-on-surface-variant">
                Para papás y mamás: mira precios y horas disponibles y reserva el cumpleaños de tu hijo o hija.
              </p>
              <span
                class="mt-auto inline-flex items-center gap-1 font-bold text-primary group-hover:gap-2 transition-all"
              >
                Reservar una fiesta
                <span class="material-symbols-outlined text-[20px]">arrow_forward</span>
              </span>
            </button>
          </div>

          <button
            class="mt-4 inline-flex items-center gap-2 text-sm font-bold text-secondary bg-surface-lowest border-2 border-secondary/40 hover:border-secondary hover:bg-secondary-fixed/60 transition-all rounded-full px-5 py-2.5 focus:outline-none focus:ring-4 focus:ring-secondary-container"
            @click="router.push('/negocios_duenos')"
          >
            <span class="material-symbols-outlined text-[20px]">storefront</span>
            Soy el dueño — gestionar mis servicios
            <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
          </button>
        </div>

        <ul class="flex flex-wrap items-center justify-center gap-x-5 gap-y-2 mt-6">
          <li
            v-for="c in confianza"
            :key="c.icon"
            class="flex items-center gap-1.5 text-xs md:text-sm font-bold text-on-surface-variant bg-surface-lowest/70 rounded-full px-3 py-1.5 shadow-soft"
          >
            <span class="material-symbols-outlined text-[16px] text-primary">{{ c.icon }}</span>
            {{ c.texto }}
          </li>
        </ul>

        <form class="mt-6 flex items-center justify-center gap-2 max-w-md mx-auto" @submit.prevent="irAlLink">
          <div class="relative flex-1">
            <span
              class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-[18px]"
              >link</span
            >
            <input
              v-model="linkInput"
              type="text"
              placeholder="Pega el link o escribe el nombre del negocio"
              aria-label="Pega el link o escribe el nombre del negocio"
              class="w-full pl-9 pr-3 py-2.5 rounded-full bg-surface-lowest border-2 border-outline-variant/30 text-sm font-medium text-on-surface placeholder:text-outline focus:outline-none focus:ring-2 focus:ring-primary-container focus:border-primary/50 transition-all"
            />
          </div>
          <button
            type="submit"
            :disabled="!linkInput.trim()"
            class="shrink-0 px-5 py-2.5 rounded-full bg-primary text-on-primary font-bold text-sm flex items-center gap-1.5 transition-all hover:shadow-md disabled:opacity-40 disabled:cursor-not-allowed"
          >
            Ir
            <span class="material-symbols-outlined text-[18px]">arrow_forward</span>
          </button>
        </form>
      </div>
    </section>

    <section v-if="!isSingleTenant" class="max-w-5xl mx-auto px-5 py-16 md:py-20">
      <div class="text-center mb-12">
        <p class="font-display font-bold text-sm tracking-widest uppercase text-secondary mb-2">Para tu negocio</p>
        <h2 class="font-display font-extrabold text-3xl md:text-4xl text-on-surface">
          Todo lo que necesitás para llenar el calendario
        </h2>
      </div>
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="f in features"
          :key="f.icon"
          class="card-festiva card-festiva--interactiva text-center hover:-translate-y-1.5"
        >
          <div
            class="w-14 h-14 rounded-2xl bg-primary-container flex items-center justify-center mx-auto mb-4 transition-transform hover:scale-110"
          >
            <span class="material-symbols-outlined text-2xl text-on-primary-container">{{ f.icon }}</span>
          </div>
          <h3 class="font-display font-bold text-on-surface mb-2 leading-snug">{{ f.titulo }}</h3>
          <p class="text-sm font-medium text-on-surface-variant leading-relaxed">{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <section class="bg-surface-low/80 py-16 md:py-20 border-y border-outline-variant/20">
      <div class="max-w-5xl mx-auto px-5">
        <div class="text-center mb-12">
          <p class="font-display font-bold text-sm tracking-widest uppercase text-secondary mb-2">Así de simple</p>
          <h2 class="font-display font-extrabold text-3xl md:text-4xl text-on-surface">
            {{ isSingleTenant ? 'Reserva en tres toques' : 'De WhatsApps sin orden a fiestas confirmadas' }}
          </h2>
        </div>
        <div class="grid gap-8 md:grid-cols-3 relative">
          <div
            class="hidden md:block absolute top-6 left-[16%] right-[16%] h-0.5 bg-gradient-to-r from-primary/30 via-primary/50 to-primary/30"
          ></div>
          <div v-for="p in pasos" :key="p.num" class="text-center relative">
            <span
              class="relative z-10 w-12 h-12 rounded-full bg-primary text-on-primary font-display font-extrabold text-xl flex items-center justify-center mx-auto mb-4 shadow-lifted"
            >
              {{ p.num }}
            </span>
            <h3 class="font-display font-bold text-lg text-on-surface mb-2">{{ p.titulo }}</h3>
            <p class="text-sm font-medium text-on-surface-variant leading-relaxed max-w-xs mx-auto">{{ p.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <footer class="bg-surface-low/80 border-t border-outline-variant/20 py-8">
      <div class="max-w-5xl mx-auto px-5 flex flex-col md:flex-row items-center justify-between gap-4">
        <p class="font-display font-bold text-primary text-lg">DulceVida</p>
        <nav class="flex items-center gap-4 text-sm font-medium text-on-surface-variant">
          <NuxtLink to="/privacidad" class="hover:text-primary transition-colors">Privacidad</NuxtLink>
          <NuxtLink to="/negocios_duenos" class="hover:text-primary transition-colors">Acceso negocios</NuxtLink>
          <NuxtLink to="/clientes/entrar" class="hover:text-primary transition-colors">Acceso clientes</NuxtLink>
        </nav>
        <p class="text-xs text-outline">© {{ new Date().getFullYear() }} DulceVida</p>
      </div>
    </footer>
  </main>
</template>
