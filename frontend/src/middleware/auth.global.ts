export default defineNuxtRouteMiddleware((to) => {
  if (import.meta.server) return

  const config = useRuntimeConfig()
  const isSingleTenant = !!(
    (typeof window !== 'undefined' && window.__SINGLE_TENANT_SLUG__) ||
    config.public.singleTenantSlug
  )

  try {
    const auth = useAuthStore()
    const clienteAuth = useClienteAuthStore()
    const adminAuth = useAdminAuthStore()
    const staff = useStaffStore()

    if (auth.autenticado && (to.meta.requiereCliente || to.meta.requiereAdmin || to.meta.requiereStaff))
      return navigateTo('/panel')

    if (clienteAuth.autenticado && (to.meta.requiereAuth || to.meta.requiereAdmin || to.meta.requiereStaff))
      return navigateTo(isSingleTenant ? '/clientes/reservas' : '/clientes')

    if (adminAuth.autenticado && (to.meta.requiereAuth || to.meta.requiereCliente || to.meta.requiereStaff))
      return navigateTo('/admin/metricas')

    if (staff.autenticado && (to.meta.requiereAuth || to.meta.requiereCliente || to.meta.requiereAdmin))
      return navigateTo('/staff/calendario')

    if (to.meta.requiereAuth && !auth.autenticado) return navigateTo('/login')
    if (to.meta.requiereCliente && !clienteAuth.autenticado) return navigateTo('/clientes/entrar')
    if (to.meta.requiereAdmin && !adminAuth.autenticado) return navigateTo('/admin/login')
    if (to.meta.requiereStaff && !staff.autenticado) return navigateTo('/staff/entrar')

    if (to.meta.soloInvitados === 'dueno' && auth.autenticado) return navigateTo('/panel')
    if (
      to.meta.soloInvitados === 'cliente' &&
      clienteAuth.autenticado
    )
      return navigateTo(isSingleTenant ? '/clientes/reservas' : '/clientes')
    if (to.meta.soloInvitados === 'admin' && adminAuth.autenticado)
      return navigateTo('/admin/metricas')
    if (to.meta.soloInvitados === 'staff' && staff.autenticado)
      return navigateTo('/staff/calendario')
  } catch {
    /* stores may not be initialized on server */
  }
})
