<script setup>
const router = useRouter()
const auth = useAdminAuthStore()

const tabs = [
  { to: '/admin/metricas', label: 'Métricas', icon: 'monitoring' },
  { to: '/admin/negocios', label: 'Negocios', icon: 'storefront' },
  { to: '/admin/auditoria', label: 'Bitácora', icon: 'history' },
  { to: '/admin/administradores', label: 'Admins', icon: 'shield_person' },
]

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
        class="font-bold text-sm text-on-surface-variant hover:text-primary transition-colors"
        @click="salir"
      >
        Salir
      </button>
    </header>

    <nav class="flex overflow-x-auto gap-1 px-5 pb-2 no-scrollbar border-b border-outline-variant/20">
      <NuxtLink
        v-for="t in tabs"
        :key="t.to"
        :to="t.to"
        class="flex items-center gap-2 text-on-surface-variant px-4 py-2 rounded-full font-bold text-sm whitespace-nowrap transition-colors"
        active-class="bg-primary-container text-on-primary-container"
      >
        <span class="material-symbols-outlined text-[20px]">{{ t.icon }}</span>
        {{ t.label }}
      </NuxtLink>
    </nav>

    <main class="flex-1 w-full max-w-6xl mx-auto p-5 md:p-8 pb-8">
      <slot />
    </main>
  </div>
</template>
