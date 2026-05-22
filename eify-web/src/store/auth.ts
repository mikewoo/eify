import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import i18n from '@/i18n'
import { authApi, type AuthResponse } from '@/api/auth'
import { workspaceApi } from '@/api/workspace'
import { ElMessage } from 'element-plus'
import { isTokenExpired } from '@/utils/jwt'

const { t } = i18n.global

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string>(localStorage.getItem('accessToken') || '')
  const user = ref<AuthResponse['user'] | null>(null)
  const workspace = ref<AuthResponse['workspace'] | null>(null)
  const workspaces = ref<AuthResponse['workspace'][]>([])
  const hydrated = ref(false)
  const refreshKey = ref(0)

  const isLoggedIn = computed(() => !!accessToken.value)
  const displayName = computed(() => user.value?.displayName || user.value?.username || '')
  const workspaceName = computed(() => workspace.value?.name || '')

  function saveAuth(resp: AuthResponse) {
    accessToken.value = resp.accessToken
    user.value = resp.user
    workspace.value = resp.workspace
    localStorage.setItem('accessToken', resp.accessToken)
    hydrated.value = true
  }

  function clearAuth() {
    accessToken.value = ''
    user.value = null
    workspace.value = null
    workspaces.value = []
    hydrated.value = false
    localStorage.removeItem('accessToken')
  }

  async function hydrate() {
    if (hydrated.value || !accessToken.value) return
    try {
      if (isTokenExpired(accessToken.value)) {
        const refreshed = await tryRefreshToken()
        if (!refreshed) {
          clearAuth()
          return
        }
      }
      const resp = await authApi.me()
      user.value = resp.user
      workspace.value = resp.workspace
      hydrated.value = true
      accessToken.value = localStorage.getItem('accessToken') || ''
      fetchWorkspaces()
    } catch {
      clearAuth()
    }
  }

  async function fetchWorkspaces() {
    try {
      workspaces.value = await authApi.listWorkspaces()
    } catch {
      workspaces.value = []
    }
  }

  async function createWorkspace(name: string, description?: string) {
    const ws = await workspaceApi.create({ name, description })
    ElMessage.success(t('profile.workspaceCreated', { name: ws.name }))
    await switchWorkspace(ws.id)
  }

  async function joinWorkspace(code: string) {
    const ws = await workspaceApi.join(code)
    ElMessage.success(t('profile.workspaceJoined', { name: ws.name }))
    await switchWorkspace(ws.id)
  }

  async function switchWorkspace(targetWorkspaceId: number) {
    const resp = await authApi.switchWorkspace(targetWorkspaceId)
    saveAuth(resp)
    refreshKey.value++
    ElMessage.success(t('profile.workspaceSwitched', { name: resp.workspace.name }))
  }

  async function login(username: string, password: string) {
    const resp = await authApi.login({ username, password })
    saveAuth(resp)
    ElMessage.success(t('auth.welcomeBack', { name: resp.user.displayName || resp.user.username }))
    return resp
  }

  async function register(username: string, email: string, password: string) {
    const resp = await authApi.register({ username, email, password })
    saveAuth(resp)
    ElMessage.success(t('auth.registerSuccess'))
    return resp
  }

  async function tryRefreshToken(): Promise<boolean> {
    try {
      const resp = await authApi.refresh()
      saveAuth(resp)
      return true
    } catch {
      clearAuth()
      return false
    }
  }

  async function logout() {
    try {
      await authApi.logout()
    } catch {
      // ignore logout errors
    }
    clearAuth()
    ElMessage.success(t('auth.loggedOut'))
  }

  return {
    accessToken,
    user,
    workspace,
    workspaces,
    isLoggedIn,
    displayName,
    workspaceName,
    hydrated,
    refreshKey,
    login,
    register,
    logout,
    hydrate,
    fetchWorkspaces,
    createWorkspace,
    joinWorkspace,
    switchWorkspace,
    tryRefreshToken,
    clearAuth,
    saveAuth
  }
})
