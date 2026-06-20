<script setup>
import bgV2 from '../assets/login-bg-v2.webp'
import charBalloon from '../assets/char-balloon.png'
import charCake from '../assets/char-cake.png'
import charHat from '../assets/char-hat.png'
import BaseButton from '../components/BaseButton.vue'

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
    <!-- Hero -->
    <section class="relative overflow-hidden">
      <div class="max-w-5xl mx-auto px-5 py-16 md:py-24 text-center relative z-10">
        <div class="flex justify-center gap-4 mb-6">
          <img
            :src="charBalloon"
            alt=""
            class="w-20 h-20 md:w-28 md:h-28 object-contain character-img animate-floating drop-shadow-xl"
            aria-hidden="true"
          />
          <img
            :src="charCake"
            alt=""
            class="w-20 h-20 md:w-28 md:h-28 object-contain character-img animate-floating-delayed drop-shadow-xl"
            aria-hidden="true"
          />
          <img
            :src="charHat"
            alt=""
            class="w-20 h-20 md:w-28 md:h-28 object-contain character-img animate-floating drop-shadow-xl"
            aria-hidden="true"
          />
        </div>
        <h1 class="font-display font-extrabold text-4xl md:text-6xl tracking-tight text-primary leading-tight">
          Reservas para cumpleaños infantiles
        </h1>
        <p class="font-medium text-lg md:text-xl text-on-surface-variant mt-4 max-w-2xl mx-auto">
          La plataforma SaaS que centraliza catálogo, calendario, cotizaciones y pagos para negocios de fiestas
          infantiles.
        </p>
        <div class="flex flex-col sm:flex-row items-center justify-center gap-3 mt-8">
          <BaseButton variante="primario" class="px-8 py-3 text-base" @click="$router.push('/login')">
            Soy negocio — Entrar
          </BaseButton>
          <BaseButton variante="secundario" class="px-8 py-3 text-base" @click="$router.push('/clientes/entrar')">
            Soy cliente — Reservar
          </BaseButton>
        </div>
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
