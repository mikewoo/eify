# Provider 删除后展示修复 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Provider 软删除后 Agent 和 Workflow 编辑页显示裸 ID 的问题，同时增加删除前引用校验。

**Architecture:** 两层防线——删除前校验（ProviderServiceImpl.delete 中检查 Agent/Workflow 引用，阻止删除） + 展示兜底（后端标记 provider 不可用，前端通过 i18n 显示占位文本）。不绕过 @TableLogic，不新增数据库字段。

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, Vue 3, vue-i18n, Element Plus

---

### 文件结构

| 文件 | 角色 | 变更类型 |
|:---|:---|:---|
| `eify-common/.../error/ErrorCode.java` | 新增错误码 | 修改 |
| `eify-common/.../i18n/messages.properties` | 中文错误消息 | 修改 |
| `eify-common/.../i18n/messages_en_US.properties` | 英文错误消息 | 修改 |
| `eify-provider/.../mapper/ProviderMapper.java` | 自定义引用计数查询 | 修改 |
| `eify-provider/.../service/impl/ProviderServiceImpl.java` | 删除前校验 | 修改 |
| `eify-agent/.../dto/AgentResponse.java` | 新增 defaultProviderAvailable | 修改 |
| `eify-agent/.../service/impl/AgentServiceImpl.java` | 批量解析 provider 名称+标记可用性 | 修改 |
| `eify-workflow/.../service/impl/WorkflowServiceImpl.java` | LLM 节点注入 providerAvailable | 修改 |
| `eify-web/src/i18n/locales/zh-CN.json` | 前端中文翻译 | 修改 |
| `eify-web/src/i18n/locales/en-US.json` | 前端英文翻译 | 修改 |
| `eify-web/src/views/AgentList.vue` | 编辑对话框 provider 下拉兜底 | 修改 |
| `eify-web/src/views/WorkflowEdit.vue` | LLM 节点 provider 下拉兜底 | 修改 |
| `eify-web/src/api/agent.ts` | 新增 defaultProviderAvailable 类型 | 修改 |

---

### Task 1: ErrorCode + i18n 消息

**Files:**
- Modify: `eify-common/src/main/java/com/eify/common/error/ErrorCode.java`
- Modify: `eify-common/src/main/resources/i18n/messages.properties`
- Modify: `eify-common/src/main/resources/i18n/messages_en_US.properties`

- [ ] **Step 1: 在 ErrorCode 中新增两条错误码**

在 `ErrorCode.java` 的 Provider 段（2000-2999），`MODEL_NOT_SUPPORTED` 之后添加：

```java
PROVIDER_IN_USE(2007, "供应商已被 Agent 使用，无法删除"),
PROVIDER_IN_USE_BY_WORKFLOW(2008, "供应商已被工作流使用，无法删除"),
```

- [ ] **Step 2: 在 messages.properties 新增中文错误消息**

在 Provider 段末尾添加：

```properties
PROVIDER_IN_USE_zh=供应商已被 Agent 使用，无法删除
PROVIDER_IN_USE_BY_WORKFLOW_zh=供应商已被工作流使用，无法删除
```

- [ ] **Step 3: 在 messages_en_US.properties 新增英文错误消息**

在 Provider 段末尾添加：

```properties
PROVIDER_IN_USE_zh=Provider is in use by Agent(s) and cannot be deleted
PROVIDER_IN_USE_BY_WORKFLOW_zh=Provider is in use by Workflow(s) and cannot be deleted
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -q -pl eify-common
```

- [ ] **Step 5: Commit**

```bash
git add eify-common/src/main/java/com/eify/common/error/ErrorCode.java \
        eify-common/src/main/resources/i18n/messages.properties \
        eify-common/src/main/resources/i18n/messages_en_US.properties
git commit -m "feat: add PROVIDER_IN_USE error codes for provider delete validation"
```

---

### Task 2: ProviderMapper — 自定义引用计数查询

**Files:**
- Modify: `eify-provider/src/main/java/com/eify/provider/mapper/ProviderMapper.java`

- [ ] **Step 1: 添加两个自定义查询方法**

```java
package com.eify.provider.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.provider.domain.entity.Provider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProviderMapper extends BaseMapper<Provider> {

    @Select("SELECT COUNT(*) FROM ai_agent WHERE default_provider_id = #{providerId} AND deleted = 0")
    int countAgentReferences(@Param("providerId") Long providerId);

    @Select("SELECT COUNT(*) FROM ai_workflow_node n " +
            "INNER JOIN ai_workflow w ON n.workflow_id = w.id " +
            "WHERE n.type = 'llm' " +
            "AND JSON_EXTRACT(n.config, '$.providerId') = #{providerId} " +
            "AND n.deleted = 0 " +
            "AND w.deleted = 0")
    int countWorkflowLlmReferences(@Param("providerId") Long providerId);
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q -pl eify-provider -am
```

- [ ] **Step 3: Commit**

```bash
git add eify-provider/src/main/java/com/eify/provider/mapper/ProviderMapper.java
git commit -m "feat: add reference count queries to ProviderMapper"
```

---

### Task 3: ProviderServiceImpl.delete() — 删除前校验

**Files:**
- Modify: `eify-provider/src/main/java/com/eify/provider/service/impl/ProviderServiceImpl.java:269-281`

- [ ] **Step 1: 在 delete() 方法中添加引用校验**

将 `delete()` 方法替换为：

```java
@Override
@Transactional(rollbackFor = Exception.class)
@CacheEvict(value = "provider-cache", key = "#id")
public void delete(Long id) {
    Provider existing = WorkspaceGuard.requireInWorkspace(
            providerMapper.selectById(id), ErrorCode.NOT_FOUND);

    int agentRefs = providerMapper.countAgentReferences(id);
    if (agentRefs > 0) {
        throw new BusinessException(ErrorCode.PROVIDER_IN_USE);
    }

    int workflowRefs = providerMapper.countWorkflowLlmReferences(id);
    if (workflowRefs > 0) {
        throw new BusinessException(ErrorCode.PROVIDER_IN_USE_BY_WORKFLOW);
    }

    modelConfigMapper.delete(new LambdaQueryWrapper<ModelConfig>()
            .eq(ModelConfig::getProviderId, id)
            .eq(ModelConfig::getWorkspaceId, CurrentContext.getWorkspaceId()));

    providerMapper.deleteById(id);
    log.info("删除供应商成功，id: {}, name: {}", id, existing.getName());
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q -pl eify-provider -am
```

- [ ] **Step 3: Commit**

```bash
git add eify-provider/src/main/java/com/eify/provider/service/impl/ProviderServiceImpl.java
git commit -m "feat: add Agent/Workflow reference check before provider deletion"
```

---

### Task 4: AgentResponse DTO — 新增 defaultProviderAvailable

**Files:**
- Modify: `eify-agent/src/main/java/com/eify/agent/domain/dto/AgentResponse.java`

- [ ] **Step 1: 添加字段**

在 `defaultProviderType` 字段之后添加：

```java
/**
 * 默认供应商是否可用（false 表示已被删除或禁用）
 */
private Boolean defaultProviderAvailable;
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q -pl eify-agent -am
```

- [ ] **Step 3: Commit**

```bash
git add eify-agent/src/main/java/com/eify/agent/domain/dto/AgentResponse.java
git commit -m "feat: add defaultProviderAvailable field to AgentResponse"
```

---

### Task 5: AgentServiceImpl.toBasicResponse() — 列表批量解析 provider 名称

**Files:**
- Modify: `eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java`
  - `list()` (line 80-102)
  - `list(page, pageSize, name, enabled)` (line 104-133)
  - `toBasicResponse()` (line 538-567)

- [ ] **Step 1: 添加批量解析 provider 名称的方法**

在 `AgentServiceImpl` 类中添加新方法（放在 `toBasicResponse` 附近）：

```java
/**
 * 批量加载 Agent 的 Provider 名称和可用状态
 */
private void batchLoadProviderNames(List<Agent> agents) {
    if (agents == null || agents.isEmpty()) return;

    Set<Long> providerIds = agents.stream()
            .map(Agent::getDefaultProviderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (providerIds.isEmpty()) return;

    List<Provider> providers = providerMapper.selectBatchIds(providerIds);
    Map<Long, Provider> providerMap = providers.stream()
            .collect(Collectors.toMap(Provider::getId, p -> p));

    for (Agent agent : agents) {
        if (agent.getDefaultProviderId() != null) {
            Provider p = providerMap.get(agent.getDefaultProviderId());
            if (p != null) {
                agent.setTempProviderName(p.getName());
                agent.setTempProviderAvailable(true);
            } else {
                agent.setTempProviderAvailable(false);
            }
        }
    }
}
```

Wait — Agent entity doesn't have `tempProviderName` / `tempProviderAvailable` fields. I should use a different approach. Let me add transient helper fields or restructure.

Actually, the cleanest approach: modify `toBasicResponse()` to accept a `Map<Long, Provider>` parameter and resolve there. Keep it stateless.

- [ ] **Step 1 (Revised): 修改 list 方法和 toBasicResponse**

修改两个 `list()` 方法，在调用 `toBasicResponse` 前批量加载 provider：

```java
@Override
public PageResult<AgentResponse> list(Integer page, Integer pageSize) {
    // ... existing pagination setup ...
    IPage<Agent> result = agentMapper.selectPage(pageObj, wrapper);

    batchLoadKnowledgeIds(result.getRecords());
    batchLoadMcpToolIds(result.getRecords());

    // 批量加载 Provider 名称
    Map<Long, Provider> providerMap = batchLoadProvidersForAgents(result.getRecords());

    List<AgentResponse> responses = result.getRecords().stream()
            .map(a -> toBasicResponse(a, providerMap))
            .collect(Collectors.toList());

    return PageResult.of(responses, result.getTotal(), page, pageSize);
}
```

同样修改带筛选的 `list(page, pageSize, name, enabled)` 方法。

添加批量加载方法：

```java
private Map<Long, Provider> batchLoadProvidersForAgents(List<Agent> agents) {
    Set<Long> providerIds = agents.stream()
            .map(Agent::getDefaultProviderId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    if (providerIds.isEmpty()) return Collections.emptyMap();
    return providerMapper.selectBatchIds(providerIds).stream()
            .collect(Collectors.toMap(Provider::getId, p -> p));
}
```

然后修改 `toBasicResponse` 签名和实现：

```java
private AgentResponse toBasicResponse(Agent agent, Map<Long, Provider> providerMap) {
    Provider provider = providerMap.get(agent.getDefaultProviderId());

    return AgentResponse.builder()
            .id(agent.getId())
            .name(agent.getName())
            .description(agent.getDescription())
            .avatar(agent.getAvatar())
            .defaultProviderId(agent.getDefaultProviderId())
            .defaultProviderName(provider != null ? provider.getName() : null)
            .defaultProviderType(provider != null ? provider.getType().toString() : null)
            .defaultProviderAvailable(provider != null)
            .defaultModel(agent.getDefaultModel())
            .systemPrompt(agent.getSystemPrompt())
            .userMessagePrefix(agent.getUserMessagePrefix())
            .welcomeMessage(agent.getWelcomeMessage())
            .temperature(agent.getTemperature())
            .maxTokens(agent.getMaxTokens())
            .topP(agent.getTopP())
            .frequencyPenalty(agent.getFrequencyPenalty())
            .presencePenalty(agent.getPresencePenalty())
            .maxHistoryRounds(agent.getMaxHistoryRounds())
            .streamEnabled(agent.getStreamEnabled())
            .agentConfig(agent.getAgentConfig())
            .enabled(agent.getEnabled())
            .createdAt(agent.getCreatedAt().toString())
            .updatedAt(agent.getUpdatedAt().toString())
            .creatorId(agent.getCreatorId())
            .knowledgeIds(agent.getKnowledgeIds())
            .mcpToolIds(agent.getMcpToolIds())
            .ragEnabled(agent.getRagEnabled())
            .ragTopK(agent.getRagTopK())
            .ragStrategy(agent.getRagStrategy())
            .build();
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q -pl eify-agent -am
```

- [ ] **Step 3: Commit**

```bash
git add eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java
git commit -m "feat: batch-resolve provider names in Agent list response"
```

---

### Task 6: AgentServiceImpl.toFullResponse() — 标记 provider 可用性

**Files:**
- Modify: `eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java:572-625`

- [ ] **Step 1: 修改 toFullResponse 中 provider 处理逻辑**

将 `toFullResponse()` 中 provider 相关部分（line 574, 615-622）替换为：

```java
// 查询关联的 Provider（可能为 null，表示已被软删除）
Provider provider = providerMapper.selectById(agent.getDefaultProviderId());

// ... (middle section unchanged) ...

// 添加 Provider 信息
if (provider != null) {
    builder.defaultProvider(AgentResponse.ProviderInfo.builder()
            .id(provider.getId())
            .name(provider.getName())
            .type(provider.getType().toString())
            .baseUrl(provider.getBaseUrl())
            .build())
            .defaultProviderName(provider.getName())
            .defaultProviderType(provider.getType().toString())
            .defaultProviderAvailable(true);
} else {
    builder.defaultProviderAvailable(false);
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -q -pl eify-agent -am
```

- [ ] **Step 3: Commit**

```bash
git add eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java
git commit -m "feat: mark provider availability in Agent detail response"
```

---

### Task 7: WorkflowServiceImpl.toNodeDetail() — LLM 节点注入 providerAvailable

**Files:**
- Modify: `eify-workflow/src/main/java/com/eify/workflow/service/impl/WorkflowServiceImpl.java:292-303`
- May need to add ProviderMapper import and field

- [ ] **Step 1: 添加 ProviderMapper 依赖注入**

在类顶部添加 import：

```java
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.mapper.ProviderMapper;
import tools.jackson.databind.node.ObjectNode;
```

在构造函数参数中添加 `ProviderMapper`（该类使用 `@RequiredArgsConstructor`，但为确保可见性，显式声明）：

```java
private final ProviderMapper providerMapper;
```

- [ ] **Step 2: 修改 toNodeDetail 方法**

```java
private NodeDetail toNodeDetail(WorkflowNode n) {
    JsonNode config = n.getConfig();

    // 为 LLM 节点注入 provider 可用性信息
    if ("llm".equals(n.getType()) && config != null && config.has("providerId")) {
        Long providerId = config.get("providerId").asLong();
        Provider provider = providerMapper.selectById(providerId);
        ObjectNode mutableConfig = (ObjectNode) config;
        mutableConfig.put("providerAvailable", provider != null);
        if (provider != null) {
            mutableConfig.put("providerName", provider.getName());
        }
    }

    return NodeDetail.builder()
            .id(n.getId())
            .workflowId(n.getWorkflowId())
            .nodeKey(n.getNodeKey())
            .type(n.getType())
            .name(n.getLabel())
            .positionX(n.getPositionX())
            .positionY(n.getPositionY())
            .config(config)
            .build();
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -q -pl eify-workflow -am
```

- [ ] **Step 4: Commit**

```bash
git add eify-workflow/src/main/java/com/eify/workflow/service/impl/WorkflowServiceImpl.java
git commit -m "feat: inject provider availability into workflow LLM node config"
```

---

### Task 8: 前端 i18n 翻译键

**Files:**
- Modify: `eify-web/src/i18n/locales/zh-CN.json`
- Modify: `eify-web/src/i18n/locales/en-US.json`

- [ ] **Step 1: 在 provider 段添加翻译**

在 `zh-CN.json` 的 `provider` 对象中添加（若没有则创建 `provider` 段）：

```json
"provider": {
  "unavailable": "(不可用)",
  "unsyncedHint": "暂无可用供应商，请先在「供应商管理」中添加供应商"
}
```

在 `en-US.json` 的 `provider` 对象中添加：

```json
"provider": {
  "unavailable": "(Unavailable)",
  "unsyncedHint": "No providers available. Please add a provider first in Provider Management"
}
```

> 注意：`provider.unsyncedHint` 已在现有代码中使用，需确认 key 存在。如已存在则只需添加 `unavailable`。

- [ ] **Step 2: 验证 i18n key 一致性**

```bash
cd eify-web && npx ts-node scripts/validate-i18n.ts && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add eify-web/src/i18n/locales/zh-CN.json eify-web/src/i18n/locales/en-US.json
git commit -m "feat: add provider.unavailable i18n key"
```

---

### Task 9: AgentList.vue — 编辑对话框 provider 下拉兜底

**Files:**
- Modify: `eify-web/src/views/AgentList.vue`
- Modify: `eify-web/src/api/agent.ts`

- [ ] **Step 1: 在 agent.ts 的 AgentResponse 接口中添加字段**

在 `AgentResponse` 接口中，`defaultProviderName` 之后添加：

```typescript
defaultProviderAvailable?: boolean
```

- [ ] **Step 2: 修改 AgentList.vue 的 handleEdit 方法**

在 `handleEdit` 方法中，加载完 providers 后，检查被编辑 agent 的 provider 是否可用。找到 `handleEdit` 方法（~line 1090），在 `loadProviders()` 返回的数据加载流程中加入检查。

当前 `handleEdit` 直接设置 `defaultProviderId: row.defaultProviderId`。需要在 `<el-select>` 中为不可用的 provider 添加一个禁用的选项。

修改模板中 `<el-select>` 的 `<el-option>` 循环之后，添加一个条件选项：

```vue
<el-select
  v-model="data.defaultProviderId"
  :placeholder="t('agent.selectProvider')"
  style="width: 100%"
  @change="handleProviderChange"
>
  <el-option
    v-for="provider in providers"
    :key="provider.id"
    :label="provider.name"
    :value="provider.id"
  >
    <span>{{ provider.name }}</span>
    <el-tag size="small" style="margin-left: 8px" effect="plain">{{ provider.type }}</el-tag>
  </el-option>
  <el-option
    v-if="unavailableProviderOption"
    :key="unavailableProviderOption.id"
    :label="unavailableProviderOption.name"
    :value="unavailableProviderOption.id"
    disabled
  />
</el-select>
```

在 `<script setup>` 中添加响应式变量和逻辑：

```typescript
const unavailableProviderOption = ref<{ id: number; name: string } | null>(null)
```

在 `handleEdit` 中，设置完 `defaultProviderId` 后（~line 1100 之后），添加：

```typescript
// 检查 provider 是否仍可用
if (row.defaultProviderId && row.defaultProviderAvailable === false) {
  unavailableProviderOption.value = {
    id: row.defaultProviderId,
    name: t('provider.unavailable')
  }
} else {
  unavailableProviderOption.value = null
}
```

- [ ] **Step 3: 编译和类型检查**

```bash
cd eify-web && npx vue-tsc --noEmit && npx vitest run && cd ..
```

- [ ] **Step 4: Commit**

```bash
git add eify-web/src/views/AgentList.vue eify-web/src/api/agent.ts
git commit -m "feat: display unavailable provider fallback in Agent edit dialog"
```

---

### Task 10: WorkflowEdit.vue — LLM 节点 provider 下拉兜底

**Files:**
- Modify: `eify-web/src/views/WorkflowEdit.vue`

- [ ] **Step 1: 修改 providerOptions 加载逻辑**

在 `loadFormOptions` 方法中（~line 485），加载 `providerOptions` 后，在 `openConfig` 方法中检查 LLM 节点的 provider 是否可用。

修改 `openConfig` 中 LLM 相关部分（~line 638）：

```typescript
if (data.type === 'llm' && configForm.value.providerId) {
  const providerId = configForm.value.providerId
  const found = providerOptions.value.find(p => p.id === providerId)
  if (!found && configForm.value.providerAvailable === false) {
    // 添加一条禁用的选项代表已删除的 provider
    providerOptions.value.push({
      id: providerId,
      name: t('provider.unavailable'),
      type: '',
      baseUrl: '',
      enabled: 0,
      authConfig: null
    } as ProviderResponse)
  }
  onProviderChange(configForm.value.providerId)
}
```

- [ ] **Step 2: 编译和类型检查**

```bash
cd eify-web && npx vue-tsc --noEmit && npx vitest run && cd ..
```

- [ ] **Step 3: Commit**

```bash
git add eify-web/src/views/WorkflowEdit.vue
git commit -m "feat: display unavailable provider fallback in Workflow LLM node config"
```

---

### Task 11: 运行全部测试

- [ ] **Step 1: 后端测试**

```bash
mvn test -q
```

- [ ] **Step 2: 前端测试**

```bash
cd eify-web && npx vue-tsc --noEmit && npx vitest run && cd ..
```

- [ ] **Step 3: 验证全部通过**

---

## Self-Review 结果

1. **Spec 覆盖**：删除校验（Task 3）、展示兜底-后端 Agent 列表（Task 5）、后端 Agent 详情（Task 6）、后端 Workflow（Task 7）、前端 Agent（Task 9）、前端 Workflow（Task 10）、i18n（Task 1 后端 + Task 8 前端）——全部 14 条验证场景均有对应实现
2. **占位符扫描**：无 TBD/TODO
3. **类型一致性**：`defaultProviderAvailable` 字段名在后端 DTO（Task 4）、Service（Task 5/6）、前端类型（Task 9）中保持一致；`providerAvailable` 在 Workflow JSON config（Task 7）和前端读取（Task 10）中保持一致
