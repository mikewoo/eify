package com.eify.chat.service.impl;

import com.eify.agent.domain.entity.Agent;
import com.eify.agent.service.AgentService;
import com.eify.chat.domain.dto.SendChatRequest;
import com.eify.chat.domain.entity.Conversation;
import com.eify.chat.domain.entity.Message;
import com.eify.chat.service.ConversationService;
import com.eify.chat.service.MessageService;
import com.eify.common.error.ErrorCode;
import com.eify.common.util.MessageUtil;
import com.eify.common.exception.BusinessException;
import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.route.EmbeddingRouteResolver;
import com.eify.knowledge.service.ChunkService;
import com.eify.knowledge.service.KnowledgeService;
import com.eify.knowledge.strategy.EmbeddingStrategy;
import com.eify.mcp.mapper.McpToolMapper;
import com.eify.mcp.service.McpClientService;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.workflow.engine.WorkflowEngine;
import com.eify.workflow.mapper.WorkflowNodeMapper;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Executor;

import com.eify.common.context.CurrentContext;
import com.eify.mcp.domain.entity.McpTool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.AfterEach;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatServiceImpl")
class ChatServiceImplTest {

    @Mock ConversationService conversationService;
    @Mock MessageService messageService;
    @Mock AgentService agentService;
    @Mock com.eify.provider.service.ProviderService providerService;
    @Mock ProviderAdapterFactory adapterFactory;
    @Mock ChunkService chunkService;
    @Mock EmbeddingStrategy embeddingStrategy;
    @Mock EmbeddingRouteResolver routeResolver;
    @Mock KnowledgeService knowledgeService;
    @Mock WorkflowEngine workflowEngine;
    @Mock WorkflowNodeMapper workflowNodeMapper;
    @Mock McpClientService mcpClientService;
    @Mock McpToolMapper mcpToolMapper;
    @Mock Executor sseExecutor;
    @Mock MessageUtil messageUtil;

    ObjectMapper objectMapper = new ObjectMapper();

    ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
                conversationService, messageService, agentService, providerService,
                adapterFactory, objectMapper, chunkService, embeddingStrategy,
                routeResolver, knowledgeService,
                workflowEngine, workflowNodeMapper, mcpClientService, mcpToolMapper,
                messageUtil, sseExecutor);
    }

    @BeforeEach
    void setUpContext() {
        CurrentContext.set(1L, 1L);
    }

    @AfterEach
    void tearDownContext() {
        CurrentContext.clear();
    }

    // ==================== getDefaultContextRounds ====================

    @Nested
    @DisplayName("getDefaultContextRounds")
    class GetDefaultContextRoundsTests {

        @Test
        @DisplayName("应返回默认值 10")
        void shouldReturnDefaultValue() {
            assertThat(chatService.getDefaultContextRounds()).isEqualTo(10);
        }
    }

    // ==================== sendMessage 参数校验 ====================

    @Nested
    @DisplayName("sendMessage - 参数校验")
    class SendMessageValidationTests {

        @Test
        @DisplayName("userId 为 null 应抛出 UNAUTHORIZED")
        void shouldThrowWhenUserIdNull() {
            SendChatRequest req = new SendChatRequest();
            req.setContent("hello");

            assertThatThrownBy(() -> chatService.sendMessage(null, req))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code")
                    .isEqualTo(ErrorCode.UNAUTHORIZED.getCode());
        }

        @Test
        @DisplayName("无 sessionId、无 agentId、无 workflowId 应创建新会话后通过 SSE 返回错误事件")
        void shouldCreateNewConversationWhenNoSessionId() {
            SendChatRequest req = new SendChatRequest();
            req.setContent("hello");
            Conversation conv = new Conversation();
            conv.setId(1L);
            when(conversationService.create(eq(1L), isNull(), isNull(), isNull())).thenReturn(conv);

            SseEmitter emitter = chatService.sendMessage(1L, req);
            assertThat(emitter).isNotNull();
        }
    }

    // ==================== saveUserMessage ====================

    @Nested
    @DisplayName("saveUserMessage")
    class SaveUserMessageTests {

        @Test
        @DisplayName("应保存用户消息并返回已保存的 Message")
        void shouldSaveAndReturnMessage() {
            Message expected = new Message();
            expected.setId(1L);
            expected.setRole("user");
            expected.setContent("hello");
            when(messageService.save(any(Message.class))).thenReturn(expected);

            Message result = chatService.saveUserMessage(100L, "hello", 1L, 1L);

            assertThat(result.getRole()).isEqualTo("user");
            assertThat(result.getContent()).isEqualTo("hello");
            verify(messageService).save(any(Message.class));
        }
    }

    // ==================== saveAssistantMessage ====================

    @Nested
    @DisplayName("saveAssistantMessage")
    class SaveAssistantMessageTests {

        @Test
        @DisplayName("应保存 AI 响应（含 token 使用量）")
        void shouldSaveAssistantMessageWithTokens() {
            com.eify.provider.domain.dto.ChatResponse.Usage usage =
                    new com.eify.provider.domain.dto.ChatResponse.Usage();
            usage.setPromptTokens(100);
            usage.setCompletionTokens(50);
            usage.setTotalTokens(150);

            chatService.saveAssistantMessage(100L, "AI response", usage, 1L, 1L, 1L);

            verify(messageService).save(any(Message.class));
        }

        @Test
        @DisplayName("usage 为 null 时应正常保存")
        void shouldSaveWithoutUsage() {
            chatService.saveAssistantMessage(100L, "AI response", null, 1L, 1L, 1L);

            verify(messageService).save(any(Message.class));
        }
    }

    @Nested
    @DisplayName("findServerIdForTool - 工作空间隔离")
    class FindServerIdForToolWorkspaceIsolation {

        @Test
        @DisplayName("按工具名查询时携带 workspace_id 过滤")
        void shouldFilterByWorkspaceIdWhenLookingUpTool() throws Exception {
            McpTool ws1Tool = new McpTool();
            ws1Tool.setId(10L);
            ws1Tool.setServerId(5L);
            ws1Tool.setName("search");
            ws1Tool.setWorkspaceId(1L);

            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(ws1Tool));

            var method = ChatServiceImpl.class.getDeclaredMethod(
                    "findServerIdForTool", String.class, java.util.List.class);
            method.setAccessible(true);
            Long serverId = (Long) method.invoke(chatService, "search", List.of());

            assertThat(serverId).isEqualTo(5L);
            verify(mcpToolMapper).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("跨工作空间工具不可见 — 同名工具在另一空间返回 null")
        void shouldReturnNullForToolInOtherWorkspace() throws Exception {
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            var method = ChatServiceImpl.class.getDeclaredMethod(
                    "findServerIdForTool", String.class, java.util.List.class);
            method.setAccessible(true);
            Long serverId = (Long) method.invoke(chatService, "get_data", List.of());

            assertThat(serverId).isNull();
        }
    }
}
