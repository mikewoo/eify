# 已删除实体引用展示修复方案

> **关联问题**：Provider 删除后 Agent/Workflow 编辑页展示裸 ID（已修复，作为本方案的参照模式）。
> 本方案覆盖剩余的两种场景：知识库删除 → Agent 编辑页、Agent/Workflow/MCP 删除 → Chat 页面。

**目标**：当引用的实体被软删除后，前端不展示裸 ID、不静默消失，统一用 "(不可用)" 红色标识提示用户。

**范围**：
- Section A：知识库删除 → Agent 编辑/列表页
- Section B：Agent/Workflow 删除 → Chat 页面

**不涉及**：MCP 删除 → Chat（MCP 工具仅后端使用，删除后静默跳过，无需 UI 处理）

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

### B.4 修复方案

**思路**：利用 Conversation.title 已持久化的名称，在 Agent API 调用失败时标记不可用，顶栏展示 title + 红色 "(不可用)" 标识，同时禁用输入。

#### Task B1：前端 — 新增 `agentUnavailable` 状态

**文件**：`eify-web/src/views/ChatView.vue`

```typescript
// 新增状态：当前对话引用的 Agent 是否不可用
const agentUnavailable = ref(false)
```

在 `selectConversation()` 中修改错误处理：

```typescript
async function selectConversation(id: number) {
  if (currentConversationId.value === id) return

  currentConversationId.value = id
  const conversation = conversations.value.find(c => c.id === id)
  agentUnavailable.value = false  // 重置

  if (conversation?.agentId) {
    try {
      const agent = await agentApi.getAgent(conversation.agentId)
      currentAgent.value = agent
      currentAgentId.value = agent.id
    } catch (error) {
      // Agent 已被删除或禁用 — 不查已删除数据，依赖 title 中已持久化的名称
      currentAgent.value = null
      currentAgentId.value = null
      agentUnavailable.value = true
    }
  } else {
    currentAgent.value = null
    currentAgentId.value = null
  }

  await loadConversationMessages(id)
}
```

#### Task B2：前端 — 顶栏展示不可用状态

**文件**：同上

title 本身就是 "与 DeepSeek 的对话" 或 workflow.name，不需要拼接额外名称。展示逻辑：

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

<!-- Agent 不可用时（新增分支：展示 title + 不可用标识） -->
<div v-else-if="agentUnavailable" class="agent-info agent-unavailable-info">
  <div class="agent-avatar-placeholder unavailable-placeholder">
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
      <circle cx="12" cy="12" r="10"/>
      <path d="M12 8v4M12 16h.01"/>
    </svg>
  </div>
  <div>
    <div class="agent-name agent-name-unavailable">
      {{ currentConversation?.title || t('chat.newChat') }}
      <span class="unavailable-badge">{{ t('provider.unavailable') }}</span>
    </div>
    <div class="conversation-title hint-unavailable">{{ t('chat.agentUnavailableHint') }}</div>
  </div>
</div>

<!-- Workflow 模式（现有代码不变） -->
<div v-else-if="currentConversation?.workflowId" class="agent-info">
  ...
</div>

<!-- 纯文本兜底（现有代码不变） -->
<div v-else>
  {{ currentConversation?.title || t('chat.newChat') }}
</div>
```

**设计说明**：顶栏主标题直接使用 conversation.title（已包含 Agent 名），附加红色 "(不可用)" 标签；副标题显示不可用提示取代原来的 conversation title 重复。

#### Task B3：前端 — 禁用输入 + 提示

**文件**：同上

```html
<textarea
  ref="inputRef"
  v-model="inputContent"
  class="message-input"
  :placeholder="agentUnavailable ? t('chat.agentUnavailableHint') : t('chat.inputPlaceholder')"
  rows="1"
  :disabled="isSending || agentUnavailable"
  @keydown="handleKeyDown"
></textarea>
<button
  class="send-button"
  @click="sendMessage"
  :disabled="!inputContent.trim() || isSending || agentUnavailable"
>
```

#### Task B4：Workflow 场景

Workflow 删除后的处理更简单——title 就是 workflow.name，用户仍能看到。仅需：SSE error 事件返回时，显示更明确的错误信息。此项后端已有兜底，前端不做额外改动。

#### Task B5：CSS — 不可用样式

**文件**：`eify-web/src/views/ChatView.vue`

在 non-scoped `<style>` 块中添加：

```css
.agent-unavailable-info .agent-name-unavailable {
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

#### Task B6：i18n

**文件**：`eify-web/src/i18n/locales/zh-CN.json`、`en-US.json`

```json
// zh-CN
{
  "chat": {
    "agentUnavailableHint": "该 Agent 已被删除，无法发送消息"
  }
}

// en-US
{
  "chat": {
    "agentUnavailableHint": "This agent has been deleted and cannot send messages"
  }
}
```

---

## 对照：与已完成的 Provider 修复模式一致

| 修复维度 | Provider 修复（已完成） | KB 修复（Section A） | Chat 修复（Section B） |
|:---|:---|:---|:---|
| 后端标记不可用 | `defaultProviderAvailable=false` | `KnowledgeBaseBrief.name=null` | 不需要后端改动 |
| 前端注入 fallback | `unavailableProviderOption` | `unavailableKnowledgeOptions` | `agentUnavailable` |
| 名称来源 | 后端返回 `defaultProviderName` | 后端填充 `knowledgeBases` | 复用 `Conversation.title`（已持久化） |
| 红色文字 | `provider-unavailable` CSS class | 同样 CSS class | `agent-name-unavailable` CSS class |
| 禁用交互 | option `:disabled` | option `:disabled` | 输入框 + 发送按钮 `:disabled` |
| i18n | `provider.unavailable: "(不可用)"` | 复用同上 key | 新增 `chat.agentUnavailableHint` |

---

## 不做的事项

- ❌ **MCP Server 删除 → Chat UI**：MCP 工具仅后端使用，前端不展示工具名/html，无需处理
- ❌ **Conversation 表加冗余名称字段**：破坏现有 schema，且 query 已删除实体的名称需要 bypass @TableLogic，解决方式不在 Conversation schema 层面
- ❌ **Agent 选择器中展示已删除 Agent**：新建对话不应选择已删除 Agent，当前逻辑正确
- ❌ **Workflow 删除后前端特殊 UI 标记**：Conversation.title 已持久化，名称不会丢失，后端报错已包含 workflow 相关信息，不需要额外前端标记
