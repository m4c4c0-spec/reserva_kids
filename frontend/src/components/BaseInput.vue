<script setup>
import { useId } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number], default: '' },
  tipo: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  icono: { type: String, default: '' },
  requerido: { type: Boolean, default: false },
  deshabilitado: { type: Boolean, default: false },
  // A11y: etiqueta accesible. `label` renderiza un <label> visible asociado por id;
  // `ariaLabel` asocia un nombre accesible sin etiqueta visible (campos compactos);
  // `id` permite asociar un <label for> externo (labels con formato propio: spans, etc.).
  label: { type: String, default: '' },
  ariaLabel: { type: String, default: '' },
  id: { type: String, default: '' },
})

const emit = defineEmits(['update:modelValue'])

// id estable y único para asociar <label for> ↔ <input id> (WCAG 1.3.1 / 4.1.2).
// Se respeta el id del padre si lo pasa (para asociar un <label for> externo).
const generatedId = useId()
const inputId = props.id || generatedId
</script>

<template>
  <div>
    <label v-if="label" :for="inputId" class="block text-sm font-bold text-on-surface mb-1">
      {{ label }}<span v-if="requerido" class="text-error" aria-hidden="true"> *</span>
    </label>
    <div class="relative">
      <span
        v-if="icono"
        class="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-on-surface-variant text-xl pointer-events-none"
      >
        {{ icono }}
      </span>
      <input
        :id="inputId"
        :type="tipo"
        :value="modelValue"
        :placeholder="placeholder"
        :required="requerido"
        :disabled="deshabilitado"
        :aria-label="!label && ariaLabel ? ariaLabel : undefined"
        class="w-full border-2 border-surface-highest bg-surface rounded-xl px-3 py-2.5 font-medium text-sm text-on-surface placeholder:text-on-surface-variant/50 focus:border-secondary focus:outline-none transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
        :class="icono ? 'pl-11' : ''"
        @input="emit('update:modelValue', $event.target.value)"
      />
    </div>
  </div>
</template>
