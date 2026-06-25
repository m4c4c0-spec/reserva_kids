<script setup>
const router = useRouter()
const cargandoPagina = ref(false)
const mensajeCarga = ref('Cargando...')
let inicioCarga = 0

const paginasTransicion = new Set(['/', '/negocios_duenos', '/negocios', '/clientes/entrar'])

router.beforeEach((to, from) => {
  if (!from || !from.path) return // SSR initial load — no mostrar loader
  const navegandoDesdeLanding = paginasTransicion.has(from.path)
  const volviendoALanding = to.path === '/'
  if (navegandoDesdeLanding || volviendoALanding) {
    cargandoPagina.value = true
    inicioCarga = Date.now()
    if (to.path === '/negocios_duenos') {
      mensajeCarga.value = 'Preparando tu panel...'
    } else if (to.path.startsWith('/clientes/agendar') || to.path === '/negocios') {
      mensajeCarga.value = 'Buscando salones...'
    } else {
      mensajeCarga.value = 'Cargando...'
    }
  }
})

router.afterEach(() => {
  const transcurrido = Date.now() - inicioCarga
  const minimo = 1400
  const espera = Math.max(0, minimo - transcurrido)
  setTimeout(() => {
    cargandoPagina.value = false
  }, espera)
})
</script>

<template>
  <NuxtLayout>
    <NuxtPage :transition="{ name: 'page', mode: 'out-in' }" />
  </NuxtLayout>

  <CottonCandyLoader v-if="cargandoPagina" :mensaje="mensajeCarga" />
</template>
