package com.eify.app.controller;

import cn.hutool.jwt.JWTUtil;
import com.eify.common.error.ErrorCode;
import com.eify.mcp.service.McpClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("MCP 工作空间隔离集成测试")
class McpWorkspaceIsolationIntegrationTest {

    private static final String JWT_SECRET = "test-eify-jwt-secret";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final java.util.concurrent.atomic.AtomicLong idCounter =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    @MockitoBean
    private McpClientService mcpClientService;

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String tokenWs1;
    private String tokenWs2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(wac.getBean(com.eify.app.security.JwtAuthFilter.class))
                .build();
        tokenWs1 = createTestToken(1L, 1L);
        tokenWs2 = createTestToken(1L, 2L);
    }

    @AfterEach
    void tearDown() {
        // Cleanup in reverse dependency order
        jdbcTemplate.update("DELETE FROM agent_mcp_tool WHERE agent_id IN (SELECT id FROM ai_agent WHERE name LIKE 'mcp-test-%')");
        jdbcTemplate.update("DELETE FROM ai_agent WHERE name LIKE 'mcp-test-%'");
        jdbcTemplate.update("DELETE FROM mcp_tool WHERE name LIKE 'mcp-test-%'");
        jdbcTemplate.update("DELETE FROM mcp_server WHERE name LIKE 'mcp-test-%'");
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

    private Long insertServer(String name, String endpoint, int workspaceId) {
        long id = idCounter.incrementAndGet();
        jdbcTemplate.update(
                "INSERT INTO mcp_server (id, name, endpoint, enabled, workspace_id) VALUES (?, ?, ?, 1, ?)",
                id, name, endpoint, workspaceId);
        return id;
    }

    private Long insertTool(String name, Long serverId, int workspaceId) {
        long id = idCounter.incrementAndGet();
        jdbcTemplate.update(
                "INSERT INTO mcp_tool (id, server_id, name, input_schema, workspace_id) VALUES (?, ?, ?, '{}', ?)",
                id, serverId, name, workspaceId);
        return id;
    }

    private Long insertAgent(String name, int workspaceId) {
        long id = idCounter.incrementAndGet();
        jdbcTemplate.update(
                "INSERT INTO ai_agent (id, workspace_id, name, default_provider_id, default_model, system_prompt, enabled) " +
                "VALUES (?, ?, ?, 1, 'gpt-4', 'You are a helpful assistant.', 1)",
                id, workspaceId, name);
        return id;
    }

    // ==================== S1: 跨工作空间访问 MCP Server ====================

    @Nested
    @DisplayName("S1: GET /api/v1/mcp-servers/{id} — 跨工作空间访问 MCP Server")
    class CrossWorkspaceServerAccess {

        private Long serverWs1;
        private Long serverWs2;

        @BeforeEach
        void setUp() {
            serverWs1 = insertServer("mcp-test-ws1-server", "http://localhost:9001/mcp", 1);
            serverWs2 = insertServer("mcp-test-ws2-server", "http://localhost:9002/mcp", 2);
        }

        @Test
        @DisplayName("WS2 token 访问 WS1 的 server 返回 MCP_SERVER_NOT_FOUND")
        void should_rejectCrossWorkspace_when_ws2AccessesWs1Server() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/mcp-servers/{id}", serverWs1)
                            .header("Authorization", "Bearer " + tokenWs2))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.MCP_SERVER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("WS1 token 访问自己的 server 返回 SUCCESS")
        void should_returnServer_when_sameWorkspace() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/mcp-servers/{id}", serverWs1)
                            .header("Authorization", "Bearer " + tokenWs1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            assertThat(node.get("data").get("name").asText()).isEqualTo("mcp-test-ws1-server");
        }

        @Test
        @DisplayName("WS1 token 访问 WS2 的 server 返回 MCP_SERVER_NOT_FOUND")
        void should_rejectCrossWorkspace_when_ws1AccessesWs2Server() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/mcp-servers/{id}", serverWs2)
                            .header("Authorization", "Bearer " + tokenWs1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.MCP_SERVER_NOT_FOUND.getCode());
        }
    }

    // ==================== S2: 列表工作空间隔离 ====================

    @Nested
    @DisplayName("S2: GET /api/v1/mcp-servers — 列表工作空间隔离")
    class ServerListingIsolation {

        private Long serverWs1;
        private Long serverWs2;

        @BeforeEach
        void setUp() {
            serverWs1 = insertServer("mcp-test-ws1-list", "http://localhost:9001/mcp", 1);
            serverWs2 = insertServer("mcp-test-ws2-list", "http://localhost:9002/mcp", 2);
        }

        @Test
        @DisplayName("WS1 列表只包含 WS1 的 server，不含 WS2 的 server")
        void should_onlyShowWs1Servers_when_ws1Token() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/mcp-servers")
                            .header("Authorization", "Bearer " + tokenWs1)
                            .param("page", "1")
                            .param("pageSize", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            JsonNode list = node.get("data").get("list");
            boolean hasWs1 = false;
            boolean hasWs2 = false;
            for (JsonNode item : list) {
                if (item.get("name").asText().equals("mcp-test-ws1-list")) hasWs1 = true;
                if (item.get("name").asText().equals("mcp-test-ws2-list")) hasWs2 = true;
            }
            assertThat(hasWs1).isTrue();
            assertThat(hasWs2).isFalse();
        }

        @Test
        @DisplayName("WS2 列表只包含 WS2 的 server，不含 WS1 的 server")
        void should_onlyShowWs2Servers_when_ws2Token() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/mcp-servers")
                            .header("Authorization", "Bearer " + tokenWs2)
                            .param("page", "1")
                            .param("pageSize", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode list = node.get("data").get("list");
            boolean hasWs1 = false;
            boolean hasWs2 = false;
            for (JsonNode item : list) {
                if (item.get("name").asText().equals("mcp-test-ws1-list")) hasWs1 = true;
                if (item.get("name").asText().equals("mcp-test-ws2-list")) hasWs2 = true;
            }
            assertThat(hasWs1).isFalse();
            assertThat(hasWs2).isTrue();
        }
    }

    // ==================== S3: 跨工作空间 testConnection 和 debugTool ====================

    @Nested
    @DisplayName("S3: POST /api/v1/mcp-servers/{id}/test + debug → 跨工作空间拒绝")
    class CrossWorkspaceTestAndDebug {

        private Long serverWs1;

        @BeforeEach
        void setUp() {
            serverWs1 = insertServer("mcp-test-ws1-conn", "http://localhost:9001/mcp", 1);
        }

        @Test
        @DisplayName("WS2 对 WS1 server 执行 testConnection 返回 MCP_SERVER_NOT_FOUND")
        void should_rejectTestConnection_when_crossWorkspace() throws Exception {
            MvcResult result = mockMvc.perform(post("/api/v1/mcp-servers/{id}/test", serverWs1)
                            .header("Authorization", "Bearer " + tokenWs2))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.MCP_SERVER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("WS2 对 WS1 server 执行 debugTool 返回 MCP_SERVER_NOT_FOUND")
        void should_rejectDebugTool_when_crossWorkspace() throws Exception {
            String body = "{\"toolName\":\"query_order\",\"arguments\":{\"orderId\":\"12345\"}}";

            MvcResult result = mockMvc.perform(post("/api/v1/mcp-servers/{id}/debug", serverWs1)
                            .header("Authorization", "Bearer " + tokenWs2)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.MCP_SERVER_NOT_FOUND.getCode());
        }
    }

    // ==================== S4: 跨工作空间工具绑定 ====================

    @Nested
    @DisplayName("S4: PUT /api/v1/agents/{id}/tools — 跨工作空间绑定工具")
    class CrossWorkspaceToolBinding {

        private Long toolWs1;
        private Long toolWs2;
        private Long agentWs1;

        @BeforeEach
        void setUp() {
            Long serverWs1 = insertServer("mcp-test-ws1-bind", "http://localhost:9001/mcp", 1);
            Long serverWs2 = insertServer("mcp-test-ws2-bind", "http://localhost:9002/mcp", 2);
            toolWs1 = insertTool("mcp-test-tool-ws1", serverWs1, 1);
            toolWs2 = insertTool("mcp-test-tool-ws2", serverWs2, 2);
            agentWs1 = insertAgent("mcp-test-agent-ws1", 1);
        }

        @Test
        @DisplayName("WS1 的 agent 绑定 WS2 的工具返回 MCP_TOOL_NOT_FOUND")
        void should_rejectBinding_when_crossWorkspace() throws Exception {
            String body = "{\"toolIds\":[" + toolWs2 + "]}";

            MvcResult result = mockMvc.perform(put("/api/v1/agents/{id}/tools", agentWs1)
                            .header("Authorization", "Bearer " + tokenWs1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.MCP_TOOL_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("WS1 的 agent 绑定 WS1 自己的工具成功")
        void should_allowBinding_when_sameWorkspace() throws Exception {
            String body = "{\"toolIds\":[" + toolWs1 + "]}";

            MvcResult result = mockMvc.perform(put("/api/v1/agents/{id}/tools", agentWs1)
                            .header("Authorization", "Bearer " + tokenWs1)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
        }
    }

    // ==================== S5: Agent 工具列表隔离 ====================

    @Nested
    @DisplayName("S5: GET /api/v1/agents/{id} — Agent 绑定的工具只包含本工作空间的")
    class AgentToolListingIsolation {

        private Long agentWs1;
        private Long agentWs2;
        private Long toolWs1A;
        private Long toolWs1B;
        private Long toolWs2A;

        @BeforeEach
        void setUp() {
            Long serverWs1 = insertServer("mcp-test-ws1-agent-srv", "http://localhost:9001/mcp", 1);
            Long serverWs2 = insertServer("mcp-test-ws2-agent-srv", "http://localhost:9002/mcp", 2);
            toolWs1A = insertTool("mcp-test-getorder", serverWs1, 1);
            toolWs1B = insertTool("mcp-test-checkinv", serverWs1, 1);
            toolWs2A = insertTool("mcp-test-getorder", serverWs2, 2);

            agentWs1 = insertAgent("mcp-test-agent-ws1-iso", 1);
            agentWs2 = insertAgent("mcp-test-agent-ws2-iso", 2);
        }

        @Test
        @DisplayName("WS1 的 agent 详情只包含 WS1 的工具")
        void should_onlyShowWs1Tools_when_getWs1Agent() throws Exception {
            // Bind WS1 tools to WS1 agent via JdbcTemplate
            long bindId1 = Math.abs(System.currentTimeMillis() % 100000) + 5000;
            long bindId2 = Math.abs(System.currentTimeMillis() % 100000) + 5001;
            jdbcTemplate.update(
                    "INSERT INTO agent_mcp_tool (id, agent_id, tool_id, workspace_id) VALUES (?, ?, ?, 1)",
                    bindId1, agentWs1, toolWs1A);
            jdbcTemplate.update(
                    "INSERT INTO agent_mcp_tool (id, agent_id, tool_id, workspace_id) VALUES (?, ?, ?, 1)",
                    bindId2, agentWs1, toolWs1B);

            MvcResult result = mockMvc.perform(get("/api/v1/agents/{id}", agentWs1)
                            .header("Authorization", "Bearer " + tokenWs1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            String toolIds = node.get("data").get("mcpToolIds").toString();
            assertThat(toolIds).contains(String.valueOf(toolWs1A));
            assertThat(toolIds).contains(String.valueOf(toolWs1B));
            assertThat(toolIds).doesNotContain(String.valueOf(toolWs2A));
        }

        @Test
        @DisplayName("WS1 不能访问 WS2 的 agent")
        void should_returnNotFound_when_crossWorkspaceAgentAccess() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/agents/{id}", agentWs2)
                            .header("Authorization", "Bearer " + tokenWs1))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("Agent 列表按工作空间隔离")
        void should_onlyShowOwnWorkspaceAgents() throws Exception {
            MvcResult result = mockMvc.perform(get("/api/v1/agents")
                            .header("Authorization", "Bearer " + tokenWs1)
                            .param("page", "1")
                            .param("pageSize", "50"))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            JsonNode list = node.get("data").get("list");
            boolean hasWs1Agent = false;
            boolean hasWs2Agent = false;
            for (JsonNode item : list) {
                if (item.get("name").asText().equals("mcp-test-agent-ws1-iso")) hasWs1Agent = true;
                if (item.get("name").asText().equals("mcp-test-agent-ws2-iso")) hasWs2Agent = true;
            }
            assertThat(hasWs1Agent).isTrue();
            assertThat(hasWs2Agent).isFalse();
        }
    }
}
