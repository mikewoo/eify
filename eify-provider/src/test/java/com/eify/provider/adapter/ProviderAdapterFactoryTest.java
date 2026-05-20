package com.eify.provider.adapter;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.provider.constant.ProviderType;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProviderAdapterFactory")
class ProviderAdapterFactoryTest {

    /**
     * 测试用适配器
     */
    static class StubAdapter implements ProviderAdapter {
        private final ProviderType type;

        StubAdapter(ProviderType type) {
            this.type = type;
        }

        @Override
        public ProviderType getSupportedType() {
            return type;
        }

        @Override
        public ConnectionTestResult testConnection(Provider provider) {
            return ConnectionTestResult.success(10, 0);
        }

        @Override
        public ChatResponse chat(Provider provider, ChatRequest request) {
            return ChatResponse.empty();
        }

        @Override
        public Flux<ChatStreamChunk> streamChat(Provider provider, ChatRequest request) {
            return Flux.empty();
        }
    }

    private ProviderAdapterFactory factory;
    private StubAdapter openaiAdapter;
    private StubAdapter anthropicAdapter;

    @BeforeEach
    void setUp() {
        openaiAdapter = new StubAdapter(ProviderType.OPENAI);
        anthropicAdapter = new StubAdapter(ProviderType.ANTHROPIC);
        factory = new ProviderAdapterFactory(List.of(openaiAdapter, anthropicAdapter));
    }

    @Nested
    @DisplayName("registration")
    class Registration {

        @Test
        @DisplayName("构造时注册所有传入的适配器")
        void shouldRegisterAllAdaptersOnConstruction() {
            Set<ProviderType> types = factory.getSupportedTypes();

            assertThat(types).containsExactlyInAnyOrder(ProviderType.OPENAI, ProviderType.ANTHROPIC);
        }

        @Test
        @DisplayName("空列表创建空工厂")
        void shouldCreateEmptyFactory() {
            ProviderAdapterFactory empty = new ProviderAdapterFactory(Collections.emptyList());

            assertThat(empty.getSupportedTypes()).isEmpty();
        }

        @Test
        @DisplayName("同名适配器后注册者覆盖前者")
        void shouldOverrideDuplicate() {
            StubAdapter second = new StubAdapter(ProviderType.OPENAI);
            ProviderAdapterFactory f = new ProviderAdapterFactory(List.of(openaiAdapter, second));

            ProviderAdapter adapter = f.getAdapter(ProviderType.OPENAI);

            // 第二个同名适配器覆盖第一个
            assertThat(adapter).isSameAs(second);
        }
    }

    @Nested
    @DisplayName("getAdapter")
    class GetAdapter {

        @Test
        @DisplayName("已注册的类型返回对应适配器")
        void shouldReturnRegisteredAdapter() {
            ProviderAdapter adapter = factory.getAdapter(ProviderType.OPENAI);

            assertThat(adapter).isSameAs(openaiAdapter);
        }

        @Test
        @DisplayName("未注册的类型抛出 BusinessException")
        void shouldThrowForUnregisteredType() {
            assertThatThrownBy(() -> factory.getAdapter(ProviderType.OLLAMA))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不支持的供应商类型")
                    .hasMessageContaining("OLLAMA");
        }
    }

    @Nested
    @DisplayName("getSupportedTypes")
    class GetSupportedTypes {

        @Test
        @DisplayName("返回所有已注册类型")
        void shouldReturnAllRegisteredTypes() {
            Set<ProviderType> types = factory.getSupportedTypes();

            assertThat(types).hasSize(2);
            assertThat(types).contains(ProviderType.OPENAI, ProviderType.ANTHROPIC);
        }
    }
}
