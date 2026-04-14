import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

let sessionInitialized = false

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // --- Auth (public) ---
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/auth/LoginView.vue'),
      meta: { requiresAuth: false, layout: 'auth' }
    },
    {
      path: '/account',
      name: 'Account',
      component: () => import('@/views/auth/AccountView.vue'),
      meta: { requiresAuth: true }
    },

    // --- Events ---
    {
      path: '/',
      name: 'EventList',
      component: () => import('@/views/events/EventListView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/events/:id',
      name: 'EventDetail',
      component: () => import('@/views/events/EventDetailView.vue'),
      meta: { requiresAuth: true }
    },

    // --- Registration ---
    {
      path: '/registration/:sessionId',
      name: 'Registration',
      component: () => import('@/views/registration/RegistrationView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/waitlist',
      name: 'Waitlist',
      component: () => import('@/views/registration/WaitlistView.vue'),
      meta: { requiresAuth: true }
    },

    // --- Check-in (staff) ---
    {
      path: '/checkin',
      name: 'CheckIn',
      component: () => import('@/views/checkin/CheckInView.vue'),
      meta: { requiresAuth: true, roles: ['EVENT_STAFF', 'SYSTEM_ADMIN'] }
    },
    {
      path: '/checkin/device-binding',
      name: 'DeviceBinding',
      component: () => import('@/views/checkin/DeviceBindingView.vue'),
      meta: { requiresAuth: true, roles: ['EVENT_STAFF', 'SYSTEM_ADMIN'] }
    },

    // --- Notifications ---
    {
      path: '/notifications',
      name: 'NotificationCenter',
      component: () => import('@/views/notifications/NotificationCenterView.vue'),
      meta: { requiresAuth: true }
    },
    {
      path: '/notifications/settings',
      name: 'SubscriptionSettings',
      component: () => import('@/views/notifications/SubscriptionSettingsView.vue'),
      meta: { requiresAuth: true }
    },

    // --- Finance ---
    {
      path: '/finance',
      name: 'FinanceDashboard',
      component: () => import('@/views/finance/FinanceDashboardView.vue'),
      meta: { requiresAuth: true, roles: ['FINANCE_MANAGER', 'SYSTEM_ADMIN'] }
    },
    {
      path: '/finance/periods',
      name: 'AccountingPeriods',
      component: () => import('@/views/finance/AccountingPeriodsView.vue'),
      meta: { requiresAuth: true, roles: ['FINANCE_MANAGER', 'SYSTEM_ADMIN'] }
    },
    {
      path: '/finance/allocations',
      name: 'Allocations',
      component: () => import('@/views/finance/AllocationView.vue'),
      meta: { requiresAuth: true, roles: ['FINANCE_MANAGER', 'SYSTEM_ADMIN'] }
    },

    // --- Admin ---
    {
      path: '/admin/audit',
      name: 'AuditLog',
      component: () => import('@/views/admin/AuditLogView.vue'),
      meta: { requiresAuth: true, roles: ['SYSTEM_ADMIN'] }
    },
    {
      path: '/admin/security',
      name: 'SecuritySettings',
      component: () => import('@/views/admin/SecuritySettingsView.vue'),
      meta: { requiresAuth: true, roles: ['SYSTEM_ADMIN'] }
    },
    {
      path: '/admin/backups',
      name: 'BackupStatus',
      component: () => import('@/views/admin/BackupStatusView.vue'),
      meta: { requiresAuth: true, roles: ['SYSTEM_ADMIN'] }
    },
    {
      path: '/admin/users',
      name: 'UserManagement',
      component: () => import('@/views/admin/UserManagementView.vue'),
      meta: { requiresAuth: true, roles: ['SYSTEM_ADMIN'] }
    },

    // --- Imports ---
    {
      path: '/imports',
      name: 'ImportDashboard',
      component: () => import('@/views/imports/ImportDashboardView.vue'),
      meta: { requiresAuth: true, roles: ['EVENT_STAFF', 'SYSTEM_ADMIN'] }
    },

    // --- Exports ---
    {
      path: '/exports',
      name: 'ExportDashboard',
      component: () => import('@/views/exports/ExportDashboardView.vue'),
      meta: { requiresAuth: true, roles: ['EVENT_STAFF', 'FINANCE_MANAGER', 'SYSTEM_ADMIN'] }
    }
  ]
})

// Navigation guard — enforces authentication and role-based access
router.beforeEach((to, from, next) => {
  const authStore = useAuthStore()

  // Restore session state on first navigation
  if (!sessionInitialized) {
    authStore.initFromSession()
    sessionInitialized = true
  }

  // Check if route requires authentication (default: true)
  const requiresAuth = to.meta.requiresAuth !== false

  if (requiresAuth && !authStore.isAuthenticated) {
    return next({
      path: '/login',
      query: { redirect: to.fullPath }
    })
  }

  // Check role-based access if route specifies required roles
  if (to.meta.roles && to.meta.roles.length > 0) {
    if (!authStore.hasAnyRole(to.meta.roles)) {
      return next({
        path: '/',
        query: { forbidden: 'true' }
      })
    }
  }

  next()
})

export default router
