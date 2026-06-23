<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useClienteAuthStore } from '../../stores/clienteAuth'
import { useAdminAuthStore } from '../../stores/adminAuth'
import BaseButton from '../../components/BaseButton.vue'
import ErrorBanner from '../../components/ErrorBanner.vue'

definePageMeta({ layout: 'default' })

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const clienteAuth = useClienteAuthStore()
const adminAuth = useAdminAuthStore()

const error = ref('')
const cargando = ref(true)
const estado = ref('Conectando con el proveedor...')

const code = route.query.code
const rawState = route.query.state || ''
const errorParam = route.query.error

const partes = rawState.includes('|') ? rawState.split('|') : []
const type = partes.length >= 2 ? partes[0] : 'cliente'
const provider = partes.length >= 2 ? partes[1] : 'google'

const necesitaDatosNegocio = ref(false)
const nombreNegocio = ref('')
const slug = ref('')

const redirectUri = computed(() => {
  if (!process.client) return ''
  return window.location.origin + '/oauth2/callback'
})

async function canjear(nombreNegocioVal, slugVal) {
  estado.value = 'Verificando identidad...'
  cargando.value = true
  error.value = ''
  try {
    if (type === 'dueno') {
      await auth.oauth2Login(provider, code, redirectUri.value, rawState, nombreNegocioVal, slugVal)
      router.push('/panel')
    } else if (type === 'admin') {
      await adminAuth.oauth2Login(provider, code, redirectUri.value, rawState)
      router.push('/admin/metricas')
    } else {
      await clienteAuth.oauth2Login(provider, code, redirectUri.value, rawState)
      router.push('/clientes')
    }
  } catch (e) {
    const msg = e.response?.data?.message || ''
    if (type === 'dueno' && msg.includes('necesitas indicar el nombre')) {
      necesitaDatosNegocio.value = true
      estado.value = ''
      error.value = ''
      cargando.value = false
      return
    }
    error.value = msg || 'Error al autenticar con el proveedor'
    cargando.value = false
  }
}

async function procesar() {
  if (errorParam) {
    error.value = 'El proveedor rechazó la autenticación: ' + errorParam
    cargando.value = false
    return
  }
  if (!code) {
    error.value = 'No se recibió el código de autorización. Intenta de nuevo.'
    cargando.value = false
    return
  }
  if (!rawState || partes.length < 2) {
    error.value = 'Estado de autenticación inválido. Intenta de nuevo.'
    cargando.value = false
    return
  }
  await canjear(null, null)
}

async function completarRegistro() {
  if (!nombreNegocio.value.trim() || !slug.value.trim()) {
    error.value = 'Completa el nombre y slug de tu negocio'
    return
  }
  await canjear(nombreNegocio.value.trim(), slug.value.trim())
}

onMounted(procesar)

function volver() {
  if (type === 'dueno') router.push('/login')
  else if (type === 'admin') router.push('/admin/login')
  else router.push('/clientes/entrar')
}

function nombreProveedor() {
  return provider === 'google' ? 'Google' : provider === 'microsoft' ? 'Microsoft' : provider === 'apple' ? 'Apple' : provider
}
</script>

<template>
  <main class="min-h-screen bg-surface flex flex-col justify-center items-center p-5">
    <div class="w-full max-w-md text-center">
      <div v-if="cargando && !necesitaDatosNegocio" class="space-y-4">
        <div class="flex justify-center">
          <div class="w-16 h-16 rounded-full bg-primary-container flex items-center justify-center shadow-soft">
            <span class="material-symbols-outlined text-on-primary-container text-3xl animate-spin">progress_activity</span>
          </div>
        </div>
        <h1 class="font-display font-extrabold text-2xl tracking-tight text-primary">Iniciando sesión</h1>
        <p class="text-on-surface-variant font-medium">{{ estado }}</p>
      </div>

      <div v-else-if="necesitaDatosNegocio" class="space-y-6">
        <div class="flex justify-center">
          <div class="w-16 h-16 rounded-full bg-tertiary-container flex items-center justify-center shadow-soft">
            <span class="material-symbols-outlined text-on-tertiary-container text-3xl">storefront</span>
          </div>
        </div>
        <h1 class="font-display font-extrabold text-2xl tracking-tight text-primary">¡Primera vez!</h1>
        <p class="text-on-surface-variant font-medium">
          Completa los datos de tu negocio para crear tu cuenta con {{ nombreProveedor() }}
        </p>

        <form class="space-y-4 text-left" @submit.prevent="completarRegistro">
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1">Nombre del negocio</label>
            <input
              v-model="nombreNegocio"
              required
              maxlength="120"
              placeholder="Fiestas Pepito"
              class="w-full rounded-xl border border-outline bg-surface-container-lowest px-4 py-3 text-on-surface placeholder:text-on-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
              @focus="error = ''"
            />
          </div>
          <div>
            <label class="block text-sm font-bold text-on-surface mb-1">Identificador (slug)</label>
            <input
              v-model="slug"
              required
              pattern="[a-z0-9-]{3,60}"
              placeholder="fiestas-pepito"
              class="w-full rounded-xl border border-outline bg-surface-container-lowest px-4 py-3 text-on-surface placeholder:text-on-surface-variant focus:outline-none focus:ring-2 focus:ring-primary"
              @focus="error = ''"
            />
            <p class="text-xs text-on-surface-variant mt-1">
              Solo minúsculas, números y guiones. Así te encuentran: reservakids.cl/<strong>{{ slug || 'tunegocio' }}</strong>
            </p>
          </div>
          <ErrorBanner v-if="error" :mensaje="error" />
          <BaseButton variante="primario" type="submit" class="w-full py-3">
            Crear cuenta y entrar
          </BaseButton>
        </form>

        <BaseButton variante="secundario" class="w-full py-3" @click="volver">
          Cancelar
        </BaseButton>
      </div>

      <div v-else class="space-y-6">
        <div class="flex justify-center">
          <div class="w-16 h-16 rounded-full bg-error-container flex items-center justify-center shadow-soft">
            <span class="material-symbols-outlined text-on-error-container text-3xl">error</span>
          </div>
        </div>
        <h1 class="font-display font-extrabold text-2xl tracking-tight text-error">No se pudo iniciar sesión</h1>
        <ErrorBanner :mensaje="error || 'Error inesperado'" />
        <BaseButton variante="primario" class="w-full py-3" @click="volver">
          Volver al inicio de sesión
        </BaseButton>
      </div>
    </div>
  </main>
</template>
