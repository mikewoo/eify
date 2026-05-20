package com.eify.provider.constant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ProviderType")
class ProviderTypeTest {

    @Nested
    @DisplayName("fromValue")
    class FromValue {

        @Test
        @DisplayName("根据 value 查找对应枚举")
        void shouldFindByValue() {
            assertThat(ProviderType.fromValue("OPENAI")).isEqualTo(ProviderType.OPENAI);
            assertThat(ProviderType.fromValue("ANTHROPIC")).isEqualTo(ProviderType.ANTHROPIC);
            assertThat(ProviderType.fromValue("OLLAMA")).isEqualTo(ProviderType.OLLAMA);
            assertThat(ProviderType.fromValue("OPENAI_COMPATIBLE")).isEqualTo(ProviderType.OPENAI_COMPATIBLE);
        }

        @Test
        @DisplayName("未知 value 抛出 IllegalArgumentException")
        void shouldThrowForUnknownValue() {
            assertThatThrownBy(() -> ProviderType.fromValue("UNKNOWN"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown ProviderType");
        }
    }

    @Nested
    @DisplayName("enum values")
    class EnumValues {

        @Test
        @DisplayName("共 4 种类型")
        void shouldHaveFourTypes() {
            assertThat(ProviderType.values()).hasSize(4);
        }

        @Test
        @DisplayName("每种类型都有 value 和 description")
        void shouldHaveValueAndDescription() {
            for (ProviderType type : ProviderType.values()) {
                assertThat(type.getValue()).isNotBlank();
                assertThat(type.getDescription()).isNotBlank();
            }
        }
    }
}
