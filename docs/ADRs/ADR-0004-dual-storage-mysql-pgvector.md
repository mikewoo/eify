# 双存储架构：MySQL（业务数据）+ PostgreSQL pgvector（向量数据）
`ADR-0004 dual-storage-mysql-pgvector`

# Status
Superseded

> 本 ADR 已被 [ADR-0013 单 PostgreSQL 17 数据库统一存储](./ADR-0013-single-postgresql17-database.md) 替代（2026-06）。
> MySQL + pgvector 双存储架构已迁移为单 PostgreSQL 17 实例承载全部业务表与向量表。
> 详见 [MySQL → PG17 迁移设计](../specs/2026-06-05-mysql-to-pg17-migration-design.md)。

# Date
2025-Q2

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

知识库模块需要对文档进行分块、向量化和语义检索。`document_chunk` 表包含高维 Embedding 向量，MySQL 不具备向量索引能力，将向量存为 JSON 数组再应用层计算会导致全表扫描。

### Decision drivers
- 需要原生向量索引支持（HNSW 近似最近邻搜索）
- 业务数据已有成熟的 MyBatis-Plus + MySQL 体系（工作空间隔离、软删除、自动填充）
- 单机开发环境资源有限，不能引入过多基础设施

# Considered Options
* **方案 A：MySQL 单一存储** — 向量字段用 BLOB/TEXT 存 JSON 数组，应用层做余弦相似度计算。全表扫描，性能不可接受。
* **方案 B：MySQL + pgvector** — 业务数据走 MySQL，向量数据走 PostgreSQL pgvector 扩展。各司其职，pgvector 提供原生 HNSW 向量索引。
* **方案 C：专用向量数据库（Milvus/Qdrant/Weaviate）** — 独立向量服务，功能强大但运维复杂，对 MVP 阶段过度。

# Decision

**选择方案 B：MySQL + PostgreSQL pgvector 双存储。**

- MySQL（Druid + MyBatis-Plus）：存储知识库元信息（`knowledge_base`）、文档元信息（`document`）
- PostgreSQL pgvector（HikariCP + JdbcTemplate）：存储分块内容和 Embedding 向量（`document_chunk`）

## Consequences

### 优势
- pgvector 提供原生 HNSW 向量索引，检索性能远优于 MySQL 应用层计算
- 双存储各司其职，业务数据享受 MyBatis-Plus 体系，向量数据享受 pgvector 原生能力
- `@Lazy` 延迟初始化确保 PostgreSQL 故障不阻塞主应用启动

### 权衡
- 增加运维复杂度（需同时维护 MySQL 和 PostgreSQL 两个数据库）
- 事务无法跨 MySQL 和 PostgreSQL（计数类数据以 MySQL 为准，pgvector 数据可从原始文档重建）
- `document_chunk` 不冗余存储 `workspace_id`，通过 `knowledge_id` 间接关联，检索时需先校验工作空间

# Details

## 数据管线

```
上传 → 解析 → 分块 → Embedding → 写入 pgvector
 │       │      │        │              │
 │       │      │        │     ┌────────┴────────┐
 │       │      │        │     │  batchInsert()  │
 │       │      │        │     │  INSERT INTO    │
 │       │      │        │     │  document_chunk │
 │       │      │        │     │  (content,      │
 │       │      │        │     │   embedding,    │
 │       │      │        │     │   chunk_hash)   │
 │       │      │        │     └─────────────────┘
 │       │      │        │
 │       │      │        └── EmbeddingStrategy.embedBatch()
 │       │      │             (调用 LLM Embedding API)
 │       │      │
 │       │      └── splitText(content, chunkSize, overlap)
 │       │           三级递归：段落 → 句子 → 字符
 │       │
 │       └── extractContent(doc)
 │            PDF (PDFBox) / Word (POI) / Markdown / TXT
 │
 └── uploadDocument(file) → MySQL document 表
```

## 写入路径（DocumentServiceImpl.processDocument）

1. `documentRepository.updateProcessStatus(id, 1)` — 标记"处理中"（MySQL）
2. `extractContent(doc)` — 解析 PDF/Word/MD/TXT 为纯文本
3. `splitText(content, chunkSize, overlap)` — 三级递归分块
4. `computeHash(chunk)` → `chunkRepository.findExistingHashes()` — 增量去重
5. `embeddingStrategy.embedBatch(newTexts)` — 调用 LLM 向量化
6. `chunkRepository.batchInsert(chunks)` — 写入 pgvector
7. `knowledgeRepository.incrementChunkCount()` — 更新计数（MySQL）

## 读取路径（RetrievalStrategyImpl）

| 检索模式 | SQL 算子 | 说明 |
|:---|:---|:---|
| `vectorRetrieval` | `<=>` 余弦距离 | 查询 Embedding → pgvector HNSW 索引检索 |
| `keywordRetrieval` | `ILIKE` | PostgreSQL 大小写不敏感全文匹配 |
| `hybridRetrieval` | 两者合并 | 归一化加权：norm(vector) × α + density(keyword) × β |

## 关键设计决策

1. **document 与 document_chunk 分属不同数据库**：`document` 需工作空间隔离、软删除、自动填充，适合 MyBatis-Plus + MySQL；`document_chunk` 核心操作是向量检索，需 pgvector 原生能力。
2. **JdbcTemplate 而非 MyBatis-Plus 操作 pgvector**：MyBatis-Plus 对 pgvector 类型无原生支持，JdbcTemplate 可直接写 `?::vector` 类型转换语法。
3. **`@Lazy` 延迟初始化**：PostgreSQL 是可选基础设施，不应阻塞应用启动；开发者本地可能只跑 MySQL。
4. **通过 `knowledge_id` 间接隔离**：`document_chunk` 不冗余存储 `workspace_id`，检索时先验证 `knowledge_base.workspace_id`，通过后才查询 pgvector。

## DDL

```sql
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document_chunk (
    id              BIGSERIAL PRIMARY KEY,
    knowledge_id    BIGINT  NOT NULL,
    document_id     BIGINT  NOT NULL,
    chunk_index     INT     NOT NULL,
    content         TEXT    NOT NULL,
    embedding       VECTOR(1024),
    chunk_hash      CHAR(64) NOT NULL,
    enabled         SMALLINT NOT NULL DEFAULT 1,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_chunk_embedding ON document_chunk
    USING hnsw (embedding vector_cosine_ops);
```

## 参考
- [DATABASE.md](../guides/DATABASE.md) — 数据库规范
