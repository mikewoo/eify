package com.eify.common.log.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogType")
class LogTypeTest {

    @Nested
    @DisplayName("fromCode")
    class FromCode {

        @ParameterizedTest
        @CsvSource({
                "req, REQ",
                "sql, SQL",
                "msg, MSG",
                "simple, SIMPLE",
                "sys, SYS",
                "REQ, REQ",
                "Sql, SQL",
        })
        @DisplayName("根据代码查找到对应类型（大小写不敏感）")
        void shouldFindByCode(String code, LogType expected) {
            assertThat(LogType.fromCode(code)).isEqualTo(expected);
        }

        @Test
        @DisplayName("null 返回 SYS")
        void shouldReturnSysForNull() {
            assertThat(LogType.fromCode(null)).isEqualTo(LogType.SYS);
        }

        @Test
        @DisplayName("未知代码返回 SYS")
        void shouldReturnSysForUnknownCode() {
            assertThat(LogType.fromCode("unknown")).isEqualTo(LogType.SYS);
            assertThat(LogType.fromCode("")).isEqualTo(LogType.SYS);
        }
    }

    @Nested
    @DisplayName("enum values")
    class EnumValues {

        @Test
        @DisplayName("共 5 种类型")
        void shouldHaveFiveTypes() {
            assertThat(LogType.values()).hasSize(5);
        }

        @Test
        @DisplayName("每种类型都有 code 和 description")
        void shouldHaveCodeAndDescription() {
            for (LogType type : LogType.values()) {
                assertThat(type.getCode()).isNotBlank();
                assertThat(type.getDescription()).isNotBlank();
            }
        }
    }
}
