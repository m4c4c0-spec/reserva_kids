<script setup>
import { ref, watch, onUnmounted } from 'vue'

const props = defineProps({
  mensaje: { type: String, default: '' },
  tipo: { type: String, default: 'exito' },
  duracion: { type: Number, default: 3000 }
})

const emit = defineEmits(['cerrar'])
const visible = ref(false)
let timer = null

function mostrar() {
  visible.value = true
  clearTimeout(timer)
  timer = setTimeout(() => {
    visible.value = false
    emit('cerrar')
  }, props.duracion)
}

watch(() => props.mensaje, (nuevo) => {
  if (nuevo) mostrar()
}, { immediate: true })

onUnmounted(() => clearTimeout(timer))
</script>

<template>
  <Transition
    enter-active-class="transition-all duration-300 ease-out"
    enter-from-class="opacity-0 translate-y-4"
    enter-to-class="opacity-100 translate-y-0"
    leave-active-class="transition-all duration-200 ease-in"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-4"
  >
    <div
      v-if="visible && mensaje"
      class="fixed top-4 right-4 z-50 max-w-sm rounded-2xl px-4 py-3 shadow-lifted font-medium text-sm flex items-center gap-2"
      :class="tipo === 'error'
        ? 'bg-error-container text-on-error-container'
        : 'bg-tertiary-fixed text-on-tertiary-fixed'"
      role="alert"
      aria-live="polite"
    >
      <span class="material-symbols-outlined text-base">
        {{ tipo === 'error' ? 'error' : 'check_circle' }}
      </span>
      <span class="flex-1">{{ mensaje }}</span>
      <button
        @click="visible = false; emit('cerrar')"
        class="ml-2 opacity-60 hover:opacity-100 transition-opacity"
        aria-label="Cerrar"
      >
        <span class="material-symbols-outlined text-base">close</span>
      </button>
    </div>
  </Transition>
</template>
