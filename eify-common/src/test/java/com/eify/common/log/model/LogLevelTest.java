package com.eify.common.log.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("LogLevel")
class LogLevelTest {

    @Nested
    @DisplayName("fromName")
    class FromName {

        @ParameterizedTest
        @CsvSource({
                "TRACE, TRACE",
                "DEBUG, DEBUG",
                "INFO, INFO",
                "WARN, WARN",
                "ERROR, ERROR",
                "trace, TRACE",
                "Info, INFO",
        })
        @DisplayName("根据名称查找到对应等级（大小写不敏感）")
        void shouldFindByName(String name, LogLevel expected) {
            assertThat(LogLevel.fromName(name)).isEqualTo(expected);
        }

        @Test
        @DisplayName("未知名称返回 INFO")
        void shouldReturnInfoForUnknownName() {
            assertThat(LogLevel.fromName("UNKNOWN")).isEqualTo(LogLevel.INFO);
            assertThat(LogLevel.fromName("")).isEqualTo(LogLevel.INFO);
        }
    }

    @Nested
    @DisplayName("fromLogbackLevel")
    class FromLogbackLevel {

        @Test
        @DisplayName("null 返回 INFO")
        void shouldReturnInfoForNull() {
            assertThat(LogLevel.fromLogbackLevel(null)).isEqualTo(LogLevel.INFO);
        }

        @Test
        @DisplayName("ERROR level 返回 ERROR")
        void shouldConvertError() {
            assertThat(LogLevel.fromLogbackLevel(ch.qos.logback.classic.Level.ERROR))
                    .isEqualTo(LogLevel.ERROR);
        }

        @Test
        @DisplayName("WARN level 返回 WARN")
        void shouldConvertWarn() {
            assertThat(LogLevel.fromLogbackLevel(ch.qos.logback.classic.Level.WARN))
                    .isEqualTo(LogLevel.WARN);
        }

        @Test
        @DisplayName("INFO level 返回 INFO")
        void shouldConvertInfo() {
            assertThat(LogLevel.fromLogbackLevel(ch.qos.logback.classic.Level.INFO))
                    .isEqualTo(LogLevel.INFO);
        }

        @Test
        @DisplayName("DEBUG level 返回 DEBUG")
        void shouldConvertDebug() {
            assertThat(LogLevel.fromLogbackLevel(ch.qos.logback.classic.Level.DEBUG))
                    .isEqualTo(LogLevel.DEBUG);
        }

        @Test
        @DisplayName("TRACE level 返回 TRACE")
        void shouldConvertTrace() {
            assertThat(LogLevel.fromLogbackLevel(ch.qos.logback.classic.Level.TRACE))
                    .isEqualTo(LogLevel.TRACE);
        }
    }

    @Nested
    @DisplayName("toLogbackLevel")
    class ToLogbackLevel {

        @Test
        @DisplayName("TRACE → Level.TRACE")
        void shouldConvertTraceToLogback() {
            assertThat(LogLevel.TRACE.toLogbackLevel())
                    .isEqualTo(ch.qos.logback.classic.Level.TRACE);
        }

        @Test
        @DisplayName("DEBUG → Level.DEBUG")
        void shouldConvertDebugToLogback() {
            assertThat(LogLevel.DEBUG.toLogbackLevel())
                    .isEqualTo(ch.qos.logback.classic.Level.DEBUG);
        }

        @Test
        @DisplayName("INFO → Level.INFO")
        void shouldConvertInfoToLogback() {
            assertThat(LogLevel.INFO.toLogbackLevel())
                    .isEqualTo(ch.qos.logback.classic.Level.INFO);
        }

        @Test
        @DisplayName("WARN → Level.WARN")
        void shouldConvertWarnToLogback() {
            assertThat(LogLevel.WARN.toLogbackLevel())
                    .isEqualTo(ch.qos.logback.classic.Level.WARN);
        }

        @Test
        @DisplayName("ERROR → Level.ERROR")
        void shouldConvertErrorToLogback() {
            assertThat(LogLevel.ERROR.toLogbackLevel())
                    .isEqualTo(ch.qos.logback.classic.Level.ERROR);
        }
    }

    @Nested
    @DisplayName("enum values")
    class EnumValues {

        @Test
        @DisplayName("共 5 个等级")
        void shouldHaveFiveLevels() {
            assertThat(LogLevel.values()).hasSize(5);
        }

        @Test
        @DisplayName("value 递增: TRACE=0, ERROR=4")
        void shouldHaveIncreasingValues() {
            assertThat(LogLevel.TRACE.getValue()).isEqualTo(0);
            assertThat(LogLevel.DEBUG.getValue()).isEqualTo(1);
            assertThat(LogLevel.INFO.getValue()).isEqualTo(2);
            assertThat(LogLevel.WARN.getValue()).isEqualTo(3);
            assertThat(LogLevel.ERROR.getValue()).isEqualTo(4);
        }
    }
}
