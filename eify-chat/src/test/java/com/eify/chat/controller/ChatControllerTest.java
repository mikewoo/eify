package com.eify.chat.controller;

import com.eify.common.context.CurrentContext;
import com.eify.chat.domain.dto.ConversationResponse;
import com.eify.chat.domain.dto.CreateConversationRequest;
import com.eify.chat.domain.dto.SendChatRequest;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.service.ChatService;
import com.eify.chat.service.ConversationService;
import com.eify.chat.service.MessageService;
import com.eify.common.error.ErrorCode;
import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatController")
class ChatControllerTest {

    @Mock
    ChatService chatService;

    @Mock
    ConversationService conversationService;

    @Mock
    MessageService messageService;

    @Mock
    JdbcTemplate jdbcTemplate;

    ChatController controller;

    @BeforeEach
    void setUp() {
        controller = new ChatController(chatService, conversationService, messageService, jdbcTemplate);
        CurrentContext.set(1L, 1L);
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    @Nested
    @DisplayName("sendMessage")
    class SendMessageTests {

        @Test
        @DisplayName("应委托 chatService.sendMessage 并返回 SseEmitter")
        void shouldDelegateToChatService() {
            SendChatRequest request = new SendChatRequest();
            request.setContent("hello");
            request.setAgentId(1L);
            SseEmitter emitter = new SseEmitter();
            when(chatService.sendMessage(eq(1L), any())).thenReturn(emitter);

            SseEmitter result = controller.sendMessage(request, new MockHttpServletRequest());

            assertThat(result).isSameAs(emitter);
        }
    }

    @Nested
    @DisplayName("getDefaultContextRounds")
    class GetDefaultContextRoundsTests {

        @Test
        @DisplayName("应返回默认上下文轮数")
        void shouldReturnDefaultRounds() {
            when(chatService.getDefaultContextRounds()).thenReturn(10);

            Result<Integer> result = controller.getDefaultContextRounds();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("health")
    class HealthTests {

        @Test
        @DisplayName("应返回健康状态")
        void shouldReturnHealthy() {
            Result<String> result = controller.health();

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData()).contains("working");
        }
    }

    @Nested
    @DisplayName("createConversation")
    class CreateConversationTests {

        @Test
        @DisplayName("创建成功应返回 ConversationResponse")
        void shouldCreateAndReturnResponse() {
            CreateConversationRequest req = new CreateConversationRequest();
            req.setAgentId(1L);
            req.setTitle("test");
            Conversation conv = new Conversation();
            conv.setId(100L);
            conv.setUserId(1L);
            conv.setAgentId(1L);
            conv.setTitle("test");
            conv.setStatus(1);
            when(conversationService.create(anyLong(), anyLong(), isNull(), eq("test"))).thenReturn(conv);

            Result<ConversationResponse> result = controller.createConversation(req, new MockHttpServletRequest());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getData().getId()).isEqualTo(100L);
            assertThat(result.getData().getTitle()).isEqualTo("test");
        }
    }

    @Nested
    @DisplayName("getConversationMessages")
    class GetConversationMessagesTests {

        @Test
        @DisplayName("对话存在应返回消息列表")
        void shouldReturnMessagesWhenConversationExists() {
            when(conversationService.getById(1L)).thenReturn(new Conversation());
            PageResult<com.eify.chat.domain.entity.Message> pageResult = new PageResult<>();
            when(messageService.listByCursor(eq(1L), any())).thenReturn(pageResult);

            Result<PageResult<com.eify.chat.domain.entity.Message>> result =
                    controller.getConversationMessages(1L, null, null, 20);

            assertThat(result.isSuccess()).isTrue();
        }

        @Test
        @DisplayName("对话不存在应返回 NOT_FOUND")
        void shouldReturnNotFoundWhenConversationMissing() {
            when(conversationService.getById(999L)).thenThrow(new RuntimeException());

            Result<PageResult<com.eify.chat.domain.entity.Message>> result =
                    controller.getConversationMessages(999L, null, null, 20);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getCode()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        }
    }
}
