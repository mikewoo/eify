<template>
  <EifyListPage
    ref="listPageRef"
    :title="t('provider.title')"
    :description="t('provider.description')"
    :search-placeholder="t('provider.searchPlaceholder')"
    :search-fields="searchFieldsConfig"
    :table-columns="columns"
    :fetch-data="fetchProviders"
    :show-pagination="true"
    :default-page-size="10"
    :page-sizes="[10, 20, 50, 100]"
    :action-width="320"
    :show-view-toggle="true"
    :card-page-size="8"
    :show-stats="true"
    :stats="statsConfig"
    @search="handleAdvancedSearch"
  >
    <!-- 操作按钮 -->
    <template #actions>
      <el-button type="primary" class="eify-btn-primary-gradient" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        {{ t('provider.addProvider') }}
      </el-button>
    </template>

    <!-- 表格列插槽 -->
    <template #table-type="{ row }">
      <div class="type-cell">
        <div class="type-icon" :class="`type-${row.type.toLowerCase()}`">
          <component :is="getTypeIcon(row.type)" />
        </div>
        <el-tag :type="getTypeTagType(row.type)" effect="plain">
          {{ getTypeLabel(row.type) }}
        </el-tag>
      </div>
    </template>

    <template #table-enabled="{ row }">
      <div class="status-cell">
        <span class="status-dot" :class="{ enabled: row.enabled === 1 }"></span>
        <span class="status-text">{{ row.enabled === 1 ? t('common.enabled') : t('common.disabled') }}</span>
      </div>
    </template>

    <template #table-health="{ row }">
      <el-tag v-if="row.health" :type="getHealthTagType(row.health.status)" effect="plain">
        {{ getHealthStatusLabel(row.health.status) }}
        <span v-if="row.health.latencyMs !== null" class="latency-text">
          {{ row.health.latencyMs }}ms
        </span>
      </el-tag>
      <span v-else class="text-placeholder">-</span>
    </template>

    <template #table-modelCount="{ row }">
      <span class="model-count">{{ getModelCount(row) }}</span>
    </template>

    <template #table-actions="{ row }">
      <div class="table-action-buttons">
        <el-button size="small" type="success" @click="handleTestConnection(row)" :loading="testingId === row.id">
          <el-icon><Connection /></el-icon>
          {{ t('provider.testConnection') }}
        </el-button>
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
        class="provider-card"
        :class="{ disabled: item.enabled === 0 }"
      >
        <div class="card-header">
          <div class="card-type-icon" :class="`type-${item.type.toLowerCase()}`">
            <component :is="getTypeIcon(item.type)" />
          </div>
          <div class="card-title">
            <h3>{{ item.name }}</h3>
            <el-tag :type="getTypeTagType(item.type)" size="small" effect="plain">
              {{ getTypeLabel(item.type) }}
            </el-tag>
          </div>
          <div class="card-status">
            <span class="status-indicator" :class="{ enabled: item.enabled === 1 }">
              <span class="indicator-dot"></span>
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="card-info-row">
            <span class="info-label">Base URL</span>
            <el-tooltip
              :content="item.baseUrl"
              placement="top"
              :show-after="300"
              effect="light"
              popper-class="eify-url-tooltip"
            >
              <span class="info-value url-tooltip-trigger">{{ item.baseUrl }}</span>
            </el-tooltip>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('provider.healthStatus') }}</span>
            <div class="info-value">
              <el-tag v-if="item.health" :type="getHealthTagType(item.health.status)" size="small" effect="plain">
                {{ getHealthStatusLabel(item.health.status) }}
                <span v-if="item.health.latencyMs !== null" class="latency-text">
                  {{ item.health.latencyMs }}ms
                </span>
              </el-tag>
              <span v-else class="text-placeholder">-</span>
            </div>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('provider.modelCount') }}</span>
            <span class="info-value model-count">{{ getModelCount(item) }}</span>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('common.createTime') }}</span>
            <span class="info-value">{{ item.createdAt }}</span>
          </div>
        </div>

        <div class="card-footer">
          <el-button size="small" type="success" @click="handleTestConnection(item)" :loading="testingId === item.id">
            <el-icon><Connection /></el-icon>
            {{ t('provider.testConnection') }}
          </el-button>
          <div class="action-buttons">
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
      addTitle: t('provider.addProvider'),
      editTitle: t('provider.editProvider'),
      submitText: t('common.confirm'),
      width: '600px',
      labelWidth: '100px'
    }"
    @submit="handleSubmit"
  >
    <template #form="{ data }">
      <el-form-item :label="t('provider.providerName')" prop="name">
        <el-input
          v-model="data.name"
          :placeholder="t('provider.providerNamePlaceholder')"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="t('provider.providerType')" prop="type">
        <el-select
          v-model="data.type"
          :placeholder="t('provider.providerTypePlaceholder')"
          style="width: 100%"
        >
          <el-option
            v-for="item in providerTypes"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>

      <el-form-item label="Base URL" prop="baseUrl">
        <el-input
          v-model="data.baseUrl"
          :placeholder="t('provider.baseUrlPlaceholder')"
          maxlength="500"
        />
      </el-form-item>

      <el-form-item label="API Key" prop="apiKey">
        <el-input
          v-model="data.apiKey"
          type="password"
          :placeholder="t('provider.apiKeyPlaceholder')"
          show-password
          maxlength="200"
        />
      </el-form-item>

      <!-- 同步模型（编辑模式 / 新增后强制同步） -->
      <el-divider v-if="formProviderId" />
      <el-form-item v-if="formProviderId" label=" ">
        <div class="sync-models-section">
          <div class="sync-header">
            <el-button
              type="success"
              :loading="syncingId === formProviderId"
              @click="handleSyncModels(formProviderId!)"
            >
              <el-icon class="sync-btn-icon"><Connection /></el-icon>{{ t('provider.syncModels') }}
            </el-button>
            <el-button
              type="primary"
              @click="handleAddModel"
            >
              <el-icon class="sync-btn-icon"><Plus /></el-icon>{{ t('provider.addModelTitle') }}
            </el-button>
            <span class="sync-hint" v-if="!syncResult && existingModelNames.length === 0">{{ t('provider.noModelsHint') }}</span>
            <span class="sync-hint existing" v-else-if="!syncResult && existingModelNames.length > 0">{{ t('provider.existingModels', { count: existingModelNames.length }) }}</span>
          </div>

          <!-- 已有模型（编辑进入时） -->
          <div v-if="!syncResult && existingModelNames.length > 0" class="sync-result existing">
            <div class="sync-result-icon existing-icon">
              <el-icon><CircleCheck /></el-icon>
            </div>
            <div class="sync-result-info">
              <div class="sync-result-title">{{ t('provider.existingModels', { count: existingModelNames.length }) }}</div>
              <div class="sync-result-model-list">
                <div class="model-list-label">{{ t('provider.modelNameList') }}</div>
                <div class="model-list-text">{{ existingModelNames.join('\n') }}</div>
              </div>
            </div>
          </div>

          <!-- 同步结果（同步操作后） -->
          <div v-if="syncResult && syncingId !== formProviderId" class="sync-result" :class="{ success: syncResult.success, failed: !syncResult.success }">
            <template v-if="syncResult.success">
              <div class="sync-result-icon success-icon">
                <el-icon><CircleCheck /></el-icon>
              </div>
              <div class="sync-result-info">
                <div class="sync-result-title">{{ t('provider.syncModelsSuccess', { count: syncResult.modelCount || 0, added: syncResult.modelCount || 0 }) }}</div>
                <div class="sync-result-model-list" v-if="syncResult.modelNames && syncResult.modelNames.length > 0">
                  <div class="model-list-label">{{ t('provider.modelNameList') }}</div>
                  <div class="model-list-text">{{ syncResult.modelNames.join('\n') }}</div>
                </div>
              </div>
            </template>
            <template v-else>
              <div class="sync-result-icon error-icon">
                <el-icon><CircleClose /></el-icon>
              </div>
              <div class="sync-result-info">
                <div class="sync-result-title error-text">{{ syncResult.errorMessage }}</div>
              </div>
            </template>
          </div>
        </div>
      </el-form-item>

      <el-form-item :label="t('common.status')" prop="enabled">
        <el-radio-group v-model="data.enabled">
          <el-radio :value="1">{{ t('common.enabled') }}</el-radio>
          <el-radio :value="0">{{ t('common.disabled') }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </template>
  </EifyFormDialog>

  <!-- 手动添加模型对话框 -->
  <EifyFormDialog
    ref="modelDialogRef"
    v-model="modelDialogVisible"
    :rules="modelFormRules"
    :default-data="defaultModelFormData"
    :dialog-props="{
      addTitle: t('provider.addModelTitle'),
      submitText: t('common.confirm'),
      width: '480px',
      labelWidth: '110px'
    }"
    @submit="handleModelSubmit"
  >
    <template #form="{ data }">
      <el-form-item :label="t('provider.modelNameLabel')" prop="modelId">
        <el-input
          v-model="data.modelId"
          :placeholder="t('provider.modelNamePlaceholder')"
          maxlength="200"
        />
      </el-form-item>

      <el-form-item :label="t('provider.displayNameLabel')" prop="displayName">
        <el-input
          v-model="data.displayName"
          :placeholder="t('provider.displayNamePlaceholder')"
          maxlength="200"
        />
      </el-form-item>

      <el-form-item :label="t('provider.modelCategoryLabel')" prop="category">
        <el-select
          v-model="data.category"
          :placeholder="t('provider.modelCategoryPlaceholder')"
          style="width: 100%"
        >
          <el-option label="Chat" :value="0" />
          <el-option label="Embedding" :value="1" />
          <el-option label="Rerank" :value="2" />
          <el-option label="Multimodal" :value="3" />
        </el-select>
      </el-form-item>

      <el-form-item :label="t('provider.modelDimensionLabel')" prop="dimension">
        <el-input-number
          v-model="data.dimension"
          :min="1"
          :max="8192"
          :step="1"
          style="width: 100%"
        />
        <div class="form-hint">{{ t('provider.modelDimensionHint') }}</div>
      </el-form-item>
    </template>
  </EifyFormDialog>

  <ConfirmDialog
    :show="showDeleteConfirm"
    :title="t('common.confirmDeleteTitle')"
    :message="deleteTarget ? t('provider.confirmDelete', { name: deleteTarget.name }) : ''"
    type="danger"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  Plus,
  Connection,
  Edit,
  Delete,
  ChatLineRound,
  ChatDotRound,
  Histogram,
  Monitor,
  Platform,
  CircleCheck,
  CircleClose
} from '@element-plus/icons-vue'
import EifyListPage from '@/components/EifyListPage.vue'
import EifyFormDialog from '@/components/EifyFormDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  providerApi,
  type ProviderResponse,
  type ProviderListParams,
  type ConnectionTestResult
} from '@/api/provider'
import type { TableColumn } from '@/types/eify-table'
import type { SearchCondition } from '@/types/eify-search'
import type { ListStat } from '@/types/api'

const { t } = useI18n()

/* ========== 类型定义 ========== */

interface ProviderFormData {
  id?: number
  name: string
  type: string
  baseUrl: string
  apiKey: string
  enabled: number
}

/* ========== 表格配置 ========== */

const columns = computed<TableColumn[]>(() => [
  { prop: 'name', label: t('provider.providerName'), minWidth: 140 },
  { prop: 'type', label: t('provider.providerType'), minWidth: 160, slot: 'type' },
  { prop: 'baseUrl', label: t('provider.apiBase'), minWidth: 200, showOverflowTooltip: true },
  { prop: 'enabled', label: t('common.status'), minWidth: 90, slot: 'enabled' },
  { prop: 'health', label: t('provider.healthStatus'), minWidth: 140, slot: 'health' },
  { prop: 'modelCount', label: t('provider.modelCount'), minWidth: 110, slot: 'modelCount' },
  { prop: 'createdAt', label: t('common.createTime'), minWidth: 150 }
])

const providerTypes = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'Anthropic Claude', value: 'ANTHROPIC' },
  { label: 'Ollama', value: 'OLLAMA' },
  { label: 'OpenAI Compatible', value: 'OPENAI_COMPATIBLE' }
]

/* ========== 搜索配置 ========== */

const searchFieldsConfig = computed(() => [
  {
    key: 'name',
    label: t('provider.providerName'),
    description: t('provider.providerName'),
    inputType: 'text' as const,
    placeholder: t('provider.providerNamePlaceholder')
  },
  {
    key: 'type',
    label: t('provider.providerType'),
    description: t('provider.providerType'),
    inputType: 'select' as const,
    options: providerTypes
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
const testingId = ref<number | null>(null)
const syncingId = ref<number | null>(null)
const syncResult = ref<ConnectionTestResult | null>(null)
const existingModelNames = ref<string[]>([])
const formProviderId = ref<number | null>(null)
const searchConditions = ref<SearchCondition[]>([])

// 删除确认
const showDeleteConfirm = ref(false)
const deleteTarget = ref<{ id: number; name: string } | null>(null)

/* ========== 统计配置 ========== */

const statsData = ref({ total: 0, enabled: 0, disabled: 0 })
const statsConfig = computed<ListStat[]>(() => [
  { key: 'total', label: t('provider.stats.total'), value: statsData.value.total || '-' },
  { key: 'enabled', label: t('provider.stats.enabled'), value: statsData.value.enabled || '-', class: 'enabled' },
  { key: 'disabled', label: t('provider.stats.disabled'), value: statsData.value.disabled || '-', class: 'disabled' }
])

/* ========== 模型表单 ========== */

interface ModelFormData {
  modelId: string
  displayName: string
  category: number
  dimension: number | undefined
}

const modelDialogRef = ref()
const modelDialogVisible = ref(false)

const defaultModelFormData: ModelFormData = {
  modelId: '',
  displayName: '',
  category: 1,
  dimension: undefined
}

const modelFormRules = {
  modelId: [
    { required: true, message: () => t('provider.form.nameRequired'), trigger: 'blur' }
  ],
  displayName: [
    { required: true, message: () => t('provider.form.nameRequired'), trigger: 'blur' }
  ],
  category: [
    { required: true, message: () => t('provider.form.typeRequired'), trigger: 'change' }
  ]
}

/* ========== 表单默认数据 ========== */

const defaultFormData: ProviderFormData = {
  name: '',
  type: 'OPENAI',
  baseUrl: '',
  apiKey: '',
  enabled: 1
}

/* ========== 表单验证规则 ========== */

const formRules = {
  name: [
    { required: true, message: () => t('provider.form.nameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: () => t('provider.form.nameLength'), trigger: 'blur' }
  ],
  type: [
    { required: true, message: () => t('provider.form.typeRequired'), trigger: 'change' }
  ],
  baseUrl: [
    { required: true, message: () => t('provider.form.baseUrlRequired'), trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: () => t('provider.form.baseUrlInvalid'),
      trigger: 'blur'
    }
  ],
  apiKey: [
    { required: true, message: () => t('provider.form.apiKeyRequired'), trigger: 'blur' },
    { min: 5, message: () => t('provider.form.apiKeyMinLength'), trigger: 'blur' }
  ]
}

/* ========== API 调用 ========== */

/**
 * 获取供应商列表
 */
const fetchProviders = async (params: {
  page: number
  size: number
  conditions?: SearchCondition[]
}) => {
  const queryParams: ProviderListParams = {
    page: params.page,
    pageSize: params.size
  }

  // 处理搜索条件
  if (params.conditions && params.conditions.length > 0) {
    params.conditions.forEach(condition => {
      if (condition.field === 'name') {
        queryParams.name = condition.value as string
      } else if (condition.field === 'type') {
        queryParams.type = condition.value as any
      } else if (condition.field === 'enabled') {
        queryParams.enabled = condition.value as number
      }
    })
  }

  const result = await providerApi.getProviderList(queryParams)
  const records = (result as any).list || result.records || []

  updateStats(records)

  return {
    records,
    total: result.total
  }
}

/**
 * 创建供应商
 */
const createProvider = async (data: ProviderFormData) => {
  const requestData = {
    name: data.name,
    type: data.type as any,
    baseUrl: data.baseUrl,
    authConfig: { api_key: data.apiKey },
    enabled: data.enabled
  }
  return await providerApi.createProvider(requestData)
}

/**
 * 更新供应商
 */
const updateProvider = async (data: ProviderFormData) => {
  if (!data.id) return

  const requestData: any = {
    name: data.name,
    type: data.type,
    baseUrl: data.baseUrl,
    authConfig: { api_key: data.apiKey },
    enabled: data.enabled
  }

  return await providerApi.updateProvider(data.id, requestData)
}

/**
 * 删除供应商
 */
const deleteProvider = async (id: number) => {
  await providerApi.deleteProvider(id)
  ElMessage.success(t('common.deleteSuccess'))
  listPageRef.value?.refresh()
}

/**
 * 测试连通性
 */
const testConnection = async (id: number): Promise<ConnectionTestResult> => {
  return await providerApi.testConnection(id)
}

/* ========== 工具方法 ========== */

const getTypeLabel = (type: string) => {
  const found = providerTypes.find(t => t.value === type)
  return found?.label || type
}

const getTypeTagType = (type: string) => {
  const typeMap: Record<string, string> = {
    OPENAI: 'primary',
    ANTHROPIC: 'success',
    OLLAMA: 'info',
    OPENAI_COMPATIBLE: 'warning'
  }
  return typeMap[type] || 'info'
}

const getTypeIcon = (type: string) => {
  const iconMap: Record<string, any> = {
    OPENAI: ChatLineRound,
    ANTHROPIC: ChatDotRound,
    OLLAMA: Monitor,
    OPENAI_COMPATIBLE: Histogram
  }
  return iconMap[type] || Platform
}

const getHealthTagType = (status: string) => {
  const typeMap: Record<string, string> = {
    UP: 'success',
    DOWN: 'danger',
    DEGRADED: 'warning',
    UNKNOWN: 'info'
  }
  return typeMap[status] || 'info'
}

const getHealthStatusLabel = (status: string) => {
  const labelMap: Record<string, string> = {
    UP: t('provider.health.normal'),
    DOWN: t('provider.health.abnormal'),
    DEGRADED: t('provider.health.degraded'),
    UNKNOWN: t('provider.health.unknown')
  }
  return labelMap[status] || status
}

const getModelCount = (row: { modelConfigs?: any[] | null }) => {
  if (!row.modelConfigs || row.modelConfigs.length === 0) {
    return 0
  }
  return row.modelConfigs.length
}

const updateStats = (list: ProviderResponse[]) => {
  if (!Array.isArray(list)) list = []
  statsData.value = {
    total: list.length,
    enabled: list.filter(p => p.enabled === 1).length,
    disabled: list.filter(p => p.enabled === 0).length
  }
}

/* ========== 事件处理 ========== */

const handleAdd = () => {
  formProviderId.value = null
  syncResult.value = null
  existingModelNames.value = []
  dialogRef.value?.open()
}

const handleEdit = async (row: { id: number; name: string; type: string; baseUrl: string; enabled: number; authConfig: any }) => {
  const authConfig = row.authConfig as any
  const apiKey = authConfig?.apiKey || authConfig?.api_key || ''

  const formData: ProviderFormData = {
    id: row.id,
    name: row.name,
    type: row.type,
    baseUrl: row.baseUrl,
    apiKey: apiKey,
    enabled: row.enabled
  }
  formProviderId.value = row.id
  syncResult.value = null
  existingModelNames.value = []
  dialogRef.value?.open(formData)

  // 检查已有模型
  try {
    const models = await providerApi.getProviderModels(row.id)
    if (models.length > 0) {
      existingModelNames.value = models.map(m => m.modelName)
    }
  } catch { /* ignore */ }
}

const handleDelete = (row: Record<string, any>) => {
  deleteTarget.value = row
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!deleteTarget.value) return
  try {
    await deleteProvider(deleteTarget.value.id)
  } finally {
    showDeleteConfirm.value = false
    deleteTarget.value = null
  }
}

const cancelDelete = () => {
  showDeleteConfirm.value = false
  deleteTarget.value = null
}

const handleTestConnection = async (row: { id: number }) => {
  testingId.value = row.id
  try {
    const result = await testConnection(row.id)

    if (result.success) {
      ElMessage.success({
        message: t('provider.connectionTestSuccess', { latency: result.latencyMs, count: result.modelCount }),
        duration: 3000
      })
    } else {
      ElMessage.error({
        message: t('provider.connectionTestFailed', { error: result.errorMessage || t('provider.health.unknown') }),
        duration: 5000
      })
    }
  } catch (error: any) {
    ElMessage.error({
      message: error.message || t('provider.connectionTestError'),
      duration: 5000
    })
  } finally {
    testingId.value = null
  }
}

const handleAddModel = () => {
  modelDialogRef.value?.open()
}

const handleModelSubmit = async (data: ModelFormData) => {
  if (!formProviderId.value) return

  const requestData: Record<string, any> = {
    modelId: data.modelId,
    displayName: data.displayName,
    category: data.category
  }

  if (data.dimension) {
    requestData.extraParams = { dimension: data.dimension }
  }

  try {
    await providerApi.createProviderModel(formProviderId.value, requestData)
    ElMessage.success(t('provider.addModelSuccess'))
    modelDialogRef.value?.submitSuccess()

    // 刷新已有模型列表
    try {
      const models = await providerApi.getProviderModels(formProviderId.value)
      existingModelNames.value = models.map(m => m.modelName)
    } catch { /* ignore */ }
    listPageRef.value?.refresh()
  } catch {
    modelDialogRef.value?.submitFail()
  }
}

const handleSyncModels = async (providerId: number) => {
  syncingId.value = providerId
  syncResult.value = null
  try {
    const result = await testConnection(providerId)
    syncResult.value = result

    if (result.success) {
      listPageRef.value?.refresh()
    } else {
      ElMessage.error({
        message: t('provider.syncModelsFailed', { error: result.errorMessage || t('provider.health.unknown') }),
        duration: 5000
      })
    }
  } catch (error: any) {
    syncResult.value = null
    ElMessage.error({
      message: error.message || t('provider.syncModelsError'),
      duration: 5000
    })
  } finally {
    syncingId.value = null
  }
}

const handleAdvancedSearch = (conditions: SearchCondition[]) => {
  searchConditions.value = [...conditions]
  listPageRef.value?.refresh()
}

const handleSubmit = async (data: any, mode: string) => {
  try {
    if (mode === 'add') {
      // 1. 创建供应商
      const created = await createProvider(data)
      ElMessage.success(t('common.createSuccess'))
      formProviderId.value = created.id

      // 2. 强制同步模型
      syncingId.value = created.id
      syncResult.value = null
      try {
        const result = await testConnection(created.id)
        syncResult.value = result
        if (result.success) {
          // 同步成功：保持弹窗打开，展示结果卡片，用户手动关闭
          listPageRef.value?.refresh()
        } else {
          ElMessage.error({
            message: t('provider.syncModelsFailed', { error: result.errorMessage || t('provider.health.unknown') }),
            duration: 6000
          })
        }
      } catch (syncError: any) {
        syncResult.value = null
        ElMessage.error({
          message: syncError.message || t('provider.syncModelsError'),
          duration: 6000
        })
      } finally {
        syncingId.value = null
        dialogRef.value?.submitFail()
      }
    } else {
      await updateProvider(data)
      ElMessage.success(t('common.updateSuccess'))
      dialogRef.value?.submitSuccess()
      listPageRef.value?.refresh()
    }
  } catch (error: any) {
    dialogRef.value?.submitFail()
  }
}
</script>

<style scoped>
/* 主按钮渐变样式 */
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

/* 操作按钮链接文字样式 */
.table-action-buttons {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-1);
}

.table-action-buttons .el-button {
  font-size: 12px;
  height: 28px;
  padding: 0 10px;
}

.table-action-buttons .el-button .el-icon {
  font-size: 13px;
  margin-right: 2px;
}

/* 表格视图增强 */
.type-cell {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-2);
}

.type-icon {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--eify-radius-sm);
}

.type-icon.type-openai {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(139, 92, 246, 0.15) 100%);
  color: var(--eify-primary);
}

.type-icon.type-anthropic {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.15) 0%, rgba(16, 185, 129, 0.15) 100%);
  color: var(--eify-success);
}

.type-icon.type-ollama {
  background: linear-gradient(135deg, rgba(148, 163, 184, 0.15) 0%, rgba(100, 116, 139, 0.15) 100%);
  color: var(--eify-text-secondary);
}

.type-icon.type-openai_compatible {
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.15) 0%, rgba(245, 158, 11, 0.15) 100%);
  color: var(--eify-warning);
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
  0%, 100% {
    transform: scale(1);
    opacity: 0.5;
  }
  50% {
    transform: scale(1.5);
    opacity: 0;
  }
}

.status-text {
}

.latency-text {
  margin-left: 4px;
  opacity: 0.8;
}

.model-count {
  font-weight: 500;
}

.text-placeholder {
  color: var(--eify-text-tertiary);
}

/* 表单样式调整 */
:deep(.el-form-item__label) {
  font-weight: 500;
}

/* ========== 卡片视图 ========== */
.provider-card {
  background: var(--eify-bg-base);
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-card-shadow);
  overflow: hidden;
  transition: var(--eify-transition-base);
  border: 1px solid var(--eify-border-subtle);
}

.provider-card:hover {
  box-shadow: var(--eify-shadow-lg);
  transform: translateY(-2px);
  border-color: rgba(99, 102, 241, 0.2);
}

.provider-card.disabled {
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

.card-type-icon {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--eify-radius-md);
  flex-shrink: 0;
}

.card-type-icon.type-openai {
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(139, 92, 246, 0.15) 100%);
  color: var(--eify-primary);
}

.card-type-icon.type-anthropic {
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.15) 0%, rgba(16, 185, 129, 0.15) 100%);
  color: var(--eify-success);
}

.card-type-icon.type-ollama {
  background: linear-gradient(135deg, rgba(148, 163, 184, 0.15) 0%, rgba(100, 116, 139, 0.15) 100%);
  color: var(--eify-text-secondary);
}

.card-type-icon.type-openai_compatible {
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.15) 0%, rgba(245, 158, 11, 0.15) 100%);
  color: var(--eify-warning);
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
  0%, 100% {
    transform: scale(1);
    opacity: 0.3;
  }
  50% {
    transform: scale(1.6);
    opacity: 0;
  }
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

.info-value.url-tooltip-trigger {
  cursor: default;
  border-bottom: 1px dashed var(--eify-border-default);
  transition: var(--eify-transition-base);
  position: relative;
}

.info-value.url-tooltip-trigger:hover {
  color: var(--eify-primary);
  border-bottom-color: var(--eify-primary);
  background: linear-gradient(180deg,
    rgba(99, 102, 241, 0.05) 0%,
    transparent 100%
  );
  padding: 0 4px;
  margin: 0 -4px;
  border-radius: 4px;
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
  font-size: 12px;
}

.action-buttons .el-button .el-icon {
  font-size: 14px;
}

/* ========== 同步模型 ========== */
.sync-btn-icon {
  margin-right: 4px;
}

.sync-models-section {
  width: 100%;
}

.sync-header {
  display: flex;
  align-items: center;
  gap: var(--eify-spacing-3);
}

.sync-hint {
  color: var(--eify-text-tertiary);
}

.sync-hint.existing {
  color: var(--eify-success);
  font-weight: 500;
}

.sync-result {
  display: flex;
  gap: var(--eify-spacing-3);
  margin-top: var(--eify-spacing-4);
  padding: var(--eify-spacing-4);
  border-radius: var(--eify-radius-md);
  background: var(--eify-bg-surface);
  border: 1px solid var(--eify-border-subtle);
}

.sync-result.success {
  border-color: rgba(34, 197, 94, 0.2);
  background: linear-gradient(135deg, rgba(34, 197, 94, 0.04) 0%, rgba(34, 197, 94, 0.02) 100%);
}

.sync-result.failed {
  border-color: rgba(239, 68, 68, 0.2);
  background: linear-gradient(135deg, rgba(239, 68, 68, 0.04) 0%, rgba(239, 68, 68, 0.02) 100%);
}

.sync-result.existing {
  border-color: rgba(99, 102, 241, 0.15);
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.03) 0%, rgba(139, 92, 246, 0.02) 100%);
}

.sync-result-icon {
  flex-shrink: 0;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.success-icon {
  color: var(--eify-success);
  background: rgba(34, 197, 94, 0.1);
}

.error-icon {
  color: var(--eify-error);
  background: rgba(239, 68, 68, 0.1);
}

.existing-icon {
  color: var(--eify-primary);
  background: rgba(99, 102, 241, 0.1);
}

.sync-result-info {
  flex: 1;
  min-width: 0;
}

.sync-result-title {
  font-weight: 500;
  color: var(--eify-text-primary);
  margin-bottom: 6px;
}

.sync-result-title.error-text {
  color: var(--eify-error);
}

.sync-result-model-list {
  margin-top: 8px;
}

.model-list-label {
  color: var(--eify-text-secondary);
  margin-bottom: 6px;
}

.model-list-text {
  font-family: 'SF Mono', 'Cascadia Code', Consolas, monospace;
  color: var(--eify-text-primary);
  background: var(--eify-bg-base);
  padding: 10px 14px;
  border-radius: var(--eify-radius-sm);
  white-space: pre-line;
  line-height: 1.7;
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--eify-border-subtle);
}

.form-hint {
  color: var(--eify-text-tertiary);
  margin-top: 4px;
}

/* ========== URL Tooltip 样式 ========== */
:deep(.eify-url-tooltip) {
  background: var(--eify-bg-base) !important;
  border: 1px solid var(--eify-border-default) !important;
  border-radius: var(--eify-radius-md) !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1),
              0 0 0 1px rgba(99, 102, 241, 0.05) !important;
  padding: var(--eify-spacing-3) var(--eify-spacing-4) !important;
  max-width: 400px !important;
}

:deep(.eify-url-tooltip .el-tooltip__content) {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  font-size: 12px;
  color: var(--eify-text-primary);
  line-height: 1.5;
  word-break: break-all;
  background: linear-gradient(135deg,
    rgba(99, 102, 241, 0.02) 0%,
    rgba(139, 92, 246, 0.01) 100%
  );
  padding: var(--eify-spacing-2) var(--eify-spacing-3);
  border-radius: var(--eify-radius-sm);
  border: 1px solid rgba(99, 102, 241, 0.1);
}
</style>
