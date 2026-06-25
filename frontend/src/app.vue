<script setup>
const router = useRouter()
const cargandoPagina = ref(false)
const mensajeCarga = ref('Cargando...')
let inicioCarga = 0

router.beforeEach((to, from) => {
  if (from.path === '/' || to.path === '/' || from.path === '/negocios_duenos' || from.path === '/negocios') {
    cargandoPagina.value = true
    inicioCarga = Date.now()
    if (to.path === '/negocios_duenos' || from.path === '/negocios_duenos') {
      mensajeCarga.value = 'Preparando tu panel...'
    } else if (to.path === '/negocios' || to.path.startsWith('/clientes/agendar')) {
      mensajeCarga.value = 'Buscando salones...'
    } else if (to.path === '/clientes/entrar' || from.path === '/clientes/entrar') {
      mensajeCarga.value = 'Preparando acceso...'
    } else {
      mensajeCarga.value = 'Cargando...'
    }
  }
})

router.afterEach(() => {
  const transcurrido = Date.now() - inicioCarga
  const minimo = 1600
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
