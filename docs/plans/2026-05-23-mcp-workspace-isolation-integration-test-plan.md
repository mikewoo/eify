# MCP Workspace Isolation Integration Test Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add automated integration tests for MCP workspace isolation that run via `mvn test` with H2 in-memory database.

**Architecture:** Follow existing `ProviderControllerIntegrationTest` / `ChatControllerIntegrationTest` patterns: `@SpringBootTest(RANDOM_PORT)` + `@ActiveProfiles("test")` + MockMvc with JWT tokens for workspace 1 and 2. Test data inserted via JdbcTemplate, cleaned up in `@AfterEach`. `McpClientService` mocked via `@MockitoBean` for debugTool tests.

**Tech Stack:** JUnit 5, MockMvc, H2 (MySQL mode), JdbcTemplate, AssertJ, Hutool JWT

---

### Task 1: Update H2 schema with V6 MCP indexes and constraints

**Files:**
- Modify: `eify-app/src/test/resources/schema-h2.sql`

- [ ] **Step 1: Fix agent_mcp_tool table and add V6 indexes to schema-h2.sql**

The H2 schema has three issues:
1. `agent_mcp_tool` table is missing `updated_at`, `deleted`, `creator_id` columns needed by MyBatis-Plus BaseEntity
2. `agent_mcp_tool` UNIQUE key uses `(agent_id, tool_id)` without `workspace_id` — must match V6 migration
3. `mcp_tool` table is missing `idx_workspace_id` and `idx_name_workspace` indexes

Replace the existing `agent_mcp_tool` CREATE TABLE block:

```sql
-- Agent 绑定的 MCP 工具表 (fixed: added updated_at/deleted/creator_id, uk_agent_tool_workspace)
CREATE TABLE IF NOT EXISTS agent_mcp_tool (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id      BIGINT          NOT NULL,
    tool_id       BIGINT          NOT NULL,
    created_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       INT             NOT NULL DEFAULT 0,
    creator_id    BIGINT          NOT NULL DEFAULT 0,
    workspace_id  BIGINT          NOT NULL DEFAULT 1,
    CONSTRAINT uk_agent_tool_workspace UNIQUE (agent_id, tool_id, workspace_id)
);
```

Then RIGHT AFTER the `mcp_tool` CREATE TABLE block, add:

```sql
-- V6: workspace isolation indexes for mcp_tool
CREATE INDEX IF NOT EXISTS idx_workspace_id ON mcp_tool(workspace_id);
CREATE INDEX IF NOT EXISTS idx_name_workspace ON mcp_tool(name, workspace_id);
```

And RIGHT AFTER the `agent_mcp_tool` CREATE TABLE block, add:

```sql
-- V6: workspace isolation index for agent_mcp_tool
CREATE INDEX IF NOT EXISTS idx_workspace_id ON agent_mcp_tool(workspace_id);
```

- [ ] **Step 2: Add workspace 2 to seed data**

In `eify-app/src/test/resources/data-h2.sql`, after the existing workspace seed data:

```sql
-- Second workspace for cross-workspace testing
MERGE INTO ai_workspace (id, name, description) KEY(id)
VALUES (2, 'Personal Workspace', 'Cross-workspace test workspace');

-- Admin also belongs to workspace 2
MERGE INTO ai_workspace_member (workspace_id, user_id, role) KEY(workspace_id, user_id)
VALUES (2, 1, 'owner');
```

- [ ] **Step 3: Verify the schema change compiles**

Run: `cd eify-app && mvn test -Dtest=ChatControllerIntegrationTest -pl eify-app -am -q`
Expected: PASS (existing tests still work with schema changes)

- [ ] **Step 4: Commit**

```bash
git add eify-app/src/test/resources/schema-h2.sql eify-app/src/test/resources/data-h2.sql
git commit -m "test: add V6 MCP workspace isolation indexes to H2 schema and second workspace seed data"
```

---

### Task 2: Write McpWorkspaceIsolationIntegrationTest

**Files:**
- Create: `eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java`

- [ ] **Step 1: Write the test class skeleton with setup/teardown**

```java
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
        long id = System.currentTimeMillis() % 100000 + (workspaceId * 1000);
        jdbcTemplate.update(
                "INSERT INTO mcp_server (id, name, endpoint, enabled, workspace_id) VALUES (?, ?, ?, 1, ?)",
                id, name, endpoint, workspaceId);
        return id;
    }

    private Long insertTool(String name, Long serverId, int workspaceId) {
        long id = System.currentTimeMillis() % 100000 + (workspaceId * 1000) + 100;
        jdbcTemplate.update(
                "INSERT INTO mcp_tool (id, server_id, name, input_schema, workspace_id) VALUES (?, ?, ?, '{}', ?)",
                id, serverId, name, workspaceId);
        return id;
    }

    private Long insertAgent(String name, int workspaceId) {
        long id = System.currentTimeMillis() % 100000 + (workspaceId * 1000) + 300;
        jdbcTemplate.update(
                "INSERT INTO ai_agent (id, workspace_id, name, default_provider_id, default_model, system_prompt, enabled) " +
                "VALUES (?, ?, ?, 1, 'gpt-4', 'You are a helpful assistant.', 1)",
                id, workspaceId, name);
        return id;
    }
}
```

- [ ] **Step 2: Run test to verify H2 context loads**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: PASS (0 tests, context loads successfully)

- [ ] **Step 3: Commit**

```bash
git add eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java
git commit -m "test: add MCP workspace isolation integration test skeleton"
```

---

### Task 3: Implement S1 — Cross-workspace MCP server access

**Files:**
- Modify: `eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java`

- [ ] **Step 1: Write the test**

Add inside the class (after helpers, before closing brace):

```java
// ==================== S1: Cross-workspace MCP server access ====================

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
```

- [ ] **Step 2: Run the tests**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: 3 tests PASS

- [ ] **Step 3: Commit**

```bash
git add eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java
git commit -m "test: add S1 cross-workspace MCP server access tests"
```

---

### Task 4: Implement S2 — Server listing workspace isolation

**Files:**
- Modify: `eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java`

- [ ] **Step 1: Write the test**

Add inside the class:

```java
// ==================== S2: Server listing workspace isolation ====================

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
        // WS1 list should contain WS1 server but not WS2 server
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
```

- [ ] **Step 2: Run the tests**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: 5 tests PASS (3 S1 + 2 S2)

- [ ] **Step 3: Commit**

```bash
git add eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java
git commit -m "test: add S2 server listing workspace isolation tests"
```

---

### Task 5: Implement S3 — Cross-workspace testConnection and debugTool

**Files:**
- Modify: `eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java`

- [ ] **Step 1: Write the test**

Add inside the class:

```java
// ==================== S3: Cross-workspace testConnection and debugTool ====================

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
```

- [ ] **Step 2: Run the tests**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: 7 tests PASS (3 S1 + 2 S2 + 2 S3)

- [ ] **Step 3: Commit**

```bash
git add eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java
git commit -m "test: add S3 cross-workspace testConnection and debugTool rejection tests"
```

---

### Task 6: Implement S4 — Cross-workspace tool binding

**Files:**
- Modify: `eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java`

- [ ] **Step 1: Write the test**

Add inside the class:

```java
// ==================== S4: Cross-workspace tool binding ====================

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
```

- [ ] **Step 2: Run the tests**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: 9 tests PASS (3 S1 + 2 S2 + 2 S3 + 2 S4)

- [ ] **Step 3: Commit**

```bash
git add eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java
git commit -m "test: add S4 cross-workspace tool binding tests"
```

---

### Task 7: Implement S5 — Agent and tool listing isolation

**Files:**
- Modify: `eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java`

- [ ] **Step 1: Write the test**

Add inside the class:

```java
// ==================== S5: Agent and tool listing isolation ====================

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
        toolWs2A = insertTool("mcp-test-getorder", serverWs2, 2); // same name, different workspace

        agentWs1 = insertAgent("mcp-test-agent-ws1-iso", 1);
        agentWs2 = insertAgent("mcp-test-agent-ws2-iso", 2);

        // Bind tools via API (uses CurrentContext)
        // We need to set CurrentContext before binding through API
    }

    @Test
    @DisplayName("WS1 的 agent 详情只包含 WS1 的工具")
    void should_onlyShowWs1Tools_when_getWs1Agent() throws Exception {
        // Bind WS1 tools to WS1 agent via JdbcTemplate
        long bindId1 = System.currentTimeMillis() % 100000 + 5000;
        jdbcTemplate.update(
                "INSERT INTO agent_mcp_tool (id, agent_id, tool_id, workspace_id) VALUES (?, ?, ?, 1)",
                bindId1, agentWs1, toolWs1A);
        long bindId2 = System.currentTimeMillis() % 100000 + 5001;
        jdbcTemplate.update(
                "INSERT INTO agent_mcp_tool (id, agent_id, tool_id, workspace_id) VALUES (?, ?, ?, 1)",
                bindId2, agentWs1, toolWs1B);

        MvcResult result = mockMvc.perform(get("/api/v1/agents/{id}", agentWs1)
                        .header("Authorization", "Bearer " + tokenWs1))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
        // mcpToolIds should contain WS1 tool IDs
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
```

- [ ] **Step 2: Run the tests**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: 12 tests PASS (3 S1 + 2 S2 + 2 S3 + 2 S4 + 3 S5)

- [ ] **Step 3: Commit**

```bash
git add eify-app/src/test/java/com/eify/app/controller/McpWorkspaceIsolationIntegrationTest.java
git commit -m "test: add S5 agent and tool listing workspace isolation tests"
```

---

### Task 8: Run full verification and verify all existing tests

**Files:**
- None (verification only)

- [ ] **Step 1: Run the new integration test**

Run: `mvn test -Dtest=McpWorkspaceIsolationIntegrationTest -pl eify-app -am -q`
Expected: 12 tests in McpWorkspaceIsolationIntegrationTest pass

- [ ] **Step 2: Run existing integration tests to verify no regressions**

Run: `mvn test -pl eify-app -am -q`
Expected: All tests pass (ChatControllerIntegrationTest 6 tests + ProviderControllerIntegrationTest 10 tests + McpWorkspaceIsolationIntegrationTest 12 tests)

- [ ] **Step 3: Run all unit tests**

Run: `mvn test -q`
Expected: All tests pass, no regressions

- [ ] **Step 4: Commit (if any final cleanup needed)**

No changes expected at this step since all code was committed in previous tasks.

---
