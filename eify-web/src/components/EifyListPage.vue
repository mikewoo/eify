<template>
  <div class="eify-list-page">
    <PageLayout :title="title" :description="description">
      <template #actions>
        <slot name="actions"></slot>
      </template>

      <!-- 视图切换和搜索 -->
      <div class="eify-list-header">
        <div class="header-left">
          <!-- 视图切换 -->
          <div v-if="showViewToggle" class="view-toggle">
            <div
              class="toggle-btn"
              :class="{ active: viewMode === 'table' }"
              @click="viewMode = 'table'"
            >
              <el-icon><List /></el-icon>
              {{ t('common.viewList') }}
            </div>
            <div
              class="toggle-btn"
              :class="{ active: viewMode === 'card' }"
              @click="viewMode = 'card'"
            >
              <el-icon><Grid /></el-icon>
              {{ t('common.viewCard') }}
            </div>
          </div>

          <!-- 高级搜索组件 -->
          <EifySearch
            v-model="searchConditions"
            :placeholder="searchPlaceholder"
            :options="{
              showShortcut: true,
              fields: searchFields
            }"
            @search="handleAdvancedSearch"
          />
        </div>

        <!-- 统计信息 -->
        <div v-if="showStats" class="eify-list-stats">
          <div
            v-for="stat in stats"
            :key="stat.key"
            class="stat-item"
          >
            <div class="stat-value" :class="stat.class">{{ stat.value }}</div>
            <div class="stat-label">{{ stat.label }}</div>
          </div>
        </div>
      </div>

      <!-- 搜索结果提示 -->
      <div v-if="searchConditions.length > 0 && showSearchHint" class="search-result-hint">
        <span class="hint-text">
          {{ t('common.filterHint', { count: searchConditions.length, total: filteredCount }) }}
        </span>
        <el-button link type="primary" size="small" @click="handleClearSearch">
          <el-icon><Close /></el-icon>
          {{ t('common.clearFilter') }}
        </el-button>
      </div>

      <!-- 工具栏额外内容 -->
      <slot name="toolbar"></slot>

      <!-- 列表视图 -->
      <div v-show="viewMode === 'table'" class="table-view">
        <EifyTable
          ref="tableRef"
          :columns="tableColumns"
          :api="fetchData"
          :show-pagination="showPagination"
          :default-page-size="defaultPageSize"
          :page-sizes="pageSizes"
          :action-width="actionWidth"
          :action-fixed="actionFixed"
          :height="tableHeight"
        >
          <template v-for="slot in tableColumnSlots" #[slot]="scope">
            <slot :name="`table-${slot}`" v-bind="scope"></slot>
          </template>
          <template #actions="scope">
            <slot name="table-actions" v-bind="scope"></slot>
          </template>
        </EifyTable>
      </div>

      <!-- 卡片视图 -->
      <div v-show="viewMode === 'card'" class="card-view-container">
        <div class="card-view">
          <slot
            name="card"
            :items="paginatedCardData"
            :loading="loading"
          ></slot>
        </div>

        <!-- 卡片视图分页 -->
        <div v-if="!loadAll && filteredCount > 0" class="card-pagination-wrapper">
          <div class="card-pagination-info">
            {{ t('common.showingRange', { start: cardPageStart, end: cardPageEnd, total: filteredCount }) }}
            <el-button
              v-if="filteredCount > cardPageSize"
              link
              type="primary"
              size="small"
              @click="handleLoadAll"
            >
              {{ t('common.loadAll') }}
            </el-button>
          </div>

          <div class="card-pagination">
            <el-pagination
              v-model:current-page="cardCurrentPage"
              :page-size="cardPageSize"
              :total="filteredCount"
              layout="prev, pager, next"
              :background="true"
              size="small"
              @current-change="handleCardPageChange"
            />
          </div>
        </div>

        <!-- 加载全部后的统计 -->
        <div v-else-if="loadAll && filteredCount > 0" class="card-all-loaded">
          <div class="loaded-info">
            <el-icon><Check /></el-icon>
            {{ t('common.loadedAll', { total: filteredCount }) }}
          </div>
          <el-button link type="primary" size="small" @click="loadAll = false">
            {{ t('common.backToPagination') }}
          </el-button>
        </div>
      </div>
    </PageLayout>
  </div>
</template>

<script setup lang="ts" generic="T extends Record<string, any>">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import i18n from '@/i18n'
import { List, Grid, Close, Check } from '@element-plus/icons-vue'
import { useAuthStore } from '@/store/auth'
import PageLayout from './PageLayout.vue'
import EifyTable from './EifyTable.vue'
import EifySearch from './EifySearch.vue'
import type { SearchCondition, SearchField } from './EifySearch.vue'
import type { TableColumn } from './EifyTable.vue'
import type { ListStat } from '@/types/api'

const authStore = useAuthStore()
const { t } = useI18n()

/* ========== 类型定义 ========== */

interface ListPageProps {
  // 页面基本信息
  title: string
  description?: string
  searchPlaceholder?: string

  // 搜索配置
  searchFields?: SearchField[]

  // 表格配置
  tableColumns: TableColumn[]
  fetchData: (params: { page: number; size: number; conditions?: SearchCondition[] }) => Promise<{
    records: T[]
    total: number
  }>
  showPagination?: boolean
  defaultPageSize?: number
  pageSizes?: number[]
  actionWidth?: number
  actionFixed?: 'left' | 'right' | false

  // 视图配置
  showViewToggle?: boolean
  defaultViewMode?: 'table' | 'card'

  // 卡片配置
  cardPageSize?: number

  // 统计配置
  showStats?: boolean
  stats?: ListStat[]

  // 其他
  showSearchHint?: boolean
}

/* ========== Props ========== */

const props = withDefaults(defineProps<ListPageProps>(), {
  description: '',
  searchPlaceholder: () => i18n.global.t('common.selectFilter'),
  searchFields: () => [],
  showPagination: true,
  defaultPageSize: 10,
  pageSizes: () => [10, 20, 50, 100],
  actionWidth: 280,
  actionFixed: 'right',
  showViewToggle: true,
  defaultViewMode: 'table',
  cardPageSize: 8,
  showStats: true,
  stats: () => [],
  showSearchHint: true
})

/* ========== Emits ========== */

const emit = defineEmits<{
  'search': [conditions: SearchCondition[]]
}>()

/* ========== 响应式状态 ========== */

const tableRef = ref()
const viewMode = ref<'table' | 'card'>(props.defaultViewMode)
const searchConditions = ref<SearchCondition[]>([])
const loading = ref(false)

// 表格高度计算
const tableHeight = ref<string>('')

// 计算表格可用高度
const calculateTableHeight = () => {
  const windowHeight = window.innerHeight
  // 减去：顶栏(56px) + 页面边距(48px) + 标题区域(80px) + 搜索栏(60px) + 统计(60px) + 分页(70px) + 余量(40px)
  const availableHeight = windowHeight - 56 - 48 - 80 - 60 - 60 - 70 - 40
  tableHeight.value = `${Math.max(availableHeight, 400)}px` // 最小400px
}

// 卡片视图分页状态
const cardCurrentPage = ref(1)
const loadAll = ref(false)
const filteredData = ref<T[]>([])
const filteredCount = ref(0)

/* ========== 计算属性 ========== */

// 表格列插槽名称
const tableColumnSlots = computed(() => {
  return props.tableColumns
    .filter(col => col.slot)
    .map(col => col.slot!)
})

// 卡片视图分页数据
const paginatedCardData = computed(() => {
  if (loadAll.value) {
    return filteredData.value
  }
  const start = (cardCurrentPage.value - 1) * props.cardPageSize
  const end = start + props.cardPageSize
  return filteredData.value.slice(start, end)
})

// 卡片分页范围
const cardPageStart = computed(() => {
  if (loadAll.value) return 1
  const start = (cardCurrentPage.value - 1) * props.cardPageSize
  return Math.min(start + 1, filteredData.value.length)
})

const cardPageEnd = computed(() => {
  if (loadAll.value) return filteredData.value.length
  const end = cardCurrentPage.value * props.cardPageSize
  return Math.min(end, filteredData.value.length)
})

/* ========== 方法 ========== */

// 高级搜索处理
const handleAdvancedSearch = async (conditions: SearchCondition[]) => {
  searchConditions.value = [...conditions]
  cardCurrentPage.value = 1

  // 如果是卡片视图，重新获取数据
  if (viewMode.value === 'card') {
    await loadCardData()
  }

  // 触发搜索事件
  emit('search', conditions)
}

// 清空搜索
const handleClearSearch = () => {
  searchConditions.value = []
  cardCurrentPage.value = 1
  filteredData.value = []
  filteredCount.value = 0

  // 刷新表格
  tableRef.value?.refresh()

  // 触发搜索事件
  emit('search', [])
}

// 加载卡片数据
const loadCardData = async () => {
  loading.value = true
  try {
    // 使用合理的分页大小获取数据
    const result = await props.fetchData({
      page: 1,
      size: 100, // 使用后端允许的最大分页大小
      conditions: searchConditions.value
    })
    filteredData.value = result.records
    filteredCount.value = result.total
  } catch {
    filteredData.value = []
    filteredCount.value = 0
  } finally {
    loading.value = false
  }
}

// 卡片视图分页切换
const handleCardPageChange = (page: number) => {
  cardCurrentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 加载全部
const handleLoadAll = () => {
  loadAll.value = true
}

// 刷新数据
const refresh = () => {
  // 刷新表格时传递搜索条件
  if (tableRef.value) {
    tableRef.value.setExtraParams({
      conditions: searchConditions.value
    })
  }
  if (viewMode.value === 'card') {
    loadCardData()
  }
}

// 窗口大小变化处理
const handleResize = () => {
  calculateTableHeight()
}

/* ========== 监听 ========== */

// 监听视图模式变化
watch(viewMode, (newMode) => {
  if (newMode === 'card') {
    loadCardData()
  }
  cardCurrentPage.value = 1
})

// 监听工作空间切换 — 自动刷新列表数据
// 使用 refreshKey 计数器确保每次切换都触发（由 authStore.switchWorkspace 递增）
watch(() => authStore.refreshKey, () => {

  searchConditions.value = []
  cardCurrentPage.value = 1
  loadAll.value = false
  refresh()
})

// 监听窗口大小变化
watch(() => [props.showStats, props.showViewToggle], () => {
  calculateTableHeight()
}, { flush: 'post' })

/* ========== 生命周期 ========== */

onMounted(() => {
  calculateTableHeight()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
})

/* ========== 暴露给父组件 ========== */

defineExpose({
  refresh,
  tableRef,
  searchConditions,
  viewMode
})
</script>

<style scoped>
/* ========== 头部区域 ========== */
.eify-list-page {
  width: 100%;
}

.eify-list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--eify-spacing-6);
  gap: var(--eify-spacing-4);
}

.header-left {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-4);
  flex: 1 1 0;
  min-width: 0;
}

/* 特别针对 EifySearch 组件 */
.header-left > :deep(.eify-advanced-search) {
  flex: 1 1 0;
  min-width: 0;
}

/* 视图切换 */
.view-toggle {
  display: flex;
  flex-shrink: 0;
  background: var(--eify-bg-surface);
  border-radius: var(--eify-radius-md);
  padding: 4px;
  gap: 4px;
}

.toggle-btn {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  padding: var(--eify-spacing-2) var(--eify-spacing-4);
  border-radius: var(--eify-radius-sm);
  cursor: pointer;
  transition: var(--eify-transition-base);
  color: var(--eify-text-secondary);
  font-size: 14px;
}

.toggle-btn:hover {
  color: var(--eify-text-primary);
  background: var(--eify-bg-surface-raised);
}

.toggle-btn.active {
  background: #ffffff;
  color: var(--eify-primary);
  box-shadow: var(--eify-shadow-sm);
}

/* 统计信息 */
.eify-list-stats {
  display: flex;
  gap: var(--eify-spacing-6);
}

.stat-item {
  text-align: center;
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  line-height: 1;
  margin-bottom: var(--eify-spacing-1);
}

.stat-label {
  font-size: 12px;
  color: var(--eify-text-tertiary);
}

/* ========== 搜索结果提示 ========== */
.search-result-hint {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
  margin-bottom: var(--eify-spacing-4);
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.05) 0%,
    rgba(139, 92, 246, 0.03) 100%
  );
  border: 1px solid rgba(99, 102, 241, 0.1);
  border-radius: var(--eify-radius-md);
}

.hint-text {
  font-size: 13px;
  color: var(--eify-text-secondary);
}

.hint-text strong {
  color: var(--eify-primary);
  font-weight: 600;
}

.search-result-hint .el-button {
  font-size: 13px;
  padding: 0;
  height: auto;
}

/* ========== 卡片视图 ========== */
.card-view-container {
  background-color: #ffffff;
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-card-shadow);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.card-view {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--eify-spacing-4);
  padding: var(--eify-spacing-4);
  flex: 1;
  overflow-y: auto;
  min-height: 300px;
}

/* 计算卡片视图的可用高度 */
@media (min-height: 800px) {
  .card-view {
    max-height: calc(100vh - 320px);
  }
}

@media (min-height: 900px) {
  .card-view {
    max-height: calc(100vh - 340px);
  }
}

@media (min-height: 1000px) {
  .card-view {
    max-height: calc(100vh - 360px);
  }
}

@media (min-height: 1200px) {
  .card-view {
    max-height: calc(100vh - 400px);
  }
}

.card-view::-webkit-scrollbar {
  width: 6px;
}

.card-view::-webkit-scrollbar-track {
  background: var(--eify-bg-surface);
}

.card-view::-webkit-scrollbar-thumb {
  background: var(--eify-gray-300);
  border-radius: var(--eify-radius-full);
}

.card-view::-webkit-scrollbar-thumb:hover {
  background: var(--eify-gray-400);
}

/* ========== 卡片分页 ========== */
.card-pagination-wrapper {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
  border-top: 1px solid var(--eify-border-subtle);
  background: linear-gradient(180deg, #ffffff 0%, var(--eify-bg-surface) 100%);
  position: relative;
  min-height: 48px;
}

.card-pagination-wrapper::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 1px;
  background: linear-gradient(90deg,
    transparent 0%,
    rgba(99, 102, 241, 0.1) 50%,
    transparent 100%
  );
}

.card-pagination-info {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  font-size: 12px;
  color: var(--eify-text-secondary);
}

.card-pagination-info .highlight {
  color: var(--eify-primary);
  font-weight: 600;
  padding: 0 2px;
}

.card-pagination {
  display: flex;
  align-items: center;
}

.card-pagination :deep(.el-pagination) {
  gap: var(--eify-spacing-1);
}

.card-pagination :deep(.el-pagination.is-background .el-pager li) {
  border-radius: var(--eify-radius-md);
  font-weight: 500;
  transition: var(--eify-transition-base);
  min-width: 28px;
  height: 28px;
  line-height: 28px;
  font-size: 13px;
}

.card-pagination :deep(.el-pagination.is-background .el-pager li:not(.is-disabled).is-active) {
  background: var(--eify-gradient-primary);
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
}

.card-pagination :deep(.el-pagination.is-background .el-pager li:hover) {
  color: var(--eify-primary);
  background-color: var(--eify-primary-50);
}

.card-pagination :deep(.el-pagination.is-background .btn-next),
.card-pagination :deep(.el-pagination.is-background .btn-prev) {
  border-radius: var(--eify-radius-md);
  min-width: 28px;
  height: 28px;
  padding: 0;
}

/* 加载全部状态 */
.card-all-loaded {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-4) var(--eify-spacing-5);
  border-top: 1px solid var(--eify-border-subtle);
  background: linear-gradient(180deg, #ffffff 0%, var(--eify-bg-surface) 100%);
}

.loaded-info {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  font-size: 13px;
  color: var(--eify-text-secondary);
}

.loaded-info .el-icon {
  color: var(--eify-success);
  font-size: 16px;
}

/* ========== 大屏幕优化 ========== */
@media (min-width: 1680px) {
  .card-view {
    grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  }
}

@media (min-width: 1920px) {
  .card-view {
    grid-template-columns: repeat(auto-fill, minmax(360px, 1fr));
    gap: var(--eify-spacing-5);
    padding: var(--eify-spacing-5);
  }
}

/* ========== 列表视图 ========== */
.table-view {
  min-height: 400px;
}

/* ========== 响应式 ========== */
@media (max-width: 768px) {
  .eify-list-header {
    flex-direction: column;
    align-items: stretch;
  }

  .card-view {
    grid-template-columns: 1fr;
    padding: var(--eify-spacing-4);
  }

  .card-pagination-wrapper {
    flex-direction: column;
    gap: var(--eify-spacing-3);
    text-align: center;
  }

  .card-pagination {
    width: 100%;
    justify-content: center;
  }
}

@media (max-width: 480px) {
  .card-pagination-wrapper,
  .card-all-loaded {
    padding: var(--eify-spacing-4);
  }

  .card-pagination-info {
    flex-direction: column;
    gap: var(--eify-spacing-2);
    font-size: 12px;
  }

  .eify-list-stats {
    gap: var(--eify-spacing-4);
  }

  .stat-item {
    flex: 1;
  }

  .stat-value {
    font-size: 18px;
  }
}
</style>
