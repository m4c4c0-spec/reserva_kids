import { ref } from 'vue'

// beforeinstallprompt es un evento Chrome/Android/Edge. iOS Safari NO lo dispara:
// ahí el usuario debe usar "Compartir → Añadir a pantalla de inicio" manualmente
// (documentado en MANUAL_DESPLIEGUE.md). Por eso este composable es opt-in y el
// botón que lo consume solo aparece cuando el navegador confirma que se puede instalar.
let eventoPendiente = null

if (typeof window !== 'undefined') {
  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault() // Evita que Chrome muestre su propio minibanner
    eventoPendiente = e
  })
  // appinstalled: limpia el estado cuando ya se instaló (no volver a ofrecer)
  window.addEventListener('appinstalled', () => {
    eventoPendiente = null
  })
}

export const puedeInstalar = ref(false)

// Polling ligero: el evento puede haberse disparado antes de que el composable
// se importara. Comprobamos si hay evento pendiente al primer acceso.
if (typeof window !== 'undefined') {
  puedeInstalar.value = eventoPendiente !== null
  // Re-check cuando el componente se monta (el evento pudo llegar tras el import)
  setTimeout(() => {
    puedeInstalar.value = eventoPendiente !== null
  }, 0)
}

export async function instalar() {
  if (!eventoPendiente) return false
  eventoPendiente.prompt()
  const { outcome } = await eventoPendiente.userChoice
  eventoPendiente = null
  puedeInstalar.value = false
  return outcome === 'accepted'
}
