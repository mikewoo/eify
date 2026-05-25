# Deleted Entity Display Fix + Delete Guards Implementation Plan

> **For agentic workers:** Implement task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix frontend display when referenced entities are soft-deleted, and add backend referential integrity checks to prevent deletion of in-use entities.

**Architecture:** Four independent sections — D (backend guards) should ship first to prevent new orphans, then A/C (display fixes for KB→Agent, MCP→Workflow), then B (Chat unavailable handling). Each section is self-contained and testable independently.

**Tech Stack:** Java 21 + Spring Boot + MyBatis-Plus (backend), Vue 3 + TypeScript + Element Plus (frontend)

**Spec:** `docs/specs/2026-05-25-deleted-entity-display-fix-design.md`

---

### Task 1: ErrorCode — Add 4 new error codes

**Files:**
- Modify: `eify-common/src/main/java/com/eify/common/error/ErrorCode.java`
- Modify: `eify-app/src/main/resources/i18n/messages.properties`
- Modify: `eify-app/src/main/resources/i18n/messages_en_US.properties`

- [ ] **Step 1: Add error code constants**

In `ErrorCode.java`, after `WORKFLOW_EXECUTION_FAILED(2008, ...)`:

```java
AGENT_IN_USE(2009, "Agent 已被对话使用，无法删除"),
KNOWLEDGE_IN_USE(2010, "知识库已被 Agent 使用，无法删除"),
WORKFLOW_IN_USE(2011, "工作流已被对话使用，无法删除"),
MCP_SERVER_IN_USE_BY_WORKFLOW(5005, "MCP 服务器已被工作流使用，无法删除"),
```

- [ ] **Step 2: Add i18n messages**

In `messages.properties`:
```properties
error.2009=Agent 已被对话使用，无法删除
error.2010=知识库已被 Agent 使用，无法删除
error.2011=工作流已被对话使用，无法删除
error.5005=MCP 服务器已被工作流使用，无法删除
```

In `messages_en_US.properties`:
```properties
error.2009=Agent is in use by conversations and cannot be deleted
error.2010=Knowledge base is in use by agents and cannot be deleted
error.2011=Workflow is in use by conversations and cannot be deleted
error.5005=MCP server is in use by workflows and cannot be deleted
```

---

### Task 2: Agent delete — Add Conversation reference check

**Files:**
- Modify: `eify-agent/src/main/java/com/eify/agent/mapper/AgentMapper.java`
- Modify: `eify-agent/src/main/resources/mapper/AgentMapper.xml`
- Modify: `eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java`

- [ ] **Step 1: Add countConversationReferences to AgentMapper**

```java
int countConversationReferences(@Param("agentId") Long agentId);
```

- [ ] **Step 2: Add SQL in AgentMapper.xml**

```xml
<select id="countConversationReferences" resultType="int">
    SELECT COUNT(*) FROM ai_chat_session
    WHERE agent_id = #{agentId} AND deleted = 0 AND status = 1
</select>
```

- [ ] **Step 3: Add check in AgentServiceImpl.delete()**

Before `agentMapper.deleteById(id)`, add:
```java
int convRefs = agentMapper.countConversationReferences(id);
if (convRefs > 0) {
    throw new BusinessException(ErrorCode.AGENT_IN_USE);
}
```

---

### Task 3: Knowledge Base delete — Add Agent binding check

**Files:**
- Modify: `eify-agent/src/main/java/com/eify/agent/mapper/AgentKnowledgeMapper.java`
- Modify: `eify-agent/src/main/resources/mapper/AgentKnowledgeMapper.xml`
- Modify: `eify-knowledge/src/main/java/com/eify/knowledge/service/impl/KnowledgeServiceImpl.java`

- [ ] **Step 1: Add countByKnowledgeId + softDeleteByKnowledgeId to AgentKnowledgeMapper**

```java
int countByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
int softDeleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
```

- [ ] **Step 2: Add SQL in AgentKnowledgeMapper.xml**

```xml
<select id="countByKnowledgeId" resultType="int">
    SELECT COUNT(*) FROM agent_knowledge
    WHERE knowledge_id = #{knowledgeId} AND deleted = 0
</select>

<update id="softDeleteByKnowledgeId">
    UPDATE agent_knowledge SET deleted = 1
    WHERE knowledge_id = #{knowledgeId} AND deleted = 0
</update>
```

- [ ] **Step 3: Add check in KnowledgeServiceImpl.deleteKnowledge()**

Inject `AgentKnowledgeMapper`, then add before `removeById(id)`:
```java
int agentRefs = agentKnowledgeMapper.countByKnowledgeId(id);
if (agentRefs > 0) {
    throw new BusinessException(ErrorCode.KNOWLEDGE_IN_USE);
}
agentKnowledgeMapper.softDeleteByKnowledgeId(id);
```

---

### Task 4: Workflow delete — Add Conversation reference check

**Files:**
- Modify: `eify-workflow/src/main/java/com/eify/workflow/mapper/WorkflowMapper.java`
- Modify: `eify-workflow/src/main/resources/mapper/WorkflowMapper.xml`
- Modify: `eify-workflow/src/main/java/com/eify/workflow/service/impl/WorkflowServiceImpl.java`

- [ ] **Step 1: Add countConversationReferences to WorkflowMapper**

```java
int countConversationReferences(@Param("workflowId") Long workflowId);
```

- [ ] **Step 2: Add SQL in WorkflowMapper.xml**

```xml
<select id="countConversationReferences" resultType="int">
    SELECT COUNT(*) FROM ai_chat_session
    WHERE workflow_id = #{workflowId} AND deleted = 0 AND status = 1
</select>
```

- [ ] **Step 3: Add check in WorkflowServiceImpl.delete()**

Before cascade deletes, add:
```java
int convRefs = workflowMapper.countConversationReferences(id);
if (convRefs > 0) {
    throw new BusinessException(ErrorCode.WORKFLOW_IN_USE);
}
```

---

### Task 5: MCP Server delete — Add Workflow ToolCall reference check

**Files:**
- Modify: `eify-mcp/src/main/java/com/eify/mcp/mapper/McpServerMapper.java`
- Modify: `eify-mcp/src/main/resources/mapper/McpServerMapper.xml`
- Modify: `eify-mcp/src/main/java/com/eify/mcp/service/impl/McpServerServiceImpl.java`

- [ ] **Step 1: Add countWorkflowToolCallReferences to McpServerMapper**

```java
int countWorkflowToolCallReferences(@Param("serverId") Long serverId);
```

- [ ] **Step 2: Add SQL in McpServerMapper.xml**

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

- [ ] **Step 3: Add check in McpServerServiceImpl.delete()**

After the existing Agent binding check, add:
```java
int workflowRefs = mcpServerMapper.countWorkflowToolCallReferences(id);
if (workflowRefs > 0) {
    throw new BusinessException(ErrorCode.MCP_SERVER_IN_USE_BY_WORKFLOW);
}
```

---

### Task 6: AgentServiceImpl — Populate knowledgeBases in responses

**Files:**
- Modify: `eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java`

- [ ] **Step 1: Inject KnowledgeBaseMapper**

Add constructor parameter `KnowledgeBaseMapper knowledgeBaseMapper`.

- [ ] **Step 2: Add loadKnowledgeBaseBriefs method**

```java
private List<AgentResponse.KnowledgeBaseBrief> loadKnowledgeBaseBriefs(List<Long> kbIds) {
    if (kbIds == null || kbIds.isEmpty()) return List.of();
    List<KnowledgeBase> kbs = knowledgeBaseMapper.selectBatchIds(kbIds);
    Map<Long, KnowledgeBase> kbMap = kbs.stream()
            .collect(Collectors.toMap(KnowledgeBase::getId, Function.identity()));
    return kbIds.stream()
            .map(id -> {
                KnowledgeBase kb = kbMap.get(id);
                return AgentResponse.KnowledgeBaseBrief.builder()
                        .id(id)
                        .name(kb != null ? kb.getName() : null)
                        .build();
            })
            .collect(Collectors.toList());
}
```

- [ ] **Step 3: Populate in toFullResponse**

After loading `knowledgeIds`, add:
```java
List<AgentResponse.KnowledgeBaseBrief> knowledgeBases = loadKnowledgeBaseBriefs(knowledgeIds);
```
Then add `.knowledgeBases(knowledgeBases)` in the builder chain.

- [ ] **Step 4: Populate in toBasicResponse (buildListResponses)**

After setting knowledgeIds on each response, also call `loadKnowledgeBaseBriefs` and set `knowledgeBases`.

---

### Task 7: Frontend — AgentList.vue KB unavailable handling

**Files:**
- Modify: `eify-web/src/views/AgentList.vue`
- Modify: `eify-web/src/i18n/locales/zh-CN.json`
- Modify: `eify-web/src/i18n/locales/en-US.json`

- [ ] **Step 1: Add unavailableKnowledgeOptions ref**

```typescript
const unavailableKnowledgeOptions = ref<Array<{ id: number; name: string }>>([])
```

- [ ] **Step 2: Add knowledgeSelectOptions computed**

```typescript
const knowledgeSelectOptions = computed(() => {
  const options = knowledgeList.value.map(kb => ({
    id: kb.id, name: kb.name, available: true, embeddingModel: kb.embeddingModel
  }))
  unavailableKnowledgeOptions.value.forEach(opt => {
    if (!options.some(o => o.id === opt.id)) {
      options.push({ id: opt.id, name: opt.name, available: false, embeddingModel: '' })
    }
  })
  return options
})
```

- [ ] **Step 3: Detect unavailable KBs in handleEdit**

```typescript
if (row.knowledgeBases) {
  unavailableKnowledgeOptions.value = row.knowledgeBases
    .filter(kb => kb.name === null)
    .map(kb => ({
      id: kb.id,
      name: `${t('knowledge.unnamed', { id: kb.id })}${t('provider.unavailable')}`
    }))
} else {
  unavailableKnowledgeOptions.value = []
}
```

- [ ] **Step 4: Update KB el-select template**

Change `v-for="kb in knowledgeList"` to `v-for="kb in knowledgeSelectOptions"`, add `:disabled="!kb.available"` and red style for unavailable entries.

- [ ] **Step 5: Add i18n keys**

`knowledge.unnamed` → zh-CN: "知识库 #{id}", en-US: "Knowledge base #{id}"

---

### Task 8: Frontend — WorkflowEdit.vue MCP Server unavailable handling

**Files:**
- Modify: `eify-web/src/views/WorkflowEdit.vue`

- [ ] **Step 1: Inject unavailable server/tool options in openConfig**

When `configForm.serverId` is set but not in `mcpServerOptions`, push a synthetic unavailable option (type: '' pattern) and a synthetic tool option.

- [ ] **Step 2: Add isMcpServerUnavailable computed**

```typescript
const isMcpServerUnavailable = computed(() => {
  if (!configForm.serverId) return false
  const s = mcpServerOptions.value.find(s => s.id === configForm.serverId)
  return !!s && !s.type
})
```

- [ ] **Step 3: Update MCP server select template**

Add `:class="{ 'provider-unavailable': isMcpServerUnavailable }"`, `:disabled="!s.type"` on unavailable options.

- [ ] **Step 4: Update MCP tool select template**

Add `:class="{ 'model-unavailable': isMcpServerUnavailable }"`, `:disabled="t.unavailable"` on unavailable tool option.

---

### Task 9: Frontend — ChatView.vue entity unavailable handling

**Files:**
- Modify: `eify-web/src/views/ChatView.vue`
- Modify: `eify-web/src/i18n/locales/zh-CN.json`
- Modify: `eify-web/src/i18n/locales/en-US.json`

- [ ] **Step 1: Add entityUnavailable refs + Workflow API call**

Add `entityUnavailable`, `entityUnavailableHint` refs. In `selectConversation()`, add `workflowApi.getWorkflow()` call for workflowId case.

- [ ] **Step 2: Add entityUnavailable header branch**

Add new `v-else-if="entityUnavailable"` block before the workflowId block, showing title + "(不可用)" with warning icon.

- [ ] **Step 3: Disable input when entity unavailable**

Add `entityUnavailable` to textarea `:disabled` and button `:disabled`, set placeholder to hint text.

- [ ] **Step 4: Add CSS styles**

Non-scoped `<style>` block with `.entity-unavailable-info`, `.entity-name-unavailable`, `.unavailable-badge`, `.unavailable-placeholder`, `.hint-unavailable`.

- [ ] **Step 5: Add i18n keys**

`chat.agentUnavailableHint`, `chat.workflowUnavailableHint`

---

### Task 10: Verify — Backend tests + Frontend type check

- [ ] **Step 1: Run backend tests**

```bash
mvn test -pl eify-agent -am -q
mvn test -pl eify-knowledge -am -q
mvn test -pl eify-workflow -am -q
mvn test -pl eify-mcp -am -q
```

- [ ] **Step 2: Run frontend checks**

```bash
cd eify-web && npx vue-tsc --noEmit && npx vitest run
```

- [ ] **Step 3: Fix any failures**
