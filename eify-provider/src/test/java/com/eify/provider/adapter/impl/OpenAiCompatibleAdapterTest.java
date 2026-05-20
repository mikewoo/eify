package com.eify.provider.adapter.impl;

import com.eify.common.http.LlmHttpClient;
import com.eify.provider.constant.ProviderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OpenAiCompatibleAdapter")
class OpenAiCompatibleAdapterTest {

    @Test
    @DisplayName("getSupportedType 返回 OPENAI_COMPATIBLE")
    void shouldReturnOpenAiCompatibleType() {
        WebClient.Builder builder = WebClient.builder();
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                new LlmHttpClient(), builder);

        assertThat(adapter.getSupportedType()).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
    }

    @Test
    @DisplayName("OpenAiCompatibleAdapter 是 OpenAiAdapter 子类")
    void shouldExtendOpenAiAdapter() {
        WebClient.Builder builder = WebClient.builder();
        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                new LlmHttpClient(), builder);

        assertThat(adapter).isInstanceOf(OpenAiAdapter.class);
    }
}
