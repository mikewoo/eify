# Provider Adapter 层 Resilience4j 熔断器
`ADR-0008 circuit-breaker-provider-adapter`

# Status
Accepted

# Date
2026-05-20

# Owner
Eify 开发团队（1 人）

# Deciders
Eify 开发团队

# Context

Eify 的 ProviderAdapter 层通过 `LlmHttpClient` 和 `WebClient` 直接调用外部 LLM API，缺乏自动故障隔离机制。当前状态：

| 调用路径 | 重试 | 熔断器 |
|:---|:---|:---|
| `OpenAiAdapter.doStreamChat()` | Reactor `Retry.max(3)`（仅网络错误） | 无 |
| 其他 Adapter 的 `streamChat()` | 无 | 无 |
| 所有 Adapter 的 `chat()`（同步） | 无 | 无 |
| 所有 Adapter 的 `testConnection()` | 无 | 无 |

问题：某个 LLM Provider 持续故障时，所有调用都会穿透到 `llmExecutor` 线程池，直到超时才释放线程，可能导致线程池耗尽。

### Decision drivers
- 需自动隔离故障 Provider，保护 `llmExecutor` 线程池
- 适配器是普通 Spring Bean，不使用 AOP 代理，`@CircuitBreaker` 注解无效
- 同步（Callable）和响应式（Flux）两条调用路径均需覆盖
- 健康探测（testConnection）在熔断器打开时仍需执行

# Considered Options
* **方案 A：Spring AOP `@CircuitBreaker`** — 注解驱动，依赖 Spring Cloud CircuitBreaker。适配器不是 AOP 代理目标，注解无效。
* **方案 B：自研简单失败计数器** — 计数连续失败，超过 N 次直接拒绝。缺少半开状态、滑动窗口等成熟的熔断器语义。
* **方案 C：Resilience4j 程序化 API** — 直接使用 `CircuitBreaker.of()` + `tryAcquirePermission()`。无框架耦合，同步和响应式均可处理。

# Decision

**选择方案 C：Resilience4j 程序化 API，为每个 Provider 实例创建独立的 CircuitBreaker。**

在 `AbstractProviderAdapter` 层通过 `ProviderCircuitBreakerManager` 管理熔断器生命周期。按 `providerId`（非 `ProviderType`）创建 CircuitBreaker——不同 Provider 实例可能使用不同的 endpoint 和 API key，故障域应隔离。

## Consequences

### 优势
- 外部 LLM API 故障自动隔离，保护 `llmExecutor` 线程池不被耗尽
- 每个 Provider 实例独立熔断，故障域隔离
- 注入方式为 `@Autowired` setter，不改变子类构造函数
- `circuitBreakerManager == null` 时回退到原始行为，测试兼容
- 熔断器状态变更自动同步 `ProviderHealth` 表

### 权衡
- 程序化 API 无法享受 Spring Boot Actuator 的 `/actuator/circuitbreakers` 端点自动暴露
- 配置硬编码在 Java 类中，调参需要重新编译
- `ProviderHealthMapper.upsertHealth()` 在状态变更事件回调中执行，可能有轻微延迟

# Details

## 架构

```
AbstractProviderAdapter
  ├── chat()          →  circuitBreakerManager.executeWithBreaker(provider, callable)
  ├── streamChat()    →  circuitBreakerManager.executeWithBreakerReactive(provider, fluxSupplier)
  └── testConnection() → circuitBreakerManager.executeTestConnection(provider, supplier)  // 穿透

ProviderCircuitBreakerManager
  ├── ConcurrentHashMap<String, CircuitBreaker>  // key = providerId
  ├── CircuitBreakerConfig: COUNT_BASED, 10 calls, 50%, 30s wait, 3 permits
  ├── EventPublisher.onStateTransition          → ProviderHealthMapper.upsertHealth()
  └── 三个执行入口：executeWithBreaker / executeWithBreakerReactive / executeTestConnection
```

## 配置

| 参数 | 值 |
|:---|:---|
| `slidingWindowType` | COUNT_BASED |
| `slidingWindowSize` | 10 |
| `minimumNumberOfCalls` | 5 |
| `failureRateThreshold` | 50% |
| `waitDurationInOpenState` | 30s |
| `permittedNumberOfCallsInHalfOpenState` | 3 |
| `automaticTransitionFromOpenToHalfOpenEnabled` | true |
| `recordExceptions` | LlmApiException, IOException, SocketTimeoutException |
| `ignoreExceptions` | IllegalArgumentException, BusinessException |

## 关键设计决策

1. **程序化 API vs 注解**：适配器不使用 AOP 代理，手动控制生命周期可同时覆盖同步和响应式路径。
2. **每个 Provider 实例独立 CircuitBreaker**：按 `providerId` 而非 `ProviderType` 创建，不同实例可能使用不同 endpoint，故障域应隔离。
3. **testConnection 穿透熔断器**：健康探测是恢复检测的唯一手段，必须在熔断器打开时继续工作。
4. **状态同步 ProviderHealth**：OPEN → DEGRADED，CLOSED → UP，HALF_OPEN → 不更新（等探测结果）。
5. **与现有 retry 共存**：`OpenAiAdapter.doStreamChat()` 中保留 Reactor `Retry.max(3)`。Retry 处理瞬时故障，CircuitBreaker 处理系统性故障。

## 参考
- [Resilience4j CircuitBreaker 文档](https://resilience4j.readme.io/docs/circuitbreaker)
- [ARCHITECTURE.md](../ARCHITECTURE.md) — 线程池隔离规范
- [CLAUDE.md](../../CLAUDE.md) — 系统风险清单（LLM 链路）
