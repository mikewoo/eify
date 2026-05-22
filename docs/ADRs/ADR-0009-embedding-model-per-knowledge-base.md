# 知识库嵌入模型可配置化
`ADR-0009 embedding-model-per-knowledge-base`

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

1. **嵌入模型硬编码**：`EmbeddingStrategyImpl` 从 `application.yml` 的 `knowledge.embedding.*` 读取单一全局配置（provider、model、apiUrl、apiKey、dimension），所有知识库共用同一个嵌入模型。用户无法按知识库选择不同模型。
2. **配置与实际存储脱节**：`KnowledgeBase` 表已有 `embedding_model` 和 `vector_dimension` 字段，创建/编辑 API 也正确读写，但嵌入管线（`DocumentServiceImpl.processDocument()`、`ChatServiceImpl` RAG）完全忽略这两个字段。
3. **Provider 体系未覆盖嵌入**：`eify-provider` 模块已完整管理 LLM 供应商和模型（Provider + ModelConfig），但它只服务聊天场景——`ProviderAdapter` 仅有 `chat()`/`streamChat()`/`testConnection()`，无嵌入相关能力。
4. **维度硬编码**：pgvector `document_chunk.embedding` 列为 `VECTOR(1024)`，无法容纳不同维度模型的输出。
5. **前端模型选择是假动作**：`KnowledgeView.vue` 有嵌入模型下拉框，但 3 个选项是硬编码的，不与 Provider 系统联动。
6. **模型分类缺失**：`ModelConfig` 表无法区分聊天模型和嵌入模型。未来还有 rerank、多模态等类型需要管理。

### 现有基础设施

| 组件 | 状态 | 说明 |
|:---|:---|:---|
| `Provider` 表 | ✅ 已有 | 存储供应商名称、类型、baseUrl、authConfig（AES-256-GCM 加密） |
| `ModelConfig` 表 | ✅ 已有 | 存储模型名称、model_id、context_size、extra_params |
| `ModelConfig.model_category` | ❌ 不存在 | 无法区分 CHAT / EMBEDDING / RERANK / MULTIMODAL |
| `ProviderAdapter.testConnection()` | ✅ 已有 | 已调用 `/v1/models` 并同步模型到 model_config |
| `ProviderAdapter` 嵌入方法 | ❌ 不存在 | 仅 chat() / streamChat() / testConnection() |
| `EmbeddingStrategyImpl` | ✅ 已有 | 始终读 `EmbeddingConfig` 全局配置，绕过 Provider 系统 |
| `KnowledgeBase.embeddingModelId` | ❌ 不存在 | 无法关联到 Provider 系统的模型记录 |
| `document_chunk.embedding` | VECTOR(1024) | 固定维度，无法支持多维度模型 |
| 前端嵌入模型选择 | ❌ 硬编码 | 3 个静态选项，不与 Provider 联动 |

### Decision drivers

- 用户做知识库处理时应能选择不同嵌入模型（不同供应商、不同维度）
- 复用现有 Provider/ModelConfig 管理体系，不重复造轮子
- 已有知识库必须向后兼容（embedding_model_id=NULL → 降级到全局配置）
- 为未来模型类型（rerank、多模态）预留扩展空间
- 遵循 CLAUDE.md 核心约束：数据库索引、Flyway 幂等迁移、工作空间隔离、ErrorCode 枚举

# Considered Options

* **方案 A：扩展 EmbeddingConfig 支持多模型配置** — 在 `application.yml` 中列出多个嵌入模型配置，KnowledgeBase 引用配置名。被拒绝原因：配置文件和数据库双写维护成本高；模型凭证（API Key）在配置文件中明文不安全，而 Provider 系统已有 AES-256-GCM 加密存储。

* **方案 B：扩展 ProviderAdapter 接口加 embed() 方法** — 给 `ProviderAdapter` 增加 `embed()`、`embedBatch()` 方法，各适配器实现嵌入调用。被拒绝原因：`ProviderAdapter` 的核心职责是聊天，已有 4 个适配器（OpenAI、Anthropic、Ollama、OpenAI Compatible），给每个都加嵌入实现改动面大；部分供应商（Anthropic）不支持嵌入；嵌入 API 调用逻辑简单（POST + 解析 `data[].embedding`），不需要适配器模式的多态分派。

* **方案 C：新建独立的 EmbeddingProvider 模块** — 创建 `eify-embedding` 模块，独立管理嵌入供应商和模型。被拒绝原因：重复造轮子——嵌入供应商的凭证结构（baseUrl + apiKey）与 LLM 供应商完全一致；增加模块依赖复杂度；用户需要维护两套供应商配置。

* **方案 D：复用 Provider 体系 + 轻量路由层（最终选择）** — KnowledgeBase 通过 `embedding_model_id` FK 关联 `ModelConfig`，新增 `EmbeddingRouteResolver` 解析供应商凭证构建调用路由，`EmbeddingStrategyImpl` 根据路由动态切换模型，不改 `ProviderAdapter` 接口。

# Decision

**复用现有 Provider/ModelConfig 体系管理嵌入模型，新增轻量路由层（EmbeddingRoute + EmbeddingRouteResolver）连接嵌入管线与 Provider 系统，不侵入 ProviderAdapter 接口。**

核心原则：
- 嵌入模型是 `model_category=EMBEDDING` 的 ModelConfig 记录，与聊天模型同表管理
- `KnowledgeBase.embeddingModelId` 外键指向 `model_config.id`，NULL 时降级到全局配置
- `EmbeddingRouteResolver` 负责从 Provider/ModelConfig 解析 API 凭证，构建调用路由
- `EmbeddingStrategyImpl` 接受可选 `EmbeddingRoute` 参数，有则动态切换，无则用全局配置
- 模型分类用 TINYINT 列而非布尔标记，为新类型（RERANK、MULTIMODAL）预留空间

## Consequences

### 优势
- 用户可在创建知识库时从已同步的嵌入模型中灵活选择，不同 KB 可用不同模型
- 复用现有 Provider 凭证加密存储（AES-256-GCM），无需在配置文件中明文存储 API Key
- 已有知识库零影响：`embedding_model_id=NULL` → 降级到全局 `application.yml` 配置
- 模型分类体系可扩展至 rerank、多模态等未来场景
- `EmbeddingRouteResolver` 对 Provider/ModelConfig 失效有降级容错，不阻塞文档处理

### 权衡
- 查询 RAG 嵌入从 1 次变为 N 次（每个 KB 用各自模型嵌入查询）——实际部署通常 1-2 个 KB，嵌入延迟 100-300ms，可接受
- pgvector HNSW 索引要求所有被索引行维度相同——多维度共存需后续实现 partial index 按 knowledge_id 分区
- 编辑知识库回填时需要遍历供应商查找模型所属 Provider（前端多一次 API 查询）

# Details

## 模型分类体系

两层设计：主类别（TINYINT 列，可索引） + 能力标记（extra_params JSON）：

| 层级 | 存储 | 用途 |
|:---|:---|:---|
| `model_category` | TINYINT UNSIGNED NOT NULL DEFAULT 0 | SQL 过滤 `WHERE model_category=1` |
| `extra_params` | JSON | 细粒度能力：`supports_streaming`、`supports_vision`、`dimension` 等 |

| 值 | 枚举 | 用途 | 同步自动识别规则 |
|:---|:---|:---|:---|
| 0 | CHAT | 文本对话 | 默认值 |
| 1 | EMBEDDING | 文本向量化 | modelId 含 `embed`、`bge-`、`e5-`、`gte-`、`stella` |
| 2 | RERANK | 重排序 | modelId 含 `rerank` |
| 3 | MULTIMODAL | 多模态 | （预留，暂无自动识别规则） |

选择 TINYINT 而非 VARCHAR 原因：4 分类用 1 字节索引 vs VARCHAR(20) 约 80 字节（utf8mb4）；整数比较是数据库最快路径；MySQL ENUM 类型扩展需 ALTER TABLE，TINYINT 无此问题。

## 数据流

```
KnowledgeBase.embeddingModelId (FK → model_config.id)
       │
       ▼
EmbeddingRouteResolver.resolve(kb)
       │
       ├── kb.embeddingModelId == null → EmbeddingRoute.empty()
       │
       └── kb.embeddingModelId != null
                │
                ├── modelConfigMapper.selectById(id)
                │       └── 校验 enabled=1
                ├── providerService.getEntityById(modelConfig.providerId)
                │       └── 校验 enabled=1
                ├── extractApiKey(provider.authConfig)  → apiKey
                ├── buildEmbeddingUrl(provider.baseUrl) → apiUrl
                │
                └── EmbeddingRoute{apiUrl, apiKey, modelId, dimension}
                         │
                         ▼
              EmbeddingStrategyImpl.embedBatch(texts, route)
                         │
                         ├── route.isPresent() → 用 route 值构造 HTTP 请求
                         └── route.isEmpty()  → 降级到 EmbeddingConfig 全局配置
```

## EmbeddingRoute 值对象

`eify-knowledge/src/main/java/com/eify/knowledge/route/EmbeddingRoute.java`

```java
@Value @Builder
public class EmbeddingRoute {
    String apiUrl;       // {provider.baseUrl}/embeddings
    String apiKey;       // 从 provider.authConfig 解密
    String modelId;      // model_config.model_id
    int dimension;       // kb.vectorDimension 或 config.dimension
    Long modelConfigId;  // 来源追踪

    public static EmbeddingRoute empty() {
        return EmbeddingRoute.builder().dimension(0).build();
    }

    public boolean isPresent() {
        return apiUrl != null && modelId != null;
    }
}
```

## 容错策略

| 场景 | 行为 |
|:---|:---|
| `embeddingModelId` = NULL | 返回 `EmbeddingRoute.empty()`，EmbeddingStrategyImpl 降级到全局配置 |
| ModelConfig 不存在/已禁用 | warn 日志 + 返回 empty |
| Provider 不存在/已禁用 | warn 日志 + 返回 empty |
| 解析过程异常 | error 日志 + 返回 empty |

> 设计原则：选模型是增强功能，不应因模型配置错误阻塞文档处理。降级到全局配置确保已有流程不受影响。

## 数据库迁移

### MySQL V2 — `model_category` + `embedding_model_id`

```sql
-- model_config 新增 model_category（TINYINT，DEFAULT 0=CHAT）
-- 遵循 CLAUDE.md Flyway 幂等迁移模板（INFORMATION_SCHEMA.COLUMNS 检查）
ALTER TABLE `model_config`
    ADD COLUMN `model_category` TINYINT UNSIGNED NOT NULL DEFAULT 0
    COMMENT '模型主类别：0=CHAT, 1=EMBEDDING, 2=RERANK, 3=MULTIMODAL'
    AFTER `model_id`;

ALTER TABLE `model_config`
    ADD INDEX `idx_model_category` (`model_category`);

-- knowledge_base 新增 embedding_model_id（FK → model_config.id）
ALTER TABLE `knowledge_base`
    ADD COLUMN `embedding_model_id` BIGINT UNSIGNED DEFAULT NULL
    COMMENT '嵌入模型 FK -> model_config.id，NULL 时降级到全局配置'
    AFTER `embedding_model`;

ALTER TABLE `knowledge_base`
    ADD INDEX `idx_embedding_model_id` (`embedding_model_id`);
```

### pgvector V2 — 移除固定维度

```sql
ALTER TABLE document_chunk ALTER COLUMN embedding TYPE VECTOR;
DROP INDEX IF EXISTS idx_chunk_embedding;
CREATE INDEX IF NOT EXISTS idx_chunk_embedding ON document_chunk
    USING hnsw (embedding vector_cosine_ops);
```

> 已知约束：HNSW 索引要求被索引行维度一致。多数部署中所有 KB 使用同维度，全局索引可正常工作。多维度共存需后续实现 partial index `WHERE knowledge_id = ?`。

## 改动文件清单

### 后端

| 模块 | 文件 | 改动类型 |
|:---|:---|:---|
| eify-app | `V2__model_category_and_embedding_model_id.sql` | 新建 — MySQL 迁移 |
| eify-app | `V2__flexible_vector_dimension.sql` | 新建 — pgvector 迁移 |
| eify-provider | `ModelCategory.java` | 新建 — TINYINT 枚举，含 `@EnumValue` + `fromModelId()` |
| eify-provider | `ModelConfig.java` | 修改 — 新增 `modelCategory` 字段 |
| eify-provider | `ProviderResponse.java` | 修改 — `ModelConfigInfo` 新增 `category`、`extraParams` |
| eify-provider | `ProviderServiceImpl.java` | 修改 — `getModels()` 新增 `category` 过滤；`toModelConfigInfo()` 新增映射 |
| eify-provider | `ProviderController.java` | 修改 — `GET /{id}/models` 新增 `?category=` 可选参数 |
| eify-provider | `ProviderConnectionTestServiceImpl.java` | 修改 — `syncModels()` 调用 `ModelCategory.fromModelId()` 自动分类 |
| eify-knowledge | `EmbeddingRoute.java` | 新建 — 值对象 |
| eify-knowledge | `EmbeddingRouteResolver.java` | 新建 — 路由解析服务 |
| eify-knowledge | `EmbeddingStrategy.java` | 修改 — 新增 `embed(text, route)` / `embedBatch(texts, route)` |
| eify-knowledge | `EmbeddingStrategyImpl.java` | 修改 — 实现带 route 参数的重载方法 |
| eify-knowledge | `KnowledgeBase.java` | 修改 — 新增 `embeddingModelId` 字段 |
| eify-knowledge | `KnowledgeCreateRequest.java` | 修改 — 新增 `embeddingModelId`、`providerId` |
| eify-knowledge | `KnowledgeUpdateRequest.java` | 修改 — 新增 `embeddingModelId` |
| eify-knowledge | `KnowledgeServiceImpl.java` | 修改 — 校验 `embeddingModelId`（工作空间隔离 + 启用状态） |
| eify-knowledge | `DocumentServiceImpl.java` | 修改 — `embedBatch` 传入 route |
| eify-common | `ErrorCode.java` | 修改 — 新增 `EMBEDDING_MODEL_NOT_AVAILABLE(7007)` |
| eify-chat | `ChatServiceImpl.java` | 修改 — RAG 按 KB 独立嵌入 |

### 前端

| 文件 | 改动类型 |
|:---|:---|
| `provider.ts` | 修改 — `ModelConfigInfo` 新增 `category`、`extraParams`；`getProviderModels` 支持 `?category=` |
| `knowledge.ts` | 修改 — 新增 `embeddingModelId` 字段 |
| `KnowledgeView.vue` | 修改 — 硬编码替换为 Provider→Model 级联选择器 + 维度自动填充 |
| `zh-CN.json` / `en-US.json` | 修改 — 新增 7 个 i18n key |

## 参考
- [CLAUDE.md](../../CLAUDE.md) — 核心约束与开发规范
- [ADR-0003 Provider 模型自动同步](./ADR-0003-provider-model-auto-sync.md) — 模型同步机制
- [ADR-0004 MySQL + pgvector 双存储](./ADR-0004-dual-storage-mysql-pgvector.md) — 向量存储架构
- [ADR-0011 嵌入端点路径 Adapter 化](./ADR-0011-embedding-endpoint-adapter.md) — 后续细化：将嵌入端点路径从硬编码改为 Adapter 驱动
- [ADR-0012 Provider 手动添加模型](./ADR-0012-manual-model-addition.md) — 后续补充：为无公开模型 API 的供应商提供手动模型创建入口
- [AUTH-WORKSPACE.md](../guides/AUTH-WORKSPACE.md) — 工作空间隔离
- [DATABASE.md](../guides/DATABASE.md) — 数据库规范（Flyway 幂等迁移、索引策略）
