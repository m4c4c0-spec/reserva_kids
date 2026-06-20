<script setup>
import { useRouter } from 'vue-router'
import { useAdminAuthStore } from '../stores/adminAuth'

const router = useRouter()
const auth = useAdminAuthStore()

const tabs = [{ to: '/admin/negocios', label: 'Negocios', icon: 'storefront' }]

async function salir() {
  await auth.logout()
  router.push('/admin/login')
}
</script>

<template>
  <div class="min-h-screen bg-surface flex flex-col">
    <header class="sticky top-0 z-40 bg-surface-low/90 backdrop-blur-sm px-5 py-3 flex items-center justify-between">
      <div class="flex items-center gap-3 min-w-0">
        <div class="w-9 h-9 rounded-xl bg-primary-container flex items-center justify-center shrink-0">
          <span class="material-symbols-outlined text-on-primary-container text-[20px]">admin_panel_settings</span>
        </div>
        <div class="min-w-0">
          <h1 class="font-display font-bold text-primary leading-tight truncate">Consola de plataforma</h1>
          <p class="text-xs font-bold text-on-surface-variant truncate">{{ auth.email }}</p>
        </div>
      </div>
      <button
        aria-label="Salir"
        class="p-2 rounded-full hover:bg-surface-highest transition-colors active:scale-95"
        @click="salir"
      >
        <span class="material-symbols-outlined text-primary">logout</span>
      </button>
    </header>

    <nav class="px-5 pt-3 flex gap-1" role="navigation" aria-label="Navegación de administración">
      <RouterLink
        v-for="t in tabs"
        :key="t.to"
        :to="t.to"
        class="flex items-center gap-2 px-4 py-2 rounded-full text-on-surface-variant transition-all font-bold text-sm hover:bg-surface-highest"
        active-class="!bg-primary-container !text-on-primary-container shadow-sm"
      >
        <span class="material-symbols-outlined text-[20px]">{{ t.icon }}</span>
        <span>{{ t.label }}</span>
      </RouterLink>
    </nav>

    <main class="flex-1 w-full max-w-5xl mx-auto p-5 md:p-8">
      <RouterView />
    </main>
  </div>
</template>
