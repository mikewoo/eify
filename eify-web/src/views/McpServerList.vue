<template>
  <EifyListPage
    ref="listPageRef"
    :title="t('mcp.title')"
    :description="t('mcp.description')"
    :search-placeholder="t('mcp.searchPlaceholder')"
    :search-fields="searchFieldsConfig"
    :table-columns="columns"
    :fetch-data="fetchServers"
    :show-pagination="true"
    :default-page-size="10"
    :page-sizes="[10, 20, 50, 100]"
    :action-width="340"
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
        {{ t('mcp.addServer') }}
      </el-button>
    </template>

    <!-- 表格：名称列 -->
    <template #table-name="{ row }">
      <div class="name-cell">
        <span class="server-name">{{ row.name }}</span>
        <span v-if="row.toolCount && row.toolCount > 0" class="tool-count-badge">
          {{ t('mcp.toolCountBadge', { count: row.toolCount }) }}
        </span>
      </div>
    </template>

    <!-- 表格：端点列 -->
    <template #table-endpoint="{ row }">
      <el-tooltip
        :content="row.endpoint"
        placement="top"
        :show-after="300"
        effect="light"
        popper-class="eify-url-tooltip"
      >
        <span class="endpoint-text">{{ row.endpoint }}</span>
      </el-tooltip>
    </template>

    <!-- 表格：启用状态列 -->
    <template #table-enabled="{ row }">
      <div class="status-cell">
        <span class="status-dot" :class="{ enabled: row.enabled === 1 }"></span>
        <span class="status-text">{{ row.enabled === 1 ? t('common.enabled') : t('common.disabled') }}</span>
      </div>
    </template>

    <!-- 表格：工具列 -->
    <template #table-toolCount="{ row }">
      <span class="tool-count">{{ getToolCount(row) }}</span>
    </template>

    <!-- 表格：操作列 -->
    <template #table-actions="{ row }">
      <div class="table-action-buttons">
        <el-button size="small" type="success" @click="handleTestConnection(row)" :loading="testingId === row.id">
          <el-icon><Connection /></el-icon>
          {{ t('mcp.testButton') }}
        </el-button>
        <el-button size="small" @click="handleView(row)">
          <el-icon><View /></el-icon>
          {{ t('mcp.detailButton') }}
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

    <!-- 卡片视图 -->
    <template #card="{ items }">
      <div
        v-for="item in items"
        :key="item.id"
        class="mcp-card"
        :class="{ disabled: item.enabled === 0 }"
      >
        <div class="card-header">
          <div class="card-icon-wrapper">
            <el-icon :size="18"><Connection /></el-icon>
          </div>
          <div class="card-title">
            <h3>{{ item.name }}</h3>
            <span class="card-endpoint">{{ item.endpoint }}</span>
          </div>
          <div class="card-status">
            <span class="status-indicator" :class="{ enabled: item.enabled === 1 }">
              <span class="indicator-dot"></span>
            </span>
          </div>
        </div>

        <div class="card-body">
          <div class="card-info-row">
            <span class="info-label">{{ t('mcp.endpoint') }}</span>
            <el-tooltip
              :content="item.endpoint"
              placement="top"
              :show-after="300"
              effect="light"
              popper-class="eify-url-tooltip"
            >
              <span class="info-value url-tooltip-trigger">{{ item.endpoint }}</span>
            </el-tooltip>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('mcp.toolCountLabel') }}</span>
            <span class="info-value">{{ getToolCount(item) }}</span>
          </div>
          <div class="card-info-row">
            <span class="info-label">{{ t('common.createTime') }}</span>
            <span class="info-value">{{ item.createdAt }}</span>
          </div>
        </div>

        <div class="card-footer">
          <el-button size="small" type="success" @click="handleTestConnection(item)" :loading="testingId === item.id">
            <el-icon><Connection /></el-icon>
            {{ t('mcp.testButton') }}
          </el-button>
          <div class="action-buttons">
            <el-button size="small" @click="handleView(item)">
              <el-icon><View /></el-icon>
              {{ t('mcp.detailButton') }}
            </el-button>
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
      addTitle: t('mcp.addServer'),
      editTitle: t('mcp.editServer'),
      submitText: t('common.confirm'),
      width: '560px',
      labelWidth: '100px'
    }"
    @submit="handleSubmit"
  >
    <template #form="{ data }">
      <el-form-item :label="t('common.name')" prop="name">
        <el-input
          v-model="data.name"
          :placeholder="t('mcp.form.nameRequired')"
          maxlength="100"
          show-word-limit
        />
      </el-form-item>

      <el-form-item :label="t('mcp.endpoint')" prop="endpoint">
        <el-input
          v-model="data.endpoint"
          :placeholder="t('mcp.endpointPlaceholder')"
          maxlength="500"
        />
      </el-form-item>

      <el-form-item :label="t('common.status')" prop="enabled">
        <el-radio-group v-model="data.enabled">
          <el-radio :value="1">{{ t('common.enabled') }}</el-radio>
          <el-radio :value="0">{{ t('common.disabled') }}</el-radio>
        </el-radio-group>
      </el-form-item>
    </template>
  </EifyFormDialog>

  <!-- 详情对话框 -->
  <el-dialog
    v-model="detailVisible"
    :title="detailServer ? t('mcp.detailTitle', { name: detailServer.name }) : t('mcp.serverDetail')"
    width="960px"
    destroy-on-close
    draggable
    class="eify-detail-dialog"
    @closed="handleDetailClosed"
  >
    <div v-if="detailServer" class="detail-content">
      <div class="detail-meta">
        <div class="detail-meta-item">
          <span class="detail-label">{{ t('mcp.endpoint') }}</span>
          <span class="detail-value">{{ detailServer.endpoint }}</span>
        </div>
        <div class="detail-meta-item">
          <span class="detail-label">{{ t('common.status') }}</span>
          <el-tag :type="detailServer.enabled === 1 ? 'success' : 'info'" size="small" effect="plain">
            {{ detailServer.enabled === 1 ? t('common.enabled') : t('common.disabled') }}
          </el-tag>
        </div>
        <div class="detail-meta-item">
          <span class="detail-label">{{ t('mcp.toolCountLabel') }}</span>
          <span class="detail-value">{{ detailServer.toolCount || 0 }}</span>
        </div>
      </div>

      <el-tabs v-model="detailActiveTab" class="detail-tabs">
        <!-- Tab 1: 工具列表 -->
        <el-tab-pane :label="t('mcp.toolList')" name="tools">
          <div v-if="detailServer.tools && detailServer.tools.length > 0" class="tool-list">
            <div v-for="tool in detailServer.tools" :key="tool.id" class="tool-item">
              <div class="tool-header">
                <span class="tool-name">{{ tool.name }}</span>
              </div>
              <div v-if="tool.description" class="tool-description">{{ tool.description }}</div>
              <div v-if="tool.inputSchema" class="tool-schema-section">
                <el-collapse>
                  <el-collapse-item :title="t('mcp.paramSchema')">
                    <pre class="schema-json">{{ formatSchema(tool.inputSchema) }}</pre>
                  </el-collapse-item>
                </el-collapse>
              </div>
            </div>
          </div>
          <div v-else class="empty-tools">
            <el-empty :description="t('mcp.noToolsYet')" :image-size="80" />
          </div>
        </el-tab-pane>

        <!-- Tab 2: 调试 -->
        <el-tab-pane :label="t('mcp.debug')" name="debug" :disabled="!detailServer.tools || detailServer.tools.length === 0">
          <div class="debug-panel" v-if="detailServer.tools && detailServer.tools.length > 0">
            <!-- 左侧：工具列表 -->
            <div class="debug-tool-list">
              <div class="debug-tool-list-title">{{ t('mcp.toolListTitle') }}</div>
              <div
                v-for="tool in detailServer.tools"
                :key="tool.id"
                class="debug-tool-item"
                :class="{ selected: selectedTool?.id === tool.id }"
                @click="selectTool(tool)"
              >
                <span class="debug-tool-name">{{ tool.name }}</span>
              </div>
            </div>

            <!-- 右侧：调试面板 -->
            <div class="debug-main" v-if="selectedTool">
              <!-- 工具描述 -->
              <div class="debug-tool-desc">
                <span class="debug-label">{{ t('common.description') }}</span>
                <p>{{ selectedTool.description || t('mcp.noDescription') }}</p>
              </div>

              <!-- 动态参数表单 -->
              <div class="debug-params">
                <span class="debug-label">{{ t('mcp.params') }}</span>
                <el-form
                  ref="debugFormRef"
                  :model="debugParams"
                  label-width="auto"
                  label-position="top"
                  size="small"
                  class="debug-form"
                >
                  <el-form-item
                    v-for="prop in getSchemaProperties(selectedTool.inputSchema)"
                    :key="prop.name"
                    :label="prop.name"
                    :required="prop.required"
                  >
                    <!-- string -->
                    <el-input
                      v-if="prop.type === 'string'"
                      v-model="debugParams[prop.name]"
                      :placeholder="prop.description || t('mcp.paramPlaceholder', { name: prop.name })"
                      clearable
                    />
                    <!-- number / integer -->
                    <el-input-number
                      v-else-if="prop.type === 'number' || prop.type === 'integer'"
                      v-model="debugParams[prop.name]"
                      :placeholder="prop.description || t('mcp.paramPlaceholder', { name: prop.name })"
                      controls-position="right"
                      style="width: 100%"
                    />
                    <!-- boolean -->
                    <el-switch
                      v-else-if="prop.type === 'boolean'"
                      v-model="debugParams[prop.name]"
                    />
                    <!-- array / object → JSON textarea -->
                    <el-input
                      v-else-if="prop.type === 'array' || prop.type === 'object'"
                      v-model="debugParams[prop.name]"
                      type="textarea"
                      :rows="3"
                      :placeholder="t('mcp.jsonParamPlaceholder', { type: prop.type })"
                    />
                    <!-- fallback -->
                    <el-input
                      v-else
                      v-model="debugParams[prop.name]"
                      :placeholder="prop.description || t('mcp.paramPlaceholder', { name: prop.name })"
                      clearable
                    />
                    <div v-if="prop.description" class="debug-param-hint">{{ prop.description }}</div>
                  </el-form-item>
                </el-form>
                <div v-if="getSchemaProperties(selectedTool.inputSchema).length === 0" class="debug-no-params">
                  {{ t('mcp.noParams') }}
                </div>
              </div>

              <!-- 调用按钮 -->
              <el-button
                type="primary"
                class="debug-call-btn"
                :loading="debugLoading"
                :disabled="debugLoading"
                @click="executeDebug"
              >
                <el-icon><CaretRight /></el-icon>
                {{ t('mcp.callTool') }}
              </el-button>

              <!-- 结果展示 -->
              <div v-if="debugResult !== null || debugError" class="debug-result">
                <div class="debug-result-header">
                  <span class="debug-label">{{ t('mcp.result') }}</span>
                  <span v-if="debugElapsedMs !== null" class="debug-elapsed">
                    {{ t('mcp.elapsed') }} {{ debugElapsedMs }}ms
                  </span>
                </div>
                <pre :class="['debug-result-content', debugError ? 'is-error' : 'is-success']">{{
                  debugError || debugResult
                }}</pre>
              </div>

              <!-- 最近调用记录 -->
              <div v-if="recentCalls.length > 0" class="debug-recent">
                <span class="debug-label">{{ t('mcp.recentCalls') }}</span>
                <div
                  v-for="(call, index) in recentCalls"
                  :key="index"
                  class="recent-call-item"
                >
                  <span class="recent-call-tool">{{ call.toolName }}</span>
                  <span class="recent-call-ms">{{ call.elapsedMs }}ms</span>
                  <span :class="['recent-call-status', call.isError ? 'error' : 'success']">
                    {{ call.isError ? t('mcp.callFailed') : t('mcp.callSuccess') }}
                  </span>
                </div>
              </div>
            </div>

            <!-- 未选择工具 -->
            <div v-else class="debug-main debug-empty">
              <el-empty :description="t('mcp.selectToolHint')" :image-size="60" />
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-dialog>

  <ConfirmDialog
    :show="showDeleteConfirm"
    :title="t('common.confirmDeleteTitle')"
    :message="deleteTarget ? t('mcp.confirmDelete', { name: deleteTarget.name }) : ''"
    type="danger"
    @confirm="confirmDelete"
    @cancel="cancelDelete"
  />
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Plus, Connection, Edit, Delete, View, CaretRight } from '@element-plus/icons-vue'
import EifyListPage from '@/components/EifyListPage.vue'
import EifyFormDialog from '@/components/EifyFormDialog.vue'
import ConfirmDialog from '@/components/ConfirmDialog.vue'
import {
  mcpApi,
  type McpServerResponse,
  type McpServerListParams,
  type ConnectionTestResult,
  type DebugToolResponse,
  type McpToolResponse
} from '@/api/mcp'
import type { TableColumn } from '@/components/EifyTable.vue'
import type { SearchCondition } from '@/components/EifySearch.vue'
import type { ListStat } from '@/types/api'

/* ========== 类型定义 ========== */

interface McpServerFormData {
  id?: number
  name: string
  endpoint: string
  enabled: number
}

/* ========== i18n ========== */

const { t } = useI18n()

/* ========== 表格配置 ========== */

const columns = computed<TableColumn[]>(() => [
  { prop: 'name', label: t('common.name'), minWidth: 160, slot: 'name' },
  { prop: 'endpoint', label: t('mcp.endpoint'), minWidth: 240, slot: 'endpoint' },
  { prop: 'enabled', label: t('common.status'), minWidth: 90, slot: 'enabled' },
  { prop: 'toolCount', label: t('mcp.toolCountLabel'), minWidth: 100, slot: 'toolCount' },
  { prop: 'createdAt', label: t('common.createTime'), minWidth: 150 }
])

/* ========== 搜索配置 ========== */

const searchFieldsConfig = computed(() => [
  {
    key: 'name',
    label: t('common.name'),
    description: t('common.name'),
    inputType: 'text' as const,
    placeholder: t('mcp.serverNamePlaceholder')
  },
  {
    key: 'endpoint',
    label: t('mcp.endpoint'),
    description: t('mcp.endpoint'),
    inputType: 'text' as const,
    placeholder: t('mcp.endpointPlaceholder')
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
const detailVisible = ref(false)
const detailServer = ref<McpServerResponse | null>(null)
const detailActiveTab = ref('tools')
const testingId = ref<number | null>(null)
const searchConditions = ref<SearchCondition[]>([])

// 删除确认
const showDeleteConfirm = ref(false)
const deleteTarget = ref<{ id: number; name: string } | null>(null)

// 调试面板状态
const selectedTool = ref<McpToolResponse | null>(null)
const debugParams = ref<Record<string, any>>({})
const debugLoading = ref(false)
const debugResult = ref<string | null>(null)
const debugError = ref<string | null>(null)
const debugElapsedMs = ref<number | null>(null)
const recentCalls = ref<Array<{ toolName: string; elapsedMs: number; isError: boolean }>>([])

/* ========== 统计配置 ========== */

const statsData = ref({ total: 0, enabled: 0, disabled: 0 })
const statsConfig = computed<ListStat[]>(() => [
  { key: 'total', label: t('mcp.stats.total'), value: statsData.value.total || '-' },
  { key: 'enabled', label: t('common.enabled'), value: statsData.value.enabled || '-', class: 'enabled' },
  { key: 'disabled', label: t('common.disabled'), value: statsData.value.disabled || '-', class: 'disabled' }
])

/* ========== 表单默认数据 ========== */

const defaultFormData: McpServerFormData = {
  name: '',
  endpoint: '',
  enabled: 1
}

/* ========== 表单验证 ========== */

const formRules = {
  name: [
    { required: true, message: () => t('mcp.form.nameRequired'), trigger: 'blur' },
    { min: 2, max: 100, message: () => t('mcp.form.nameLength'), trigger: 'blur' }
  ],
  endpoint: [
    { required: true, message: () => t('mcp.form.endpointRequired'), trigger: 'blur' },
    {
      pattern: /^https?:\/\/.+/,
      message: () => t('mcp.form.endpointInvalid'),
      trigger: 'blur'
    }
  ]
}

/* ========== API 调用 ========== */

const fetchServers = async (params: {
  page: number
  size: number
  conditions?: SearchCondition[]
}) => {
  const queryParams: McpServerListParams = {
    page: params.page,
    pageSize: params.size
  }

  if (params.conditions && params.conditions.length > 0) {
    params.conditions.forEach(condition => {
      if (condition.field === 'name') {
        queryParams.name = condition.value as string
      } else if (condition.field === 'endpoint') {
        queryParams.endpoint = condition.value as string
      } else if (condition.field === 'enabled') {
        queryParams.enabled = condition.value as number
      }
    })
  }

  const result = await mcpApi.getList(queryParams)
  const records = (result as any).list || result.records || []

  updateStats(records)

  return {
    records,
    total: result.total
  }
}

const createServer = async (data: McpServerFormData) => {
  return await mcpApi.create({
    name: data.name,
    endpoint: data.endpoint,
    enabled: data.enabled
  })
}

const updateServer = async (data: McpServerFormData) => {
  if (!data.id) return
  return await mcpApi.update(data.id, {
    name: data.name,
    endpoint: data.endpoint,
    enabled: data.enabled
  })
}

const deleteServer = async (id: number) => {
  await mcpApi.delete(id)
  ElMessage.success(t('common.deleteSuccess'))
  listPageRef.value?.refresh()
}

const testConnection = async (id: number): Promise<ConnectionTestResult> => {
  return await mcpApi.testConnection(id)
}

/* ========== 工具方法 ========== */

const getToolCount = (row: McpServerResponse) => {
  return row.toolCount || 0
}

const updateStats = (list: McpServerResponse[]) => {
  if (!Array.isArray(list)) list = []
  statsData.value = {
    total: list.length,
    enabled: list.filter(s => s.enabled === 1).length,
    disabled: list.filter(s => s.enabled === 0).length
  }
}

const formatSchema = (schema: Record<string, any> | null) => {
  if (!schema) return ''
  return JSON.stringify(schema, null, 2)
}

/* ========== 事件处理 ========== */

/* ========== 调试面板方法 ========== */

interface SchemaProperty {
  name: string
  type: string
  description: string | null
  required: boolean
}

const getSchemaProperties = (schema: Record<string, any> | null): SchemaProperty[] => {
  if (!schema || !schema.properties) return []
  const required: string[] = schema.required || []
  return Object.entries(schema.properties).map(([name, def]: [string, any]) => ({
    name,
    type: def.type || 'string',
    description: def.description || null,
    required: required.includes(name)
  }))
}

const selectTool = (tool: McpToolResponse) => {
  selectedTool.value = tool
  debugResult.value = null
  debugError.value = null
  debugElapsedMs.value = null

  // 根据 inputSchema 初始化参数默认值
  const params: Record<string, any> = {}
  for (const prop of getSchemaProperties(tool.inputSchema)) {
    if (prop.type === 'boolean') {
      params[prop.name] = false
    } else if (prop.type === 'number' || prop.type === 'integer') {
      params[prop.name] = undefined
    } else if (prop.type === 'array') {
      params[prop.name] = '[]'
    } else if (prop.type === 'object') {
      params[prop.name] = '{}'
    } else {
      params[prop.name] = ''
    }
  }
  debugParams.value = params
}

const executeDebug = async () => {
  if (!detailServer.value || !selectedTool.value) return
  const toolName = selectedTool.value.name

  // 构建参数：解析 JSON 字符串类型的参数
  const args: Record<string, any> = {}
  for (const prop of getSchemaProperties(selectedTool.value.inputSchema)) {
    const raw = debugParams.value[prop.name]
    if (prop.type === 'array' || prop.type === 'object') {
      try {
        args[prop.name] = raw ? JSON.parse(raw) : (prop.type === 'array' ? [] : {})
      } catch {
        debugError.value = t('mcp.jsonParamError', { name: prop.name, raw })
        debugResult.value = null
        debugElapsedMs.value = null
        return
      }
    } else if (prop.type === 'number' || prop.type === 'integer') {
      args[prop.name] = raw !== undefined && raw !== '' ? Number(raw) : undefined
    } else {
      args[prop.name] = raw || ''
    }
  }

  debugLoading.value = true
  debugResult.value = null
  debugError.value = null
  debugElapsedMs.value = null

  try {
    const resp: DebugToolResponse = await mcpApi.debugTool(detailServer.value.id, {
      toolName,
      arguments: args
    })

    debugElapsedMs.value = resp.elapsedMs
    try {
      const parsed = JSON.parse(resp.result)
      debugResult.value = JSON.stringify(parsed, null, 2)
      // 检查 success 字段
      if (parsed.success === false) {
        debugError.value = JSON.stringify(parsed, null, 2)
        debugResult.value = null
      }
    } catch {
      debugResult.value = resp.result
    }

    // 记录最近调用
    const isError = debugError.value !== null
    recentCalls.value.unshift({ toolName, elapsedMs: resp.elapsedMs, isError })
    if (recentCalls.value.length > 5) {
      recentCalls.value = recentCalls.value.slice(0, 5)
    }
  } catch (err: any) {
    debugError.value = err.message || t('mcp.callFailed')
  } finally {
    debugLoading.value = false
  }
}

const handleDetailClosed = () => {
  detailActiveTab.value = 'tools'
  selectedTool.value = null
  debugParams.value = {}
  debugResult.value = null
  debugError.value = null
  debugElapsedMs.value = null
  recentCalls.value = []
}

const handleAdd = () => {
  dialogRef.value?.open()
}

const handleEdit = (row: { id: number; name: string; endpoint: string; enabled: number }) => {
  const formData: McpServerFormData = {
    id: row.id,
    name: row.name,
    endpoint: row.endpoint,
    enabled: row.enabled
  }
  dialogRef.value?.open(formData)
}

const handleView = async (row: { id: number }) => {
  try {
    const server = await mcpApi.getById(row.id)
    detailServer.value = server
    detailVisible.value = true
  } catch {
    // 错误已在拦截器中处理
  }
}

const handleDelete = (row: { id: number; name: string }) => {
  deleteTarget.value = row
  showDeleteConfirm.value = true
}

const confirmDelete = async () => {
  if (!deleteTarget.value) return
  try {
    await deleteServer(deleteTarget.value.id)
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
      const namesStr = result.toolNames && result.toolNames.length > 0 ? '：' + result.toolNames.join(', ') : ''
      const msg = t('mcp.connectionTestSuccess', { latency: result.latencyMs, count: result.toolCount, names: namesStr })
      ElMessage.success({
        message: msg,
        duration: 4000
      })
      // 刷新列表以更新工具数量
      listPageRef.value?.refresh()
    } else {
      ElMessage.error({
        message: t('mcp.connectionTestFailed', { error: result.errorMessage || t('error.unknown') }),
        duration: 5000
      })
    }
  } catch (error: any) {
    ElMessage.error({
      message: error.message || t('mcp.connectionTestError'),
      duration: 5000
    })
  } finally {
    testingId.value = null
  }
}

const handleAdvancedSearch = (conditions: SearchCondition[]) => {
  searchConditions.value = [...conditions]
  listPageRef.value?.refresh()
}

const handleSubmit = async (data: any, mode: string) => {
  try {
    if (mode === 'add') {
      await createServer(data)
      ElMessage.success(t('common.createSuccess'))
    } else {
      await updateServer(data)
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
/* ========== 主按钮渐变 ========== */
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

/* ========== 表格单元格 ========== */
.name-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.server-name {
  font-weight: 500;
  color: var(--eify-text-primary);
}

.tool-count-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.1);
  color: var(--eify-primary);
  white-space: nowrap;
}

.endpoint-text {
  color: var(--eify-text-secondary);
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  font-size: 13px;
  cursor: default;
  border-bottom: 1px dashed var(--eify-border-default);
}

.endpoint-text:hover {
  color: var(--eify-primary);
}

.status-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--eify-text-tertiary);
  position: relative;
}

.status-dot.enabled {
  background: var(--eify-success);
  box-shadow: 0 0 8px rgba(34, 197, 94, 0.5);
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

.status-text {
  font-size: 14px;
}

.tool-count {
  font-weight: 500;
}

/* ========== 操作按钮 ========== */
.table-action-buttons {
  display: flex;
  align-items: center;
  gap: 4px;
}

.table-action-buttons .el-button {
  font-size: 12px;
  height: 28px;
  padding: 0 8px;
}

.table-action-buttons .el-button .el-icon {
  font-size: 13px;
  margin-right: 2px;
}

/* ========== 表单样式 ========== */
:deep(.el-form-item__label) {
  font-weight: 500;
}

/* ========== 卡片视图 ========== */
.mcp-card {
  background: var(--eify-bg-base);
  border-radius: var(--eify-card-radius);
  box-shadow: var(--eify-card-shadow);
  overflow: hidden;
  transition: var(--eify-transition-base);
  border: 1px solid var(--eify-border-subtle);
}

.mcp-card:hover {
  box-shadow: var(--eify-shadow-lg);
  transform: translateY(-2px);
  border-color: rgba(99, 102, 241, 0.2);
}

.mcp-card.disabled {
  opacity: 0.7;
}

.card-header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px;
  border-bottom: 1px solid var(--eify-border-subtle);
  background: linear-gradient(180deg, var(--eify-bg-surface) 0%, var(--eify-bg-base) 100%);
  min-height: 64px;
}

.card-icon-wrapper {
  width: 38px;
  height: 38px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.12) 0%, rgba(139, 92, 246, 0.12) 100%);
  color: var(--eify-primary);
  flex-shrink: 0;
}

.card-title {
  flex: 1;
  min-width: 0;
}

.card-title h3 {
  font-size: 15px;
  font-weight: 600;
  color: var(--eify-text-primary);
  margin: 0 0 4px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-endpoint {
  font-size: 12px;
  color: var(--eify-text-tertiary);
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
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
  background: var(--eify-text-tertiary);
}

.status-indicator.enabled .indicator-dot {
  background: var(--eify-success);
  box-shadow: 0 0 12px rgba(34, 197, 94, 0.6);
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
  padding: 16px;
}

.card-info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 4px 0;
  font-size: 12px;
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
}

.info-value.url-tooltip-trigger:hover {
  color: var(--eify-primary);
  border-bottom-color: var(--eify-primary);
}

.card-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-top: 1px solid var(--eify-border-subtle);
  background: var(--eify-bg-surface);
  min-height: 48px;
}

.action-buttons {
  display: flex;
  gap: 8px;
}

.action-buttons .el-button {
  height: 26px;
  padding: 0 10px;
  font-size: 12px;
}

.action-buttons .el-button .el-icon {
  font-size: 14px;
}

/* ========== 详情对话框 ========== */
.detail-content {
  padding: 4px 0;
}

.detail-meta {
  display: flex;
  gap: 24px;
  padding: 16px;
  background: var(--eify-bg-surface);
  border-radius: 8px;
  margin-bottom: 16px;
}

.detail-meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail-label {
  font-size: 12px;
  color: var(--eify-text-tertiary);
}

.detail-value {
  font-size: 14px;
  color: var(--eify-text-primary);
  font-weight: 500;
}

.tool-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tool-item {
  padding: 12px 16px;
  background: var(--eify-bg-surface);
  border-radius: 8px;
  border: 1px solid var(--eify-border-subtle);
}

.tool-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.tool-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--eify-primary);
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
}

.tool-description {
  font-size: 13px;
  color: var(--eify-text-secondary);
  margin-bottom: 8px;
  line-height: 1.5;
}

.tool-schema-section {
  margin-top: 4px;
}

.schema-json {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  font-size: 12px;
  color: var(--eify-text-primary);
  background: var(--eify-bg-base);
  padding: 12px;
  border-radius: 6px;
  border: 1px solid var(--eify-border-subtle);
  overflow-x: auto;
  white-space: pre;
  max-height: 300px;
  overflow-y: auto;
}

.empty-tools {
  padding: 32px 0;
}

/* ========== 详情 Tabs ========== */
.detail-tabs {
  margin-top: 4px;
}

.detail-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

/* ========== 调试面板 ========== */
.debug-panel {
  display: flex;
  gap: 0;
  min-height: 400px;
  border: 1px solid var(--eify-border-subtle);
  border-radius: 8px;
  overflow: hidden;
}

/* 左侧工具列表 */
.debug-tool-list {
  width: 200px;
  flex-shrink: 0;
  background: var(--eify-bg-surface);
  border-right: 1px solid var(--eify-border-subtle);
  overflow-y: auto;
}

.debug-tool-list-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--eify-text-tertiary);
  padding: 12px 12px 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.debug-tool-item {
  padding: 8px 12px;
  cursor: pointer;
  font-size: 13px;
  color: var(--eify-text-secondary);
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  transition: background 0.15s, color 0.15s;
  border-left: 2px solid transparent;
}

.debug-tool-item:hover {
  background: rgba(99, 102, 241, 0.06);
  color: var(--eify-text-primary);
}

.debug-tool-item.selected {
  background: rgba(99, 102, 241, 0.1);
  color: var(--eify-primary);
  font-weight: 600;
  border-left-color: var(--eify-primary);
}

/* 右侧调试主面板 */
.debug-main {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.debug-empty {
  align-items: center;
  justify-content: center;
}

.debug-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--eify-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 4px;
  display: block;
}

.debug-tool-desc p {
  font-size: 13px;
  color: var(--eify-text-secondary);
  line-height: 1.6;
  margin: 4px 0 0;
}

/* 动态参数表单 */
.debug-params {
  background: var(--eify-bg-surface);
  border-radius: 8px;
  padding: 12px;
}

.debug-form {
  margin-top: 8px;
}

.debug-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

.debug-form :deep(.el-form-item__label) {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  font-size: 12px;
  color: var(--eify-text-primary);
}

.debug-param-hint {
  font-size: 11px;
  color: var(--eify-text-tertiary);
  margin-top: 2px;
  line-height: 1.4;
}

.debug-no-params {
  font-size: 13px;
  color: var(--eify-text-tertiary);
  padding: 8px 0;
  font-style: italic;
}

/* 调用按钮 */
.debug-call-btn {
  width: 100%;
  height: 36px;
  font-weight: 500;
}

/* 结果展示 */
.debug-result {
  background: #1e1e2e;
  border-radius: 8px;
  overflow: hidden;
}

.debug-result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: rgba(255, 255, 255, 0.04);
}

.debug-result-header .debug-label {
  color: rgba(255, 255, 255, 0.6);
  margin-bottom: 0;
}

.debug-elapsed {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
}

.debug-result-content {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  font-size: 12px;
  line-height: 1.5;
  padding: 12px;
  margin: 0;
  max-height: 240px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
}

.debug-result-content.is-success {
  color: #a6e3a1;
}

.debug-result-content.is-error {
  color: #f38ba8;
}

/* 最近调用 */
.debug-recent {
  margin-top: 4px;
}

.recent-call-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 10px;
  border-radius: 6px;
  transition: background 0.15s;
  font-size: 12px;
}

.recent-call-item:hover {
  background: var(--eify-bg-surface);
}

.recent-call-tool {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  color: var(--eify-text-primary);
  flex: 1;
}

.recent-call-ms {
  color: var(--eify-text-tertiary);
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  min-width: 50px;
}

.recent-call-status {
  font-weight: 500;
  min-width: 32px;
  text-align: right;
}

.recent-call-status.success {
  color: var(--eify-success);
}

.recent-call-status.error {
  color: var(--eify-error);
}

/* ========== URL Tooltip 样式 ========== */
:deep(.eify-url-tooltip) {
  background: var(--eify-bg-base) !important;
  border: 1px solid var(--eify-border-default) !important;
  border-radius: 8px !important;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1),
              0 0 0 1px rgba(99, 102, 241, 0.05) !important;
  padding: 8px 16px !important;
  max-width: 400px !important;
}

:deep(.eify-url-tooltip .el-tooltip__content) {
  font-family: 'SF Mono', 'Monaco', 'Inconsolata', monospace;
  font-size: 12px;
  color: var(--eify-text-primary);
  line-height: 1.5;
  word-break: break-all;
}
</style>
