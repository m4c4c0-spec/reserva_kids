import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vuePrettier from '@vue/eslint-config-prettier'
import globals from 'globals'

export default [
  {
    // Salidas generadas por Nuxt/Nitro y dependencias: no son código fuente.
    ignores: ['dist/**', 'node_modules/**', '.nuxt/**', '.output/**', 'public/**'],
  },
  js.configs.recommended,
  ...pluginVue.configs['flat/recommended'],
  {
    // Archivos de configuración que corren en Node (no en el bundle del navegador)
    files: ['*.config.js', 'eslint.config.js'],
    languageOptions: {
      globals: { ...globals.node, process: 'readonly' },
    },
  },
  {
    // Tests: entornos jsdom + vitest globals
    files: ['tests/**/*.spec.js'],
    languageOptions: {
      globals: {
        ...globals.browser,
        ...globals.node,
        vi: 'readonly',
        describe: 'readonly',
        it: 'readonly',
        expect: 'readonly',
        beforeEach: 'readonly',
      },
    },
  },
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      globals: {
        // ── Auto-imports de Nuxt ──
        definePageMeta: 'readonly',
        defineNuxtRouteMiddleware: 'readonly',
        defineNuxtPlugin: 'readonly',
        navigateTo: 'readonly',
        useRouter: 'readonly',
        useRoute: 'readonly',
        useRuntimeConfig: 'readonly',
        useRequestURL: 'readonly',
        useNuxtApp: 'readonly',
        useHead: 'readonly',
        useSeoMeta: 'readonly',
        useState: 'readonly',
        useCookie: 'readonly',
        // ── Auto-imports de Vue (reactividad + ciclo de vida) ──
        ref: 'readonly',
        reactive: 'readonly',
        computed: 'readonly',
        watch: 'readonly',
        watchEffect: 'readonly',
        nextTick: 'readonly',
        onMounted: 'readonly',
        onUnmounted: 'readonly',
        onBeforeMount: 'readonly',
        onBeforeUnmount: 'readonly',
        // ── Stores Pinia (auto-importados desde src/stores por @pinia/nuxt) ──
        useAuthStore: 'readonly',
        useClienteAuthStore: 'readonly',
        useAdminAuthStore: 'readonly',
        useStaffStore: 'readonly',
        // ── Composables propios auto-importados desde src/composables ──
        instalar: 'readonly',
        puedeInstalar: 'readonly',
        process: 'readonly',
      },
    },
    rules: {
      'vue/multi-word-component-names': 'off',
      'vue/no-v-html': 'error',
      'vue/require-default-prop': 'warn',
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'no-console': ['warn', { allow: ['warn', 'error'] }],
      'no-debugger': 'error',
      'prefer-const': 'error',
      eqeqeq: ['error', 'smart'],
    },
  },
  vuePrettier,
]
