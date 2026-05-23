<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { workspaceApi } from '@/api/workspace'
import { ElMessage } from 'element-plus'
import { User, Message, Lock, Plus } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const { t } = useI18n()
const loading = ref(false)
const switchingId = ref<number | null>(null)

// {{ t('profile.createWorkspace') }}/{{ t('profile.joinWorkspace') }}对话框
const createDialogVisible = ref(false)
const joinDialogVisible = ref(false)
const createLoading = ref(false)
const joinLoading = ref(false)
const newWorkspaceName = ref('')
const newWorkspaceDesc = ref('')
const inviteCode = ref('')
const inviteLoading = ref(false)

const userInitial = computed(() => {
  const name = authStore.displayName || authStore.user?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const roleLabel = (role: string) => {
  const map: Record<string, string> = {
    owner: t('header.role.owner'),
    admin: t('header.role.admin'),
    member: t('header.role.member')
  }
  return map[role] || t('header.role.member')
}

const roleType = (role: string) => {
  if (role === 'owner') return 'primary'
  return 'info'
}

const otherWorkspaces = computed(() =>
  authStore.workspaces.filter(w => w.id !== authStore.workspace?.id)
)

onMounted(async () => {
  loading.value = true
  await authStore.hydrate()
  await authStore.fetchWorkspaces()
  loading.value = false
})

async function handleSwitchWorkspace(id: number) {
  switchingId.value = id
  try {
    await authStore.switchWorkspace(id)
  } finally {
    switchingId.value = null
  }
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

async function handleCreateWorkspace() {
  if (!newWorkspaceName.value.trim()) return
  createLoading.value = true
  try {
    await authStore.createWorkspace(newWorkspaceName.value.trim(), newWorkspaceDesc.value.trim() || undefined)
  } catch {
    // 错误已在 store 中处理
  } finally {
    createLoading.value = false
    createDialogVisible.value = false
  }
}

async function handleJoinWorkspace() {
  if (!inviteCode.value.trim()) return
  joinLoading.value = true
  try {
    await authStore.joinWorkspace(inviteCode.value.trim().toUpperCase())
  } catch {
    // 错误已在 store 中处理
  } finally {
    joinLoading.value = false
    joinDialogVisible.value = false
  }
}

function openCreateDialog() {
  newWorkspaceName.value = ''
  newWorkspaceDesc.value = ''
  createDialogVisible.value = true
}

function openJoinDialog() {
  inviteCode.value = ''
  joinDialogVisible.value = true
}

async function handleGenerateInvite() {
  if (!authStore.workspace) return
  inviteLoading.value = true
  try {
    const code = await workspaceApi.generateInviteCode(authStore.workspace.id)
    await navigator.clipboard.writeText(code)
    ElMessage.success(t('profile.inviteCodeCopied', { code }))
  } catch {
    ElMessage.error(t('profile.inviteCodeFailed'))
  } finally {
    inviteLoading.value = false
  }
}
</script>

<template>
  <div class="profile-page">
    <div class="eify-page-header">
      <div class="eify-page-header-left">
        <h1 class="eify-page-title">{{ t('profile.title') }}</h1>
        <p class="eify-page-description">{{ t('profile.description') }}</p>
      </div>
    </div>

    <div class="profile-grid" v-loading="loading">
      <!-- 用户信息卡片 -->
      <div class="eify-card profile-user-card">
        <div class="profile-user-banner">
          <div class="profile-user-avatar">{{ userInitial }}</div>
          <h3 class="profile-user-name text-2xl">{{ authStore.displayName }}</h3>
          <p class="profile-user-username text-base">@{{ authStore.user?.username }}</p>
        </div>
        <div class="profile-user-details">
          <div class="profile-detail-item text-sm">
            <el-icon :size="16"><User /></el-icon>
            <span class="profile-detail-label">{{ t('profile.userId') }}</span>
            <span class="profile-detail-value">{{ authStore.user?.id }}</span>
          </div>
          <div class="profile-detail-item text-sm">
            <el-icon :size="16"><Message /></el-icon>
            <span class="profile-detail-label">{{ t('profile.email') }}</span>
            <span class="profile-detail-value">{{ authStore.user?.email || '-' }}</span>
          </div>
          <div class="profile-detail-item text-sm">
            <el-icon :size="16"><Lock /></el-icon>
            <span class="profile-detail-label">{{ t('profile.status') }}</span>
            <el-tag size="small" type="success" round>{{ t('profile.statusNormal') }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 工作空间列表卡片 -->
      <div class="eify-card profile-workspaces-card">
        <div class="eify-card-header">
          <span class="eify-card-title">{{ t('profile.workspaces') }}</span>
          <span class="profile-count-badge text-xs">{{ t('profile.workspaceCount', { count: authStore.workspaces.length }) }}</span>
          <div class="eify-card-header-actions">
            <el-button size="small" :icon="Plus" @click="openCreateDialog">{{ t('profile.createWorkspace') }}</el-button>
            <el-button size="small" @click="openJoinDialog">{{ t('profile.joinWorkspace') }}</el-button>
          </div>
        </div>
        <div class="eify-card-body">
          <div class="profile-workspace-list">
            <!-- 当前工作空间 -->
            <div class="profile-workspace-item is-current" v-if="authStore.workspace">
              <div class="profile-ws-left">
                <div class="profile-ws-icon current-icon">
                  {{ authStore.workspace.name?.charAt(0) || 'W' }}
                </div>
                <div class="profile-ws-info">
                  <span class="profile-ws-name text-base">{{ authStore.workspace.name }}</span>
                  <span class="profile-ws-id text-xs">ID: {{ authStore.workspace.id }}</span>
                </div>
              </div>
              <div class="profile-ws-right">
                <el-tag :type="roleType(authStore.workspace.role)" effect="dark" round size="small">
                  {{ roleLabel(authStore.workspace.role) }}
                </el-tag>
                <span class="profile-ws-badge text-xs">{{ t('profile.current') }}</span>
                <el-button
                  v-if="authStore.workspace.role === 'owner' || authStore.workspace.role === 'admin'"
                  size="small"
                  text
                  type="primary"
                  :loading="inviteLoading"
                  @click="handleGenerateInvite"
                >
                  {{ t('profile.invite') }}
                </el-button>
              </div>
            </div>
            <!-- 其他工作空间 -->
            <div
              class="profile-workspace-item"
              v-for="ws in otherWorkspaces"
              :key="ws.id"
            >
              <div class="profile-ws-left">
                <div class="profile-ws-icon text-base">
                  {{ ws.name.charAt(0) }}
                </div>
                <div class="profile-ws-info">
                  <span class="profile-ws-name text-base">{{ ws.name }}</span>
                  <span class="profile-ws-id text-xs">ID: {{ ws.id }}</span>
                </div>
              </div>
              <div class="profile-ws-right">
                <el-tag :type="roleType(ws.role)" effect="dark" round size="small">
                  {{ roleLabel(ws.role) }}
                </el-tag>
                <button
                  class="profile-ws-switch-btn text-xs"
                  :disabled="switchingId === ws.id"
                  @click="handleSwitchWorkspace(ws.id)"
                >
                  {{ switchingId === ws.id ? t('common.loading') : t('profile.switchWorkspace') }}
                </button>
              </div>
            </div>
            <!-- 空状态 -->
            <div v-if="authStore.workspaces.length === 0" class="profile-workspace-empty text-sm">
              {{ t('profile.noWorkspace') }}
            </div>
          </div>
        </div>
      </div>

      <!-- 操作卡片 -->
      <div class="eify-card profile-actions-card">
        <div class="eify-card-header">
          <span class="eify-card-title">{{ t('profile.accountOps') }}</span>
        </div>
        <div class="eify-card-body">
          <p class="profile-hint text-sm">{{ t('profile.logoutHint') }}</p>
        </div>
        <div class="eify-card-footer">
          <button class="eify-btn eify-btn-danger" @click="handleLogout">
            {{ t('profile.logoutBtn') }}
          </button>
        </div>
      </div>
    </div>

    <!-- 创建工作空间对话框 -->
    <el-dialog v-model="createDialogVisible" :title="t('profile.createWorkspaceTitle')" width="420px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="handleCreateWorkspace">
        <el-form-item :label="t('profile.workspaceName')" required>
          <el-input v-model="newWorkspaceName" maxlength="100" :placeholder="t('profile.workspaceNamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('profile.workspaceDesc')">
          <el-input v-model="newWorkspaceDesc" type="textarea" :rows="3" maxlength="500" :placeholder="t('profile.workspaceDescPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="createLoading" @click="handleCreateWorkspace">{{ t('profile.createWorkspace') }}</el-button>
      </template>
    </el-dialog>

    <!-- Join workspace dialog -->
    <el-dialog v-model="joinDialogVisible" :title="t('profile.joinWorkspaceTitle')" width="420px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="handleJoinWorkspace">
        <el-form-item :label="t('profile.inviteCode')" required>
          <el-input v-model="inviteCode" maxlength="16" :placeholder="t('profile.inviteCodePlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="joinLoading" @click="handleJoinWorkspace">{{ t('profile.joinWorkspace') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  height: 100%;
  overflow-y: auto;
  padding: var(--eify-page-padding);
}

.profile-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  grid-template-rows: auto auto;
  gap: var(--eify-spacing-6);
  max-width: 800px;
}

.profile-user-card {
  grid-column: 1 / -1;
}

.profile-user-banner {
  text-align: center;
  padding: var(--eify-spacing-8) var(--eify-card-padding) var(--eify-spacing-6);
}

.profile-user-avatar {
  width: 80px;
  height: 80px;
  border-radius: var(--eify-radius-full);
  background: var(--eify-gradient-primary);
  color: var(--eify-text-inverse);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  font-weight: 700;
  margin: 0 auto var(--eify-spacing-4);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.3);
}

.profile-user-name {
  margin: 0;
  font-weight: 600;
  color: var(--eify-text-primary);
}

.profile-user-username {
  margin: var(--eify-spacing-1) 0 0;
  color: var(--eify-text-secondary);
}

.profile-user-details {
  display: flex;
  justify-content: center;
  gap: var(--eify-spacing-8);
  padding: var(--eify-spacing-6) var(--eify-card-padding);
  border-top: 1px solid var(--eify-border-subtle);
}

.profile-detail-item {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  color: var(--eify-text-secondary);
}

.profile-detail-item .el-icon {
  color: var(--eify-primary);
}

.profile-detail-label {
  color: var(--eify-text-tertiary);
}

.profile-detail-value {
  color: var(--eify-text-primary);
  font-weight: 500;
}

.profile-workspaces-card {
  grid-column: 1 / -1;
}

.profile-count-badge {
  color: var(--eify-text-tertiary);
  background: var(--eify-bg-surface);
  padding: 2px 8px;
  border-radius: 10px;
}

.eify-card-header-actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.profile-workspace-list {
  display: flex;
  flex-direction: column;
}

.profile-workspace-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border: 1px solid var(--eify-border-subtle);
  border-radius: var(--eify-radius-lg);
  transition: border-color 0.2s, background 0.2s;
}

.profile-workspace-item + .profile-workspace-item {
  margin-top: 8px;
}

.profile-workspace-item:hover {
  background: var(--eify-bg-hover, rgba(0, 0, 0, 0.01));
}

.profile-workspace-item.is-current {
  border-color: var(--eify-primary);
  background: var(--eify-primary-50, rgba(99, 102, 241, 0.04));
}

.profile-ws-left {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-4);
}

.profile-ws-icon {
  width: 40px;
  height: 40px;
  border-radius: var(--eify-radius-lg);
  background: var(--eify-bg-surface);
  color: var(--eify-text-secondary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}

.profile-ws-icon.current-icon {
  background: var(--eify-gradient-primary);
  color: var(--eify-text-inverse);
}

.profile-ws-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile-ws-name {
  font-weight: 600;
  color: var(--eify-text-primary);
}

.profile-ws-id {
  color: var(--eify-text-tertiary);
}

.profile-ws-right {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

.profile-ws-badge {
  color: var(--eify-primary);
  background: var(--eify-primary-50, rgba(99, 102, 241, 0.1));
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}

.profile-ws-switch-btn {
  padding: 4px 12px;
  border-radius: var(--eify-radius-base);
  border: 1px solid var(--eify-border);
  background: transparent;
  color: var(--eify-text-secondary);
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}

.profile-ws-switch-btn:hover:not(:disabled) {
  border-color: var(--eify-primary);
  color: var(--eify-primary);
  background: var(--eify-primary-50, rgba(99, 102, 241, 0.06));
}

.profile-ws-switch-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.profile-workspace-empty {
  text-align: center;
  padding: 32px;
  color: var(--eify-text-tertiary);
}

.profile-actions-card {
  grid-column: 1 / -1;
}

.profile-hint {
  margin: 0;
  color: var(--eify-text-secondary);
  line-height: 1.6;
}

@media (max-width: 768px) {
  .profile-grid {
    grid-template-columns: 1fr;
    max-width: 100%;
  }

  .profile-user-details {
    flex-direction: column;
    gap: var(--eify-spacing-4);
    align-items: center;
  }
}
</style>
