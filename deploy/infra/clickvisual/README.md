# ClickVisual 配置指南
# ClickVisual 是一个基于 ClickHouse 的日志可视化平台

## 1. 安装 ClickVisual

```bash
# Docker 安装
docker run -d \
  --name clickvisual \
  -p 19001:19001 \
  -e CLICKHOUSE_DS_URL="clickhouse://default:@clickhouse-server:9000/logs" \
  -e CLICKHOUSE_DS_USER="default" \
  -e CLICKHOUSE_DS_PASSWORD="" \
  -v /opt/clickvisual/conf:/opt/clickvisual/conf \
  -v /opt/clickvisual/data:/opt/clickvisual/data \
  ghcr.io/clickvisual/clickvisual:latest

# 或使用 Docker Compose
version: '3'
services:
  clickvisual:
    image: ghcr.io/clickvisual/clickvisual:latest
    container_name: clickvisual
    ports:
      - "19001:19001"
    environment:
      - CLICKHOUSE_DS_URL=clickhouse://default:@clickhouse:9000/logs
      - CLICKHOUSE_DS_USER=default
      - CLICKHOUSE_DS_PASSWORD=
    volumes:
      - ./clickvisual/conf:/opt/clickvisual/conf
      - ./clickvisual/data:/opt/clickvisual/data
    depends_on:
      - clickhouse
    networks:
      - clickhouse-net

  clickhouse:
    image: clickhouse/clickhouse-server:latest
    container_name: clickhouse
    ports:
      - "8123:8123"
      - "9000:9000"
    volumes:
      - ./clickhouse/data:/var/lib/clickhouse
      - ./clickhouse/conf.d:/etc/clickhouse-server/config.d
    networks:
      - clickhouse-net

networks:
  clickhouse-net:
    driver: bridge
```

## 2. ClickVisual 数据源配置

在 ClickVisual 界面中配置数据源：

```json
{
  "name": "eify-logs",
  "type": "clickhouse",
  "connection": {
    "host": "localhost",
    "port": 8123,
    "database": "logs",
    "username": "default",
    "password": "",
    "compress": true
  }
}
```

## 3. 日志视图配置

### 3.1 主日志查询视图

```yaml
# 视图配置
name: "Eify 应用日志"
table: "logs.app_logs"
description: "Eify 应用主日志查询"

# 查询配置
query:
  default: |
    SELECT
      timestamp,
      level,
      log_type,
      logger_name,
      message,
      trace_id,
      span_id,
      app,
      env
    FROM app_logs
    WHERE timestamp >= now() - INTERVAL 1 HOUR
    ORDER BY timestamp DESC
    LIMIT 100

# 字段配置
fields:
  - name: timestamp
    label: "时间戳"
    type: "datetime"
    format: "YYYY-MM-DD HH:mm:ss"
    sortable: true

  - name: level
    label: "日志级别"
    type: "enum"
    values: ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
    color:
      ERROR: "#ff0000"
      WARN: "#ffa500"
      INFO: "#0000ff"
      DEBUG: "#808080"
      TRACE: "#c0c0c0"

  - name: log_type
    label: "日志类型"
    type: "enum"
    values: ["req", "sql", "msg", "simple", "sys"]

  - name: message
    label: "消息"
    type: "string"
    highlight: true

  - name: trace_id
    label: "TraceId"
    type: "string"
    link: "/traces/{value}"

  - name: app
    label: "应用"
    type: "string"
    filter: true

  - name: env
    label: "环境"
    type: "enum"
    values: ["dev", "test", "prod"]
```

### 3.2 错误日志视图

```yaml
name: "错误日志统计"
table: "logs.error_stats"

query:
  default: |
    SELECT
      toDateTime(hour, 'Asia/Shanghai') as hour,
      app,
      env,
      level,
      sum(error_count) as total_errors
    FROM error_stats
    WHERE hour >= now() - INTERVAL 24 HOUR
    GROUP BY hour, app, env, level
    ORDER BY hour DESC

# 图表配置
chart:
  type: "line"
  x_axis: "hour"
  y_axis: "total_errors"
  series:
    - field: "total_errors"
      name: "错误数量"
      color: "#ff0000"
```

### 3.3 慢查询视图

```yaml
name: "慢 SQL 查询"
table: "logs.slow_query_stats"

query:
  default: |
    SELECT
      hour,
      app,
      class_name,
      query_count,
      avg_line
    FROM slow_query_stats
    WHERE hour >= now() - INTERVAL 24 HOUR
    ORDER BY query_count DESC
    LIMIT 50
```

## 4. 告警配置

### 4.1 错误率告警

```yaml
name: "错误率告警"
description: "当错误率超过阈值时触发"

# 查询配置
query:
  sql: |
    SELECT
      countIf(level = 'ERROR') as error_count,
      count() as total_count,
      error_count / total_count * 100 as error_rate
    FROM app_logs
    WHERE timestamp >= now() - INTERVAL 5 MINUTE

# 触发条件
trigger:
  type: "threshold"
  field: "error_rate"
  operator: ">"
  value: 5  # 错误率 > 5%

# 通知配置
notification:
  type: "webhook"
  url: "http://your-webhook-url/alert"
  headers:
    Content-Type: "application/json"
  body: |
    {
      "alert_name": "Eify 错误率告警",
      "severity": "critical",
      "message": "错误率: ${error_rate}%, 错误数: ${error_count}, 总数: ${total_count}",
      "timestamp": "${timestamp}",
      "app": "eify"
    }

  # 钉钉通知
  type: "dingtalk"
  webhook: "https://oapi.dingtalk.com/robot/send?access_token=xxx"
  secret: "SEC***"
  msgtype: "markdown"
  markdown: |
    ### Eify 错误率告警
    
    **应用**: eify  
    **错误率**: ${error_rate}%  
    **错误数**: ${error_count}  
    **总数**: ${total_count}  
    **时间**: ${timestamp}
```

### 4.2 慢查询告警

```yaml
name: "慢查询告警"
description: "当慢查询数量超过阈值时触发"

query:
  sql: |
    SELECT
      class_name,
      count() as slow_count,
      avg(JSONExtractFloat(extra_fields, 'executionTime')) as avg_time
    FROM app_logs
    WHERE log_type = 'sql'
      AND JSONExtractFloat(extra_fields, 'executionTime') > 1000
      AND timestamp >= now() - INTERVAL 5 MINUTE
    GROUP BY class_name
    HAVING slow_count > 10

trigger:
  type: "exists"  # 只要查询有结果就触发

notification:
  type: "dingtalk"
  webhook: "https://oapi.dingtalk.com/robot/send?access_token=xxx"
  msgtype: "markdown"
  markdown: |
    ### Eify 慢查询告警
    
    **慢查询数量**: ${slow_count}  
    **平均耗时**: ${avg_time}ms  
    **类名**: ${class_name}
```

## 5. 仪表盘配置

创建综合监控仪表盘：

```yaml
name: "Eify 应用监控仪表盘"
panels:
  # 总请求数
  - title: "总请求数（1小时）"
    type: "stat"
    query: |
      SELECT count() as value
      FROM app_logs
      WHERE log_type = 'req'
        AND timestamp >= now() - INTERVAL 1 HOUR

  # 错误率趋势
  - title: "错误率趋势（24小时）"
    type: "line"
    query: |
      SELECT
        toStartOfMinute(timestamp) as time,
        countIf(level = 'ERROR') / count() * 100 as error_rate
      FROM app_logs
      WHERE timestamp >= now() - INTERVAL 24 HOUR
      GROUP BY time
      ORDER BY time

  # 按级别统计
  - title: "日志级别分布"
    type: "pie"
    query: |
      SELECT
        level,
        count() as count
      FROM app_logs
      WHERE timestamp >= now() - INTERVAL 1 HOUR
      GROUP BY level

  # Top 10 错误日志
  - title: "Top 10 错误日志"
    type: "table"
    query: |
      SELECT
        logger_name,
        log_type,
        message,
        count() as error_count
      FROM app_logs
      WHERE level = 'ERROR'
        AND timestamp >= now() - INTERVAL 1 HOUR
      GROUP BY logger_name, log_type, message
      ORDER BY error_count DESC
      LIMIT 10

  # Trace 统计
  - title: "平均响应时间（按 Trace）"
    type: "bar"
    query: |
      SELECT
        trace_id,
        max(JSONExtractFloat(extra_fields, 'duration')) as duration
      FROM app_logs
      WHERE log_type = 'req'
        AND timestamp >= now() - INTERVAL 1 HOUR
      GROUP BY trace_id
      ORDER BY duration DESC
      LIMIT 20
```

## 6. 查询示例

### 6.1 按时间范围查询
```sql
-- 最近 1 小时
WHERE timestamp >= now() - INTERVAL 1 HOUR

-- 今天
WHERE toDate(timestamp) = today()

-- 最近 7 天
WHERE timestamp >= now() - INTERVAL 7 DAY
```

### 6.2 按 TraceId 查询调用链
```sql
SELECT * FROM app_logs
WHERE trace_id = 'your-trace-id'
ORDER BY timestamp;
```

### 6.3 全文搜索
```sql
-- 搜索包含特定关键词的日志
SELECT * FROM app_logs
WHERE positionCaseInsensitive(message, '错误') > 0
  AND timestamp >= now() - INTERVAL 1 HOUR;

-- 使用正则表达式
SELECT * FROM app_logs
WHERE match(message, '(登录|login|auth)')
  AND timestamp >= now() - INTERVAL 1 HOUR;
```

### 6.4 聚合分析
```sql
-- 每分钟的请求量
SELECT
    toStartOfMinute(timestamp) as minute,
    count() as request_count
FROM app_logs
WHERE log_type = 'req'
  AND timestamp >= now() - INTERVAL 1 HOUR
GROUP BY minute
ORDER BY minute;

-- 按 URL 分组统计
SELECT
    JSONExtractString(extra_fields, 'path') as path,
    count() as count,
    avg(JSONExtractFloat(extra_fields, 'duration')) as avg_duration
FROM app_logs
WHERE log_type = 'req'
  AND timestamp >= now() - INTERVAL 1 HOUR
GROUP BY path
ORDER BY count DESC;
```

## 7. 性能优化

### 7.1 分区优化
```sql
-- 按日期和小时双重分区
ALTER TABLE app_logs
MODIFY PARTITION BY
  (toYYYYMM(timestamp), toDayOfMonth(timestamp));
```

### 7.2 物化视图预聚合
```sql
-- 创建分钟级聚合表
CREATE TABLE IF NOT EXISTS app_logs_minute (
    minute DateTime,
    app LowCardinality(String),
    env LowCardinality(String),
    level LowCardinality(String),
    count UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(minute)
ORDER BY (minute, app, env, level);

-- 创建物化视图
CREATE MATERIALIZED VIEW IF NOT EXISTS app_logs_minute_mv
TO app_logs_minute
AS SELECT
    toStartOfMinute(timestamp) as minute,
    app,
    env,
    level,
    count() as count
FROM app_logs
GROUP BY minute, app, env, level;
```

## 8. 告警 Webhook 示例

### 8.1 企业微信机器人
```json
{
  "msgtype": "markdown",
  "markdown": {
    "content": "### Eify 应用告警\n\n**告警类型**: ${alert_type}\n**严重程度**: ${severity}\n**详细信息**: ${message}\n**时间**: ${timestamp}"
  }
}
```

### 8.2 钉钉机器人
```json
{
  "msgtype": "markdown",
  "markdown": {
    "title": "Eify 告警",
    "text": "## ${alert_type}\n\n**应用**: ${app}\n**环境**: ${env}\n**消息**: ${message}\n**时间**: ${timestamp}"
  }
}
```

### 8.3 飞书机器人
```json
{
  "msg_type": "interactive",
  "card": {
    "header": {
      "title": {
        "tag": "plain_text",
        "content": "Eify 应用告警"
      },
      "template": "red"
    },
    "elements": [
      {
        "tag": "div",
        "text": {
          "tag": "lark_md",
          "content": "**告警类型**: ${alert_type}\n**严重程度**: ${severity}\n**详细信息**: ${message}"
        }
      }
    ]
  }
}
```
