<template>
  <div class="eify-table-container">
    <!-- 表格 -->
    <el-table
      v-loading="loading"
      :data="tableData"
      :height="height"
      :max-height="maxHeight"
      :stripe="stripe"
      :border="border"
      :show-header="showHeader"
      highlight-current-row
      :resizable="true"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <!-- 选择列 -->
      <el-table-column
        v-if="showSelection"
        type="selection"
        width="55"
        :selectable="selectable"
        fixed="left"
      />

      <!-- 序号列 -->
      <el-table-column
        v-if="showIndex"
        type="index"
        :label="t('common.number')"
        width="60"
        :index="indexMethod"
        fixed="left"
      />

      <!-- 动态列 -->
      <template v-for="column in columns" :key="column.prop">
        <el-table-column
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth"
          :fixed="column.fixed"
          :sortable="column.sortable ? 'custom' : false"
          :align="column.align || 'left'"
          :show-overflow-tooltip="column.showOverflowTooltip !== false"
        >
          <template #header="{ column: col }">
            <el-tooltip :content="col.label" placement="top" :show-after="300">
              <span class="eify-table-header-label">{{ col.label }}</span>
            </el-tooltip>
          </template>
          <template #default="{ row, column: col, $index }">
            <slot
              v-if="column.slot"
              :name="column.slot"
              :row="row"
              :column="col"
              :index="$index"
            />
            <template v-else-if="column.render">
              <component
                :is="() => column.render!(row, col, $index)"
              />
            </template>
            <span v-else>{{ row[column.prop] }}</span>
          </template>
        </el-table-column>
      </template>

      <!-- 操作列 -->
      <el-table-column
        v-if="$slots.actions"
        :label="t('common.actions')"
        :width="actionWidth"
        :fixed="actionFixed"
        align="center"
      >
        <template #default="{ row, column, $index }">
          <slot name="actions" :row="row" :column="column" :index="$index" />
        </template>
      </el-table-column>

      <!-- 空状态 -->
      <template #empty>
        <el-empty
          :description="emptyText"
          :image-size="emptyImageSize"
        >
          <template #image>
            <slot name="empty-image" />
          </template>
          <template #description>
            <slot name="empty-description">
              <span class="eify-table-empty-text text-base">{{ emptyText }}</span>
            </slot>
          </template>
        </el-empty>
      </template>
    </el-table>

    <!-- 分页 -->
    <div v-if="showPagination && total > 0" class="eify-table-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="pageSizes"
        :total="total"
        :layout="paginationLayout"
        :background="true"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts" generic="T = any">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import i18n from '@/i18n'

const { t } = useI18n()

/* ========== 类型定义 ========== */

export interface TableColumn {
  /** 列属性名 */
  prop: string
  /** 列标题 */
  label: string
  /** 列宽度 */
  width?: number | string
  /** 最小列宽 */
  minWidth?: number | string
  /** 固定列 */
  fixed?: 'left' | 'right' | true
  /** 是否可排序 */
  sortable?: boolean
  /** 对齐方式 */
  align?: 'left' | 'center' | 'right'
  /** 是否显示溢出提示 */
  showOverflowTooltip?: boolean
  /** 插槽名称 */
  slot?: string
  /** 自定义渲染函数 */
  render?: (row: any, column: any, index: number) => any
}

export interface PaginationParams {
  page: number
  size: number
}

export interface PageResult<T> {
  records: T[]
  total: number
}

/* ========== Props ========== */

interface Props {
  /** 列配置 */
  columns: TableColumn[]
  /** API 方法 */
  api: (params: PaginationParams) => Promise<PageResult<T>>
  /** 是否显示分页 */
  showPagination?: boolean
  /** 分页布局 */
  paginationLayout?: string
  /** 每页显示数量选项 */
  pageSizes?: number[]
  /** 默认每页数量 */
  defaultPageSize?: number
  /** 表格高度 */
  height?: string | number
  /** 最大高度 */
  maxHeight?: string | number
  /** 是否显示斑马纹 */
  stripe?: boolean
  /** 是否显示边框 */
  border?: boolean
  /** 是否显示表头 */
  showHeader?: boolean
  /** 是否显示多选列 */
  showSelection?: boolean
  /** 是否显示序号列 */
  showIndex?: boolean
  /** 序号计算方法 */
  indexMethod?: (index: number) => number
  /** 行是否可选 */
  selectable?: (row: T, index: number) => boolean
  /** 操作列宽度 */
  actionWidth?: number | string
  /** 操作列固定方式 */
  actionFixed?: 'left' | 'right' | true | false
  /** 空状态文案 */
  emptyText?: string
  /** 空状态图片大小 */
  emptyImageSize?: number
  /** 初始请求参数 */
  initParams?: Record<string, any>
  /** 是否立即加载数据 */
  immediate?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  showPagination: true,
  paginationLayout: 'total, sizes, prev, pager, next, jumper',
  pageSizes: () => [10, 20, 50, 100],
  defaultPageSize: 10,
  stripe: true,
  border: false,
  showHeader: true,
  showSelection: false,
  showIndex: false,
  actionWidth: 120,
  actionFixed: 'right',
  emptyText: () => i18n.global.t('common.noData'),
  emptyImageSize: 120,
  immediate: true
})

/* ========== Emits ========== */

const emit = defineEmits<{
  selectionChange: [selection: T[]]
  sortChange: [sort: { column: any; prop: string; order: string | null }]
}>()

/* ========== 响应式状态 ========== */

const loading = ref(false)
const tableData = ref<T[]>([])
const total = ref(0)

// 分页状态
const currentPage = ref(1)
const pageSize = ref(props.defaultPageSize)

// 附加参数（用于筛选等）
const extraParams = ref<Record<string, any>>(props.initParams || {})

/* ========== 计算属性 ========== */

const paginationParams = computed<PaginationParams>(() => ({
  page: currentPage.value,
  size: pageSize.value
}))

/* ========== 方法 ========== */

/**
 * 加载数据
 */
const loadData = async () => {
  if (!props.api) return

  loading.value = true
  try {
    const result = await props.api({
      ...paginationParams.value,
      ...extraParams.value
    })
    tableData.value = result.records || []
    total.value = result.total || 0
  } catch (error) {
    console.error('加载数据失败:', error)
    tableData.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/**
 * 刷新数据（保持当前页）
 */
const refresh = () => {
  return loadData()
}

/**
 * 重置到第一页
 */
const reset = () => {
  currentPage.value = 1
  return loadData()
}

/**
 * 设置额外参数（筛选条件等）
 */
const setExtraParams = (params: Record<string, any>) => {
  extraParams.value = { ...params }
  currentPage.value = 1
  return loadData()
}

/**
 * 获取选中的行
 */
const getSelectionRows = () => {
  return selectionRows.value
}

/* ========== 事件处理 ========== */

const selectionRows = ref<T[]>([])

const handleSelectionChange = (selection: T[]) => {
  selectionRows.value = selection
  emit('selectionChange', selection)
}

const handleSortChange = (sort: { column: any; prop: string; order: string | null }) => {
  emit('sortChange', sort)
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  loadData()
}

/* ========== 生命周期 ========== */

onMounted(() => {
  if (props.immediate) {
    loadData()
  }
})

/* ========== 暴露给父组件 ========== */

defineExpose({
  refresh,
  reset,
  setExtraParams,
  getSelectionRows,
  loadData
})
</script>

<style scoped>
.eify-table-container {
  background-color: var(--eify-bg-base);
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-card-shadow);
  overflow: auto;
}

/* 表格样式覆盖 */
:deep(.el-table) {
  font-size: 14px;
}

:deep(.el-table__header-wrapper) {
  background-color: var(--eify-bg-surface);
}

:deep(.el-table th) {
  background-color: var(--eify-bg-surface);
  color: var(--eify-text-secondary);
  font-weight: 600;
  height: 52px;
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
}

.eify-table-header-label {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: default;
}

:deep(.el-table td) {
  height: var(--eify-table-row-height);
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
}

:deep(.el-table tr:hover > td) {
  background-color: var(--eify-primary-50);
}

:deep(.el-table__empty-block) {
  min-height: 200px;
}

/* 空状态样式 */
.eify-table-empty-text {
  color: var(--eify-text-tertiary);
}

/* 分页样式 */
.eify-table-pagination {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: var(--eify-spacing-3);
  padding: var(--eify-spacing-4) var(--eify-spacing-5);
  border-top: 1px solid var(--eify-border-subtle);
  background-color: var(--eify-bg-base);
}

.eify-table-pagination-info {
  font-size: 13px;
  color: var(--eify-text-secondary);
  margin-right: var(--eify-spacing-2);
}

.eify-table-pagination-info strong {
  color: var(--eify-text-primary);
  font-weight: 600;
}

:deep(.el-pagination) {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  font-weight: 400;
}

:deep(.el-pagination .el-pagination__total) {
  color: var(--eify-text-secondary);
  font-size: 13px;
}

:deep(.el-pagination .el-pagination__sizes) {
  color: var(--eify-text-secondary);
}

:deep(.el-pagination .el-select .el-input__wrapper) {
  border-radius: var(--eify-radius-md);
  border-color: var(--eify-border-default);
  transition: var(--eify-transition-base);
}

:deep(.el-pagination .el-select .el-input__wrapper:hover) {
  border-color: var(--eify-primary);
}

:deep(.el-pagination .el-select .el-input__wrapper.is-focus) {
  border-color: var(--eify-primary);
  box-shadow: var(--eify-shadow-primary);
}

:deep(.el-pagination.is-background .el-pager li) {
  border-radius: var(--eify-radius-md);
  font-weight: 500;
  transition: var(--eify-transition-base);
  min-width: 32px;
  height: 32px;
  line-height: 32px;
}

:deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background: var(--eify-gradient-primary);
  color: var(--eify-text-inverse);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

:deep(.el-pagination.is-background .el-pager li:hover) {
  color: var(--eify-primary);
  background-color: var(--eify-primary-50);
}

:deep(.el-pagination.is-background .btn-next),
:deep(.el-pagination.is-background .btn-prev) {
  border-radius: var(--eify-radius-md);
  min-width: 32px;
  height: 32px;
  padding: 0;
  transition: var(--eify-transition-base);
}

:deep(.el-pagination.is-background .btn-next:hover),
:deep(.el-pagination.is-background .btn-prev:hover) {
  color: var(--eify-primary);
  background-color: var(--eify-primary-50);
}

:deep(.el-pagination .el-pagination__jump) {
  color: var(--eify-text-secondary);
  font-size: 13px;
}

:deep(.el-pagination .el-pagination__jump .el-input__wrapper) {
  border-radius: var(--eify-radius-md);
  border-color: var(--eify-border-default);
  transition: var(--eify-transition-base);
}

:deep(.el-pagination .el-pagination__jump .el-input__wrapper:hover) {
  border-color: var(--eify-primary);
}

:deep(.el-pagination .el-pagination__jump .el-input__wrapper.is-focus) {
  border-color: var(--eify-primary);
  box-shadow: var(--eify-shadow-primary);
}

/* 响应式 */
@media (max-width: 768px) {
  .eify-table-pagination {
    justify-content: center;
  }

  :deep(.el-pagination) {
    flex-wrap: wrap;
    justify-content: center;
  }
}
</style>
