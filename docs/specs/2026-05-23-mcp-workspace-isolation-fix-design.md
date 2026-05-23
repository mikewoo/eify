# MCP 模块工作空间隔离修复

## Status

Approved

## Date

2026-05-23

## Owner

mingming

## Deciders

mingming

## Context

安全审计发现 MCP 模块存在多处工作空间隔离漏洞。`McpClientServiceImpl.getServer()` 和 `ChatServiceImpl.findServerIdForTool()` 查询时未按 `workspace_id` 过滤，且 `McpTool`、`AgentMcpTool` 实体未映射数据库已有的 `workspace_id` 列。详见 `.github/issues/001-mcp-cross-workspace-isolation.md`。

深入排查后共发现 4 层 8 处缺口。

## Decision

### 实体层

**`McpTool.java`** — 添加字段 + 实现接口：

```java
@TableField("workspace_id")
private Long workspaceId;
// implements WorkspaceAware
```

**`AgentMcpTool.java`** — 同上。

### 查询与写入

| # | 文件 | 方法/位置 | 修复方式 |
|:---|:---|:---|:---|
| 1 | `McpClientServiceImpl` | `getServer()` | `selectById` + `WorkspaceGuard.requireInWorkspace()` |
| 2 | `ChatServiceImpl` | `findServerIdForTool()` | 手动 `.eq(McpTool::getWorkspaceId, CurrentContext.getWorkspaceId())` |
| 3 | `McpServerServiceImpl` | 工具刷新（创建 McpTool） | `WorkspaceGuard.bind(tool)` |
| 4 | `McpServerServiceImpl` | `refreshTools()` 内 `mcpToolMapper.selectList` | 手动加 workspace 过滤（防御深度） |
| 5 | `McpServerServiceImpl` | `deleteServer()` 内 `agentMcpToolMapper.selectCount` | 手动加 workspace 过滤 |
| 6 | `AgentMcpToolMapper` | `batchInsert` | SQL 加 `workspace_id = #{workspaceId}` 参数 |
| 7 | `AgentMcpToolMapper` | `deleteByAgentId` | SQL 加 `AND workspace_id = #{workspaceId}` |
| 8 | `AgentMcpToolMapper` | `selectToolIdsByAgentId` | SQL 加 `AND workspace_id = #{workspaceId}` |
| 9 | `AgentMcpToolMapper` | `selectByAgentIds` | SQL 加 `AND workspace_id = #{workspaceId}` |
| 10 | `AgentServiceImpl` | `validateAndInsertMcpTools()` | `selectById` + `WorkspaceGuard.requireInWorkspace()` |

### 数据库迁移 (V6)

```sql
-- mcp_tool: idx_workspace_id 索引
-- agent_mcp_tool: idx_workspace_id 索引
-- agent_mcp_tool: uk_agent_tool (agent_id, tool_id) → (agent_id, tool_id, workspace_id)
-- mcp_tool: idx_name_workspace 联合索引（findServerIdForTool 按名称+workspace 查询需要）
```

所有 DDL 使用 INFORMATION_SCHEMA 检查模板确保幂等。

### 测试用例 — 6 场景 13 用例

**Scenario A — 跨空间篡改 serverId（P0）**

| # | 描述 | 验证点 |
|:---|:---|:---|
| A1 | 空间 B 传空间 A 的 serverId 调 `callTool` → 拒绝 | `MCP_SERVER_NOT_FOUND` |
| A2 | 同上场景调 `listTools` → 拒绝 | `MCP_SERVER_NOT_FOUND` |

**Scenario B — 同名工具跨空间碰撞（P0）**

| # | 描述 | 验证点 |
|:---|:---|:---|
| B1 | 空间 A/B 各有 `get_data`，B 中查询只返回 B 的 | serverId 正确 |
| B2 | 工具仅存在于 A，B 中查询返回 null | 不串到其他空间 |

**Scenario C — 跨空间 toolId 绑定（P0）**

| # | 描述 | 验证点 |
|:---|:---|:---|
| C1 | 空间 B 更新 Agent 提交空间 A 的 toolId → 拒绝 | `MCP_TOOL_NOT_FOUND` |
| C2 | 校验 server 时同样按 workspace 过滤 | `MCP_SERVER_NOT_FOUND` |

**Scenario D — 工具刷新写入 workspaceId（P1）**

| # | 描述 | 验证点 |
|:---|:---|:---|
| D1 | 刷新后 McpTool 的 workspaceId = 当前上下文 | 写入正确 |
| D2 | 工具列表查询仅返回当前空间 | 过滤生效 |

**Scenario E — AgentMcpTool 映射表（P1）**

| # | 描述 | 验证点 |
|:---|:---|:---|
| E1 | `batchInsert` 写入正确 workspace_id | DB 记录 |
| E2 | `selectToolIdsByAgentId` 过滤 workspace | 只返回当前空间 |
| E3 | `deleteByAgentId` 只删当前空间 | 不误删 |
| E4 | `selectByAgentIds` 过滤 workspace | 批量查询隔离 |

**Scenario F — findServerIdForTool 回归（P1）**

| # | 描述 | 验证点 |
|:---|:---|:---|
| F1 | `ChatServiceImpl` 内 findServerIdForTool 正常匹配 + workspace 过滤 | 工具查找正确 |

## Consequences

- 所有 MCP 操作（调用、列表、绑定、刷新）均受 workspace 隔离保护
- 数据库层面添加索引和联合唯一约束，防止跨空间数据碰撞
- `McpTool` 和 `AgentMcpTool` 需实现 `WorkspaceAware`，与 `McpServer` 保持一致
- 向后兼容：当前 `mcp_tool` 表已有 `workspace_id` 列（默认值 1），仅映射到实体

## Details

### 执行步骤（7 步，按依赖顺序）

1. **Flyway V6 迁移** — 索引和唯一键先行
2. **`McpTool` + `AgentMcpTool` 实体** — 加 `workspaceId` 字段 + `implements WorkspaceAware`
3. **`AgentMcpToolMapper`** — 4 个 SQL 加 `workspaceId` 参数
4. **`McpServerServiceImpl`** — 查询加 workspace 过滤 + 创建用 `WorkspaceGuard.bind`
5. **`McpClientServiceImpl`** — `getServer()` 用 `requireInWorkspace`
6. **`AgentServiceImpl`** — `validateAndInsertMcpTools()` 用 `requireInWorkspace`
7. **`ChatServiceImpl`** — `findServerIdForTool()` 加 workspace 过滤

### 受影响文件

```
eify-app/src/main/resources/db/migration/V6__mcp_workspace_isolation.sql  (新增)
eify-mcp/src/main/java/com/eify/mcp/domain/entity/McpTool.java
eify-mcp/src/main/java/com/eify/mcp/domain/entity/AgentMcpTool.java
eify-mcp/src/main/java/com/eify/mcp/mapper/AgentMcpToolMapper.java
eify-mcp/src/main/java/com/eify/mcp/service/impl/McpServerServiceImpl.java
eify-mcp/src/main/java/com/eify/mcp/service/impl/McpClientServiceImpl.java
eify-mcp/src/test/java/com/eify/mcp/service/impl/McpClientServiceImplTest.java
eify-mcp/src/test/java/com/eify/mcp/service/impl/McpServerServiceImplTest.java
eify-agent/src/main/java/com/eify/agent/service/impl/AgentServiceImpl.java
eify-agent/src/test/java/com/eify/agent/service/impl/AgentServiceImplTest.java
eify-chat/src/main/java/com/eify/chat/service/impl/ChatServiceImpl.java
```

### 验证命令

```bash
mvn compile -q
mvn test -pl eify-mcp,eify-agent,eify-chat -am -q
```