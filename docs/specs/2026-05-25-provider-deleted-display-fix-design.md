# Provider 删除后展示修复设计

## Status

Approved

## Date

2026-05-25

## Owner

mingming

## Deciders

mingming

## Context

当 Provider 被软删除后，Agent 编辑页和 Workflow LLM 节点编辑页展示的是裸数字 `providerId` 而非 provider 名称。根因有两层：

1. **后端**：`@TableLogic` 自动过滤 `deleted=1` 记录，`AgentServiceImpl` 查不到已删除 provider 时跳过名称填充；`WorkflowServiceImpl` 从来不解析 provider 名称
2. **前端**：`<el-select>` 下拉选项来自 `getProviderList()`（自动排除已删除），找不到匹配选项时就显示裸 ID

此外，从业务可用性角度，被引用的 Provider 不应允许删除。

## Decision

采用**删除前校验（主力防线）+ 展示兜底（安全网）**的策略。

### 删除前校验

`ProviderServiceImpl.delete()` 增加两层检查：

1. **Agent 引用**：查询 `ai_agent` 表 `default_provider_id = providerId AND deleted = 0`，有记录则抛 `PROVIDER_IN_USE`
2. **Workflow LLM 节点引用**：查询 `workflow_node` 表，对 `config` JSON 中 `providerId` 做匹配（`deleted=0` 的 workflow），有记录则抛 `PROVIDER_IN_USE_BY_WORKFLOW`

级联删除 `ai_model` 和 `provider_model_config` 保持现有逻辑不变。

### 展示兜底

后端不绕过 `@TableLogic`，只在查不到 provider 时标记不可用：

- `AgentResponse` 新增 `defaultProviderAvailable` 字段（`Boolean`，默认 `true`）
- `toBasicResponse()`：批量查 provider 名称，查不到的设 `defaultProviderAvailable=false`、`defaultProviderName=null`
- `toFullResponse()`：查不到 provider 时设 `defaultProviderAvailable=false`、`defaultProviderName=null`
- `WorkflowServiceImpl.toNodeDetail()`：解析 config JSON 中 `providerId`，查不到则注入 `providerAvailable: false`

前端检查标记：

- `AgentList.vue`：`defaultProviderAvailable === false` 时显示 `t('provider.unavailable')`
- `WorkflowEdit.vue`：LLM 节点 provider 下拉同理
- i18n key：中文 `(不可用)` / 英文 `(Unavailable)`

## Considered Options

| 方案 | 描述 | 拒绝原因 |
|:---|:---|:---|
| A. 反范式化存储 | `ai_agent` 加 `default_provider_name` 列 | 需改表结构，provider 改名需同步 |
| B. 后端占位兜底 | Service 层查不到时直接填占位文本 | **采用** |
| C. 绕过 @TableLogic | 自定义 SQL 查全部记录 | 破坏 ORM 一致性，两套查询路径 |

## Details

### 后端变更清单

| 文件 | 变更 |
|:---|:---|
| `eify-common/.../error/ErrorCode.java` | 新增 `PROVIDER_IN_USE`、`PROVIDER_IN_USE_BY_WORKFLOW` |
| `eify-common/.../i18n/messages.properties` | 新增中文错误消息 |
| `eify-common/.../i18n/messages_en_US.properties` | 新增英文错误消息 |
| `eify-provider/.../service/impl/ProviderServiceImpl.java` | `delete()` 增加 Agent + Workflow 引用校验 |
| `eify-agent/.../dto/AgentResponse.java` | 新增 `defaultProviderAvailable` 字段 |
| `eify-agent/.../service/impl/AgentServiceImpl.java` | `toBasicResponse()` / `toFullResponse()` 标记 provider 可用性 |
| `eify-workflow/.../service/impl/WorkflowServiceImpl.java` | `toNodeDetail()` 解析 providerId，注入 `providerAvailable` |

### 前端变更清单

| 文件 | 变更 |
|:---|:---|
| `eify-web/src/i18n/locales/zh-CN.json` | 新增 `provider.unavailable: "(不可用)"` |
| `eify-web/src/i18n/locales/en-US.json` | 新增 `provider.unavailable: "(Unavailable)"` |
| `eify-web/src/views/AgentList.vue` | provider 列检查 `defaultProviderAvailable` 显示占位文本 |
| `eify-web/src/views/WorkflowEdit.vue` | LLM 节点 provider 下拉处理不可用情况 |

### 验证场景

#### 删除校验

| # | 场景 | 预期 |
|:---|:---|:---|
| 1 | 删除未被任何 Agent/Workflow 引用的 provider | 成功，级联删除 model 和 config |
| 2 | 删除被 1 个 Agent 引用的 provider | 阻止，返回 `PROVIDER_IN_USE` |
| 3 | 删除被多个 Agent 引用的 provider | 阻止，错误信息包含 Agent 数量 |
| 4 | 删除被 Workflow LLM 节点引用的 provider | 阻止，返回 `PROVIDER_IN_USE_BY_WORKFLOW` |
| 5 | 删除被 Agent 和 Workflow 同时引用的 provider | 阻止 |
| 6 | 删除被已软删除 Agent（`deleted=1`）引用的 provider | 成功 |

#### 展示兜底

| # | 场景 | 预期 |
|:---|:---|:---|
| 7 | Agent 列表 — provider 正常存在 | 显示 provider 名称 |
| 8 | Agent 列表 — provider 已删除 | 显示 `(不可用)` / `(Unavailable)` |
| 9 | Agent 列表 — defaultProviderId 为 null | 显示 `—` |
| 10 | Agent 列表 — 混合（部分正常、部分已删） | 各自正确显示 |
| 11 | Agent 详情 — provider 已删除 | `defaultProviderName=null`, `defaultProviderAvailable=false` |
| 12 | Workflow LLM 节点 — provider 已删除 | config JSON 注入 `providerAvailable: false` |
| 13 | Agent 编辑页 — provider 已删除 | `<el-select>` 显示 `(不可用)` |
| 14 | Workflow 编辑页 — provider 已删除 | LLM 节点 provider 下拉显示 `(不可用)` |

## Consequences

- **正面**：用户可以清楚看到 provider 不可用状态，不会困惑于裸数字 ID；被引用的 provider 不会被误删
- **负面**：删除时多两次数据库查询（Agent + WorkflowNode 引用计数）
- **风险**：WorkflowNode config JSON 中 `providerId` 的查询依赖 JSON 函数（`JSON_EXTRACT`），大数据量时需关注索引；目前 workflow 数量有限，风险可控
