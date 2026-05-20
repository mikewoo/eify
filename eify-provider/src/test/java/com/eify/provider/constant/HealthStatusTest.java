package com.eify.provider.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("HealthStatus")
class HealthStatusTest {

    @Nested
    @DisplayName("fromValue")
    class FromValue {

        @Test
        @DisplayName("根据 value 查找对应枚举")
        void shouldFindByValue() {
            assertThat(HealthStatus.fromValue("UP")).isEqualTo(HealthStatus.UP);
            assertThat(HealthStatus.fromValue("DOWN")).isEqualTo(HealthStatus.DOWN);
            assertThat(HealthStatus.fromValue("DEGRADED")).isEqualTo(HealthStatus.DEGRADED);
            assertThat(HealthStatus.fromValue("UNKNOWN")).isEqualTo(HealthStatus.UNKNOWN);
        }

        @Test
        @DisplayName("未知 value 返回 UNKNOWN（不抛异常）")
        void shouldReturnUnknownForUnknownValue() {
            assertThat(HealthStatus.fromValue("INVALID")).isEqualTo(HealthStatus.UNKNOWN);
            assertThat(HealthStatus.fromValue("")).isEqualTo(HealthStatus.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("enum values")
    class EnumValues {

        @Test
        @DisplayName("共 4 种状态")
        void shouldHaveFourStatuses() {
            assertThat(HealthStatus.values()).hasSize(4);
        }

        @Test
        @DisplayName("每种状态都有 value 和 description")
        void shouldHaveValueAndDescription() {
            for (HealthStatus status : HealthStatus.values()) {
                assertThat(status.getValue()).isNotBlank();
                assertThat(status.getDescription()).isNotBlank();
            }
        }
    }
}
