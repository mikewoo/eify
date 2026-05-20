<script setup lang="ts">
import { computed } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { useAuthStore } from '@/store/auth'
import { useLocaleStore } from '@/store/locale'
import EifySidebar from './components/EifySidebar.vue'
import EifyHeader from './components/EifyHeader.vue'

const route = useRoute()
const authStore = useAuthStore()
const localeStore = useLocaleStore()
const isFullLayout = computed(() => route.meta?.layout === 'full')
</script>

<template>
  <el-config-provider :locale="localeStore.elementLocale">
    <template v-if="isFullLayout">
      <RouterView :key="authStore.refreshKey" />
    </template>

    <template v-else>
      <el-container class="layout-container">
        <EifySidebar />
        <el-container class="main-container">
          <EifyHeader />
          <el-main class="main-content">
            <RouterView :key="authStore.refreshKey" />
          </el-main>
        </el-container>
      </el-container>
    </template>
  </el-config-provider>
</template>

<style scoped>
.layout-container {
  height: 100vh;
  padding: 0;
  margin: 0;
}

.main-container {
  flex-direction: column;
  min-width: 0;
}

.main-content {
  flex: 1;
  overflow: hidden;
  min-height: 0;
  padding: 0;
  background-color: var(--eify-bg-secondary);
}

.main-content::-webkit-scrollbar {
  width: 8px;
}

.main-content::-webkit-scrollbar-track {
  background: transparent;
}

.main-content::-webkit-scrollbar-thumb {
  background: var(--eify-gray-300);
  border-radius: var(--eify-radius-full);
}

.main-content::-webkit-scrollbar-thumb:hover {
  background: var(--eify-gray-400);
}
</style>
