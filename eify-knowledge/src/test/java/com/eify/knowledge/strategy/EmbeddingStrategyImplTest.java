package com.eify.knowledge.strategy;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmHttpClient;
import com.eify.knowledge.config.EmbeddingConfig;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmbeddingStrategyImpl")
class EmbeddingStrategyImplTest {

    @Mock
    LlmHttpClient httpClient;

    @Mock
    EmbeddingConfig config;

    @Mock
    Executor embeddingExecutor;

    ObjectMapper objectMapper = new ObjectMapper();

    EmbeddingStrategyImpl strategy;

    @BeforeEach
    void setUp() {
        strategy = new EmbeddingStrategyImpl(httpClient, config, objectMapper, embeddingExecutor);
        when(config.getModel()).thenReturn("text-embedding-3-small");
        when(config.getApiUrl()).thenReturn("https://api.openai.com/v1/embeddings");
        when(config.getApiKey()).thenReturn("sk-test-key");
        when(config.getMaxBatchSize()).thenReturn(100);
        when(config.getTimeout()).thenReturn(30000);
    }

    @Nested
    @DisplayName("embed")
    class EmbedTests {

        @Test
        @DisplayName("嵌入成功应返回向量数组")
        void shouldReturnEmbeddingOnSuccess() throws Exception {
            String responseJson = "{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}";
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn(responseJson);

            float[] result = strategy.embed("hello world");

            assertThat(result).containsExactly(0.1f, 0.2f, 0.3f);
        }

        @Test
        @DisplayName("响应无 data 时应抛出 EMBEDDING_FAILED")
        void shouldThrowWhenNoData() {
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn("{}");

            assertThatThrownBy(() -> strategy.embed("test"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.EMBEDDING_FAILED.getCode());
        }

        @Test
        @DisplayName("data 为空数组时应抛出 EMBEDDING_FAILED")
        void shouldThrowWhenEmptyData() {
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn("{\"data\":[]}");

            assertThatThrownBy(() -> strategy.embed("test"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("HTTP 调用失败时应捕获异常并抛出 BusinessException")
        void shouldWrapHttpException() {
            when(httpClient.post(anyString(), anyMap(), anyString()))
                    .thenThrow(new RuntimeException("network error"));

            assertThatThrownBy(() -> strategy.embed("test"))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.EMBEDDING_FAILED.getCode());
        }

        @Test
        @DisplayName("请求应包含 Authorization header")
        void shouldIncludeAuthHeader() throws Exception {
            String responseJson = "{\"data\":[{\"embedding\":[0.1]}]}";
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn(responseJson);

            strategy.embed("test");

            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpClient).post(anyString(), headersCaptor.capture(), anyString());
            assertThat(headersCaptor.getValue()).containsKey("Authorization");
            assertThat(headersCaptor.getValue().get("Authorization")).isEqualTo("Bearer sk-test-key");
        }
    }

    @Nested
    @DisplayName("embedBatch")
    class EmbedBatchTests {

        @Test
        @DisplayName("小批次应直接调用 embedBatchInternal")
        void shouldCallInternalDirectlyForSmallBatch() throws Exception {
            when(config.getMaxBatchSize()).thenReturn(10);
            String responseJson = "{\"data\":[{\"embedding\":[0.1]},{\"embedding\":[0.2]}]}";
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn(responseJson);

            // 使用 mock executor 让 CompletableFuture 同步执行
            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(embeddingExecutor).execute(any(Runnable.class));

            List<float[]> results = strategy.embedBatch(List.of("text1", "text2"));

            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("大批次应拆分为多批处理")
        void shouldPartitionLargeBatch() throws Exception {
            when(config.getMaxBatchSize()).thenReturn(3);
            String responseJson = "{\"data\":[{\"embedding\":[0.1]},{\"embedding\":[0.2]},{\"embedding\":[0.3]}]}";
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn(responseJson);

            doAnswer(invocation -> {
                Runnable r = invocation.getArgument(0);
                r.run();
                return null;
            }).when(embeddingExecutor).execute(any(Runnable.class));

            List<float[]> results = strategy.embedBatch(List.of("a", "b", "c", "d", "e"));

            // 每批返回 3 个 embedding，共 2 批 → 6 个
            assertThat(results).hasSize(6);
            verify(httpClient, times(2)).post(anyString(), anyMap(), anyString());
        }
    }

    @Nested
    @DisplayName("getDimension")
    class GetDimensionTests {

        @Test
        @DisplayName("应返回配置的维度值")
        void shouldReturnConfiguredDimension() {
            when(config.getDimension()).thenReturn(1536);

            assertThat(strategy.getDimension()).isEqualTo(1536);
        }
    }

    @Nested
    @DisplayName("getModelName")
    class GetModelNameTests {

        @Test
        @DisplayName("应返回配置的模型名")
        void shouldReturnConfiguredModel() {
            assertThat(strategy.getModelName()).isEqualTo("text-embedding-3-small");
        }
    }

    @Nested
    @DisplayName("isHealthy")
    class IsHealthyTests {

        @Test
        @DisplayName("健康检查成功应返回 true")
        void shouldReturnTrueWhenHealthy() throws Exception {
            String responseJson = "{\"data\":[{\"embedding\":[0.1,0.2]}]}";
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn(responseJson);

            assertThat(strategy.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("健康检查异常应返回 false")
        void shouldReturnFalseWhenException() {
            when(httpClient.post(anyString(), anyMap(), anyString()))
                    .thenThrow(new RuntimeException("unavailable"));

            assertThat(strategy.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("返回空向量应返回 false")
        void shouldReturnFalseWhenEmptyVector() {
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn("{\"data\":[]}");

            assertThat(strategy.isHealthy()).isFalse();
        }
    }

    @Nested
    @DisplayName("createHeaders")
    class CreateHeadersTests {

        @Test
        @DisplayName("apiKey 为空时不应添加 Authorization header")
        void shouldNotAddAuthWhenApiKeyEmpty() throws Exception {
            when(config.getApiKey()).thenReturn(null);
            // 重新创建以应用新的 apiKey mock
            strategy = new EmbeddingStrategyImpl(httpClient, config, objectMapper, embeddingExecutor);
            String responseJson = "{\"data\":[{\"embedding\":[0.1]}]}";
            when(httpClient.post(anyString(), anyMap(), anyString())).thenReturn(responseJson);

            strategy.embed("test");

            ArgumentCaptor<Map<String, String>> headersCaptor = ArgumentCaptor.forClass(Map.class);
            verify(httpClient).post(anyString(), headersCaptor.capture(), anyString());
            assertThat(headersCaptor.getValue()).doesNotContainKey("Authorization");
        }
    }
}
