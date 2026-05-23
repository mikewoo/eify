# 数据库设计

## 目录

- [MySQL 核心设计](#mysql-核心设计)
  - [核心数据表](#核心数据表)
  - [缓存策略](#缓存策略)
  - [表设计规范](#表设计规范)
  - [字段设计规范](#字段设计规范)
  - [通用字段约定](#通用字段约定)
  - [索引设计原则](#索引设计原则)
  - [大表预判和应对策略](#大表预判和应对策略)
  - [分页查询注意事项](#分页查询注意事项)
  - [游标分页优化规范](#游标分页优化规范)
  - [SQL 编写规范](#sql-编写规范)
  - [数据库配置优化](#数据库配置优化)
  - [建表标准模板](#建表标准模板)
  - [Eify 业务表 DDL](#eify-业务表-ddl)
  - [索引维护策略](#索引维护策略)
  - [数据归档策略](#数据归档策略)
- [ClickHouse 日志数据库](#clickhouse-日志数据库)

---

# MySQL 核心设计

## 核心数据表

> 完整 DDL 见 Flyway 迁移文件（`V1__init.sql`、`V4__model_category_and_embedding_model_id.sql`、`V5__name_workspace_deleted_unique.sql`），由 Flyway 自动执行

| 表名 | 用途 |
| :--- | :--- |
| `ai_user` | 用户表（认证与身份） |
| `ai_workspace` | 工作空间表（多租户隔离） |
| `ai_workspace_member` | 工作空间成员关联表 |
| `ai_workspace_invite` | 工作空间邀请码表 |
| `ai_user_session` | 用户会话（refresh token） |
| `provider` | 模型供应商表 |
| `model_config` | 模型配置表（含 `model_category` 模型分类：CHAT/EMBEDDING/RERANK/MULTIMODAL） |
| `provider_health` | 供应商健康状态表 |
| `ai_agent` | Agent 配置表 |
| `ai_chat_session` | 对话会话表 |
| `ai_chat_message` | 聊天消息表（大表，游标分页） |
| `knowledge_base` | 知识库表 |
| `document` | 文档表 |
| `document_chunk` | 文档分块 + Embedding 向量表（**PostgreSQL pgvector**，非 MySQL） |
| `agent_knowledge` | Agent 与知识库关联表 |
| `mcp_server` | MCP 服务器表 |
| `mcp_tool` | MCP 工具表 |
| `agent_mcp_tool` | Agent 绑定的 MCP 工具表 |
| `ai_workflow` | 工作流主表 |
| `ai_workflow_node` | 工作流节点表 |
| `ai_workflow_edge` | 工作流连线表 |
| `ai_workflow_execution` | 工作流执行记录表 |

> **双存储架构**：`document_chunk` 表使用 PostgreSQL pgvector 存储向量数据（HNSW 索引），其他表使用 MySQL。详细设计见 [双存储架构设计决策](ADRs/ADR-0004-dual-storage-mysql-pgvector.md)。

## 缓存策略

| 缓存对象 | 层级 | 过期时间 |
| :--- | :--- | :--- |
| Agent 配置 | Caffeine | 30 分钟 |
| 用户权限 | Caffeine | 15 分钟 |
| 对话上下文 | Redis | 24 小时 |
| 用户会话 | Redis | 24 小时 |

## 表设计规范

### 表名规范
- 使用小写字母，单词间用下划线分隔
- 格式：`{业务模块}_{实体}`
- 示例：`ai_agent`、`ai_conversation`、`sys_user`
- 禁止使用 MySQL 保留字作为表名

### 存储引擎
- 所有业务表必须使用 InnoDB
- 原因：支持事务、外键、行级锁

### 字符集
- 字符集：`utf8mb4`
- 排序规则：`utf8mb4_unicode_ci`
- 原因：支持 Emoji、多语言

## 字段设计规范

### 字段类型选择

| 类型 | 使用场景 | 示例 |
|:---|:---|:---|
| `BIGINT UNSIGNED` | 主键、外键 | `id` |
| `VARCHAR(N)` | 短字符串 | `name` VARCHAR(100) |
| `TEXT` | 长文本 | `content` |
| `DECIMAL(M,D)` | 金额、高精度数值 | `price` DECIMAL(10,2) |
| `DATETIME` | 业务时间 | `created_at` |
| `TINYINT` | 状态、枚举 | `status` TINYINT |
| `JSON` | 配置、元数据 | `config` JSON |

### NULL 值处理
- 所有字段必须指定 NULL 或 NOT NULL
- 业务字段尽量设为 NOT NULL + 默认值
- 索引字段必须设为 NOT NULL
- NULL 值会影响索引效率

### 默认值规范
- 数值类型默认值：0
- 字符串类型默认值：''
- 枚举类型默认值：明确指定（如 status = 1）
- 时间字段：`CURRENT_TIMESTAMP` 或 `NOW()`

### 字段注释
- 所有字段必须添加 COMMENT
- 枚举值必须说明含义（如 0=禁用，1=启用）
- JSON 字段必须说明结构

## 通用字段约定

```sql
-- 主键：统一使用 id，BIGINT 自增
id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',

-- 创建时间：所有表必备
created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

-- 更新时间：所有表必备，自动更新
updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

-- 软删除：所有业务表必备（非日志表）
deleted TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=正常，1=删除',

-- 创建人：业务表必备
creator_id BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',
```

## 索引设计原则

```sql
-- 索引命名规范
-- 普通索引：idx_字段名
KEY `idx_user_id` (`user_id`)

-- 唯一索引：uk_字段名
UNIQUE KEY `uk_name` (`name`)

-- 联合索引：idx_字段1_字段2
KEY `idx_user_id_status` (`user_id`, `status`)

-- 索引设计原则
-- 1. where/order by/group by 的字段建索引
-- 2. 最左前缀原则
-- 3. 区分度高的字段放左边
-- 4. 覆盖索引优化
-- 5. 单表索引数 ≤ 5
```

## 大表预判和应对策略

```
┌─────────────────────────────────────────────────────────────┐
│              大表定义（Eify 场景）                            │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ✅ ai_chat_message（消息表）- 必然大表                      │
│  ✅ ai_workflow_execution（执行记录）- 可能大表              │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**应对策略**：
1. 分页查询优化：使用游标分页，避免深分页
2. 冷热数据分离：定期归档历史数据
3. 分区表：按时间分区（可选）
4. 向量索引优化：使用 HNSW 算法（pgvector）

## 分页查询注意事项

项目支持两种分页模式，根据数据量选择合适的分页方式：

| 分页模式 | 适用场景 | 请求参数 | 响应字段 | 性能 |
|:---|:---|:---|:---|:---|
| **传统分页** | 小表（< 10 万行） | `page`、`pageSize` | `total`、`page`、`pageSize` | 较慢 |
| **游标分页** | 大表（>= 10 万行） | `lastId`、`pageSize` | `hasMore`、`pageSize` | 快 6-50 倍 |

### 传统分页（小表）

**SQL 示例**：
```sql
-- 适用于小表（如 ai_chat_session、ai_agent）
SELECT * FROM ai_chat_session
WHERE user_id = 123
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;
```

**特点**：
- 支持 COUNT(*) 查询 total
- 支持跳页
- 深分页性能差

### 游标分页（大表）

**SQL 示例**：
```sql
-- ❌ 不好：深分页性能差
SELECT * FROM ai_chat_message
WHERE session_id = 123
ORDER BY created_at DESC
LIMIT 10000, 20;

-- ✅ 好：游标分页（推荐）
-- 第一页
SELECT * FROM ai_chat_message
WHERE session_id = 123
ORDER BY id DESC
LIMIT 21;  -- 多查一条判断 hasMore

-- 下一页
SELECT * FROM ai_chat_message
WHERE session_id = 123 AND id < 1000
ORDER BY id DESC
LIMIT 21;
```

**性能优势**：
| 优势 | 说明 |
|:---|:---|
| **避免 COUNT(\*)** | 不执行全表扫描，响应速度快 6-50 倍 |
| **索引优化** | 利用主键索引 `WHERE id < lastId` 快速定位 |
| **无深分页问题** | 不受 OFFSET 累积影响，翻页性能稳定 |
| **带宽节省** | 不返回 total 字段，减少响应体积 |

**游标分页限制**：
| 限制 | 说明 |
|:---|:---|
| **不支持跳页** | 只能顺序翻页，不能跳到指定页 |
| **无总页数** | 无法显示总页数和总记录数 |
| **需要排序字段** | 必须有唯一、有序的字段（如主键 ID） |

### 分页接口选择指南

| 场景 | 推荐方式 | 判断标准 |
|:---|:---|:---|
| **新模块** | 同时实现两种 | 未来数据量不确定 |
| **小表** | 仅传统分页 | 数据量 < 10 万，增长缓慢 |
| **大表** | 仅游标分页 | 数据量 >= 10 万，或高频写入 |
| **历史数据** | 游标分页 | 历史数据不变化，适合缓存 |

### 游标分页索引设计

```sql
-- 游标分页专用索引（覆盖查询 + 排序）
KEY `idx_session_id_id` (`session_id`, `id`)
```

**索引设计原则**：
1. 索引第一列：WHERE 条件字段（如 session_id）
2. 索引第二列：排序字段（主键 id）
3. 确保索引覆盖，避免回表

## 游标分页优化规范

### 三大优化技术

| 技术 | 作用 | 核心 SQL |
|:---|:---|:---|
| **覆盖索引** | 索引包含所有查询字段，避免回表 | `Extra: Using index` |
| **范围查询** | 利用索引有序性缩小扫描范围 | `WHERE created_at >= ... AND created_at < ...` |
| **游标分页** | 用上一页最后 ID 定位，避免深分页 | `WHERE id < lastId ORDER BY id LIMIT N` |

### 覆盖索引设计原则

1. **查询字段全覆盖**：索引包含 WHERE + ORDER BY + SELECT 所有字段
2. **最左前缀匹配**：索引从左到右匹配，不能跳过中间列
3. **高选择性放左边**：区分度高的字段放索引左侧

```sql
-- 覆盖索引示例
-- 查询：SELECT id, session_id, role, created_at
--        WHERE session_id = 123 ORDER BY id DESC
-- 索引：(session_id, id, role, created_at)
-- 结果：Extra: Using where; Using index（无回表）
```

### 范围查询规范

```sql
-- ❌ 禁止：函数操作导致索引失效
WHERE DATE(created_at) = '2024-01-01'

-- ✅ 正确：范围查询利用索引
WHERE created_at >= '2024-01-01 00:00:00'
  AND created_at < '2024-01-02 00:00:00'
```

### 优化效果参考

| 场景 | 优化前 | 优化后 | 提升 |
|:---|:---|:---|:---|
| 浅分页（1-10 页） | 50ms | 10ms | 5 倍 |
| 深分页（100+ 页） | 5000ms | 15ms | 333 倍 |
| 大表查询（百万级） | 超时 | 50ms | 质变 |
| 并发（100 QPS） | DB 负载 100% | DB 负载 10% | 10 倍 |

### 检查清单

**索引设计**：
- [ ] 查询字段全部包含在索引中（覆盖索引）
- [ ] WHERE 条件字段在索引左边（最左前缀）
- [ ] ORDER BY 字段在索引中
- [ ] 高选择性字段在索引左边
- [ ] 索引字段数量 <= 5
- [ ] EXPLAIN 验证 Extra 包含 "Using index"

**查询优化**：
- [ ] 大表使用游标分页（避免深分页）
- [ ] 时间过滤使用范围查询（避免函数计算）
- [ ] 限制返回字段（禁止 SELECT *）
- [ ] 单次查询数量 <= 100
- [ ] 添加查询超时保护

## SQL 编写规范

### 禁止的操作

```sql
-- ❌ 禁止：SELECT *
SELECT * FROM ai_agent;

-- ✅ 正确：明确字段
SELECT id, name, description FROM ai_agent;

-- ❌ 禁止：WHERE 字段 IS NULL（索引失效）
WHERE name IS NULL

-- ✅ 正确：设计时避免 NULL，使用默认值
WHERE name = ''

-- ❌ 禁止：隐式类型转换
WHERE user_id = '123'

-- ✅ 正确：类型匹配
WHERE user_id = 123

-- ❌ 禁止：在 WHERE 中对字段进行函数操作
WHERE DATE(created_at) = '2024-01-01'

-- ✅ 正确：使用范围查询
WHERE created_at >= '2024-01-01 00:00:00'
  AND created_at < '2024-01-02 00:00:00'
```

## 数据库配置优化

```ini
[mysqld]
# 基础配置
character-set-server=utf8mb4
collation-server=utf8mb4_unicode_ci

# InnoDB 配置
innodb_buffer_pool_size=2G
innodb_log_file_size=512M
innodb_flush_log_at_trx_commit=2

# 连接配置
max_connections=500

# 慢查询日志
slow_query_log=1
long_query_time=2
```

## 建表标准模板

所有业务表必须遵循以下模板结构：

```sql
CREATE TABLE `{table_prefix}_{entity}` (
  -- ============ 主键 ============
  `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',

  -- ============ 业务字段（按需添加） ============
  `workspace_id` BIGINT UNSIGNED NOT NULL COMMENT '工作空间 ID',
  `name` VARCHAR(100) NOT NULL DEFAULT '' COMMENT '名称',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态：0=禁用，1=启用',

  -- ============ 通用字段（所有表必备） ============
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '删除标识：0=正常，1=删除',
  `creator_id` BIGINT UNSIGNED NOT NULL COMMENT '创建人ID',

  -- ============ 索引 ============
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name_workspace_deleted` (`name`, `workspace_id`, `deleted`),
  KEY `idx_workspace_id` (`workspace_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_status_deleted` (`status`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='表说明';
```

## Eify 业务表 DDL

> **完整 DDL**：Flyway 迁移文件（`V1__init.sql`、`V4__model_category_and_embedding_model_id.sql`、`V5__name_workspace_deleted_unique.sql`）— 包含所有表的 CREATE TABLE 和增量变更。
>
> 以下为各表结构说明，实际执行请使用 Flyway 自动迁移。

### 表清单

| 模块 | 表名 | 说明 | workspace_id |
|:---|:---|:---|:---:|
| **用户** | `ai_user` | 用户表（认证与身份） | - |
| **工作空间** | `ai_workspace` | 工作空间表（多租户隔离） | - |
| **工作空间** | `ai_workspace_member` | 工作空间成员关联表 | - |
| **工作空间** | `ai_workspace_invite` | 工作空间邀请码表 | - |
| **会话** | `ai_user_session` | 用户会话（refresh token） | - |
| **Provider** | `provider` | 模型供应商表 | Y |
| **Provider** | `model_config` | 模型配置表 | Y |
| **Provider** | `provider_health` | 供应商健康状态表 | - |
| **Agent** | `ai_agent` | Agent 配置表 | Y |
| **Chat** | `ai_chat_session` | 对话会话表 | Y |
| **Chat** | `ai_chat_message` | 聊天消息表（大表，游标分页） | Y |
| **Knowledge** | `knowledge_base` | 知识库表（含 `embedding_model_id` FK → `model_config.id`） | Y |
| **Knowledge** | `document` | 文档表 | Y |
| **Knowledge** | `agent_knowledge` | Agent 与知识库关联表 | - |
| **MCP** | `mcp_server` | MCP 服务器表 | Y |
| **MCP** | `mcp_tool` | MCP 工具表 | - |
| **MCP** | `agent_mcp_tool` | Agent 绑定的 MCP 工具表 | - |
| **Workflow** | `ai_workflow` | 工作流主表 | Y |
| **Workflow** | `ai_workflow_node` | 工作流节点表 | - |
| **Workflow** | `ai_workflow_edge` | 工作流连线表 | - |
| **Workflow** | `ai_workflow_execution` | 工作流执行记录表 | - |

### 关键字段说明

**Provider 表 auth_config JSON 结构**：

```json
// OPENAI / OPENAI_COMPATIBLE（通义千问、DeepSeek 等）
{ "api_key": "sk-xxxxxxxxxxxx" }

// ANTHROPIC — 使用 x-api-key 头传递
{ "api_key": "sk-ant-xxxxxxxxxxxx" }

// OLLAMA — 本地无需鉴权
{ "require_auth": false }
```

**provider_health 状态机**：

```
    连续3次失败       连续5次失败
UP ──────────→ DEGRADED ──────────→ DOWN
 ↑                  ↑
 └── 连续5次成功 ────┘ 连续3次成功 ────┘
```

**ai_chat_message 游标分页**：

```sql
-- 第一页
SELECT * FROM ai_chat_message
WHERE session_id = 123
ORDER BY id DESC LIMIT 21;

-- 下一页
SELECT * FROM ai_chat_message
WHERE session_id = 123 AND id < 1000
ORDER BY id DESC LIMIT 21;
```

### 执行方式

```bash
# 初始化数据库（包含所有表 + 开发环境初始数据）
# 数据库由 Flyway 自动迁移，无需手动导入。备用方式：
mysql -u root -p eify < deploy/sql/init_eify_mysql.sql
```

## 索引维护策略

### 索引监控

```sql
-- 查看未使用的索引
SELECT
    t.table_schema,
    t.table_name,
    s.index_name,
    s.seq_in_index,
    s.column_name,
    s.cardinality
FROM information_schema.tables t
JOIN information_schema.statistics s ON t.table_name = s.table_name
WHERE t.table_schema = 'eify'
  AND t.table_name NOT LIKE 'sys_%'
  AND s.index_name != 'PRIMARY'
  AND s.cardinality IS NULL
ORDER BY t.table_name, s.index_name, s.seq_in_index;

-- 查看表大小和索引大小
SELECT
    table_name,
    ROUND(((data_length + index_length) / 1024 / 1024), 2) AS '总大小MB',
    ROUND((data_length / 1024 / 1024), 2) AS '数据大小MB',
    ROUND((index_length / 1024 / 1024), 2) AS '索引大小MB'
FROM information_schema.tables
WHERE table_schema = 'eify'
ORDER BY (data_length + index_length) DESC;
```

### 索引优化

```sql
-- 分析表（更新统计信息）
ANALYZE TABLE ai_message;

-- 优化表（回收空间）
OPTIMIZE TABLE ai_message;

-- 检查表（检查完整性）
CHECK TABLE ai_message;
```

## 数据归档策略

```sql
-- 归档 6 个月前的消息到历史表
-- 1. 创建历史表（结构相同）
CREATE TABLE `ai_chat_message_history` LIKE `ai_chat_message`;

-- 2. 归档数据（分批执行，避免锁表）
INSERT INTO ai_chat_message_history
SELECT * FROM ai_chat_message
WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH)
LIMIT 10000;

-- 3. 删除已归档数据（分批执行）
DELETE FROM ai_chat_message
WHERE id IN (
  SELECT id FROM ai_chat_message_history
)
LIMIT 10000;

-- 4. 验证归档完成
SELECT COUNT(*) FROM ai_chat_message
WHERE created_at < DATE_SUB(NOW(), INTERVAL 6 MONTH);
```

---

# ClickHouse 日志数据库

## 设计概述

### 设计目标

| 目标 | 说明 | 实现方式 |
|:---|:---|:---|
| **存储效率** | 减少 30-40% 存储空间 | `Nullable` 类型 + ZSTD 压缩 |
| **查询性能** | 加速常见查询 10-20% | 布隆过滤器索引 + 物化视图 |
| **语义清晰** | 字段用途明确 | 按 `logType` 分类字段 |
| **可扩展性** | 支持数据量和查询量增长 | 分区 + TTL + 分布式表 |

### 架构流程

```
┌─────────────────────────────────────────────────────────────────┐
│                      应用层 (Spring Boot)                        │
│  StructuredLogLayout → 纯 JSON 格式日志 → 文件输出              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      采集层 (Vector)                             │
│  文件读取 → JSON 解析 → 字段转换 → ClickHouse 写入              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│                      存储层 (ClickHouse)                         │
│  主表 + 物化视图 + 分区 + TTL                                    │
└─────────────────────────────────────────────────────────────────┘
```

## 数据模型

### 日志类型分类

| 类型 | 代码 | 说明 | 专用字段数 |
|:---|:---|:---|:---:|
| **请求日志** | `req` | HTTP 请求记录 | 16 |
| **SQL 日志** | `sql` | 数据库查询记录 | 9 |
| **消息日志** | `msg` | 消息队列记录 | 16 |
| **业务日志** | `simple` | 业务代码日志 | - |
| **系统日志** | `sys` | 框架和系统日志 | - |

### 字段分类原则

**核心原则**：按 `logType` 分类，使用 `Nullable` 类型避免存储无意义的默认值。

```sql
-- 标准字段（所有日志类型）
timestamp DateTime64(3),
level LowCardinality(String),
logType LowCardinality(String),
...

-- REQ 专用字段（仅 req 类型使用，NULL 不占用存储）
clientIp Nullable(String),
method Nullable(String),
path Nullable(String),
status Nullable(UInt32),
...

-- SQL 专用字段（仅 sql 类型使用）
sql Nullable(String),
executionTime Nullable(UInt32),
isSlowQuery Nullable(UInt8),
rowCount Nullable(UInt32),
```

## 表结构设计

### 主表：app_logs

```sql
CREATE TABLE IF NOT EXISTS app_logs
(
    -- ========== 标准字段（所有日志类型） ==========
    timestamp DateTime64(3) CODEC(DoubleDelta),
    pid String CODEC(ZSTD(1)),
    className String CODEC(ZSTD(1)),
    lineNumber UInt32,
    level LowCardinality(String) CODEC(ZSTD(1)),
    logType LowCardinality(String) CODEC(ZSTD(1)),
    traceId String CODEC(ZSTD(1)),
    spanId String CODEC(ZSTD(1)),
    appName LowCardinality(String) CODEC(ZSTD(1)),
    serverIp LowCardinality(String) CODEC(ZSTD(1)),
    appVersion LowCardinality(String) CODEC(ZSTD(1)),
    pod LowCardinality(String) CODEC(ZSTD(1)),
    duration UInt32 CODEC(ZSTD(1)),

    -- ========== 通用字段（所有日志类型） ==========
    message String CODEC(ZSTD(3)),
    exception Nullable(String) CODEC(ZSTD(3)),

    -- ========== REQ 专用字段（仅 req 使用） ==========
    clientIp Nullable(String) CODEC(ZSTD(1)),
    method Nullable(String) CODEC(ZSTD(1)),
    path Nullable(String) CODEC(ZSTD(1)),
    status Nullable(UInt32),
    userAgent Nullable(String) CODEC(ZSTD(3)),
    error Nullable(String) CODEC(ZSTD(3)),
    requestBody Nullable(String) CODEC(ZSTD(3)),
    requestBodySize Nullable(UInt32),
    response Nullable(String) CODEC(ZSTD(3)),
    responseSize Nullable(UInt32),
    asyncRequest Nullable(UInt8),
    asyncType Nullable(String) CODEC(ZSTD(1)),
    asyncCompletionStatus Nullable(String) CODEC(ZSTD(1)),

    -- ========== SQL 专用字段（仅 sql 使用） ==========
    sql Nullable(String) CODEC(ZSTD(3)),
    executionTime Nullable(UInt32),
    isSlowQuery Nullable(UInt8),
    rowCount Nullable(UInt32),
    errorStack Nullable(String) CODEC(ZSTD(3)),

    -- ========== MSG 专用字段（仅 msg 使用） ==========
    msgType Nullable(String) CODEC(ZSTD(1)),
    operationType Nullable(String) CODEC(ZSTD(1)),
    topic Nullable(String) CODEC(ZSTD(1)),
    partition Nullable(UInt32),
    offset Nullable(UInt64),
    key Nullable(String) CODEC(ZSTD(1)),
    consumerGroupId Nullable(String) CODEC(ZSTD(1)),
    processResult Nullable(String) CODEC(ZSTD(1)),
    retryCount Nullable(UInt32),
    producerTime Nullable(DateTime64(3)),
    consumerTime Nullable(DateTime64(3)),
    payloadSize Nullable(UInt32),

    -- ========== 元数据 ==========
    createdAt DateTime64(3) DEFAULT now64() CODEC(DoubleDelta)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, logType, level, traceId, appName)
TTL timestamp + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;
```

### 设计说明

| 设计决策 | 说明 |
|:---|:---|
| **DateTime64(3)** | 毫秒精度，与 Java 日志时间戳一致 |
| **LowCardinality(String)** | 低基数字符串（枚举值），减少存储 |
| **Nullable + CODEC** | NULL 不占用空间，ZSTD 压缩率高 |
| **顶层专用字段** | 类型专用字段在顶层，提升查询性能 |
| **message 保留** | message 字段保留完整业务数据，字段不删除 |
| **PARTITION BY toYYYYMM** | 按月分区，便于删除过期数据 |
| **ORDER BY** | 查询优化，覆盖常见查询模式 |
| **TTL 30 DAY** | 自动删除 30 天前数据 |

**字段分布策略**：
- **顶层字段**：直接查询索引，提升性能（如 `WHERE status = 500`）
- **message 字段**：保留完整原始数据，方便业务分析

### 类型约束

**重要**：ClickHouse 不支持 `Nullable(LowCardinality(String))`

```sql
-- ❌ 错误
method Nullable(LowCardinality(String))

-- ✅ 正确
method Nullable(String)
```

## 索引策略

### 跳数索引（Skip Index）

```sql
-- 布隆过滤器索引（加速等值查询）
ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_trace_id
    traceId TYPE bloom_filter GRANULARITY 1;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_log_type
    logType TYPE bloom_filter GRANULARITY 1;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_level
    level TYPE bloom_filter GRANULARITY 1;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_class_name
    className TYPE bloom_filter GRANULARITY 1;

-- MinMax 索引（加速范围查询）
ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_timestamp_minmax
    TYPE minmax GRANULARITY 4;

-- Set 索引（加速 IN 查询）
ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_level_set
    TYPE set(8) GRANULARITY 2;
```

### 索引用途

| 索引 | 类型 | 查询模式 | 示例 |
|:---|:---|:---|:---|
| `idx_trace_id` | bloom_filter | 等值查询 | `WHERE traceId = 'xxx'` |
| `idx_log_type` | bloom_filter | 等值查询 | `WHERE logType = 'req'` |
| `idx_level` | bloom_filter | 等值查询 | `WHERE level = 'ERROR'` |
| `idx_timestamp_minmax` | minmax | 范围查询 | `WHERE timestamp > now() - INTERVAL 1 HOUR` |
| `idx_level_set` | set | IN 查询 | `WHERE level IN ('ERROR', 'WARN')` |

## 物化视图

### 日志类型专用视图

```sql
-- REQ 日志视图
CREATE MATERIALIZED VIEW IF NOT EXISTS req_logs_mv
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, traceId, path, status)
TTL timestamp + INTERVAL 30 DAY
AS SELECT
    timestamp, pid, className, lineNumber, level,
    traceId, spanId, appName, serverIp, appVersion, pod, duration,
    clientIp, method, path, status, userAgent, error,
    requestBody, requestBodySize, response, responseSize,
    asyncRequest, asyncType, asyncCompletionStatus,
    message, exception
FROM app_logs
WHERE logType = 'req';

-- SQL 日志视图
CREATE MATERIALIZED VIEW IF NOT EXISTS sql_logs_mv
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, className, isSlowQuery, executionTime)
TTL timestamp + INTERVAL 30 DAY
AS SELECT
    timestamp, pid, className, lineNumber, level,
    traceId, spanId, appName, serverIp, appVersion, pod, duration,
    sql, executionTime, isSlowQuery, rowCount, errorStack, message, exception
FROM app_logs
WHERE logType = 'sql';

-- MSG 日志视图
CREATE MATERIALIZED VIEW IF NOT EXISTS msg_logs_mv
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, msgType, topic, partition)
TTL timestamp + INTERVAL 30 DAY
AS SELECT
    timestamp, pid, className, lineNumber, level,
    traceId, spanId, appName, serverIp, appVersion, pod, duration,
    msgType, operationType, topic, partition, offset, key,
    consumerGroupId, processResult, error, retryCount,
    producerTime, consumerTime, payloadSize, message, exception
FROM app_logs
WHERE logType = 'msg';
```

### 统计视图

```sql
-- 错误日志统计（按小时）
CREATE MATERIALIZED VIEW IF NOT EXISTS error_stats_hourly_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, appName, level, className)
TTL hour + INTERVAL 7 DAY
AS SELECT
    toStartOfHour(timestamp) as hour,
    appName,
    level,
    className,
    count() as error_count
FROM app_logs
WHERE level = 'ERROR'
GROUP BY hour, appName, level, className;

-- 慢请求统计（按分钟）
CREATE MATERIALIZED VIEW IF NOT EXISTS slow_requests_minute_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(minute)
ORDER BY (minute, appName, path)
TTL minute + INTERVAL 7 DAY
AS SELECT
    toStartOfMinute(timestamp) as minute,
    appName,
    path,
    count() as request_count,
    avg(duration) as avg_duration,
    max(duration) as max_duration
FROM app_logs
WHERE logType = 'req' AND duration > 1000
GROUP BY minute, appName, path;

-- 慢 SQL 统计（按小时）
CREATE MATERIALIZED VIEW IF NOT EXISTS slow_sql_hourly_mv
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, appName, className)
TTL hour + INTERVAL 7 DAY
AS SELECT
    toStartOfHour(timestamp) as hour,
    appName,
    className,
    count() as query_count,
    avg(executionTime) as avg_execution_time,
    max(executionTime) as max_execution_time
FROM app_logs
WHERE logType = 'sql' AND isSlowQuery = 1
GROUP BY hour, appName, className;
```

### 视图用途

| 视图 | 用途 | 查询示例 |
|:---|:---|:---|
| `req_logs_mv` | 请求日志专用 | `SELECT * FROM req_logs_mv WHERE duration > 1000` |
| `sql_logs_mv` | SQL 日志专用 | `SELECT * FROM sql_logs_mv WHERE isSlowQuery = 1` |
| `msg_logs_mv` | 消息日志专用 | `SELECT * FROM msg_logs_mv WHERE topic = 'xxx'` |
| `error_stats_hourly_mv` | 错误趋势分析 | `SELECT * FROM error_stats_hourly_mv ORDER BY hour DESC LIMIT 24` |
| `slow_requests_minute_mv` | 慢请求监控 | `SELECT * FROM slow_requests_minute_mv ORDER BY minute DESC LIMIT 60` |

## 分区策略

### 分区设计

```sql
PARTITION BY toYYYYMM(timestamp)
```

### 分区特点

| 特性 | 说明 |
|:---|:---|
| **时间粒度** | 按月分区 |
| **优势** | 便于删除过期数据，提高查询性能 |
| **查询优化** | 按时间范围查询自动跳过无关分区 |

### 分区管理

```sql
-- 查看分区
SELECT
    partition,
    rows,
    formatReadableSize(bytes_on_disk) as size
FROM system.parts
WHERE table = 'app_logs' AND active
ORDER BY partition DESC;

-- 删除特定分区（手动清理）
ALTER TABLE app_logs DROP PARTITION '202604';
```

## TTL 策略

### TTL 配置

```sql
TTL timestamp + INTERVAL 30 DAY
```

### TTL 说明

| 配置 | 说明 |
|:---|:---|
| **保留时间** | 30 天 |
| **删除方式** | 自动删除过期数据 |
| **触发时机** | ClickHouse 后台合并时触发 |

### TTL 调整

```sql
-- 修改 TTL 为 60 天
ALTER TABLE app_logs MODIFY TTL timestamp + INTERVAL 60 DAY;

-- 禁用 TTL
ALTER TABLE app_logs REMOVE TTL;
```

## 查询模式

### 基础查询

```sql
-- 最近 100 条日志
SELECT * FROM app_logs
ORDER BY timestamp DESC
LIMIT 100;

-- 错误日志
SELECT * FROM app_logs
WHERE level = 'ERROR'
ORDER BY timestamp DESC
LIMIT 100;

-- 按日志类型查询
SELECT * FROM app_logs
WHERE logType = 'req'
ORDER BY timestamp DESC
LIMIT 100;
```

### Trace 查询

```sql
-- 完整调用链
SELECT
    timestamp,
    logType,
    level,
    message
FROM app_logs
WHERE traceId = 'xxx'
ORDER BY timestamp;

-- 使用物化视图（更快）
SELECT * FROM req_logs_mv
WHERE traceId = 'xxx'
ORDER BY timestamp;
```

### 统计查询

```sql
-- 每小时请求数
SELECT
    toStartOfHour(timestamp) as hour,
    count() as count
FROM app_logs
WHERE logType = 'req'
  AND timestamp > now() - INTERVAL 24 HOUR
GROUP BY hour
ORDER BY hour;

-- 错误率统计
SELECT
    toStartOfMinute(timestamp) as minute,
    countIf(level = 'ERROR') * 100.0 / count() as error_rate
FROM app_logs
WHERE timestamp > now() - INTERVAL 1 HOUR
GROUP BY minute
ORDER BY minute DESC;
```

## 性能优化

### 压缩效果

| 指标 | 优化前 | 优化后 | 改善 |
|:---|:---|:---|:---|
| **存储空间** | ~100% | ~60-70% | 30-40% ↓ |
| **查询速度** | 基准 | 更快 | 10-20% ↑ |

### 优化建议

1. **使用物化视图** - 针对高频查询创建专用视图
2. **合理使用索引** - 布隆过滤器适合等值查询
3. **分区裁剪** - 查询时指定时间范围
4. **避免 SELECT *** - 只查询需要的字段

## 迁移指南

### 迁移步骤

1. **备份数据**
```sql
RENAME TABLE app_logs TO app_logs_backup;
```

2. **创建新表**
```bash
clickhouse-client --multiquery < deploy/infra/clickhouse/init.sql
```

3. **迁移数据**
```sql
INSERT INTO app_logs SELECT * FROM app_logs_backup;
```

4. **验证结果**
```sql
-- 检查数据行数
SELECT count() FROM app_logs;

-- 检查存储使用
SELECT formatReadableSize(sum(bytes_on_disk)) as disk_size
FROM system.parts
WHERE table = 'app_logs' AND active;
```

### 回滚方案

```sql
-- 停止写入，删除新表
DROP TABLE IF EXISTS app_logs;

-- 从备份恢复
CREATE TABLE app_logs AS app_logs_backup ENGINE = MergeTree() ...;
INSERT INTO app_logs SELECT * FROM app_logs_backup;

---

## ClickHouse 监控查询

### 慢查询排查

```sql
-- 查询最近 1 小时耗时超过 1 秒的查询
SELECT
    query_start_time,
    query_duration_ms,
    user,
    query
FROM system.query_log
WHERE type = 'QueryFinish'
  AND query_duration_ms > 1000
  AND event_time > now() - INTERVAL 1 HOUR
ORDER BY query_duration_ms DESC
LIMIT 20;
```

### 存储统计

```sql
-- 按表查看磁盘占用和数据量
SELECT
    table,
    formatReadableSize(sum(bytes_on_disk)) AS disk_size,
    sum(rows) AS total_rows,
    max(modification_time) AS last_modified
FROM system.parts
WHERE active
GROUP BY table
ORDER BY sum(bytes_on_disk) DESC;
```

### 写入吞吐监控

```sql
-- 最近 1 小时内各表的写入行数
SELECT
    table,
    count() AS inserts,
    sum(rows) AS total_rows_written,
    formatReadableSize(sum(bytes)) AS total_written
FROM system.query_log
WHERE type = 'QueryFinish'
  AND query_kind = 'Insert'
  AND event_time > now() - INTERVAL 1 HOUR
GROUP BY table
ORDER BY inserts DESC;
```
```
