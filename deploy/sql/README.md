# Eify 数据库 SQL 文件说明

## 概述

本目录包含 Eify 项目的数据库参考脚本。MySQL 和 PostgreSQL/pgvector 的迁移均由 **Flyway** 在应用启动时自动执行。

## 迁移方式

### Flyway（推荐）

数据库表结构由 Flyway 在应用启动时自动创建：

```
# MySQL
eify-app/src/main/resources/db/migration/V1__init.sql
eify-app/src/main/resources/db/migration/V4__model_category_and_embedding_model_id.sql
eify-app/src/main/resources/db/migration/V5__name_workspace_deleted_unique.sql

# PostgreSQL（pgvector 向量存储）
eify-app/src/main/resources/db/migration-pg/V1__init_pgvector.sql
eify-app/src/main/resources/db/migration-pg/V2__flexible_vector_dimension.sql
```

- **本地开发**：启动应用时自动执行迁移
- **Docker 部署**：Backend 容器启动时自动执行迁移
- **无需手动导入 SQL**

### 手动导入（备用）

如需手动导入或参考表结构：

```bash
# MySQL
mysql -u root -p eify < deploy/sql/init_eify_mysql.sql

# PostgreSQL（pgvector 向量存储）
psql -U postgres -d eify_vector < deploy/sql/init_eify_vector_pgsql.sql
```

## 文件清单

| 文件 | 说明 | 用途 |
|------|------|------|
| `init_eify_mysql.sql` | MySQL 完整 DDL 参考 | Flyway 迁移源文件，手动导入备用 |
| `init_eify_vector_pgsql.sql` | PostgreSQL pgvector DDL 参考 | Flyway 迁移源文件，手动导入备用 |
| `README.md` | 本说明文件 | 文档说明 |

## pgvector（PostgreSQL 向量存储）

Eify 使用 PostgreSQL + pgvector 扩展存储文档分块的向量嵌入，支撑知识库 RAG 检索。

### 迁移管理

pgvector 表结构由 Flyway 独立实例管理：
- 迁移文件：`db/migration-pg/`
- 历史表：`flyway_schema_history_pg`（与 MySQL 的 `flyway_schema_history` 隔离）
- `baselineOnMigrate: true`：已有数据库自动基线，不影响存量数据

### 表结构

- **`document_chunk`**：文档分块向量表
  - `embedding VECTOR` — 向量嵌入字段（灵活维度，由知识库模型决定）
  - `chunk_hash CHAR(64)` — 内容 SHA-256 哈希，用于去重和增量更新
  - 索引：HNSW 向量索引（余弦距离）+ 业务索引（workspace_id / knowledge_id / document_id / chunk_hash）

### 容器部署

Docker Compose 仅挂载数据目录，表迁移由 Flyway 负责：

```yaml
pgvector:
  image: pgvector/pgvector:pg16
  volumes:
    - pgvector-data:/var/lib/postgresql/data
```

### 相关文档

- [DATABASE.md](../../docs/guides/DATABASE.md) — 数据库设计规范（含向量存储架构）
- [双存储设计决策](../../docs/ADRs/ADR-0004-dual-storage-mysql-pgvector.md) — 为什么选择 MySQL + pgvector 分离

### MySQL 初始化脚本包含的内容

1. **用户与工作空间模块**
   - `ai_user` - 用户表
   - `ai_workspace` - 工作空间表
   - `ai_workspace_member` - 工作空间成员表
   - `ai_workspace_invite` - 工作空间邀请码表
   - `ai_user_session` - 用户会话表

2. **Provider 模块**
   - `provider` - 模型供应商表
   - `model_config` - 模型配置表
   - `provider_health` - 供应商健康状态表

3. **Agent 模块**
   - `ai_agent` - Agent 配置表

4. **Chat 模块**
   - `ai_chat_session` - 对话会话表
   - `ai_chat_message` - 聊天消息表

5. **Knowledge 模块**
   - `knowledge_base` - 知识库表
   - `document` - 文档表
   - `agent_knowledge` - Agent 与知识库关联表

6. **MCP 模块**
   - `mcp_server` - MCP 服务器表
   - `mcp_tool` - MCP 工具表
   - `agent_mcp_tool` - Agent 绑定的 MCP 工具表

7. **Workflow 模块**
   - `ai_workflow` - 工作流主表
   - `ai_workflow_node` - 工作流节点表
   - `ai_workflow_edge` - 工作流连线表
   - `ai_workflow_execution` - 工作流执行记录表

8. **初始数据**
   - 管理员用户（admin/admin123）
   - 默认工作空间
   - 默认供应商（OpenAI、Ollama）

## 数据库规范

### 字段命名规范

- 时间字段：`created_at`、`updated_at`
- 逻辑删除：`deleted`（0=正常，1=删除）
- 工作空间隔离：`workspace_id`

### 索引规范

- 主键：`id`（自增）
- 唯一索引：`uk_` 前缀
- 普通索引：`idx_` 前缀
- 工作空间索引：所有业务表必须有 `idx_workspace_id`

### 字符集规范

- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_unicode_ci`

## 注意事项

1. **工作空间隔离**：所有业务表都包含 `workspace_id` 字段，用于多租户数据隔离
2. **逻辑删除**：使用 `deleted` 字段实现软删除，不物理删除数据
3. **外键约束**：不使用外键约束，由应用层保证数据一致性
4. **索引覆盖**：所有查询必须有索引覆盖，避免全表扫描

## 相关文档

- [DATABASE.md](../../docs/guides/DATABASE.md) - 数据库设计规范
- [AUTH-WORKSPACE.md](../../docs/guides/AUTH-WORKSPACE.md) - 认证与工作空间设计
- [ARCHITECTURE.md](../../docs/ARCHITECTURE.md) - 架构设计规范
