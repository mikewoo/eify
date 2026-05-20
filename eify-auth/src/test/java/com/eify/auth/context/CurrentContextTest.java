package com.eify.auth.context;

import com.eify.common.context.CurrentContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CurrentContext")
class CurrentContextTest {

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    @Nested
    @DisplayName("set and get")
    class SetAndGet {

        @Test
        @DisplayName("设置后正确返回 userId 和 workspaceId")
        void shouldReturnSetValues() {
            CurrentContext.set(100L, 20L);

            assertThat(CurrentContext.getUserId()).isEqualTo(100L);
            assertThat(CurrentContext.getWorkspaceId()).isEqualTo(20L);
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("清除后 getWorkspaceId 返回 null")
        void shouldReturnNullAfterClear() {
            CurrentContext.set(100L, 20L);
            CurrentContext.clear();

            assertThat(CurrentContext.getWorkspaceId()).isNull();
        }

        @Test
        @DisplayName("清除后 getUserId 返回 null")
        void shouldReturnNullUserIdAfterClear() {
            CurrentContext.set(100L, 20L);
            CurrentContext.clear();

            assertThat(CurrentContext.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("default workspace")
    class DefaultWorkspace {

        @Test
        @DisplayName("未设置时 getWorkspaceId 返回 null")
        void shouldReturnNullWhenNotSet() {
            assertThat(CurrentContext.getWorkspaceId()).isNull();
        }

        @Test
        @DisplayName("未设置时 getUserId 返回 null")
        void shouldReturnNullUserIdWhenNotSet() {
            assertThat(CurrentContext.getUserId()).isNull();
        }
    }

    @Nested
    @DisplayName("getRequiredWorkspaceId")
    class GetRequiredWorkspaceId {

        @Test
        @DisplayName("设置后正确返回 workspaceId")
        void shouldReturnSetValue() {
            CurrentContext.set(100L, 20L);
            assertThat(CurrentContext.getRequiredWorkspaceId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("未设置时抛出 UNAUTHORIZED")
        void shouldThrowWhenNotSet() {
            assertThatThrownBy(CurrentContext::getRequiredWorkspaceId)
                    .isInstanceOf(com.eify.common.exception.BusinessException.class)
                    .extracting("code")
                    .isEqualTo(com.eify.common.error.ErrorCode.UNAUTHORIZED.getCode());
        }
    }
}
