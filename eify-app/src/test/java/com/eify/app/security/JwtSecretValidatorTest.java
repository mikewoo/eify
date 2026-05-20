package com.eify.app.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("JwtSecretValidator")
class JwtSecretValidatorTest {

    private Environment env;

    @BeforeEach
    void setUp() {
        env = mock(Environment.class);
    }

    private JwtSecretValidator createValidator(String secret, String... activeProfiles) {
        when(env.getActiveProfiles()).thenReturn(activeProfiles);
        JwtSecretValidator validator = new JwtSecretValidator(env);
        ReflectionTestUtils.setField(validator, "jwtSecret", secret);
        return validator;
    }

    @Nested
    @DisplayName("dev profile")
    class DevProfile {

        @Test
        @DisplayName("dev 环境跳过验证，即使密钥为默认值")
        void shouldSkipValidationInDevProfile() {
            JwtSecretValidator validator = createValidator(
                    "dev-eify-jwt-secret-not-for-production", "dev");

            assertThatCode(validator::validate).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("dev 环境跳过验证，即使密钥很短")
        void shouldSkipValidationInDevProfileShortKey() {
            JwtSecretValidator validator = createValidator("short", "dev");

            assertThatCode(validator::validate).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("non-dev profile")
    class NonDevProfile {

        @Test
        @DisplayName("生产环境已知默认值 dev key 抛出异常")
        void shouldRejectKnownDevDefaultInProd() {
            JwtSecretValidator validator = createValidator(
                    "dev-eify-jwt-secret-not-for-production", "prod");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已知默认值");
        }

        @Test
        @DisplayName("生产环境已知默认值 old default 抛出异常")
        void shouldRejectKnownOldDefaultInProd() {
            JwtSecretValidator validator = createValidator(
                    "eify-jwt-secret-key-2024-please-change-in-prod", "prod");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已知默认值");
        }

        @Test
        @DisplayName("生产环境空白密钥抛出异常")
        void shouldRejectBlankSecretInProd() {
            JwtSecretValidator validator = createValidator("", "prod");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("必须配置 JWT 密钥");
        }

        @Test
        @DisplayName("生产环境 null 密钥抛出异常")
        void shouldRejectNullSecretInProd() {
            JwtSecretValidator validator = createValidator(null, "prod");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("必须配置 JWT 密钥");
        }

        @Test
        @DisplayName("生产环境密钥太短抛出异常")
        void shouldRejectShortSecretInProd() {
            JwtSecretValidator validator = createValidator("12345", "prod");

            assertThatThrownBy(validator::validate)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("太短");
        }

        @Test
        @DisplayName("生产环境合法密钥通过验证")
        void shouldPassValidSecretInProd() {
            JwtSecretValidator validator = createValidator(
                    "a-very-long-and-secure-jwt-secret-key-for-production-use-only-2026", "prod");

            assertThatCode(validator::validate).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("test 环境合法密钥通过验证")
        void shouldPassValidSecretInTest() {
            JwtSecretValidator validator = createValidator(
                    "test-jwt-secret-for-unit-tests-not-a-default", "test");

            assertThatCode(validator::validate).doesNotThrowAnyException();
        }
    }
}
