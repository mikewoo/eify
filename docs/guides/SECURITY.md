# 系统风险清单

> 基于 9 个模块的架构分析与风险修复后的复盘。用于指导新功能开发时的安全审查和测试优先级。
> 最后更新：2026-05-20

---

## 一、核心链路（5 条）

| # | 链路名称 | 涉及模块/类 | 核心原因 |
|:---|:---|:---|:---|
| **1** | **Agent 对话链路** | `ChatController` → `ChatServiceImpl` → `ProviderAdapterFactory` → `LlmHttpClient` → SSE 流式输出 | 用户核心体验路径。涉及 LLM 调用、SSE 流式、工具调用循环（最多 5 轮）、RAG 检索增强。任一环失败用户直接感知 |
| **2** | **工作流执行链路** | `WorkflowEngine` → 7 种 `NodeExecutor`（llm / api_call / code / condition / tool_call / start / end）→ `VariableResolver` | 系统最复杂的编排路径。DAG 拓扑执行，节点超时按类型区分（LLM 3min / API 30s），代码节点有沙箱隔离，变量系统单次替换防注入 |
| **3** | **认证与工作空间隔离链路** | `JwtAuthFilter` → `JwtSecretValidator`（启动校验）→ `CurrentContext`（ThreadLocal）→ `ContextPropagatingTaskDecorator`（5 个线程池）→ `WorkspaceGuard` | 多租户安全防火墙。JWT 密钥生产环境强制校验，ThreadLocal 通过 TaskDecorator 传播到所有异步线程池 |
| **4** | **知识库 RAG 链路** | `EmbeddingStrategy` → pgvector `<=>` 余弦相似度 → `ChunkService.search()` → `mergeAndRerank()` | 知识检索质量决定 Agent 回答准确度。涉及向量化、HNSW 索引、多知识库合并去重重排序 |
| **5** | **MCP 工具调用链路** | `McpClientServiceImpl`（DCL+锁保护）→ `io.modelcontextprotocol` SDK → 外部 MCP Server | Agent 能力扩展通道。客户端缓存 5 分钟 TTL，DCL+同步锁防并发竞态，最多 2 次重试，按 serverId 缓存可用工具集 |

---

## 二、风险集中区域

### 🔴 严重 — 安全红线，触碰即可能造成数据泄露或服务不可用

| 风险点 | 类型 | 位置 | 关键防护 |
|:---|:---|:---|:---|
| **JWT 密钥泄露** | 安全 | `JwtAuthFilter` + 配置 | `JwtSecretValidator` 启动时检测已知默认值/空值/短密钥，非 dev 环境拒绝启动 |
| **ApiNodeExecutor SSRF** | 安全 | `ApiNodeExecutor.validateUrl()` + `isBlockedAddress()` | 封堵 `file/ftp/jar/gopher`，阻止云元数据 IP，IPv4/IPv6 回环/内网/链路本地地址，IPv4-mapped IPv6 递归检查 |
| **CodeNodeExecutor 沙箱** | 安全 | `CodeNodeExecutor` | 共享守护线程池（4 线程），30s 超时 + Future.cancel(true)，线程池满时 AbortPolicy 拒绝 |
| **VariableResolver 注入** | 安全 | `VariableResolver` + 各消费者 | 单次替换防递归展开；消费者层防护：ConditionNodeExecutor 用 getVariable 取原始值，ApiNodeExecutor URL 校验，CodeNodeExecutor ScriptEngine.put 注入 |

### 🟡 高 — 可能造成部分功能不可用或数据不一致

| 风险点 | 类型 | 位置 | 关键防护 |
|:---|:---|:---|:---|
| **ThreadLocal 丢失** | 并发 | `CurrentContext` + `ThreadPoolConfig` | `ObjectProvider<TaskDecorator>` 注入，5 个线程池自动应用 context 传播 |
| **SSE 连接泄漏** | 性能 | `ChatServiceImpl` + `LlmHttpClient` | onTimeout/onCompletion/onError 三回调 + activeSessions 清理 + OkHttp ConnectionPool(20) |
| **MCP Client 竞态** | 并发 | `McpClientServiceImpl` | DCL + synchronized(lock) + 独立锁对象 per serverId |
| **节点超时不均** | 性能 | `WorkflowEngine.coreLoop()` | 按类型区分：LLM 3min / 默认 30s |

### 🟢 中 — 边界场景或性能退化

| 风险点 | 类型 | 关键防护 |
|:---|:---|:---|
| **线程池满** | 性能 | llm/workflow/mcp/sse 四个池 AbortPolicy，仅 asyncExecutor 保留 CallerRunsPolicy |
| **API 响应截断** | 数据一致性 | 512KB 截断 + warn 日志，需工作流设计层面约束 |

---

## 三、测试重心

### P0 — 必须有测试覆盖

| 测试对象 | 原因 | 状态 |
|:---|:---|:---|
| `ApiNodeExecutor` SSRF 防护 | 安全红线 | ✅ 12 个测试 |
| `JwtSecretValidator` 密钥校验 | 安全红线 | ✅ 8 个测试 |
| `ConditionNodeExecutor` 条件路由 | 工作流核心 | ✅ 18 个测试 |
| `WorkspaceGuard` 空间隔离 | 多租户安全 | ✅ 已有 |
| `CodeNodeExecutor` 沙箱超时 | 安全红线 | ✅ 已有 |

### P1 — 应该有测试覆盖

| 测试对象 | 原因 | 状态 |
|:---|:---|:---|
| `WorkflowEngine` 端到端 | 多节点 DAG + 分支 + 超时 | ⚠️ 仅单测 |
| `ChatServiceImpl` SSE 生命周期 | 连接异常处理 | ⚠️ 仅 mock |
| `McpClientServiceImpl` 生命周期 | 缓存/竞态/降级 | ❌ 无 |
| `ProviderAdapter` SSE 解析 | 各厂商协议差异 | ❌ 无 |
| `ChunkService` RAG 检索 | 向量搜索 + 降级 | ❌ 无 |

### P2 — 可以后补

| 类别 | 原因 |
|:---|:---|
| CRUD Controller / Mapper XML | 样板代码，Service 层已验证 |
| 游标分页 | MyBatis-Plus 封装 |
| 前端组件单测 | TS 类型检查 + vitest 基础覆盖即可，复杂交互用 e2e |

---

## 相关文档

- [CLAUDE.md](../../CLAUDE.md) — 核心约束 + 代码提交检查清单
- [ARCHITECTURE.md](../ARCHITECTURE.md) — 架构设计 + 编码规范
- [AUTH-WORKSPACE.md](AUTH-WORKSPACE.md) — 认证与工作空间隔离
- [E2E-TESTING.md](E2E-TESTING.md) — 端到端测试指南

---

**最后更新**：2026-05-20（从 CLAUDE.md 迁出）
