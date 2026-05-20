<script setup lang="ts">
import i18n from '@/i18n'

interface Props {
  show: boolean
  title?: string
  message: string
  confirmText?: string
  cancelText?: string
  type?: 'danger' | 'warning' | 'info'
}

interface Emits {
  (e: 'confirm'): void
  (e: 'cancel'): void
}

const props = withDefaults(defineProps<Props>(), {
  title: () => i18n.global.t('common.confirmTitle'),
  confirmText: () => i18n.global.t('common.confirm'),
  cancelText: () => i18n.global.t('common.cancel'),
  type: 'danger'
})

const emit = defineEmits<Emits>()

function handleConfirm() {
  emit('confirm')
}

function handleCancel() {
  emit('cancel')
}
</script>

<template>
  <Transition name="modal">
    <div v-if="show" class="modal-overlay" @click.self="handleCancel" @keydown.esc="handleCancel">
      <div class="modal-container" @click.stop tabindex="-1">
        <!-- 图标 -->
        <div class="modal-icon" :class="`icon-${type}`">
          <svg v-if="type === 'danger'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>
            <path d="M10 11v6M14 11v6"/>
          </svg>
          <svg v-else-if="type === 'warning'" width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
            <line x1="12" y1="9" x2="12" y2="13"/>
            <line x1="12" y1="17" x2="12.01" y2="17"/>
          </svg>
          <svg v-else width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10"/>
            <line x1="12" y1="16" x2="12" y2="12"/>
            <line x1="12" y1="8" x2="12.01" y2="8"/>
          </svg>
        </div>

        <!-- 标题 -->
        <h3 class="modal-title">{{ title }}</h3>

        <!-- 消息内容 -->
        <p class="modal-message">{{ message }}</p>

        <!-- 按钮组 -->
        <div class="modal-actions">
          <button class="modal-btn modal-btn-cancel" @click="handleCancel">
            {{ cancelText }}
          </button>
          <button class="modal-btn modal-btn-confirm" :class="`btn-${type}`" @click="handleConfirm">
            {{ confirmText }}
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
/* ========== 模态框遮罩 ========== */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  backdrop-filter: blur(4px);
  padding: 20px;
}

/* ========== 模态框容器 ========== */
.modal-container {
  background: var(--eify-bg-base);
  border-radius: 16px;
  padding: 32px 28px 24px;
  min-width: 360px;
  max-width: 480px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

/* ========== 图标 ========== */
.modal-icon {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
}

.modal-icon svg {
  width: 32px;
  height: 32px;
}

.icon-danger {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.icon-warning {
  background: rgba(251, 191, 36, 0.1);
  color: #fbbf24;
}

.icon-info {
  background: rgba(99, 102, 241, 0.1);
  color: var(--eify-primary);
}

/* ========== 标题 ========== */
.modal-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--eify-text-primary);
  margin: 0 0 12px 0;
}

/* ========== 消息 ========== */
.modal-message {
  font-size: 14px;
  color: var(--eify-text-secondary);
  margin: 0 0 28px 0;
  line-height: 1.6;
}

/* ========== 按钮组 ========== */
.modal-actions {
  display: flex;
  gap: 12px;
  width: 100%;
  justify-content: center;
}

.modal-btn {
  flex: 1;
  max-width: 140px;
  padding: 12px 24px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
}

.modal-btn-cancel {
  background: var(--eify-bg-surface);
  color: var(--eify-text-primary);
  border: 1px solid var(--eify-border-default);
}

.modal-btn-cancel:hover {
  background: var(--eify-bg-subtle);
  border-color: var(--eify-border-strong);
}

.modal-btn-confirm {
  color: white;
}

.modal-btn-confirm.btn-danger {
  background: #ef4444;
}

.modal-btn-confirm.btn-danger:hover {
  background: #dc2626;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}

.modal-btn-confirm.btn-warning {
  background: #fbbf24;
  color: #78350f;
}

.modal-btn-confirm.btn-warning:hover {
  background: #f59e0b;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(251, 191, 36, 0.3);
}

.modal-btn-confirm.btn-info {
  background: var(--eify-primary);
}

.modal-btn-confirm.btn-info:hover {
  background: var(--eify-primary-hover);
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(99, 102, 241, 0.3);
}

/* ========== 动画效果 ========== */
.modal-enter-active,
.modal-leave-active {
  transition: all 0.3s ease;
}

.modal-enter-active .modal-container,
.modal-leave-active .modal-container {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.modal-enter-from {
  opacity: 0;
}

.modal-enter-from .modal-container {
  opacity: 0;
  transform: scale(0.9) translateY(-20px);
}

.modal-leave-to {
  opacity: 0;
}

.modal-leave-to .modal-container {
  opacity: 0;
  transform: scale(0.95) translateY(10px);
}

.modal-enter-to {
  opacity: 1;
}

.modal-enter-to .modal-container {
  opacity: 1;
  transform: scale(1) translateY(0);
}
</style>
