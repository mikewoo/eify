# 工作流引擎设计

## 概述

Eify 工作流引擎是一个可视化的 AI Agent 编排系统，支持通过拖拽节点和连线构建复杂的多步骤 AI 处理流程。

**核心能力**：
- 7 种节点类型覆盖代码执行、LLM 调用、工具调用、条件分支
- 变量系统支持节点间数据传递
- 条件路由支持多分支并行
- MCP 工具无缝集成

## 节点类型

| 类型 | 说明 | 执行器 |
|:---|:---|:---|
| `start` | 流程入口，接收外部输入变量 | StartNodeExecutor |
| `code` | 执行 JavaScript 代码，用于数据提取/转换 | CodeNodeExecutor |
| `condition` | 条件路由，根据变量值分流到不同分支 | ConditionNodeExecutor |
| `llm` | 调用 LLM 生成回复 | LlmNodeExecutor |
| `tool_call` | 调用 MCP 工具（如退款、查询） | ToolCallNodeExecutor |
| `api` | 调用外部 HTTP API | ApiNodeExecutor |
| `end` | 流程出口，输出最终结果 | EndNodeExecutor |

### 节点配置结构

每个节点包含通用字段和类型特定的 `config`：

```json
{
  "nodeKey": "唯一标识",
  "type": "节点类型",
  "name": "显示名称",
  "positionX": 100,
  "positionY": 200,
  "config": { ... }
}
```

#### code 节点

```json
{
  "language": "javascript",
  "outputKey": "intent",
  "code": "var input = user_input.toLowerCase(); ..."
}
```

- `outputKey`：代码返回值存入该变量名
- `code`：JavaScript 代码，可访问所有已定义的变量

#### condition 节点

```json
{
  "expression": "{{intent}}"
}
```

- `expression`：求值表达式，结果与各出边的 `condition` 匹配

#### llm 节点

```json
{
  "model": "deepseek-v4-flash",
  "providerId": 3,
  "temperature": 0.7,
  "maxTokens": 1000,
  "outputKey": "reply",
  "systemPrompt": "你是...",
  "userPrompt": "用户输入：{{user_input}}"
}
```

- `systemPrompt` / `userPrompt`：支持 `{{variable}}` 模板变量
- `outputKey`：LLM 回复存入该变量名

#### tool_call 节点

```json
{
  "serverId": 3,
  "toolName": "check_refund_eligibility",
  "argumentsTemplate": { "orderId": "{{order_id}}" },
  "outputKey": "eligibility"
}
```

- `serverId`：MCP Server ID
- `toolName`：工具名称
- `argumentsTemplate`：参数模板，支持 `{{variable}}` 插值

## 变量系统

变量是节点间数据传递的载体。在工作流启动时定义初始变量，各节点通过 `outputKey` 写入结果，后续节点通过 `{{variable}}` 读取。

```json
"variables": [
  { "key": "user_input", "type": "string", "required": true },
  { "key": "order_id", "type": "string" },
  { "key": "user_id", "type": "string" }
]
```

**变量生命周期**：
1. `start` 节点接收外部传入的变量值
2. `code` / `llm` / `tool_call` 节点通过 `outputKey` 写入新变量
3. 后续节点通过 `{{variable}}` 模板读取
4. `end` 节点输出最终变量值

## 边与条件

边定义节点间的连接关系：

```json
{
  "sourceNodeKey": "condition_1",
  "targetNodeKey": "code_extract_order",
  "condition": "退货",
  "label": "退货"
}
```

- 无 `condition` 的边：无条件跳转
- 有 `condition` 的边：condition 节点的求值结果等于该值时走此分支

## 参考实现：电商客服意图分类

以下是一个完整的工作流示例，展示了所有节点类型的组合使用。

### 流程图

```
┌─────────┐
│  开始    │
└────┬────┘
     │
┌────▼────┐
│ 关键词   │  正则匹配：退货/换货/投诉
│ 预检     │
└────┬────┘
     │
┌────▼──────────────────────────────────────────────────┐
│                  关键词路由 (condition)                  │
├────────┬──────────┬──────────┬─────────────────────────┤
│ 退货   │  换货    │  咨询    │  投诉  │  需要LLM分类     │
└───┬────┘   │       │         │        └───────┬─────────┘
    │        │       │         │                │
    ▼        ▼       ▼         ▼         ┌──────▼───────┐
  退货      换货    咨询      结束        │ LLM意图补位   │
  处理链    LLM     LLM                  └──────┬───────┘
    │        │       │         ┌────────┬────────┤
    │        │       │         │        │        │
    ▼        ▼       ▼         ▼        ▼        ▼
                         LLM路由 → 退货/换货/咨询/引导
```

### 退货处理链（tool_call 串联）

```
提取订单号 → 提取用户ID → 检查退款资格 → 预览退款金额
→ 提交退款申请 → 提取退款ID → 查询退款状态
→ 查询退款历史 → LLM综合回复 → 结束
```

**关键设计**：
- 前置 `code` 节点用正则从用户输入提取 `order_id` 和 `user_id`
- 串联 5 个 `tool_call` 节点调用退款 MCP 工具
- 最终 `llm` 节点综合所有工具结果生成用户友好回复

### LLM 意图补位（fallback 策略）

当关键词预检无法匹配时（返回"需要LLM分类"），由 LLM 进行语义级意图分类：

1. `llm_intent`：低温度（0.1）分类，输出 退货/换货/咨询/未分类
2. `condition_2`：根据 LLM 分类结果路由
3. "未分类"走 `llm_guide`：柔性引导用户说明需求

### 完整 JSON 配置

<details>
<summary>点击展开完整 workflow-config-final.json</summary>

```json
{
  "name": "电商客服意图分类工作流",
  "variables": [
    { "key": "user_input", "type": "string", "required": true },
    { "key": "order_id", "type": "string" },
    { "key": "user_id", "type": "string" },
    { "key": "refund_id", "type": "string" }
  ],
  "nodes": [
    { "nodeKey": "start_1", "type": "start", "name": "开始", "config": {} },
    {
      "nodeKey": "code_keyword", "type": "code", "name": "关键词预检",
      "config": {
        "language": "javascript", "outputKey": "intent",
        "code": "var input = user_input.toLowerCase().trim();\nif (/退货|退款|退钱/.test(input)) { '退货'; }\nelse if (/换货|换一个|更换/.test(input)) { '换货'; }\nelse if (/投诉|举报/.test(input)) { '投诉'; }\nelse { '需要LLM分类'; }"
      }
    },
    { "nodeKey": "condition_1", "type": "condition", "name": "关键词路由", "config": { "expression": "{{intent}}" } },
    {
      "nodeKey": "code_extract_order", "type": "code", "name": "提取订单号",
      "config": { "language": "javascript", "outputKey": "order_id", "code": "..." }
    },
    {
      "nodeKey": "code_extract_user", "type": "code", "name": "提取用户ID",
      "config": { "language": "javascript", "outputKey": "user_id", "code": "..." }
    },
    {
      "nodeKey": "tool_check_eligibility", "type": "tool_call", "name": "检查退款资格",
      "config": { "serverId": 3, "toolName": "check_refund_eligibility", "argumentsTemplate": { "orderId": "{{order_id}}", "userId": "{{user_id}}" }, "outputKey": "eligibility" }
    },
    {
      "nodeKey": "tool_preview_amount", "type": "tool_call", "name": "预览退款金额",
      "config": { "serverId": 3, "toolName": "preview_refund_amount", "argumentsTemplate": { "orderId": "{{order_id}}", "userId": "{{user_id}}", "reason": "用户申请退款" }, "outputKey": "preview" }
    },
    {
      "nodeKey": "tool_submit_refund", "type": "tool_call", "name": "提交退款申请",
      "config": { "serverId": 3, "toolName": "submit_refund", "argumentsTemplate": { "orderId": "{{order_id}}", "userId": "{{user_id}}", "reason": "用户申请退款" }, "outputKey": "refund_result" }
    },
    {
      "nodeKey": "llm_intent", "type": "llm", "name": "LLM意图补位",
      "config": { "model": "deepseek-v4-flash", "providerId": 3, "temperature": 0.1, "maxTokens": 100, "outputKey": "intent", "systemPrompt": "你是电商客服意图分类助手...", "userPrompt": "用户输入：{{user_input}}" }
    },
    { "nodeKey": "condition_2", "type": "condition", "name": "LLM路由", "config": { "expression": "{{intent}}" } },
    {
      "nodeKey": "llm_return", "type": "llm", "name": "退货处理",
      "config": { "model": "deepseek-v4-flash", "providerId": 3, "temperature": 0.7, "maxTokens": 1000, "outputKey": "reply", "systemPrompt": "你是退货处理专员...", "userPrompt": "..." }
    },
    { "nodeKey": "end_1", "type": "end", "name": "结束", "config": { "outputKey": "reply" } }
  ],
  "edges": [
    { "sourceNodeKey": "start_1", "targetNodeKey": "code_keyword" },
    { "sourceNodeKey": "code_keyword", "targetNodeKey": "condition_1" },
    { "sourceNodeKey": "condition_1", "targetNodeKey": "code_extract_order", "condition": "退货" },
    { "sourceNodeKey": "condition_1", "targetNodeKey": "llm_exchange", "condition": "换货" },
    { "sourceNodeKey": "condition_1", "targetNodeKey": "llm_consult", "condition": "咨询" },
    { "sourceNodeKey": "condition_1", "targetNodeKey": "end_1", "condition": "投诉" },
    { "sourceNodeKey": "condition_1", "targetNodeKey": "llm_intent", "condition": "需要LLM分类" },
    { "sourceNodeKey": "llm_intent", "targetNodeKey": "condition_2" },
    { "sourceNodeKey": "condition_2", "targetNodeKey": "code_extract_order", "condition": "退货" },
    { "sourceNodeKey": "condition_2", "targetNodeKey": "llm_exchange", "condition": "换货" },
    { "sourceNodeKey": "condition_2", "targetNodeKey": "llm_consult", "condition": "咨询" },
    { "sourceNodeKey": "condition_2", "targetNodeKey": "llm_guide", "condition": "未分类" }
  ]
}
```

</details>

## 前端架构：可视化画布编辑器

基于 **Vue Flow**（`@vue-flow/core` v1.48）实现拖拽式可视化工作流编辑。

### 技术选型

| 项目 | 选择 | 说明 |
|------|------|------|
| 画布引擎 | Vue Flow 1.x | Vue 3 原生，API 对齐 React Flow |
| 样式 | `@vue-flow/core/dist/theme-default.css` | 与 Dify 风格一致的深色 mini-map |
| 拖拽协议 | HTML5 Drag API (`application/vueflow`) | 从 NodePanel 拖入画布 |
| 节点注册 | `markRaw()` + `node-types` prop | 7 个自定义 Vue 组件 |
| 坐标转换 | `screenToFlowCoordinate()` | 处理画布偏移和缩放 |

### 组件架构

```
WorkflowEdit.vue (画布编排主页面)
├── NodePanel.vue          (左侧 220px 节点列表，拖拽源)
├── VueFlow                (中间画布)
│   ├── Background         (20px 网格点阵)
│   ├── Controls           (右下角缩放/居中控制)
│   └── MiniMap            (左下角 180×120 缩略图)
├── el-dialog              (基本设置：描述、状态、变量)
└── el-drawer              (右侧 520px 节点配置抽屉)
    └── 7 种节点配置表单   (供应商下拉、模型级联、KV 表等)
```

### 自定义节点组件

每个节点继承 `NodeProps`，使用 Handle 定义输入输出端口：

| 组件 | 颜色 | 图标 | 端口 |
|------|------|------|------|
| `StartNode` | #22c55e | VideoPlay | source ↓ |
| `EndNode` | #ef4444 | SwitchFilled | target ↑ |
| `LlmNode` | #8b5cf6 | Cpu | target ↑ + source ↓ |
| `ApiNode` | #f97316 | Connection | target ↑ + source ↓ |
| `ConditionNode` | #eab308 | Guide | target ↑ + true ↓ + false ↓ |
| `CodeNode` | #3b82f6 | Edit | target ↑ + source ↓ |
| `ToolNode` | #06b6d4 | SetUp | target ↑ + source ↓ |

所有节点共用 `node.css`，使用 CSS 变量 `--wf-node-color` 控制主题色。

### 数据映射（Vue Flow ↔ 后端 DTO）

```
Vue Flow Node { id, type, position, data }
  id        ←→ nodeKey
  type      ←→ type
  position  ←→ positionX / positionY
  data.name ←→ name
  data.config ←→ config (JsonNode)

Vue Flow Edge { id, source, target, sourceHandle }
  source       ←→ sourceNodeKey → DB sourceNodeId
  target       ←→ targetNodeKey → DB targetNodeId
  sourceHandle ←→ condition
```

### 交互流程

1. **创建新工作流**：画布初始化 start + end 两个默认节点
2. **拖入节点**：NodePanel `dragstart` → 画布 `drop` → `screenToFlowCoordinate()` → `addNodes()`
3. **连接节点**：Handle 拖线 → `onConnect` → `addEdges()`（smoothstep 样式）
4. **配置节点**：点击节点 → `openConfig()` → 抽屉渲染类型对应表单 → 确定写回 `node.data.config`
5. **保存**：验证（名称、键重复、边有效性、必有 start/end）→ 序列化 nodes/edges → DTO → API
6. **编辑已有**：API 返回节点列表 → `createFlowNode()` 映射 → 恢复画布状态

### 节点配置表单要点

- **LLM 节点**：供应商 → 模型级联选择，依赖 `providerApi.getProviderList()` + `ModelConfigInfo`
- **Tool 节点**：MCP Server → 工具名级联选择，依赖 `mcpApi.getList()` + `mcpApi.getById()`
- 所有 select 选择后自动清空无效的级联选项
- Headers 和 ArgumentsTemplate 使用 KV 编辑表（可增删行）

## 设计模式

### 1. 关键词预检 + LLM 补位

先用低成本的正则匹配处理高频场景，匹配失败时再调用 LLM。兼顾响应速度和覆盖率。

### 2. code 节点数据提取

在调用工具前，用 `code` 节点从自然语言中提取结构化数据（订单号、用户ID），避免让 LLM 做简单的正则工作。

### 3. tool_call 串联

多个工具节点串联执行，前一个的输出作为后一个的输入。适用于需要多步查询/操作的业务流程。

### 4. 柔性引导

当用户意图无法识别时，不直接报错，而是用 LLM 生成友好的引导语，提升用户体验。

## 更新日志

- **2026-05-15**: 新增前端 Vue Flow 画布编辑器架构文档；节点组件尺寸优化（紧凑风格）
- **2026-05-14**: 从 workflow-config-final.json 提炼为设计文档
