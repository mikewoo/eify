-- =============================================================================
-- ClickHouse 日志表初始化脚本
-- 使用方式: clickhouse-client --multiquery < init.sql
-- =============================================================================

-- 创建日志数据库
CREATE DATABASE IF NOT EXISTS logs;

-- 使用日志数据库
USE logs;

-- 删除旧表（谨慎！生产环境请先备份）
DROP TABLE IF EXISTS app_logs;
DROP TABLE IF EXISTS req_logs_mv;
DROP TABLE IF EXISTS sql_logs_mv;
DROP TABLE IF EXISTS msg_logs_mv;
DROP TABLE IF EXISTS error_stats_hourly_mv;
DROP TABLE IF EXISTS slow_requests_minute_mv;
DROP TABLE IF EXISTS slow_sql_hourly_mv;
DROP TABLE IF EXISTS error_stats_hourly;
DROP TABLE IF EXISTS slow_requests_minute;
DROP TABLE IF EXISTS slow_sql_hourly;

-- =============================================================================
-- 主表：app_logs（最新结构）
-- =============================================================================
CREATE TABLE IF NOT EXISTS app_logs (
    -- ==================== 标准字段 (所有日志类型共有) ====================
    timestamp DateTime64(3) CODEC(DoubleDelta),
    pid String CODEC(ZSTD(1)),
    className String CODEC(ZSTD(1)),
    lineNumber UInt32,
    level LowCardinality(String) CODEC(ZSTD(1)),      -- TRACE/DEBUG/INFO/WARN/ERROR
    logType LowCardinality(String) CODEC(ZSTD(1)),    -- req/sql/msg/simple/sys
    traceId String CODEC(ZSTD(1)),
    spanId String CODEC(ZSTD(1)),
    appName LowCardinality(String) CODEC(ZSTD(1)),
    serverIp LowCardinality(String) CODEC(ZSTD(1)),
    appVersion LowCardinality(String) CODEC(ZSTD(1)),
    pod LowCardinality(String) CODEC(ZSTD(1)),
    duration UInt32 CODEC(ZSTD(1)),

    -- ==================== 通用字段 (所有日志类型) ====================
    message String CODEC(ZSTD(3)),                     -- 完整 message JSON 字符串
    exception Nullable(String) CODEC(ZSTD(3)),         -- 异常信息 JSON

    -- ==================== REQ 日志字段 (仅 req 日志使用) ====================
    clientIp Nullable(String) CODEC(ZSTD(1)),
    method Nullable(String) CODEC(ZSTD(1)),
    path Nullable(String) CODEC(ZSTD(1)),
    status Nullable(UInt32),
    userAgent Nullable(String) CODEC(ZSTD(3)),
    error Nullable(String) CODEC(ZSTD(3)),             -- 错误信息
    requestBody Nullable(String) CODEC(ZSTD(3)),       -- 请求体
    requestBodySize Nullable(UInt32),                 -- 请求体大小（字节）
    response Nullable(String) CODEC(ZSTD(3)),          -- 响应体
    responseSize Nullable(UInt32),                    -- 响应体大小（字节）
    asyncRequest Nullable(UInt8),                     -- 是否异步请求
    asyncType Nullable(String) CODEC(ZSTD(1)),        -- 异步类型
    asyncCompletionStatus Nullable(String) CODEC(ZSTD(1)), -- 异步完成状态

    -- ==================== SQL 日志字段 (仅 sql 日志使用) ====================
    sql Nullable(String) CODEC(ZSTD(3)),
    executionTime Nullable(UInt32),
    isSlowQuery Nullable(UInt8),
    rowCount Nullable(UInt32),
    errorStack Nullable(String) CODEC(ZSTD(3)),        -- 错误堆栈

    -- ==================== MSG 日志字段 (仅 msg 日志使用) ====================
    msgType Nullable(String) CODEC(ZSTD(1)),
    operationType Nullable(String) CODEC(ZSTD(1)),     -- PRODUCE/CONSUME
    topic Nullable(String) CODEC(ZSTD(1)),
    partition Nullable(UInt32),
    offset Nullable(UInt64),
    key Nullable(String) CODEC(ZSTD(1)),               -- 消息键
    consumerGroupId Nullable(String) CODEC(ZSTD(1)),
    processResult Nullable(String) CODEC(ZSTD(1)),
    retryCount Nullable(UInt32),                       -- 重试次数
    producerTime Nullable(DateTime64(3)),
    consumerTime Nullable(DateTime64(3)),
    payloadSize Nullable(UInt32),

    -- ==================== 系统字段 ====================
    createdAt DateTime64(3) DEFAULT now64() CODEC(DoubleDelta)
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, logType, level, traceId, appName)
TTL timestamp + INTERVAL 30 DAY
SETTINGS index_granularity = 8192;

-- =============================================================================
-- 索引优化
-- =============================================================================

-- 布隆过滤器索引（加速等值查询）
ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_trace_id
    traceId(1024) TYPE bloom_filter GRANULARITY 1;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_log_type
    logType(16) TYPE bloom_filter GRANULARITY 1;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_level
    level(8) TYPE bloom_filter GRANULARITY 1;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_class_name
    className(64) TYPE bloom_filter GRANULARITY 1;

-- Skip 索引（加速范围查询）
ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_timestamp_minmax
    timestamp TYPE minmax GRANULARITY 4;

ALTER TABLE app_logs ADD INDEX IF NOT EXISTS idx_level_set
    level TYPE set(8) GRANULARITY 2;

-- =============================================================================
-- 物化视图：按日志类型优化查询
-- =============================================================================

-- 请求日志视图
CREATE MATERIALIZED VIEW IF NOT EXISTS req_logs_mv
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, traceId, path, status)
TTL timestamp + INTERVAL 30 DAY
SETTINGS allow_nullable_key = 1
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
SETTINGS allow_nullable_key = 1
AS SELECT
    timestamp, pid, className, lineNumber, level,
    traceId, spanId, appName, serverIp, appVersion, pod, duration,
    sql, executionTime, isSlowQuery, rowCount, errorStack, message, exception
FROM app_logs
WHERE logType = 'sql';

-- 消息队列日志视图
CREATE MATERIALIZED VIEW IF NOT EXISTS msg_logs_mv
ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, msgType, topic, partition)
TTL timestamp + INTERVAL 30 DAY
SETTINGS allow_nullable_key = 1
AS SELECT
    timestamp, pid, className, lineNumber, level,
    traceId, spanId, appName, serverIp, appVersion, pod, duration,
    msgType, operationType, topic, partition, offset, key,
    consumerGroupId, processResult, error, retryCount,
    producerTime, consumerTime, payloadSize, message, exception
FROM app_logs
WHERE logType = 'msg';

-- =============================================================================
-- 统计视图：汇总分析
-- =============================================================================

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

-- 错误统计表
CREATE TABLE IF NOT EXISTS error_stats_hourly (
    hour DateTime,
    appName LowCardinality(String),
    level LowCardinality(String),
    className String,
    error_count UInt64
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, appName, level, className)
TTL hour + INTERVAL 7 DAY;

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

-- 慢请求统计表
CREATE TABLE IF NOT EXISTS slow_requests_minute (
    minute DateTime,
    appName LowCardinality(String),
    path String,
    request_count UInt64,
    avg_duration Float64,
    max_duration UInt32
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(minute)
ORDER BY (minute, appName, path)
TTL minute + INTERVAL 7 DAY;

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

-- 慢 SQL 统计表
CREATE TABLE IF NOT EXISTS slow_sql_hourly (
    hour DateTime,
    appName LowCardinality(String),
    className String,
    query_count UInt64,
    avg_execution_time Float64,
    max_execution_time UInt32
)
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMM(hour)
ORDER BY (hour, appName, className)
TTL hour + INTERVAL 7 DAY;

-- =============================================================================
-- 查询示例
-- =============================================================================

-- 1. 查询最近 100 条日志
-- SELECT * FROM app_logs ORDER BY timestamp DESC LIMIT 100;

-- 2. 按 traceId 查询完整调用链
-- SELECT * FROM app_logs WHERE traceId = 'xxx' ORDER BY timestamp;

-- 3. 统计各日志类型数量
-- SELECT logType, count() FROM app_logs GROUP BY logType;

-- 4. 查询慢请求（使用物化视图，更快）
-- SELECT * FROM req_logs_mv WHERE duration > 1000 ORDER BY timestamp DESC LIMIT 100;

-- 5. 查询慢 SQL（使用物化视图）
-- SELECT * FROM sql_logs_mv WHERE isSlowQuery = 1 ORDER BY timestamp DESC LIMIT 100;

-- 6. 查询错误日志
-- SELECT * FROM app_logs WHERE level = 'ERROR' ORDER BY timestamp DESC LIMIT 100;

-- 7. 统计每个路径的请求次数
-- SELECT path, count() AS requests, avg(duration) AS avg_duration
-- FROM req_logs_mv
-- WHERE path != ''
-- GROUP BY path
-- ORDER BY requests DESC
-- LIMIT 20;

-- 8. 查看错误统计
-- SELECT * FROM error_stats_hourly ORDER BY hour DESC LIMIT 24;

-- 9. 查看慢请求统计
-- SELECT * FROM slow_requests_minute ORDER BY minute DESC LIMIT 60;

-- 10. 查看慢 SQL 统计
-- SELECT * FROM slow_sql_hourly ORDER BY hour DESC LIMIT 24;

-- 11. 按日志类型统计存储使用情况
-- SELECT
--     logType,
--     count() as row_count,
--     formatReadableSize(sum(bytes)) as size
-- FROM system.parts
-- WHERE table = 'app_logs' AND active
-- GROUP BY logType;

-- 12. 查询响应体（顶层字段）
-- SELECT
--     path,
--     status,
--     responseSize,
--     response
-- FROM app_logs
-- WHERE logType = 'req' AND response != ''
-- ORDER BY timestamp DESC
-- LIMIT 10;
