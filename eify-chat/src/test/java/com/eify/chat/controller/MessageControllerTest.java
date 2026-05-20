package com.eify.chat.controller;

import com.eify.chat.domain.dto.MessageResponse;
import com.eify.chat.domain.dto.MessageTimeRangeRequest;
import com.eify.chat.domain.entity.Message;
import com.eify.chat.service.MessageService;
import com.eify.common.dto.CursorPageRequest;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MessageController")
class MessageControllerTest {

    @Mock
    MessageService messageService;

    @InjectMocks
    MessageController controller;

    private Message buildMessage(Long id, String role, String content) {
        Message m = new Message();
        m.setId(id);
        m.setSessionId(100L);
        m.setRole(role);
        m.setContent(content);
        m.setTokensUsed(100);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }

    @Nested
    @DisplayName("listByCursor")
    class ListByCursorTests {

        @Test
        @DisplayName("应返回游标分页消息列表")
        void shouldReturnCursorPagedMessages() {
            CursorPageRequest req = new CursorPageRequest();
            req.setPageSize(20);
            PageResult<Message> pageResult = PageResult.ofCursor(
                    List.of(buildMessage(1L, "user", "hello"),
                            buildMessage(2L, "assistant", "hi there")),
                    20, false);
            when(messageService.listByCursor(eq(100L), any())).thenReturn(pageResult);

            Result<PageResult<MessageResponse>> result = controller.listByCursor(100L, req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).hasSize(2);
            assertThat(result.getData().getList().get(0).getRole()).isEqualTo("user");
        }

        @Test
        @DisplayName("空结果应正常返回")
        void shouldReturnEmptyWhenNoMessages() {
            CursorPageRequest req = new CursorPageRequest();
            PageResult<Message> pageResult = PageResult.ofCursor(List.of(), 20, false);
            when(messageService.listByCursor(eq(100L), any())).thenReturn(pageResult);

            Result<PageResult<MessageResponse>> result = controller.listByCursor(100L, req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).isEmpty();
        }
    }

    @Nested
    @DisplayName("listByTimeRange")
    class ListByTimeRangeTests {

        @Test
        @DisplayName("应按时间范围返回消息")
        void shouldReturnMessagesInTimeRange() {
            MessageTimeRangeRequest req = new MessageTimeRangeRequest();
            req.setStartTime(1000L);
            req.setEndTime(2000L);
            req.setPageSize(20);
            PageResult<Message> pageResult = PageResult.ofCursor(List.of(buildMessage(1L, "user", "hi")), 20, false);
            when(messageService.listByTimeRange(any())).thenReturn(pageResult);

            Result<PageResult<MessageResponse>> result = controller.listByTimeRange(req);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("应返回单条消息")
        void shouldReturnSingleMessage() {
            when(messageService.getById(1L)).thenReturn(buildMessage(1L, "assistant", "response"));

            Result<MessageResponse> result = controller.getById(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getRole()).isEqualTo("assistant");
            assertThat(result.getData().getContent()).isEqualTo("response");
        }
    }
}
