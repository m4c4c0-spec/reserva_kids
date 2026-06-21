import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useClienteAuthStore } from '../stores/clienteAuth'
import { useAdminAuthStore } from '../stores/adminAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: () => import('../views/LandingPageView.vue') },
    {
      path: '/login',
      component: () => import('../views/LoginView.vue'),
      meta: { soloInvitados: 'dueno' },
    },
    { path: '/reset', component: () => import('../views/ResetPasswordView.vue') },
    {
      path: '/panel',
      component: () => import('../views/DashboardLayout.vue'),
      meta: { requiereAuth: true },
      children: [
        { path: '', redirect: '/panel/solicitudes' },
        { path: 'solicitudes', component: () => import('../views/SolicitudesView.vue') },
        { path: 'servicios', component: () => import('../views/ServiciosView.vue') },
        { path: 'calendario', component: () => import('../views/CalendarioView.vue') },
        { path: 'configuracion', component: () => import('../views/ConfiguracionView.vue') },
      ],
    },
    {
      path: '/clientes/entrar',
      component: () => import('../views/ClienteLoginView.vue'),
      meta: { soloInvitados: 'cliente' },
    },
    { path: '/privacidad', component: () => import('../views/PrivacidadView.vue') },
    { path: '/clientes', component: () => import('../views/ClienteHomeView.vue'), meta: { requiereCliente: true } },
    {
      path: '/clientes/negocios',
      component: () => import('../views/DirectorioView.vue'),
      meta: { requiereCliente: true },
    },
    {
      path: '/clientes/reservas',
      component: () => import('../views/ClienteHistorialView.vue'),
      meta: { requiereCliente: true },
    },
    {
      path: '/clientes/agendar/:slug',
      component: () => import('../views/AgendarView.vue'),
      meta: { requiereCliente: true },
    },
    { path: '/clientes/reset', component: () => import('../views/ClienteResetView.vue') },
    { path: '/clientes/reset/confirmar', component: () => import('../views/ClienteResetConfirmView.vue') },
    // Consola de plataforma (admin). Va ANTES del catch-all /:slug: si no, /admin
    // (un solo segmento) lo capturaría PublicSiteView como si fuera un slug de negocio.
    {
      path: '/admin/login',
      component: () => import('../views/AdminLoginView.vue'),
      meta: { soloInvitados: 'admin' },
    },
    {
      path: '/admin',
      component: () => import('../views/AdminLayout.vue'),
      meta: { requiereAdmin: true },
      children: [
        { path: '', redirect: '/admin/metricas' },
        { path: 'metricas', component: () => import('../views/AdminMetricasView.vue') },
        { path: 'negocios', component: () => import('../views/AdminNegociosView.vue') },
        { path: 'auditoria', component: () => import('../views/AdminAuditoriaView.vue') },
        { path: 'administradores', component: () => import('../views/AdminAdminsView.vue') },
      ],
    },
    { path: '/404', component: () => import('../views/NotFoundView.vue') },
    // Página pública de cada negocio: ruta dedicada con :slug param explícito.
    // Debe ir ANTES del catch-all para que route.params.slug esté definido
    // (la catch-all exponeroute.params.pathMatch, no slug).
    { path: '/:slug', component: () => import('../views/PublicSiteView.vue') },
    // Catch-all: lo que no matchea nada (incluida la ruta /:slug si el catalogo
    // responde noExiste) caer al 404 dedicado en vez de a PublicSiteView.
    { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  const clienteAuth = useClienteAuthStore()
  const adminAuth = useAdminAuthStore()

  // Rutas protegidas: requieren sesión activa.
  if (to.meta.requiereAuth && !auth.autenticado) return '/login'
  if (to.meta.requiereCliente && !clienteAuth.autenticado) return '/clientes/entrar'
  if (to.meta.requiereAdmin && !adminAuth.autenticado) return '/admin/login'

  // Rutas "solo invitados": si ya tiene sesión, lo mandamos a su panel en vez
  // de mostrarle de nuevo el formulario de login (UX + evita dobles sesiones).
  if (to.meta.soloInvitados === 'dueno' && auth.autenticado) return '/panel'
  if (to.meta.soloInvitados === 'cliente' && clienteAuth.autenticado) return '/clientes'
  if (to.meta.soloInvitados === 'admin' && adminAuth.autenticado) return '/admin/metricas'
})

export default router
