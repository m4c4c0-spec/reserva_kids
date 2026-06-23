export default defineNuxtRouteMiddleware((to) => {
  const config = useRuntimeConfig()
  const slug =
    (typeof window !== 'undefined' && window.__SINGLE_TENANT_SLUG__) ||
    config.public.singleTenantSlug

  const isSingleTenant = !!(slug && slug.length > 0)

  // En modo single-tenant el directorio público no tiene sentido (solo hay un negocio).
  if (isSingleTenant && to.path === '/negocios') {
    return navigateTo('/404', { replace: true })
  }

  // El home del cliente va directo a sus reservas (no al directorio).
  if (isSingleTenant && to.path === '/clientes') {
    return navigateTo('/clientes/reservas', { replace: true })
  }
})
