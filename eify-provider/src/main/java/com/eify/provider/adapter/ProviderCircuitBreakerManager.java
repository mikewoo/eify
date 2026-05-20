package com.eify.provider.adapter;

import com.eify.common.http.LlmApiException;
import com.eify.provider.constant.HealthStatus;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.domain.entity.ProviderHealth;
import com.eify.provider.mapper.ProviderHealthMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Provider 熔断器管理器。
 * <p>
 * 为每个 Provider 实例创建独立的 {@link CircuitBreaker}，在出错率超过阈值时
 * 自动熔断（拒绝调用），保护系统不被外部 LLM API 故障拖垮。
 * <p>
 * <b>配置</b>（程序化，无 yml 依赖）：
 * <ul>
 *   <li>滑动窗口：COUNT_BASED，10 次调用</li>
 *   <li>失败率阈值：50%</li>
 *   <li>半开等待：30s</li>
 *   <li>半开允许调用数：3</li>
 *   <li>记录异常：LlmApiException、IOException、SocketTimeoutException</li>
 *   <li>忽略异常：IllegalArgumentException、BusinessException</li>
 * </ul>
 */
@Component
public class ProviderCircuitBreakerManager {

    private static final Logger log = LoggerFactory.getLogger(ProviderCircuitBreakerManager.class);

    private final ConcurrentHashMap<String, CircuitBreaker> breakers = new ConcurrentHashMap<>();
    private final ProviderHealthMapper healthMapper;
    private final CircuitBreakerConfig config;

    public ProviderCircuitBreakerManager(ProviderHealthMapper healthMapper) {
        this.healthMapper = healthMapper;
        this.config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .recordExceptions(LlmApiException.class, IOException.class, SocketTimeoutException.class)
                .ignoreExceptions(IllegalArgumentException.class)
                .build();
    }

    /**
     * 获取或创建 provider 对应的熔断器，并注册状态变更监听。
     */
    private CircuitBreaker getOrCreate(Provider provider) {
        String key = String.valueOf(provider.getId());
        return breakers.computeIfAbsent(key, k -> {
            CircuitBreaker cb = CircuitBreakerRegistry.of(config).circuitBreaker(k);
            cb.getEventPublisher().onStateTransition(event -> {
                CircuitBreaker.StateTransition transition = event.getStateTransition();
                log.warn("[CircuitBreaker] 状态变更: {} -> {} (providerId={}, name={})",
                        transition.getFromState(), transition.getToState(),
                        provider.getId(), provider.getName());

                switch (transition.getToState()) {
                    case OPEN, FORCED_OPEN -> updateHealth(provider, HealthStatus.DEGRADED,
                            "Circuit breaker OPEN");
                    case CLOSED -> updateHealth(provider, HealthStatus.UP, null);
                    // HALF_OPEN: 不更新状态，等探测结果决定
                    default -> {}
                }
            });
            log.info("[CircuitBreaker] 创建熔断器: providerId={}, name={}", provider.getId(), provider.getName());
            return cb;
        });
    }

    private void updateHealth(Provider provider, HealthStatus status, String errorMessage) {
        try {
            ProviderHealth health = new ProviderHealth();
            health.setProviderId(provider.getId());
            health.setStatus(status.getValue());
            health.setLastCheckAt(LocalDateTime.now());
            if (errorMessage != null) {
                health.setErrorMessage(errorMessage);
            }
            healthMapper.upsertHealth(health);
        } catch (Exception e) {
            log.error("[CircuitBreaker] 更新健康状态失败: providerId={}", provider.getId(), e);
        }
    }

    /**
     * 在熔断器保护下执行同步调用。
     *
     * @throws LlmApiException 熔断器打开时抛出 CIRCUIT_OPEN
     */
    public <T> T executeWithBreaker(Provider provider, Callable<T> callable) {
        CircuitBreaker breaker = getOrCreate(provider);

        if (!breaker.tryAcquirePermission()) {
            throw new LlmApiException(LlmApiException.ErrorType.CIRCUIT_OPEN,
                    provider.getType().name(), null,
                    "Circuit breaker is OPEN for provider: " + provider.getName());
        }

        long start = System.nanoTime();
        try {
            T result = callable.call();
            breaker.onResult(start, TimeUnit.NANOSECONDS, result);
            return result;
        } catch (Exception e) {
            breaker.onError(start, TimeUnit.NANOSECONDS, e);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * 在熔断器保护下执行响应式（Flux）调用。
     * <p>
     * 熔断器打开时返回 {@code Flux.error(LlmApiException.CIRCUIT_OPEN)}。
     * 正常执行时通过 {@code doOnComplete/doOnError} 向熔断器报告结果。
     */
    public <T> Flux<T> executeWithBreakerReactive(Provider provider, Supplier<Flux<T>> fluxSupplier) {
        CircuitBreaker breaker = getOrCreate(provider);

        if (!breaker.tryAcquirePermission()) {
            return Flux.error(new LlmApiException(LlmApiException.ErrorType.CIRCUIT_OPEN,
                    provider.getType().name(), null,
                    "Circuit breaker is OPEN for provider: " + provider.getName()));
        }

        long start = System.nanoTime();
        return fluxSupplier.get()
                .doOnComplete(() -> breaker.onSuccess(start, TimeUnit.NANOSECONDS))
                .doOnError(e -> breaker.onError(start, TimeUnit.NANOSECONDS, e));
    }

    /**
     * 在熔断器保护下执行连通性测试。
     * <p>
     * 与 {@link #executeWithBreaker} 的区别：熔断器打开时<b>仍然执行</b>探测（穿透），
     * 以便在外部 API 恢复后及时关闭熔断器。探测结果仍会记录到熔断器。
     */
    public <T> T executeTestConnection(Provider provider, Supplier<T> supplier) {
        CircuitBreaker breaker = getOrCreate(provider);
        breaker.tryAcquirePermission(); // 即使返回 false 也继续执行

        long start = System.nanoTime();
        try {
            T result = supplier.get();
            breaker.onResult(start, TimeUnit.NANOSECONDS, result);
            return result;
        } catch (Exception e) {
            breaker.onError(start, TimeUnit.NANOSECONDS, e);
            if (e instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e);
        }
    }
}
