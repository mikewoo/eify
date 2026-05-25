<template>
  <div class="workflow-edit">
    <!-- 顶部栏 -->
    <div class="edit-header">
      <div class="header-left">
        <el-button :icon="ArrowLeft" @click="handleBack">{{ t('common.back') }}</el-button>
        <el-input
          v-model="formName"
          class="header-name-input"
          :placeholder="t('workflow.workflowName')"
          maxlength="100"
          size="large"
        />
        <el-button :icon="Setting" circle @click="basicDialogVisible = true" />
      </div>
      <div class="header-right">
        <span class="header-hint text-xs" v-if="dirty">{{ t('workflow.unsavedHint') }}</span>
        <el-button @click="handleBack">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">{{ t('common.save') }}</el-button>
      </div>
    </div>

    <!-- 主画布区域 -->
    <div class="edit-canvas-wrapper">
      <NodePanel />

      <div class="canvas-container" @drop="onDrop" @dragover="onDragOver">
        <VueFlow
          ref="vueFlowRef"
          v-model:nodes="vfNodes"
          v-model:edges="vfEdges"
          :node-types="nodeTypes"
          :default-edge-options="{ type: 'smoothstep', animated: false, deletable: true }"
          fit-view-on-init
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :delete-key-code="['Backspace', 'Delete']"
          :connection-line-style="{ stroke: '#6366f1', strokeWidth: 2 }"
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @nodes-change="onNodesChange"
          @edges-change="onEdgesChange"
        >
          <Background :gap="20" pattern-color="#e2e8f0" />
          <Controls position="bottom-right" />
          <MiniMap position="bottom-left" :width="180" :height="120" />
        </VueFlow>
      </div>
    </div>

    <!-- 基本信息对话框 -->
    <el-dialog v-model="basicDialogVisible" :title="t('workflow.basicSettings')" width="560px" :close-on-click-modal="false">
      <el-form label-position="top">
        <el-form-item :label="t('common.status')">
          <el-select v-model="formStatus" style="width:100%">
            <el-option :label="t('workflow.statusDraft')" :value="0" />
            <el-option :label="t('workflow.statusPublished')" :value="1" />
            <el-option :label="t('common.disabled')" :value="2" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('workflow.workflowDesc')">
          <el-input v-model="formDesc" type="textarea" :rows="2" :placeholder="t('workflow.descPlaceholder')" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('workflow.variableDefinition')">
          <el-table :data="variables" border size="small" class="kv-table">
            <el-table-column :label="t('workflow.variableName')" width="140">
              <template #default="{ row }">
                <el-input v-model="row.key" :placeholder="t('workflow.variableName')" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.type')" width="110">
              <template #default="{ row }">
                <el-select v-model="row.type" size="small" style="width:100%">
                  <el-option label="string" value="string" />
                  <el-option label="number" value="number" />
                  <el-option label="boolean" value="boolean" />
                  <el-option label="object" value="object" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.varRequired')" width="60" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.required" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('workflow.defaultValue')" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.defaultVal" :placeholder="t('workflow.defaultValueOptional')" size="small" />
              </template>
            </el-table-column>
            <el-table-column :label="t('common.actions')" width="60" align="center">
              <template #default="{ $index }">
                <el-button type="danger" :icon="Delete" circle size="small" @click="variables.splice($index, 1)" />
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" :icon="Plus" style="margin-top:8px" @click="variables.push({ key: '', type: 'string', required: false, defaultVal: '' })">
            {{ t('workflow.addVariable') }}
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="basicDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- 节点配置抽屉 -->
    <el-drawer
      v-model="configDrawerVisible"
      :title="t('workflow.nodeConfig', { name: configNodeLabel })"
      size="520px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div class="config-drawer-body" v-if="configNode">
        <!-- start -->
        <template v-if="configNode.type === 'start'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.inputVariables')">
            <el-table :data="configForm.inputVariables || []" border size="small" class="kv-table">
              <el-table-column :label="t('workflow.variableName')" width="140">
                <template #default="{ row: v }">
                  <el-input v-model="v.key" :placeholder="t('workflow.variableName')" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.type')" width="110">
                <template #default="{ row: v }">
                  <el-select v-model="v.varType" size="small" style="width:100%">
                    <el-option label="string" value="string" />
                    <el-option label="number" value="number" />
                    <el-option label="boolean" value="boolean" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column :label="t('workflow.varRequired')" width="60" align="center">
                <template #default="{ row: v }">
                  <el-switch v-model="v.required" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('workflow.defaultValue')" min-width="120">
                <template #default="{ row: v }">
                  <el-input v-model="v.defaultVal" :placeholder="t('workflow.defaultValue')" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="60" align="center">
                <template #default="{ $index: vi }">
                  <el-button type="danger" :icon="Delete" circle size="small" @click="configForm.inputVariables.splice(vi, 1)" />
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" :icon="Plus" style="margin-top:8px" @click="configForm.inputVariables.push({ key: '', varType: 'string', required: false, defaultVal: '' })">
              {{ t('workflow.addItem') }}
            </el-button>
          </el-form-item>
        </template>

        <!-- end -->
        <template v-else-if="configNode.type === 'end'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.outputVariableName')">
            <el-input v-model="configForm.outputKey" :placeholder="t('workflow.outputVarPlaceholder')" />
          </el-form-item>
        </template>

        <!-- llm -->
        <template v-else-if="configNode.type === 'llm'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.supplier')">
            <el-select v-model="configForm.providerId" :placeholder="t('workflow.supplierPlaceholder')" style="width:100%" @change="onProviderChange">
              <el-option v-for="p in providerOptions" :key="p.id" :label="`${p.name} (${p.type})`" :value="p.id" />
            </el-select>
            <div class="form-tip text-xs" v-if="providerOptions.length === 0">{{ t('provider.unsyncedHint') }}</div>
          </el-form-item>
          <el-form-item :label="t('workflow.modelLabel')">
            <el-select v-model="configForm.model" :placeholder="t('workflow.modelPlaceholder')" style="width:100%" filterable>
              <el-option v-for="m in availableModels" :key="m.modelName" :label="m.displayName || m.modelName" :value="m.modelName" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workflow.temperatureLabel')">
            <el-slider v-model="configForm.temperature" :min="0" :max="2" :step="0.1" show-input style="width:100%" />
          </el-form-item>
          <el-form-item :label="t('workflow.maxTokensLabel')">
            <el-input-number v-model="configForm.maxTokens" :min="1" :max="128000" style="width:100%" />
          </el-form-item>
          <el-form-item :label="t('workflow.systemPromptLabel')">
            <el-input v-model="configForm.systemPrompt" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item :label="t('workflow.userPromptLabel')">
            <el-input v-model="configForm.userPrompt" type="textarea" :rows="4" />
          </el-form-item>
          <el-form-item :label="t('workflow.outputVariableName')">
            <el-input v-model="configForm.outputKey" :placeholder="t('workflow.llmOutputPlaceholder')" />
          </el-form-item>
        </template>

        <!-- api_call -->
        <template v-else-if="configNode.type === 'api_call'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.urlLabel')">
            <el-input v-model="configForm.url" :placeholder="t('workflow.urlPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('workflow.requestMethod')">
            <el-select v-model="configForm.method" style="width:100%">
              <el-option label="GET" value="GET" />
              <el-option label="POST" value="POST" />
              <el-option label="PUT" value="PUT" />
              <el-option label="DELETE" value="DELETE" />
            </el-select>
          </el-form-item>
          <el-form-item label="Headers">
            <el-table :data="configHeadersList" border size="small" class="kv-table">
              <el-table-column label="Key" width="160">
                <template #default="{ row: h }">
                  <el-input v-model="h.key" :placeholder="t('workflow.headerName')" size="small" />
                </template>
              </el-table-column>
              <el-table-column label="Value" min-width="180">
                <template #default="{ row: h }">
                  <el-input v-model="h.value" :placeholder="t('workflow.headerValue')" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="60" align="center">
                <template #default="{ $index: hi }">
                  <el-button type="danger" :icon="Delete" circle size="small" @click="configHeadersList.splice(hi, 1)" />
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" :icon="Plus" style="margin-top:8px" @click="configHeadersList.push({ key: '', value: '' })">{{ t('workflow.addItem') }}</el-button>
          </el-form-item>
          <el-form-item :label="t('workflow.bodyLabel')">
            <el-input v-model="configForm.body" type="textarea" :rows="4" :placeholder="t('workflow.bodyPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('workflow.outputVariableName')">
            <el-input v-model="configForm.outputKey" />
          </el-form-item>
          <el-form-item :label="t('workflow.timeoutSeconds')">
            <el-input-number v-model="configForm.timeoutSeconds" :min="1" :max="300" style="width:100%" />
          </el-form-item>
        </template>

        <!-- condition -->
        <template v-else-if="configNode.type === 'condition'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.conditionExpression')">
            <el-input v-model="configForm.expression" :placeholder="t('workflow.conditionPlaceholder')" />
            <div class="form-tip text-xs">{{ t('workflow.conditionExpressionHint') }}</div>
          </el-form-item>
        </template>

        <!-- code -->
        <template v-else-if="configNode.type === 'code'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.languageLabel')">
            <el-input v-model="configForm.language" :placeholder="t('workflow.codeLanguagePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('workflow.codeLabel')">
            <el-input v-model="configForm.code" type="textarea" :rows="10" class="code-textarea" />
          </el-form-item>
          <el-form-item :label="t('workflow.outputVariableName')">
            <el-input v-model="configForm.outputKey" :placeholder="t('workflow.codeOutputPlaceholder')" />
          </el-form-item>
        </template>

        <!-- tool_call -->
        <template v-else-if="configNode.type === 'tool_call'">
          <el-form-item :label="t('workflow.nodeName')">
            <el-input v-model="configForm._name" :placeholder="t('workflow.nodeNamePlaceholder')" maxlength="50" />
          </el-form-item>
          <el-form-item :label="t('workflow.mcpServerLabel')">
            <el-select v-model="configForm.serverId" :placeholder="t('workflow.mcpServerPlaceholder')" style="width:100%" @change="onServerChange">
              <el-option v-for="s in mcpServerOptions" :key="s.id" :label="`${s.name} (${t('workflow.toolCount', { count: s.toolCount || 0 })})`" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workflow.toolNameLabel')">
            <el-select v-model="configForm.toolName" :placeholder="t('workflow.toolPlaceholder')" style="width:100%" filterable :loading="loadingTools">
              <el-option v-for="t in availableTools" :key="t.name" :label="t.description ? `${t.name} - ${t.description}` : t.name" :value="t.name" />
            </el-select>
          </el-form-item>
          <el-form-item :label="t('workflow.argTemplate')">
            <el-table :data="configArgsList" border size="small" class="kv-table">
              <el-table-column :label="t('workflow.argName')" width="160">
                <template #default="{ row: a }">
                  <el-input v-model="a.key" :placeholder="t('workflow.argName')" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('workflow.argValue')" min-width="180">
                <template #default="{ row: a }">
                  <el-input v-model="a.value" :placeholder="t('workflow.argValuePlaceholder')" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.actions')" width="60" align="center">
                <template #default="{ $index: ai }">
                  <el-button type="danger" :icon="Delete" circle size="small" @click="configArgsList.splice(ai, 1)" />
                </template>
              </el-table-column>
            </el-table>
            <el-button size="small" :icon="Plus" style="margin-top:8px" @click="configArgsList.push({ key: '', value: '' })">{{ t('workflow.addItem') }}</el-button>
          </el-form-item>
          <el-form-item :label="t('workflow.outputVariableName')">
            <el-input v-model="configForm.outputKey" />
          </el-form-item>
        </template>
      </div>

      <template #footer>
        <el-button @click="configDrawerVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" plain @click="deleteSelectedNode">{{ t('workflow.deleteNode') }}</el-button>
        <el-button type="primary" @click="confirmConfig">{{ t('common.confirm') }}</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, markRaw } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Plus, Delete, Setting } from '@element-plus/icons-vue'
import { VueFlow, useVueFlow, type Node, type Edge, type Connection as VFConnection } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import { workflowApi, type NodeType } from '@/api/workflow'
import { providerApi, type ProviderResponse, type ModelConfigInfo } from '@/api/provider'
import { mcpApi, type McpServerResponse, type McpToolResponse } from '@/api/mcp'
import NodePanel from '@/components/workflow/NodePanel.vue'
import StartNode from '@/components/workflow/nodes/StartNode.vue'
import EndNode from '@/components/workflow/nodes/EndNode.vue'
import LlmNode from '@/components/workflow/nodes/LlmNode.vue'
import ApiNode from '@/components/workflow/nodes/ApiNode.vue'
import ConditionNode from '@/components/workflow/nodes/ConditionNode.vue'
import CodeNode from '@/components/workflow/nodes/CodeNode.vue'
import ToolNode from '@/components/workflow/nodes/ToolNode.vue'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

/* ========== Vue Flow ========== */

const vueFlowRef = ref()
const vfNodes = ref<Node[]>([])
const vfEdges = ref<Edge[]>([])

const nodeTypes = {
  start: markRaw(StartNode),
  end: markRaw(EndNode),
  llm: markRaw(LlmNode),
  api_call: markRaw(ApiNode),
  condition: markRaw(ConditionNode),
  code: markRaw(CodeNode),
  tool_call: markRaw(ToolNode),
}

const { screenToFlowCoordinate, addNodes, addEdges, removeNodes } = useVueFlow()

/* ========== 模式判断 ========== */

const isEdit = computed(() => !!route.params.id)
const editId = computed(() => Number(route.params.id) || 0)
const dirty = ref(false)
const nextNodeNum = ref(1)

/* ========== 基本信息 ========== */

const basicDialogVisible = ref(false)
const formName = ref('')
const formDesc = ref('')
const formStatus = ref<number>(0)
const variables = ref<{ key: string; type: string; required: boolean; defaultVal: string }[]>([])

/* ========== 节点配置抽屉 ========== */

const configDrawerVisible = ref(false)
const configNodeId = ref<string | null>(null)

interface EditableNodeData {
  name: string
  type: string
  nodeKey: string
  config: Record<string, any>
}

const configNode = computed(() => {
  if (!configNodeId.value) return null
  const vfNode = vfNodes.value.find(n => n.id === configNodeId.value)
  if (!vfNode) return null
  const d = vfNode.data as EditableNodeData
  return { ...d, id: vfNode.id, position: vfNode.position }
})

const configNodeLabel = computed(() => {
  if (!configNode.value) return ''
  return configNode.value.name || configNode.value.nodeKey || ''
})

const configForm = ref<Record<string, any>>({})
const configHeadersList = ref<{ key: string; value: string }[]>([])
const configArgsList = ref<{ key: string; value: string }[]>([])

/* ========== Provider & MCP 选项 ========== */

const providerOptions = ref<ProviderResponse[]>([])
const providerModelsMap = ref<Map<number, ModelConfigInfo[]>>(new Map())
const mcpServerOptions = ref<McpServerResponse[]>([])
const availableModels = ref<ModelConfigInfo[]>([])
const availableTools = ref<McpToolResponse[]>([])
const loadingTools = ref(false)

/* ========== 保存状态 ========== */

const saving = ref(false)

/* ========== 节点配置函数 ========== */

const defaultConfigs: Record<string, Record<string, any>> = {
  start: { inputVariables: [] },
  end: { outputKey: '' },
  llm: { providerId: undefined, model: '', temperature: 0.7, maxTokens: 1000, systemPrompt: '', userPrompt: '', outputKey: '' },
  api_call: { url: '', method: 'GET', headers: {}, body: '', outputKey: '', timeoutSeconds: 30 },
  condition: { expression: '' },
  code: { language: 'javascript', code: '', outputKey: '' },
  tool_call: { serverId: undefined, toolName: '', argumentsTemplate: {}, outputKey: '' }
}

function onProviderChange(providerId: number | null) {
  if (!providerId) {
    availableModels.value = []
    return
  }
  // 优先使用缓存的模型，否则从 API 获取
  const cached = providerModelsMap.value.get(providerId)
  if (cached) {
    availableModels.value = cached
    if (configForm.value.model && !cached.some(m => m.modelName === configForm.value.model)) {
      configForm.value.model = ''
    }
  } else {
    providerApi.getProviderModels(providerId).then(models => {
      providerModelsMap.value.set(providerId, models)
      availableModels.value = models
      if (configForm.value.model && !models.some(m => m.modelName === configForm.value.model)) {
        configForm.value.model = ''
      }
    }).catch(() => {
      availableModels.value = []
    })
  }
}

async function onServerChange(serverId: number | null) {
  if (!serverId) { availableTools.value = []; return }
  loadingTools.value = true
  try {
    const detail = await mcpApi.getById(serverId)
    availableTools.value = detail.tools || []
  } catch {
    availableTools.value = []
  } finally {
    loadingTools.value = false
  }
  if (configForm.value.toolName && !availableTools.value.some(t => t.name === configForm.value.toolName)) {
    configForm.value.toolName = ''
  }
}

async function loadFormOptions() {
  try {
    const [providersResult, mcpResult] = await Promise.all([
      providerApi.getProviderList({ enabled: 1, pageSize: 100 }),
      mcpApi.getList({ enabled: 1, pageSize: 100 })
    ])
    const allProviders = (providersResult as any).list || providersResult.records || []
    mcpServerOptions.value = (mcpResult as any).list || mcpResult.records || []

    // 获取每个供应商的模型列表，过滤未同步的供应商
    const modelsResults = await Promise.allSettled(
      allProviders.map((p: ProviderResponse) => providerApi.getProviderModels(p.id))
    )
    const map = new Map<number, ModelConfigInfo[]>()
    providerOptions.value = allProviders.filter((p: ProviderResponse, i: number) => {
      const result = modelsResults[i]
      if (result.status === 'fulfilled' && result.value.length > 0) {
        map.set(p.id, result.value)
        return true
      }
      return false
    })
    providerModelsMap.value = map
  } catch {
    providerOptions.value = []
    mcpServerOptions.value = []
  }
}

/* ========== Canvas 事件 ========== */

let _nodeIdCounter = 0

function generateNodeKey(type: string): string {
  _nodeIdCounter++
  return `node_${type}_${_nodeIdCounter}`
}

function createFlowNode(type: string, position: { x: number; y: number }, existing?: Partial<EditableNodeData>): Node {
  const nodeKey = existing?.nodeKey || generateNodeKey(type)
  const nodeName = existing?.name || nodeTypeLabel(type)
  const config = existing?.config ? JSON.parse(JSON.stringify(existing.config)) : JSON.parse(JSON.stringify(defaultConfigs[type] || {}))

  return {
    id: nodeKey,
    type,
    position: { x: position.x, y: position.y },
    data: {
      name: nodeName,
      type,
      nodeKey,
      config
    }
  }
}

function nodeTypeLabel(type: string): string {
  const map: Record<string, string> = {
    start: t('workflow.nodeTypes.start'),
    end: t('workflow.nodeTypes.end'),
    llm: t('workflow.nodeTypes.llm'),
    api_call: t('workflow.nodeTypes.api'),
    condition: t('workflow.nodeTypes.condition'),
    code: t('workflow.nodeTypes.code'),
    tool_call: t('workflow.nodeTypes.tool')
  }
  return map[type] || type
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function onDrop(event: DragEvent) {
  event.preventDefault()
  const type = event.dataTransfer?.getData('application/vueflow')
  if (!type) return

  const position = screenToFlowCoordinate({
    x: event.clientX - 220,
    y: event.clientY - 60
  })

  const node = createFlowNode(type, position)
  addNodes([node])
  dirty.value = true
  nextNodeNum.value++
}

function onConnect(connection: VFConnection) {
  const edge: Edge = {
    id: `e_${connection.source}_${connection.target}_${Date.now()}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
    type: 'smoothstep',
    label: '',
    data: { condition: connection.sourceHandle || '' }
  }
  addEdges([edge])
  dirty.value = true
}

function onNodeClick({ node }: { node: Node }) {
  openConfig(node.id)
}

function onPaneClick() {
  configDrawerVisible.value = false
  configNodeId.value = null
}

function onNodesChange() {
  dirty.value = true
}

function onEdgesChange() {
  dirty.value = true
}

/* ========== 节点配置面板 ========== */

function openConfig(nodeId: string) {
  configNodeId.value = nodeId
  const node = vfNodes.value.find(n => n.id === nodeId)
  if (!node) return
  const data = node.data as EditableNodeData
  configForm.value = JSON.parse(JSON.stringify(data.config || {}))
  configForm.value._name = data.name

  if (data.type === 'api_call') {
    const headers = configForm.value.headers || {}
    configHeadersList.value = Object.entries(headers).map(([k, v]) => ({ key: k, value: String(v) }))
  } else {
    configHeadersList.value = []
  }

  if (data.type === 'tool_call') {
    const tmpl = configForm.value.argumentsTemplate || {}
    configArgsList.value = Object.entries(tmpl).map(([k, v]) => ({ key: k, value: String(v) }))
    if (configForm.value.serverId) {
      onServerChange(configForm.value.serverId)
    } else {
      availableTools.value = []
    }
  } else {
    configArgsList.value = []
  }

  if (data.type === 'llm' && configForm.value.providerId) {
    const providerId = configForm.value.providerId
    const found = providerOptions.value.find(p => p.id === providerId)
    if (!found && configForm.value.providerAvailable === false) {
      providerOptions.value.push({
        id: providerId,
        name: t('provider.unavailable'),
        type: '' as any,
        baseUrl: '',
        authConfig: null,
        enabled: 0,
        modelConfigs: null,
        health: null,
        createdAt: '',
        updatedAt: ''
      })
    }
    onProviderChange(configForm.value.providerId)
  } else if (data.type === 'llm') {
    availableModels.value = []
  }

  configDrawerVisible.value = true
}

function confirmConfig() {
  if (!configNodeId.value) return
  const node = vfNodes.value.find(n => n.id === configNodeId.value)
  if (!node) return
  const data = node.data as EditableNodeData

  if (data.type === 'api_call') {
    const headers: Record<string, string> = {}
    configHeadersList.value.forEach(h => { if (h.key) headers[h.key] = h.value })
    configForm.value.headers = headers
  }

  if (data.type === 'tool_call') {
    const args: Record<string, any> = {}
    configArgsList.value.forEach(a => { if (a.key) args[a.key] = a.value })
    configForm.value.argumentsTemplate = args
  }

  data.name = configForm.value._name || data.name
  const cleanConfig = { ...configForm.value }
  delete cleanConfig._name
  data.config = JSON.parse(JSON.stringify(cleanConfig))
  node.data = { ...data }
  configDrawerVisible.value = false
  dirty.value = true
}

function deleteSelectedNode() {
  if (!configNodeId.value) return
  removeNodes([configNodeId.value])
  configDrawerVisible.value = false
  configNodeId.value = null
  dirty.value = true
}

/* ========== 数据加载 ========== */

async function loadWorkflow() {
  if (!isEdit.value) {
    _nodeIdCounter = 2
    vfNodes.value = [
      createFlowNode('start', { x: 100, y: 200 }, { nodeKey: 'start', name: t('workflow.nodeTypes.start'), config: { inputVariables: [] } }),
      createFlowNode('end', { x: 600, y: 200 }, { nodeKey: 'end', name: t('workflow.nodeTypes.end'), config: { outputKey: 'result' } })
    ]
    loadDraftFromStorage()
    return
  }

  try {
    const detail = await workflowApi.getById(editId.value)
    formName.value = detail.name
    formDesc.value = detail.description || ''
    formStatus.value = detail.status

    if (detail.variables) {
      try {
        const vars = typeof detail.variables === 'string' ? JSON.parse(detail.variables) : detail.variables
        variables.value = Array.isArray(vars) ? vars.map((v: any) => ({
          key: v.key || '',
          type: v.type || v.varType || 'string',
          required: !!v.required,
          defaultVal: v.defaultVal || v.default || ''
        })) : []
      } catch { variables.value = [] }
    }

    _nodeIdCounter = detail.nodes.length + 1
    vfNodes.value = detail.nodes.map((n: any) => createFlowNode(
      n.type,
      { x: n.positionX || 0, y: n.positionY || 0 },
      { nodeKey: n.nodeKey, name: n.name, config: n.config }
    ))

    const idToKey = new Map(detail.nodes.map((n: any) => [n.id, n.nodeKey]))
    vfEdges.value = detail.edges.map((e: any, i: number) => ({
      id: `e_${i}_${Date.now()}`,
      source: idToKey.get(e.sourceNodeId) || '',
      target: idToKey.get(e.targetNodeId) || '',
      sourceHandle: e.condition || undefined,
      type: 'smoothstep',
      label: e.label || '',
      data: { condition: e.condition || '' }
    }))
  } catch {
    ElMessage.error(t('workflow.loadFailed'))
    router.push('/workflows')
  }
}

function loadDraftFromStorage() {
  try {
    const raw = sessionStorage.getItem('workflowDraft')
    if (raw) {
      const draft = JSON.parse(raw)
      formName.value = draft.name || ''
      formDesc.value = draft.description || ''
      formStatus.value = draft.status ?? 0
      variables.value = Array.isArray(draft.variables) ? draft.variables : []
      sessionStorage.removeItem('workflowDraft')
    }
  } catch { /* ignore */ }
}

/* ========== 验证 & 保存 ========== */

function validate(): string | null {
  if (!formName.value.trim()) return t('workflow.workflowNameRequired')

  const nodes = vfNodes.value
  const edges = vfEdges.value
  const keys = nodes.map(n => (n.data as EditableNodeData).nodeKey).filter(Boolean)

  const keySet = new Set<string>()
  for (const k of keys) {
    if (keySet.has(k)) return t('workflow.duplicateKey', { key: k })
    keySet.add(k)
  }

  for (const e of edges) {
    if (!keys.includes(e.source)) return t('workflow.sourceNotFound', { source: e.source })
    if (!keys.includes(e.target)) return t('workflow.targetNotFound', { target: e.target })
  }

  const hasStart = nodes.some(n => n.type === 'start')
  const hasEnd = nodes.some(n => n.type === 'end')
  if (!hasStart) return t('workflow.atLeastStart')
  if (!hasEnd) return t('workflow.atLeastEnd')

  return null
}

async function handleSave() {
  const error = validate()
  if (error) { ElMessage.warning(error); return }

  const nodes = vfNodes.value
  const edges = vfEdges.value

  const payload: any = {
    name: formName.value.trim(),
    description: formDesc.value.trim() || undefined,
    status: formStatus.value as 0 | 1 | 2,
    variables: variables.value.filter(v => v.key),
    nodes: nodes.map(n => {
      const d = n.data as EditableNodeData
      return {
        nodeKey: d.nodeKey,
        type: d.type as NodeType,
        name: d.name || d.nodeKey,
        positionX: Math.round(n.position.x),
        positionY: Math.round(n.position.y),
        config: d.config
      }
    }),
    edges: edges.map(e => ({
      sourceNodeKey: e.source,
      targetNodeKey: e.target,
      condition: (e.data as any)?.condition || undefined,
      label: e.label || undefined
    }))
  }

  saving.value = true
  try {
    if (isEdit.value) {
      await workflowApi.update(editId.value, payload)
      ElMessage.success(t('common.updateSuccess'))
    } else {
      await workflowApi.create(payload)
      ElMessage.success(t('common.createSuccess'))
    }
    dirty.value = false
    router.push('/workflows')
  } catch {
    // error handled by request interceptor
  } finally {
    saving.value = false
  }
}

/* ========== 返回 ========== */

async function handleBack() {
  if (dirty.value) {
    try { await ElMessageBox.confirm(t('workflow.unsavedConfirm'), t('workflow.unsavedConfirmTitle'), { confirmButtonText: t('workflow.confirmLeave'), cancelButtonText: t('workflow.continueEditing') }) } catch { return }
  }
  router.push('/workflows')
}

/* ========== 初始化 ========== */

onMounted(() => {
  loadWorkflow()
  loadFormOptions()
})
</script>

<style scoped>
.workflow-edit {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

/* ========== 顶部栏 ========== */

.edit-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--eify-bg-surface);
  border-bottom: 1px solid var(--eify-border-subtle);
  z-index: 10;
  flex-shrink: 0;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-name-input {
  width: 280px;
}

.header-name-input :deep(.el-input__inner) {
  font-size: 16px;
  font-weight: 600;
  border: none;
  background: transparent;
  padding-left: 4px;
}

.header-name-input :deep(.el-input__inner):focus {
  background: var(--eify-bg-secondary);
  border-bottom: 2px solid var(--eify-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.header-hint {
  color: var(--eify-warning);
}

/* ========== 画布区域 ========== */

.edit-canvas-wrapper {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.canvas-container {
  flex: 1;
  position: relative;
}

/* ========== 抽屉 ========== */

.config-drawer-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.config-drawer-body :deep(.el-form-item) {
  margin-bottom: 14px;
}

.config-drawer-body :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--eify-text-secondary);
}

/* ========== KV 子表 ========== */

.kv-table :deep(td) {
  padding: 4px 6px;
}

/* ========== 代码输入 ========== */

.code-textarea :deep(.el-textarea__inner) {
  font-family: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', 'Consolas', monospace;
  font-size: 13px;
  line-height: 1.6;
  tab-size: 2;
}

.form-tip {
  color: var(--eify-text-tertiary);
  margin-top: 4px;
}
</style>
