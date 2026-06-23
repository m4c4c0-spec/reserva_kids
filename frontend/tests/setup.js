// Stubs de los auto-imports de Nuxt que los stores/servicios usan en runtime.
// En la app real Nuxt los inyecta globalmente; bajo vitest hay que definirlos
// o `useRuntimeConfig?.()` lanza ReferenceError (el optional chaining no protege
// contra un identificador no declarado, solo contra un valor null/undefined).
globalThis.useRuntimeConfig = () => ({ public: { apiUrl: '', singleTenantSlug: '' } })

// Los stores solo tocan sessionStorage cuando corren en cliente (process.client).
// jsdom provee sessionStorage; marcamos el entorno como cliente para ejercitarlo.
process.client = true
process.server = false
