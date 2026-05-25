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

### B.2 约束

- `Conversation` 实体 **只存储 ID**（`agentId`、`workflowId`），不存名称。后向兼容考虑，不加字段。
- 获取已删除实体的名称需要绕过 `@TableLogic` —— 目前后端没有提供这个能力。**Chat 场景不涉及列表渲染**，只涉及单个对话的顶栏展示，可以用后端已有的 `getEntityById` 改为允许查询已删除记录，或在 Chat API 返回时附加名称。

### B.3 修复方案

**思路**：最小侵入。在 Chat 前端做降级展示 + 禁用输入，而非在 Conversation 表加冗余字段。

#### Task B1：前端 — 新增 `agentUnavailable` 状态

**文件**：`eify-web/src/views/ChatView.vue`

```typescript
// 新增状态：当前对话引用的 Agent 是否不可用
const agentUnavailable = ref(false)
const agentUnavailableName = ref('')
```

在 `selectConversation()` 中修改错误处理：

```typescript
async function selectConversation(id: number) {
  // ... existing code ...
  if (conversation?.agentId) {
    agentUnavailable.value = false
    try {
      const agent = await agentApi.getAgent(conversation.agentId)
      currentAgent.value = agent
      currentAgentId.value = agent.id
    } catch (error) {
      // Agent 已被删除或禁用
      currentAgent.value = null
      currentAgentId.value = conversation.agentId // 保留 ID 用于前端展示
      agentUnavailable.value = true
      // 尝试从 knowledgeBases/其他来源推测名称（做不到就用 ID 构造）
      agentUnavailableName.value = `Agent #${conversation.agentId}`
    }
  }
  // ...
}
```

#### Task B2：前端 — 顶栏展示不可用状态

**文件**：`eify-web/src/views/ChatView.vue`

修改顶栏模板（当前 `v-if="currentAgent"` 的 block），新增 `agentUnavailable` 分支：

```html
<!-- Agent 正常时 -->
<div v-if="currentAgent" class="agent-info">
  <!-- 现有代码不变 -->
</div>

<!-- Agent 不可用时 -->
<div v-else-if="agentUnavailable" class="agent-info agent-unavailable-info">
  <div class="agent-avatar-placeholder unavailable-placeholder">
    <svg><!-- warning icon --></svg>
  </div>
  <div>
    <div class="agent-name agent-name-unavailable">
      {{ agentUnavailableName }}
      <span class="unavailable-badge">{{ t('provider.unavailable') }}</span>
    </div>
    <div class="conversation-title">{{ currentConversation?.title || t('chat.newChat') }}</div>
  </div>
</div>

<!-- Workflow / fallback -->
<div v-else-if="currentConversation?.workflowId" class="agent-info">
  <!-- 现有代码不变 -->
</div>
```

#### Task B3：前端 — 禁用输入框 + 提示

**文件**：同上

当 `agentUnavailable` 为 true 时：
1. 禁用输入框（textarea `:disabled` 增加 `agentUnavailable` 条件）
2. 修改 placeholder 为提示文字
3. 发送按钮禁用

```html
<textarea
  :placeholder="agentUnavailable ? t('chat.agentUnavailableHint') : t('chat.inputPlaceholder')"
  :disabled="isSending || agentUnavailable"
/>
```

```html
<button
  :disabled="!inputContent.trim() || isSending || agentUnavailable"
/>
```

#### Task B4：前端 — Workflow 不可用同理

Workflow 的修复更简单——Conversation 的 title 已持久化，用户仍能看到对话名称。只需在 `currentConversation?.workflowId` 存在但 chat 调用失败时给出明确提示。目前的 SSE error 事件已有兜底，但可以增强错误信息。

此项改动最小：确保后端 workflow 执行失败时返回包含 "workflow" 关键字的错误信息，前端不做额外改动。

#### Task B5：CSS — 不可用样式

**文件**：`eify-web/src/views/ChatView.vue`

仿照 AgentList 的 provider-unavailable 模式，在 `<style>` (non-scoped) 中添加：

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
```

#### Task B6：i18n

**文件**：`eify-web/src/i18n/locales/zh-CN.json`、`en-US.json`

```json
{
  "chat": {
    "agentUnavailableHint": "该 Agent 已被删除，无法发送消息",
    "workflowUnavailableHint": "该工作流已被删除，无法发送消息"
  }
}
```

---

## 对照：与已完成的 Provider 修复模式一致

| 修复维度 | Provider 修复（已完成） | KB 修复（Section A） | Chat 修复（Section B） |
|:---|:---|:---|:---|
| 后端标记不可用 | `defaultProviderAvailable=false` | `KnowledgeBaseBrief.name=null` | 前端探测（API 报错） |
| 前端注入 fallback 选项 | `unavailableProviderOption` | `unavailableKnowledgeOptions` | `agentUnavailable` |
| 红色文字 | `provider-unavailable` CSS class | 同样 CSS class | `agent-name-unavailable` CSS class |
| 禁用交互 | option `:disabled` | option `:disabled` | 输入框 `:disabled` |
| i18n | `provider.unavailable: "(不可用)"` | 复用同上 key | 新增 `chat.agentUnavailableHint` |

---

## 不做的事项

- ❌ **MCP Server 删除 → Chat UI**：MCP 工具仅后端使用，前端不展示工具名/html，无需处理
- ❌ **Conversation 表加冗余名称字段**：破坏现有 schema，且 query 已删除实体的名称需要 bypass @TableLogic，解决方式不在 Conversation schema 层面
- ❌ **Agent 选择器中展示已删除 Agent**：新建对话不应选择已删除 Agent，当前逻辑正确
- ❌ **Workflow 删除后前端特殊 UI 标记**：Conversation.title 已持久化，名称不会丢失，后端报错已包含 workflow 相关信息，不需要额外前端标记
