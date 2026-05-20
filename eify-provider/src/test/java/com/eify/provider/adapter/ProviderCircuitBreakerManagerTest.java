package com.eify.provider.adapter;

import com.eify.common.http.LlmApiException;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.domain.entity.ProviderHealth;
import com.eify.provider.mapper.ProviderHealthMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("ProviderCircuitBreakerManager")
class ProviderCircuitBreakerManagerTest {

    private final List<ProviderHealth> healthUpdates = new ArrayList<>();
    private ProviderHealthMapper healthMapper;
    private ProviderCircuitBreakerManager manager;
    private Provider provider;

    @BeforeEach
    void setUp() {
        healthUpdates.clear();
        healthMapper = mock(ProviderHealthMapper.class);
        when(healthMapper.upsertHealth(any(ProviderHealth.class))).thenAnswer(inv -> {
            healthUpdates.add(inv.getArgument(0));
            return 1;
        });
        manager = new ProviderCircuitBreakerManager(healthMapper);
        provider = new Provider();
        provider.setId(1L);
        provider.setName("test-provider");
        provider.setType(ProviderType.OPENAI);
    }

    @Nested
    @DisplayName("executeWithBreaker (sync)")
    class SyncExecution {

        @Test
        @DisplayName("P0 - 正常执行返回结果")
        void shouldReturnResultOnSuccess() {
            String result = manager.executeWithBreaker(provider, () -> "ok");

            assertThat(result).isEqualTo("ok");
            assertThat(healthUpdates).isEmpty(); // 不触发状态变更
        }

        @Test
        @DisplayName("P0 - 调用失败时传播 LlmApiException")
        void shouldPropagateLlmApiException() {
            Callable<String> failing = () -> {
                throw new LlmApiException(LlmApiException.ErrorType.TIMEOUT, "openai", 408, "timeout");
            };

            assertThatThrownBy(() -> manager.executeWithBreaker(provider, failing))
                    .isInstanceOf(LlmApiException.class)
                    .extracting(e -> ((LlmApiException) e).getErrorType())
                    .isEqualTo(LlmApiException.ErrorType.TIMEOUT);
        }

        @Test
        @DisplayName("P0 - 达到阈值后熔断器打开，抛出 CIRCUIT_OPEN")
        void shouldOpenAndThrowCircuitOpen() {
            // 触发 6 次失败（超过 50% 阈值）
            for (int i = 0; i < 6; i++) {
                try {
                    manager.executeWithBreaker(provider, () -> {
                        throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR,
                                "openai", 500, "error");
                    });
                } catch (LlmApiException e) {
                    // expected
                }
            }

            // 熔断器应已打开
            assertThatThrownBy(() -> manager.executeWithBreaker(provider, () -> "should not reach"))
                    .isInstanceOf(LlmApiException.class)
                    .extracting(e -> ((LlmApiException) e).getErrorType())
                    .isEqualTo(LlmApiException.ErrorType.CIRCUIT_OPEN);
        }
    }

    @Nested
    @DisplayName("executeWithBreakerReactive (Flux)")
    class ReactiveExecution {

        @Test
        @DisplayName("P0 - 正常 Flux 返回数据并完成")
        void shouldReturnFluxOnSuccess() {
            Flux<String> flux = manager.executeWithBreakerReactive(provider, () ->
                    Flux.just("a", "b", "c"));

            List<String> items = flux.collectList().block();
            assertThat(items).containsExactly("a", "b", "c");
        }

        @Test
        @DisplayName("P0 - Flux 错误时传播异常")
        void shouldPropagateFluxError() {
            Flux<String> flux = manager.executeWithBreakerReactive(provider, () ->
                    Flux.error(new LlmApiException(LlmApiException.ErrorType.TIMEOUT,
                            "openai", 408, "timeout")));

            assertThatThrownBy(() -> flux.collectList().block())
                    .isInstanceOf(LlmApiException.class);
        }

        @Test
        @DisplayName("P0 - 熔断器打开时返回 Flux.error(CIRCUIT_OPEN)")
        void shouldReturnErrorFluxWhenOpen() {
            // 触发 6 次失败
            for (int i = 0; i < 6; i++) {
                manager.executeWithBreakerReactive(provider, () ->
                                Flux.error(new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR,
                                        "openai", 500, "error")))
                        .onErrorResume(e -> Mono.empty())
                        .collectList()
                        .block();
            }

            Flux<String> flux = manager.executeWithBreakerReactive(provider, () -> Flux.just("x"));
            assertThatThrownBy(() -> flux.collectList().block())
                    .isInstanceOf(LlmApiException.class)
                    .extracting(e -> ((LlmApiException) e).getErrorType())
                    .isEqualTo(LlmApiException.ErrorType.CIRCUIT_OPEN);
        }
    }

    @Nested
    @DisplayName("executeTestConnection")
    class TestConnection {

        @Test
        @DisplayName("P0 - 熔断器打开时仍执行探测（穿透）")
        void shouldStillProbeWhenCircuitOpen() {
            // 触发 6 次失败打开熔断器
            for (int i = 0; i < 6; i++) {
                try {
                    manager.executeTestConnection(provider, () -> {
                        throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR,
                                "openai", 500, "error");
                    });
                } catch (Exception e) {
                    // expected
                }
            }

            // 此时熔断器应打开，但 testConnection 仍应执行
            AtomicInteger callCount = new AtomicInteger(0);
            String result = manager.executeTestConnection(provider, () -> {
                callCount.incrementAndGet();
                return "probe-ok";
            });

            assertThat(result).isEqualTo("probe-ok");
            assertThat(callCount.get()).isEqualTo(1); // 确实执行了
        }

        @Test
        @DisplayName("P1 - 探测成功时返回结果")
        void shouldReturnResultOnSuccess() {
            String result = manager.executeTestConnection(provider, () -> "connected");
            assertThat(result).isEqualTo("connected");
        }
    }

    @Nested
    @DisplayName("Health update on state transition")
    class HealthUpdate {

        @Test
        @DisplayName("P0 - 熔断器打开时更新 ProviderHealth 为 DEGRADED")
        void shouldUpdateHealthToDegradedOnOpen() {
            for (int i = 0; i < 6; i++) {
                try {
                    manager.executeWithBreaker(provider, () -> {
                        throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR,
                                "openai", 500, "error");
                    });
                } catch (LlmApiException e) {
                    // expected
                }
            }

            assertThat(healthUpdates).isNotEmpty();
            ProviderHealth lastUpdate = healthUpdates.get(healthUpdates.size() - 1);
            assertThat(lastUpdate.getStatus()).isEqualTo("DEGRADED");
            assertThat(lastUpdate.getProviderId()).isEqualTo(1L);
            assertThat(lastUpdate.getErrorMessage()).contains("Circuit breaker OPEN");
        }
    }

    @Nested
    @DisplayName("Provider isolation")
    class ProviderIsolation {

        @Test
        @DisplayName("P0 - 不同 Provider 的熔断器独立")
        void shouldIsolatePerProvider() {
            Provider providerA = new Provider();
            providerA.setId(1L);
            providerA.setName("A");
            providerA.setType(ProviderType.OPENAI);

            Provider providerB = new Provider();
            providerB.setId(2L);
            providerB.setName("B");
            providerB.setType(ProviderType.OPENAI);

            // 只让 A 失败
            for (int i = 0; i < 6; i++) {
                try {
                    manager.executeWithBreaker(providerA, () -> {
                        throw new LlmApiException(LlmApiException.ErrorType.NETWORK_ERROR,
                                "openai", 500, "error");
                    });
                } catch (LlmApiException e) {
                    // expected
                }
            }

            // A 应已熔断
            assertThatThrownBy(() -> manager.executeWithBreaker(providerA, () -> "ok"))
                    .isInstanceOf(LlmApiException.class)
                    .extracting(e -> ((LlmApiException) e).getErrorType())
                    .isEqualTo(LlmApiException.ErrorType.CIRCUIT_OPEN);

            // B 仍应正常
            String resultB = manager.executeWithBreaker(providerB, () -> "ok from B");
            assertThat(resultB).isEqualTo("ok from B");
        }
    }
}
