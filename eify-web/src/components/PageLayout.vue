<template>
  <div class="eify-page">
    <!-- 页面标题区域 -->
    <div v-if="!hideHeader" class="eify-page-content">
      <div class="eify-page-header">
        <div class="eify-page-header-left">
          <h1 class="eify-page-title">{{ title }}</h1>
          <p v-if="description" class="eify-page-description">{{ description }}</p>
        </div>
        <div v-if="$slots.actions" class="eify-page-header-actions">
          <slot name="actions"></slot>
        </div>
      </div>
    </div>

    <!-- 页面内容 -->
    <div class="eify-page-content">
      <slot></slot>
    </div>
  </div>
</template>

<script setup lang="ts">
interface Props {
  title?: string
  description?: string
  hideHeader?: boolean
}

withDefaults(defineProps<Props>(), {
  hideHeader: false
})
</script>

<style scoped>
@import '../styles/page.css';
</style>
