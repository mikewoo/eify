<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '@/store/auth'
import { useLocaleStore } from '@/store/locale'
import { Refresh, User, SwitchButton } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const localeStore = useLocaleStore()
const { t } = useI18n()

interface BreadcrumbItem {
  title: string
  path?: string
}

const breadcrumbs = computed<BreadcrumbItem[]>(() => {
  const matched = route.matched.filter(item => item.meta?.titleKey)
  return matched.map(item => ({
    title: t(item.meta?.titleKey as string),
    path: item.path === route.path ? undefined : item.path
  }))
})

const userInitial = computed(() => {
  const name = authStore.displayName || 'U'
  return name.charAt(0).toUpperCase()
})

const otherWorkspaces = computed(() =>
  authStore.workspaces.filter(w => w.id !== authStore.workspace?.id)
)

const roleLabel = (role: string) => {
  const map: Record<string, string> = {
    owner: t('header.role.owner'),
    admin: t('header.role.admin'),
    member: t('header.role.member'),
  }
  return map[role] || t('header.role.member')
}

async function handleSwitchWorkspace(id: number) {
  await authStore.switchWorkspace(id)
}

async function handleLogout() {
  await authStore.logout()
  router.push('/login')
}

const localeOpen = ref(false)
const localeTrigger = ref<HTMLElement>()

function toggleLocale() {
  localeOpen.value = !localeOpen.value
}

function selectLocale(locale: string) {
  localeStore.setLocale(locale)
  localeOpen.value = false
}

function onLocaleClickOutside(e: MouseEvent) {
  if (localeOpen.value && localeTrigger.value && !localeTrigger.value.contains(e.target as Node)) {
    localeOpen.value = false
  }
}

const currentLocaleLabel = computed(() => localeStore.current === 'zh-CN' ? '简体中文' : 'English')

const localeDropdownStyle = computed(() => {
  if (!localeTrigger.value) return {}
  const rect = localeTrigger.value.getBoundingClientRect()
  return {
    position: 'fixed' as const,
    top: `${rect.bottom + 6}px`,
    right: `${window.innerWidth - rect.right}px`
  }
})

onMounted(() => {
  authStore.fetchWorkspaces()
  document.addEventListener('click', onLocaleClickOutside, true)
})

onUnmounted(() => {
  document.removeEventListener('click', onLocaleClickOutside, true)
})
</script>

<template>
  <header class="eify-header">
    <div class="eify-header-left">
      <nav class="eify-breadcrumb">
        <span class="eify-breadcrumb-item">
          <router-link to="/" class="eify-breadcrumb-link">{{ t('header.home') }}</router-link>
        </span>
        <template v-for="(item, index) in breadcrumbs" :key="index">
          <span class="eify-breadcrumb-separator">/</span>
          <span class="eify-breadcrumb-item">
            <router-link
              v-if="item.path && index !== breadcrumbs.length - 1"
              :to="item.path"
              class="eify-breadcrumb-link"
            >
              {{ item.title }}
            </router-link>
            <span v-else>{{ item.title }}</span>
          </span>
        </template>
      </nav>
    </div>

    <div class="eify-header-right">
      <!-- 工作空间切换 -->
      <el-dropdown trigger="click" v-if="authStore.workspaces.length > 0">
        <div class="eify-workspace-switcher">
          <div class="eify-workspace-avatar">
            {{ authStore.workspace?.name?.charAt(0) || 'W' }}
          </div>
          <span class="eify-workspace-name">{{ authStore.workspace?.name }}</span>
          <svg class="eify-workspace-chevron" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="6 9 12 15 18 9"/>
          </svg>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              <span class="eify-workspace-menu-label">{{ t('header.currentWorkspace') }}</span>
            </el-dropdown-item>
            <el-dropdown-item disabled>
              <div class="eify-workspace-current-item">
                <span>{{ authStore.workspace?.name }}</span>
                <el-tag size="small" round>{{ roleLabel(authStore.workspace?.role || '') }}</el-tag>
              </div>
            </el-dropdown-item>
            <template v-if="otherWorkspaces.length > 0">
              <el-dropdown-item disabled divided>
                <span class="eify-workspace-menu-label">{{ t('header.otherWorkspaces') }}</span>
              </el-dropdown-item>
              <el-dropdown-item
                v-for="ws in otherWorkspaces"
                :key="ws.id"
                @click="handleSwitchWorkspace(ws.id)"
              >
                <div class="eify-workspace-switch-item">
                  <div class="eify-workspace-switch-icon">{{ ws.name.charAt(0) }}</div>
                  <span>{{ ws.name }}</span>
                  <el-tag size="small" round>{{ roleLabel(ws.role) }}</el-tag>
                </div>
              </el-dropdown-item>
            </template>
            <el-dropdown-item divided @click="router.push('/profile')">
              <el-icon><Refresh /></el-icon>
              {{ t('header.manageWorkspace') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <!-- 语言切换 -->
      <div ref="localeTrigger" class="locale-trigger" @click="toggleLocale">
        <svg class="locale-globe" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="10"/>
          <ellipse cx="12" cy="12" rx="4" ry="10"/>
          <path d="M2 12h20"/>
        </svg>
        <span class="locale-current-label">{{ currentLocaleLabel }}</span>
        <svg class="locale-chevron" :class="{ open: localeOpen }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="6 9 12 15 18 9"/>
        </svg>

        <Teleport to="body">
          <div v-if="localeOpen" class="locale-overlay" @click.stop="localeOpen = false" />
          <transition name="locale-drop">
            <div v-if="localeOpen" class="locale-dropdown" :style="localeDropdownStyle" @click.stop>
              <div class="locale-dropdown-header">{{ t('common.language') }}</div>
              <button
                :class="['locale-dropdown-item', { active: localeStore.current === 'zh-CN' }]"
                @click="selectLocale('zh-CN')"
              >
                <span class="locale-item-label">中文</span>
                <span class="locale-item-badge">简体中文</span>
                <svg v-if="localeStore.current === 'zh-CN'" class="locale-item-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </button>
              <button
                :class="['locale-dropdown-item', { active: localeStore.current === 'en-US' }]"
                @click="selectLocale('en-US')"
              >
                <span class="locale-item-label">English</span>
                <span class="locale-item-badge">English</span>
                <svg v-if="localeStore.current === 'en-US'" class="locale-item-check" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                  <polyline points="20 6 9 17 4 12"/>
                </svg>
              </button>
            </div>
          </transition>
        </Teleport>
      </div>

      <!-- 用户菜单 -->
      <el-dropdown trigger="click">
        <div class="eify-user-info">
          <div class="eify-user-avatar">{{ userInitial }}</div>
          <span class="eify-user-name">{{ authStore.displayName }}</span>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="router.push('/profile')">
              <el-icon><User /></el-icon>
              {{ t('header.personalCenter') }}
            </el-dropdown-item>
            <el-dropdown-item @click="handleLogout">
              <el-icon><SwitchButton /></el-icon>
              {{ t('header.logout') }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<style scoped>
@import '../styles/page.css';

/* ===== 工作空间切换 ===== */
.eify-workspace-switcher {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: var(--eify-radius-md);
  transition: background var(--eify-duration-fast) var(--eify-ease-default);
  margin-right: 8px;
  flex-shrink: 0;
  max-width: none;
}

.eify-workspace-switcher:hover {
  background: var(--eify-bg-surface);
}

.eify-workspace-avatar {
  width: 26px;
  height: 26px;
  border-radius: 6px;
  background: var(--eify-gradient-primary);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}

.eify-workspace-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--eify-text-primary);
  white-space: nowrap;
}

.eify-workspace-chevron {
  width: 12px;
  height: 12px;
  color: var(--eify-text-tertiary);
  flex-shrink: 0;
  transition: transform var(--eify-duration-fast) var(--eify-ease-default);
}

.eify-workspace-menu-label {
  font-size: 11px;
  color: var(--eify-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.eify-workspace-current-item,
.eify-workspace-switch-item {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 180px;
}

.eify-workspace-switch-icon {
  width: 24px;
  height: 24px;
  border-radius: 4px;
  background: var(--eify-primary-50);
  color: var(--eify-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

/* ===== 语言切换 ===== */

/* 触发器按钮 */
.locale-trigger {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 4px 10px;
  border-radius: var(--eify-radius-md);
  cursor: pointer;
  transition: background var(--eify-duration-fast) var(--eify-ease-default);
  user-select: none;
  margin-right: 8px;
  flex-shrink: 0;
}

.locale-trigger:hover {
  background: var(--eify-bg-surface);
}

/* 地球图标 */
.locale-globe {
  width: 15px;
  height: 15px;
  color: var(--eify-primary-400);
  flex-shrink: 0;
  transition: color var(--eify-duration-fast);
}

.locale-trigger:hover .locale-globe {
  color: var(--eify-primary);
}

/* 当前语言标签 */
.locale-current-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--eify-text-primary);
}

/* 小箭头 */
.locale-chevron {
  width: 12px;
  height: 12px;
  color: var(--eify-text-tertiary);
  transition: transform var(--eify-duration-fast) var(--eify-ease-default);
}

.locale-chevron.open {
  transform: rotate(180deg);
}

/* 遮罩层（透明，仅用于点击关闭） */
.locale-overlay {
  position: fixed;
  inset: 0;
  z-index: 1050;
}

/* 下拉菜单卡片 */
.locale-dropdown {
  z-index: 1051;
  min-width: 192px;
  background: var(--eify-bg-card-1);
  border: 1px solid var(--eify-border-default);
  border-radius: var(--eify-radius-lg);
  box-shadow:
    0 0 0 1px rgba(0, 0, 0, 0.03),
    0 4px 16px rgba(0, 0, 0, 0.08),
    0 8px 32px rgba(0, 0, 0, 0.04);
  padding: 4px;
  overflow: hidden;
  transform-origin: top right;
}

/* 下拉菜单头部 */
.locale-dropdown-header {
  padding: 6px 10px 4px;
  font-size: 11px;
  font-weight: 600;
  color: var(--eify-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 下拉菜单项 */
.locale-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 7px 10px;
  border: none;
  border-radius: var(--eify-radius-md);
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  color: var(--eify-text-primary);
  transition: background var(--eify-duration-fast) var(--eify-ease-default);
  text-align: left;
}

.locale-dropdown-item:hover {
  background: var(--eify-bg-surface);
}

.locale-dropdown-item.active {
  background: var(--eify-primary-50);
}

/* 菜单项标签 */
.locale-item-label {
  font-weight: 500;
  flex: 1;
}

/* 菜单项副文本 */
.locale-item-badge {
  font-size: 11px;
  color: var(--eify-text-tertiary);
}

.locale-dropdown-item.active .locale-item-badge {
  color: var(--eify-primary-400);
}

/* 选中勾号 */
.locale-item-check {
  width: 16px;
  height: 16px;
  color: var(--eify-primary);
  flex-shrink: 0;
  margin-left: 4px;
}

/* ===== 下拉菜单过渡动画 ===== */
.locale-drop-enter-active {
  transition: opacity 150ms ease-out, transform 150ms ease-out;
}

.locale-drop-leave-active {
  transition: opacity 100ms ease-in, transform 100ms ease-in;
}

.locale-drop-enter-from {
  opacity: 0;
  transform: scale(0.95) translateY(-4px);
}

.locale-drop-leave-to {
  opacity: 0;
  transform: scale(0.97) translateY(-2px);
}

/* ===== 用户菜单 ===== */
.eify-user-info {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: var(--eify-radius-md);
  transition: background var(--eify-duration-fast) var(--eify-ease-default);
  flex-shrink: 0;
  max-width: none;
}

.eify-user-name {
  white-space: nowrap;
}

.eify-user-info:hover {
  background: var(--eify-bg-surface);
}

/* 确保 el-dropdown 不裁剪内部内容 */
:deep(.el-dropdown) {
  display: inline-flex !important;
  max-width: none !important;
  overflow: visible !important;
}
</style>
