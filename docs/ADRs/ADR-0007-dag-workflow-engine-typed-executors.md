# DAG 工作流引擎 + 类型化节点执行器
`ADR-0007 dag-workflow-engine-typed-executors`

# Status
Accepted

# Date
2026-Q1

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

Eify 需要将 AI Agent 的对话能力扩展为可编排的多步骤处理流程。典型场景：电商客服工作流（关键词预检 → 意图分类 → API 调用 → LLM 回复），需要支持条件分支、变量传递和多轮工具调用。

### Decision drivers
- 需要支持条件分支和多路路由（非简单线性 Pipeline）
- 前端需可视化拖拽编排（JSON 图结构友好）
- 不同节点类型执行耗时差异极大（LLM 3min vs API 30s），需差异化超时
- 代码节点需沙箱隔离（用户自定义 JS）

# Considered Options
* **方案 A：Temporal / Cadence** — 分布式工作流引擎。对 MVP 过度复杂，需要额外部署 Temporal Server。
* **方案 B：Camunda / Flowable** — BPMN 标准引擎。BPMN XML 编辑体验差，前端可视化集成困难。
* **方案 C：线性 Pipeline** — 固定顺序执行。无法表达条件分支和多路路由。
* **方案 D：自研 DAG 引擎 + 类型化执行器** — 轻量 DAG + NodeExecutor 接口多态。前端可视化友好，按需扩展，代码量可控。

# Decision

**选择方案 D：自研轻量 DAG 引擎 + 7 种类型化节点执行器（NodeExecutor 接口），支持按节点类型差异化超时和变量系统。**

每种节点类型实现独立的 `NodeExecutor`，通过接口多态（而非 if-else）dispatch。新增节点类型只需实现接口并注册，现有代码零修改。

## Consequences

### 优势
- 7 种执行器覆盖 LLM 编排全场景，新节点类型扩展成本低
- 差异化超时避免慢 LLM 调用阻塞快速 API 调用
- SSRF + 沙箱 + 变量防护三重安全措施
- 共享守护线程池限制代码节点资源消耗

### 权衡
- DAG 合法性校验较简单（依赖前端保证，后端仅做基本检查）
- 无分布式执行能力（当前限制单机内存）
- 无工作流版本回滚机制（通过 `version` 字段记录快照，但不可回滚）
- 共享沙箱线程池意味着恶意代码节点可能影响其他工作流

# Details

## 架构

```
WorkflowEngine
  │
  ├─ WorkflowNode[] (DAG 节点)
  │    └─ type: start / code / condition / llm / tool_call / api / end
  │
  ├─ WorkflowEdge[] (DAG 边)
  │    └─ sourceNodeId → targetNodeId (condition 可选)
  │
  ├─ NodeExecutor 接口 (策略模式)
  │    ├─ StartNodeExecutor
  │    ├─ CodeNodeExecutor      (JS 沙箱, 4 线程守护池, 30s 超时)
  │    ├─ ConditionNodeExecutor (表达式求值, 防注入)
  │    ├─ LlmNodeExecutor       (LLM 调用, 3min 超时)
  │    ├─ ToolCallNodeExecutor  (MCP 工具调用)
  │    ├─ ApiNodeExecutor       (HTTP API 调用, SSRF 防护, 30s 超时)
  │    └─ EndNodeExecutor
  │
  └─ VariableResolver (变量系统)
       └─ 单次替换, 防递归展开
```

## 按节点类型差异化超时

| 节点类型 | 超时 | 原因 |
|:---|:---|:---|
| `llm` | 3 分钟 | LLM API 可能耗时较长，尤其是 reasoning 模型 |
| `api` | 30 秒 | HTTP 调用应在秒级完成 |
| `code` | 30 秒 | JS 沙箱执行，共享守护线程池（4 线程），`Future.cancel(true)` 强制中断 |
| 其他 | 30 秒 | 默认值 |

整体工作流 5 分钟总超时。

## 安全三重防护

| 防护层 | 节点 | 机制 |
|:---|:---|:---|
| **SSRF 防护** | `api` | `validateUrl()` 封堵 `file/ftp/jar/gopher` 协议，阻止云元数据 IP、回环/内网/链路本地地址 |
| **沙箱隔离** | `code` | 共享守护线程池（4 线程），30s 超时 + `Future.cancel(true)`，池满 AbortPolicy 拒绝 |
| **变量注入防护** | 全局 | `VariableResolver` 单次替换（禁止递归 `{{{{x}}}}`），各消费者层二次防护 |

## 参考
- [WORKFLOW.md](../guides/WORKFLOW.md) — 工作流引擎详细设计、节点配置、变量系统
- [系统风险清单](../../CLAUDE.md#系统风险清单) — 安全防护详情
