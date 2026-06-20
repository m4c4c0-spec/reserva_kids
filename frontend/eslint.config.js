import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import vuePrettier from '@vue/eslint-config-prettier'
import globals from 'globals'

export default [
  {
    ignores: ['dist/**', 'node_modules/**'],
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
