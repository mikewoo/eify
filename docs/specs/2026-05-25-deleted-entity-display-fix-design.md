# 已删除实体引用：展示修复 + 删除校验方案

> **关联问题**：Provider 删除后 Agent/Workflow 编辑页展示裸 ID（已修复，作为本方案的参照模式）。
> 本方案覆盖：前端展示修复（Section A-C）+ 后端删除引用校验（Section D）。

**目标**：
1. 当引用的实体被软删除后，前端不展示裸 ID、不静默消失，统一用 "(不可用)" 红色标识
2. 当实体被其他实体引用时，后端阻止删除，返回明确的错误码

**范围**：
- Section A：知识库删除 → Agent 编辑/列表页
- Section B：Agent/Workflow 删除 → Chat 页面
- Section C：MCP Server 删除 → Workflow Tool Call 节点编辑

**排查后确认不需要修复的场景**：

| 场景 | 结论 | 原因 |
|:---|:---|:---|
| MCP Server → Agent | 无需修复 | 后端 `McpServerServiceImpl.delete()` 会检查 agent 绑定，有绑定则阻止删除（`MCP_SERVER_HAS_BINDINGS`） |
| Knowledge Base → Workflow | 不存在 | Workflow 不引用知识库，WorkflowEdit.vue 中无 knowledge 相关代码 |
| MCP → Chat | 无需修复 | MCP 工具仅后端使用，Chat UI 不展示工具名 |

---

## Section A：知识库删除 → Agent 编辑/列表页

### A.1 现状

| 层面 | 问题 |
|:---|:---|
| 后端 `AgentResponse.knowledgeBases` | DTO 定义了 `List<KnowledgeBaseBrief> {id, name}` 字段（`AgentResponse.java:151`），但 **从未填充**，始终为 null |
| 后端 `toFullResponse()` | 填充了 `mcpTools`，但 **没有** 填充 `knowledgeBases` —— 对标缺失 |
| 后端 `toBasicResponse()` | 同样未填充 `knowledgeBases` |
| 前端 `AgentList.vue` 编辑弹窗 | `knowledgeList` 从 `knowledgeApi.getKnowledgeList()` 获取，已删除 KB 被 `@TableLogic` 过滤。`el-select` 的 v-model 值匹配不到 option，显示裸 ID |

### A.2 修复方案

**总体思路**：完全对标 Provider 修复的三层模式（后端标记不可用 + 前端注入不可用选项 + 红色文字）。

#### Task A1：后端 — 新增 `loadKnowledgeBaseBriefs` 方法

**文件**：`eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java`

参照 `loadMcpToolBriefs()` 模式，新增方法：

```java
private List<AgentResponse.KnowledgeBaseBrief> loadKnowledgeBaseBriefs(List<Long> kbIds) {
    if (kbIds == null || kbIds.isEmpty()) return List.of();
    // selectBatchIds 会被 @TableLogic 自动过滤，已删除的 KB 查不出来
    List<KnowledgeBase> kbs = knowledgeBaseMapper.selectBatchIds(kbIds);
    Map<Long, KnowledgeBase> kbMap = kbs.stream()
            .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));

    return kbIds.stream()
            .map(id -> {
                KnowledgeBase kb = kbMap.get(id);
                if (kb != null) {
                    return AgentResponse.KnowledgeBaseBrief.builder()
                            .id(id).name(kb.getName()).build();
                }
                return AgentResponse.KnowledgeBaseBrief.builder()
                        .id(id).name(null).build(); // name=null 标记不可用
            })
            .collect(Collectors.toList());
}
```

**关键点**：`selectBatchIds` 受 `@TableLogic` 影响，已删除的 KB 查不出来 → name 为 null → 前端据此展示 "(不可用)"。

需要注入 `KnowledgeBaseMapper`（新增依赖）。

#### Task A2：后端 — 在 `toFullResponse` 中填充 `knowledgeBases`

**文件**：同上

在 `toFullResponse()` 中，参照 `mcpTools` 的填充方式：

```java
// 现有代码（line 601）
List<Long> knowledgeIds = agentKnowledgeMapper.selectKnowledgeIdsByAgentId(agent.getId());

// 新增：填充 knowledgeBases
List<AgentResponse.KnowledgeBaseBrief> knowledgeBases = loadKnowledgeBaseBriefs(knowledgeIds);

// 在 builder 中添加（参照 line 633 mcpTools 的写法）
.knowledgeBases(knowledgeBases)
```

#### Task A3：后端 — 在 `toBasicResponse` 中填充 `knowledgeBases`

**文件**：同上

`toBasicResponse()` 用于列表查询，Agent 实体的 `knowledgeIds` 已在 `batchLoadKnowledgeIds()` 中批量加载。

修改 `buildListResponses()` 或在 `toBasicResponse()` 中：
1. 收集所有 agent 的 knowledgeIds
2. 批量查出 KnowledgeBase name
3. 填充 `knowledgeBases`

```java
// 在 buildListResponses 中，对每个 agent：
List<AgentResponse.KnowledgeBaseBrief> knowledgeBases = 
    loadKnowledgeBaseBriefs(agent.getKnowledgeIds());
response.setKnowledgeBases(knowledgeBases);
```

#### Task A4：前端 — AgentList.vue 知识库多选添加不可用选项

**文件**：`eify-web/src/views/AgentList.vue`

参照 Provider 的 `unavailableProviderOption` 模式：

1. 新增 ref：
```typescript
const unavailableKnowledgeOptions = ref<Array<{ id: number; name: string }>>([])
```

2. 新增 computed 合并可用和不可用选项：
```typescript
const knowledgeSelectOptions = computed(() => {
  const options = knowledgeList.value.map(kb => ({
    id: kb.id, name: kb.name, available: true
  }))
  unavailableKnowledgeOptions.value.forEach(opt => {
    if (!options.some(o => o.id === opt.id)) {
      options.push({ id: opt.id, name: opt.name, available: false })
    }
  })
  return options
})
```

3. 在 `handleEdit` 中检测不可用 KB：
```typescript
// 从 knowledgeBases 字段判断（后端已填充 name=null 标记不可用）
if (row.knowledgeBases) {
  const unavailable = row.knowledgeBases
    .filter(kb => kb.name === null)
    .map(kb => ({
      id: kb.id,
      name: `${t('knowledge.unnamed', { id: kb.id })}${t('provider.unavailable')}`
    }))
  unavailableKnowledgeOptions.value = unavailable
}
```

4. 修改模板中 el-select 的 option 列表：
```html
<el-option
  v-for="kb in knowledgeSelectOptions"
  :key="kb.id"
  :label="kb.available ? kb.name : `${kb.name}${t('provider.unavailable')}`"
  :value="kb.id"
  :disabled="!kb.available"
>
  <span :style="!kb.available ? { color: 'var(--eify-error)' } : {}">
    {{ kb.available ? kb.name : `${kb.name}${t('provider.unavailable')}` }}
  </span>
  <el-tag v-if="kb.available" size="small" style="margin-left: 8px" effect="plain">
    {{ kb.embeddingModel }}
  </el-tag>
</el-option>
```

5. 知识库选中值红色显示 — 需要给包含已禁用 KB 的 select 添加 CSS class（仿 provider-unavailable 模式）。

#### Task A5：i18n

**文件**：`eify-web/src/i18n/locales/zh-CN.json`、`en-US.json`

新增 key（如未存在）：
```json
{
  "knowledge": {
    "unnamed": "知识库 #{id}"
  }
}
```

---

## Section B：Agent/Workflow 删除 → Chat 页面

### B.1 现状

| 场景 | 当前行为 | 问题 |
|:---|:---|:---|
| Agent 被删除，打开历史对话 | `selectConversation()` 中 `agentApi.getAgent()` 抛错，`currentAgent` 设为 null，顶栏静默回退显示 conversation.title | 用户不知道 Agent 已被删除，头像和名称无故消失 |
| Agent 被删除，尝试发消息 | 后端 `agentService.getEntityById()` 抛 `NOT_FOUND`，返回通用错误 | 错误信息不明确，用户不知道为什么发不出去 |
| Workflow 被删除 | 顶栏始终用 `conversation.title`（持久化字段），不查 workflow 名 | 名称正常，但发消息时 workflow 引擎调用失败，错误不明确 |
| Agent/Workflow 选择器 | 已删除的不会出现 | 正常，无需改动 |

### B.2 关键发现：名称已在 title 中持久化

创建对话时，title 已经包含了实体名称：

```typescript
// ChatView.vue line 139 — 创建 Agent 对话
const title = newConversationTitle.value.trim() || t('chat.conversationWithAgent', { name: agent.name })
// 例："与 DeepSeek 的对话"

// ChatView.vue line 189 — 创建 Workflow 对话
const title = newConversationTitle.value.trim() || workflow.name
// 例："客户服务流程"
```

**这意味着：不需要绕过 `@TableLogic` 查已删除数据，也不需要给 Conversation 表加冗余字段。title 就是已持久化的名称。**

### B.3 约束

- 🔴 **不绕过 `@TableLogic`**：已删除的数据不应被查询，尊重软删除语义
- 🟡 **不加数据库字段**：title 已包含名称，无需冗余
- 🟢 **Chat 降级展示**：用 title + "(不可用)" 标识 + 禁用输入
- 🟢 **Agent 和 Workflow 统一处理**：两者删除场景一致，用同一套状态和模板逻辑

### B.4 修复方案

**思路**：进入对话时探测 Agent/Workflow 是否还存在（正常 API 调用，不绕过软删除）。如果不存在，用 `conversation.title`（已持久化）作为名称，附加红色 "(不可用)" 标识，同时禁用输入。Agent 和 Workflow 完全统一处理。

#### Task B1：前端 — 新增 `entityUnavailable` 状态

**文件**：`eify-web/src/views/ChatView.vue`

用统一状态覆盖 Agent 和 Workflow 两种场景：

```typescript
// 新增：当前对话引用的实体（Agent/Workflow）是否已被删除
const entityUnavailable = ref(false)
const entityUnavailableHint = ref('') // 对应的提示文字
```

在 `selectConversation()` 中增加可用性探测：

```typescript
async function selectConversation(id: number) {
  if (currentConversationId.value === id) return

  currentConversationId.value = id
  const conversation = conversations.value.find(c => c.id === id)
  entityUnavailable.value = false  // 重置
  entityUnavailableHint.value = ''

  if (conversation?.agentId) {
    try {
      const agent = await agentApi.getAgent(conversation.agentId)
      currentAgent.value = agent
      currentAgentId.value = agent.id
    } catch (error) {
      // Agent 已被删除或禁用
      currentAgent.value = null
      currentAgentId.value = null
      entityUnavailable.value = true
      entityUnavailableHint.value = t('chat.agentUnavailableHint')
    }
  } else if (conversation?.workflowId) {
    // 新增：探测 Workflow 是否可用
    try {
      await workflowApi.getWorkflow(conversation.workflowId)
      // 可用，正常进入（现有 Workflow 模式逻辑）
    } catch (error) {
      // Workflow 已被删除或禁用
      entityUnavailable.value = true
      entityUnavailableHint.value = t('chat.workflowUnavailableHint')
    }
    currentAgent.value = null
    currentAgentId.value = null
  } else {
    currentAgent.value = null
    currentAgentId.value = null
  }

  await loadConversationMessages(id)
}
```

**注意**：Workflow 分支之前没有 API 调用，现在新增 `workflowApi.getWorkflow()` 调用用于可用性探测。需要从 `@/api/workflow` 确认该 API 存在。

#### Task B2：前端 — 顶栏模板，Agent 和 Workflow 统一不可用分支

**文件**：同上

当前 Workflow 模式显示独立的 header（带 upload icon + title），但被删除后应和 Agent 删除走统一的不可用样式：

```html
<!-- Agent 正常时（现有代码不变） -->
<div v-if="currentAgent" class="agent-info">
  <img v-if="currentAgent.avatar" :src="currentAgent.avatar" class="agent-avatar" alt="">
  <div v-else class="agent-avatar-placeholder">{{ currentAgent.name.charAt(0) }}</div>
  <div>
    <div class="agent-name">{{ currentAgent.name }}</div>
    <div class="conversation-title">{{ currentConversation?.title || t('chat.newChat') }}</div>
  </div>
</div>

<!-- 实体不可用（新增：Agent 或 Workflow 删除统一走此分支） -->
<div v-else-if="entityUnavailable" class="agent-info entity-unavailable-info">
  <div class="agent-avatar-placeholder unavailable-placeholder">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="10"/>
      <path d="M12 8v4M12 16h.01"/>
    </svg>
  </div>
  <div>
    <div class="agent-name entity-name-unavailable">
      {{ currentConversation?.title || t('chat.newChat') }}
      <span class="unavailable-badge">{{ t('provider.unavailable') }}</span>
    </div>
    <div class="conversation-title hint-unavailable">{{ entityUnavailableHint }}</div>
  </div>
</div>

<!-- Workflow 模式（现有代码不变 — 仅在 workflow 可用时显示） -->
<div v-else-if="currentConversation?.workflowId" class="agent-info">
  <div class="agent-avatar-placeholder workflow-placeholder">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <path d="M4 17v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 9l5-5 5 5M12 4v12"/>
    </svg>
  </div>
  <div>
    <div class="agent-name">{{ currentConversation.title }}</div>
    <div class="conversation-title">{{ t('chat.workflowMode') }}</div>
  </div>
</div>

<!-- 纯文本兜底（现有代码不变） -->
<div v-else>
  {{ currentConversation?.title || t('chat.newChat') }}
</div>
```

**关键变化**：`entityUnavailable` 分支放在 `workflowId` 分支之前——因为 Workflow 不可用时 `currentConversation?.workflowId` 仍然为真，需要先匹配不可用状态。

#### Task B3：前端 — 禁用输入 + 提示

**文件**：同上

```html
<textarea
  ref="inputRef"
  v-model="inputContent"
  class="message-input"
  :placeholder="entityUnavailable ? entityUnavailableHint : t('chat.inputPlaceholder')"
  rows="1"
  :disabled="isSending || entityUnavailable"
  @keydown="handleKeyDown"
></textarea>
<button
  class="send-button"
  @click="sendMessage"
  :disabled="!inputContent.trim() || isSending || entityUnavailable"
>
```

#### Task B4：CSS — 不可用样式

**文件**：`eify-web/src/views/ChatView.vue`

在 non-scoped `<style>` 块中添加：

```css
.entity-unavailable-info .entity-name-unavailable {
  color: var(--eify-error);
}

.unavailable-badge {
  color: var(--eify-error);
  font-size: 0.85em;
  margin-left: 4px;
}

.unavailable-placeholder {
  background-color: var(--eify-error-bg, #fef2f2);
  border: 1px dashed var(--eify-error);
}

.hint-unavailable {
  color: var(--eify-error);
}
```

#### Task B5：i18n

**文件**：`eify-web/src/i18n/locales/zh-CN.json`、`en-US.json`

```json
// zh-CN
{
  "chat": {
    "agentUnavailableHint": "该 Agent 已被删除，无法发送消息",
    "workflowUnavailableHint": "该工作流已被删除，无法发送消息"
  }
}

// en-US
{
  "chat": {
    "agentUnavailableHint": "This agent has been deleted and cannot send messages",
    "workflowUnavailableHint": "This workflow has been deleted and cannot send messages"
  }
}
```

---

## Section C：MCP Server 删除 → Workflow Tool Call 节点编辑

### C.1 现状

Workflow 的 Tool Call 节点存储 MCP 引用方式不同于 Agent（Agent 用 `agent_mcp_tool` 中间表），Tool Call 节点直接在 config JSON 中存 `serverId` + `toolName`。

| 层面 | 问题 |
|:---|:---|
| 后端 `McpServerServiceImpl.delete()` | **不检查 Workflow 引用**——只检查 Agent 绑定，Workflow 节点引用不受保护 |
| 前端 `WorkflowEdit.vue` `openConfig()` | `mcpServerOptions` 从 `mcpApi.getList({ enabled: 1 })` 加载，已删除 server 不出现。但 `configForm.serverId` 保留旧值，select 为空 |
| 前端 tool 下拉 | `onServerChange()` 调用 `mcpApi.getById(serverId)`，已删除 server 返回 404，工具列表为空，`configForm.toolName` 保留旧值但不匹配任何 option |
| 运行时 | `ToolCallNodeExecutor` 检查 `mcpServerMapper.selectById(config.serverId())`，返回 null 时报错 "MCP Server 不存在或已禁用: id=X"——**裸 ID 出现在错误消息中** |

### C.2 修复方案

参照 Provider 修复模式，在 `openConfig` 和 `onServerChange` 中处理不可用 server。

#### Task C1：前端 — 新增 `unavailableMcpServer` 选项

**文件**：`eify-web/src/views/WorkflowEdit.vue`

参照已有的 `providerOptions` 不可用处理（Lines 649-658）：

在 `openConfig()` 中，检测 `configForm.serverId` 是否在 `mcpServerOptions` 中：

```typescript
// openConfig 中 serverId 探测（参照 provider 处理 lines 649-658）
if (configForm.serverId) {
  const serverExists = mcpServerOptions.value.some(s => s.id === configForm.serverId)
  if (!serverExists) {
    // MCP Server 已被删除 —— 注入不可用选项
    const unavailableOpt = {
      id: configForm.serverId,
      name: `MCP Server #${configForm.serverId}${t('provider.unavailable')}`,
      type: '' // 标记为不可用（与 provider 的 type: '' 模式一致）
    }
    mcpServerOptions.value.push(unavailableOpt)
    // tool 下拉同样注入不可用选项
    toolOptions.value = [{
      value: configForm.toolName,
      label: `${configForm.toolName}${t('provider.unavailable')}`,
      description: '',
      unavailable: true
    }]
  } else {
    await onServerChange(configForm.serverId)
  }
}
```

#### Task C2：模板 — Server 下拉展示不可用样式

**文件**：同上

Server select 中 disabled 不可用选项 + 红色文字（仿 provider 模式）：

```html
<el-select v-model="configForm.serverId" :class="{ 'provider-unavailable': isMcpServerUnavailable }" ...>
  <el-option
    v-for="s in mcpServerOptions"
    :key="s.id"
    :label="s.type === '' ? s.name : s.name"
    :value="s.id"
    :disabled="s.type === ''"
  >
    <span :style="s.type === '' ? { color: 'var(--eify-error)' } : {}">{{ s.name }}</span>
  </el-option>
</el-select>
```

新增 computed：
```typescript
const isMcpServerUnavailable = computed(() => {
  if (!configForm.serverId) return false
  const s = mcpServerOptions.value.find(s => s.id === configForm.serverId)
  return !!s && !s.type // type 为空 = 不可用
})
```

#### Task C3：模板 — Tool 下拉展示不可用样式

Tool select 同理，当 server 不可用时，tool 也是不可用的：

```html
<el-select v-model="configForm.toolName" :class="{ 'model-unavailable': isMcpServerUnavailable }" ...>
  <el-option
    v-for="t in toolOptions"
    :key="t.value"
    :label="t.unavailable ? t.label : t.value"
    :value="t.value"
    :disabled="t.unavailable"
  >
    <span :style="t.unavailable ? { color: 'var(--eify-error)' } : {}">{{ t.label || t.value }}</span>
  </el-option>
</el-select>
```

#### Task C4：ToolCallNodeExecutor 错误消息优化（可选）

**文件**：`eify-workflow/src/main/java/com/eify/workflow/engine/executor/ToolCallNodeExecutor.java`

当前错误消息含裸 serverId：
```java
return NodeResult.fail("MCP Server 不存在或已禁用: id=" + config.serverId());
```

改进为包含上下文：
```java
return NodeResult.fail("MCP Server 不可用 (id=" + config.serverId() + ")，无法执行工具调用: " + config.toolName());
```

---

## 对照：与已完成的 Provider 修复模式一致

| 修复维度 | Provider（已完成） | KB→Agent（Sec A） | Agent/Workflow→Chat（Sec B） | MCP→Workflow（Sec C） |
|:---|:---|:---|:---|:---|
| 后端标记不可用 | `defaultProviderAvailable=false` | `KnowledgeBaseBrief.name=null` | 不需要后端改动 | `ToolCallNodeExecutor` 错误消息优化 |
| 前端注入 fallback | `unavailableProviderOption` | `unavailableKnowledgeOptions` | `entityUnavailable` | 注入不可用 server/tool 选项 |
| 名称来源 | 后端返回 name | 后端填充 `knowledgeBases` | 复用 `Conversation.title` | 用 `serverId` 构造（无法获取原名） |
| 红色文字 | CSS class | 同左 | `entity-name-unavailable` | 复用已有 CSS class |
| 禁用交互 | option `:disabled` | option `:disabled` | 输入框 + 按钮 `:disabled` | option `:disabled` |
| i18n | `provider.unavailable` | 复用同 key | 新增 `chat.*Hint` | 复用 `provider.unavailable` |

---

## Section D：后端删除引用校验

### D.1 现状审计

| 实体 | 删除方法 | 现有校验 | 缺失校验 |
|:---|:---|:---|:---|
| **Provider** | `ProviderServiceImpl.delete()` | Agent + Workflow LLM 节点引用 ✅ | 无缺失（最完善） |
| **Agent** | `AgentServiceImpl.delete()` | **无** ❌ | Conversation (`ai_chat_session.agentId`) |
| **Knowledge Base** | `KnowledgeServiceImpl.deleteKnowledge()` | **无** ❌ | Agent 绑定 (`agent_knowledge.knowledge_id`) |
| **Workflow** | `WorkflowServiceImpl.delete()` | **无** ❌ | Conversation (`ai_chat_session.workflowId`) + WorkflowExecution |
| **MCP Server** | `McpServerServiceImpl.delete()` | Agent 绑定 ✅ | Workflow ToolCall 节点 config JSON 中的 `serverId` |

现有 `ErrorCode` 中 "IN_USE" 相关只有三个：
- `PROVIDER_IN_USE` (2007) — "供应商已被 Agent 使用，无法删除"
- `PROVIDER_IN_USE_BY_WORKFLOW` (2008) — "供应商已被工作流使用，无法删除"
- `MCP_SERVER_HAS_BINDINGS` (5004) — "MCP 服务器有 Agent 绑定，无法删除"

### D.2 各实体校验逻辑

#### Task D1：Agent 删除 — 检查 Conversation 引用

**文件**：`eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java`

在 `delete()` 方法开头增加：

```java
// 检查是否有进行中的对话引用了此 Agent
Long convCount = conversationMapper.selectCount(
    new LambdaQueryWrapper<Conversation>()
        .eq(Conversation::getAgentId, id)
        .eq(Conversation::getStatus, 1) // 仅检查进行中的对话
);
if (convCount > 0) {
    throw new BusinessException(ErrorCode.AGENT_IN_USE);
}
```

需要在 `AgentServiceImpl` 中注入 `ConversationMapper`（或通过 AgentMapper 新增 count 方法）。

**新增 ErrorCode**：`AGENT_IN_USE(2009, "Agent 已被对话使用，无法删除")`

#### Task D2：Knowledge Base 删除 — 检查 Agent 绑定

**文件**：`eify-knowledge/src/main/java/com/eify/knowledge/service/impl/KnowledgeServiceImpl.java`

在 `deleteKnowledge()` 方法开头增加：

```java
// 检查是否有 Agent 引用了此知识库
Long agentCount = agentKnowledgeMapper.selectCount(
    new LambdaQueryWrapper<AgentKnowledge>()
        .eq(AgentKnowledge::getKnowledgeId, id)
);
if (agentCount > 0) {
    throw new BusinessException(ErrorCode.KNOWLEDGE_IN_USE);
}

// 清理关联记录
agentKnowledgeMapper.softDeleteByKnowledgeId(id);
```

需要在 `KnowledgeServiceImpl` 中注入 `AgentKnowledgeMapper`，并在 `AgentKnowledgeMapper` 中新增 `softDeleteByKnowledgeId` 方法。

**新增 ErrorCode**：`KNOWLEDGE_IN_USE(2010, "知识库已被 Agent 使用，无法删除")`

#### Task D3：Workflow 删除 — 检查 Conversation 引用

**文件**：`eify-workflow/src/main/java/com/eify/workflow/service/impl/WorkflowServiceImpl.java`

在 `delete()` 方法开头增加：

```java
// 检查是否有进行中的对话引用了此 Workflow
Long convCount = conversationMapper.selectCount(
    new LambdaQueryWrapper<Conversation>()
        .eq(Conversation::getWorkflowId, id)
        .eq(Conversation::getStatus, 1)
);
if (convCount > 0) {
    throw new BusinessException(ErrorCode.WORKFLOW_IN_USE);
}

// 清理执行记录
workflowExecutionMapper.delete(
    new LambdaQueryWrapper<WorkflowExecution>()
        .eq(WorkflowExecution::getWorkflowId, id)
);
```

**新增 ErrorCode**：`WORKFLOW_IN_USE(2011, "工作流已被对话使用，无法删除")`

#### Task D4：MCP Server 删除 — 检查 Workflow ToolCall 引用

**文件**：`eify-mcp/src/main/java/com/eify/mcp/service/impl/McpServerServiceImpl.java`

在现有 Agent 绑定检查之后，增加 Workflow ToolCall 节点引用检查：

```java
// 检查是否有 Workflow ToolCall 节点引用了此 Server
int workflowRefs = mcpServerMapper.countWorkflowToolCallReferences(id);
if (workflowRefs > 0) {
    throw new BusinessException(ErrorCode.MCP_SERVER_IN_USE_BY_WORKFLOW);
}
```

需要在 `McpServerMapper` 中新增 SQL 查询（类似 Provider 的 `countWorkflowLlmReferences`）：

```xml
<select id="countWorkflowToolCallReferences" resultType="int">
    SELECT COUNT(*)
    FROM ai_workflow_node n
    JOIN ai_workflow w ON w.id = n.workflow_id AND w.deleted = 0
    WHERE n.type = 'tool_call'
      AND n.deleted = 0
      AND JSON_EXTRACT(n.config, '$.serverId') = #{serverId}
</select>
```

**新增 ErrorCode**：`MCP_SERVER_IN_USE_BY_WORKFLOW(5005, "MCP 服务器已被工作流使用，无法删除")`

### D.3 ErrorCode 汇总

**文件**：`eify-common/src/main/java/com/eify/common/error/ErrorCode.java`

新增 4 个错误码：

| 常量 | Code | Message (zh_CN) | Message (en_US) |
|:---|:---|:---|:---|
| `AGENT_IN_USE` | 2009 | Agent 已被对话使用，无法删除 | Agent is in use by conversations and cannot be deleted |
| `KNOWLEDGE_IN_USE` | 2010 | 知识库已被 Agent 使用，无法删除 | Knowledge base is in use by agents and cannot be deleted |
| `WORKFLOW_IN_USE` | 2011 | 工作流已被对话使用，无法删除 | Workflow is in use by conversations and cannot be deleted |
| `MCP_SERVER_IN_USE_BY_WORKFLOW` | 5005 | MCP 服务器已被工作流使用，无法删除 | MCP server is in use by workflows and cannot be deleted |

### D.4 Mapper 新增方法汇总

| Mapper | 新增方法 | 用途 |
|:---|:---|:---|
| `AgentMapper` | `countConversationReferences(Long agentId)` | 统计引用该 Agent 的对话数 |
| `AgentKnowledgeMapper` | `softDeleteByKnowledgeId(Long knowledgeId)` | 按 knowledgeId 清理关联记录 |
| `WorkflowMapper` | `countConversationReferences(Long workflowId)` | 统计引用该 Workflow 的对话数 |
| `McpServerMapper` | `countWorkflowToolCallReferences(Long serverId)` | 统计引用该 Server 的 Workflow ToolCall 节点数 |

### D.5 i18n 消息

**文件**：`eify-app/src/main/resources/i18n/messages.properties`、`messages_en_US.properties`

新增对应错误消息 key（与 ErrorCode 的 message 一致）。

---

## 不做的事项

- ❌ **MCP Server → Chat UI**：MCP 工具仅后端使用，Chat UI 不展示工具名，无需处理
- ❌ **Conversation 表加冗余名称字段**：title 字段已持久化名称，不需额外字段
- ❌ **Agent 选择器中展示已删除 Agent**：新建对话不应选择已删除 Agent，当前逻辑正确
- ❌ **MCP Tool 独立删除方法**：当前无此功能，不属于本方案范围
