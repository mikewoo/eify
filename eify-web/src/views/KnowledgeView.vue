<template>
  <EifyListPage
    ref="listPageRef"
    :title="t('knowledge.title')"
    :description="t('knowledge.description')"
    :search-placeholder="t('knowledge.searchPlaceholder')"
    :search-fields="searchFieldsConfig"
    :table-columns="columns"
    :fetch-data="fetchKnowledgeList"
    :show-pagination="false"
    :show-view-toggle="true"
    :card-page-size="8"
    :show-stats="true"
    :stats="statsConfig"
    :action-width="320"
    @search="handleAdvancedSearch"
  >
    <!-- 操作按钮 -->
    <template #actions>
      <el-button type="primary" class="eify-btn-primary-gradient" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        {{ t('knowledge.addKnowledge') }}
      </el-button>
    </template>

    <!-- 表格列插槽 -->
    <template #table-name="{ row }">
      <div class="name-cell">
        <div class="kb-icon text-xl">
          <el-icon><Collection /></el-icon>
        </div>
        <div class="name-info">
          <div class="name-text">{{ row.name }}</div>
          <div class="model-text text-xs">
            <span v-if="row.embeddingModelId" class="provider-model-badge">{{ row.embeddingModel }}</span>
            <el-tooltip v-else :content="t('knowledge.globalConfigFallback')">
              <span class="global-model-text">{{ row.embeddingModel }} *</span>
            </el-tooltip>
          </div>
        </div>
      </div>
    </template>

    <template #table-enabled="{ row }">
      <div class="status-cell">
        <span class="status-dot" :class="{ enabled: row.enabled === 1 }"></span>
        <span class="status-text text-base">{{ row.enabled === 1 ? t('common.enabled') : t('common.disabled') }}</span>
      </div>
    </template>

    <template #table-documentCount="{ row }">
      <el-tag size="small" effect="plain">{{ row.documentCount || 0 }}</el-tag>
    </template>

    <template #table-chunkCount="{ row }">
      <el-tag size="small" type="success" effect="plain">{{ row.chunkCount || 0 }}</el-tag>
    </template>

    <template #table-actions="{ row }">
      <div class="table-action-buttons">
        <el-button size="small" type="primary" class="text-xs" @click="handleViewDocuments(row)">
          <el-icon><FolderOpened /></el-icon>
          {{ t('knowledge.viewDocuments') }}
        </el-button>
        <el-button size="small" class="text-xs" @click="handleEdit(row)">
          <el-icon><Edit /></el-icon>
          {{ t('common.edit') }}
        </el-button>
        <el-button size="small" type="danger" class="text-xs" @click="handleDelete(row)">
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
        class="kb-card"
        :class="{ disabled: item.enabled === 0 }"
      >
        <div class="card-header">
          <div class="card-avatar-placeholder text-2xl">
            <el-icon><Collection /></el-icon>
          </div>
          <div class="card-title">
            <h3 class="text-lg">{{ item.name }}</h3>
            <el-tag v-if="item.embeddingModelId" size="small" type="success" effect="plain">{{ item.embeddingModel }}</el-tag>
            <el-tooltip v-else :content="t('knowledge.globalConfigFallback')">
              <span class="global-model-tag"><el-tag size="small" type="info" effect="plain">{{ item.embeddingModel }}</el-tag></span>
            </el-tooltip>
          </div>
          <div class="card-status">
            <span class="status-indicator" :class="{ enabled: item.enabled === 1 }">
              <span class="indicator-dot"></span>
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="card-info-row text-xs">
            <span class="info-label">{{ t('common.description') }}</span>
            <el-tooltip :content="item.description" placement="top" :show-after="300" :disabled="!item.description">
              <span class="info-value">{{ item.description || '-' }}</span>
            </el-tooltip>
          </div>
          <div class="card-info-row text-xs">
            <span class="info-label">{{ t('knowledge.vectorDimension') }}</span>
            <span class="info-value">{{ item.vectorDimension }}</span>
          </div>
          <div class="card-stats-row">
            <div class="stat-item">
              <span class="stat-value text-xl">{{ item.documentCount || 0 }}</span>
              <span class="stat-label text-xs">{{ t('knowledge.documentLabel') }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-value text-xl">{{ item.chunkCount || 0 }}</span>
              <span class="stat-label text-xs">{{ t('knowledge.chunkLabel') }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-value text-xl">{{ item.chunkSize }}</span>
              <span class="stat-label text-xs">{{ t('knowledge.chunkSizeLabel') }}</span>
            </div>
          </div>
        </div>

        <div class="card-footer">
          <el-button size="small" type="primary" @click="handleViewDocuments(item)">
            <el-icon><FolderOpened /></el-icon>
            {{ t('knowledge.viewDocuments') }}
          </el-button>
          <div class="action-buttons">
            <el-button size="small" class="text-xs" @click="handleEdit(item)">
              <el-icon><Edit /></el-icon>
              {{ t('common.edit') }}
            </el-button>
            <el-button size="small" type="danger" class="text-xs" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon>
              {{ t('common.delete') }}
            </el-button>
          </div>
        </div>
      </div>
    </template>
  </EifyListPage>

  <!-- 表单对话框 -->
  <EifyFormDialog
    ref="dialogRef"
    v-model="dialogVisible"
    :rules="formRules"
    :default-data="defaultFormData"
    :dialog-props="{
      addTitle: t('knowledge.addKnowledge'),
      editTitle: t('knowledge.editKnowledge'),
      submitText: t('common.confirm'),
      width: '600px',
      labelWidth: '120px'
    }"
    @submit="handleSubmit"
  >
    <template #form="{ data }">
      <el-form-item :label="t('knowledge.knowledgeName')" prop="name">
        <el-input
          v-model="data.name"
          :placeholder="t('knowledge.knowledgeNamePlaceholder')"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="t('knowledge.knowledgeDesc')" prop="description">
        <el-input
          v-model="data.description"
          type="textarea"
          :rows="3"
          :placeholder="t('knowledge.knowledgeDescPlaceholder')"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="t('knowledge.embeddingProvider')" prop="providerId">
        <el-select
          v-model="data.providerId"
          :placeholder="t('knowledge.embeddingProviderPlaceholder')"
          style="width: 100%"
          @change="(val: number) => onEmbeddingProviderChange(val, data)"
        >
          <el-option
            v-for="p in embeddingProviders"
            :key="p.id"
            :label="p.name"
            :value="p.id"
          >
            <span>{{ p.name }}</span>
            <el-tag size="small" style="margin-left: 8px" effect="plain">{{ p.type }}</el-tag>
          </el-option>
        </el-select>
        <div class="form-hint text-xs" v-if="embeddingProviders.length === 0">
          {{ t('knowledge.noEmbeddingProviderHint') }}
          <el-link type="primary" @click="router.push('/providers')">
            {{ t('knowledge.goAddProvider') }}
          </el-link>
        </div>
      </el-form-item>

      <el-form-item :label="t('knowledge.embeddingModel')" prop="embeddingModelId">
        <el-select
          v-model="data.embeddingModelId"
          :placeholder="t('knowledge.embeddingModelPlaceholder')"
          style="width: 100%"
          :disabled="!data.providerId"
          :loading="embeddingModelsLoading"
          @change="(val: number) => onEmbeddingModelChange(val, data)"
        >
          <el-option
            v-for="m in embeddingModels"
            :key="m.id"
            :label="`${m.displayName} (${m.modelName})`"
            :value="m.id"
          />
        </el-select>
        <div class="form-hint text-xs" v-if="data.providerId && !embeddingModelsLoading && embeddingModels.length === 0">
          {{ t('knowledge.noEmbeddingModelHint') }}
        </div>
      </el-form-item>

      <el-form-item :label="t('knowledge.vectorDimension')" prop="vectorDimension">
        <el-input-number
          v-model="data.vectorDimension"
          :min="0"
          :max="8192"
          :step="1"
          style="width: 100%"
        />
        <div class="form-hint text-xs" v-if="selectedModelDimension">
          {{ t('knowledge.vectorDimensionAutoHint', { dim: selectedModelDimension }) }}
        </div>
      </el-form-item>

      <el-form-item :label="t('knowledge.chunkSize')" prop="chunkSize">
        <el-input-number
          v-model="data.chunkSize"
          :min="0"
          :max="5000"
          :step="50"
          style="width: 100%"
        />
        <div class="form-hint text-xs">{{ t('knowledge.chunkSizeHint') }}</div>
      </el-form-item>

      <el-form-item :label="t('knowledge.chunkOverlap')" prop="chunkOverlap">
        <el-input-number
          v-model="data.chunkOverlap"
          :min="0"
          :max="500"
          :step="10"
          style="width: 100%"
        />
        <div class="form-hint text-xs">{{ t('knowledge.chunkOverlapHint') }}</div>
      </el-form-item>
    </template>
  </EifyFormDialog>

  <ConfirmDialog
    :show="showDeleteConfirm"
    :title="t('common.confirmDeleteTitle')"
    :message="deleteTarget ? t('knowledge.confirmDelete', { name: deleteTarget.name }) : ''"
    type="danger"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Plus,
  Edit,
  Delete,
  Collection,
  FolderOpened
} from '@element-plus/icons-vue'
import EifyListPage from '@/components/EifyListPage.vue'
import EifyFormDialog from '@/components/EifyFormDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  knowledgeApi,
  type KnowledgeBaseResponse,
  type KnowledgeCreateRequest
} from '@/api/knowledge'
import { providerApi, type ProviderResponse, type ModelConfigInfo } from '@/api/provider'
import type { TableColumn } from '@/components/EifyTable.vue'
import type { SearchCondition } from '@/components/EifySearch.vue'
import type { ListStat } from '@/types/api'

const router = useRouter()
const { t } = useI18n()

/* ========== 类型定义 ========== */

interface KnowledgeFormData {
  id?: number
  name: string
  description: string
  embeddingModel: string
  embeddingModelId?: number
  providerId?: number
  vectorDimension: number
  chunkSize: number
  chunkOverlap: number
}

/* ========== 表格配置 ========== */

const columns = computed<TableColumn[]>(() => [
  { prop: 'name', label: t('common.name'), minWidth: 200, slot: 'name' },
  { prop: 'description', label: t('common.description'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'documentCount', label: t('knowledge.documentCount'), minWidth: 100, slot: 'documentCount' },
  { prop: 'chunkCount', label: t('knowledge.chunkCount'), minWidth: 100, slot: 'chunkCount' },
  { prop: 'enabled', label: t('common.status'), minWidth: 90, slot: 'enabled' },
  { prop: 'createdAt', label: t('common.createTime'), minWidth: 150 }
])

/* ========== 搜索配置 ========== */

const searchFieldsConfig = computed(() => [
  {
    key: 'name',
    label: t('common.name'),
    description: t('knowledge.knowledgeNamePlaceholder'),
    inputType: 'text' as const,
    placeholder: t('knowledge.knowledgeNamePlaceholder')
  },
  {
    key: 'enabled',
    label: t('common.status'),
    description: t('common.status'),
    inputType: 'select' as const,
    options: [
      { label: t('common.enabled'), value: 1 },
      { label: t('common.disabled'), value: 0 }
    ]
  }
])

/* ========== 响应式状态 ========== */

const listPageRef = ref()
const dialogRef = ref()
const dialogVisible = ref(false)
const searchConditions = ref<SearchCondition[]>([])
const allKnowledge = ref<KnowledgeBaseResponse[]>([])

const embeddingProviders = ref<ProviderResponse[]>([])
const embeddingModels = ref<ModelConfigInfo[]>([])
const embeddingModelsLoading = ref(false)
const selectedModelDimension = ref<number | null>(null)

const showDeleteConfirm = ref(false)
const deleteTarget = ref<{ id: number; name: string } | null>(null)

/* ========== 统计配置 ========== */

const statsData = ref({ total: 0, enabled: 0, documents: 0 })
const statsConfig = computed<ListStat[]>(() => [
  { key: 'total', label: t('knowledge.stats.total'), value: statsData.value.total || '-' },
  { key: 'enabled', label: t('knowledge.stats.enabled'), value: statsData.value.enabled || '-', class: 'enabled' },
  { key: 'documents', label: t('knowledge.stats.documents'), value: statsData.value.documents || '-', class: 'documents' }
])

/* ========== 表单默认数据 ========== */

const defaultFormData: KnowledgeFormData = {
  name: '',
  description: '',
  embeddingModel: '',
  embeddingModelId: undefined,
  providerId: undefined,
  vectorDimension: 0,
  chunkSize: 0,
  chunkOverlap: 0
}

/* ========== 表单验证规则 ========== */

const formRules = {
  name: [
    { required: true, message: () => t('knowledge.form.nameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: () => t('knowledge.form.nameLength'), trigger: 'blur' }
  ],
  embeddingModel: [
    { required: false, message: () => t('knowledge.form.embeddingModelRequired'), trigger: 'change' }
  ],
  vectorDimension: [
    { required: true, message: () => t('knowledge.form.vectorDimensionRequired'), trigger: 'blur' }
  ],
  chunkSize: [
    { required: true, message: () => t('knowledge.form.chunkSizeRequired'), trigger: 'blur' }
  ]
}

/* ========== API 调用 ========== */

const fetchKnowledgeList = async (params: {
  page: number
  size: number
  conditions?: SearchCondition[]
}) => {
  const list = await knowledgeApi.getKnowledgeList()
  const listArray = Array.isArray(list) ? list : []

  let filtered = [...listArray]
  if (params.conditions && params.conditions.length > 0) {
    params.conditions.forEach(condition => {
      if (condition.field === 'name' && condition.value) {
        filtered = filtered.filter(kb =>
          kb.name.toLowerCase().includes((condition.value as string).toLowerCase())
        )
      } else if (condition.field === 'enabled' && condition.value !== undefined) {
        filtered = filtered.filter(kb => kb.enabled === condition.value)
      }
    })
  }

  allKnowledge.value = listArray
  updateStats(listArray)

  const start = (params.page - 1) * params.size
  const end = start + params.size

  return {
    records: filtered.slice(start, end),
    total: filtered.length
  }
}

const updateStats = (list: KnowledgeBaseResponse[]) => {
  if (!Array.isArray(list)) list = []
  statsData.value = {
    total: list.length,
    enabled: list.filter(kb => kb.enabled === 1).length,
    documents: list.reduce((sum, kb) => sum + (kb.documentCount || 0), 0)
  }
}

/* ========== 嵌入模型供应商/模型加载 ========== */

const loadEmbeddingProviders = async () => {
  try {
    const result = await providerApi.getProviderList({ enabled: 1, pageSize: 100 })
    const allProviders = (result as any).list || result.records || []
    embeddingProviders.value = allProviders as ProviderResponse[]
  } catch {
    embeddingProviders.value = []
  }
}

const onEmbeddingProviderChange = async (providerId: number, formData: KnowledgeFormData) => {
  embeddingModels.value = []
  selectedModelDimension.value = null
  formData.embeddingModelId = undefined
  formData.embeddingModel = ''

  if (!providerId) return

  embeddingModelsLoading.value = true
  try {
    const models = await providerApi.getProviderModels(providerId, { category: 1, enabled: 1 })
    embeddingModels.value = models
    if (models.length === 1) {
      onEmbeddingModelChange(models[0].id, formData)
    }
  } catch {
    embeddingModels.value = []
  } finally {
    embeddingModelsLoading.value = false
  }
}

const onEmbeddingModelChange = (modelId: number, formData: KnowledgeFormData) => {
  const model = embeddingModels.value.find(m => m.id === modelId)
  if (model?.extraParams?.dimension) {
    selectedModelDimension.value = model.extraParams.dimension
    formData.vectorDimension = model.extraParams.dimension
  } else {
    selectedModelDimension.value = null
  }
  if (model) {
    formData.embeddingModelId = modelId
    formData.embeddingModel = model.modelName
  }
}

/* ========== 事件处理 ========== */

const handleAdd = () => {
  loadEmbeddingProviders()
  embeddingModels.value = []
  selectedModelDimension.value = null
  dialogRef.value?.open()
}

const handleEdit = async (row: Record<string, any>) => {
  await loadEmbeddingProviders()

  const hasModel = !!row.embeddingModelId

  const formData: KnowledgeFormData = {
    id: row.id,
    name: row.name,
    description: row.description || '',
    embeddingModel: row.embeddingModel,
    embeddingModelId: row.embeddingModelId || undefined,
    providerId: undefined,
    vectorDimension: hasModel ? row.vectorDimension : 0,
    chunkSize: hasModel ? row.chunkSize : 0,
    chunkOverlap: hasModel ? row.chunkOverlap : 0
  }

  if (hasModel) {
    try {
      for (const p of embeddingProviders.value) {
        const models = await providerApi.getProviderModels(p.id, { category: 1 })
        const found = models.find(m => m.id === row.embeddingModelId)
        if (found) {
          formData.providerId = p.id
          embeddingModels.value = models
          selectedModelDimension.value = found.extraParams?.dimension ?? null
          break
        }
      }
    } catch { /* 回填失败不影响编辑 */ }
  }

  dialogRef.value?.open(formData)
}

const handleDelete = (row: { id: number; name: string }) => {
  deleteTarget.value = row
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!deleteTarget.value) return
  try {
    await knowledgeApi.deleteKnowledge(deleteTarget.value.id)
    ElMessage.success(t('common.deleteSuccess'))
    listPageRef.value?.refresh()
  } finally {
    showDeleteConfirm.value = false
    deleteTarget.value = null
  }
}

const cancelDelete = () => {
  showDeleteConfirm.value = false
  deleteTarget.value = null
}

const handleViewDocuments = (row: KnowledgeBaseResponse) => {
  router.push(`/knowledge/${row.id}/documents`)
}

const handleAdvancedSearch = (conditions: SearchCondition[]) => {
  searchConditions.value = [...conditions]
  listPageRef.value?.refresh()
}

const handleSubmit = async (data: Record<string, any>, mode: string) => {
  try {
    if (mode === 'add') {
      const requestData: KnowledgeCreateRequest = {
        name: data.name,
        description: data.description || undefined,
        embeddingModel: data.embeddingModel || undefined,
        embeddingModelId: data.embeddingModelId || undefined,
        vectorDimension: data.vectorDimension,
        chunkSize: data.chunkSize,
        chunkOverlap: data.chunkOverlap
      }
      await knowledgeApi.createKnowledge(requestData)
      ElMessage.success(t('common.createSuccess'))
    } else {
      await knowledgeApi.updateKnowledge(data.id!, {
        name: data.name,
        description: data.description || undefined,
        embeddingModel: data.embeddingModel || undefined,
        embeddingModelId: data.embeddingModelId || undefined,
        vectorDimension: data.vectorDimension,
        chunkSize: data.chunkSize,
        chunkOverlap: data.chunkOverlap
      })
      ElMessage.success(t('common.updateSuccess'))
    }

    dialogRef.value?.submitSuccess()
    listPageRef.value?.refresh()
  } catch (error: any) {
    dialogRef.value?.submitFail()
  }
}
</script>

<style scoped>
.eify-btn-primary-gradient {
  background: var(--eify-gradient-primary);
  border: none;
  box-shadow: 0 2px 8px rgba(99, 102, 241, 0.3);
  transition: var(--eify-transition-base);
}

.eify-btn-primary-gradient:hover {
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.4);
  transform: translateY(-1px);
}

.table-action-buttons {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-1);
  flex-wrap: nowrap;
}

.table-action-buttons .el-button {
  height: 28px;
  padding: 0 10px;
  flex-shrink: 0;
}

.table-action-buttons .el-button .el-icon {
  font-size: 13px;
  margin-right: 2px;
}

.name-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

.kb-icon {
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--eify-primary);
  flex-shrink: 0;
}

.name-info {
  flex: 1;
  min-width: 0;
}

.name-text {
  font-weight: 500;
  color: var(--eify-text-primary);
  margin-bottom: 2px;
}

.model-text {
  color: var(--eify-text-tertiary);
}

.provider-model-badge {
  color: var(--eify-success);
}

.global-model-text {
  color: var(--eify-text-tertiary);
  cursor: help;
}

.global-model-tag {
  cursor: help;
}

.status-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  position: relative;
}

.status-dot.enabled {
  background: var(--eify-success);
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
}

.status-dot:not(.enabled) {
  background: var(--eify-text-tertiary);
}

.status-dot.enabled::before {
  content: '';
  position: absolute;
  inset: -2px;
  border-radius: 50%;
  border: 1px solid var(--eify-success);
  opacity: 0.5;
  animation: status-pulse 2s ease-in-out infinite;
}

@keyframes status-pulse {
  0%, 100% { transform: scale(1); opacity: 0.5; }
  50% { transform: scale(1.5); opacity: 0; }
}

.form-hint {
  color: var(--eify-text-tertiary);
  margin-top: 4px;
}

/* 卡片视图 */
.kb-card {
  background: var(--eify-bg-base);
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-card-shadow);
  overflow: hidden;
  transition: var(--eify-transition-base);
  border: 1px solid var(--eify-border-subtle);
}

.kb-card:hover {
  box-shadow: var(--eify-shadow-lg);
  transform: translateY(-2px);
  border-color: rgba(99, 102, 241, 0.2);
}

.kb-card.disabled {
  opacity: 0.7;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: var(--eify-spacing-3);
  padding: var(--eify-spacing-4);
  border-bottom: 1px solid var(--eify-border-subtle);
  background: linear-gradient(180deg, var(--eify-bg-surface) 0%, var(--eify-bg-base) 100%);
  min-height: 64px;
}

.card-avatar-placeholder {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.1) 0%, rgba(139, 92, 246, 0.1) 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--eify-primary);
  flex-shrink: 0;
}

.card-title {
  flex: 1;
}

.card-title h3 {
  font-weight: 600;
  color: var(--eify-text-primary);
  margin: 0 0 var(--eify-spacing-1) 0;
}

.card-status {
  flex-shrink: 0;
}

.status-indicator {
  display: flex;
  align-items: center;
  justify-content: center;
}

.indicator-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  position: relative;
}

.status-indicator.enabled .indicator-dot {
  background: var(--eify-success);
  box-shadow: 0 0 12px rgba(34, 197, 94, 0.6);
}

.status-indicator:not(.enabled) .indicator-dot {
  background: var(--eify-text-tertiary);
}

.status-indicator.enabled .indicator-dot::before {
  content: '';
  position: absolute;
  inset: -3px;
  border-radius: 50%;
  border: 2px solid var(--eify-success);
  opacity: 0.3;
  animation: indicator-pulse 2s ease-in-out infinite;
}

@keyframes indicator-pulse {
  0%, 100% { transform: scale(1); opacity: 0.3; }
  50% { transform: scale(1.6); opacity: 0; }
}

.card-body {
  padding: var(--eify-spacing-4);
}

.card-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-1) 0;
}

.info-label {
  color: var(--eify-text-tertiary);
  flex-shrink: 0;
}

.info-value {
  color: var(--eify-text-secondary);
  text-align: right;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 200px;
}

.card-stats-row {
  display: flex;
  justify-content: space-around;
  padding: var(--eify-spacing-3) 0;
  margin-top: var(--eify-spacing-2);
  border-top: 1px solid var(--eify-border-subtle);
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-value {
  font-weight: 600;
  color: var(--eify-primary);
}

.stat-label {
  color: var(--eify-text-tertiary);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--eify-spacing-3) var(--eify-spacing-4);
  border-top: 1px solid var(--eify-border-subtle);
  background: var(--eify-bg-surface);
  min-height: 48px;
}

.action-buttons {
  display: flex;
  gap: var(--eify-spacing-2);
}

.action-buttons .el-button {
  height: 26px;
  padding: 0 10px;
}

.action-buttons .el-button .el-icon {
  font-size: 14px;
}
</style>
