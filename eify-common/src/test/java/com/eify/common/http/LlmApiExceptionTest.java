package com.eify.common.http;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LlmApiException")
class LlmApiExceptionTest {

    @Nested
    @DisplayName("constructor without provider/http")
    class ConstructorWithoutProvider {

        @Test
        @DisplayName("仅 ErrorType 和消息构造")
        void shouldConstructWithErrorTypeAndMessage() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.TIMEOUT, "请求超时");

            assertThat(ex.getErrorType()).isEqualTo(LlmApiException.ErrorType.TIMEOUT);
            assertThat(ex.getProviderCode()).isNull();
            assertThat(ex.getHttpStatus()).isNull();
            assertThat(ex.getErrorMessage()).isEqualTo("请求超时");
            assertThat(ex.getMessage()).isEqualTo("请求超时");
        }
    }

    @Nested
    @DisplayName("constructor with provider and http status")
    class ConstructorWithProvider {

        @Test
        @DisplayName("包含 providerCode 和 httpStatus")
        void shouldConstructWithFullDetails() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.AUTH_FAILED, "openai", 401, "Invalid API key");

            assertThat(ex.getErrorType()).isEqualTo(LlmApiException.ErrorType.AUTH_FAILED);
            assertThat(ex.getProviderCode()).isEqualTo("openai");
            assertThat(ex.getHttpStatus()).isEqualTo(401);
            assertThat(ex.getErrorMessage()).isEqualTo("Invalid API key");
        }
    }

    @Nested
    @DisplayName("ErrorType enum")
    class ErrorTypeEnum {

        @Test
        @DisplayName("所有 ErrorType 有非空描述")
        void shouldHaveNonEmptyDescription() {
            for (LlmApiException.ErrorType type : LlmApiException.ErrorType.values()) {
                assertThat(type.getDescription()).isNotBlank();
            }
        }

        @Test
        @DisplayName("ErrorType 数量")
        void shouldHaveExpectedErrorTypes() {
            assertThat(LlmApiException.ErrorType.values()).hasSize(6);
        }
    }

    @Nested
    @DisplayName("type check helpers")
    class TypeCheckHelpers {

        @Test
        @DisplayName("isTimeout")
        void shouldCheckTimeout() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.TIMEOUT, "timeout");
            assertThat(ex.isTimeout()).isTrue();
            assertThat(ex.isAuthFailed()).isFalse();
            assertThat(ex.isRateLimited()).isFalse();
            assertThat(ex.isCircuitOpen()).isFalse();
        }

        @Test
        @DisplayName("isAuthFailed")
        void shouldCheckAuthFailed() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.AUTH_FAILED, "auth failed");
            assertThat(ex.isAuthFailed()).isTrue();
            assertThat(ex.isTimeout()).isFalse();
        }

        @Test
        @DisplayName("isRateLimited")
        void shouldCheckRateLimited() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.RATE_LIMITED, "rate limited");
            assertThat(ex.isRateLimited()).isTrue();
            assertThat(ex.isCircuitOpen()).isFalse();
        }

        @Test
        @DisplayName("isCircuitOpen")
        void shouldCheckCircuitOpen() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.CIRCUIT_OPEN, "circuit open");
            assertThat(ex.isCircuitOpen()).isTrue();
        }
    }

    @Nested
    @DisplayName("getFullMessage")
    class GetFullMessage {

        @Test
        @DisplayName("包含完整信息")
        void shouldContainFullInfo() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.AUTH_FAILED, "openai", 401, "Invalid key");

            String msg = ex.getFullMessage();
            assertThat(msg).contains("[认证失败]");
            assertThat(msg).contains("openai");
            assertThat(msg).contains("401");
            assertThat(msg).contains("Invalid key");
        }

        @Test
        @DisplayName("缺少 providerCode 时不显示 Provider")
        void shouldNotShowProviderWhenNull() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.TIMEOUT, "请求超时");

            String msg = ex.getFullMessage();
            assertThat(msg).doesNotContain("Provider");
        }

        @Test
        @DisplayName("缺少 httpStatus 时不显示 HTTP")
        void shouldNotShowHttpWhenNull() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.TIMEOUT, "请求超时");

            String msg = ex.getFullMessage();
            assertThat(msg).doesNotContain("HTTP");
        }
    }

    @Nested
    @DisplayName("inheritance")
    class Inheritance {

        @Test
        @DisplayName("LlmApiException 是 RuntimeException 的子类")
        void shouldBeRuntimeException() {
            LlmApiException ex = new LlmApiException(
                    LlmApiException.ErrorType.UNKNOWN, "error");
            assertThat(ex).isInstanceOf(RuntimeException.class);
        }
    }
}
