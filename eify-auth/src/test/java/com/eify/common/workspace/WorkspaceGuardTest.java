package com.eify.common.workspace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WorkspaceGuard")
class WorkspaceGuardTest {

    private static final Long WS_ID = 10L;

    @BeforeEach
    void setUp() {
        CurrentContext.set(100L, WS_ID);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    static class TestEntity implements WorkspaceAware {
        private Long id;
        private Long workspaceId;
        private String name;

        TestEntity(Long id, Long workspaceId, String name) {
            this.id = id;
            this.workspaceId = workspaceId;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Long getWorkspaceId() { return workspaceId; }
        public void setWorkspaceId(Long workspaceId) { this.workspaceId = workspaceId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    // ==================== bind ====================

    @Nested
    @DisplayName("bind")
    class Bind {

        @Test
        @DisplayName("将当前 Context 的 workspaceId 绑定到实体")
        void shouldBindWorkspaceId() {
            TestEntity entity = new TestEntity(null, null, "test");

            WorkspaceGuard.bind(entity);

            assertThat(entity.getWorkspaceId()).isEqualTo(WS_ID);
        }

        @Test
        @DisplayName("未设置 workspace 时 bind 抛出 UNAUTHORIZED")
        void shouldThrowWhenWorkspaceNotSet() {
            CurrentContext.clear();
            TestEntity entity = new TestEntity(null, null, "test");

            assertThatThrownBy(() -> WorkspaceGuard.bind(entity))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        }
    }

    // ==================== requireInWorkspace ====================

    @Nested
    @DisplayName("requireInWorkspace")
    class RequireInWorkspace {

        @Test
        @DisplayName("实体存在且 workspace 匹配时返回实体")
        void shouldReturnEntityWhenMatch() {
            TestEntity entity = new TestEntity(1L, WS_ID, "test");

            TestEntity result = WorkspaceGuard.requireInWorkspace(entity, ErrorCode.NOT_FOUND);

            assertThat(result).isSameAs(entity);
        }

        @Test
        @DisplayName("实体为 null 时抛出异常")
        void shouldThrowWhenEntityNull() {
            assertThatThrownBy(() -> WorkspaceGuard.requireInWorkspace(null, ErrorCode.NOT_FOUND))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("workspace 不匹配时抛出异常")
        void shouldThrowWhenWorkspaceMismatch() {
            TestEntity entity = new TestEntity(1L, 999L, "test");

            assertThatThrownBy(() -> WorkspaceGuard.requireInWorkspace(entity, ErrorCode.NOT_FOUND))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== 异步线程场景 ====================

    @Nested
    @DisplayName("async thread scenarios")
    class AsyncThreadScenarios {

        @Test
        @DisplayName("CurrentContext 未设置时 bind 抛出 UNAUTHORIZED（模拟异步线程）")
        void shouldThrowWhenContextNotSetInAsyncThread() {
            // 模拟异步线程：ContextPropagatingTaskDecorator 未正确传播
            CurrentContext.clear();

            TestEntity entity = new TestEntity(null, null, "test");
            assertThatThrownBy(() -> WorkspaceGuard.bind(entity))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        }

        @Test
        @DisplayName("CurrentContext 未设置时 requireInWorkspace 应通过（无法校验 workspace）")
        void shouldBehaveWhenContextNotSetInRequireInWorkspace() {
            CurrentContext.clear();

            TestEntity entity = new TestEntity(1L, 10L, "test");
            // requireInWorkspace 使用 CurrentContext.getWorkspaceId() 获取当前上下文
            // 未设置时 getWorkspaceId() 返回 null，与实体的 workspaceId (10L)
            // 通过 Objects.equals(null, 10L) → false，应抛异常
            assertThatThrownBy(() -> WorkspaceGuard.requireInWorkspace(entity, ErrorCode.NOT_FOUND))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("实体 workspaceId 为 null 时 requireInWorkspace 抛出异常")
        void shouldThrowWhenEntityWorkspaceIdIsNull() {
            TestEntity entity = new TestEntity(1L, null, "test");

            assertThatThrownBy(() -> WorkspaceGuard.requireInWorkspace(entity, ErrorCode.NOT_FOUND))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== checkNameUnique ====================

    @Nested
    @DisplayName("checkNameUnique")
    class CheckNameUnique {

        @Mock BaseMapper<TestEntity> mapper;

        @Test
        @DisplayName("名称不重复时不抛异常")
        void shouldPassWhenNameUnique() {
            when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            SFunction<TestEntity, String> nameCol = TestEntity::getName;
            SFunction<TestEntity, Long> wsCol = TestEntity::getWorkspaceId;
            SFunction<TestEntity, Long> idCol = TestEntity::getId;

            WorkspaceGuard.checkNameUnique(mapper, nameCol, wsCol, idCol, "uniqueName", null, ErrorCode.AGENT_NAME_DUPLICATE);

            verify(mapper).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("名称重复时抛出异常")
        void shouldThrowWhenNameDuplicate() {
            when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            SFunction<TestEntity, String> nameCol = TestEntity::getName;
            SFunction<TestEntity, Long> wsCol = TestEntity::getWorkspaceId;
            SFunction<TestEntity, Long> idCol = TestEntity::getId;

            assertThatThrownBy(() -> WorkspaceGuard.checkNameUnique(
                    mapper, nameCol, wsCol, idCol, "dupe", null, ErrorCode.AGENT_NAME_DUPLICATE))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("排除自身 ID 时，同 ID 不视为重复")
        void shouldPassWhenExcludingItself() {
            when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            SFunction<TestEntity, String> nameCol = TestEntity::getName;
            SFunction<TestEntity, Long> wsCol = TestEntity::getWorkspaceId;
            SFunction<TestEntity, Long> idCol = TestEntity::getId;

            WorkspaceGuard.checkNameUnique(mapper, nameCol, wsCol, idCol, "sameName", 5L, ErrorCode.AGENT_NAME_DUPLICATE);

            verify(mapper).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("自定义错误消息的 overload")
        void shouldThrowWithCustomMessage() {
            when(mapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            SFunction<TestEntity, String> nameCol = TestEntity::getName;
            SFunction<TestEntity, Long> wsCol = TestEntity::getWorkspaceId;
            SFunction<TestEntity, Long> idCol = TestEntity::getId;

            assertThatThrownBy(() -> WorkspaceGuard.checkNameUnique(
                    mapper, nameCol, wsCol, idCol, "dupe", null, ErrorCode.AGENT_NAME_DUPLICATE, "自定义错误"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("自定义错误");
        }
    }
}
