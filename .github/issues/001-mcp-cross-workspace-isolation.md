# MCP Client Queries Missing Workspace Isolation

## Description

`McpClientServiceImpl.getServer()` and `ChatServiceImpl.findServerIdForTool()` do not filter by `workspace_id` when querying MCP Servers and MCP Tools. An attacker can call MCP Servers belonging to other tenants by tampering with the `serverId` or tool name, leading to cross-workspace data leakage.

**Two affected code locations:**

1. **`McpClientServiceImpl.getServer()`** (`eify-mcp/.../McpClientServiceImpl.java:287-293`) — calls `mcpServerMapper.selectById(serverId)` directly, without workspace filtering. This method is invoked by `callToolWithRetry()` (:96) and `listToolsWithRetry()` (:131).

2. **`ChatServiceImpl.findServerIdForTool()`** (`eify-chat/.../ChatServiceImpl.java:1024-1038`) — queries `mcpToolMapper` by `toolName` only, without restricting by `workspace_id`. Additionally, the `McpTool` entity does not map the `workspace_id` field, making it impossible to add filtering even if the code wanted to.

## Steps to Reproduce

### Scenario A: Cross-workspace MCP tool invocation

1. In Workspace A, create an Agent and bind it to an MCP tool (e.g., `weather_query`)
2. Note the MCP Server's `serverId` in Workspace A (e.g., 123)
3. Switch to Workspace B (a different tenant)
4. Initiate a conversation in Workspace B that triggers a tool call
5. Intercept the request and tamper `serverId` to 123
6. Workspace B's Agent successfully calls Workspace A's MCP Server, returning Workspace A's data

### Scenario B: Same-name tool conflict across workspaces

1. Workspace A and Workspace B each create a tool named `get_customer_data`, but pointing to different internal APIs
2. Workspace A's Agent initiates a conversation that triggers `get_customer_data`
3. `findServerIdForTool()` finds Workspace B's tool record (same name), returns Workspace B's serverId
4. The Agent calls the wrong workspace's MCP Server, retrieving another tenant's data

## Expected Behavior

- `getServer()` should add workspace filtering: `.eq(McpServer::getWorkspaceId, CurrentContext.getWorkspaceId())`
- `findServerIdForTool()` should add workspace filtering, and the `McpTool` entity should map the `workspaceId` field
- Cross-workspace access should return `MCP_SERVER_NOT_FOUND` (without leaking specific error details to avoid information disclosure)

## Impact Analysis

| Call Chain | Risk |
|:---|:---|
| `ChatServiceImpl.executeToolSafely()` → `McpClientServiceImpl.callTool(serverId, ...)` → `callToolWithRetry()` → **`getServer(serverId)`** | Direct: tamper serverId to call across workspaces |
| `McpServerServiceImpl` → `McpClientServiceImpl.listTools(serverId)` → **`getServer(serverId)`** | Indirect: must first bypass Controller-layer workspace check |
| `ChatServiceImpl.executeToolSafely()` → **`findServerIdForTool(toolName, ...)`** → `mcpToolMapper.selectList(name)` | Indirect: same-name tool collision may hit wrong workspace |

## Suggested Fix

```java
// McpClientServiceImpl.getServer() — after fix
private McpServer getServer(Long serverId) {
    McpServer server = mcpServerMapper.selectOne(
        new LambdaQueryWrapper<McpServer>()
            .eq(McpServer::getId, serverId)
            .eq(McpServer::getWorkspaceId, CurrentContext.getWorkspaceId()));
    if (server == null) {
        throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
    }
    return server;
}

// ChatServiceImpl.findServerIdForTool() — after fix
private Long findServerIdForTool(String toolName, List<ToolDefinition> toolDefs) {
    try {
        List<McpTool> tools = mcpToolMapper.selectList(
            new LambdaQueryWrapper<McpTool>()
                .eq(McpTool::getName, toolName)
                .eq(McpTool::getWorkspaceId, CurrentContext.getWorkspaceId()));
        if (tools != null && !tools.isEmpty()) {
            return tools.get(0).getServerId();
        }
    } catch (Exception e) {
        log.warn("[ChatService] Failed to find tool server ID: toolName={}, error={}", toolName, e.getMessage());
    }
    return null;
}
```

**Prerequisite**: `McpTool` entity must map the `workspaceId` field:

```java
@TableField("workspace_id")
private Long workspaceId;
```

## Environment

| Info | Detail |
|:---|:---|
| **Deployment** | Docker / Local Dev |
| **Affected Version** | 1.0.0-SNAPSHOT (current main branch) |
| **Database** | MySQL 8.x |
| **Discovery Method** | Code review — workspace isolation audit |

## Additional Context

- This issue violates the 🔴 workspace data isolation constraint in [CLAUDE.md core constraints](https://github.com/.../CLAUDE.md#core-constraints)
- The `mcp_tool` table (V1__init.sql:391-407) already has a `workspace_id` column, but `McpTool.java` entity does not map it, making it inaccessible from the Java layer
- The `mcp_tool` table is missing `idx_workspace_id` index; after adding workspace filtering, an index should be added to ensure query performance
