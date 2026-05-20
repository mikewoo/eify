<template>
  <div class="eify-advanced-search" :class="{ 'eify-advanced-search--focused': focused }">
    <!-- 搜索栏 -->
    <div class="eify-search-bar" ref="searchBarRef">
      <!-- 左侧搜索图标 -->
      <div class="eify-search-bar__icon">
        <el-icon :size="18"><Search /></el-icon>
      </div>

      <!-- 条件标签区域 -->
      <div class="eify-search-bar__content" @click="handleContentClick">
        <!-- 已选条件标签 -->
        <div
          v-for="(condition, index) in searchConditions"
          :key="index"
          class="eify-search-tag"
          :class="{ 'is-active': activeConditionIndex === index }"
          @click.stop="handleTagClick(index)"
        >
          <span class="tag-field">{{ condition.fieldLabel }}</span>
          <span class="tag-operator">：</span>
          <span class="tag-value">{{ condition.displayValue }}</span>
          <el-icon class="tag-delete" :size="14" @click.stop="removeCondition(index)">
            <Close />
          </el-icon>
        </div>

        <!-- 当前输入 -->
        <input
          ref="inputRef"
          v-model="inputValue"
          type="text"
          class="eify-search-input"
          :placeholder="searchConditions.length === 0 ? placeholder : t('common.addFilter')"
          @focus="handleFocus"
          @blur="handleBlur"
          @keydown="handleKeydown"
          @input="handleInput"
        />
      </div>

      <!-- 快捷键提示（内部右侧） -->
      <div v-if="showShortcut && !focused && searchConditions.length === 0" class="eify-search-bar__shortcut">
        <span>⌘K</span>
      </div>

      <!-- 搜索栏内右侧删除按钮 -->
      <div
        v-if="searchConditions.length > 0"
        class="eify-search-bar__clear"
        @click="handleReset"
        :title="t('common.clearFilter')"
      >
        <el-icon :size="16"><Close /></el-icon>
      </div>
    </div>

    <!-- 搜索栏外部刷新按钮 -->
    <div
      class="eify-search-refresh"
      :class="{ 'is-loading': searching }"
      @click="handleSearchClick"
      title="搜索"
    >
      <el-icon :size="18"><Refresh /></el-icon>
    </div>

    <!-- 搜索条件下拉面板 -->
    <div
      v-if="dropdownVisible"
      class="eify-search-dropdown"
      :style="dropdownStyle"
      @mousedown.stop
    >
      <!-- 搜索条件列表 -->
      <div v-if="dropdownMode === 'select'" class="dropdown-section">
        <div class="dropdown-title">{{ t('common.selectFilter') }}</div>
        <div
          v-for="field in availableFields"
          :key="field.key"
          class="dropdown-item"
          :class="{ 'is-disabled': !field.enabled }"
          @click.stop="selectField(field)"
        >
          <div class="item-icon">
            <el-icon :size="16"><component :is="getFieldIcon(field.key)" /></el-icon>
          </div>
          <div class="item-content">
            <div class="item-label">{{ field.label }}</div>
            <div class="item-desc">{{ field.description || '' }}</div>
          </div>
        </div>
      </div>

      <!-- 条件值输入/选择 -->
      <div v-if="dropdownMode === 'input'" class="dropdown-section">
        <div class="dropdown-title">
          {{ currentField?.label }}
          <span class="title-operator">{{ currentOperator?.label }}</span>
        </div>

        <!-- 文本输入 -->
        <div v-if="currentField?.inputType === 'text'" class="dropdown-input">
          <el-input
            ref="conditionInputRef"
            v-model="conditionValue"
            :placeholder="currentField?.placeholder || t('common.enterValue')"
            size="default"
            clearable
            @keyup.enter="confirmCondition"
            @keyup.esc="closeDropdown"
            @mousedown.stop
          >
            <template #prefix>
              <el-icon><component :is="getFieldIcon(currentField?.key)" /></el-icon>
            </template>
          </el-input>
        </div>

        <!-- 下拉选择 -->
        <div v-if="currentField?.inputType === 'select'" class="dropdown-select">
          <el-select
            ref="conditionSelectRef"
            v-model="conditionValue"
            :placeholder="currentField?.placeholder || t('common.select')"
            size="default"
            style="width: 100%"
            @focus="handleSelectFocus"
            @visible-change="handleSelectVisibleChange"
            @mousedown.stop
            popper-class="eify-select-dropdown"
          >
            <el-option
              v-for="option in currentField?.options"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </div>

        <!-- 操作按钮 -->
        <div class="dropdown-actions">
          <el-button size="small" @click.stop="closeDropdown">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" size="small" @click.stop="confirmCondition">{{ t('common.confirm') }}</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts" generic="T = any">
import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import i18n from '@/i18n'
import {
  Search,
  Refresh,
  Close,
  Document,
  Grid,
  Link,
  SuccessFilled
} from '@element-plus/icons-vue'

const { t } = useI18n()

/* ========== 类型定义 ========== */

export interface SearchFieldOption {
  label: string
  value: string | number
}

export interface SearchField {
  key: string
  label: string
  description?: string
  inputType: 'text' | 'select'
  placeholder?: string
  options?: SearchFieldOption[]
  operators?: SearchOperator[]
  enabled?: boolean
}

export interface SearchOperator {
  value: string
  label: string
}

export interface SearchCondition {
  field: string
  fieldLabel: string
  operator: string
  operatorLabel?: string
  value: string | number
  displayValue: string
}

export interface AdvancedSearchOptions {
  fields?: SearchField[]
  showShortcut?: boolean
}

/* ========== Props ========== */

interface Props {
  modelValue: SearchCondition[]
  placeholder?: string
  options?: AdvancedSearchOptions
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  placeholder: () => i18n.global.t('common.selectFilter'),
  options: () => ({})
})

/* ========== Emits ========== */

const emit = defineEmits<{
  'update:modelValue': [conditions: SearchCondition[]]
  'search': [conditions: SearchCondition[]]
}>()

/* ========== 响应式状态 ========== */

const searchBarRef = ref<HTMLElement>()
const inputRef = ref<HTMLInputElement>()
const conditionInputRef = ref()
const conditionSelectRef = ref()
const focused = ref(false)
const inputValue = ref('')
const searchConditions = ref<SearchCondition[]>([...props.modelValue])
const activeConditionIndex = ref<number | null>(null)
const dropdownVisible = ref(false)
const dropdownMode = ref<'select' | 'input'>('select')
const dropdownPosition = ref({ top: '0px', left: '0px' })
const currentField = ref<SearchField | null>(null)
const currentOperator = ref<SearchOperator | null>(null)
const conditionValue = ref('')
const searching = ref(false)

// el-select 下拉菜单状态
const selectDropdownVisible = ref(false)

// 下拉面板内的交互状态
const isInteractingWithDropdown = ref(false)

// 鼠标位置追踪（用于防止误关闭下拉面板）
const lastMousePosition = ref({ x: 0, y: 0 })

// 监听鼠标移动
if (typeof window !== 'undefined') {
  document.addEventListener('mousemove', (e) => {
    lastMousePosition.value = { x: e.clientX, y: e.clientY }
  })
}

// 默认搜索字段配置
const defaultFields: SearchField[] = [
  {
    key: 'name',
    label: '名称',
    description: '按提供商名称搜索',
    inputType: 'text',
    placeholder: '输入提供商名称'
  },
  {
    key: 'type',
    label: '类型',
    description: '按提供商类型筛选',
    inputType: 'select',
    options: [
      { label: 'OpenAI', value: 'openai' },
      { label: 'Claude', value: 'claude' },
      { label: 'Gemini', value: 'gemini' },
      { label: 'Ollama', value: 'ollama' }
    ]
  },
  {
    key: 'url',
    label: 'Base URL',
    description: '按 API 地址搜索',
    inputType: 'text',
    placeholder: '输入 Base URL'
  },
  {
    key: 'status',
    label: '状态',
    description: '按启用状态筛选',
    inputType: 'select',
    options: [
      { label: '启用', value: 'enabled' },
      { label: '禁用', value: 'disabled' }
    ]
  }
]

// 默认操作符
const defaultOperators: SearchOperator[] = [
  { value: 'contains', label: '包含' },
  { value: 'equals', label: '等于' }
]

/* ========== 计算属性 ========== */

const searchOptions = computed<AdvancedSearchOptions>(() => ({
  showShortcut: true,
  fields: defaultFields,
  ...props.options
}))

const searchFields = computed<SearchField[]>(() =>
  searchOptions.value.fields || defaultFields
)

// 可用的搜索条件（排除已使用的）
const availableFields = computed<SearchField[]>(() => {
  const usedKeys = searchConditions.value.map(c => c.field)
  return searchFields.value.filter(f => !usedKeys.includes(f.key))
})

const showShortcut = computed(() => searchOptions.value.showShortcut)

const placeholder = computed(() => props.placeholder)

const dropdownStyle = computed(() => ({
  top: dropdownPosition.value.top,
  left: dropdownPosition.value.left,
  pointerEvents: (dropdownVisible.value ? 'auto' : 'none') as any
}))

/* ========== 方法 ========== */

/**
 * 获取字段图标
 */
const getFieldIcon = (key?: string) => {
  const iconMap: Record<string, any> = {
    name: Document,
    type: Grid,
    url: Link,
    status: SuccessFilled
  }
  return iconMap[key || ''] || Search
}

/**
 * 处理聚焦
 */
const handleFocus = async () => {
  focused.value = true
  activeConditionIndex.value = null
  // 聚焦时设置交互状态
  isInteractingWithDropdown.value = true

  if (searchConditions.value.length === 0 && !dropdownVisible.value) {
    openDropdown('select')
  } else if (availableFields.value.length > 0) {
    openDropdown('select')
  }
}

/**
 * 处理失焦
 */
const handleBlur = () => {
  // 不在这里关闭下拉，让点击外部处理器来处理
  // 只重置聚焦状态
  if (!dropdownVisible.value) {
    focused.value = false
  }
}

/**
 * 处理内容区点击
 */
const handleContentClick = () => {
  if (availableFields.value.length > 0) {
    openDropdown('select')
  }
  inputRef.value?.focus()
}

/**
 * 处理标签点击
 */
const handleTagClick = (index: number) => {
  activeConditionIndex.value = index
}

/**
 * 处理 el-select 聚焦
 */
const handleSelectFocus = () => {
  // 保持下拉面板开启状态
}

/**
 * 处理 el-select 下拉菜单可见性变化
 */
const handleSelectVisibleChange = (visible: boolean) => {
  selectDropdownVisible.value = visible

  if (visible) {
    // el-select 打开时，保持交互状态
    isInteractingWithDropdown.value = true
  } else {
    // el-select 关闭后，重置交互状态
    setTimeout(() => {
      isInteractingWithDropdown.value = false
    }, 100)
  }
}

/**
 * 处理键盘事件
 */
const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Backspace' && !inputValue.value && activeConditionIndex.value === null) {
    // 删除最后一个条件
    if (searchConditions.value.length > 0) {
      removeCondition(searchConditions.value.length - 1)
    }
  } else if (e.key === 'ArrowLeft') {
    // 左移光标到上一个标签
    if (activeConditionIndex.value === null && searchConditions.value.length > 0) {
      activeConditionIndex.value = searchConditions.value.length - 1
    } else if (activeConditionIndex.value !== null && activeConditionIndex.value > 0) {
      activeConditionIndex.value--
    }
  } else if (e.key === 'ArrowRight') {
    // 右移光标到下一个标签
    if (activeConditionIndex.value !== null) {
      if (activeConditionIndex.value < searchConditions.value.length - 1) {
        activeConditionIndex.value++
      } else {
        activeConditionIndex.value = null
      }
    }
  } else if (e.key === 'Delete') {
    // 删除当前激活的标签
    if (activeConditionIndex.value !== null) {
      removeCondition(activeConditionIndex.value)
      activeConditionIndex.value = null
    }
  } else if (e.key === 'Escape') {
    closeDropdown()
  }
}

/**
 * 处理输入
 */
const handleInput = () => {
  // 全局搜索模式 - 输入时不触发搜索
}

/**
 * 打开下拉面板
 */
const openDropdown = async (mode: 'select' | 'input') => {
  dropdownMode.value = mode
  dropdownVisible.value = true
  // 打开下拉时设置交互状态
  isInteractingWithDropdown.value = true

  // 使用 CSS 中的 top: 100% 和 margin-top 来定位，不需要动态计算
  dropdownPosition.value = {
    top: '100%',
    left: '0px'
  }

  if (mode === 'input') {
    // 聚焦到条件输入框
    await nextTick()
    if (currentField.value?.inputType === 'text') {
      conditionInputRef.value?.focus()
    } else if (currentField.value?.inputType === 'select') {
      conditionSelectRef.value?.focus()
    }
  }
}

/**
 * 关闭下拉面板
 */
const closeDropdown = () => {
  dropdownVisible.value = false
  currentField.value = null
  currentOperator.value = null
  conditionValue.value = ''
  // 关闭时重置交互状态
  isInteractingWithDropdown.value = false
  selectDropdownVisible.value = false
}

/**
 * 选择搜索字段
 */
const selectField = (field: SearchField) => {
  currentField.value = field
  // 使用第一个操作符
  currentOperator.value = field.operators?.[0] || defaultOperators[0]
  openDropdown('input')
}

/**
 * 确认添加条件
 */
const confirmCondition = () => {
  if (!currentField.value || conditionValue.value == null) {
    return
  }

  // 获取显示值
  let displayValue = conditionValue.value
  if (currentField.value.inputType === 'select') {
    const option = currentField.value.options?.find(o => o.value === conditionValue.value)
    displayValue = option?.label || conditionValue.value
  }

  const newCondition: SearchCondition = {
    field: currentField.value.key,
    fieldLabel: currentField.value.label,
    operator: currentOperator.value?.value || 'contains',
    operatorLabel: currentOperator.value?.label,
    value: conditionValue.value,
    displayValue
  }

  // 先保存当前条件的副本
  const conditionsToEmit = [...searchConditions.value, newCondition]

  // 添加到数组
  searchConditions.value.push(newCondition)
  inputValue.value = ''
  activeConditionIndex.value = null

  // 立即执行搜索（使用保存的副本）
  emit('update:modelValue', conditionsToEmit)
  emit('search', conditionsToEmit)

  // 清空当前选择状态并关闭下拉面板
  conditionValue.value = ''
  currentField.value = null
  currentOperator.value = null
  dropdownVisible.value = false
  isInteractingWithDropdown.value = false
  selectDropdownVisible.value = false
}

/**
 * 删除条件
 */
const removeCondition = (index: number) => {
  searchConditions.value.splice(index, 1)
  if (activeConditionIndex.value === index) {
    activeConditionIndex.value = null
  } else if (activeConditionIndex.value !== null && activeConditionIndex.value > index) {
    activeConditionIndex.value--
  }
  // 立即触发搜索
  emit('update:modelValue', [...searchConditions.value])
  emit('search', [...searchConditions.value])
}

/**
 * 重置搜索
 */
const handleReset = () => {
  searchConditions.value = []
  inputValue.value = ''
  activeConditionIndex.value = null
  // 立即触发搜索
  emit('update:modelValue', [])
  emit('search', [])
  // 重置交互状态
  isInteractingWithDropdown.value = false
  selectDropdownVisible.value = false
  dropdownVisible.value = false
  currentField.value = null
  currentOperator.value = null
  conditionValue.value = ''
}

/**
 * 处理搜索按钮点击
 */
const handleSearchClick = async () => {
  searching.value = true

  // 模拟搜索延迟效果
  await new Promise(resolve => setTimeout(resolve, 300))

  // 触发搜索
  emit('update:modelValue', [...searchConditions.value])
  emit('search', [...searchConditions.value])

  setTimeout(() => {
    searching.value = false
  }, 200)
}

/**
 * 聚焦搜索框
 */
const focus = () => {
  inputRef.value?.focus()
}

/**
 * 失焦搜索框
 */
const blur = () => {
  inputRef.value?.blur()
}

/* ========== 监听 ========== */

watch(
  () => props.modelValue,
  (val) => {
    searchConditions.value = [...val]
  },
  { deep: true }
)

/* ========== 全局快捷键 ========== */

const handleGlobalKeydown = (e: KeyboardEvent) => {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    focus()
  }
}

/* ========== 生命周期 ========== */

// 处理点击外部关闭下拉面板
const handleClickOutside = (e: MouseEvent) => {
  if (!dropdownVisible.value) return

  const target = e.target as Node
  const searchBar = searchBarRef.value
  const dropdown = document.querySelector('.eify-search-dropdown')
  const selectDropdowns = document.querySelectorAll('.el-select-dropdown')

  // 检查点击是否在搜索栏内
  if (searchBar && searchBar.contains(target)) {
    isInteractingWithDropdown.value = true
    focused.value = true
    return
  }

  // 检查点击是否在下拉面板内
  if (dropdown && dropdown.contains(target)) {
    isInteractingWithDropdown.value = true
    return
  }

  // 检查点击是否在 el-select 下拉菜单内
  for (const selectDropdown of selectDropdowns) {
    if (selectDropdown.contains(target)) {
      isInteractingWithDropdown.value = true
      // 重新聚焦主输入框
      setTimeout(() => {
        inputRef.value?.focus()
        focused.value = true
      }, 10)
      return
    }
  }

  // 点击在外部，关闭下拉面板
  dropdownVisible.value = false
  isInteractingWithDropdown.value = false
  selectDropdownVisible.value = false
  focused.value = false
}

onMounted(() => {
  document.addEventListener('keydown', handleGlobalKeydown)
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleGlobalKeydown)
  document.removeEventListener('click', handleClickOutside)
})

/* ========== 暴露给父组件 ========== */

defineExpose({
  focus,
  blur,
  reset: handleReset
})
</script>

<style scoped>
/* ============================================
   容器
   ============================================ */

.eify-advanced-search {
  position: relative;
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

/* ============================================
   搜索栏
   ============================================ */

.eify-search-bar {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  background: #ffffff;
  border: 1px solid var(--eify-border-default);
  border-radius: var(--eify-radius-md);
  transition: all var(--eify-duration-base) var(--eify-ease-out);
  height: 40px;
  padding: 0 0 0 var(--eify-spacing-3);
  gap: 0;
  position: relative;
  overflow: hidden;
  box-sizing: border-box;
}

.eify-search-bar::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(99, 102, 241, 0.3) 50%,
    transparent 100%
  );
  opacity: 0;
  transition: var(--eify-transition-base);
}

.eify-search-bar:hover {
  border-color: var(--eify-primary);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.08);
}

.eify-advanced-search--focused .eify-search-bar {
  border-color: var(--eify-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1),
              0 2px 12px rgba(99, 102, 241, 0.15);
}

.eify-advanced-search--focused .eify-search-bar::before {
  opacity: 1;
}

/* 内容区域 */
.eify-search-bar__content {
  flex: 1;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--eify-spacing-2);
  height: 40px;
  cursor: text;
  padding: 0 var(--eify-spacing-2);
}

/* 左侧搜索图标 */
.eify-search-bar__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--eify-text-tertiary);
  flex-shrink: 0;
}

.eify-advanced-search--focused .eify-search-bar__icon {
  color: var(--eify-primary);
}

/* 搜索条件标签 */
.eify-search-tag {
  display: inline-flex;
  align-items: center;
  gap: var(--eify-spacing-1);
  padding: 3px var(--eify-spacing-2);
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.08) 0%,
    rgba(139, 92, 246, 0.06) 100%
  );
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 6px;
  font-size: 13px;
  transition: all var(--eify-duration-base) var(--eify-ease-out);
  cursor: pointer;
  position: relative;
  overflow: hidden;
}

.eify-search-tag::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--eify-gradient-primary);
  opacity: 0;
  transition: var(--eify-transition-base);
}

.eify-search-tag:hover {
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.12) 0%,
    rgba(139, 92, 246, 0.1) 100%
  );
  border-color: rgba(99, 102, 241, 0.4);
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15);
}

.eify-search-tag:hover::before {
  opacity: 0.05;
}

.eify-search-tag.is-active {
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.15) 0%,
    rgba(139, 92, 246, 0.12) 100%
  );
  border-color: var(--eify-primary);
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1),
              0 2px 8px rgba(99, 102, 241, 0.2);
  animation: tag-pulse 2s ease-in-out infinite;
}

@keyframes tag-pulse {
  0%, 100% {
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.1),
                0 2px 8px rgba(99, 102, 241, 0.2);
  }
  50% {
    box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.15),
                0 2px 12px rgba(99, 102, 241, 0.3);
  }
}

.tag-field {
  font-weight: 500;
  color: var(--eify-primary);
  position: relative;
  z-index: 1;
}

.tag-operator {
  color: var(--eify-text-tertiary);
  font-size: 12px;
  position: relative;
  z-index: 1;
}

.tag-value {
  color: var(--eify-text-secondary);
  position: relative;
  z-index: 1;
}

.tag-delete {
  color: var(--eify-text-tertiary);
  cursor: pointer;
  transition: all var(--eify-transition-base);
  border-radius: var(--eify-radius-full);
  padding: 2px;
  position: relative;
  z-index: 1;
}

.tag-delete:hover {
  color: var(--eify-error);
  background: rgba(239, 68, 68, 0.1);
  transform: rotate(90deg);
}

/* 输入框 */
.eify-search-input {
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--eify-text-primary);
  font-family: inherit;
  min-width: 150px;
  flex: 1;
}

.eify-search-input::placeholder {
  color: var(--eify-text-tertiary);
}

/* 重置按钮 */
.eify-search-bar__reset {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  color: var(--eify-text-tertiary);
  cursor: pointer;
  border-radius: var(--eify-radius-sm);
  transition: var(--eify-transition-base);
  flex-shrink: 0;
}

.eify-search-bar__reset:hover {
  color: var(--eify-primary);
  background: var(--eify-bg-surface);
}

/* ============================================
   搜索栏内右侧删除按钮
   ============================================ */

.eify-search-bar__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  color: var(--eify-text-tertiary);
  cursor: pointer;
  border-radius: 0 var(--eify-radius-md) var(--eify-radius-md) 0;
  transition: var(--eify-transition-base);
  flex-shrink: 0;
  margin-left: var(--eify-spacing-1);
}

.eify-search-bar__clear:hover {
  color: var(--eify-error);
  background: rgba(239, 68, 68, 0.1);
}

.eify-search-bar__clear:active {
  transform: scale(0.95);
}

/* ============================================
   搜索栏外部刷新按钮
   ============================================ */

.eify-search-refresh {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  background: #ffffff;
  border: 1px solid var(--eify-border-default);
  border-radius: var(--eify-radius-md);
  color: var(--eify-text-tertiary);
  cursor: pointer;
  transition: var(--eify-transition-base);
}

.eify-search-refresh:hover {
  color: var(--eify-primary);
  border-color: var(--eify-primary);
  background: var(--eify-primary-50);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.15);
}

.eify-search-refresh:active {
  transform: scale(0.95);
}

.eify-search-refresh.is-loading {
  opacity: 0.7;
  cursor: wait;
}

.eify-search-refresh.is-loading .el-icon {
  animation: refresh-spin 0.6s linear infinite;
}

@keyframes refresh-spin {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

/* ============================================
   下拉面板 - Eify 科技风格
   ============================================ */

.eify-search-dropdown {
  position: absolute;
  top: 100%;
  left: 0;
  margin-top: 8px;
  background: linear-gradient(145deg,
    rgba(255, 255, 255, 0.98) 0%,
    rgba(248, 250, 252, 0.95) 100%
  );
  border: 1px solid;
  border-image: linear-gradient(135deg,
    rgba(99, 102, 241, 0.3) 0%,
    rgba(139, 92, 246, 0.2) 50%,
    rgba(99, 102, 241, 0.3) 100%
  ) 1;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  z-index: 1000;
  min-width: 320px;
  max-width: 400px;
  overflow: visible;
  animation: dropdown-in 200ms cubic-bezier(0.34, 1.56, 0.64, 1);
  pointer-events: auto;
  /* 移除 backdrop-filter 以避免创建新的 stacking context */
}

.eify-search-dropdown::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 3px;
  background: var(--eify-gradient-primary);
  box-shadow: 0 2px 12px rgba(99, 102, 241, 0.4);
}

.eify-search-dropdown::after {
  content: '';
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at top, rgba(99, 102, 241, 0.05) 0%, transparent 50%),
    radial-gradient(ellipse at bottom right, rgba(139, 92, 246, 0.03) 0%, transparent 50%);
  pointer-events: none;
}

@keyframes dropdown-in {
  from {
    opacity: 0;
    transform: translateY(-12px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.dropdown-section {
  padding: var(--eify-spacing-4) var(--eify-spacing-5);
  position: relative;
  z-index: 1;
}

.dropdown-title {
  font-size: 13px;
  font-weight: 700;
  background: var(--eify-gradient-primary);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  margin-bottom: var(--eify-spacing-4);
  padding-bottom: var(--eify-spacing-3);
  border-bottom: 2px solid;
  border-image: linear-gradient(90deg,
    var(--eify-primary) 0%,
    transparent 100%
  ) 1;
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  position: relative;
  text-transform: none;
  letter-spacing: 0.3px;
}

.dropdown-title::before {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 60px;
  height: 2px;
  background: var(--eify-gradient-primary);
  border-radius: var(--eify-radius-full);
  box-shadow: 0 0 10px rgba(99, 102, 241, 0.5);
}

.title-operator {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(139, 92, 246, 0.12) 100%);
  color: var(--eify-primary);
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
  font-size: 11px;
  border: 1px solid rgba(99, 102, 241, 0.3);
  box-shadow: 0 1px 4px rgba(99, 102, 241, 0.15);
}

.dropdown-item {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
  padding: var(--eify-spacing-3);
  margin: 4px 0;
  border-radius: 10px;
  cursor: pointer;
  transition: all var(--eify-duration-base) cubic-bezier(0.34, 1.56, 0.64, 1);
  position: relative;
  background: linear-gradient(135deg,
    rgba(255, 255, 255, 0.6) 0%,
    rgba(255, 255, 255, 0.4) 100%
  );
  border: 1px solid rgba(99, 102, 241, 0.1);
  overflow: hidden;
}

.dropdown-item::before {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--eify-gradient-primary);
  opacity: 0;
  transition: opacity var(--eify-duration-base) ease;
}

.dropdown-item::after {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 4px;
  height: 0;
  background: var(--eify-gradient-primary);
  border-radius: 0 var(--eify-radius-full) var(--eify-radius-full) 0;
  transition: all var(--eify-duration-base) cubic-bezier(0.34, 1.56, 0.64, 1);
}

.dropdown-item:hover {
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.08) 0%,
    rgba(139, 92, 246, 0.06) 100%
  );
  border-color: rgba(99, 102, 241, 0.3);
  transform: translateX(6px);
  box-shadow:
    0 4px 16px rgba(99, 102, 241, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.dropdown-item:hover::before {
  opacity: 0.03;
}

.dropdown-item:hover::after {
  height: 24px;
}

.dropdown-item.is-disabled {
  opacity: 0.6;
  cursor: default;
  background: rgba(0, 0, 0, 0.02);
  border-color: transparent;
}

.dropdown-item.is-disabled:hover {
  background: rgba(0, 0, 0, 0.02);
  transform: none;
  border-color: transparent;
}

.dropdown-item.is-disabled:hover::before {
  opacity: 0;
}

.dropdown-item.is-disabled:hover::after {
  height: 0;
}

.dropdown-item.is-disabled .item-label {
  color: #0f172a;
  font-weight: 600;
}

.dropdown-item.is-disabled .item-desc {
  color: #64748b;
}

.item-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.12) 0%,
    rgba(139, 92, 246, 0.08) 100%
  );
  border: 1px solid rgba(99, 102, 241, 0.2);
  border-radius: 10px;
  color: var(--eify-primary);
  flex-shrink: 0;
  position: relative;
  z-index: 1;
  transition: all var(--eify-duration-base) cubic-bezier(0.34, 1.56, 0.64, 1);
}

.item-icon::before {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg,
    rgba(79, 70, 229, 0.3) 0%,
    rgba(67, 56, 202, 0.25) 100%
  );
  border-radius: 10px;
  opacity: 0;
  transition: opacity var(--eify-duration-base) ease;
}

.dropdown-item:hover .item-icon {
  background: linear-gradient(135deg,
    rgba(79, 70, 229, 0.25) 0%,
    rgba(67, 56, 202, 0.2) 100%
  );
  border-color: rgba(79, 70, 229, 0.5);
  color: var(--eify-primary-hover);
  transform: scale(1.1) rotate(5deg);
  box-shadow:
    0 0 20px rgba(79, 70, 229, 0.35),
    inset 0 0 20px rgba(79, 70, 229, 0.15);
}

.dropdown-item:hover .item-icon::before {
  opacity: 0.5;
}

.dropdown-item.is-disabled .item-icon {
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.12) 0%,
    rgba(139, 92, 246, 0.08) 100%
  );
  border-color: rgba(99, 102, 241, 0.2);
  color: var(--eify-primary);
  transform: none;
}

.dropdown-item.is-disabled .item-icon::before {
  opacity: 0;
}

.item-content {
  flex: 1;
  min-width: 0;
}

.item-label {
  font-size: 15px;
  font-weight: 600;
  color: #334155;
  position: relative;
  z-index: 1;
  transition: all var(--eify-duration-base) ease;
}

.dropdown-item:hover .item-label {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  font-weight: 700;
}

.item-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 4px;
  position: relative;
  z-index: 1;
  line-height: 1.5;
  font-weight: 400;
  transition: all var(--eify-duration-base) ease;
}

.dropdown-item:hover .item-desc {
  color: #334155;
}

.dropdown-input,
.dropdown-select {
  margin-bottom: var(--eify-spacing-3);
  position: relative;
  z-index: 1;
}

/* 优化下拉面板中的输入框样式 */
.dropdown-input :deep(.el-input__wrapper) {
  background: linear-gradient(135deg,
    rgba(255, 255, 255, 0.9) 0%,
    rgba(255, 255, 255, 0.8) 100%
  );
  border-color: rgba(99, 102, 241, 0.2);
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
  transition: all var(--eify-duration-base) ease;
}

.dropdown-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(99, 102, 241, 0.4);
  background: rgba(255, 255, 255, 0.95);
}

.dropdown-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--eify-primary);
  background: #ffffff;
  box-shadow:
    0 0 0 4px rgba(99, 102, 241, 0.1),
    0 2px 8px rgba(99, 102, 241, 0.15);
}

/* 优化下拉面板中的选择器样式 */
.dropdown-select :deep(.el-select .el-input__wrapper) {
  background: linear-gradient(135deg,
    rgba(255, 255, 255, 0.9) 0%,
    rgba(255, 255, 255, 0.8) 100%
  );
  border-color: rgba(99, 102, 241, 0.2);
}

.dropdown-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--eify-spacing-3);
  padding-top: var(--eify-spacing-4);
  position: relative;
  z-index: 1;
}

.dropdown-actions :deep(.el-button) {
  background: linear-gradient(135deg,
    rgba(255, 255, 255, 0.9) 0%,
    rgba(255, 255, 255, 0.8) 100%
  );
  border-color: rgba(99, 102, 241, 0.2);
  color: #0f172a;
  font-weight: 600;
  font-size: 13px;
  padding: 8px 18px;
  height: 36px;
  transition: all var(--eify-duration-base) cubic-bezier(0.34, 1.56, 0.64, 1);
  border-radius: 8px;
  box-shadow:
    0 1px 3px rgba(0, 0, 0, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.5);
}

.dropdown-actions :deep(.el-button:hover) {
  border-color: var(--eify-primary);
  color: var(--eify-primary);
  background: rgba(255, 255, 255, 0.95);
  transform: translateY(-2px);
  box-shadow:
    0 4px 12px rgba(99, 102, 241, 0.15),
    inset 0 1px 0 rgba(255, 255, 255, 0.8);
}

.dropdown-actions :deep(.el-button--primary) {
  background: var(--eify-gradient-primary);
  border-color: transparent;
  color: #ffffff;
  box-shadow:
    0 2px 8px rgba(99, 102, 241, 0.3),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

.dropdown-actions :deep(.el-button--primary:hover) {
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 1) 0%,
    rgba(139, 92, 246, 1) 100%
  );
  border-color: transparent;
  color: #ffffff;
  transform: translateY(-2px) scale(1.02);
  box-shadow:
    0 6px 20px rgba(99, 102, 241, 0.4),
    inset 0 1px 0 rgba(255, 255, 255, 0.3);
}

.dropdown-actions :deep(.el-button--primary:active) {
  transform: translateY(0) scale(0.98);
}

/* ============================================
   搜索栏内部快捷键提示
   ============================================ */

.eify-search-bar__shortcut {
  display: flex;
  align-items: center;
  padding: 0 var(--eify-spacing-3);
  flex-shrink: 0;
}

.eify-search-bar__shortcut span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--eify-text-tertiary);
  background: var(--eify-bg-surface);
  border: 1px solid var(--eify-border-subtle);
  border-radius: 4px;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
}

/* ============================================
   搜索栏内部快捷键提示
   ============================================ */

.eify-search-bar__shortcut {
  display: flex;
  align-items: center;
  padding: 0 var(--eify-spacing-3);
  flex-shrink: 0;
}

.eify-search-bar__shortcut span {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 500;
  color: var(--eify-text-tertiary);
  background: var(--eify-bg-surface);
  border: 1px solid var(--eify-border-subtle);
  border-radius: 4px;
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
}

/* ============================================
   响应式
   ============================================ */

/* el-select 下拉菜单样式优化 */
:deep(.eify-select-dropdown) {
  z-index: 2000 !important;
}

/* 确保 Element Plus 的 select 下拉菜单也有足够高的 z-index */
:deep(.el-select-dropdown) {
  z-index: 2000 !important;
}

/* 增加 el-popper 的 z-index */
:deep(.el-popper) {
  z-index: 2000 !important;
}

@media (max-width: 768px) {
  .eify-advanced-search {
    max-width: 100%;
  }

  .eify-search-dropdown {
    min-width: auto;
    max-width: none;
    left: 0 !important;
    right: 0;
  }
}
</style>

<style>
/* 全局样式：确保搜索下拉面板内的 el-select 下拉菜单显示在最上层 */
.eify-search-dropdown + .el-select-dropdown,
.eify-select-dropdown {
  z-index: 3000 !important;
}

/* 确保 el-popper 也有足够高的 z-index */
.eify-search-dropdown ~ .el-popper {
  z-index: 3000 !important;
}
</style>
