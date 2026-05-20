package com.eify.provider.adapter;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmApiException;
import com.eify.common.http.LlmHttpClient;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ChatMessage;
import com.eify.provider.domain.dto.ChatRequest;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.eify.provider.domain.dto.ConnectionTestResult;
import com.eify.provider.domain.entity.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AbstractProviderAdapter")
class AbstractProviderAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 用于测试的适配器子类，只实现必要的抽象方法
     */
    static class TestAdapter extends AbstractProviderAdapter {
        private final String testResult;
        private final boolean throwOnTest;

        TestAdapter(LlmHttpClient llmHttpClient, String testResult, boolean throwOnTest) {
            super(llmHttpClient);
            this.testResult = testResult;
            this.throwOnTest = throwOnTest;
        }

        @Override
        public ProviderType getSupportedType() {
            return ProviderType.OPENAI;
        }

        @Override
        protected String doTest(Provider provider) throws Exception {
            if (throwOnTest) {
                throw new LlmApiException(
                        LlmApiException.ErrorType.NETWORK_ERROR, "openai", 500, "Connection refused");
            }
            return testResult;
        }

        @Override
        protected ChatResponse doChat(Provider provider, ChatRequest request) {
            return ChatResponse.empty();
        }

        @Override
        protected Flux<ChatStreamChunk> doStreamChat(Provider provider, ChatRequest request) {
            return Flux.empty();
        }
    }

    private LlmHttpClient mockHttpClient;

    @BeforeEach
    void setUp() {
        mockHttpClient = new LlmHttpClient() {
            @Override
            public String get(String url, java.util.Map<String, String> headers, int timeoutSeconds) {
                return "{\"data\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}";
            }
        };
    }

    private Provider buildProvider() {
        Provider p = new Provider();
        p.setId(1L);
        p.setName("Test");
        p.setType(ProviderType.OPENAI);
        p.setBaseUrl("https://api.openai.com/");
        ObjectNode auth = JsonNodeFactory.instance.objectNode();
        auth.put("api_key", "sk-test-key");
        p.setAuthConfig(auth);
        p.setEnabled(1);
        return p;
    }

    // ==================== normalizeBaseUrl ====================

    @Nested
    @DisplayName("normalizeBaseUrl")
    class NormalizeBaseUrl {

        @Test
        @DisplayName("移除末尾斜杠")
        void shouldRemoveTrailingSlash() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            assertThat(adapter.normalizeBaseUrl("https://api.openai.com/")).isEqualTo("https://api.openai.com");
            assertThat(adapter.normalizeBaseUrl("https://api.openai.com/v1/")).isEqualTo("https://api.openai.com/v1");
        }

        @Test
        @DisplayName("无末尾斜杠时原样返回")
        void shouldReturnUnchangedWithoutTrailingSlash() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            assertThat(adapter.normalizeBaseUrl("https://api.openai.com")).isEqualTo("https://api.openai.com");
        }

        @Test
        @DisplayName("只有斜杠时返回空")
        void shouldReturnEmptyForSingleSlash() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            assertThat(adapter.normalizeBaseUrl("/")).isEqualTo("");
        }
    }

    // ==================== buildModelsUrl ====================

    @Nested
    @DisplayName("buildModelsUrl")
    class BuildModelsUrl {

        @Test
        @DisplayName("baseUrl 不含 /v1 时追加")
        void shouldAppendV1AndModels() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            assertThat(adapter.buildModelsUrl("https://api.openai.com"))
                    .isEqualTo("https://api.openai.com/v1/models");
        }

        @Test
        @DisplayName("baseUrl 已含 /v1 时不重复追加")
        void shouldNotDoubleAppendV1() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            assertThat(adapter.buildModelsUrl("https://api.openai.com/v1"))
                    .isEqualTo("https://api.openai.com/v1/models");
        }

        @Test
        @DisplayName("baseUrl 末尾带斜杠时正确拼接")
        void shouldHandleTrailingSlash() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            assertThat(adapter.buildModelsUrl("https://api.openai.com/"))
                    .isEqualTo("https://api.openai.com/v1/models");
        }
    }

    // ==================== getApiKey ====================

    @Nested
    @DisplayName("getApiKey")
    class GetApiKey {

        @Test
        @DisplayName("从 authConfig.api_key 提取")
        void shouldExtractFromApiKeyField() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            String key = adapter.getApiKey(buildProvider());

            assertThat(key).isEqualTo("sk-test-key");
        }

        @Test
        @DisplayName("从 authConfig.apiKey 提取（JSON camelCase）")
        void shouldExtractFromCamelCaseField() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            Provider p = new Provider();
            p.setId(1L);
            p.setName("Test");
            p.setType(ProviderType.OPENAI);
            p.setBaseUrl("https://api.openai.com");
            ObjectNode auth = JsonNodeFactory.instance.objectNode();
            auth.put("apiKey", "sk-camel-key");
            p.setAuthConfig(auth);

            String key = adapter.getApiKey(p);

            assertThat(key).isEqualTo("sk-camel-key");
        }

        @Test
        @DisplayName("authConfig 为 null 时抛出 BusinessException")
        void shouldThrowWhenAuthConfigIsNull() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            Provider p = new Provider();
            p.setAuthConfig(null);

            assertThatThrownBy(() -> adapter.getApiKey(p))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("认证配置缺失");
        }

        @Test
        @DisplayName("api_key 为空字符串时抛出异常")
        void shouldThrowWhenApiKeyEmpty() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            Provider p = new Provider();
            ObjectNode auth = JsonNodeFactory.instance.objectNode();
            auth.put("api_key", "");
            p.setAuthConfig(auth);

            assertThatThrownBy(() -> adapter.getApiKey(p))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("API Key 未配置");
        }

        @Test
        @DisplayName("无 api_key 字段时抛出异常")
        void shouldThrowWhenNoApiKeyField() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            Provider p = new Provider();
            ObjectNode auth = JsonNodeFactory.instance.objectNode();
            auth.put("other_field", "value");
            p.setAuthConfig(auth);

            assertThatThrownBy(() -> adapter.getApiKey(p))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("API Key 未配置");
        }

        @Test
        @DisplayName("apiKey 优先级：api_key 优先于 apiKey")
        void shouldPreferApiKeyOverCamelCase() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            Provider p = new Provider();
            ObjectNode auth = JsonNodeFactory.instance.objectNode();
            auth.put("api_key", "snake-key");
            auth.put("apiKey", "camel-key");
            p.setAuthConfig(auth);

            String key = adapter.getApiKey(p);

            assertThat(key).isEqualTo("snake-key");
        }
    }

    // ==================== validateRequest ====================

    @Nested
    @DisplayName("validateRequest")
    class ValidateRequest {

        @Test
        @DisplayName("正常请求通过校验")
        void shouldPassValidRequest() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(List.of(ChatMessage.builder().role("user").content("hello").build()))
                    .temperature(0.7)
                    .maxTokens(1000)
                    .build();

            // 不抛异常即为通过
            adapter.validateRequest(request);
        }

        @Test
        @DisplayName("model 为 null 时抛出异常")
        void shouldThrowWhenModelIsNull() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model(null)
                    .messages(List.of(ChatMessage.builder().role("user").content("hello").build()))
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("model 不能为空");
        }

        @Test
        @DisplayName("model 为空字符串时抛出异常")
        void shouldThrowWhenModelIsBlank() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model("  ")
                    .messages(List.of(ChatMessage.builder().role("user").content("hello").build()))
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("model 不能为空");
        }

        @Test
        @DisplayName("messages 为 null 时抛出异常")
        void shouldThrowWhenMessagesIsNull() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(null)
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("messages 不能为空");
        }

        @Test
        @DisplayName("messages 为空列表时抛出异常")
        void shouldThrowWhenMessagesIsEmpty() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(Collections.emptyList())
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("messages 不能为空");
        }

        @Test
        @DisplayName("temperature 超出 [0, 2] 范围时抛出异常")
        void shouldThrowWhenTemperatureOutOfRange() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest reqHigh = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                    .temperature(2.5)
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(reqHigh))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("temperature");

            ChatRequest reqNeg = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                    .temperature(-0.1)
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(reqNeg))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("temperature");
        }

        @Test
        @DisplayName("topP 超出 [0, 1] 范围时抛出异常")
        void shouldThrowWhenTopPOutOfRange() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                    .topP(1.5)
                    .build();

            assertThatThrownBy(() -> adapter.validateRequest(request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("topP");
        }

        @Test
        @DisplayName("temperature 为 null 时不校验范围")
        void shouldPassWhenTemperatureIsNull() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            ChatRequest request = ChatRequest.builder()
                    .model("gpt-4")
                    .messages(List.of(ChatMessage.builder().role("user").content("hi").build()))
                    .temperature(null)
                    .build();

            adapter.validateRequest(request); // 不抛异常
        }
    }

    // ==================== testConnection ====================

    @Nested
    @DisplayName("testConnection")
    class TestConnection {

        @Test
        @DisplayName("成功时返回 success=true")
        void shouldReturnSuccessWhenDoTestSucceeds() {
            TestAdapter adapter = new TestAdapter(mockHttpClient,
                    "{\"data\":[{\"id\":\"m1\"},{\"id\":\"m2\"}]}", false);

            ConnectionTestResult result = adapter.testConnection(buildProvider());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.getModelCount()).isEqualTo(2);
            assertThat(result.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("失败时返回 success=false")
        void shouldReturnFailureWhenDoTestThrows() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", true);

            ConnectionTestResult result = adapter.testConnection(buildProvider());

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getLatencyMs()).isGreaterThanOrEqualTo(0);
            assertThat(result.getErrorMessage()).isNotNull();
        }
    }

    // ==================== extractModelCount ====================

    @Nested
    @DisplayName("extractModelCount")
    class ExtractModelCount {

        @Test
        @DisplayName("从 data 数组提取模型数量")
        void shouldExtractModelCountFromDataArray() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            String response = "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4\"},{\"id\":\"gpt-3.5\"},{\"id\":\"dall-e\"}]}";

            int count = adapter.extractModelCount(response);

            assertThat(count).isEqualTo(3);
        }

        @Test
        @DisplayName("空 data 数组返回 0")
        void shouldReturnZeroForEmptyData() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            int count = adapter.extractModelCount("{\"data\":[]}");

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("无 data 字段返回 0")
        void shouldReturnZeroForNoDataField() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            int count = adapter.extractModelCount("{\"error\":\"not found\"}");

            assertThat(count).isEqualTo(0);
        }

        @Test
        @DisplayName("非 JSON 字符串返回 0")
        void shouldReturnZeroForInvalidJson() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);

            int count = adapter.extractModelCount("not json");

            assertThat(count).isEqualTo(0);
        }
    }

    // ==================== buildErrorMessage ====================

    @Nested
    @DisplayName("buildErrorMessage")
    class BuildErrorMessage {

        @Test
        @DisplayName("从 LlmApiException 构建错误消息")
        void shouldBuildFromLlmApiException() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            LlmApiException llmEx = new LlmApiException(
                    LlmApiException.ErrorType.AUTH_FAILED, "openai", 401, "Invalid API key");

            String msg = adapter.buildErrorMessage(llmEx, buildProvider());

            assertThat(msg).contains("认证失败", "openai", "401", "Invalid API key");
        }

        @Test
        @DisplayName("从 BusinessException 构建错误消息")
        void shouldBuildFromBusinessException() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            BusinessException bizEx = new BusinessException(
                    com.eify.common.error.ErrorCode.PARAM_ERROR, "参数校验失败");

            String msg = adapter.buildErrorMessage(bizEx, buildProvider());

            assertThat(msg).isEqualTo("参数校验失败");
        }

        @Test
        @DisplayName("从普通 Exception 构建错误消息")
        void shouldBuildFromGenericException() {
            TestAdapter adapter = new TestAdapter(mockHttpClient, "{}", false);
            Exception ex = new RuntimeException("unknown error");

            String msg = adapter.buildErrorMessage(ex, buildProvider());

            assertThat(msg).contains("RuntimeException", "unknown error");
        }
    }
}
