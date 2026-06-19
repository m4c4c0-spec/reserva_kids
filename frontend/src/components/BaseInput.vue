<script setup>
const props = defineProps({
  modeloValue: { type: [String, Number], default: '' },
  tipo: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  icono: { type: String, default: '' },
  requerido: { type: Boolean, default: false },
  deshabilitado: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modeloValue'])
</script>

<template>
  <div class="relative">
    <span
      v-if="icono"
      class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-xl pointer-events-none"
    >
      {{ icono }}
    </span>
    <input
      :type="tipo"
      :value="modeloValue"
      :placeholder="placeholder"
      :required="requerido"
      :disabled="deshabilitado"
      @input="emit('update:modeloValue', $event.target.value)"
      class="w-full border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:border-secondary focus:outline-none transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      :class="icono ? 'pl-11' : ''"
    />
  </div>
</template>
