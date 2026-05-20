# Eify 项目文档

本目录包含 Eify 项目的详细技术文档。

## 项目概述

### 产品定位

轻量级 AI Agent 平台，面向团队内部 20-50 人使用，支持本地部署。

### 核心功能

| 功能模块 | 说明 |
|:---|:---|
| **多模型提供商管理** | 支持 OpenAI、Claude 等多个 LLM 提供商 |
| **Agent 创建与配置** | 可视化创建和配置 AI Agent，支持自定义提示词和参数 |
| **对话引擎** | 支持流式响应（SSE）、多轮对话、上下文管理 |
| **知识库 + RAG** | 文档上传、向量检索、检索增强生成（支持多策略） |
| **MCP 工具接入** | 支持 Model Context Protocol 工具扩展 |
| **工作流引擎** | 可视化工作流编排，支持条件分支和节点执行 |
| **多租户工作空间** | JWT 认证 + 工作空间数据隔离 |

### 技术栈

| 层级 | 技术选型 |
|:---|:---|
| **后端** | Spring Boot 4.0.6 + MyBatis-Plus 3.5.15 |
| **数据库** | MySQL 8.0 + Redis 7 |
| **前端** | Vue 3 + TypeScript + Element Plus |
| **容器化** | Docker + Docker Compose |
| **监控** | Micrometer Tracing + Brave + ClickHouse + Vector |

### 开发约束

| 约束 | 说明 |
|:---|:---|
| **团队规模** | 1 人开发 |
| **开发周期** | 2-4 周 MVP |
| **工程优先级** | 工程化成熟度 > AI 生态（选择 Spring Boot 而非 Python） |

## 文档结构

```
docs/
├── README.md                  # 本文件（文档索引）
├── ARCHITECTURE.md            # 架构设计 + 编码规范（模块结构、命名、分层职责）
├── API-SPEC.md                # API 接口规范
├── DEPLOYMENT.md              # 部署与 CI/CD（本地、Docker、K8s、Jenkins）
├── ADRs/                      # 设计决策记录（ADR，一次性归档）
│   ├── ADR-0001-cursor-pagination-improvement.md
│   ├── ADR-0002-i18n-json-exchange-format.md
│   ├── ADR-0003-provider-model-auto-sync.md
│   ├── ADR-0004-dual-storage-mysql-pgvector.md
│   ├── ADR-0005-json-structured-logging.md
│   ├── ADR-0006-jwt-threadlocal-workspace-isolation.md
│   ├── ADR-0007-dag-workflow-engine-typed-executors.md
│   └── ADR-0008-circuit-breaker-provider-adapter.md
└── guides/                    # HOW-TO 指南（体量大、持续更新）
    ├── AUTH-WORKSPACE.md      # 用户认证与工作空间多租户
    ├── DATABASE.md            # 数据库设计（MySQL + ClickHouse + 游标分页）
    ├── LOGGING.md             # 日志系统完整指南（架构、格式、MQ、监控、性能）
    └── WORKFLOW.md            # 工作流引擎设计（节点类型、变量系统、参考实现）
```

## 日志系统文档

### [LOGGING.md](guides/LOGGING.md)
**日志系统完整指南**

涵盖内容：
- 架构设计（智能分类、异步日志、响应记录）
- 日志格式（纯 JSON、UTC 时区、标准字段）
- 日志等级和类型（req/sql/msg/simple/sys）
- TraceId/SpanId 机制（生成、跨线程传递）
- 消息队列日志（自动记录 Kafka/RocketMQ/Async/Event）
- 配置说明（Tracing、MQ、SQL、请求日志）
- ClickHouse 存储
- 日志监控方案（ClickVisual vs Prometheus+Grafana）
- 性能分析与瓶颈（容量规划、优化优先级）

**适合读者**：所有开发人员、架构师、运维人员

## 部署相关文档

部署相关文档位于 `deploy/` 目录：

### [../deploy/infra/deploy/README.md](../deploy/infra/deploy/README.md)
**日志系统集成指南**

涵盖内容：
- 架构概述（应用层 → 采集层 → 存储层 → 查询层）
- 快速开始（一键部署）
- 详细配置（应用日志、Vector、ClickHouse）
- 查询示例（基础查询、Trace 查询、聚合统计）
- 告警配置（错误率、慢查询、请求量突增）
- 性能优化（分区、物化视图、跳数索引）
- 监控指标和常见问题

**适合读者**：运维人员、DevOps 工程师

---

## 可选工具

以下工具**不是必需的**，核心功能独立运行：

### [../deploy/optional/JAEGER.md](../deploy/optional/JAEGER.md)
**Jaeger 分布式追踪可视化**

涵盖内容：
- 快速启动和管理命令
- 服务端点说明
- 与日志系统的关系

**适合读者**：运维人员、架构师（多服务场景）

**注意**：单服务 MVP 不需要 Jaeger，TraceId/SpanId 已集成在日志系统中。

## 决策记录（ADR）

设计决策记录归档在 [ADRs/](ADRs/) 目录，记录一次性决策和历史变更：

| 文件 | 内容 |
|:---|:---|
| [ADR-0001-cursor-pagination-improvement.md](ADRs/ADR-0001-cursor-pagination-improvement.md) | 游标分页：大表深分页优化 |
| [ADR-XXXX-Template.md](ADRs/ADR-XXXX-Template.md) | ADR 模板（新建 ADR 时复制此文件） |
| [ADR-0002-i18n-json-exchange-format.md](ADRs/ADR-0002-i18n-json-exchange-format.md) | i18n JSON 交换格式 + 构建时嵌入 |
| [ADR-0003-provider-model-auto-sync.md](ADRs/ADR-0003-provider-model-auto-sync.md) | Provider 模型自动同步设计 |
| [ADR-0004-dual-storage-mysql-pgvector.md](ADRs/ADR-0004-dual-storage-mysql-pgvector.md) | MySQL + pgvector 双存储架构 |
| [ADR-0005-json-structured-logging.md](ADRs/ADR-0005-json-structured-logging.md) | 纯 JSON 结构化日志 |
| [ADR-0006-jwt-threadlocal-workspace-isolation.md](ADRs/ADR-0006-jwt-threadlocal-workspace-isolation.md) | JWT + ThreadLocal 工作空间隔离 |
| [ADR-0007-dag-workflow-engine-typed-executors.md](ADRs/ADR-0007-dag-workflow-engine-typed-executors.md) | DAG 工作流引擎 + 类型化执行器 |
| [ADR-0008-circuit-breaker-provider-adapter.md](ADRs/ADR-0008-circuit-breaker-provider-adapter.md) | Provider Adapter 层 Resilience4j 熔断器 |

## 文档拆分原则

Eify 项目采用模块化文档管理，遵循以下原则：

### 1. 单一职责原则
每个文档文件负责一个明确的技术领域或主题：
- **LOGGING.md** → 日志系统完整指南（架构、格式、配置、MQ、监控、性能）
- **ARCHITECTURE.md** → 系统架构设计
- **DATABASE.md** → 数据库设计（MySQL + ClickHouse）

### 2. 大小控制
- 单个文档文件建议不超过 **500 行**
- 如果超过，考虑按子主题拆分
- 使用目录（TOC）提高长文档的可读性

### 3. 命名约定
- 使用 **大写 + 连字符**：`LOGGING.md`、`DATABASE.md`
- 使用 **描述性名称**：清晰表达文档内容
- 使用 **分类前缀**：`LOGGING-`、`DEPLOY-` 等

### 4. 可维护性
- 独立文件便于**单独更新**
- 减少**合并冲突**
- 便于**版本控制**

### 5. 可读性
- 较小文件**加载更快**
- **导航更清晰**
- **查找更方便**

### 6. 可复用性
- 每个文档可以**独立引用**
- 便于**分享给特定读者**
- 支持**跨项目复用**

### 7. 交叉引用
- 主文档（CLAUDE.md）作为**索引和入口**
- 各文档间通过**相对路径**相互链接
- 在相关章节添加**参见引用**

## 文档更新规范

### 更新流程
1. 修改具体文档时，同步更新相关文档的交叉引用
2. 在文档末尾的"更新日志"中记录变更
3. 重大变更时，更新 CLAUDE.md 中的相应章节

### 更新日志格式
```markdown
## 更新日志

- **YYYY-MM-DD**: 变更描述
  - 详细变更点 1
  - 详细变更点 2
```

## 快速查找

### 按角色查找

| 角色 | 推荐阅读文档 |
|:---|:---|
| **开发人员** | ARCHITECTURE.md、LOGGING.md |
| **运维人员** | LOGGING.md、deploy/infra/deploy/README.md |
| **架构师** | 所有文档 |
| **技术负责人** | LOGGING.md、ARCHITECTURE.md、DEPLOYMENT.md |

### 按主题查找

| 主题 | 文档 |
|:---|:---|
| **架构设计** | ARCHITECTURE.md |
| **工作流引擎** | WORKFLOW.md |
| **编码规范** | ARCHITECTURE.md#编码规范 |
| **API 规范** | API-SPEC.md |
| **用户认证/多租户** | AUTH-WORKSPACE.md |
| **数据库设计** | DATABASE.md（含 MySQL + ClickHouse + 游标分页） |
| **日志系统** | LOGGING.md |
| **LLM 调用 / SSE 流式** | ARCHITECTURE.md（线程池配置）、LOGGING.md |
| **性能优化** | LOGGING.md#性能分析与瓶颈、DEPLOYMENT.md#扩容触发条件 |
| **部署运维** | DEPLOYMENT.md |
| **CI/CD 流水线** | DEPLOYMENT.md |
| **监控告警** | LOGGING.md#日志监控方案 |

## 贡献指南

### 新增文档
1. 确定文档主题和读者对象
2. 选择合适的文件名（遵循命名约定）
3. 编写文档内容（包含目录、概述、详细内容、示例）
4. 在本 README.md 中添加文档索引
5. 在 CLAUDE.md 中添加引用（如适用）

### 文档模板
```markdown
# 文档标题

## 一、概述
简要描述文档内容和目标读者

## 二、正文
详细内容...

## 三、示例
代码示例...

## 四、常见问题
FAQ...

## 五、相关文档
交叉引用...

---
## 更新日志
- **YYYY-MM-DD**: 创建文档
```

## 反馈与改进

如果发现文档问题或有改进建议：
1. 提交 Issue 描述问题
2. 提交 PR 直接改进
3. 在团队会议中讨论

---

**最后更新**: 2026-05-20（文档全面审计与更新）
