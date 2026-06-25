export default defineNuxtRouteMiddleware((to) => {
  const { slug, isSingleTenant } = useSingleTenant()

  // En modo single-tenant el directorio público no tiene sentido (solo hay un negocio).
  if (isSingleTenant && to.path === '/negocios') {
    return navigateTo(`/clientes/agendar/${slug}`, { replace: true })
  }

  // El home del cliente va directo a sus reservas (no al directorio).
  if (isSingleTenant && to.path === '/clientes') {
    return navigateTo('/clientes/reservas', { replace: true })
  }
})
