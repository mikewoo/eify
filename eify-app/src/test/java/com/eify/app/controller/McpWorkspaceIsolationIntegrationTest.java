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
        long id = Math.abs(System.currentTimeMillis() % 100000) + (workspaceId * 1000L);
        jdbcTemplate.update(
                "INSERT INTO mcp_server (id, name, endpoint, enabled, workspace_id) VALUES (?, ?, ?, 1, ?)",
                id, name, endpoint, workspaceId);
        return id;
    }

    private Long insertTool(String name, Long serverId, int workspaceId) {
        long id = Math.abs(System.currentTimeMillis() % 100000) + (workspaceId * 1000L) + 100;
        jdbcTemplate.update(
                "INSERT INTO mcp_tool (id, server_id, name, input_schema, workspace_id) VALUES (?, ?, ?, '{}', ?)",
                id, serverId, name, workspaceId);
        return id;
    }

    private Long insertAgent(String name, int workspaceId) {
        long id = Math.abs(System.currentTimeMillis() % 100000) + (workspaceId * 1000L) + 300;
        jdbcTemplate.update(
                "INSERT INTO ai_agent (id, workspace_id, name, default_provider_id, default_model, system_prompt, enabled) " +
                "VALUES (?, ?, ?, 1, 'gpt-4', 'You are a helpful assistant.', 1)",
                id, workspaceId, name);
        return id;
    }
}
