# Deleted Provider Shows ID Instead of Name in Agent and Workflow Edit Pages

## Description

When a Provider is soft-deleted (`deleted=1`), the Agent edit page and Workflow LLM node edit page display the raw numeric `providerId` instead of the provider name. This happens because:

1. **Backend**: `@TableLogic` on `BaseEntity.deleted` causes MyBatis-Plus to auto-append `AND deleted=0` to all queries. Both `AgentServiceImpl.toBasicResponse()` (list) and `toFullResponse()` (detail) fail to resolve the provider name for soft-deleted providers. `WorkflowServiceImpl.toNodeDetail()` never resolves provider names at all — raw JSON config is passed through.

2. **Frontend**: Both edit pages load a live provider list via `getProviderList()` for `<el-select>` dropdown options. The deleted provider is absent from this list, so the `<el-select>` bound to the numeric `providerId` cannot find a matching option label and displays the raw ID number.

**Seven affected code locations:**

| # | Location | Issue |
|:---|:---|:---|
| 1 | `AgentServiceImpl.toBasicResponse():538` | Never resolves `defaultProviderName` — always null in list response |
| 2 | `AgentServiceImpl.toFullResponse():615` | Silently skips provider info when `providerMapper.selectById()` returns null for deleted provider |
| 3 | `WorkflowServiceImpl.toNodeDetail():292` | Passes raw JSON config through, no provider name resolution |
| 4 | `AgentList.vue` `<el-select>` :188-198 | Dropdown shows raw ID when provider not in options list |
| 5 | `WorkflowEdit.vue` `<el-select>` :176-178 | Same issue for LLM node config drawer |
| 6 | `ProviderServiceImpl.list()` | Excludes soft-deleted providers due to `@TableLogic` |
| 7 | `AgentResponse` DTO | Has `defaultProviderName` field but backend never populates it for deleted providers |

## Steps to Reproduce

### Scenario: Agent edit page shows raw provider ID

1. Create a Provider (e.g., "OpenAI", id=5)
2. Create an Agent and set its `defaultProviderId` to 5
3. Delete the Provider (soft-delete, `deleted=1`)
4. Navigate to Agent list page → see the agent row
5. Click "Edit" on the agent
6. The Provider dropdown displays "5" (raw ID) instead of "OpenAI"

### Scenario: Workflow LLM node edit shows raw provider ID

1. Create a Provider (e.g., "Claude", id=3)
2. Create a Workflow with an LLM node, configure it to use providerId=3
3. Delete the Provider (soft-delete)
4. Navigate to Workflow edit page → open the LLM node config drawer
5. The Provider dropdown displays "3" (raw ID) instead of "Claude"

## Expected Behavior

- When a referenced provider is soft-deleted, the UI should still display the provider **name** with a visual indicator that it has been deleted (e.g., "OpenAI (deleted)" or "(Deleted Provider)" in muted text)
- The backend should resolve and return provider names even for soft-deleted records
- Workflow LLM node config should also resolve and display provider names

## Impact Analysis

| Call Chain | Risk |
|:---|:---|
| `AgentController.list()` → `AgentServiceImpl.list()` → `toBasicResponse()` | Agent list rows show raw providerId |
| `AgentController.getById()` → `AgentServiceImpl.getById()` → `toFullResponse()` | Agent detail shows no provider info at all |
| `WorkflowController.getById()` → `WorkflowServiceImpl.getById()` → `toNodeDetail()` | LLM node config carries unresolved providerId |
| `ProviderController.list()` → `ProviderServiceImpl.list()` | Excludes deleted providers, frontend dropdown has no matching option |

## Suggested Fix

### Backend: Resolve provider name including soft-deleted records

**Option A — Use custom mapper query that ignores `@TableLogic`:**

```java
// ProviderMapper.java — add a method that includes deleted records
@Select("SELECT id, name, type, base_url, deleted FROM ai_provider WHERE id = #{id}")
Provider selectByIdIncludeDeleted(@Param("id") Long id);
```

**Option B — Use `selectList` with `@TableLogic` override (MyBatis-Plus 3.5.1+):**

Not reliably supported; prefer Option A.

**AgentServiceImpl.toFullResponse():**
```java
// After fix
Provider provider = providerMapper.selectByIdIncludeDeleted(agent.getDefaultProviderId());
if (provider != null) {
    response.setDefaultProvider(ProviderBasic.builder()
        .id(provider.getId())
        .name(provider.getName())
        .type(provider.getType())
        .baseUrl(provider.getBaseUrl())
        .deleted(provider.getDeleted())  // so frontend knows it's deleted
        .build());
    response.setDefaultProviderName(provider.getName());
}
```

**AgentServiceImpl.toBasicResponse():**
```java
// After fix — batch-resolve provider names for all agents in list
Set<Long> providerIds = agents.stream()
    .map(Agent::getDefaultProviderId)
    .filter(Objects::nonNull)
    .collect(Collectors.toSet());
Map<Long, Provider> providerMap = providerMapper.selectByIdsIncludeDeleted(providerIds)
    .stream().collect(Collectors.toMap(Provider::getId, Function.identity()));
// In the response builder:
agentResponse.setDefaultProviderName(
    Optional.ofNullable(providerMap.get(agent.getDefaultProviderId()))
        .map(Provider::getName).orElse(null));
```

**WorkflowServiceImpl.toNodeDetail():**
```java
// After fix — resolve providerId from config JSON
if (n.getConfig() != null && n.getConfig().has("providerId")) {
    Long providerId = n.getConfig().get("providerId").asLong();
    Provider provider = providerMapper.selectByIdIncludeDeleted(providerId);
    if (provider != null) {
        ObjectNode config = (ObjectNode) n.getConfig();
        config.put("providerName", provider.getName());
        config.put("providerDeleted", provider.getDeleted() == 1);
    }
}
```

### Frontend: Display deleted indicator

```vue
<!-- AgentList.vue — provider column -->
<template v-if="row.defaultProviderName">
  <span v-if="row.defaultProvider?.deleted" class="text-muted">
    {{ row.defaultProviderName }} (deleted)
  </span>
  <span v-else>{{ row.defaultProviderName }}</span>
</template>
<template v-else>
  <span class="text-muted">—</span>
</template>
```

## Environment

| Info | Detail |
|:---|:---|
| **Deployment** | Docker / Local Dev |
| **Affected Version** | 1.0.0-SNAPSHOT (current main branch) |
| **Database** | MySQL 8.x |
| **Discovery Method** | Manual testing — delete provider while referenced by Agent/Workflow |

## Additional Context

- The `@TableLogic` annotation in `BaseEntity.java` (line 48) auto-filters `deleted=1` rows from ALL MyBatis-Plus queries, making soft-deleted providers invisible to standard mapper methods
- Referential integrity for soft-deleted references is not currently enforced — Agent and Workflow LLM nodes only store numeric provider IDs with no cascade or constraint
- Similar issue may exist for other soft-deleted entities referenced by Agent (e.g., Knowledge base, MCP Server) — should be verified as follow-up
- The batch-resolution approach in `toBasicResponse()` avoids N+1 queries by collecting all provider IDs first and doing a single `SELECT ... WHERE id IN (...)` query
