# CLAUDE.md

> 本文件为 Claude Code (claude.ai/code) 提供项目指导，包含开发规范、行为指令和常见问题。

---

## 目录

- [快速启动](#快速启动)
- [核心约束](#核心约束)
- [开发规范](#开发规范)
- [日志系统快速参考](#日志系统快速参考)
- [代码提交检查清单](#代码提交检查清单)
- [常见问题](#常见问题)
- [设计系统](#设计系统)
- [项目上下文](#项目上下文)
- [系统风险清单](#系统风险清单)
- [文档索引](#文档索引)

---

## 快速启动

### 本地开发

```bash
# 启动开发环境（推荐）
./start.sh dev

# 停止服务
./stop.sh dev

# 直接使用 Maven
mvn spring-boot:run -pl eify-app -Dspring-boot.run.profiles=dev
```

### 环境 Profile

| Profile | 配置文件 | 用途 | 部署方式 |
|:---|:---|:---|:---|
| `dev` | `application-dev.yml` | 本地开发 | Docker Compose |
| `test` | `application-test.yml` | CCE Turbo K8s 自动化测试 | K8s Deployment |
| `staging` | `application-staging.yml` | CCE Turbo K8s 预发布 | K8s Deployment |
| `prod` | `application-prod.yml` | CCE Turbo K8s 生产 | K8s Deployment |

### Docker 部署

```bash
# 启动所有服务（MySQL + Redis + pgvector + 应用）
docker-compose -f deploy/infra/deploy/docker-compose.yml up -d

# 启动日志采集链路（ClickHouse + Vector + Grafana + Prometheus）
docker-compose -f deploy/infra/deploy/docker-compose-logging.yml up -d

# 查看日志
docker-compose -f deploy/infra/deploy/docker-compose.yml logs -f
```

### 日志查看

```bash
# 实时查看（纯 JSON 格式）
tail -f ./logs/eify.log | jq

# 按类型过滤
grep '"logType":"req"' ./logs/eify.log | jq  # 请求日志
grep '"logType":"sql"' ./logs/eify.log | jq  # SQL 日志
grep '"level":"ERROR"' ./logs/eify.log | jq  # 错误日志

# 按 traceId 查询
grep 'YOUR_TRACE_ID' ./logs/eify.log | jq
```

---

## 核心约束

以下约束是项目的基础规则，**必须严格遵守**：

| 约束 | 说明 | 违反后果 | 相关文档 |
|:---|:---|:---|:---|
| 🔴 **数据库索引** | 查询必须有索引覆盖，不允许全表扫描 | 生产性能问题 | [DATABASE.md](docs/guides/DATABASE.md) |
| 🔴 **分页限制** | 分页查询必须有最大页数限制（≤ 100） | 潜在的 OOM | [API-SPEC.md](docs/API-SPEC.md) |
| 🔴 **线程池隔离** | 所有外部调用必须使用线程池隔离（llm/sse/workflow/mcp/async 五个独立线程池，AbortPolicy 拒绝策略） | 线程耗尽，服务雪崩 | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| 🔴 **SSE 超时处理** | SSE 连接必须有超时和错误处理（5min 超时，onTimeout/onCompletion/onError 三回调） | 连接泄漏 | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| 🔴 **异常处理** | 异常处理必须使用 ErrorCode 枚举 | 错误信息不一致 | [ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| 🔴 **日志格式** | 使用统一日志配置，输出纯 JSON 格式（UTC 时区） | 日志格式混乱 | [LOGGING.md](docs/guides/LOGGING.md) |
| 🔴 **ClickHouse 类型** | Nullable 字段不能与 LowCardinality 嵌套 | 类型错误 | [DATABASE.md](docs/guides/DATABASE.md) |
| 🔴 **工作空间数据隔离** | Service 层所有查询/更新/删除必须过滤 `workspace_id`，更新前验证归属。使用 `WorkspaceGuard` 工具类消除样板代码 | 跨工作空间数据泄露/篡改 | [AUTH-WORKSPACE.md](docs/guides/AUTH-WORKSPACE.md) |
| 🔴 **Flyway 迁移幂等** | 所有 DDL 语句必须检查对象是否已存在（ADD COLUMN → INFORMATION_SCHEMA.COLUMNS，ADD INDEX → INFORMATION_SCHEMA.STATISTICS），确保幂等可重入 | 应用启动失败，迁移阻塞 | [DATABASE.md](docs/guides/DATABASE.md) |
| 🔴 **ADR 命名规范** | 架构决策记录统一放在 `docs/ADRs/`，文件命名 `ADR-{四位递增序号}-{名称}.md`，序号按创建时间递增 | ADR 命名混乱，查找困难 | — |
| 🔴 **ADR 格式规范** | 所有 ADR 文档必须遵循 `docs/ADRs/ADR-XXXX-Template.md` 模板格式，包含 6 个必填章节：`# Status`、`# Date`、`# Owner`、`# Deciders`、`# Context`、`# Decision`，以及 2 个推荐章节：`## Consequences`、`# Details`。`# Considered Options` 章节列出所有候选方案及其被拒绝原因 | ADR 结构不一致，难以阅读和对比 | `docs/ADRs/ADR-XXXX-Template.md` |

---

## 开发规范

### 项目目录规范

根目录**只保留**以下文件，其余按类型归档：

| 位置 | 用途 | 示例 |
|:---|:---|:---|
| **根目录** | 项目入口 | `CLAUDE.md`、`DESIGN.md`、`start.sh`、`stop.sh` |
| **docs/** | 按开发者查阅路径组织的规范文档 | `ARCHITECTURE.md`、`API-SPEC.md` |
| **docs/guides/** | HOW-TO 指南（体量大、持续更新） | `DATABASE.md`、`LOGGING.md` |
| **docs/ADRs/** | 设计决策记录（ADR，一次性归档） | `ADR-0001-cursor-pagination-improvement.md` |
| **docs/specs/** | 功能实现规格说明（开发前编写，实施中迭代）。**所有 spec 必须放此目录，禁止使用 `docs/superpowers/specs/` 等其他路径** | `2026-05-23-mcp-workspace-isolation-fix-design.md` |
| **docs/plans/** | 实现计划（基于 spec 的任务拆分，逐步骤可执行） | `2026-05-23-design-md-compliance-fix.md` |
| **scripts/** | 开发/运维工具脚本 | `mock-mcp-server.py` |
| **deploy/** | 部署配置和脚本 | `Dockerfile`、`nginx.conf`、`k8s/`、`infra/` |
| **deploy/infra/deploy/** | Docker Compose 和部署脚本 | `docker-compose.yml`、`deploy-local.sh` |
| **deploy/optional/** | 可选组件（非核心依赖） | `docker-compose-jaeger.yml` |

**禁止**在根目录放置临时脚本、配置文件、JSON 文件、设计文档（`DESIGN.md` 除外）。

### 文档组织规范

文档按**开发者查阅路径**组织，而非技术主题：

- **规范文档**（docs/）：开发时随时查阅的入口文档，持续更新，与代码同步
- **HOW-TO 指南**（docs/guides/）：体量较大的操作指南，持续更新
- **决策记录**（docs/ADRs/）：一次性写入的 ADR，基本不改
- **规格说明**（docs/specs/）：功能实现前的设计文档，命名 `YYYY-MM-DD-<topic>-design.md`
- **实现计划**（docs/plans/）：基于 spec 的任务拆分计划，命名 `YYYY-MM-DD-<feature-name>.md`
- 每个指南对应一个开发场景："我要建表" → guides/DATABASE.md，"我要加日志" → guides/LOGGING.md

### 环境配置规范

- ✅ **优先使用脚本启动**：`./start.sh dev`、`./start.sh test`、`./start.sh staging` 或 `./start.sh prod`
- ✅ **环境配置文件分离**：dev 用 `application-dev.yml`，test 用 `application-test.yml`，staging 用 `application-staging.yml`，prod 用 `application-prod.yml`
- ❌ **不要硬编码环境配置**：所有 IP、密码、连接串必须使用 `${ENV_VAR:默认值}` 占位符
- ❌ **不要手动指定 spring.profiles.active**：已在配置文件中设置
- ❌ **禁止硬编码密码**：`Study@123`、`123456` 等真实密码不得出现在任何配置文件中
- ❌ **禁止硬编码 IP**：`192.168.x.x` 等内网 IP 不得出现在配置文件中，使用 `localhost` 或环境变量

### 编码规范

**实现原则**：
- 每个功能用最简单直接的方式实现
- 不引入不必要的设计模式，除非明确要求
- 不引入技术栈以外的依赖，需要时先询问

**修改原则**：
- 先理解相关模块的设计意图
- 不要为了新功能破坏已有接口契约
- 改完确保已有测试通过

**每次代码修改后必须执行**（Claude Code 行为指令）：

```
修改任何 Java 文件后，必须跑编译 + 受影响模块的测试，确认通过才算完成：

1. 编译验证
   mvn compile -q

2. 跑受影响模块的测试（替换 <module> 为实际模块名）
   mvn test -pl eify-<module> -am -q

3. 如果改了前端文件，额外跑：
   cd eify-web && npx vue-tsc --noEmit && npx vitest run && cd ..

4. 全部通过后才报告"完成"，有失败则修到通过为止
```

> 这条是 Claude Code 的行为约束，不是给人类开发者看的。目的是确保每次代码变更都经过自动化验证。

### 异常处理规范

- ✅ 使用 `ErrorCode` 枚举作为唯一错误码定义
- ✅ 抛出 `BusinessException(ErrorCode.xxx)`
- ✅ 返回 `Result.fail(ErrorCode.xxx)`
- ❌ 禁止 `throw new RuntimeException()`
- ❌ 禁止 `Result.fail("字符串消息")`
- ❌ `ResponseCode` 已废弃，不再使用

### 数据库操作

```java
// ✅ 正确：使用软删除
@TableName("ai_agent")
public class Agent {
    @TableLogic
    private Integer deleted;
}

// ✅ 正确：通用字段
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createdAt;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updatedAt;
```

**必须遵守**：
- 所有表必须包含通用字段（`id`, `created_at`, `updated_at`, `deleted`, `creator_id`）
- 软删除字段 `deleted` 必须有索引
- 分页查询优先使用游标分页（针对大表）
- JSON 字段必须添加注释说明结构
- Flyway 迁移必须幂等：DDL 通过 INFORMATION_SCHEMA 检查后执行（模板见下方）

**Flyway 幂等迁移模板**：

```sql
-- 加列（幂等）
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'table_name' AND COLUMN_NAME = 'col_name') = 0,
    'ALTER TABLE `table_name` ADD COLUMN `col_name` TYPE COMMENT ''注释'' AFTER `prev_col`',
    'SELECT ''Column col_name already exists, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 加索引/唯一约束（幂等）
SET @sql = IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'table_name' AND INDEX_NAME = 'idx_name') = 0,
    'ALTER TABLE `table_name` ADD INDEX `idx_name` (`col1`, `col2`)',
    'SELECT ''Index idx_name already exists, skipping'' AS info'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
```

> **规则**：所有 Flyway 迁移中的 DDL（`ADD COLUMN`, `ADD INDEX`, `ADD UNIQUE KEY`）必须使用上述模板。DML（`UPDATE`, `INSERT`）本身是幂等的，无需包装。

---

## 日志系统快速参考

### 核心要点

- **格式**：纯 JSON，UTC 时区，标准字段在顶层
- **日志类型**：REQ（9 字段）、SQL（9 字段）、MSG（16 字段）、SIMPLE、SYS
- **链路追踪**：基于 Brave/Micrometer Tracing 自动生成 TraceId/SpanId
- **存储**：ClickHouse + Vector 采集，使用 Nullable 优化存储

### 关键约束

| 约束 | 说明 | 详见 |
|:---|:---|:---|
| **ClickHouse 类型** | ❌ `Nullable(LowCardinality(String))` → ✅ `Nullable(String)` | [DATABASE.md](docs/guides/DATABASE.md) |
| **UTC 时区** | 所有日志时间戳使用 UTC 时区 | [LOGGING.md](docs/guides/LOGGING.md) |
| **MDC Keys** | `traceId`、`spanId`（由 Brave Tracing 设置） | [LOGGING.md](docs/guides/LOGGING.md) |

### 日志文档索引

| 文档 | 用途 |
|:---|:---|
| [LOGGING.md](docs/guides/LOGGING.md) | 日志系统完整指南（架构、格式、配置、MQ、监控、性能） |
| [DATABASE.md](docs/guides/DATABASE.md) | ClickHouse 表结构、索引策略、物化视图 |
| [deploy/infra/deploy/README.md](deploy/infra/deploy/README.md) | Vector + ClickHouse 部署指南 |

---

## 代码提交检查清单

> **原则**：能自动化的绝不人肉。CI 已覆盖编译、测试、TypeScript 类型检查、Vitest。
> 以下为 CI 无法判断的领域知识检查项，**按变更范围勾选对应的 section，无变更则跳过**。

### 执行方式

每次 `git commit` 前执行三步：

```bash
# 1. 自动检查（CI 等同，本地先跑）
mvn test -q                              # 后端全部测试
cd eify-web && npx vitest run && cd ..   # 前端测试
cd eify-web && npx vue-tsc --noEmit && cd ..  # 前端类型检查

# 2. 按变更范围勾选下方对应 checklists

# 3. git commit
```

### 每次提交（必检，与变更范围无关）

- [ ] **测试通过**：`mvn test` 全部通过，新增逻辑有对应测试用例
- [ ] **无硬编码**：IP、密码、密钥使用 `${ENV_VAR:}` 占位符
- [ ] **异常规范**：使用 `ErrorCode` 枚举，禁止 `throw new RuntimeException()` 和 `Result.fail("裸字符串")`

### 涉及数据库时

- [ ] **索引覆盖**：新查询有对应索引，EXPLAIN 确认无全表扫描
- [ ] **Flyway 幂等**：DDL 使用 `INFORMATION_SCHEMA` 检查模板（ADD COLUMN / ADD INDEX / ADD UNIQUE KEY）
- [ ] **ClickHouse 类型**：Nullable 不与 LowCardinality 嵌套；按 logType 设置 Nullable 字段

### 涉及 API 接口时

- [ ] **工作空间隔离**：新 Entity 实现 `WorkspaceAware`，查询/更新/删除过滤 `workspace_id`
- [ ] **分页限制**：分页查询有最大页数限制（≤ 100）
- [ ] **Breaking change**：变更了已有接口的请求/响应字段？在 commit message 中标注 `BREAKING:`

### 涉及前端时

- [ ] **XSS 防护**：所有 `v-html` 使用 `DOMPurify.sanitize()`
- [ ] **认证规范**：从 JWT token 解析用户信息，禁止硬编码 `X-User-Id`

### 涉及外部调用时（LLM / MCP / HTTP API）

- [ ] **线程池隔离**：使用命名线程池执行，禁止在 Tomcat 线程上同步调用外部服务
- [ ] **超时设置**：HTTP 调用有 connect/read timeout，SSE 有 `onTimeout` + `onError` 回调
- [ ] **安全校验**：API 调用经过 SSRF 防护（`ApiNodeExecutor.validateUrl()`）

---

## 常见问题

### Q: 日志输出格式是什么？

**A**: 纯 JSON 格式，UTC 时区，标准字段在顶层，业务数据在 `message` 对象中。详细说明：[LOGGING.md](docs/guides/LOGGING.md)

### Q: 如何按 traceId 查询完整调用链？

**A**:
```sql
-- ClickHouse 查询
SELECT * FROM app_logs WHERE traceId = 'xxx' ORDER BY timestamp;

-- 文件查询
grep 'YOUR_TRACE_ID' ./logs/eify.log | jq
```

### Q: ClickHouse 类型错误：`Nullable(LowCardinality(String))`？

**A**: ClickHouse 不支持此类型组合，改用 `Nullable(String)`。详见：[DATABASE.md](docs/guides/DATABASE.md)

### Q: Vector 连接 ClickHouse 失败？

**A**: 检查 `deploy/infra/vector/vector.toml` 中的连接配置，确保环境变量 `CLICKHOUSE_ENDPOINT` 和 `CLICKHOUSE_PASSWORD` 已设置。参考 `.env.example`。

### Q: 如何切换用户的工作空间？

**A**: 调用 `POST /api/auth/switch-workspace { workspaceId }`，后端验证成员身份后签发新 JWT（含新 `wid`）。前端 `authStore.switchWorkspace()` 自动保存新 token 并执行 `window.location.reload()` 全量刷新。详见：[AUTH-WORKSPACE.md](docs/guides/AUTH-WORKSPACE.md)

### Q: 新建业务模块时如何实现工作空间隔离？

**A**: 
1. 数据表添加 `workspace_id BIGINT UNSIGNED` 字段
2. Entity 实现 `WorkspaceAware` 接口
3. Service 层所有查询使用 `.eq(Entity::getWorkspaceId, CurrentContext.getWorkspaceId())`
4. 更新/删除使用 `WorkspaceGuard.requireInWorkspace(mapper.selectById(id), ErrorCode)` 一行完成校验
5. 创建使用 `WorkspaceGuard.bind(entity)` 绑定 workspace
6. 唯一索引必须与 `workspace_id` 组合：`UNIQUE KEY (name, workspace_id)`
7. 名称唯一性检查使用 `WorkspaceGuard.checkNameUnique(mapper, ...)`

详见：[AUTH-WORKSPACE.md](docs/guides/AUTH-WORKSPACE.md)

---

## 设计系统

> **重要**：生成或修改任何前端 UI 代码时，必须先阅读根目录的 [DESIGN.md](DESIGN.md)，使用已有的 `--eify-*` CSS 变量和 `.eify-*` 组件类名。

| 设计资源 | 路径 | 说明 |
|:---|:---|:---|
| 设计令牌 (CSS) | `eify-web/src/styles/design-tokens.css` | 颜色、字体、间距、圆角、阴影等所有 CSS 变量 |
| 组件样式 (CSS) | `eify-web/src/styles/components.css` | 按钮、输入框、卡片、表格、标签、徽章等组件 |
| 页面布局 (CSS) | `eify-web/src/styles/page.css` | 顶栏、页面容器、分页、响应式断点 |
| 侧边栏 (CSS) | `eify-web/src/styles/sidebar.css` | 深色侧边栏完整样式 |
| 工具类 (CSS) | `eify-web/src/styles/utilities.css` | 文字、间距、布局、圆角、阴影等原子类 |
| 设计规范 (MD) | `DESIGN.md` | AI 编码助手的视觉参考文档，以上所有 CSS 的 Markdown 描述 |

**核心规则：**

1. **禁止硬编码颜色、字号、间距** — 必须使用 `var(--eify-*)` 引用设计令牌
2. **优先使用 `.eify-*` 组件类** — 按钮用 `.eify-btn-primary`，卡片用 `.eify-card`，不使用内联样式或裸 HTML
3. **布局遵循 Shell 结构** — 深色侧边栏 (`#161b2e`) + 白色顶栏 + `#f8fafc` 内容区
4. **新页面放在 `eify-web/src/views/`** — 命名 `XxxView.vue`，路由在 `router/index.ts` 注册

**设计理念**：浅底科技风，蓝紫主色 (`#6366f1`) + 青薄荷辅色 (`#2dd4bf`)，深色侧边栏 + 浅色内容区，4px 网格节奏。

---

## 项目上下文

### 基本信息
- **团队规模**：1 人开发
- **开发周期**：2-4 周 MVP
- **工程优先级**：工程化成熟度 > AI 生态（选择 Spring Boot 而非 Python）
- **版本管理**：Git 语义化版本，重要变更记录在 git commit history 中

### 技术架构
- **日志架构**：纯 JSON 格式（UTC），标准字段在顶层，业务字段按 logType 分类
- **链路追踪**：基于 Brave/Micrometer Tracing 实现分布式追踪
- **日志存储**：ClickHouse + Vector 采集，使用 Nullable 优化存储
- **异步处理**：使用线程池隔离外部调用，确保服务稳定性

---

## 系统风险清单

> 基于 9 个模块的架构分析与风险修复后的复盘。用于指导新功能开发时的安全审查和测试优先级。
> 最后更新：2026-05-20

### 一、核心链路（5 条）

| # | 链路名称 | 涉及模块/类 | 核心原因 |
|:---|:---|:---|:---|
| **1** | **Agent 对话链路** | `ChatController` → `ChatServiceImpl` → `ProviderAdapterFactory` → `LlmHttpClient` → SSE 流式输出 | 用户核心体验路径。涉及 LLM 调用、SSE 流式、工具调用循环（最多 5 轮）、RAG 检索增强。任一环失败用户直接感知 |
| **2** | **工作流执行链路** | `WorkflowEngine` → 7 种 `NodeExecutor`（llm / api_call / code / condition / tool_call / start / end）→ `VariableResolver` | 系统最复杂的编排路径。DAG 拓扑执行，节点超时按类型区分（LLM 3min / API 30s），代码节点有沙箱隔离，变量系统单次替换防注入 |
| **3** | **认证与工作空间隔离链路** | `JwtAuthFilter` → `JwtSecretValidator`（启动校验）→ `CurrentContext`（ThreadLocal）→ `ContextPropagatingTaskDecorator`（5 个线程池）→ `WorkspaceGuard` | 多租户安全防火墙。JWT 密钥生产环境强制校验，ThreadLocal 通过 TaskDecorator 传播到所有异步线程池 |
| **4** | **知识库 RAG 链路** | `EmbeddingStrategy` → pgvector `<=>` 余弦相似度 → `ChunkService.search()` → `mergeAndRerank()` | 知识检索质量决定 Agent 回答准确度。涉及向量化、HNSW 索引、多知识库合并去重重排序 |
| **5** | **MCP 工具调用链路** | `McpClientServiceImpl`（DCL+锁保护）→ `io.modelcontextprotocol` SDK → 外部 MCP Server | Agent 能力扩展通道。客户端缓存 5 分钟 TTL，DCL+同步锁防并发竞态，最多 2 次重试，按 serverId 缓存可用工具集 |

### 二、风险集中区域

#### 🔴 严重 — 安全红线，触碰即可能造成数据泄露或服务不可用

| 风险点 | 类型 | 位置 | 关键防护 |
|:---|:---|:---|:---|
| **JWT 密钥泄露** | 安全 | `JwtAuthFilter` + 配置 | `JwtSecretValidator` 启动时检测已知默认值/空值/短密钥，非 dev 环境拒绝启动 |
| **ApiNodeExecutor SSRF** | 安全 | `ApiNodeExecutor.validateUrl()` + `isBlockedAddress()` | 封堵 `file/ftp/jar/gopher`，阻止云元数据 IP，IPv4/IPv6 回环/内网/链路本地地址，IPv4-mapped IPv6 递归检查 |
| **CodeNodeExecutor 沙箱** | 安全 | `CodeNodeExecutor` | 共享守护线程池（4 线程），30s 超时 + Future.cancel(true)，线程池满时 AbortPolicy 拒绝 |
| **VariableResolver 注入** | 安全 | `VariableResolver` + 各消费者 | 单次替换防递归展开；消费者层防护：ConditionNodeExecutor 用 getVariable 取原始值，ApiNodeExecutor URL 校验，CodeNodeExecutor ScriptEngine.put 注入 |

#### 🟡 高 — 可能造成部分功能不可用或数据不一致

| 风险点 | 类型 | 位置 | 关键防护 |
|:---|:---|:---|:---|
| **ThreadLocal 丢失** | 并发 | `CurrentContext` + `ThreadPoolConfig` | `ObjectProvider<TaskDecorator>` 注入，5 个线程池自动应用 context 传播 |
| **SSE 连接泄漏** | 性能 | `ChatServiceImpl` + `LlmHttpClient` | onTimeout/onCompletion/onError 三回调 + activeSessions 清理 + OkHttp ConnectionPool(20) |
| **MCP Client 竞态** | 并发 | `McpClientServiceImpl` | DCL + synchronized(lock) + 独立锁对象 per serverId |
| **节点超时不均** | 性能 | `WorkflowEngine.coreLoop()` | 按类型区分：LLM 3min / 默认 30s |

#### 🟢 中 — 边界场景或性能退化

| 风险点 | 类型 | 关键防护 |
|:---|:---|:---|
| **线程池满** | 性能 | llm/workflow/mcp/sse 四个池 AbortPolicy，仅 asyncExecutor 保留 CallerRunsPolicy |
| **API 响应截断** | 数据一致性 | 512KB 截断 + warn 日志，需工作流设计层面约束 |

### 三、测试重心

#### P0 — 必须有测试覆盖

| 测试对象 | 原因 | 状态 |
|:---|:---|:---|
| `ApiNodeExecutor` SSRF 防护 | 安全红线 | ✅ 12 个测试 |
| `JwtSecretValidator` 密钥校验 | 安全红线 | ✅ 8 个测试 |
| `ConditionNodeExecutor` 条件路由 | 工作流核心 | ✅ 18 个测试 |
| `WorkspaceGuard` 空间隔离 | 多租户安全 | ✅ 已有 |
| `CodeNodeExecutor` 沙箱超时 | 安全红线 | ✅ 已有 |

#### P1 — 应该有测试覆盖

| 测试对象 | 原因 | 状态 |
|:---|:---|:---|
| `WorkflowEngine` 端到端 | 多节点 DAG + 分支 + 超时 | ⚠️ 仅单测 |
| `ChatServiceImpl` SSE 生命周期 | 连接异常处理 | ⚠️ 仅 mock |
| `McpClientServiceImpl` 生命周期 | 缓存/竞态/降级 | ❌ 无 |
| `ProviderAdapter` SSE 解析 | 各厂商协议差异 | ❌ 无 |
| `ChunkService` RAG 检索 | 向量搜索 + 降级 | ❌ 无 |

#### P2 — 可以后补

| 类别 | 原因 |
|:---|:---|
| CRUD Controller / Mapper XML | 样板代码，Service 层已验证 |
| 游标分页 | MyBatis-Plus 封装 |
| 前端组件单测 | TS 类型检查 + vitest 基础覆盖即可，复杂交互用 e2e |

---

## 文档索引

### 核心文档

| 文档 | 用途 | 何时查看 |
|:---|:---|:---|
| [docs/README.md](docs/README.md) | 项目总览 | 了解项目整体情况 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计 | 开发新功能前了解模块结构 |
| [API-SPEC.md](docs/API-SPEC.md) | 接口规范 | 设计 API 接口 |
| [DATABASE.md](docs/guides/DATABASE.md) | 数据库规范 | MySQL 建表模板、索引分页、业务表 DDL、ClickHouse 日志库、游标分页优化 |
| [AUTH-WORKSPACE.md](docs/guides/AUTH-WORKSPACE.md) | 用户认证与工作空间 | 多租户架构、JWT 认证、数据隔离 |
| [DESIGN.md](DESIGN.md) | 设计系统规范 | 生成前端 UI 时参照，包含颜色、字体、间距、组件、布局等视觉令牌 |

### 日志文档

| 文档 | 用途 | 何时查看 |
|:---|:---|:---|
| [LOGGING.md](docs/guides/LOGGING.md) | 日志系统完整指南 | 记录和查看日志、架构设计、MQ 日志、监控、性能分析 |
| [deploy/infra/deploy/README.md](deploy/infra/deploy/README.md) | Vector + ClickHouse 部署 | 部署日志采集链路 |

### 研发过程文档

| 文档 | 用途 | 何时查看 |
|:---|:---|:---|
| [docs/specs/](docs/specs/) | 功能规格说明 | 实现前编写、实施中对照、完成后归档 |
| [docs/ADRs/](docs/ADRs/) | 架构决策记录 | 理解历史设计取舍 |

### 研发 Skills

| Skill | 用途 | 何时调用 |
|:---|:---|:---|
| [unit-test](.claude/skills/unit-test.md) | Java Service 方法单元测试 | 说"单测"、"写单测"、"unit test"或使用 /unit-test 命令 |
| [integration-test](.claude/skills/integration-test.md) | Spring Boot 模块集成测试 | 说"集成测试"、"integration test"或使用 /集成测试 命令 |
| [workspace-isolation-testing](.claude/skills/workspace-isolation-testing.md) | 工作空间隔离集成测试（integration-test 专项补充） | 新模块上线前 / 发现隔离 bug / 补隔离集成测 |
| [provider-adapter](.claude/skills/provider-adapter.md) | 新 LLM 供应商适配器开发 | 接入新的 LLM 供应商（如 OpenAI、Claude、DeepSeek 等） |
| [module-delivery](.claude/skills/module-delivery.md) | 标准化模块交付流程 | 新业务模块从需求到验收的完整交付 |

### 专项文档

| 文档 | 用途 | 何时查看 |
|:---|:---|:---|
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 线程池配置 | 调用外部 LLM API / 实现 SSE 流式响应 |
| [WORKFLOW.md](docs/guides/WORKFLOW.md) | 工作流引擎设计 | 开发工作流节点、理解节点类型和变量系统 |

### 运维文档

| 文档 | 用途 | 何时查看 |
|:---|:---|:---|
| [DEPLOYMENT.md](docs/DEPLOYMENT.md) | 部署与 CI/CD | 部署到生产环境、CI/CD 流水线、故障排查 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构与编码规范 | 模块结构、命名规范、分层职责 |

---

## 附录

### 项目结构

```
eify/
├── CLAUDE.md              # Claude Code 指导（根目录仅此 4 个文件）
├── DESIGN.md              # 设计系统规范（AI 编码助手的视觉参考）
├── start.sh               # 启动脚本
├── stop.sh                # 停止脚本
│
├── eify-common/           # 公共模块（工具类、常量、异常、DTO）
├── eify-auth/              # 认证与工作空间（JWT、用户管理、多租户隔离）
├── eify-provider/         # LLM 提供商模块
├── eify-agent/            # Agent 模块
├── eify-chat/             # 对话模块
├── eify-mcp/              # MCP 工具模块
├── eify-workflow/         # 工作流引擎模块
├── eify-knowledge/        # 知识库模块
├── eify-app/              # 应用模块（Spring Boot 入口）
├── eify-web/              # 前端模块（Vue 3 + TypeScript）
│
├── docs/                  # 项目文档（按开发者查阅路径组织）
│   ├── ARCHITECTURE.md    # 架构 + 编码规范
│   ├── API-SPEC.md        # API 接口规范
│   ├── DEPLOYMENT.md      # 部署与 CI/CD（本地、Docker、K8s、Jenkins）
│   ├── ADRs/              # 架构决策记录（ADR）
│   └── guides/            # HOW-TO 指南
│       ├── AUTH-WORKSPACE.md  # 认证 + 多租户
│       ├── DATABASE.md        # MySQL + ClickHouse + 游标分页
│       ├── LOGGING.md         # 日志系统完整指南
│       └── WORKFLOW.md        # 工作流引擎设计
│
├── scripts/               # 开发/运维工具脚本
├── Jenkinsfile            # CI/CD 流水线定义
├── deploy/                # 部署配置
│   ├── Dockerfile         # 后端镜像构建
│   ├── Dockerfile.web     # 前端镜像构建
│   ├── nginx.conf         # Nginx 反向代理
│   ├── k8s/               # K8s 清单（Deployment、Service、Ingress）
│   ├── sql/               # MySQL 初始化脚本（统一入口）
│   ├── clickhouse/        # ClickHouse DDL
│   ├── vector/            # Vector 采集配置
│   ├── postgresql/        # PostgreSQL pgvector 初始化脚本
│   ├── clickvisual/       # ClickVisual 配置
│   ├── optional/          # 可选组件（Jaeger 等）
│   └── *.yml              # Docker Compose 文件
│
└── .env.example           # 环境变量模板
```

### 环境配置

| 环境 | 配置文件 | 用途 | 部署方式 |
|:---|:---|:---|:---|
| **开发** | `application-dev.yml` | 本地开发 | Docker Compose |
| **测试** | `application-test.yml` | CCE Turbo K8s 自动化测试 | K8s Deployment |
| **预发布** | `application-staging.yml` | CCE Turbo K8s 预发布 | K8s Deployment |
| **生产** | `application-prod.yml` | CCE Turbo K8s 生产 | K8s Deployment |
