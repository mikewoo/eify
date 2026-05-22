# 嵌入端点路径 Adapter 化
`ADR-0011 embedding-endpoint-adapter`

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

ADR-0009 实现了知识库按模型独立嵌入，新增了 `EmbeddingRouteResolver` 负责从 Provider/ModelConfig 解析 API 凭证并构建嵌入调用 URL。但 `buildEmbeddingUrl()` 方法硬编码拼接 `/embeddings`，完全绕过了 `ProviderAdapter` 体系。

对比 Chat 路径有完整的 Adapter 分派，嵌入路径缺乏同样的灵活性：

| 路径 | URL 构建方式 | 适配性 |
|:---|:---|:---|
| Chat | `ProviderAdapter.chat()` 各实现自行构建 | 每种 Provider 独立路径 |
| Embedding | `EmbeddingRouteResolver` 硬编码 `baseUrl + "/embeddings"` | 只支持 OpenAI 兼容规范 |

### 各 Adapter 嵌入 API 差异

| Provider 类型 | 嵌入端点 | 备注 |
|:---|:---|:---|
| OPENAI | `/v1/embeddings` | 默认规范 |
| OPENAI_COMPATIBLE | `/v1/embeddings` | 同 OpenAI |
| ANTHROPIC | N/A（无嵌入 API） | 沿用默认 |
| OLLAMA | `/api/embeddings` | 不同路径，不走 `/v1` 规范 |

Ollama 的嵌入 API 是 `POST /api/embeddings`，此前硬编码 `/embeddings` 在 Ollama 上实际不工作（Ollama 没有 `/v1/embeddings` 端点）。

### 与 ADR-0009 的关系

ADR-0009 曾考虑并拒绝了"方案 B：扩展 ProviderAdapter 接口加 embed() 方法"，理由是改动面大、部分供应商不支持嵌入、嵌入调用逻辑简单不需要多态分派。

本次方案与方案 B 有本质区别：只给 `ProviderAdapter` 加一个返回路径字符串的 `default` 方法，不涉及嵌入调用逻辑。这是一个轻量级的信息查询方法，而非行为分派。

### Decision drivers

- 消除 `EmbeddingRouteResolver` 中对嵌入端点路径的硬编码
- 与 Chat 路径的 Adapter 分派体系保持一致
- 修正 Ollama 嵌入端点路径（`/embeddings` → `/api/embeddings`）
- 向后兼容：已有 Provider 自动获得正确的端点路径
- 改动最小化：使用 Java `default` 方法，现有适配器无需逐一修改

# Considered Options

* **方案 A：在 EmbeddingRouteResolver 中维护 ProviderType → endpoint 映射表** — 在 `EmbeddingRouteResolver` 内部加一个 `Map<ProviderType, String>` 维护各类型的嵌入端点。被拒绝原因：端点路径与 Provider 类型强相关，放在 Adapter 体系外违反单一职责；未来新增 Provider 类型需要同时修改两处（Adapter + Resolver 映射表），维护成本高。

* **方案 B：给 ProviderAdapter 加 getEmbeddingEndpoint() default 方法（最终选择）** — 接口新增 `default String getEmbeddingEndpoint()` 返回 `"/embeddings"`，`OllamaAdapter` 覆盖返回 `"/api/embeddings"`。`EmbeddingRouteResolver` 注入 `ProviderAdapterFactory`，通过 adapter 获取端点路径。

# Decision

**在 `ProviderAdapter` 接口新增 `getEmbeddingEndpoint()` default 方法，由各 Adapter 实现定义自己的嵌入端点路径。`EmbeddingRouteResolver.buildEmbeddingUrl()` 改为通过 `ProviderAdapterFactory` 获取 adapter 并查询端点。**

核心原则：
- 使用 Java `default` 方法向后兼容，所有现有适配器自动获得 OpenAI 兼容默认值（`"/embeddings"`）
- 仅 `OllamaAdapter` 覆盖为 `"/api/embeddings"`
- `EmbeddingRouteResolver` 注入 `ProviderAdapterFactory`，根据端点路径特征选择 URL 规范化策略
- 端点路径由 Adapter 代码决定（非数据库存储），已有 Provider 自动获得对应路径，无需数据迁移

## Consequences

### 优势
- Chat 和 Embedding 两条路径的 URL 构建都由 `ProviderAdapter` 体系管理，架构一致
- 未来新增 Provider 类型时，嵌入端点和聊天端点在同一处定义，不会遗漏
- Ollama 嵌入从不可用变为可用（bug fix，非 breaking change）
- 改动面极小：ProviderAdapter 加 1 个 default 方法 + OllamaAdapter 加 1 行覆盖 + EmbeddingRouteResolver 重构 1 个私有方法

### 权衡
- `EmbeddingRouteResolver` 新增对 `ProviderAdapterFactory` 的依赖（原仅依赖 `ProviderService` + `ModelConfigMapper`）
- 端点路径规范化逻辑从"直接拼 `/embeddings`"变为"根据端点特征选择策略"，增加约 15 行代码

# Details

## ProviderAdapter 新增方法

```java
/**
 * 获取 Embedding API 端点路径（相对路径）。
 * 默认返回 OpenAI 兼容的 /embeddings，子类可覆盖。
 */
default String getEmbeddingEndpoint() {
    return "/embeddings";
}
```

## OllamaAdapter 覆盖

```java
@Override
public String getEmbeddingEndpoint() {
    return "/api/embeddings";
}
```

## EmbeddingRouteResolver 改造

注入 `ProviderAdapterFactory`，`buildEmbeddingUrl(Provider)` 改为：

```java
private String buildEmbeddingUrl(Provider provider) {
    ProviderAdapter adapter = adapterFactory.getAdapter(provider.getType());
    String endpoint = adapter.getEmbeddingEndpoint();
    String baseUrl = provider.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) return null;

    String normalized = baseUrl.endsWith("/")
        ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;

    // Ollama 类不使用 /v1 规范
    if (endpoint.startsWith("/api/")) {
        if (normalized.endsWith("/v1")) {
            normalized = normalized.substring(0, normalized.length() - 3);
        }
        return normalized + endpoint;
    }

    // OpenAI 兼容规范：统一到 /v1 层级
    int v1SlashIndex = normalized.indexOf("/v1/");
    if (v1SlashIndex >= 0) {
        normalized = normalized.substring(0, v1SlashIndex + 3);
    }
    if (!normalized.endsWith("/v1")) {
        normalized += "/v1";
    }
    return normalized + endpoint;
}
```

## 各 Provider 类型行为对比

| Provider 类型 | 改造前（硬编码） | 改造后（Adapter） | 是否变化 |
|:---|:---|:---|:---|
| OPENAI / OPENAI_COMPATIBLE | `baseUrl + /embeddings` | `normalizeV1(baseUrl) + /embeddings` | 规范等价 |
| ANTHROPIC | `baseUrl + /embeddings` | `normalizeV1(baseUrl) + /embeddings` | 规范等价 |
| OLLAMA | `baseUrl + /embeddings` | `baseUrl + /api/embeddings` | **修正** |

> Ollama 此前硬编码 `/embeddings` 在 Ollama 上实际不工作，本次改造是 bug fix 而非 breaking change。

## 改动文件清单

| 模块 | 文件 | 改动类型 |
|:---|:---|:---|
| eify-provider | `adapter/ProviderAdapter.java` | 修改 — 新增 `getEmbeddingEndpoint()` default 方法 |
| eify-provider | `adapter/impl/OllamaAdapter.java` | 修改 — 覆盖 `getEmbeddingEndpoint()` |
| eify-knowledge | `route/EmbeddingRouteResolver.java` | 修改 — 注入 `ProviderAdapterFactory`，重构 `buildEmbeddingUrl()` |

## 参考
- [ADR-0009 知识库嵌入模型可配置化](./ADR-0009-embedding-model-per-knowledge-base.md) — 本次改造的直接前置决策
- [ADR-0008 Provider Adapter 熔断器](./ADR-0008-circuit-breaker-provider-adapter.md) — ProviderAdapter 体系设计
- [CLAUDE.md](../../CLAUDE.md) — 核心约束与开发规范
