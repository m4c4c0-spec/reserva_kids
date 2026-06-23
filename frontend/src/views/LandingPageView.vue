<script setup>
import { useHead } from '@vueuse/head'
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import charCake from '../assets/char-cake.png'
import charHat from '../assets/char-hat.png'
import BaseButton from '../components/BaseButton.vue'

useHead({
  title: 'ReservaKids — Reservas para cumpleaños infantiles en Chile',
  meta: [
    {
      name: 'description',
      content:
        'Software de reservas online para negocios de cumpleaños infantiles. Catálogo, calendario, notificaciones y pagos por Mercado Pago.',
    },
    { property: 'og:title', content: 'ReservaKids — Reservas para cumpleaños infantiles' },
    {
      property: 'og:description',
      content:
        'Publica tus servicios, recibe solicitudes, cobra señas por Mercado Pago y gestiona tu calendario de cumpleaños infantiles.',
    },
    { property: 'og:type', content: 'website' },
  ],
})

const features = [
  {
    icon: 'storefront',
    titulo: 'Catálogo de servicios',
    desc: 'Publica tus paquetes de cumpleaños con precios, duración y capacidad.',
  },
  {
    icon: 'calendar_month',
    titulo: 'Calendario de disponibilidad',
    desc: 'Define tus horarios y los clientes eligen el día y la hora.',
  },
  {
    icon: 'notifications_active',
    titulo: 'Notificaciones automáticas',
    desc: 'Email y WhatsApp para solicitudes, cotizaciones y confirmaciones.',
  },
  { icon: 'payments', titulo: 'Pagos online', desc: 'Señas por Mercado Pago. El cliente paga y tú confirmas.' },
]

const pasos = [
  { num: 1, titulo: 'Crea tu negocio', desc: 'Regístrate, define tus servicios y publica tu disponibilidad.' },
  { num: 2, titulo: 'Recibe solicitudes', desc: 'Los clientes eligen, cotizas y confirmas con un clic.' },
  { num: 3, titulo: '¡A celebrar!', desc: 'El cliente paga la seña y tú te enfocas en la fiesta.' },
]

const tiers = [
  {
    nombre: 'Gratis',
    precio: '$0',
    periodo: '/mes',
    features: ['1 negocio', 'Hasta 5 servicios', 'Calendario básico', 'Notificaciones email'],
    destacado: false,
  },
  {
    nombre: 'Pro',
    precio: '$9.990',
    periodo: '/mes',
    features: ['Todo lo del Gratis', 'Pagos con Mercado Pago', 'WhatsApp automático', 'Soporte prioritario'],
    destacado: true,
  },
  {
    nombre: 'Business',
    precio: '$29.990',
    periodo: '/mes',
    features: ['Todo lo del Pro', 'Múltiples sucursales', 'Reportes de ingresos', 'Marca personalizada'],
    destacado: false,
  },
]
</script>

<template>
  <main class="min-h-screen bg-surface bg-cover bg-center" :style="{ backgroundImage: `url(${bgV2})` }">
    <!-- Hero + selector de rol (dueño vs cliente) -->
    <section class="relative overflow-hidden">
      <div class="max-w-4xl mx-auto px-5 py-12 md:py-16 text-center relative z-10">
        <div class="flex justify-center gap-4 mb-6">
          <img
            :src="charBalloon"
            alt=""
            class="w-16 h-16 md:w-24 md:h-24 object-contain character-img animate-floating drop-shadow-xl"
            aria-hidden="true"
          />
          <img
            :src="charCake"
            alt=""
            class="w-16 h-16 md:w-24 md:h-24 object-contain character-img animate-floating-delayed drop-shadow-xl"
            aria-hidden="true"
          />
          <img
            :src="charHat"
            alt=""
            class="w-16 h-16 md:w-24 md:h-24 object-contain character-img animate-floating drop-shadow-xl"
            aria-hidden="true"
          />
        </div>
        <p class="font-display font-bold text-sm md:text-base tracking-widest uppercase text-outline mb-1">
          ReservaKids
        </p>
        <h1 class="font-display font-extrabold text-3xl md:text-5xl tracking-tight text-primary leading-tight">
          Reservas para cumpleaños infantiles
        </h1>

        <!-- Pregunta guía: una sola decisión, en lenguaje simple -->
        <p class="font-display font-bold text-xl md:text-2xl text-on-surface mt-8">¿Qué quieres hacer?</p>

        <div class="grid gap-4 sm:grid-cols-2 mt-5 text-left">
          <!-- Camino CLIENTE (primario emocional: la mayoría que llega es a reservar) -->
          <button
            class="group bg-surface-lowest rounded-3xl shadow-soft border-2 border-primary/30 p-6 flex flex-col items-start gap-3 transition-all hover:shadow-lifted hover:-translate-y-1 hover:border-primary focus:outline-none focus:ring-4 focus:ring-primary-container"
            @click="$router.push('/negocios')"
          >
            <div class="w-16 h-16 rounded-2xl bg-primary-container flex items-center justify-center">
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

          <!-- Camino DUEÑO -->
          <button
            class="group bg-surface-lowest rounded-3xl shadow-soft border-2 border-outline-variant/20 p-6 flex flex-col items-start gap-3 transition-all hover:shadow-lifted hover:-translate-y-1 hover:border-secondary focus:outline-none focus:ring-4 focus:ring-secondary-container"
            @click="$router.push('/login')"
          >
            <div class="w-16 h-16 rounded-2xl bg-secondary-container flex items-center justify-center">
              <span class="material-symbols-outlined text-3xl text-on-secondary-container">storefront</span>
            </div>
            <h2 class="font-display font-extrabold text-xl text-on-surface">Tengo un negocio de fiestas</h2>
            <p class="text-sm font-medium text-on-surface-variant">
              Publica tus servicios, recibe reservas, organiza tu calendario y cobra las señas online.
            </p>
            <span
              class="mt-auto inline-flex items-center gap-1 font-bold text-secondary group-hover:gap-2 transition-all"
            >
              Entrar o crear mi negocio
              <span class="material-symbols-outlined text-[20px]">arrow_forward</span>
            </span>
          </button>
        </div>

        <!-- Camino sin cuenta: el link directo del negocio NO exige registro.
             Lo hacemos explícito para no empujar a todos al muro de login. -->
        <p class="text-sm font-medium text-on-surface-variant mt-5 flex items-center justify-center gap-1.5">
          <span class="material-symbols-outlined text-[18px] text-outline">link</span>
          ¿El negocio te pasó un link? Ábrelo para ver su catálogo sin registrarte.
        </p>
      </div>
    </section>

    <!-- Features -->
    <section class="max-w-5xl mx-auto px-5 py-16">
      <h2 class="font-display font-extrabold text-3xl text-center text-on-surface mb-10">
        Todo lo que necesitas para gestionar tus fiestas
      </h2>
      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
        <div
          v-for="f in features"
          :key="f.icon"
          class="bg-surface-lowest rounded-3xl shadow-soft border border-outline-variant/20 p-6 text-center hover:shadow-lifted hover:-translate-y-1 transition-all"
        >
          <div class="w-14 h-14 rounded-2xl bg-primary-container flex items-center justify-center mx-auto mb-4">
            <span class="material-symbols-outlined text-2xl text-on-primary-container">{{ f.icon }}</span>
          </div>
          <h3 class="font-display font-bold text-on-surface mb-2">{{ f.titulo }}</h3>
          <p class="text-sm font-medium text-on-surface-variant">{{ f.desc }}</p>
        </div>
      </div>
    </section>

    <!-- Cómo funciona -->
    <section class="bg-surface-low/80 py-16">
      <div class="max-w-5xl mx-auto px-5">
        <h2 class="font-display font-extrabold text-3xl text-center text-on-surface mb-10">¿Cómo funciona?</h2>
        <div class="grid gap-8 md:grid-cols-3">
          <div v-for="p in pasos" :key="p.num" class="text-center">
            <span
              class="w-12 h-12 rounded-full bg-primary-container text-on-primary-container font-display font-extrabold text-xl flex items-center justify-center mx-auto mb-4"
            >
              {{ p.num }}
            </span>
            <h3 class="font-display font-bold text-lg text-on-surface mb-2">{{ p.titulo }}</h3>
            <p class="text-sm font-medium text-on-surface-variant">{{ p.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Pricing -->
    <section class="max-w-5xl mx-auto px-5 py-16">
      <h2 class="font-display font-extrabold text-3xl text-center text-on-surface mb-10">
        Planes simples, sin sorpresas
      </h2>
      <div class="grid gap-6 md:grid-cols-3">
        <div
          v-for="t in tiers"
          :key="t.nombre"
          class="bg-surface-lowest rounded-3xl shadow-soft border p-6 flex flex-col transition-all"
          :class="
            t.destacado
              ? 'border-primary ring-2 ring-primary-container shadow-lifted scale-[1.02]'
              : 'border-outline-variant/20'
          "
        >
          <h3 class="font-display font-bold text-lg text-on-surface">{{ t.nombre }}</h3>
          <div class="flex items-baseline gap-1 mt-2 mb-4">
            <span class="font-display font-extrabold text-3xl text-primary">{{ t.precio }}</span>
            <span class="text-sm font-medium text-on-surface-variant">{{ t.periodo }}</span>
          </div>
          <ul class="space-y-2 flex-1">
            <li
              v-for="feat in t.features"
              :key="feat"
              class="flex items-center gap-2 text-sm font-medium text-on-surface-variant"
            >
              <span class="material-symbols-outlined text-base text-tertiary-container">check_circle</span>
              {{ feat }}
            </li>
          </ul>
          <BaseButton
            :variante="t.destacado ? 'primario' : 'secundario'"
            class="w-full mt-6"
            @click="$router.push('/login')"
          >
            {{ t.destacado ? 'Comenzar gratis' : 'Elegir plan' }}
          </BaseButton>
        </div>
      </div>
    </section>

    <!-- Footer -->
    <footer class="bg-surface-low/80 border-t border-outline-variant/20 py-8">
      <div class="max-w-5xl mx-auto px-5 flex flex-col md:flex-row items-center justify-between gap-4">
        <p class="font-display font-bold text-primary text-lg">ReservaKids</p>
        <nav class="flex items-center gap-4 text-sm font-medium text-on-surface-variant">
          <RouterLink to="/privacidad" class="hover:text-primary transition-colors">Privacidad</RouterLink>
          <RouterLink to="/login" class="hover:text-primary transition-colors">Acceso negocios</RouterLink>
          <RouterLink to="/clientes/entrar" class="hover:text-primary transition-colors">Acceso clientes</RouterLink>
        </nav>
        <p class="text-xs text-outline">© {{ new Date().getFullYear() }} ReservaKids</p>
      </div>
    </footer>
  </main>
</template>
