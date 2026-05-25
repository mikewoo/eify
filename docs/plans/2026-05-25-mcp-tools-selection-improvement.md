# MCP Tools 选择体验优化 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Agent 编辑页的 MCP Tools 标签页中增加 Server 状态/endpoint/工具数展示，以及工具 `inputSchema` 参数预览，同时用批量 API 替代当前 N+1 查询。

**Architecture:** 后端新增 `GET /api/v1/mcp-servers/tools` 批量接口，一次返回所有 Server + 工具 + `online` 状态。前端用 `getToolsByServer()` 替换 `getList()` + 循环 `getById()`，模板增加 Status Dot、endpoint tag、工具参数展开面板。

**Tech Stack:** Spring Boot + MyBatis Plus（后端），Vue 3 + TypeScript + Element Plus（前端），Vitest + Mockito（测试）

---

## Task 1: McpServerResponse 增加 `online` 字段

**Files:**
- Modify: `eify-mcp/src/main/java/com/eify/mcp/domain/dto/McpServerResponse.java`

- [ ] **Step 1: 添加 `online` 字段**

在 `McpServerResponse` 类的 `enabled` 字段后添加：

```java
@Schema(description = "服务器是否在线（缓存状态或 enabled 推断）")
private Boolean online;
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl eify-mcp -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add eify-mcp/src/main/java/com/eify/mcp/domain/dto/McpServerResponse.java
git commit -m "feat: add online field to McpServerResponse"
```

---

## Task 2: McpServerServiceImpl 增加 `listToolsByWorkspace` 方法

**Files:**
- Modify: `eify-mcp/src/main/java/com/eify/mcp/service/McpServerService.java`
- Modify: `eify-mcp/src/main/java/com/eify/mcp/service/impl/McpServerServiceImpl.java`

- [ ] **Step 1: 在 Service 接口添加方法签名**

在 `McpServerService.java` 的 `list` 方法后添加：

```java
/**
 * 批量查询当前工作空间下所有 Server 及其工具列表（含 online 状态）。
 *
 * @param enabled 筛选 enabled 状态（null=全部，1=仅启用）
 * @return Server + 工具完整信息列表
 */
List<McpServerResponse> listToolsByWorkspace(Integer enabled);
```

- [ ] **Step 2: 在 ServiceImpl 添加实现**

在 `McpServerServiceImpl.java` 的 `list` 方法后添加：

```java
@Override
public List<McpServerResponse> listToolsByWorkspace(Integer enabled) {
    Long workspaceId = CurrentContext.getWorkspaceId();
    LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
            .eq(McpServer::getWorkspaceId, workspaceId)
            .orderByAsc(McpServer::getName);
    if (enabled != null) {
        wrapper.eq(McpServer::getEnabled, enabled);
    }
    List<McpServer> servers = mcpServerMapper.selectList(wrapper);
    if (servers.isEmpty()) {
        return List.of();
    }

    // 批量查询所有工具
    List<Long> serverIds = servers.stream().map(McpServer::getId).collect(Collectors.toList());
    List<McpTool> allTools = mcpToolMapper.selectList(
            new LambdaQueryWrapper<McpTool>()
                    .in(McpTool::getServerId, serverIds)
                    .eq(McpTool::getWorkspaceId, workspaceId));

    // 按 serverId 分组
    Map<Long, List<McpTool>> toolsByServer = allTools.stream()
            .collect(Collectors.groupingBy(McpTool::getServerId));

    return servers.stream()
            .map(server -> toToolsListResponse(server, toolsByServer.getOrDefault(server.getId(), List.of()), workspaceId))
            .collect(Collectors.toList());
}
```

- [ ] **Step 3: 添加私有转换方法 `toToolsListResponse`**

在 `McpServerServiceImpl.java` 的 `toFullResponse` 方法后添加：

```java
private McpServerResponse toToolsListResponse(McpServer server, List<McpTool> tools, Long workspaceId) {
    // online 状态：取连接缓存，无缓存则用 enabled 推断
    boolean online = clientCacheContains(server.getId());
    if (!online && server.getEnabled() == 1) {
        online = true; // enabled 但未缓存 → 假定在线
    }

    List<McpServerResponse.McpToolResponse> toolResponses = tools.stream()
            .map(t -> McpServerResponse.McpToolResponse.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .description(t.getDescription())
                    .inputSchema(t.getInputSchema())
                    .build())
            .collect(Collectors.toList());

    return McpServerResponse.builder()
            .id(server.getId())
            .name(server.getName())
            .endpoint(server.getEndpoint())
            .enabled(server.getEnabled())
            .online(online)
            .toolCount(toolResponses.size())
            .tools(toolResponses)
            .createdAt(server.getCreatedAt())
            .updatedAt(server.getUpdatedAt())
            .build();
}
```

- [ ] **Step 4: 在 McpClientServiceImpl 中暴露缓存查询方法**

在 `McpClientServiceImpl.java` 添加 package-private 方法：

```java
/**
 * 检查指定 serverId 的客户端是否在缓存中（即最近 5 分钟内连接成功过）。
 */
boolean clientCacheContains(Long serverId) {
    ClientEntry entry = clientCache.get(serverId);
    if (entry == null) return false;
    if (System.currentTimeMillis() - entry.createdAt > CLIENT_TTL_MS) {
        clientCache.remove(serverId);
        return false;
    }
    return true;
}
```

同时在 `McpServerServiceImpl` 中将构造函数注入的 `McpClientService` cast 为 `McpClientServiceImpl` 调用此方法。或者更好的做法：直接在 `McpClientService` 接口中添加 `isClientCached(Long serverId)` 方法。

修改 `McpClientService.java` 接口，添加：

```java
/**
 * 检查指定 serverId 的 MCP 客户端是否在连接缓存中（最近 5 分钟内连接成功过）。
 */
boolean isClientCached(Long serverId);
```

`McpClientServiceImpl.java` 中实现：

```java
@Override
public boolean isClientCached(Long serverId) {
    ClientEntry entry = clientCache.get(serverId);
    if (entry == null) return false;
    if (System.currentTimeMillis() - entry.createdAt > CLIENT_TTL_MS) {
        clientCache.remove(serverId);
        return false;
    }
    return true;
}
```

- [ ] **Step 5: 编译验证**

```bash
mvn compile -pl eify-mcp -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add eify-mcp/src/main/java/com/eify/mcp/service/McpServerService.java \
        eify-mcp/src/main/java/com/eify/mcp/service/impl/McpServerServiceImpl.java \
        eify-mcp/src/main/java/com/eify/mcp/service/McpClientService.java \
        eify-mcp/src/main/java/com/eify/mcp/service/impl/McpClientServiceImpl.java
git commit -m "feat: add listToolsByWorkspace method with batch tool query and online status"
```

---

## Task 3: McpServerController 新增 `/tools` 端点

**Files:**
- Modify: `eify-mcp/src/main/java/com/eify/mcp/controller/McpServerController.java`

- [ ] **Step 1: 添加端点方法**

在 `McpServerController.java` 的 `getById` 方法后添加：

```java
@Operation(summary = "批量获取 Server 及工具列表", description = "一次性返回当前工作空间所有 Server 及其工具（含 online 状态和 inputSchema），用于 Agent 编辑页工具选择。")
@GetMapping("/tools")
public Result<List<McpServerResponse>> listTools(
        @Parameter(description = "筛选启用状态：1=仅启用，不传=全部") @RequestParam(required = false) Integer enabled) {
    return Result.success(mcpServerService.listToolsByWorkspace(enabled));
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl eify-mcp -am -q
```

- [ ] **Step 3: Commit**

```bash
git add eify-mcp/src/main/java/com/eify/mcp/controller/McpServerController.java
git commit -m "feat: add GET /api/v1/mcp-servers/tools endpoint"
```

---

## Task 4: 后端测试

**Files:**
- Modify: `eify-mcp/src/test/java/com/eify/mcp/service/impl/McpServerServiceImplTest.java`
- Create: `eify-mcp/src/test/java/com/eify/mcp/controller/McpServerControllerTest.java`

- [ ] **Step 1: 为 `McpServerServiceImpl` 添加 `listToolsByWorkspace` 测试**

在 `McpServerServiceImplTest.java` 中添加 `@Nested` 测试组：

```java
@Nested
@DisplayName("listToolsByWorkspace")
class ListToolsByWorkspaceTests {

    @Test
    @DisplayName("按 enabled=1 过滤，仅返回启用的 Server 及其工具")
    void shouldFilterByEnabled() {
        McpServer s1 = buildServer(1L, "知识检索", "http://kb:8080", 1L);
        s1.setEnabled(1);
        McpServer s2 = buildServer(2L, "数据服务", "http://data:9090", 1L);
        s2.setEnabled(0);

        McpTool tool1 = buildTool(10L, 1L, "search");
        tool1.setDescription("搜索");
        tool1.setWorkspaceId(1L);
        McpTool tool2 = buildTool(11L, 1L, "get-doc");
        tool2.setDescription("获取文档");
        tool2.setWorkspaceId(1L);

        when(mcpServerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(s1));
        when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(tool1, tool2));
        when(mcpClientService.isClientCached(eq(1L))).thenReturn(false);

        List<McpServerResponse> result = mcpServerService.listToolsByWorkspace(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getOnline()).isTrue(); // enabled=1 fallback
        assertThat(result.get(0).getToolCount()).isEqualTo(2);
        assertThat(result.get(0).getTools()).hasSize(2);
        assertThat(result.get(0).getTools().get(0).getName()).isEqualTo("search");
        assertThat(result.get(0).getTools().get(0).getDescription()).isEqualTo("搜索");
    }

    @Test
    @DisplayName("enabled=0 的 Server 即使被查询，online 应为 false")
    void shouldMarkDisabledAsOffline() {
        McpServer s1 = buildServer(1L, "离线服务", "http://off:8080", 1L);
        s1.setEnabled(0);

        when(mcpServerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(s1));
        when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        when(mcpClientService.isClientCached(eq(1L))).thenReturn(false);

        List<McpServerResponse> result = mcpServerService.listToolsByWorkspace(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOnline()).isFalse();
        assertThat(result.get(0).getTools()).isEmpty();
    }

    @Test
    @DisplayName("缓存中有连接记录时 online 为 true")
    void shouldUseCacheForOnlineStatus() {
        McpServer s1 = buildServer(1L, "在线服务", "http://on:8080", 1L);
        s1.setEnabled(1);

        when(mcpServerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(s1));
        when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());
        when(mcpClientService.isClientCached(eq(1L))).thenReturn(true);

        List<McpServerResponse> result = mcpServerService.listToolsByWorkspace(1);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOnline()).isTrue();
    }

    @Test
    @DisplayName("无符合条件的 Server 时应返回空列表")
    void shouldReturnEmptyList() {
        when(mcpServerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of());

        List<McpServerResponse> result = mcpServerService.listToolsByWorkspace(1);

        assertThat(result).isEmpty();
        verify(mcpToolMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("工具按 serverId 正确分组")
    void shouldGroupToolsByServerId() {
        McpServer s1 = buildServer(1L, "S1", "http://s1:8080", 1L);
        s1.setEnabled(1);
        McpServer s2 = buildServer(2L, "S2", "http://s2:9090", 1L);
        s2.setEnabled(1);

        McpTool t1 = buildTool(10L, 1L, "a");
        t1.setWorkspaceId(1L);
        McpTool t2 = buildTool(20L, 2L, "b");
        t2.setWorkspaceId(1L);
        McpTool t3 = buildTool(30L, 2L, "c");
        t3.setWorkspaceId(1L);

        when(mcpServerMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(s1, s2));
        when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(t1, t2, t3));
        when(mcpClientService.isClientCached(anyLong())).thenReturn(false);

        List<McpServerResponse> result = mcpServerService.listToolsByWorkspace(1);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTools()).hasSize(1);
        assertThat(result.get(1).getTools()).hasSize(2);
    }
}
```

- [ ] **Step 2: 运行测试验证通过**

```bash
mvn test -pl eify-mcp -am -q
```

Expected: Tests run with 0 failures

- [ ] **Step 3: 创建 `McpServerControllerTest.java`**

```java
package com.eify.mcp.controller;

import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.service.McpServerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpServerController")
class McpServerControllerTest {

    @Mock McpServerService mcpServerService;

    @InjectMocks McpServerController controller;

    @Nested
    @DisplayName("GET /api/v1/mcp-servers/tools")
    class ListToolsTests {

        @Test
        @DisplayName("enabled=1 参数透传给 Service")
        void shouldPassEnabledParam() {
            McpServerResponse resp = McpServerResponse.builder().id(1L).name("S1").online(true).tools(List.of()).build();
            when(mcpServerService.listToolsByWorkspace(eq(1))).thenReturn(List.of(resp));

            var result = controller.listTools(1);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getName()).isEqualTo("S1");
            verify(mcpServerService).listToolsByWorkspace(1);
        }

        @Test
        @DisplayName("不传 enabled 时传 null")
        void shouldPassNullWhenNoParam() {
            when(mcpServerService.listToolsByWorkspace(isNull())).thenReturn(List.of());

            var result = controller.listTools(null);

            assertThat(result.getData()).isEmpty();
            verify(mcpServerService).listToolsByWorkspace(null);
        }
    }
}
```

- [ ] **Step 4: 运行全部测试**

```bash
mvn test -pl eify-mcp -am -q
```

Expected: All tests pass, 0 failures

- [ ] **Step 5: Commit**

```bash
git add eify-mcp/src/test/java/com/eify/mcp/service/impl/McpServerServiceImplTest.java \
        eify-mcp/src/test/java/com/eify/mcp/controller/McpServerControllerTest.java
git commit -m "test: add tests for listToolsByWorkspace and /tools endpoint"
```

---

## Task 5: 前端 API 层 — `mcp.ts` 新增批量接口

**Files:**
- Modify: `eify-web/src/api/mcp.ts`

- [ ] **Step 1: 添加类型和 API 方法**

在 `McpServerResponse` 接口后添加：

```typescript
/** 批量获取 Server + 工具完整信息（含 online 状态和 inputSchema） */
export interface McpServerToolsResponse {
  id: number
  name: string
  endpoint: string
  enabled: number
  online: boolean
  toolCount: number
  tools: McpToolOption[]
  createdAt: string
  updatedAt: string
}

/** 工具选项（含所属 Server 信息和 inputSchema） */
export interface McpToolOption {
  id: number
  name: string
  description: string
  serverName: string
  serverId: number
  inputSchema: Record<string, any> | null
}
```

在 `mcpApi` 对象的 `getById` 方法后添加：

```typescript
/** 批量获取所有 Server 及其工具（含 online 状态、endpoint、inputSchema） */
getToolsByServer: (params?: { enabled?: number }) =>
  get<McpServerToolsResponse[]>('/api/v1/mcp-servers/tools', { params }),
```

- [ ] **Step 2: TypeScript 类型检查**

```bash
cd eify-web && npx vue-tsc --noEmit && cd ..
```

Expected: 无新增类型错误

- [ ] **Step 3: Commit**

```bash
git add eify-web/src/api/mcp.ts
git commit -m "feat: add getToolsByServer API and McpServerToolsResponse type"
```

---

## Task 6: 前端 AgentList.vue — 模板 + 脚本 + 样式

**Files:**
- Modify: `eify-web/src/views/AgentList.vue`
- Modify: `eify-web/src/i18n/locales/zh-CN.json`
- Modify: `eify-web/src/i18n/locales/en-US.json`

- [ ] **Step 1: 替换 `McpToolOption` 类型定义（~line 706）**

```typescript
interface McpToolOption {
  id: number
  name: string
  description: string
  serverName: string
  serverId: number
  inputSchema: Record<string, any> | null
}
```

- [ ] **Step 2: 添加 Server 数据结构和新响应式状态**

在 `mcpToolOptions` 附近添加：

```typescript
interface McpServerWithTools {
  id: number
  name: string
  endpoint: string
  enabled: number
  online: boolean
  toolCount: number
  tools: McpToolOption[]
}

const mcpServers = ref<McpServerWithTools[]>([])
const expandedToolId = ref<number | null>(null)

function toggleExpand(toolId: number) {
  expandedToolId.value = expandedToolId.value === toolId ? null : toolId
}

function isRequired(required: string[] | undefined, key: string): boolean {
  return required?.includes(key) ?? false
}
```

- [ ] **Step 3: 重写 `loadMcpTools` 方法**

替换原有的 N+1 查询逻辑：

```typescript
const loadMcpTools = async () => {
  try {
    const servers = await mcpApi.getToolsByServer({ enabled: 1 })
    mcpServers.value = servers
  } catch (error) {
    console.error('Failed to load MCP tools:', error)
  }
}
```

- [ ] **Step 4: 移除 `groupedMcpTools` 计算属性，替换为 `mcpServers`**

删除 `groupedMcpTools` computed。模板中直接用 `mcpServers` 迭代。

- [ ] **Step 5: 更新模板 MCP Tools 部分（~line 420-448）**

```html
<el-tab-pane :label="t('agent.mcpTools')" name="tools">
  <div v-if="mcpServers.length === 0" class="empty-tools">
    <el-empty :description="t('agent.noToolsAvailable')" :image-size="80" />
  </div>
  <div v-else class="tools-wrapper">
    <div class="tools-hint">{{ t('agent.toolsHint') }}</div>
    <el-checkbox-group v-model="data.mcpToolIds" :max="10">
      <div v-for="server in mcpServers" :key="server.id" class="tool-group">
        <div class="tool-group-header">
          <span class="eify-status-dot" :class="server.online ? 'online' : 'offline'" />
          <span class="server-name">{{ server.name }}</span>
          <span class="eify-tag eify-tag-gray server-endpoint">{{ server.endpoint }}</span>
          <span class="server-tool-count">{{ server.toolCount }} {{ t('agent.toolsCount') }}</span>
        </div>
        <div v-for="tool in server.tools" :key="tool.id"
             class="tool-item"
             :class="{ 'tool-disabled': !server.online }">
          <el-checkbox :value="tool.id" :label="tool.id" :disabled="!server.online">
            <span class="tool-name">{{ tool.name }}</span>
          </el-checkbox>
          <span class="tool-desc" :title="tool.description">{{ tool.description }}</span>
          <button v-if="tool.inputSchema && tool.inputSchema.properties"
                  class="eify-btn eify-btn-text eify-btn-sm tool-expand-btn"
                  type="button"
                  @click="toggleExpand(tool.id)">
            {{ expandedToolId === tool.id ? t('common.collapse') : t('agent.expandParams') }}
          </button>
        </div>
        <!-- 参数预览 -->
        <div v-if="expandedToolId === tool.id && tool.inputSchema?.properties" class="tool-params">
          <div class="params-header">{{ t('agent.paramsHint') }}</div>
          <div v-for="(param, key) in tool.inputSchema.properties" :key="key" class="param-row">
            <span class="param-name">{{ key }}</span>
            <span class="param-type">{{ param.type || '-' }}</span>
            <span :class="isRequired(tool.inputSchema.required, key) ? 'param-required' : 'param-optional'">
              {{ isRequired(tool.inputSchema.required, key) ? t('agent.required') : t('agent.optional') }}
            </span>
            <span class="param-desc">{{ param.description || '-' }}</span>
          </div>
        </div>
      </div>
    </el-checkbox-group>
  </div>
</el-tab-pane>
```

- [ ] **Step 6: 处理模板中使用 `mcpToolOptions` 的其他位置**

`mcpToolOptions` 目前还用于 `empty-tools` 判断。将其改为 `mcpServers`。

- [ ] **Step 7: 更新样式（~line 1715-1776）**

保留现有样式，在 `.tool-desc` 之后添加新样式：

```css
/* Server 分组标题增强 */
.server-name {
  font-weight: 600;
}

.server-endpoint {
  flex-shrink: 0;
}

.server-tool-count {
  margin-left: auto;
  font-weight: 400;
  color: var(--eify-text-tertiary);
}

/* 离线工具项 */
.tool-item.tool-disabled {
  opacity: 0.5;
}

/* 展开按钮 */
.tool-expand-btn {
  flex-shrink: 0;
  margin-left: auto;
}

/* 参数预览面板 */
.tool-params {
  margin: 4px 12px 12px 44px;
  padding: 12px;
  background: var(--eify-bg-subtle);
  border: 1px solid var(--eify-border-subtle);
  border-radius: var(--eify-radius-sm);
}

.params-header {
  font-weight: 600;
  color: var(--eify-text-secondary);
  margin-bottom: 8px;
}

.param-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 4px 0;
}

.param-name {
  font-weight: 500;
  color: var(--eify-text-primary);
  min-width: 80px;
}

.param-type {
  display: inline-block;
  padding: 1px 6px;
  background: var(--eify-bg-surface);
  border-radius: var(--eify-radius-xs);
  color: var(--eify-text-secondary);
  font-family: 'SF Mono', Monaco, monospace;
  font-size: var(--eify-font-size-xs);
}

.param-required {
  color: var(--eify-error);
  font-weight: 500;
}

.param-optional {
  color: var(--eify-text-tertiary);
}

.param-desc {
  color: var(--eify-text-secondary);
  flex: 1;
}
```

- [ ] **Step 8: 添加 i18n keys**

`zh-CN.json` 添加：
```json
"toolsCount": "个工具",
"expandParams": "参数",
"paramsHint": "参数说明",
"required": "必填",
"optional": "可选"
```

`en-US.json` 添加：
```json
"toolsCount": "tools",
"expandParams": "Params",
"paramsHint": "Parameters",
"required": "Required",
"optional": "Optional"
```

- [ ] **Step 9: 验证**

```bash
cd eify-web && npx vue-tsc --noEmit && npx vitest run && cd ..
```

Expected: 类型检查通过，测试通过

- [ ] **Step 10: Commit**

```bash
git add eify-web/src/views/AgentList.vue \
        eify-web/src/i18n/locales/zh-CN.json \
        eify-web/src/i18n/locales/en-US.json
git commit -m "feat: add server info display and tool param preview to MCP Tools tab"
```

---

## Task 7: 全量集成验证

- [ ] **Step 1: 后端全部测试**

```bash
mvn test -q
```

Expected: All tests pass

- [ ] **Step 2: 前端类型检查 + 测试**

```bash
cd eify-web && npx vue-tsc --noEmit && npx vitest run && cd ..
```

Expected: 类型检查通过，测试通过

- [ ] **Step 3: 功能自检列表**

| 功能点 | 预期 |
|:---|:---|
| 打开 Agent 编辑 → MCP Tools 标签 | Server 按名称排序展示，带状态灯（在线绿/离线灰） |
| Server 分组标题 | 显示 endpoint tag + 工具数量 |
| 在线 Server 的工具 | checkbox 可选 |
| 离线 Server 的工具 | checkbox 禁用 + 半透明 |
| 点击工具 `参数` 按钮 | 展开 inputSchema 参数表（名称/类型/必填/说明） |
| 再次点击 `参数` 按钮 | 收起参数表 |
| `mcpToolIds` 提交 | 仅选中工具的 ID 提交，与之前一致 |

- [ ] **Step 4: Commit (如有修复)**

```bash
git add <fixed-files>
git commit -m "fix: integration tweaks for MCP tools improvement"
```
