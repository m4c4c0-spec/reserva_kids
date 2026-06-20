import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'
import { VitePWA } from 'vite-plugin-pwa'

// El backend (Spring) corre en :8080 dentro de docker compose.
const API_TARGET = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      includeAssets: ['favicon-16.png', 'favicon-32.png', 'icon.svg', 'icons/apple-touch-icon.png'],
      manifest: {
        name: 'ReservaKids — Panel del negocio',
        short_name: 'ReservaKids',
        description: 'Gestión de reservas para cumpleaños infantiles',
        theme_color: '#7C4DFF',
        background_color: '#ffffff',
        // display_override: standalone primero, browser como fallback. iOS usa
        // apple-mobile-web-app-capable (index.html) en vez de este campo.
        display: 'standalone',
        display_override: ['standalone', 'browser'],
        scope: '/panel/',
        start_url: '/panel/solicitudes',
        lang: 'es',
        icons: [
          // any: sirve para cualquier propósito (barra de tareas, splash, etc.)
          { src: '/icons/icon-192.png', sizes: '192x192', type: 'image/png', purpose: 'any' },
          { src: '/icons/icon-512.png', sizes: '512x512', type: 'image/png', purpose: 'any' },
          // maskable: icono con padding seguro para que Android recorte sin perder contenido
          { src: '/icons/icon-192-maskable.png', sizes: '192x192', type: 'image/png', purpose: 'maskable' },
          { src: '/icons/icon-512-maskable.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        // Precache del shell estático únicamente. Las respuestas de la API
        // (datos de negocio, reservas, calendario) no se cachean: siempre frescas.
        globPatterns: ['**/*.{js,css,html,svg,png,webp,woff2}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api\//],
        runtimeCaching: [],
      },
    }),
  ],
  server: {
    // Escucha solo en localhost por defecto (no expone el dev server a la LAN:
    // los CVEs de path-traversal/file-read del dev server de Vite se acotan así).
    // Para acceder desde otro dispositivo de la red, arranca con `vite --host`.
    port: 5173,
    strictPort: true, // si 5173 está ocupado, falla en vez de saltar a otro puerto (rompía CORS)
    // El WebSocket de HMR se sirve en el mismo origen/puerto (5173); con strictPort fijo,
    // el cliente siempre lo encuentra. Si accedes por una IP/host distinto, descomenta:
    // hmr: { host: 'localhost' },
    proxy: {
      // Todo /api/** se reenvía al backend. El navegador habla con un solo origen (:5173),
      // así que no dependemos de CORS y los datos (negocio, agendas) cargan en dev sin fricción.
      '/api': {
        target: API_TARGET,
        changeOrigin: true,
      },
    },
  },
})
