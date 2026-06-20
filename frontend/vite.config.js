import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

// El backend (Spring) corre en :8080 dentro de docker compose.
const API_TARGET = process.env.VITE_API_TARGET || 'http://localhost:8080'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
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
