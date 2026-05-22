# Provider 手动添加模型
`ADR-0012 manual-model-addition`

# Status
Accepted

# Date
2026-05-22

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

### 问题

某些嵌入模型供应商（如阿里云 DashScope / Qwen）不公开模型列表 API（无 `/v1/models` 端点或端点不返回完整模型信息），导致 Provider 连接测试时无法通过 `syncModels()` 自动发现嵌入模型。

当前 `model_config` 表的写入唯一路径是 `ProviderConnectionTestServiceImpl.syncModels()`，用户必须经过连接测试才能创建模型记录。缺少独立的模型创建入口。

**影响**：用户在知识库创建时无法选择这些供应商的嵌入模型，只能降级到全局 `application.yml` 配置。

### 现有流程

```
Provider 连接测试 → syncModels() → GET /v1/models → 批量 INSERT model_config
                                   ↑
                              唯一写入路径
```

缺失路径：用户直接在 Provider 管理界面手动创建 ModelConfig 记录。

### Decision drivers

- 用户需要为不公开模型列表 API 的供应商手动注册嵌入模型
- 复用现有 `ModelConfig` 表结构和 `ModelCategory` 枚举（ADR-0009 已引入）
- 遵循 CLAUDE.md 核心约束：工作空间隔离、ErrorCode 枚举、Flyway 幂等迁移
- 与现有 Provider 管理 UI 集成，不引入新的管理界面

# Considered Options

* **方案 A：在 application.yml 中配置额外模型列表** — 在全局配置文件中列出需要手动注册的模型。被拒绝原因：配置文件与数据库双写维护成本高；模型凭证无法使用 Provider 系统的 AES-256-GCM 加密存储；不支持工作空间隔离。

* **方案 B：提供 SQL 脚本让用户直接 INSERT** — 文档指导用户手动执行 SQL 插入模型记录。被拒绝原因：操作门槛高，普通用户不会用；缺少校验（workspace_id、provider 存在性、enabled 状态）；绕过了 API 层的权限控制。

* **方案 C：新增 API 端点 + 前端表单（最终选择）** — `POST /api/v1/providers/{id}/models` 端点 + Provider 管理界面"手动添加模型"按钮和表单对话框。

# Decision

**新增 `POST /api/v1/providers/{id}/models` 端点，允许用户为指定 Provider 手动创建 ModelConfig 记录。前端在 Provider 编辑对话框中增加"手动添加模型"按钮，弹出表单填写模型信息。**

核心原则：
- 后端校验：Provider 存在且启用 + 工作空间隔离
- 前端表单字段：`modelId`（必填）、`displayName`（必填）、`category`（必填下拉）、`dimension`（可选，写入 `extraParams.dimension`）
- 提交成功后刷新模型列表，无需重新连接测试
- 遵循项目既有 UI 模式：`EifyFormDialog` + scoped CSS + `--eify-*` 设计变量

## Consequences

### 优势
- 填补了无公开模型 API 供应商的使用缺口（DashScope / Qwen 等）
- 模型创建与 Provider 管理在同一界面，操作路径短
- 复用现有 `ModelConfig` 表结构和 `ModelCategory` 枚举，无需新表
- 手动创建的模型与自动同步的模型在系统中完全等价

### 权衡
- 手动添加的模型字段（如 `contextSize`）可能不准确，依赖用户自行填写
- 无法自动验证模型是否真实存在于供应商侧（需在知识库文档处理时才能验证）
- `extraParams` 以 JSON 自由格式提交，缺少前端结构校验

# Details

## API 端点

```
POST /api/v1/providers/{id}/models
```

### 请求体

```java
@Data
public class ModelCreateRequest {
    @NotBlank
    private String modelId;       // API 标识，如 "text-embedding-v4"
    @NotBlank
    private String displayName;   // 显示名，如 "Qwen text-embedding-v4"
    @NotNull
    private Integer category;     // 0=CHAT, 1=EMBEDDING, 2=RERANK, 3=MULTIMODAL
    private Integer contextSize;  // 上下文大小（可选）
    private JsonNode extraParams; // JSON 扩展参数，含 dimension 等
    private Integer enabled;      // 默认 1
}
```

### 后端校验

```java
// ProviderServiceImpl.createModel()
1. 校验 provider 存在且 enabled=1（含 workspace_id 过滤）
2. 构建 ModelConfig 实体，设置 modelCategory = ModelCategory.fromValue(request.getCategory())
3. WorkspaceGuard.bind(modelConfig)
4. modelConfigMapper.insert(modelConfig)
5. 返回 ModelConfigInfo
```

### 前端表单

在 Provider 编辑对话框中，"Sync Models"按钮旁边增加"手动添加模型"按钮：

- `modelId`（必填）：text input，placeholder 示例 `text-embedding-v4`
- `displayName`（必填）：text input
- `category`（必填）：select — Chat / Embedding / Rerank / Multimodal
- `dimension`（可选）：number input，提交时包装为 `{ "dimension": <value> }` 写入 `extraParams`

## 改动文件清单

| 模块 | 文件 | 改动类型 |
|:---|:---|:---|
| eify-provider | `domain/dto/request/ModelCreateRequest.java` | 新建 — 请求 DTO |
| eify-provider | `service/ProviderService.java` | 修改 — 新增 `createModel()` 接口方法 |
| eify-provider | `service/impl/ProviderServiceImpl.java` | 修改 — 实现 `createModel()` |
| eify-provider | `controller/ProviderController.java` | 修改 — 新增 `@PostMapping("/{id}/models")` |
| eify-web | `api/provider.ts` | 修改 — 新增 `createProviderModel()` 方法 |
| eify-web | `views/ProviderList.vue` | 修改 — 新增"手动添加模型"按钮 + `EifyFormDialog` |
| eify-web | `i18n/locales/zh-CN.json` | 修改 — 新增 6 个 i18n key |
| eify-web | `i18n/locales/en-US.json` | 修改 — 新增 6 个 i18n key |

## 参考
- [ADR-0009 知识库嵌入模型可配置化](./ADR-0009-embedding-model-per-knowledge-base.md) — `ModelCategory` 枚举和 `ModelConfig` 扩展
- [ADR-0003 Provider 模型自动同步](./ADR-0003-provider-model-auto-sync.md) — `syncModels()` 自动发现机制
- [CLAUDE.md](../../CLAUDE.md) — 工作空间隔离、ErrorCode 枚举规范
