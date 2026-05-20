<template>
  <el-dialog
    v-model="visible"
    :title="currentTitle"
    :width="dialogProps.width"
    :close-on-click-modal="dialogProps.closeOnClickModal"
    :close-on-press-escape="dialogProps.closeOnPressEscape"
    :before-close="handleClose"
    :destroy-on-close="dialogProps.destroyOnClose"
    draggable
    class="eify-form-dialog"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-width="dialogProps.labelWidth"
      :label-position="dialogProps.labelPosition"
      :size="dialogProps.size"
      @submit.prevent="handleSubmit"
    >
      <slot name="form" :data="formData" :mode="mode">
        <!-- 默认插槽：表单项 -->
      </slot>
    </el-form>

    <template #footer>
      <div class="eify-form-dialog-footer">
        <slot name="footer" :mode="mode" :loading="submitLoading">
          <el-button class="eify-btn-secondary" @click="handleClose">{{ t('common.cancel') }}</el-button>
          <el-button
            class="eify-btn-primary"
            :loading="submitLoading"
            @click="handleSubmit"
          >
            {{ currentSubmitText }}
          </el-button>
        </slot>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'

const { t } = useI18n()

/* ========== 类型定义 ========== */

export type FormMode = 'add' | 'edit'

export interface FormDialogProps {
  /** 弹窗标题 */
  title?: string
  /** 新增模式标题 */
  addTitle?: string
  /** 编辑模式标题 */
  editTitle?: string
  /** 弹窗宽度 */
  width?: string | number
  /** 表单标签宽度 */
  labelWidth?: string | number
  /** 表单标签位置 */
  labelPosition?: 'left' | 'right' | 'top'
  /** 表单尺寸 */
  size?: 'large' | 'default' | 'small'
  /** 点击遮罩是否关闭 */
  closeOnClickModal?: boolean
  /** 按 ESC 是否关闭 */
  closeOnPressEscape?: boolean
  /** 关闭时是否销毁内容 */
  destroyOnClose?: boolean
  /** 提交按钮文字 */
  submitText?: string
}

/* ========== Props ========== */

interface Props {
  /** modelValue 控制显示隐藏 */
  modelValue: boolean
  /** 表单验证规则 */
  rules?: FormRules
  /** 默认表单数据（新增时使用） */
  defaultData?: Record<string, any>
  /** 配置项 */
  dialogProps?: FormDialogProps
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  rules: () => ({}),
  defaultData: () => ({}),
  dialogProps: () => ({})
})

/* ========== Emits ========== */

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  'submit': [data: Record<string, any>, mode: FormMode]
  'open': [mode: FormMode, data?: Record<string, any>]
  'close': []
}>()

/* ========== 响应式状态 ========== */

const visible = ref(false)
const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
const submitLoading = ref(false)
const mode = ref<FormMode>('add')
const editData = ref<Record<string, any> | null>(null)

/* ========== 计算属性 ========== */

// 合并配置
const dialogProps = computed<FormDialogProps>(() => ({
  title: '',
  addTitle: t('common.add'),
  editTitle: t('common.edit'),
  width: '600px',
  labelWidth: '100px',
  labelPosition: 'right',
  size: 'default',
  closeOnClickModal: false,
  closeOnPressEscape: true,
  destroyOnClose: true,
  submitText: t('common.save'),
  ...props.dialogProps
}))

// 当前标题
const currentTitle = computed(() => {
  if (dialogProps.value.title) return dialogProps.value.title
  return mode.value === 'add' ? dialogProps.value.addTitle : dialogProps.value.editTitle
})

// 当前提交按钮文字
const currentSubmitText = computed(() => {
  const baseText = dialogProps.value.submitText
  return mode.value === 'add' ? t('common.add') : baseText
})

// 表单验证规则
const formRules = computed(() => props.rules)

/* ========== 方法 ========== */

/**
 * 打开弹窗
 *
 * @param data 编辑数据，不传则为新增模式
 */
const open = async (data?: Record<string, any>) => {
  mode.value = data ? 'edit' : 'add'

  if (data) {
    // 编辑模式：深拷贝数据
    editData.value = data
    formData.value = { ...data }
  } else {
    // 新增模式：使用默认数据
    editData.value = null
    formData.value = { ...props.defaultData }
  }

  visible.value = true
  emit('open', mode.value, data)

  // 等待 DOM 更新后清除验证
  await nextTick()
  formRef.value?.clearValidate()
}

/**
 * 关闭弹窗
 */
const close = () => {
  visible.value = false
  emit('close')
  emit('update:modelValue', false)
}

/**
 * 处理关闭前的确认
 */
const handleClose = () => {
  if (submitLoading.value) return
  close()
}

/**
 * 重置表单
 */
const resetForm = () => {
  formRef.value?.resetFields()
  formData.value = { ...props.defaultData }
}

/**
 * 设置表单数据
 */
const setFormData = (data: Partial<Record<string, any>>) => {
  formData.value = { ...formData.value, ...data }
}

/**
 * 验证表单
 */
const validate = async () => {
  return await formRef.value?.validate()
}

/**
 * 清除验证
 */
const clearValidate = () => {
  formRef.value?.clearValidate()
}

/**
 * 提交表单
 */
const handleSubmit = async () => {
  if (!formRef.value || submitLoading.value) return

  try {
    await formRef.value.validate()
    submitLoading.value = true
    emit('submit', formData.value, mode.value)
  } catch (error) {
    submitLoading.value = false
  }
}

/**
 * 提交成功后调用
 */
const submitSuccess = () => {
  submitLoading.value = false
  close()
}

/**
 * 提交失败后调用
 */
const submitFail = () => {
  submitLoading.value = false
}

/* ========== 监听 ========== */

// 监听 modelValue 变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
  },
  { immediate: true }
)

// 监听 visible 变化
watch(visible, (val) => {
  if (!val) {
    emit('update:modelValue', false)
  }
})

/* ========== 暴露给父组件 ========== */

defineExpose({
  open,
  close,
  resetForm,
  setFormData,
  validate,
  clearValidate,
  submitSuccess,
  submitFail,
  formRef,
  formData
})
</script>

<style scoped>
.eify-form-dialog {
  --dialog-padding: var(--eify-card-padding);
}

:deep(.el-dialog) {
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-shadow-lg);
}

:deep(.el-dialog__header) {
  padding: var(--eify-spacing-5) var(--eify-spacing-6);
  border-bottom: 1px solid var(--eify-border-subtle);
}

:deep(.el-dialog__title) {
  font-size: 16px;
  font-weight: 600;
  color: var(--eify-text-primary);
}

:deep(.el-dialog__body) {
  padding: var(--dialog-padding);
  max-height: 60vh;
  overflow-y: auto;
}

:deep(.el-dialog__footer) {
  padding: var(--eify-spacing-4) var(--eify-spacing-6);
  border-top: 1px solid var(--eify-border-subtle);
}

/* 表单样式 */
:deep(.el-form-item__label) {
  color: var(--eify-text-secondary);
  font-weight: 500;
}

:deep(.el-input__inner),
:deep(.el-textarea__inner) {
  border-color: var(--eify-border-default);
  transition: var(--eify-transition-base);
}

:deep(.el-input__inner:focus),
:deep(.el-textarea__inner:focus) {
  border-color: var(--eify-primary);
}

/* 底部按钮 */
.eify-form-dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--eify-spacing-3);
}

/* 设计系统按钮样式 - 统一风格 */
.eify-form-dialog-footer .el-button {
  height: 38px;
  padding: 0 20px;
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--eify-radius-md);
  transition: all var(--eify-duration-base) var(--eify-ease-default);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--eify-spacing-2);
  min-width: 80px;
  border: 1px solid transparent;
}

/* 次要按钮 */
.eify-form-dialog-footer .eify-btn-secondary {
  background-color: #ffffff;
  color: var(--eify-text-secondary);
  border-color: var(--eify-border-default);
}

.eify-form-dialog-footer .eify-btn-secondary:hover {
  color: var(--eify-text-primary);
  border-color: var(--eify-primary);
  background-color: var(--eify-primary-50);
}

.eify-form-dialog-footer .eify-btn-secondary:active {
  transform: scale(0.98);
}

/* 主要按钮 - 蓝紫渐变 */
.eify-form-dialog-footer .eify-btn-primary {
  background: var(--eify-gradient-primary);
  color: #ffffff;
  border: none;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.25);
}

.eify-form-dialog-footer .eify-btn-primary:hover:not(:disabled) {
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.4);
  transform: translateY(-1px);
}

.eify-form-dialog-footer .eify-btn-primary:active:not(:disabled) {
  transform: translateY(0) scale(0.98);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

.eify-form-dialog-footer .eify-btn-primary:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

/* 滚动条样式 */
:deep(.el-dialog__body)::-webkit-scrollbar {
  width: 6px;
}

:deep(.el-dialog__body)::-webkit-scrollbar-track {
  background: transparent;
}

:deep(.el-dialog__body)::-webkit-scrollbar-thumb {
  background: var(--eify-gray-300);
  border-radius: var(--eify-radius-full);
}

:deep(.el-dialog__body)::-webkit-scrollbar-thumb:hover {
  background: var(--eify-gray-400);
}
</style>
