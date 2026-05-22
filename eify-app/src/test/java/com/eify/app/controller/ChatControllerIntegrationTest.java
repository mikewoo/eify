package com.eify.app.controller;

import cn.hutool.jwt.JWTUtil;
import com.eify.chat.service.ChatService;
import com.eify.chat.service.impl.ChatServiceImpl;
import com.eify.common.error.ErrorCode;
import com.eify.provider.adapter.ProviderAdapter;
import com.eify.provider.adapter.ProviderAdapterFactory;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.dto.ChatResponse;
import com.eify.provider.domain.dto.ChatStreamChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("Agent 对话集成测试")
class ChatControllerIntegrationTest {

    private static final String JWT_SECRET = "test-eify-jwt-secret";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private ProviderAdapterFactory adapterFactory;

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String authToken;
    private ProviderAdapter mockAdapter;
    private Long testAgentId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(wac.getBean(com.eify.app.security.JwtAuthFilter.class))
                .build();
        authToken = createTestToken(1L, 1L);

        mockAdapter = mock(ProviderAdapter.class);
        when(mockAdapter.getSupportedType()).thenReturn(ProviderType.OPENAI);
        when(adapterFactory.getAdapter(ProviderType.OPENAI)).thenReturn(mockAdapter);

        testAgentId = insertTestAgent();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM ai_chat_message WHERE session_id IN (SELECT id FROM ai_chat_session WHERE agent_id = ?)", testAgentId);
        jdbcTemplate.update("DELETE FROM ai_chat_session WHERE agent_id = ?", testAgentId);
        jdbcTemplate.update("DELETE FROM ai_agent WHERE id = ?", testAgentId);
    }

    // ==================== helpers ====================

    private String createTestToken(Long userId, Long workspaceId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", userId);
        payload.put("wid", workspaceId);
        payload.put("role", "owner");
        payload.put("iss", "eify-test");
        payload.put("aud", "eify-api");
        long now = System.currentTimeMillis() / 1000;
        payload.put("iat", now);
        payload.put("exp", now + 3600);
        return JWTUtil.createToken(payload, JWT_SECRET.getBytes(StandardCharsets.UTF_8));
    }

    private Long insertTestAgent() {
        long id = System.currentTimeMillis() % 100000;
        jdbcTemplate.update(
                "INSERT INTO ai_agent (id, workspace_id, name, default_provider_id, default_model, system_prompt, enabled) " +
                "VALUES (?, 1, 'Test Agent', 1, 'gpt-4', 'You are a helpful assistant.', 1)", id);
        return id;
    }

    private Long insertTestConversation(Long agentId) {
        long id = System.currentTimeMillis() % 100000 + 1;
        jdbcTemplate.update(
                "INSERT INTO ai_chat_session (id, workspace_id, user_id, agent_id, title, status, creator_id) " +
                "VALUES (?, 1, 1, ?, 'Test Conversation', 1, 1)", id, agentId);
        return id;
    }

    /** 创建一个异步 Flux：延迟 10ms 后依次发出内容块 + done 块 */
    @SuppressWarnings("unchecked")
    private Flux<ChatStreamChunk> stubStream(List<String> contents) {
        ChatStreamChunk[] chunks = new ChatStreamChunk[contents.size() + 1];
        for (int i = 0; i < contents.size(); i++) {
            chunks[i] = ChatStreamChunk.content(contents.get(i));
        }
        chunks[contents.size()] = ChatStreamChunk.done(
                ChatResponse.Usage.of(10, contents.size() * 2), "stop");
        return Mono.delay(Duration.ofMillis(10))
                .thenMany(Flux.fromArray(chunks));
    }

    /** 解析 SSE 文本，返回事件列表。支持多行 data:（Spring SseEmitter 会拆分 JSON） */
    private List<String[]> parseSseEvents(String content) {
        List<String[]> events = new java.util.ArrayList<>();
        String[] blocks = content.split("\n\n");
        for (String block : blocks) {
            if (block.trim().isEmpty()) continue;
            String eventName = null;
            StringBuilder dataBuilder = new StringBuilder();
            for (String line : block.split("\n")) {
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    if (dataBuilder.length() > 0) {
                        dataBuilder.append('\n');
                    }
                    dataBuilder.append(line.substring(5).trim());
                }
            }
            if (eventName != null) {
                String data = dataBuilder.toString();
                // 合并多行 JSON 为单行
                if (data.contains("\n")) {
                    data = data.replace("\n", "");
                }
                events.add(new String[]{eventName, data});
            }
        }
        return events;
    }

    private Long extractId(JsonNode node) {
        return node.get("data").get("id").asLong();
    }

    // ==================== Scenario 1: 创建会话 ====================

    @Test
    @DisplayName("POST /api/v1/chat/conversations — 合法请求创建成功")
    void should_createConversation_when_requestIsValid() throws Exception {
        String body = "{\"agentId\": " + testAgentId + "}";

        MvcResult result = mockMvc.perform(post("/api/v1/chat/conversations")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

        JsonNode data = node.get("data");
        assertThat(data.get("id").asLong()).isPositive();
        assertThat(data.get("title").asText()).isEqualTo("新对话");
        assertThat(data.get("agentId").asLong()).isEqualTo(testAgentId);
        assertThat(data.get("userId").asLong()).isEqualTo(1L);

        // 验证数据库落库
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_session WHERE id = ? AND deleted = 0",
                Integer.class, data.get("id").asLong());
        assertThat(count).isEqualTo(1);
    }

    // ==================== Scenario 2: 新建会话发送消息（mock LLM） ====================

    @Test
    @DisplayName("POST /api/v1/chat/send — 新建会话发送消息，SSE 流式返回")
    void should_sendMessageAndStream_when_newSession() throws Exception {
        when(mockAdapter.streamChat(any(), any()))
                .thenReturn(stubStream(List.of("你好", "，我是AI助手")));

        String body = "{\"agentId\": " + testAgentId + ", \"content\": \"Hello\"}";

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/send")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        String content = asyncResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String[]> events = parseSseEvents(content);

        // 验证 SSE 事件
        assertThat(events).hasSize(3); // 2 message + 1 complete

        // 验证 message 事件内容
        List<String[]> messageEvents = events.stream()
                .filter(e -> "message".equals(e[0])).toList();
        assertThat(messageEvents).hasSize(2);
        assertThat(messageEvents.get(0)[1]).contains("你好");
        assertThat(messageEvents.get(1)[1]).contains("，我是AI助手");

        // 验证 complete 事件
        List<String[]> completeEvents = events.stream()
                .filter(e -> "complete".equals(e[0])).toList();
        assertThat(completeEvents).hasSize(1);
        assertThat(completeEvents.get(0)[1]).contains("\"done\" : true");
        assertThat(completeEvents.get(0)[1]).contains("\"finishReason\" : \"stop\"");

        // 验证数据库落库：user message + assistant message
        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_message WHERE session_id IN (SELECT id FROM ai_chat_session WHERE agent_id = ?)",
                Integer.class, testAgentId);
        assertThat(msgCount).isEqualTo(2);

        // 验证 user 和 assistant 消息都存在
        Integer userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_message WHERE session_id IN (SELECT id FROM ai_chat_session WHERE agent_id = ?) AND role = 'user'",
                Integer.class, testAgentId);
        assertThat(userCount).isEqualTo(1);

        Integer assistantCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_message WHERE session_id IN (SELECT id FROM ai_chat_session WHERE agent_id = ?) AND role = 'assistant'",
                Integer.class, testAgentId);
        assertThat(assistantCount).isEqualTo(1);
    }

    // ==================== Scenario 3: 已有会话继续对话 ====================

    @Test
    @DisplayName("POST /api/v1/chat/send — 已有会话继续对话")
    void should_continueConversation_when_sessionExists() throws Exception {
        Long sessionId = insertTestConversation(testAgentId);
        // 插入一条历史 user message
        long histMsgId = System.currentTimeMillis() % 100000 + 500;
        jdbcTemplate.update(
                "INSERT INTO ai_chat_message (id, session_id, workspace_id, role, content, creator_id) " +
                "VALUES (?, ?, 1, 'user', 'Previous message', 1)", histMsgId, sessionId);

        when(mockAdapter.streamChat(any(), any()))
                .thenReturn(stubStream(List.of("收到，继续")));

        String body = "{\"sessionId\": " + sessionId + ", \"content\": \"Continue\"}";

        MvcResult mvcResult = mockMvc.perform(post("/api/v1/chat/send")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult asyncResult = mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andReturn();

        String content = asyncResult.getResponse().getContentAsString(StandardCharsets.UTF_8);
        List<String[]> events = parseSseEvents(content);

        assertThat(events.stream().filter(e -> "message".equals(e[0])).count()).isEqualTo(1);
        assertThat(events.stream().filter(e -> "complete".equals(e[0])).count()).isEqualTo(1);

        // 验证新增了 2 条消息（1 user + 1 assistant），加上原有 1 条 = 3 条
        Integer msgCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ai_chat_message WHERE session_id = ?",
                Integer.class, sessionId);
        assertThat(msgCount).isEqualTo(3);
    }

    // ==================== Scenario 4: 查询会话消息历史 ====================

    @Test
    @DisplayName("GET /api/v1/chat/conversations/{id}/messages — 查询消息历史")
    void should_returnMessages_when_conversationExists() throws Exception {
        Long sessionId = insertTestConversation(testAgentId);
        // 插入 3 条消息
        jdbcTemplate.update(
                "INSERT INTO ai_chat_message (id, session_id, workspace_id, role, content, creator_id) " +
                "VALUES (?, ?, 1, 'user', 'Q1', 1)", sessionId * 10 + 1, sessionId);
        jdbcTemplate.update(
                "INSERT INTO ai_chat_message (id, session_id, workspace_id, role, content, creator_id) " +
                "VALUES (?, ?, 1, 'assistant', 'A1', 1)", sessionId * 10 + 2, sessionId);
        jdbcTemplate.update(
                "INSERT INTO ai_chat_message (id, session_id, workspace_id, role, content, creator_id) " +
                "VALUES (?, ?, 1, 'user', 'Q2', 1)", sessionId * 10 + 3, sessionId);

        MvcResult result = mockMvc.perform(get("/api/v1/chat/conversations/{conversationId}/messages", sessionId)
                        .header("Authorization", "Bearer " + authToken)
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

        JsonNode data = node.get("data");
        assertThat(data.get("list").size()).isEqualTo(3);
        assertThat(data.get("total").asLong()).isEqualTo(3);
    }

    // ==================== Scenario 5: 工作空间隔离 ====================

    @Test
    @DisplayName("GET /api/v1/chat/conversations/{id}/messages — 跨工作空间访问返回 NOT_FOUND")
    void should_returnNotFound_when_crossWorkspaceAccess() throws Exception {
        Long sessionId = insertTestConversation(testAgentId);

        // 用 workspace_id=2 的 token 访问 workspace_id=1 的会话
        String otherWorkspaceToken = createTestToken(1L, 2L);

        MvcResult result = mockMvc.perform(get("/api/v1/chat/conversations/{conversationId}/messages", sessionId)
                        .header("Authorization", "Bearer " + otherWorkspaceToken)
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        assertThat(node.get("data").isNull()).isTrue();
    }

    // ==================== Scenario 6: 并发发送防护 ====================

    @Test
    @DisplayName("POST /api/v1/chat/send — 同一会话并发请求被拒绝")
    @SuppressWarnings("unchecked")
    void should_rejectConcurrentSend_when_sessionIsProcessing() throws Exception {
        Long sessionId = insertTestConversation(testAgentId);

        ChatService chatService = wac.getBean(ChatService.class);
        // CGLIB proxy 通过 Objenesis 创建时会跳过字段初始化，需要手动初始化 activeSessions
        java.util.concurrent.ConcurrentHashMap<Long, Boolean> activeSessions =
                (java.util.concurrent.ConcurrentHashMap<Long, Boolean>)
                        ReflectionTestUtils.getField(chatService, "activeSessions");
        if (activeSessions == null) {
            activeSessions = new java.util.concurrent.ConcurrentHashMap<>();
            ReflectionTestUtils.setField(chatService, "activeSessions", activeSessions);
        }
        activeSessions.put(sessionId, Boolean.TRUE);

        try {
            String body = "{\"sessionId\": " + sessionId + ", \"content\": \"Should be rejected\"}";

            MvcResult result = mockMvc.perform(post("/api/v1/chat/send")
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.getCode());
            assertThat(node.get("message").asText()).contains("正在处理中");
        } finally {
            activeSessions.remove(sessionId);
        }
    }
}
