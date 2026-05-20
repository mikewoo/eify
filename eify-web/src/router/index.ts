import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import i18n from '@/i18n'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginView.vue'),
    meta: { titleKey: 'router.login', layout: 'full' }
  },
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/views/ProfileView.vue'),
    meta: { titleKey: 'router.profile' }
  },
  {
    path: '/',
    name: 'Home',
    redirect: '/chat'
  },
  {
    path: '/chat',
    name: 'Chat',
    component: () => import('@/views/ChatView.vue'),
    meta: { titleKey: 'router.chat' }
  },
  {
    path: '/agents',
    name: 'Agents',
    component: () => import('@/views/AgentList.vue'),
    meta: { titleKey: 'router.agents' }
  },
  {
    path: '/providers',
    name: 'Providers',
    component: () => import('@/views/ProviderList.vue'),
    meta: { titleKey: 'router.providers' }
  },
  {
    path: '/knowledge',
    name: 'Knowledge',
    component: () => import('@/views/KnowledgeView.vue'),
    meta: { titleKey: 'router.knowledge' }
  },
  {
    path: '/knowledge/:id/documents',
    name: 'Documents',
    component: () => import('@/views/DocumentView.vue'),
    meta: { titleKey: 'router.documents' }
  },
  {
    path: '/workflows',
    name: 'Workflows',
    component: () => import('@/views/WorkflowList.vue'),
    meta: { titleKey: 'router.workflows' }
  },
  {
    path: '/workflows/create',
    name: 'WorkflowCreate',
    component: () => import('@/views/WorkflowCreate.vue'),
    meta: { titleKey: 'router.workflowCreate' }
  },
  {
    path: '/workflows/new',
    name: 'WorkflowNew',
    component: () => import('@/views/WorkflowEdit.vue'),
    meta: { titleKey: 'router.workflowEdit' }
  },
  {
    path: '/workflows/:id/edit',
    name: 'WorkflowEdit',
    component: () => import('@/views/WorkflowEdit.vue'),
    meta: { titleKey: 'router.workflowEdit' }
  },
  {
    path: '/mcp-servers',
    name: 'McpServers',
    component: () => import('@/views/McpServerList.vue'),
    meta: { titleKey: 'router.mcpServers' }
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})

router.beforeEach(async (to) => {
  const token = localStorage.getItem('accessToken')

  if (to.name === 'Login') {
    if (token) {
      return { path: '/' }
    }
    return true
  }

  if (!token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  const authStore = useAuthStore()
  if (!authStore.hydrated) {
    await authStore.hydrate()
  }

  // hydrate 失败（token 过期且刷新失败）则跳转登录
  if (!authStore.accessToken) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }

  return true
})

router.afterEach((to) => {
  if (to.meta.titleKey) {
    document.title = i18n.global.t(to.meta.titleKey as string) + ' - Eify'
  } else {
    document.title = 'Eify'
  }
})

export default router
