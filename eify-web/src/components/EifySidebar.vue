<template>
  <aside class="eify-sidebar" :class="{ collapsed: isCollapsed }">
    <!-- SVG 渐变定义 -->
    <svg width="0" height="0" style="position: absolute;">
      <defs>
        <linearGradient id="icon-gradient-active" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" style="stop-color: #6366f1" />
          <stop offset="100%" style="stop-color: #8b5cf6" />
        </linearGradient>
      </defs>
    </svg>

    <!-- 科技感网格背景装饰 -->
    <div class="eify-sidebar-grid-bg"></div>

    <!-- Logo 区域 -->
    <div class="eify-sidebar-logo">
      <div class="eify-sidebar-logo-title">EIFY</div>
      <div class="eify-sidebar-logo-subtitle">AI Agent Platform</div>
      <!-- 状态指示灯 -->
      <div class="eify-status-indicator"></div>
    </div>

    <!-- 折叠按钮（移到 Logo 下方） -->
    <div class="eify-sidebar-collapse-wrapper">
      <button class="eify-collapse-btn-tech" @click="toggleCollapse" :title="isCollapsed ? t('sidebar.expand') : t('sidebar.collapse')">
        <div class="eify-collapse-icon-wrapper">
          <svg class="eify-collapse-icon" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path v-if="!isCollapsed" d="M15 18L9 12L15 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            <path v-else d="M9 18L15 12L9 6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </div>
        <div class="eify-collapse-glow"></div>
      </button>
    </div>

    <!-- 菜单区域 -->
    <div class="eify-sidebar-menu">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="eify-menu-item"
        :class="{ active: isActive(item.path) }"
      >
        <div class="eify-menu-icon">
          <component :is="item.icon" />
        </div>
        <span class="eify-menu-text">{{ t(item.titleKey) }}</span>
      </router-link>
    </div>

    <!-- 底部版本信息 -->
    <div class="eify-sidebar-footer">
      <div class="eify-version-info" v-if="!isCollapsed">
        <div class="eify-version-text">v{{ appVersion }}</div>
        <div class="eify-version-badge">{{ versionBadge }}</div>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ChatDotRound, Cpu, MagicStick, Reading, Guide, Tools } from '@element-plus/icons-vue'

const route = useRoute()
const { t } = useI18n()

const appVersion = computed(() => __APP_VERSION__.replace(/^v/, ''))

const versionBadge = computed(() => {
  const v = __APP_VERSION__.toLowerCase()
  if (v.includes('-dev') || v.includes('-snapshot') || v === 'dev') return 'DEV'
  if (v.includes('-test')) return 'TEST'
  if (v.includes('-staging') || v.includes('-rc')) return 'STAGING'
  return 'PROD'
})

interface MenuItem {
  path: string
  titleKey: string
  icon: any
}

const menuItems: MenuItem[] = [
  { path: '/chat', titleKey: 'sidebar.chat', icon: ChatDotRound },
  { path: '/agents', titleKey: 'sidebar.agentMgmt', icon: Cpu },
  { path: '/providers', titleKey: 'sidebar.modelMgmt', icon: MagicStick },
  { path: '/knowledge', titleKey: 'sidebar.knowledge', icon: Reading },
  { path: '/workflows', titleKey: 'sidebar.workflow', icon: Guide },
  { path: '/mcp-servers', titleKey: 'sidebar.mcpTools', icon: Tools },
]

const isCollapsed = ref(false)

const isActive = (path: string) => {
  return route.path === path || route.path.startsWith(path + '/')
}

const toggleCollapse = () => {
  isCollapsed.value = !isCollapsed.value
}
</script>

<style scoped>
@import '../styles/sidebar.css';
</style>
