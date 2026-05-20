package com.eify.chat.controller;

import com.eify.chat.domain.dto.ConversationResponse;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.service.ConversationService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationController")
class ConversationControllerTest {

    @Mock
    ConversationService conversationService;

    @InjectMocks
    ConversationController controller;

    private Conversation buildConversation(Long id) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setUserId(1L);
        c.setAgentId(10L);
        c.setTitle("test");
        c.setStatus(1);
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        return c;
    }

    @Nested
    @DisplayName("listUserConversations")
    class ListUserConversationsTests {

        @Test
        @DisplayName("应返回用户对话列表（游标分页）")
        void shouldReturnUserConversations() {
            PageResult<Conversation> pageResult = PageResult.ofCursor(
                    List.of(buildConversation(1L), buildConversation(2L)), 20, false);
            when(conversationService.listUserConversations(eq(1L), eq(1), isNull(), isNull(), eq(20)))
                    .thenReturn(pageResult);

            Result<PageResult<ConversationResponse>> result =
                    controller.listUserConversations(1L, 1, null, null, 20);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).hasSize(2);
            assertThat(result.getData().getList().get(0).getUserId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("listAgentConversations")
    class ListAgentConversationsTests {

        @Test
        @DisplayName("应返回 Agent 对话列表")
        void shouldReturnAgentConversations() {
            PageResult<Conversation> pageResult = PageResult.ofCursor(
                    List.of(buildConversation(1L)), 20, false);
            when(conversationService.listAgentConversations(eq(10L), isNull(), isNull(), eq(20)))
                    .thenReturn(pageResult);

            Result<PageResult<ConversationResponse>> result =
                    controller.listAgentConversations(10L, null, null, 20);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getList()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("getById")
    class GetByIdTests {

        @Test
        @DisplayName("应返回对话详情")
        void shouldReturnConversation() {
            when(conversationService.getById(1L)).thenReturn(buildConversation(1L));

            Result<ConversationResponse> result = controller.getById(1L);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("应委托 service 删除并返回成功")
        void shouldDelegateAndReturnSuccess() {
            Result<Void> result = controller.delete(1L);

            assertThat(result.isSuccess()).isTrue();
            verify(conversationService).delete(1L);
        }
    }
}
