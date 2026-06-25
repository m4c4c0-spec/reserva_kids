import tailwindcss from '@tailwindcss/vite'

const API_TARGET = process.env.VITE_API_TARGET || 'http://localhost:8080'
const SINGLE_TENANT_SLUG = process.env.VITE_SINGLE_TENANT_SLUG || ''
const SINGLE_TENANT_NAME = process.env.VITE_SINGLE_TENANT_NAME || SINGLE_TENANT_SLUG
const REGISTRATION_OPEN = process.env.VITE_REGISTRATION_OPEN !== 'false'
const API_URL = process.env.VITE_API_URL || ''

export default defineNuxtConfig({
  ssr: true,

  devtools: { enabled: false },

  srcDir: 'src',

  modules: ['@pinia/nuxt', '@vite-pwa/nuxt'],

  css: ['~/assets/css/material-symbols.css', '~/assets/css/main.css'],

  vite: {
    plugins: [tailwindcss()],
    server: {
      proxy: {
        '/api': {
          target: API_TARGET,
          changeOrigin: true,
        },
      },
    },
  },

  nitro: {
    prerender: {
      autoSubfolderIndex: false,
    },
  },

  routeRules: {
    '/api/**': { proxy: (process.env.VITE_API_TARGET || (process.env.NODE_ENV === 'production' ? 'http://backend:8080' : 'http://localhost:8080')) + '/api/**' },
  },

  pwa: {
    registerType: 'autoUpdate',
    includeAssets: ['favicon-16.png', 'favicon-32.png', 'icon.svg', 'icons/apple-touch-icon.png'],
    manifest: {
      name: 'DulceVida — Panel del negocio',
      short_name: 'DulceVida',
      description: 'Gestión de reservas para cumpleaños infantiles',
      theme_color: '#b5007d',
      background_color: '#ffffff',
      display: 'standalone',
      display_override: ['standalone', 'browser'],
      scope: '/panel/',
      start_url: '/panel/solicitudes',
      lang: 'es',
      icons: [
        { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
        { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
        { src: '/icons/icon-192-maskable.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
        { src: '/icons/icon-512-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
      ],
    },
    workbox: {
      globPatterns: ['**/*.{js,css,html,svg,png,webp,woff2}'],
      navigateFallback: null,
      runtimeCaching: [],
    },
    client: {
      installPrompt: true,
    },
  },

  runtimeConfig: {
    public: {
      apiUrl: API_URL,
      singleTenantSlug: SINGLE_TENANT_SLUG,
      singleTenantName: SINGLE_TENANT_NAME,
      registrationOpen: REGISTRATION_OPEN,
    },
  },

  app: {
    head: {
      htmlAttrs: { lang: 'es' },
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1.0, viewport-fit=cover',
      link: [
        { rel: 'icon', type: 'image/svg+xml', href: '/icon.svg' },
        { rel: 'icon', type: 'image/png', sizes: '32x32', href: '/favicon-32.png' },
        { rel: 'icon', type: 'image/png', sizes: '16x16', href: '/favicon-16.png' },
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/icons/apple-touch-icon.png' },
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@700;800&family=Quicksand:wght@500;600;700&display=swap',
        },
        {
          rel: 'stylesheet',
          href: 'https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@20..48,100..700,0..1,-50..200&display=swap',
        },
      ],
      meta: [
        { name: 'theme-color', content: '#b5007d' },
        { name: 'apple-mobile-web-app-capable', content: 'yes' },
        { name: 'mobile-web-app-capable', content: 'yes' },
        { name: 'apple-mobile-web-app-status-bar-style', content: 'default' },
        { name: 'apple-mobile-web-app-title', content: 'DulceVida' },
      ],
      script: [
        {
          innerHTML: `window.__SINGLE_TENANT_SLUG__ = "${SINGLE_TENANT_SLUG}";`,
          type: 'text/javascript',
        },
      ],
    },
  },

  compatibilityDate: '2025-03-01',
})
