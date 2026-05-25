# MCP Tools 选择体验优化 — 增加 Server 信息展示与工具参数预览

> 在设计回顾中鉴定出的需求。添加/编辑 Agent 时，MCP Tools 标签页展示 MCP Server 名称及基本信息（状态、endpoint、工具数量），并为每个工具提供 `inputSchema` 参数预览展开功能，帮助用户选择合适的工具。

**目标：** 增强 MCP 工具选择页的信息密度，让用户一眼了解每个工具所属的 Server、Server 是否在线、以及工具能接收哪些参数。

**方案：** 方案 C — 既有后端优化（新增批量接口），也有前端 Server 信息展示，还有工具 `inputSchema` 参数预览。

**技术栈：** Spring Boot（后端新 API 端点），Vue 3 + TypeScript（前端 UI 改造），复用已有设计系统（`--eify-*` CSS 变量和 `.eify-*` 组件类）。

---

## 1. 后端：新增批量工具查询接口

### 1.1 动机

当前 `AgentList.vue` 加载工具选项时，先调用 `GET /api/v1/mcp-servers?enabled=1` 获取 Server 列表，再对每个 Server 逐个调用 `GET /api/v1/mcp-servers/{id}` 获取详情（含 tools 数组）。这是典型的 N+1 查询，无缓存情况下会发出 N+1 个 HTTP 请求。

### 1.2 新接口

```
GET /api/v1/mcp-servers/tools?enabled=1
```

**请求参数：**

| 参数 | 类型 | 必填 | 说明 |
|:---|:---|:---|:---|
| `enabled` | int | 否 | 1=仅启用，不传=全部 |

**响应格式：**

```json
[
  {
    "id": 1,
    "name": "知识检索服务",
    "endpoint": "http://kb-mcp:8080",
    "enabled": 1,
    "online": true,
    "toolCount": 3,
    "tools": [
      {
        "id": 10,
        "name": "search",
        "description": "全文检索知识库内容",
        "inputSchema": {
          "type": "object",
          "properties": {
            "query": { "type": "string", "description": "搜索关键词" },
            "topK": { "type": "number", "description": "返回结果数量" }
          },
          "required": ["query"]
        }
      }
    ]
  }
]
```

### 1.3 变更清单

| 文件 | 变更 |
|:---|:---|
| `McpServerController` | 新增 `GET /api/v1/mcp-servers/tools` 端点 |
| `McpServerService` + `McpServerServiceImpl` | 新增 `listToolsByWorkspace(enabled)` 方法，按 `workspace_id` 过滤 |
| `McpServerResponse` | 新增 `online` (boolean) 字段 |

### 1.4 `online` 状态

不额外发起连接测试（避免打开表单响应慢）。优先取 `McpClientService` 已有连接缓存状态；若未缓存，则用 `enabled` 字段作为默认值（`enabled=1 → online=true`）。

---

## 2. 前端：AgentList.vue MCP Tools 标签页改造

### 2.1 API 层

`eify-web/src/api/mcp.ts` — 新增：

```typescript
export interface McpServerToolsResponse {
  id: number
  name: string
  endpoint: string
  enabled: number
  online: boolean
  toolCount: number
  tools: McpToolOption[]
}

export function getToolsByServer(params?: { enabled?: number }) {
  return get<McpServerToolsResponse[]>('/api/v1/mcp-servers/tools', { params })
}
```

### 2.2 数据加载

用 `mcpApi.getToolsByServer({ enabled: 1 })` 替换当前循环 `mcpApi.getById()`。`McpToolOption` 增加 `inputSchema` 字段：

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

### 2.3 模板改动

**Server 分组标题增强：**

```html
<div class="tool-group-header">
  <span class="eify-status-dot" :class="server.online ? 'online' : 'offline'" />
  <span class="server-name">{{ server.name }}</span>
  <span class="eify-tag eify-tag-gray server-endpoint">{{ server.endpoint }}</span>
  <span class="server-tool-count">{{ server.toolCount }} 个工具</span>
</div>
```

- `.eify-status-dot.online` — teal `#2dd4bf`，8×8px 圆点
- `.eify-status-dot.offline` — gray `#94a3b8`
- `.eify-tag.eify-tag-gray` — endpoint 标签

**工具展开按钮（仅 `inputSchema` 非空时显示）：**

```html
<button v-if="tool.inputSchema"
        class="eify-btn eify-btn-text eify-btn-sm"
        @click="toggleExpand(tool.id)">
  {{ expandedToolId === tool.id ? '收起' : '参数' }}
</button>
```

**参数预览区域（展开时显示）：**

```html
<div v-if="expandedToolId === tool.id && tool.inputSchema" class="tool-params">
  <div class="params-header">参数说明</div>
  <div v-for="(param, key) in tool.inputSchema.properties" :key="key" class="param-row">
    <span class="param-name">{{ key }}</span>
    <span class="param-type">{{ param.type }}</span>
    <span class="param-required"
          :class="isRequired(key) ? 'required' : 'optional'">
      {{ isRequired(key) ? '必填' : '可选' }}
    </span>
    <span class="param-desc">{{ param.description || '-' }}</span>
  </div>
</div>
```

**离线 Server 的工具项：** checkbox 禁用 + 降低不透明度（`opacity: 0.5`）。

### 2.4 样式约束（DESIGN.md 合规）

| 元素 | 令牌 / 类 |
|:---|:---|
| 状态点 | `.eify-status-dot` (已有组件) |
| Endpoint 标签 | `.eify-tag.eify-tag-gray` (已有组件) |
| 展开按钮 | `.eify-btn.eify-btn-text.eify-btn-sm` (已有组件) |
| 参数类型标签 | 等宽字体 `SF Mono / Monaco` + `var(--eify-radius-xs)` |
| 必填标记 | `var(--eify-error)` |
| 可选标记 | `var(--eify-text-tertiary)` |
| 参数表格背景 | `var(--eify-bg-subtle)` (`#fafafa`) |
| 离线工具 | `opacity: 0.5`（对齐 Button 禁用态规范） |
| 所有间距 | 对齐 4px 网格 |

**重要：** 必须使用 `var(--eify-*)` 引用颜色，禁止硬编码颜色值；使用 `.eify-*` 组件类；不在卡片上使用渐变。

---

## 3. 测试

| 模块 | 测试项 |
|:---|:---|
| `McpServerServiceImplTest` | 新增 `listToolsByWorkspace` 的集成测试：按 workspace_id 过滤、enabled 过滤、空结果 |
| `McpServerControllerTest` | 新端点请求参数解析、响应序列化验证 |
| `AgentList.vue` (Vitest) | `loadMcpTools` 数据加载、`toggleExpand` 展开/收起、`isRequired` 判断 |
| Playwright e2e | MCP Tools 标签页渲染 Server 分组、展开参数预览、离线 Server 工具禁用 |

---

## 4. 错误处理

- 批量接口异常：`mcpApi.getToolsByServer()` 调用时已有统一的 `request` 拦截器处理错误
- 单个 Server `online` 字段异常：默认为 `false`，工具 checkbox 禁用
- `inputSchema.properties` 为空或缺失：不显示展开按钮

---

## 5. 非目标

- 不实时检测 MCP Server 连接状态（避免表单打开延迟）
- 不修改 Agent-MCP 工具的持久化逻辑（只影响选择展示）
- 不修改 MCP 工具的前端数据结构 `McpToolBrief`（仅 Agent 列表接口使用，此次改动不涉及）
