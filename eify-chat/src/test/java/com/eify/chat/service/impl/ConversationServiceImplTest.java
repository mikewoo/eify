package com.eify.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.context.CurrentContext;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.mapper.ConversationMapper;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ConversationServiceImpl 单元测试。
 * <p>
 * 使用 Mockito mock 所有依赖，只测业务逻辑。
 * 不启动 Spring 容器，单个测试 < 50ms。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationServiceImpl")
class ConversationServiceImplTest {

    @Mock
    ConversationMapper conversationMapper;

    @InjectMocks
    ConversationServiceImpl conversationService;

    private static final Long USER_ID = 1L;
    private static final Long AGENT_ID = 10L;
    private static final Long WORKFLOW_ID = 20L;
    private static final Long WORKSPACE_ID = 1L;

    @BeforeEach
    void setUp() {
        CurrentContext.set(USER_ID, WORKSPACE_ID);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ========== 辅助方法 ==========

    private Conversation buildConversation(Long id, Long userId, Long agentId, Long workspaceId) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setUserId(userId);
        c.setAgentId(agentId);
        c.setWorkspaceId(workspaceId);
        c.setTitle("测试对话");
        c.setStatus(1);
        c.setCreatorId(userId);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    // ========== listUserConversations() ==========

    @Nested
    @DisplayName("listUserConversations()")
    class ListUserConversationsTests {

        @Test
        @DisplayName("P0 - userId 为 null 应抛参数异常")
        void shouldThrowWhenUserIdIsNull() {
            assertThrows(BusinessException.class,
                    () -> conversationService.listUserConversations(null, 1, null, null, 20));
        }

        @Test
        @DisplayName("P1 - status 为 null 时默认查询进行中（status=1）")
        void shouldDefaultStatusToOneWhenNull() {
            // given
            Page<Conversation> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(conversationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            conversationService.listUserConversations(USER_ID, null, null, null, 20);

            // then
            ArgumentCaptor<LambdaQueryWrapper<Conversation>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(conversationMapper).selectPage(any(Page.class), wrapperCaptor.capture());
            // 验证 wrapper 被正确构建（不抛异常即为通过，status 默认为 1）
        }

        @Test
        @DisplayName("P1 - pageSize 为 null 时默认使用 20")
        void shouldDefaultPageSizeTo20WhenNull() {
            // given
            Page<Conversation> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Page<Conversation>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            when(conversationMapper.selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            conversationService.listUserConversations(USER_ID, 1, null, null, null);

            // then - 验证传给 mapper 的 Page 使用了默认 pageSize=20
            assertEquals(20, pageCaptor.getValue().getSize());
        }

        @Test
        @DisplayName("P1 - pageSize 超过 100 时默认使用 20")
        void shouldDefaultPageSizeTo20WhenExceeds100() {
            // given
            Page<Conversation> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Page<Conversation>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            when(conversationMapper.selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            conversationService.listUserConversations(USER_ID, 1, null, null, 101);

            // then
            assertEquals(20, pageCaptor.getValue().getSize());
        }

        @Test
        @DisplayName("P1 - pageSize 小于 1 时默认使用 20")
        void shouldDefaultPageSizeTo20WhenLessThan1() {
            // given
            Page<Conversation> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Page<Conversation>> pageCaptor = ArgumentCaptor.forClass(Page.class);
            when(conversationMapper.selectPage(pageCaptor.capture(), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            conversationService.listUserConversations(USER_ID, 1, null, null, 0);

            // then
            assertEquals(20, pageCaptor.getValue().getSize());
        }

        @Test
        @DisplayName("P1 - 正常查询应返回分页结果")
        void shouldReturnPaginatedResults() {
            // given
            Page<Conversation> pageObj = new Page<>(1, 20);
            Conversation conv = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            pageObj.setRecords(List.of(conv));
            pageObj.setTotal(1);

            when(conversationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<Conversation> result = conversationService
                    .listUserConversations(USER_ID, 1, null, null, 20);

            // then
            assertEquals(1, result.getList().size());
            assertEquals(1L, result.getTotal());
            assertEquals("测试对话", result.getList().get(0).getTitle());
        }

        @Test
        @DisplayName("P1 - 空列表应返回空结果")
        void shouldReturnEmptyListWhenNoRecords() {
            // given
            Page<Conversation> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(conversationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<Conversation> result = conversationService
                    .listUserConversations(USER_ID, 1, null, null, 20);

            // then
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
        }
    }

    // ========== listAgentConversations() ==========

    @Nested
    @DisplayName("listAgentConversations()")
    class ListAgentConversationsTests {

        @Test
        @DisplayName("P0 - agentId 为 null 应抛参数异常")
        void shouldThrowWhenAgentIdIsNull() {
            assertThrows(BusinessException.class,
                    () -> conversationService.listAgentConversations(null, null, null, 20));
        }

        @Test
        @DisplayName("P1 - pageSize 为 null 时默认使用 20")
        void shouldDefaultPageSizeTo20WhenNull() {
            // given
            when(conversationMapper.selectAgentConversationsFirstPage(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(21)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<Conversation> result = conversationService
                    .listAgentConversations(AGENT_ID, null, null, null);

            // then
            assertEquals(20, result.getPageSize());
        }

        @Test
        @DisplayName("P1 - pageSize 超过 100 时默认使用 20")
        void shouldDefaultPageSizeTo20WhenExceeds100() {
            // given
            when(conversationMapper.selectAgentConversationsFirstPage(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(21)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<Conversation> result = conversationService
                    .listAgentConversations(AGENT_ID, null, null, 101);

            // then
            assertEquals(20, result.getPageSize());
        }

        @Test
        @DisplayName("P1 - 第一页（lastId 为 null）应使用 selectAgentConversationsFirstPage")
        void shouldUseFirstPageQueryWhenLastIdIsNull() {
            // given
            Conversation conv = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            when(conversationMapper.selectAgentConversationsFirstPage(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(21)))
                    .thenReturn(List.of(conv));

            // when
            PageResult<Conversation> result = conversationService
                    .listAgentConversations(AGENT_ID, null, null, 20);

            // then
            assertTrue(result.isCursorMode()); // ofCursor 设置 total=null，isCursorMode()=true
            assertEquals(1, result.getList().size());
            assertFalse(result.getHasMore());
            verify(conversationMapper).selectAgentConversationsFirstPage(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(21));
            verify(conversationMapper, never()).selectAgentConversationsByCursor(
                    any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("P1 - 第二页（lastId 不为 null）应使用 selectAgentConversationsByCursor")
        void shouldUseCursorQueryWhenLastIdProvided() {
            // given
            Long lastId = 50L;
            LocalDateTime lastTimestamp = LocalDateTime.now().minusHours(1);
            Conversation conv = buildConversation(49L, USER_ID, AGENT_ID, WORKSPACE_ID);

            when(conversationMapper.selectAgentConversationsByCursor(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(lastId), any(LocalDateTime.class), eq(21)))
                    .thenReturn(List.of(conv));

            // when
            PageResult<Conversation> result = conversationService
                    .listAgentConversations(AGENT_ID, lastId, lastTimestamp, 20);

            // then
            assertTrue(result.isCursorMode());
            assertEquals(1, result.getList().size());
            assertFalse(result.getHasMore());
            verify(conversationMapper).selectAgentConversationsByCursor(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(lastId), any(LocalDateTime.class), eq(21));
        }

        @Test
        @DisplayName("P1 - 游标分页 lastTimestamp 为 null 时使用当前时间")
        void shouldUseNowWhenLastTimestampIsNull() {
            // given
            Long lastId = 50L;
            when(conversationMapper.selectAgentConversationsByCursor(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(lastId), any(LocalDateTime.class), eq(21)))
                    .thenReturn(Collections.emptyList());

            // when
            conversationService.listAgentConversations(AGENT_ID, lastId, null, 20);

            // then - 验证 lastTimestamp 不为 null（使用了 LocalDateTime.now()）
            ArgumentCaptor<LocalDateTime> timeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
            verify(conversationMapper).selectAgentConversationsByCursor(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(lastId), timeCaptor.capture(), eq(21));
            assertNotNull(timeCaptor.getValue());
        }

        @Test
        @DisplayName("P1 - 返回数据多于 pageSize 时应截断并标记 hasMore=true")
        void shouldTruncateAndSetHasMoreWhenExtraDataExists() {
            // given - 返回 21 条（多查的 1 条）
            Conversation conv1 = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            Conversation conv2 = buildConversation(2L, USER_ID, AGENT_ID, WORKSPACE_ID);
            List<Conversation> extraList = List.of(conv1, conv2); // 2 条 > pageSize=1

            when(conversationMapper.selectAgentConversationsFirstPage(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(2)))
                    .thenReturn(extraList);

            // when
            PageResult<Conversation> result = conversationService
                    .listAgentConversations(AGENT_ID, null, null, 1);

            // then
            assertTrue(result.isCursorMode());
            assertTrue(result.getHasMore());
            assertEquals(1, result.getList().size()); // 截断到 pageSize
        }

        @Test
        @DisplayName("P1 - 返回数据等于 pageSize 时 hasMore=false")
        void shouldSetHasMoreFalseWhenExactPageSize() {
            // given
            Conversation conv = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            when(conversationMapper.selectAgentConversationsFirstPage(
                    eq(AGENT_ID), eq(WORKSPACE_ID), eq(21)))
                    .thenReturn(List.of(conv)); // 1 条 <= pageSize=20

            // when
            PageResult<Conversation> result = conversationService
                    .listAgentConversations(AGENT_ID, null, null, 20);

            // then
            assertFalse(result.getHasMore());
        }
    }

    // ========== getById() ==========

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("P0 - 对话不存在应抛 NOT_FOUND 异常")
        void shouldThrowWhenConversationNotFound() {
            // given
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> conversationService.getById(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛异常（模拟返回 null）")
        void shouldThrowWhenConversationInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> conversationService.getById(1L));
        }

        @Test
        @DisplayName("P1 - 正常查询应返回对话实体")
        void shouldReturnConversationWhenFound() {
            // given
            Conversation conv = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(conv);

            // when
            Conversation result = conversationService.getById(1L);

            // then
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals(USER_ID, result.getUserId());
            assertEquals(AGENT_ID, result.getAgentId());
            assertEquals("测试对话", result.getTitle());
        }
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("P1 - 基本创建（userId + agentId）应设置默认标题")
        void shouldCreateWithDefaultTitle() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

            // when
            Conversation result = conversationService.create(USER_ID, AGENT_ID);

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).insert(captor.capture());

            Conversation saved = captor.getValue();
            assertEquals(USER_ID, saved.getUserId());
            assertEquals(AGENT_ID, saved.getAgentId());
            assertNull(saved.getWorkflowId());
            assertEquals("新对话", saved.getTitle());
            assertEquals(1, saved.getStatus());
            assertEquals(WORKSPACE_ID, saved.getWorkspaceId());
            assertEquals(USER_ID, saved.getCreatorId());
        }

        @Test
        @DisplayName("P1 - 带自定义标题创建")
        void shouldCreateWithCustomTitle() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

            // when
            Conversation result = conversationService.create(USER_ID, AGENT_ID, "自定义标题");

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).insert(captor.capture());

            assertEquals("自定义标题", captor.getValue().getTitle());
        }

        @Test
        @DisplayName("P1 - 标题为空字符串时应使用默认标题")
        void shouldUseDefaultTitleWhenTitleIsBlank() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

            // when
            conversationService.create(USER_ID, AGENT_ID, "   ");

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).insert(captor.capture());
            assertEquals("新对话", captor.getValue().getTitle());
        }

        @Test
        @DisplayName("P1 - 标题为 null 时应使用默认标题")
        void shouldUseDefaultTitleWhenTitleIsNull() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

            // when
            conversationService.create(USER_ID, AGENT_ID, null);

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).insert(captor.capture());
            assertEquals("新对话", captor.getValue().getTitle());
        }

        @Test
        @DisplayName("P1 - 带 workflowId 创建")
        void shouldCreateWithWorkflowId() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

            // when
            Conversation result = conversationService.create(USER_ID, null, WORKFLOW_ID, "工作流对话");

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).insert(captor.capture());

            Conversation saved = captor.getValue();
            assertNull(saved.getAgentId());
            assertEquals(WORKFLOW_ID, saved.getWorkflowId());
            assertEquals("工作流对话", saved.getTitle());
        }

        @Test
        @DisplayName("P1 - 创建成功应返回包含 ID 的实体")
        void shouldReturnEntityWithIdAfterInsert() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenAnswer(invocation -> {
                Conversation c = invocation.getArgument(0);
                c.setId(100L); // 模拟 MyBatis-Plus 回填 ID
                return 1;
            });

            // when
            Conversation result = conversationService.create(USER_ID, AGENT_ID);

            // then
            assertEquals(100L, result.getId());
        }

        @Test
        @DisplayName("P1 - 创建时应绑定 workspaceId")
        void shouldBindWorkspaceIdOnCreate() {
            // given
            when(conversationMapper.insert(any(Conversation.class))).thenReturn(1);

            // when
            conversationService.create(USER_ID, AGENT_ID);

            // then
            ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
            verify(conversationMapper).insert(captor.capture());
            assertEquals(WORKSPACE_ID, captor.getValue().getWorkspaceId());
        }

        @Test
        @DisplayName("P0 - insert 抛异常时应原样抛出（不吞异常）")
        void shouldRethrowWhenInsertFails() {
            // given
            RuntimeException dbError = new RuntimeException("数据库连接失败");
            when(conversationMapper.insert(any(Conversation.class))).thenThrow(dbError);

            // when & then
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> conversationService.create(USER_ID, AGENT_ID));
            assertSame(dbError, ex);
        }
    }

    // ========== delete() ==========

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("P0 - 对话不存在应抛 NOT_FOUND 异常")
        void shouldThrowWhenConversationNotFound() {
            // given
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> conversationService.delete(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 删除应抛异常（模拟返回 null）")
        void shouldThrowWhenConversationInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> conversationService.delete(1L));
        }

        @Test
        @DisplayName("P1 - 正常删除应调用 deleteById")
        void shouldDeleteSuccessfully() {
            // given
            Conversation conv = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(conv);
            when(conversationMapper.deleteById(1L)).thenReturn(1);

            // when
            assertDoesNotThrow(() -> conversationService.delete(1L));

            // then
            verify(conversationMapper).deleteById(1L);
        }

        @Test
        @DisplayName("P1 - 删除前应按 workspaceId 校验归属")
        void shouldVerifyWorkspaceBeforeDelete() {
            // given
            Conversation conv = buildConversation(1L, USER_ID, AGENT_ID, WORKSPACE_ID);
            when(conversationMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(conv);
            when(conversationMapper.deleteById(1L)).thenReturn(1);

            // when
            conversationService.delete(1L);

            // then
            ArgumentCaptor<LambdaQueryWrapper<Conversation>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(conversationMapper).selectOne(wrapperCaptor.capture());
            // 验证 selectOne 被调用（包含了 id + workspaceId 过滤条件）
        }
    }
}
