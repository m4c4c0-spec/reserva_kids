<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  titulo: { type: String, default: '' },
})

const emit = defineEmits(['confirmar', 'cancelar', 'cerrar'])

const dialogRef = ref(null)
let focoPrevio = null

// Selectores de elementos focusable en orden de tab natural.
const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
]

function focusables() {
  const root = dialogRef.value
  if (!root) return []
  return [...root.querySelectorAll(FOCUSABLE.join(','))].filter((el) => el.offsetParent !== null)
}

function enfoqueInicial() {
  const elems = focusables()
  if (elems.length) {
    // Salteamos el backdrop y enfocamos el primer control interactivo del panel.
    ;(elems[0] ?? dialogRef.value).focus({ focusVisible: true })
  } else {
    dialogRef.value?.focus()
  }
}

function restaurarFoco() {
  if (focoPrevio && typeof focoPrevio.focus === 'function') {
    focoPrevio.focus({ focusVisible: true })
    focoPrevio = null
  }
}

function alTab(e) {
  if (!props.visible) return
  const elems = focusables()
  if (!elems.length) {
    e.preventDefault()
    dialogRef.value?.focus()
    return
  }
  const primero = elems[0]
  const ultimo = elems[elems.length - 1]
  const activo = document.activeElement
  if (e.shiftKey && activo === primero) {
    e.preventDefault()
    ultimo.focus()
  } else if (!e.shiftKey && activo === ultimo) {
    e.preventDefault()
    primero.focus()
  } else if (!elems.includes(activo)) {
    // El foco salió del diálogo (p. ej. tabla de dibujo): lo devolvemos.
    e.preventDefault()
    primero.focus()
  }
}

function alEscape(e) {
  if (!props.visible) return
  if (e.key === 'Escape') {
    e.stopPropagation()
    emit('cerrar')
  }
}

watch(
  () => props.visible,
  async (abierto) => {
    if (abierto) {
      focoPrevio = document.activeElement
      window.addEventListener('keydown', alTab)
      window.addEventListener('keydown', alEscape)
      await nextTick()
      enfoqueInicial()
    } else {
      window.removeEventListener('keydown', alTab)
      window.removeEventListener('keydown', alEscape)
      restaurarFoco()
    }
  },
)

onBeforeUnmount(() => {
  window.removeEventListener('keydown', alTab)
  window.removeEventListener('keydown', alEscape)
  restaurarFoco()
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-all duration-200 ease-out"
      enter-from-class="opacity-0"
      enter-to-class="opacity-100"
      leave-active-class="transition-all duration-150 ease-in"
      leave-from-class="opacity-100"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        ref="dialogRef"
        tabindex="-1"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 outline-none"
        role="dialog"
        aria-modal="true"
        :aria-label="titulo"
      >
        <div class="absolute inset-0 bg-on-surface/40 backdrop-blur-sm" @click="emit('cerrar')"></div>
        <div class="relative bg-surface-lowest rounded-3xl shadow-lifted w-full max-w-md p-6 z-10">
          <h3 v-if="titulo" class="text-lg font-bold text-on-surface mb-4 font-display">
            {{ titulo }}
          </h3>
          <div class="mb-6">
            <slot />
          </div>
          <div class="flex justify-end gap-3">
            <slot name="acciones">
              <button
                class="px-4 py-2 rounded-full font-bold text-sm border-2 border-outline-variant text-on-surface-variant hover:bg-surface-container transition-colors"
                @click="emit('cancelar')"
              >
                Cancelar
              </button>
              <button
                class="px-4 py-2 rounded-full font-bold text-sm bg-primary-container text-on-primary-container hover:bg-primary-fixed shadow-md transition-all active:scale-95"
                @click="emit('confirmar')"
              >
                Confirmar
              </button>
            </slot>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
