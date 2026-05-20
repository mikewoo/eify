<template>
  <EifyListPage
    ref="listPageRef"
    :title="t('workflow.title')"
    :description="t('workflow.description')"
    :search-placeholder="t('workflow.searchPlaceholder')"
    :search-fields="searchFieldsConfig"
    :table-columns="columns"
    :fetch-data="fetchWorkflows"
    :show-pagination="true"
    :default-page-size="10"
    :page-sizes="[10, 20, 50, 100]"
    :action-width="260"
    :show-view-toggle="true"
    :card-page-size="8"
    :show-stats="true"
    :stats="statsConfig"
  >
    <!-- 操作按钮 -->
    <template #actions>
      <el-button type="primary" class="eify-btn-primary-gradient" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        {{ t('workflow.addWorkflow') }}
      </el-button>
    </template>

    <!-- 表格列插槽 -->
    <template #table-name="{ row }">
      <div class="name-cell">
        <div class="avatar-placeholder workflow-avatar">
          <el-icon><Connection /></el-icon>
        </div>
        <div class="name-info">
          <div class="name-text">{{ row.name }}</div>
          <div class="model-text">v{{ row.version }}</div>
        </div>
      </div>
    </template>

    <template #table-status="{ row }">
      <div class="status-cell">
        <span class="status-dot" :class="statusClass(row.status)"></span>
        <span class="status-text">{{ statusLabel(row.status) }}</span>
      </div>
    </template>

    <template #table-nodeCount="{ row }">
      <el-tag size="small" effect="plain" type="info">{{ t('workflow.nodeCount', { count: row.nodeCount }) }}</el-tag>
    </template>

    <template #table-edgeCount="{ row }">
      <el-tag size="small" effect="plain" type="info">{{ t('workflow.edgeCount', { count: row.edgeCount }) }}</el-tag>
    </template>

    <template #table-actions="{ row }">
      <div class="table-action-buttons">
        <el-button size="small" @click="handleEdit(row)">
          <el-icon><Edit /></el-icon>
          {{ t('common.edit') }}
        </el-button>
        <el-button size="small" type="danger" @click="handleDelete(row)">
          <el-icon><Delete /></el-icon>
          {{ t('common.delete') }}
        </el-button>
      </div>
    </template>

    <!-- 卡片视图插槽 -->
    <template #card="{ items }">
      <div
        v-for="item in items"
        :key="item.id"
        class="workflow-card"
        :class="{ disabled: item.status === 2 }"
      >
        <div class="card-header">
          <div class="card-avatar-placeholder workflow-card-avatar">
            <el-icon><Connection /></el-icon>
          </div>
          <div class="card-title">
            <h3>{{ item.name }}</h3>
            <el-tag size="small" effect="plain">v{{ item.version }}</el-tag>
            <span class="card-status-badge" :class="statusClass(item.status)">
              {{ statusLabel(item.status) }}
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="card-info-row" v-if="item.description">
            <span class="info-label">{{ t('common.description') }}</span>
            <el-tooltip :content="item.description" placement="top" :show-after="300">
              <span class="info-value prompt-tooltip">{{ truncateText(item.description, 50) }}</span>
            </el-tooltip>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('workflow.nodeCountLabel') }}</span>
            <el-tag size="small" effect="plain">{{ item.nodeCount }}</el-tag>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('workflow.edgeCountLabel') }}</span>
            <el-tag size="small" effect="plain">{{ item.edgeCount }}</el-tag>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('common.updateTime') }}</span>
            <span class="info-value">{{ formatDate(item.updatedAt) }}</span>
          </div>
        </div>

        <div class="card-footer">
          <el-button size="small" @click="handleEdit(item)">
            <el-icon><Edit /></el-icon>
            {{ t('common.edit') }}
          </el-button>
          <el-button size="small" type="danger" @click="handleDelete(item)">
            <el-icon><Delete /></el-icon>
            {{ t('common.delete') }}
          </el-button>
        </div>
      </div>
    </template>
  </EifyListPage>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Connection } from '@element-plus/icons-vue'
import EifyListPage from '@/components/EifyListPage.vue'

const { t } = useI18n()
import type { ListStat } from '@/types/api'
import {
  workflowApi,
  type WorkflowResponse,
  type WorkflowListParams
} from '@/api/workflow'
import type { TableColumn } from '@/components/EifyTable.vue'
import type { SearchCondition } from '@/components/EifySearch.vue'

/* ========== 表格配置 ========== */

const columns = computed<TableColumn[]>(() => [
  { prop: 'name', label: t('common.name'), minWidth: 180, slot: 'name' },
  { prop: 'description', label: t('common.description'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'status', label: t('common.status'), minWidth: 90, slot: 'status' },
  { prop: 'version', label: t('workflow.version'), minWidth: 80 },
  { prop: 'nodeCount', label: t('workflow.nodes'), minWidth: 100, slot: 'nodeCount' },
  { prop: 'edgeCount', label: t('workflow.edges'), minWidth: 100, slot: 'edgeCount' },
  { prop: 'updatedAt', label: t('common.updateTime'), minWidth: 150 }
])

/* ========== 搜索配置 ========== */

const searchFieldsConfig = computed(() => [
  {
    key: 'name',
    label: t('common.name'),
    description: t('workflow.searchPlaceholder'),
    inputType: 'text' as const,
    placeholder: t('workflow.workflowNamePlaceholder')
  },
  {
    key: 'status',
    label: t('common.status'),
    description: t('workflow.searchByStatus'),
    inputType: 'select' as const,
    options: [
      { label: t('workflow.statusDraft'), value: 0 },
      { label: t('workflow.statusPublished'), value: 1 },
      { label: t('common.disabled'), value: 2 }
    ]
  }
])

/* ========== 响应式状态 ========== */

const listPageRef = ref()
const router = useRouter()

/* ========== 统计配置 ========== */

const statsData = ref({ total: 0, published: 0, draft: 0, disabled: 0 })
const statsConfig = computed<ListStat[]>(() => [
  { key: 'total', label: t('common.total'), value: statsData.value.total || '-' },
  { key: 'published', label: t('workflow.publishedLabel'), value: statsData.value.published || '-', class: 'enabled' },
  { key: 'draft', label: t('workflow.draftLabel'), value: statsData.value.draft || '-', class: 'draft' },
  { key: 'disabled', label: t('common.disabled'), value: statsData.value.disabled || '-', class: 'disabled' }
])

/* ========== 工具方法 ========== */

const statusLabel = (status: number): string => {
  switch (status) {
    case 0: return t('workflow.statusDraft')
    case 1: return t('workflow.statusPublished')
    case 2: return t('common.disabled')
    default: return t('workflow.statusUnknown')
  }
}

const statusClass = (status: number): string => {
  switch (status) {
    case 0: return 'draft'
    case 1: return 'published'
    case 2: return 'disabled'
    default: return ''
  }
}

const truncateText = (text: string, maxLength: number): string => {
  if (!text) return ''
  return text.length > maxLength ? text.substring(0, maxLength) + '...' : text
}

const formatDate = (dateStr: string): string => {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

/* ========== API 调用 ========== */

const fetchWorkflows = async (params: {
  page: number
  size: number
  conditions?: SearchCondition[]
}) => {
  const queryParams: WorkflowListParams = {
    page: params.page,
    pageSize: params.size
  }

  if (params.conditions && params.conditions.length > 0) {
    const nameCondition = params.conditions.find(c => c.field === 'name')
    if (nameCondition && nameCondition.value) {
      ;(queryParams as any).name = nameCondition.value
    }
    const statusCondition = params.conditions.find(c => c.field === 'status')
    if (statusCondition && statusCondition.value !== undefined) {
      ;(queryParams as any).status = statusCondition.value
    }
  }

  const result = await workflowApi.getList(queryParams)
  const records = (result as any).list || result.records || []

  updateStats(records)

  return {
    records,
    total: result.total
  }
}

const deleteWorkflow = async (id: number) => {
  await workflowApi.delete(id)
  ElMessage.success(t('common.deleteSuccess'))
  listPageRef.value?.refresh()
}

const updateStats = (list: WorkflowResponse[]) => {
  if (!Array.isArray(list)) list = []
  statsData.value = {
    total: list.length,
    published: list.filter(w => w.status === 1).length,
    draft: list.filter(w => w.status === 0).length,
    disabled: list.filter(w => w.status === 2).length
  }
}

/* ========== 事件处理 ========== */

const handleAdd = () => {
  router.push('/workflows/create')
}

const handleEdit = (row: { id: number }) => {
  router.push(`/workflows/${row.id}/edit`)
}

const handleDelete = async (row: { id: number; name: string }) => {
  try {
    await ElMessageBox.confirm(
      t('workflow.confirmDeleteWithName', { name: row.name }),
      t('common.confirmDeleteTitle'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )
    await deleteWorkflow(row.id)
  } catch {
    // 用户取消
  }
}

</script>

<style scoped>
/* ========== 名称单元格 ========== */

.name-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

.avatar-placeholder.workflow-avatar {
  width: 32px;
  height: 32px;
  border-radius: var(--eify-radius-md);
  background: linear-gradient(135deg, var(--eify-primary-50), var(--eify-primary-100));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--eify-primary);
  font-size: 16px;
  flex-shrink: 0;
}

.name-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.name-text {
  font-size: 14px;
  font-weight: 500;
  color: var(--eify-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.model-text {
  font-size: 12px;
  color: var(--eify-text-tertiary);
}

/* ========== 状态单元格 ========== */

.status-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: var(--eify-gray-400);
  flex-shrink: 0;
}

.status-dot.published {
  background-color: var(--eify-success);
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
}

.status-dot.draft {
  background-color: var(--eify-warning);
  box-shadow: 0 0 6px rgba(245, 158, 11, 0.4);
}

.status-dot.disabled {
  background-color: var(--eify-gray-400);
}

.status-text {
  font-size: 13px;
  color: var(--eify-text-secondary);
}

/* ========== 表格操作按钮 ========== */

.table-action-buttons {
  display: flex;
  gap: var(--eify-spacing-2);
  justify-content: flex-end;
}

/* ========== 卡片视图 ========== */

.workflow-card {
  background: #ffffff;
  border: 1px solid var(--eify-border-subtle);
  border-radius: var(--eify-card-radius);
  padding: var(--eify-spacing-4);
  transition: var(--eify-transition-base);
  display: flex;
  flex-direction: column;
}

.workflow-card:hover {
  border-color: var(--eify-primary-300);
  box-shadow: var(--eify-shadow-md);
  transform: translateY(-2px);
}

.workflow-card.disabled {
  opacity: 0.6;
}

.card-header {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
  margin-bottom: var(--eify-spacing-3);
}

.workflow-card-avatar {
  width: 42px;
  height: 42px;
  border-radius: var(--eify-radius-lg);
  background: linear-gradient(135deg, var(--eify-primary-50), var(--eify-primary-100));
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--eify-primary);
  font-size: 20px;
  flex-shrink: 0;
}

.card-title {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}

.card-title h3 {
  font-size: 15px;
  font-weight: 600;
  margin: 0;
  color: var(--eify-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-status-badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: var(--eify-radius-full);
  font-weight: 500;
  margin-left: auto;
}

.card-status-badge.published {
  background: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.card-status-badge.draft {
  background: rgba(245, 158, 11, 0.1);
  color: #d97706;
}

.card-status-badge.disabled {
  background: rgba(156, 163, 175, 0.1);
  color: #6b7280;
}

.card-body {
  flex: 1;
  margin-bottom: var(--eify-spacing-3);
}

.card-info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--eify-spacing-1) 0;
  font-size: 13px;
}

.card-info-row .info-label {
  color: var(--eify-text-tertiary);
  font-weight: 500;
}

.card-info-row .info-value {
  color: var(--eify-text-secondary);
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-info-row .info-value.prompt-tooltip {
  cursor: help;
}

.card-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--eify-spacing-2);
  padding-top: var(--eify-spacing-3);
  border-top: 1px solid var(--eify-border-subtle);
}
</style>
