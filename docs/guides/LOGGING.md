# 日志系统

## 目录

- [概述](#概述)
- [架构设计](#架构设计)
- [日志格式](#日志格式)
- [日志等级](#日志等级)
- [日志类型](#日志类型)
- [TraceId 和 SpanId](#traceid-和-spanid)
- [消息队列日志](#消息队列日志)
- [配置说明](#配置说明)
- [使用指南](#使用指南)
- [ClickHouse 存储](#clickhouse-存储)
- [日志监控方案](#日志监控方案)
- [性能分析与瓶颈](#性能分析与瓶颈)
- [常见问题](#常见问题)

---

## 概述

eify-common 模块配置了统一的日志系统，支持环境区分和链路追踪。

### 设计目标

- **统一格式**：所有日志输出为纯 JSON 格式，标准字段在顶层
- **UTC 时区**：使用 UTC 时区确保 ClickHouse 兼容性
- **智能分类**：自动识别日志类型（req/sql/msg/simple/sys）
- **字段分布**：类型专用字段同时存在于顶层和 message 中（查询性能 + 数据完整性）
- **高性能**：异步日志，不阻塞业务线程
- **可观测**：集成 TraceId/SpanId，支持链路追踪

### 代码结构

```
eify-common/
├── src/main/resources/
│   └── logback-spring.xml                  # Logback 配置（区分环境）
└── src/main/java/com/eify/common/
    ├── log/
    │   ├── model/                         # 数据模型
    │   │   ├── LogHeader.java             # 日志 Header（14 个标准字段）
    │   │   ├── LogLevel.java              # 日志等级枚举
    │   │   ├── LogMessage.java            # 消息基类
    │   │   └── LogType.java               # 日志类型枚举
    │   ├── config/                        # 配置类
    │   │   ├── MqLogConfig.java           # MQ 日志配置
    │   │   └── ReqLogConfig.java          # 请求日志配置
    │   ├── util/
    │   │   └── StructuredLogger.java      # 结构化日志工具类
    │   ├── layout/
    │   │   └── StructuredLogLayout.java   # JSON 格式输出（UTC 时区）
    │   ├── message/                       # 消息类型
    │   │   ├── ReqLogMessage.java         # 请求日志消息
    │   │   ├── SqlLogMessage.java         # SQL 日志消息
    │   │   ├── MsgLogMessage.java         # 消息队列日志消息
    │   │   └── SimpleLogMessage.java      # 业务日志消息
    │   ├── mq/                            # MQ 自动日志拦截
    │   │   ├── MsgLogAutoConfiguration.java
    │   │   ├── MsgLogContext.java
    │   │   ├── MsgLogInterceptor.java
    │   │   ├── AsyncTaskInterceptor.java  # @Async 自动日志
    │   │   └── EventListenerInterceptor.java  # @EventListener 自动日志
    │   ├── RequestLogInterceptor.java     # HTTP 请求日志拦截器
    │   ├── ResponseLoggingFilter.java     # 响应包装过滤器
    │   ├── ResponseWrapper.java           # 响应体缓存包装器
    │   ├── SqlLogAutoConfiguration.java   # SQL 日志自动配置
    │   ├── SqlLogInterceptor.java         # SQL 日志拦截器
    │   ├── SqlLogProperties.java          # SQL 日志配置属性
    │   ├── AsyncLoggingListener.java      # 异步请求完成监听器
    │   ├── TraceContext.java              # 跨线程 Trace 传递
    │   ├── TraceIdUtils.java              # TraceId 工具类
    │   ├── AppInfo.java                   # 应用信息工具
    │   └── AppVersion.java                # 应用版本管理
    └── config/
        ├── WebLogConfig.java              # Web 日志配置
        └── MicrometerTracerConfig.java    # Tracing 配置
```

---

## 架构设计

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                       应用层 (Spring Boot)                       │
│                                                                  │
│  业务代码 (com.eify)              │ 框架代码 (其他包)            │
│  StructuredLogger.logReq()        │ logger.info("message")      │
│  StructuredLogger.logSql()        │                             │
│  StructuredLogger.logMsg()        │                             │
│                              ↓                                   │
│  StructuredLogLayout (纯 JSON 格式化, UTC 时区)                  │
│                              ↓                                   │
│  AsyncAppender (异步队列, neverBlock=true, queueSize=10000)     │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                       输出层 (文件)                               │
│  所有环境: 纯 JSON 格式 (UTC 时区)                               │
│  文件位置: ./logs/eify.log                                      │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                       采集层 (Vector)                             │
│  文件读取 → JSON 解析 → 字段转换 → ClickHouse 写入              │
└─────────────────────────────────────────────────────────────────┘
                                ↓
┌─────────────────────────────────────────────────────────────────┐
│                       存储层 (ClickHouse)                         │
│  分区表 + 跳数索引 + TTL 30 天自动清理                           │
└─────────────────────────────────────────────────────────────────┘
```

### 智能分类机制

StructuredLogLayout 实现三层分类逻辑，自动识别日志类型：

| 层级 | 触发条件 | logType | message | 示例 |
|:---|:---|:---|:---|:---|
| **第1层** | MDC 包含 `_structuredLogType` | `req`/`sql`/`msg` | 原始 JSON | StructuredLogger.logReq() |
| **第2层** | 类名以 `com.eify` 开头 | `simple` | 解析后的 JSON | `log.info("message")` |
| **第3层** | 其他所有日志 | `sys` | 包含 logger/thread | 框架日志 |

```java
// 第1层：StructuredLogger 日志（通过 MDC 标记）
String structuredLogType = event.getMDCPropertyMap().get("_structuredLogType");

// 第2层：业务代码日志（com.eify 包）
else if (isBusinessLog(className)) { logType = "simple"; }

// 第3层：框架日志（其他包）
else { logType = "sys"; }
```

### JSON 字符串自动解析

业务代码中记录 JSON 数据时，StructuredLogLayout 自动解析为 JSON 对象：

```java
// 输入
log.info("agent info: {}", JSONUtil.toJsonStr(agent));

// 输出
{
  "message": "agent info: ",
  "data": { "name": "客服助手", "id": 1 }
}
```

### 异步日志优化

所有日志通过 AsyncAppender 异步写入，不阻塞业务线程：

```xml
<appender name="FILE_JSON_ASYNC" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>10000</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <neverBlock>true</neverBlock>
    <appender-ref ref="FILE_JSON"/>
</appender>
```

| 指标 | 同步日志 | 异步日志 | 提升 |
|:---|:---|:---|:---|
| 单次耗时 | ~200 μs | ~1 μs | **200 倍** |
| QPS 上限 | ~5000 | ~50000 | **10 倍** |

### 响应记录架构

ResponseLoggingFilter（HIGHEST_PRECEDENCE）包装响应对象 → RequestLogInterceptor 记录日志。异步请求通过 AsyncLoggingListener 监听完成状态（COMPLETED/TIMEOUT/ERROR）。

---

## 日志格式

### 纯 JSON 格式（UTC 时间）

日志输出为**纯 JSON 格式**，使用 **UTC 时区**（确保 ClickHouse 兼容性）。

**字段分布策略**：
- **顶层字段**：标准字段 + 类型专用字段（方便 ClickHouse 查询）
- **message 字段**：保留完整业务数据（字段重复，方便调试）

```json
{
  "timestamp": "2026-05-15T07:55:33.417Z",
  "pid": "31",
  "className": "com.eify.common.log.RequestLogInterceptor",
  "lineNumber": 58,
  "level": "INFO",
  "logType": "req",
  "traceId": "2044610983019810816",
  "spanId": "2044610983019810816",
  "appName": "eify",
  "serverIp": "10.0.0.1",
  "appVersion": "1.0.0",
  "pod": "local",
  "duration": 150,
  "clientIp": "0:0:0:0:0:0:0:1",
  "method": "GET",
  "path": "/api/v1/health",
  "status": 200,
  "message": {
    "method": "GET",
    "path": "/api/v1/health",
    "status": 200,
    "duration": 150
  }
}
```

### 标准字段（所有日志类型）

| 字段 | 类型 | 说明 | 示例 |
|:---|:---|:---|:---|
| **timestamp** | String | 日志时间戳 (**UTC**，ISO 8601) | `"2026-05-15T07:55:33.417Z"` |
| **pid** | String | 进程 ID | `"31"` |
| **className** | String | 类名 | `"com.eify.common.log.RequestLogInterceptor"` |
| **lineNumber** | Int | 行号 | `58` |
| **level** | String | 日志级别（Java 标准） | `"INFO"` |
| **logType** | String | 日志类型 | `"req"` |
| **traceId** | String | 链路追踪 ID | `"2044610983019810816"` |
| **spanId** | String | Span ID | `"2044610983019810816"` |
| **appName** | String | 应用名称 | `"eify"` |
| **serverIp** | String | 服务器 IP | `"10.0.0.1"` |
| **appVersion** | String | 应用版本 | `"1.0.0"` |
| **pod** | String | Pod 名称 | `"local"` |
| **duration** | Int | 耗时（毫秒） | `150` |
| **message** | Object | 业务数据 | `{...}` |
| **exception** | Object | 异常信息（可选） | `{...}` |

---

## 日志等级

Java 标准日志等级（大写）：

| 等级 | 数值 | 说明 | 使用场景 | SLF4J 方法 |
|:---|:---:|:---|:---|:---|
| **TRACE** | 0 | 最详细的跟踪信息 | 极详细的调试信息 | log.trace() |
| **DEBUG** | 1 | 调试信息 | 开发调试信息 | log.debug() |
| **INFO** | 2 | 一般信息（默认） | 正常业务流程 | log.info() |
| **WARN** | 3 | 警告信息 | 慢请求、慢查询、潜在问题 | log.warn() |
| **ERROR** | 4 | 错误信息 | 异常、错误响应、处理失败 | log.error() |

### 自动日志等级规则

StructuredLogger 根据日志内容**自动设置日志等级**，并使用**对应的 SLF4J 日志方法**：

| 日志类型 | INFO | WARN | ERROR |
|:---|:---|:---|:---|
| **REQ** | status < 400 且耗时 < 1000ms | 耗时 >= 1000ms | status >= 400 |
| **SQL** | 正常查询 | 慢查询（isSlowQuery = true） | 有错误（error != null） |
| **MSG** | 处理成功（processResult = SUCCESS） | 耗时 >= 1000ms | 处理失败（processResult = FAILED） |
| **SIMPLE** | 根据 level 字段 | - | - |
| **SYS** | 使用实际日志等级 | - | - |

### 日志级别最佳实践

| 级别 | 使用场景 | 示例 | 是否需要告警 |
|:---|:---|:---|:---:|
| **ERROR** | 系统错误、异常、处理失败 | 数据库连接失败、空指针异常 | 是 |
| **WARN** | 潜在问题、慢请求、慢查询 | SQL 查询耗时 2000ms | 否 |
| **INFO** | 正常业务流程 | 用户登录成功、订单创建完成 | 否 |
| **DEBUG** | 开发调试信息 | 方法入参、返回值 | 否 |
| **TRACE** | 极详细的调试信息 | 循环中的变量值 | 否 |

**GlobalExceptionHandler 示例**：

```java
// 业务异常 - WARN
@ExceptionHandler(BizException.class)
public Result<Void> handleBizException(BizException e) {
    log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
    return Result.fail(e.getCode(), e.getMessage());
}

// 系统异常 - ERROR
@ExceptionHandler(Exception.class)
public Result<Void> handleException(Exception e) {
    log.error("系统异常", e);
    return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage());
}
```

---

## 日志类型

### 概览

| 类型 | 代码 | 说明 | 专用字段数 |
|:---|:---|:---|:---:|
| **REQ** | `req` | HTTP 请求日志 | 16 |
| **SQL** | `sql` | 数据库查询日志 | 9 |
| **MSG** | `msg` | 消息队列日志 | 16 |
| **SIMPLE** | `simple` | 业务代码日志 | - |
| **SYS** | `sys` | 系统/框架日志 | - |

### 1. REQ 日志（请求日志）

**顶层字段**：`clientIp`, `method`, `path`, `status`, `userAgent`, `requestBody`, `requestBodySize`, `response`, `responseSize`, `error`, `asyncRequest`, `asyncType`, `asyncCompletionStatus`

**message 字段结构**：
```java
{
  "method": "GET", "path": "/api/v1/health", "status": 200,
  "clientIp": "0:0:0:0:0:0:0:1", "userAgent": "curl/8.7.1",
  "duration": 150, "responseSize": 131
  // 其他可选字段: requestBody, requestBodySize, responseBody, error, asyncRequest, asyncType, asyncCompletionStatus
}
```

**字段映射**：`responseBody` → `response`（ClickHouse 列名）

**响应体记录策略**：

| 场景 | 记录方式 | 配置项 |
|:---|:---|:---|
| **错误响应** | 完整记录（JSON 对象） | `req.logging.record-error-response: true` |
| **成功响应** | 根据配置记录 | `req.logging.record-success-response: false` |
| **采样响应** | 按采样率记录 | `req.logging.sampling-rate: 0.01` |

### 2. SQL 日志（数据库日志）

**顶层字段**：`sql`, `executionTime`, `isSlowQuery`, `rowCount`, `errorStack`

**message 字段结构**：
```java
{
  "appName": "eify", "sql": "SELECT * FROM ai_agent...", "executionTime": 71,
  "isSlowQuery": false, "rowCount": 1
  // 其他可选字段: params, mappedId, error, errorStack
}
```

### 3. MSG 日志（消息队列日志）

**顶层字段**：`msgType`, `operationType`, `topic`, `partition`, `offset`, `key`, `consumerGroupId`, `processResult`, `errorStack`, `retryCount`, `producerTime`, `consumerTime`, `payloadSize`

**message 字段结构**：
```java
{
  "msgType": "KAFKA", "operationType": "CONSUME", "topic": "chat.events",
  "partition": 0, "offset": 12345, "consumerGroupId": "consumer-group",
  "processResult": "SUCCESS", "duration": 10, "payloadSize": 256
  // 其他可选字段: key, payload, error, errorStack, producerTime, consumerTime, retryCount, sampled, extensions
}
```

**msgType 默认值**：如果未显式设置 `msgType`，系统会自动设置为 `ASYNC`。推荐明确指定以获得更准确的分类（KAFKA、ROCKETMQ、RABBITMQ、REDIS、ASYNC、EVENT）。

### 4. SIMPLE 日志（业务日志）

```java
{
  "message": "用户登录成功",
  "level": "INFO",
  "tags": { "userId": "123", "action": "login" }
}
```

### 5. SYS 日志（系统/框架日志）

```java
{
  "message": "Completed initialization in 10 ms",
  "logger": "o.s.web.servlet.DispatcherServlet",
  "thread": "http-nio-8080-exec-1"
}
```

---

## TraceId 和 SpanId

### 概念

```
TraceId: 2044610983019810816...
├── SpanId: 2044610983019810816 (根节点)         → parentSpanId = null
├── SpanId: 3019822345129822345 (HTTP请求)       → parentSpanId = 2044610983019810816
├── SpanId: 4029933456239933456 (数据库查询)     → parentSpanId = 3019822345129822345
└── SpanId: 5030044567340044567 (外部API调用)    → parentSpanId = 3019822345129822345
```

| 维度 | TraceId | SpanId |
|:---|:---|:---|
| **作用** | 标识整个调用链 | 标识单个操作/步骤 |
| **范围** | 全局唯一 | 同一 Trace 内唯一 |
| **共享** | 所有相关 Span 共享 | 每个 Span 独立 |

### 生成机制

使用 **Micrometer Tracing + Brave** 自动生成，通过父-子 span 结构确保两者不同。TraceId 和 SpanId 由 Brave Tracing 自动设置到 MDC：

```java
private static final String MDC_TRACE_ID = "traceId";
private static final String MDC_SPAN_ID = "spanId";
```

### 跨线程传递

使用 `TraceContext` 工具类传递 TraceId 和 SpanId：

```java
// Runnable
CompletableFuture.runAsync(TraceContext.wrap(() -> {
    log.info("异步任务 - traceId 自动传递");
}));

// Supplier
String result = TraceContext.call(() -> {
    log.info("这段代码的日志会带上 traceId");
    return "结果";
});
```

---

## 消息队列日志

### 核心理念

**零代码侵入**：通过 AOP 拦截器自动记录日志，业务代码无需添加任何日志调用。

### 支持的自动记录场景

| 场景 | msgType | 触发方式 | 状态 |
|:---|:---|:---|:---|
| **异步任务** | `ASYNC` | `@Async` 注解 | 已实现 |
| **Spring 事件** | `EVENT` | `@EventListener` 注解 | 已实现 |
| **Kafka 生产** | `KAFKA` | Producer 拦截器 | 已实现 |
| **Kafka 消费** | `KAFKA` | Consumer 拦截器 | 已实现 |
| **RocketMQ** | `ROCKETMQ` | 拦截器 | 架构已支持 |
| **RabbitMQ** | `RABBITMQ` | 拦截器 | 架构已支持 |

### 使用方式

自动配置已通过 `META-INF/spring.factories` 注册，无需额外配置。

- **异步任务**：添加 `@Async` 注解即可，日志自动记录
- **Spring 事件**：添加 `@EventListener` 注解即可，支持条件过滤
- **Kafka 消息**：在 Producer/Consumer 配置中添加 `KafkaMsgProducerInterceptor` / `KafkaMsgConsumerInterceptor` 拦截器

### 手动记录（高级用法）

当自动记录不满足需求时（如需要记录额外信息），使用 `StructuredLogger.logMsg()` 手动构建 `MsgLogMessage`，可添加 `extension()` 扩展字段。

### MsgLogMessage 完整字段（16 个专用字段）

| 字段分类 | 字段 | 说明 |
|:---|:---|:---|
| **类型** | msgType | KAFKA/ROCKETMQ/RABBITMQ/REDIS/ASYNC/EVENT |
| **操作** | operationType | PRODUCE/CONSUME |
| **MQ 字段** | topic, partition, offset, key, consumerGroupId, payloadSize | MQ 元数据 |
| **消息体** | payload | 消息体内容（根据配置记录） |
| **控制** | forceRecordPayload, sampled | 记录控制 |
| **结果** | processResult, error, errorStack | 处理结果 |
| **时间** | duration, producerTime, consumerTime | 时间相关 |
| **重试** | retryCount | 重试次数 |
| **扩展** | extensions | 扩展字段 |

### 采样策略

| 场景 | 采样率 | 说明 |
|:---|:---|:---|
| **高频消息**（>1000 msg/s） | 1% | 只记录关键消息 |
| **中频消息**（100-1000 msg/s） | 10% | 默认采样率 |
| **低频消息**（<100 msg/s） | 100% | 全部记录 |
| **慢消息**（>1000ms） | 100% | 全部记录，不采样 |
| **失败消息** | 100% | 全部记录，不采样 |

### 故障排查

**日志没有输出**：检查自动配置是否启用、采样率是否过低、拦截器是否正确注册。临时设置 `sampling-rate: 1.0` 排查。

**性能下降**：降低采样率 `sampling-rate: 0.01`，关闭消息体记录 `record-payload: false`。

---

## 配置说明

### application.yml 配置

```yaml
# ========== Tracing 配置 ==========
management:
  tracing:
    sampling:
      probability: 0.1  # 10% 采样率（生产环境）
    enabled: true

# ========== MQ 日志配置 ==========
mq:
  logging:
    record-payload: true           # 是否记录消息体（默认 true）
    max-payload-length: 1000       # 消息体最大长度（字符数，默认 1000）
    record-full-stack: false       # 是否记录完整错误堆栈（默认 false）
    max-stack-depth: 50            # 堆栈最大深度（行数，默认 50）
    sampling-rate: 0.1             # 默认采样率 10%（默认 0.1）
    slow-message-threshold: 1000   # 慢消息阈值（毫秒，默认 1000）

# ========== SQL 日志配置 ==========
sql:
  logging:
    enabled: true                  # 是否启用 SQL 日志（默认 true）
    slow-query-threshold: 1000     # 慢查询阈值（毫秒，默认 1000）
    max-sql-length: 2000           # SQL 最大长度（字符数，默认 2000）
    sampling-rate: 0.1             # 采样率（默认 0.1）
    record-params: true            # 是否记录参数（默认 true）
    record-full-stack: false       # 是否记录完整错误堆栈（默认 false）
    max-stack-depth: 50            # 堆栈最大深度（行数，默认 50）

# ========== 请求日志配置 ==========
req:
  logging:
    record-response: true          # 是否记录响应体（默认 false）
    record-error-response: true    # 错误响应是否完整记录（默认 true）
    record-success-response: false # 成功响应是否完整记录（默认 false）
    sampling-rate: 0.01            # 成功响应采样率（默认 0.01，即 1%）
```

### 环境差异配置

| 配置 | 开发环境 | 生产环境 |
|:---|:---|:---|
| **日志文件** | `./logs/eify-dev.log` | `./logs/eify.log` |
| **日志级别** | DEBUG | INFO |
| **Tracing 采样率** | 1.0 (100%) | 0.1 (10%) |
| **文件写入** | 同步 + 异步 | 纯异步 |
| **控制台输出** | 启用 | 禁用 |
| **MQ 消息体长度** | 5000 | 500 |
| **MQ 采样率** | 1.0 (100%) | 0.1 (10%) |
| **堆栈记录** | 完整 | 仅第一行 |

### Logback 配置

详见 `logback-spring.xml`。开发环境启用 CONSOLE + DEV_FILE，生产环境使用 FILE_JSON_ASYNC + ERROR_FILE_ASYNC。生产环境 Spring/MyBatis 日志级别设为 WARN。

---

## 使用指南

### 日志查看

```bash
# 实时查看（纯 JSON 格式）
tail -f ./logs/eify.log | jq

# 按类型过滤
grep '"logType":"req"' ./logs/eify.log | jq
grep '"logType":"sql"' ./logs/eify.log | jq

# 查看慢请求
grep '"logType":"req"' ./logs/eify.log | jq 'select(.duration > 1000)'

# 按 traceId 查询
grep 'YOUR_TRACE_ID' ./logs/eify.log | jq
```

### StructuredLogger 使用

```java
// 记录请求日志
StructuredLogger.logReq(() -> ReqLogMessage.builder()
    .method("POST").path("/api/v1/chat").status(200)
    .clientIp("192.168.1.100").duration(150L).build());

// 记录 SQL 日志
StructuredLogger.logSql(() -> SqlLogMessage.builder()
    .sql("SELECT * FROM ai_agent WHERE id = 1")
    .executionTime(71L).isSlowQuery(false).rowCount(1).build());

// 记录消息日志
StructuredLogger.logMsg(() -> MsgLogMessage.builder()
    .msgType(MsgLogMessage.MsgType.KAFKA)
    .operationType(MsgLogMessage.OperationType.CONSUME)
    .topic("chat.events")
    .processResult(MsgLogMessage.ProcessResult.SUCCESS).build());

// 记录业务日志
StructuredLogger.logSimple("用户登录成功", "INFO",
    Map.of("userId", "123", "action", "login"));
```

---

## ClickHouse 存储

### 表结构

使用 `Nullable` 类型减少存储浪费。标准字段（timestamp, level, logType, traceId, duration 等）使用 `LowCardinality(String)` 或基础类型；REQ/SQL/MSG 专用字段使用 `Nullable` 类型（NULL 不占用存储）。完整 DDL 见 [DATABASE.md](DATABASE.md)。

**类型约束**：ClickHouse 不支持 `Nullable(LowCardinality(String))`，使用 `Nullable(String)` 代替。

### 查询示例

```sql
-- 按 traceId 查询完整调用链
SELECT * FROM app_logs WHERE traceId = 'xxx' ORDER BY timestamp;

-- 查询慢请求（使用物化视图，更快）
SELECT * FROM req_logs_mv WHERE duration > 1000 ORDER BY timestamp DESC;

-- 查询慢 SQL
SELECT * FROM sql_logs_mv WHERE isSlowQuery = 1 ORDER BY timestamp DESC;

-- 统计错误日志
SELECT level, count() FROM app_logs WHERE level = 'ERROR' GROUP BY level;
```

---

## 日志监控方案

### 方案对比

| 维度 | ClickVisual | Prometheus + Grafana |
|:---|:---|:---|
| **数据类型** | 日志（字符串） | 指标（数值） |
| **存储引擎** | ClickHouse (OLAP) | TSDB (时序数据库) |
| **查询语言** | SQL | PromQL |
| **适用场景** | 日志查询、分析、告警 | 指标监控、趋势分析 |
| **保留时间** | 天级（30 天） | 月级（15 个月） |
| **实时性** | 秒级 | 分钟级 |
| **资源消耗** | 高（全量日志） | 低（预聚合指标） |

### 当前阶段推荐：仅 ClickVisual

适用于团队 < 50 人、日志量 < 100 GB/天、单机部署场景。

**理由**：指标可从日志提取（SQL 聚合查询），ClickVisual 支持告警，部署简单（一个 docker-compose 命令）。

**监控指标提取示例**：

```sql
-- QPS
SELECT toStartOfMinute(timestamp) as minute, count() as qps
FROM app_logs
WHERE logType = 'req' AND timestamp >= now() - INTERVAL 1 HOUR
GROUP BY minute ORDER BY minute;

-- P95 延迟
SELECT quantile(0.95)(duration) as p95_latency
FROM app_logs
WHERE logType = 'req' AND timestamp >= now() - INTERVAL 1 HOUR;

-- 错误率
SELECT countIf(level = 'ERROR') / count() * 100 as error_rate
FROM app_logs
WHERE timestamp >= now() - INTERVAL 5 MINUTE;
```

### 扩容阶段：ClickVisual + Prometheus + Grafana

**触发条件**：团队 50-200 人、QPS > 10、需要长期趋势分析（> 30 天）、Kubernetes 部署。

**架构**：
- ClickVisual 负责日志查询、分析（保留 30 天）
- Prometheus + Grafana 负责指标监控、告警（保留 15 个月）
- 可选添加 Jaeger（链路追踪）

### 告警配置

ClickVisual 支持基于 SQL 查询结果的告警规则（如 5 分钟内错误率 > 5%），可配置钉钉/企业微信通知。Prometheus 告警通过 AlertManager 支持更复杂的多条件、多维度告警路由。

---

## 性能分析与瓶颈

### 当前架构性能评估

| 层级 | 组件 | 当前 QPS | 理论上限 | 使用率 |
|:---|:---|:---|:---|:---|
| **应用层** | AsyncAppender | 5-50 条/秒 | 50000 条/秒 | 0.1% |
| **磁盘写入** | 文件 I/O | 0.05-0.5 MB/s | 100 MB/s | 0.5% |
| **采集层** | Vector | 5-50 条/秒 | 50000 条/秒 | 0.1% |
| **存储层** | ClickHouse | 5-50 行/秒 | 50000 行/秒 | 0.1% |

**结论**：当前配置有 **100-1000x 性能余量**，即使 500 人在线也完全无压力。

### 关键瓶颈及解决方案

| 组件 | 瓶颈 | 当前状态 | 解决方案 |
|:---|:---|:---|:---|
| **应用层** | 同步写入阻塞 | 已优化（AsyncAppender） | - |
| **JSON 序列化** | 每条日志 ~11 μs | 无需优化 | QPS > 10000 时考虑对象池 |
| **ClickHouse 查询** | 大范围查询慢 | 已优化 | 物化视图 + 跳数索引 |

### 容量规划

| 规模 | 在线人数 | QPS | 配置建议 |
|:---|:---|:---|:---|
| **小规模** | 20-50 人 | 1-5 | 单机部署，当前配置 |
| **中规模** | 100-500 人 | 10-50 | 单机 + 日志采样（10%） |
| **大规模** | 500-2000 人 | 50-200 | ClickHouse 主从 + 异步日志 |
| **超大规模** | 2000+ 人 | 200+ | ClickHouse 集群 + Kafka + 日志采样 |

### 性能优化优先级

| 优先级 | 优化项 | 触发条件 | 效果 |
|:---|:---|:---|:---|
| **P0** | 异步日志 | - | 已完成 |
| **P1** | 日志采样 | QPS > 5000 | 支撑 10x 流量 |
| **P2** | ClickHouse 分区优化 | 查询慢 | 提升 5-10x 查询 |
| **P3** | Vector 实例扩展 | QPS > 10000 | 线性扩展 |
| **P4** | ClickHouse 集群 | QPS > 50000 | 支撑大规模 |

### 性能监控指标

| 指标 | 告警阈值 | 监控方式 |
|:---|:---|:---|
| **日志 QPS** | > 5000 条/秒 | ClickHouse SQL |
| **ClickHouse 查询延迟** | P95 > 5s | ClickVisual |
| **异步队列积压** | > 5000 条 | JMX / Actuator |
| **业务 QPS** | - | `SELECT count() GROUP BY minute WHERE logType='req'` |
| **业务延迟** | P95 > 3s | `SELECT quantile(0.95)(duration) WHERE logType='req'` |

### 不监控日志格式化性能

**决策**：不引入专门的日志格式化性能监控组件。

**原因**：日志格式化是元操作（~10-20 μs），监控开销可能超过实际开销。所有性能指标可通过 ClickHouse SQL 查询获取，数据更准确且无额外开销。

---

## 常见问题

### Q: 日志格式是什么？

**A**: 纯 JSON 格式，使用 **UTC 时区**，标准字段在顶层，业务数据在 `message` 对象中。

### Q: 如何按 traceId 查询？

**A**:
```bash
grep 'YOUR_TRACE_ID' ./logs/eify.log | jq
# 或 ClickHouse
SELECT * FROM app_logs WHERE traceId = 'xxx' ORDER BY timestamp;
```

### Q: ClickHouse 类型错误：Nullable(LowCardinality(String))？

**A**: ClickHouse 不支持此类型组合，改用 `Nullable(String)`。

### Q: traceId 显示为 "-"？

**A**: 确认 Micrometer Tracing 已启用（`management.tracing.enabled=true`），且在 Web 请求上下文中。

### Q: 日志级别配置不生效？

**A**: 检查 `application-{profile}.yml` 中的日志级别配置，同时检查 `logback-spring.xml`。

### Q: 如何调整采样率？

**A**: 修改 `application.yml` 中的 `management.tracing.sampling.probability` 值（0.0-1.0）。

### Q: 为什么系统错误日志级别很重要？

**A**:
- **日志过滤**：生产环境通常设置为 WARN 级别，ERROR 日志必须正确输出
- **告警系统**：监控系统依赖 ERROR 级别触发告警
- **日志分析**：ELK/Splunk 等工具根据日志级别着色和统计

---

## 参见

- [DATABASE.md](DATABASE.md) - 数据库设计（含 ClickHouse 日志数据库）
- [deploy/infra/deploy/README.md](../deploy/infra/deploy/README.md) - 日志系统部署指南

---

**最后更新**: 2026-05-20（文档全面审计与更新）
