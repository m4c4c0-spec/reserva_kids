import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

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
      ],
    },
    // Mini-sitio público del negocio (RF-03): reservakids.cl/{slug}
    { path: '/:slug', component: () => import('../views/PublicSiteView.vue') },
  ],
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiereAuth && !auth.autenticado) return '/login'
})

export default router
