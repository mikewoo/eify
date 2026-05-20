package com.eify.common.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ErrorCode")
class ErrorCodeTest {

    @Nested
    @DisplayName("of")
    class Of {

        @ParameterizedTest
        @CsvSource({
                "200, SUCCESS",
                "1000, SYSTEM_ERROR",
                "1001, PARAM_ERROR",
                "1002, UNAUTHORIZED",
                "1003, FORBIDDEN",
                "1004, NOT_FOUND",
                "1005, TIMEOUT",
                "1006, TOO_MANY_REQUESTS",
                "1007, DUPLICATE_REQUEST",
        })
        @DisplayName("根据 code 查找通用错误枚举")
        void shouldFindByCode(int code, ErrorCode expected) {
            assertThat(ErrorCode.of(code)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "2000, PROVIDER_NOT_FOUND",
                "2001, PROVIDER_CALL_FAILED",
                "2002, PROVIDER_TIMEOUT",
                "2003, PROVIDER_RATE_LIMIT",
                "2004, PROVIDER_CIRCUIT_OPEN",
                "2005, API_KEY_INVALID",
                "2006, MODEL_NOT_SUPPORTED",
        })
        @DisplayName("根据 code 查找 Provider 错误枚举")
        void shouldFindProviderErrorByCode(int code, ErrorCode expected) {
            assertThat(ErrorCode.of(code)).isEqualTo(expected);
        }

        @ParameterizedTest
        @CsvSource({
                "3000, AGENT_NOT_FOUND",
                "3001, AGENT_DISABLED",
                "3002, AGENT_CONFIG_INVALID",
                "3003, AGENT_NAME_DUPLICATE",
        })
        @DisplayName("根据 code 查找 Agent 错误枚举")
        void shouldFindAgentErrorByCode(int code, ErrorCode expected) {
            assertThat(ErrorCode.of(code)).isEqualTo(expected);
        }

        @Test
        @DisplayName("未知 code 返回 SYSTEM_ERROR")
        void shouldReturnSystemErrorForUnknownCode() {
            assertThat(ErrorCode.of(9999)).isEqualTo(ErrorCode.SYSTEM_ERROR);
            assertThat(ErrorCode.of(-1)).isEqualTo(ErrorCode.SYSTEM_ERROR);
            assertThat(ErrorCode.of(0)).isEqualTo(ErrorCode.SYSTEM_ERROR);
        }
    }

    @Nested
    @DisplayName("codes and messages")
    class CodesAndMessages {

        @Test
        @DisplayName("SUCCESS code 为 200")
        void shouldHaveSuccessCode200() {
            assertThat(ErrorCode.SUCCESS.getCode()).isEqualTo(200);
        }

        @Test
        @DisplayName("每个模块 code 在对应区间内")
        void shouldHaveCodesInCorrectRanges() {
            for (ErrorCode ec : ErrorCode.values()) {
                if (ec == ErrorCode.SUCCESS) continue;
                int code = ec.getCode();
                assertThat(code).isBetween(1000, 8999);
            }
        }

        @Test
        @DisplayName("所有枚举都有非空 message")
        void shouldHaveNonEmptyMessage() {
            for (ErrorCode ec : ErrorCode.values()) {
                assertThat(ec.getMessage()).isNotBlank();
            }
        }
    }
}
