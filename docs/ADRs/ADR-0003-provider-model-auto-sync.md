# Provider 模型自动同步
`ADR-0003 provider-model-auto-sync`

# Status
Accepted

# Date
2026-05-19

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

### 问题
1. **Workflow LLM 节点**：选择模型供应商后，模型下拉框无法联动展示该供应商提供的可用模型。原因：前端调 `GET /api/v1/providers`（列表接口），响应使用 `toBasicResponse()` 不含 `modelConfigs`。
2. **Agent 表单**：模型选择使用硬编码的 `providerModelRecommendations`，与数据库中 `model_config` 表脱节，私有部署的模型无法覆盖。
3. **模型维护缺失**：`model_config` 表存在但没有 CRUD API，新增供应商后无机制填充可用模型。

### 现有基础设施
| 组件 | 状态 | 说明 |
|---|---|---|
| `model_config` 表 | ✅ 已有 | provider_id, name, model_id, context_size, extra_params |
| `ProviderAdapter.testConnection()` | ✅ 已有 | 已调用 `/v1/models` 获取模型列表，但只取 `modelCount`，模型数据被丢弃 |
| `toBasicResponse()` | ❌ 不含 models | 列表接口不返回 modelConfigs |
| `toFullResponse()` | ✅ 含 models | 详情接口返回 modelConfigs，但前端未充分利用 |
| 模型 CRUD API | ❌ 不存在 | 无法通过 API 管理模型 |

### Decision drivers
- 用户不应手动输入模型名称和 model_id（易出错、体验差）
- 连接测试已有完整模型列表，可作为副产品写入
- 需支持私有部署模型（不在任何推荐列表中）

# Considered Options
* **方案 A：手动维护模型（CRUD API + UI）** — 用户自行创建模型记录。对通用平台用户需自行查阅文档填模型名，易出错；对私有部署需手动列出所有模型，体验差。
* **方案 B：列表接口返回 modelConfigs** — 将 `list()` 的 `toBasicResponse()` 改为 `toFullResponse()`。列表接口会 N+1 查询（每个 provider 查一次 model_config），且前端只需在选中供应商时才需要模型列表。
* **方案 C：覆盖式同步** — 每次同步 DELETE 不在 API 返回中的模型，再 INSERT 新模型。用户可能添加了 API 没有暴露的模型（如内部测试模型），覆盖同步会丢失数据。

# Decision

**连接测试成功后自动将返回的模型列表写入 `model_config`，用"同步模型"替代手动维护。**

核心原则：
- 用户只需配好供应商（名称、类型、Base URL、API Key），点击"同步模型"即可自动拉取模型列表
- 无需手动输入模型名称、model_id 等字段
- "同步模型"是强制操作——未同步的供应商在 LLM 节点和 Agent 中不可选
- 同步采用只新增、不删除、不覆盖策略

## Consequences

### 优势
- 用户零手动输入模型信息，连接测试副产品即可完成模型同步
- 支持私有部署模型（Ollama、OpenAI 兼容接口）
- 不删除用户手动添加的特殊模型

### 权衡
- `context_size` 和 `extra_params` 无法自动探测（`/v1/models` 不返回），默认留空
- 需各适配器实现 `extractModelNames()` 方法（不同厂商返回格式不同）

# Details

## 同步流程

```
用户点击"同步模型"
       │
       ▼
 ProviderConnectionTestServiceImpl.testConnection(provider)
       │
       ├── adapter.doTest(provider)           # GET /v1/models (或 Ollama /api/tags)
       │       └── 返回原始 JSON 响应体
       │
       ├── adapter.extractModelNames(body)    # 提取模型 ID 列表
       │       └── OpenAI: data[].id
       │       └── Ollama:  models[].name
       │
       └── syncModels(provider, modelNames)   # 增量同步到 model_config
               │
               ├── 查询已有 model_id 集合
               ├── 过滤已存在的
               └── INSERT 新模型（name=model_id, context_size=0）
```

## 同步策略：只新增

| API 返回的模型 | 已有 model_config | 结果 |
|:---|:---|:---|
| deepseek-chat | (不存在) | INSERT |
| deepseek-reasoner | (不存在) | INSERT |
| gpt-4o | gpt-4o (已存在) | 跳过 |
| claude-sonnet | claude-sonnet (已存在) | 跳过 |

## 各适配器实现

| 适配器 | API 端点 | `extractModelNames` 解析路径 |
|---|---|---|
| `OpenAiAdapter` | `GET {baseUrl}/v1/models` | `data[].id` |
| `OpenAiCompatibleAdapter` | 同上（继承） | `data[].id` |
| `AnthropicAdapter` | `GET {baseUrl}/v1/models` | `data[].id` |
| `OllamaAdapter` | `GET {baseUrl}/api/tags` | `models[].name` |

## 前后端改动清单

### 后端
| 文件 | 改动 |
|---|---|
| `AbstractProviderAdapter.java` | 新增 `extractModelNames()` 方法（含默认实现） |
| `OllamaAdapter.java` | 覆盖 `extractModelNames()`，解析 `models[].name` |
| `ConnectionTestResult.java` | 新增 `modelNames` 字段 |
| `ProviderConnectionTestServiceImpl.java` | 新增 `syncModels()` 方法 |
| `ProviderController.java` | 新增 `GET /{id}/models` 接口 |
| `ProviderServiceImpl.java` | 新增 `getModels(id)` 方法 |

### 前端
| 文件 | 改动 |
|---|---|
| Provider 表单 | "测试连接"按钮 → "同步模型"；创建后引导同步 |
| `provider.ts` | 新增 `getProviderModels(id)` |
| `WorkflowEdit.vue` | `onProviderChange` 异步调用 `getProviderModels`；过滤未同步供应商 |
| `AgentList.vue` | `handleProviderChange` 调用 API 替代硬编码；过滤未同步供应商 |
| i18n | 新增 `provider.syncModels`、`provider.syncModelsSuccess`、`provider.noModelsHint` 等键 |
