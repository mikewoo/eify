package com.eify.common.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseCode")
class ResponseCodeTest {

    @Nested
    @DisplayName("enum values")
    class EnumValues {

        @Test
        @DisplayName("所有枚举都有非空 code 和 message")
        void shouldHaveNonEmptyCodeAndMessage() {
            for (ResponseCode rc : ResponseCode.values()) {
                assertThat(rc.getCode()).isNotNull();
                assertThat(rc.getMessage()).isNotBlank();
            }
        }

        @Test
        @DisplayName("SUCCESS code 为 200, message 为 success")
        void shouldHaveCorrectSuccessValues() {
            assertThat(ResponseCode.SUCCESS.getCode()).isEqualTo(200);
            assertThat(ResponseCode.SUCCESS.getMessage()).isEqualTo("success");
        }

        @Test
        @DisplayName("code 在合理范围内")
        void shouldHaveCodesInReasonableRange() {
            for (ResponseCode rc : ResponseCode.values()) {
                if (rc == ResponseCode.SUCCESS) continue;
                assertThat(rc.getCode()).isBetween(1000, 8999);
            }
        }
    }
}
