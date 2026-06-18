import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useClienteAuthStore } from '../stores/clienteAuth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    { path: '/login', component: () => import('../views/LoginView.vue') },
    // Falla 1.3 (5 años): reset de contraseña — "reset" está en SLUGS_RESERVADOS del backend
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
    // Área de cliente (apoderado): login propio + directorio de negocios con disponibilidad.
    // Deben ir ANTES del catch-all /:slug para que "clientes" no se interprete como un slug.
    { path: '/clientes/entrar', component: () => import('../views/ClienteLoginView.vue') },
    { path: '/clientes', component: () => import('../views/DirectorioView.vue'), meta: { requiereCliente: true } },
    // Mini-sitio público del negocio (RF-03): reservakids.cl/{slug}
    { path: '/:slug', component: () => import('../views/PublicSiteView.vue') },
  ],
})

router.beforeEach((to) => {
  if (to.meta.requiereAuth && !useAuthStore().autenticado) return '/login'
  if (to.meta.requiereCliente && !useClienteAuthStore().autenticado) return '/clientes/entrar'
})

export default router
