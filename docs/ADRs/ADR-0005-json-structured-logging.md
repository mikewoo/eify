# JSON 结构化日志 + Vector 采集 + ClickHouse 存储
`ADR-0005 json-structured-logging`

# Status
Accepted

# Date
2025-Q1

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

Eify 需要一个可观测的日志系统，支撑以下场景：
- 按请求链路追踪（HTTP → Service → SQL → LLM 调用全链路）
- 多维度查询（按用户、工作空间、接口、日志级别、慢查询）
- 生产环境故障排查和告警

### Decision drivers
- 需要全链路 TraceId 贯通
- 支持按多维度（用户、工作空间、接口、日志级别）查询
- 单机开发环境可运行，资源消耗适中
- 团队无额外学习成本（标准 SQL 查询）

# Considered Options
* **方案 A：ELK Stack** — Logstash 采集 + Elasticsearch 存储。资源消耗大，单机开发环境负担重。
* **方案 B：Grafana Loki** — 轻量级日志聚合，label-based 索引。不适合高基数字段（traceId、userId），查询受限。
* **方案 C：纯文本日志** — 传统 pattern layout。多维度查询几乎不可能，无法结构化分析。
* **方案 D：JSON + Vector + ClickHouse** — 自建 structured layout + 轻量采集 + 列存。资源适中，列存分析能力强，SQL 查询灵活。

# Decision

**选择方案 D：纯 JSON 格式（UTC 时区）+ Logback AsyncAppender + Vector 采集 + ClickHouse 存储。** 自建 `StructuredLogLayout` 实现三层日志分类。

## Consequences

### 优势
- 单行 JSON 可直接用 `grep` + `jq` 在本地排查
- TraceId 贯通全链路（HTTP → SQL → MQ → LLM）
- ClickHouse 支持复杂聚合查询（错误率、慢查询统计、P99 延迟）
- `neverBlock=true` 确保日志写入失败不会阻塞业务线程
- 异步日志将业务线程日志写入耗时从 ~200μs 降至 ~1μs（200 倍提升）

### 权衡
- JSON 格式文件体积比纯文本大约 30%
- ClickHouse 非 OLTP 数据库，不支持单条更新/删除
- Vector 配置需要维护（文件路径、字段映射）

# Details

## 架构

```
应用层 (Spring Boot)
  StructuredLogger (logReq/logSql/logMsg)
       │
       ▼
  StructuredLogLayout (三层分类, UTC 时区, 纯 JSON)
       │
       ▼
  AsyncAppender (queueSize=10000, neverBlock=true)
       │
       ▼
  ./logs/eify.log
       │
       ▼
  Vector (文件采集, JSON 解析, 字段提取)
       │
       ▼
  ClickHouse (按月分区, bloom_filter 索引, TTL 30天)
```

## 三层日志分类

`StructuredLogLayout` 自动识别日志来源并标记 `logType`：

| 层级 | 来源 | logType | 示例 |
|:---|:---|:---|:---|
| 第1层 | `StructuredLogger`（MDC 标记） | req / sql / msg | 请求日志、SQL 日志、MQ 日志 |
| 第2层 | `com.eify` 包业务日志 | simple | `log.info("处理完成")` |
| 第3层 | 框架日志 | sys | Spring、MyBatis 日志 |

## 关键设计决策

1. **纯 JSON + UTC 时区**：标准字段（timestamp, level, logType, traceId, spanId 等）在顶层，业务字段在 `message` 对象中。UTC 时区确保 ClickHouse 时间排序正确。
2. **异步日志不阻塞**：`queueSize=10000`、`neverBlock=true`、`discardingThreshold=0`（永不丢日志）。
3. **ClickHouse 替代 Elasticsearch**：列式存储天然适合日志扫描场景；单机可运行；标准 SQL 无额外学习成本；按月分区 + TTL 30 天自动清理。

## 参考
- [LOGGING.md](../guides/LOGGING.md) — 日志格式、配置、使用指南
- [DATABASE.md](../guides/DATABASE.md) — ClickHouse 表结构和索引策略
