<script setup>
defineProps({
  visible: { type: Boolean, default: false },
  titulo: { type: String, default: '' }
})

const emit = defineEmits(['confirmar', 'cancelar', 'cerrar'])
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
        class="fixed inset-0 z-50 flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        :aria-label="titulo"
      >
        <div
          class="absolute inset-0 bg-on-surface/40 backdrop-blur-sm"
          @click="emit('cerrar')"
        ></div>
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
                @click="emit('cancelar')"
                class="px-4 py-2 rounded-full font-bold text-sm border-2 border-outline-variant text-on-surface-variant hover:bg-surface-container transition-colors"
              >
                Cancelar
              </button>
              <button
                @click="emit('confirmar')"
                class="px-4 py-2 rounded-full font-bold text-sm bg-primary-container text-on-primary-container hover:bg-primary-fixed shadow-md transition-all active:scale-95"
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
