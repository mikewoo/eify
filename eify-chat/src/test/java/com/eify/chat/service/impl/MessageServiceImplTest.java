package com.eify.chat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.chat.domain.dto.MessageTimeRangeRequest;
import com.eify.chat.domain.entity.Message;
import com.eify.chat.mapper.MessageMapper;
import com.eify.common.context.CurrentContext;
import com.eify.common.dto.CursorPageRequest;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageServiceImpl")
class MessageServiceImplTest {

    @Mock
    MessageMapper messageMapper;

    MessageServiceImpl messageService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L);
        messageService = new MessageServiceImpl(messageMapper);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    private Message buildMessage(Long id, Long sessionId, String role, String content) {
        Message msg = new Message();
        msg.setId(id);
        msg.setSessionId(sessionId);
        msg.setWorkspaceId(1L);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.of(2026, 5, 15, 10, 0, id != null ? id.intValue() : 0));
        return msg;
    }

    // ==================== listByCursor ====================

    @Nested
    @DisplayName("listByCursor")
    class ListByCursor {

        @Test
        @DisplayName("游标分页查询消息")
        void shouldListByCursor() {
            Page<Message> mPage = new Page<>(1, 20);
            mPage.setRecords(List.of(buildMessage(1L, 100L, "user", "hello")));
            mPage.setTotal(1L);
            when(messageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mPage);

            PageResult<Message> result = messageService.listByCursor(100L,
                    new CursorPageRequest(null, 20));

            assertThat(result.getList()).hasSize(1);
            assertThat(result.getList().get(0).getContent()).isEqualTo("hello");
            assertThat(result.getTotal()).isEqualTo(1L);
        }

        @Test
        @DisplayName("sessionId 为 null 时抛出 PARAM_ERROR")
        void shouldThrowWhenSessionIdNull() {
            assertThatThrownBy(() -> messageService.listByCursor(null,
                    new CursorPageRequest(null, 20)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("pageSize 超出范围时抛出 PARAM_ERROR")
        void shouldThrowWhenPageSizeOutOfRange() {
            assertThatThrownBy(() -> messageService.listByCursor(100L,
                    new CursorPageRequest(null, 0)))
                    .isInstanceOf(BusinessException.class);

            assertThatThrownBy(() -> messageService.listByCursor(100L,
                    new CursorPageRequest(null, 101)))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("空结果返回空列表")
        void shouldReturnEmptyList() {
            Page<Message> mPage = new Page<>(1, 20);
            mPage.setRecords(Collections.emptyList());
            mPage.setTotal(0L);
            when(messageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mPage);

            PageResult<Message> result = messageService.listByCursor(100L,
                    new CursorPageRequest(null, 20));

            assertThat(result.getList()).isEmpty();
            assertThat(result.getTotal()).isEqualTo(0L);
        }
    }

    // ==================== listByTimeRange ====================

    @Nested
    @DisplayName("listByTimeRange")
    class ListByTimeRange {

        @Test
        @DisplayName("按时间范围查询消息")
        void shouldListByTimeRange() {
            Page<Message> mPage = new Page<>(1, 20);
            mPage.setRecords(List.of(buildMessage(1L, 100L, "user", "hi")));
            mPage.setTotal(1L);
            when(messageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mPage);

            MessageTimeRangeRequest request = new MessageTimeRangeRequest();
            request.setStartTime(1700000000000L);
            request.setEndTime(1800000000000L);
            request.setPageSize(20);

            PageResult<Message> result = messageService.listByTimeRange(request);

            assertThat(result.getList()).hasSize(1);
        }

        @Test
        @DisplayName("startTime 为 null 时抛出异常")
        void shouldThrowWhenStartTimeNull() {
            MessageTimeRangeRequest request = new MessageTimeRangeRequest();
            request.setStartTime(null);
            request.setEndTime(1700000000000L);
            request.setPageSize(20);

            assertThatThrownBy(() -> messageService.listByTimeRange(request))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("endTime 为 null 时抛出异常")
        void shouldThrowWhenEndTimeNull() {
            MessageTimeRangeRequest request = new MessageTimeRangeRequest();
            request.setStartTime(1700000000000L);
            request.setEndTime(null);
            request.setPageSize(20);

            assertThatThrownBy(() -> messageService.listByTimeRange(request))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== getById ====================

    @Nested
    @DisplayName("getById")
    class GetById {

        @Test
        @DisplayName("根据 ID 获取消息")
        void shouldGetById() {
            Message msg = buildMessage(1L, 100L, "user", "hello");
            when(messageMapper.selectById(1L)).thenReturn(msg);

            Message result = messageService.getById(1L);

            assertThat(result).isSameAs(msg);
        }

        @Test
        @DisplayName("不存在时抛出 NOT_FOUND")
        void shouldThrowWhenNotFound() {
            when(messageMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> messageService.getById(999L))
                    .isInstanceOf(BusinessException.class);
        }
    }

    // ==================== save ====================

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("保存消息并返回")
        void shouldSaveAndReturn() {
            Message msg = buildMessage(null, 100L, "user", "new message");
            when(messageMapper.insert(msg)).thenReturn(1);

            Message result = messageService.save(msg);

            assertThat(result).isSameAs(msg);
            verify(messageMapper).insert(msg);
        }
    }

    // ==================== loadRecentMessages ====================

    @Nested
    @DisplayName("loadRecentMessages")
    class LoadRecentMessages {

        @Test
        @DisplayName("加载最近消息并按旧到新排列")
        void shouldLoadRecentMessagesChronologically() {
            Message newer = buildMessage(2L, 100L, "assistant", "reply");
            Message older = buildMessage(1L, 100L, "user", "question");
            Page<Message> mPage = new Page<>(1, 10);
            // DB returns desc order (newest first)
            mPage.setRecords(List.of(newer, older));
            mPage.setTotal(2L);
            when(messageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mPage);

            List<Message> result = messageService.loadRecentMessages(100L, 10);

            assertThat(result).hasSize(2);
            // Should be reversed: oldest first
            assertThat(result.get(0).getId()).isEqualTo(1L);
            assertThat(result.get(1).getId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("无消息时返回空列表")
        void shouldReturnEmptyWhenNoMessages() {
            Page<Message> mPage = new Page<>(1, 10);
            mPage.setRecords(Collections.emptyList());
            mPage.setTotal(0L);
            when(messageMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(mPage);

            List<Message> result = messageService.loadRecentMessages(100L, 10);

            assertThat(result).isEmpty();
        }
    }
}
