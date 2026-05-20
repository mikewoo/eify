# 日志监控系统部署指南

本文档介绍日志监控栈的部署和配置。

**日志格式和架构说明**：[../../docs/guides/LOGGING.md](../../docs/guides/LOGGING.md)

---

## 快速开始

### 一键部署（推荐）

```bash
# 在项目根目录执行
sudo bash deploy/infra/deploy/deploy-local.sh
```

### 手动启动

```bash
# 在 deploy/infra/deploy/ 目录下
docker compose -f docker-compose-logging.yml up -d

# 或在项目根目录
docker compose -f deploy/infra/deploy/docker-compose-logging.yml up -d
```

### 初始化 ClickHouse

```bash
docker exec -it eify-clickhouse clickhouse-client --multiquery < ../clickhouse/init.sql
```

---

## 架构概述

```
┌──────────┐   采集    ┌──────────┐   存储    ┌────────────┐
│ 应用日志  │ ──────→ │  Vector  │ ──────→ │ ClickHouse │
│ (JSON)   │          │ (清洗)   │          │            │
└──────────┘          └──────────┘          └──────┬─────┘
                                                   │
                    ┌──────────────────────────────┼──────────────┐
                    │                              │              │
                    ▼                              ▼              ▼
            ┌─────────────┐              ┌─────────────┐  ┌───────────┐
            │ ClickVisual │              │   Grafana   │  │ Prometheus│
            │ (日志查询)   │              │  (仪表盘)    │  │ (指标采集) │
            └─────────────┘              └─────────────┘  └───────────┘
```

| 组件 | 用途 | 端口 |
|:---|:---|:---|
| **ClickHouse** | 日志存储（列式数据库） | 8123 (HTTP), 9000 (Native) |
| **Vector** | 日志采集与转换 | 8686 (API) |
| **ClickVisual** | 日志查询与可视化 | 19001 |
| **Grafana** | 监控仪表盘 | 3000 |
| **Prometheus** | 指标采集与告警 | 9090 |

---

## 配置说明

### 1. Vector 配置

**配置文件**：`deploy/infra/vector/vector.toml`

```toml
[sources.file_logs]
type = "file"
include = ["/logs/*.log"]

[transforms.parse_log]
type = "remap"
inputs = ["file_logs"]
source = '''
  . = parse_json!(.message)
  # 提取业务字段到顶层...
'''

[sinks.clickhouse]
type = "clickhouse"
endpoint = "http://clickhouse:8123"
database = "logs"
table = "app_logs"
```

### 2. ClickHouse 配置

**Schema 文件**：`deploy/infra/clickhouse/init.sql`

包含内容：
- 主表结构 `app_logs`（使用 Nullable 优化存储）
- 索引定义（布隆过滤器、MinMax、Set）
- 物化视图（req/sql/msg 日志视图，统计视图）
- TTL 配置（30 天自动删除）

---

## 查询示例

### 基础查询

```sql
-- 最近 100 条日志
SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 100;

-- 错误日志
SELECT * FROM app_logs WHERE level = 'ERROR';

-- 按日志类型查询
SELECT * FROM app_logs WHERE logType = 'req';
```

### Trace 查询

```sql
-- 完整调用链
SELECT * FROM app_logs WHERE traceId = 'xxx' ORDER BY timestamp;
```

### 统计查询

```sql
-- 每分钟请求数
SELECT
    toStartOfMinute(timestamp) as minute,
    count() as count
FROM app_logs
WHERE logType = 'req'
GROUP BY minute
ORDER BY minute DESC;
```

---

## 告警配置

### 错误率告警

```sql
SELECT
    countIf(level = 'ERROR') * 100.0 / count() as error_rate
FROM app_logs
WHERE timestamp > now() - INTERVAL 5 MINUTE;
```

### 慢查询告警

```sql
SELECT count() FROM app_logs
WHERE logType = 'req' AND duration > 1000
  AND timestamp > now() - INTERVAL 5 MINUTE;
```

---

## 性能优化

### 已优化项目

1. **分区**：按月分区 `PARTITION BY toYYYYMM(timestamp)`
2. **排序键**：`(timestamp, logType, level, traceId, appName)`
3. **TTL**：30 天自动删除 `TTL timestamp + INTERVAL 30 DAY`
4. **Nullable 字段**：按 logType 分类，减少存储
5. **索引**：bloom_filter 索引加速等值查询
6. **物化视图**：按日志类型专用查询

### 监控指标

```sql
-- 存储使用
SELECT formatReadableSize(sum(bytes_on_disk)) as size
FROM system.parts
WHERE table = 'app_logs' AND active;

-- 写入吞吐量
SELECT
    toStartOfMinute(timestamp) as minute,
    count() as rows_per_minute
FROM app_logs
GROUP BY minute
ORDER BY minute DESC;
```

---

## 故障排查

### Vector 连接失败

```bash
docker ps | grep vector
docker logs eify-vector
curl http://clickhouse:8123/ping
```

### 日志未写入

```bash
ls -la /logs/*.log
docker exec eify-vector cat /etc/vector/vector.toml
curl http://vector:8686/metrics
```

### ClickHouse 查询慢

```sql
SELECT * FROM system.query_log
WHERE type = 'QueryFinish'
  AND query_duration_ms > 1000
ORDER BY event_time DESC
LIMIT 10;
```

---

## 参见

- [../../docs/guides/LOGGING.md](../../docs/guides/LOGGING.md) — 日志格式和架构
- [../../docs/guides/DATABASE.md](../../docs/guides/DATABASE.md) — 数据库设计（含 ClickHouse 日志库）
