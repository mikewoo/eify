package com.eify.app.controller;

import cn.hutool.jwt.JWTUtil;
import com.eify.common.error.ErrorCode;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("ProviderController 集成测试")
class ProviderControllerIntegrationTest {

    private static final String JWT_SECRET = "test-eify-jwt-secret";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;
    private String authToken;
    private final List<Long> createdProviderIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(wac.getBean(com.eify.app.security.JwtAuthFilter.class))
                .build();
        authToken = createTestToken(1L, 1L);
    }

    @AfterEach
    void tearDown() {
        for (Long id : createdProviderIds) {
            jdbcTemplate.update("DELETE FROM provider WHERE id = ?", id);
        }
        createdProviderIds.clear();
    }

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

    private Long extractId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("data").get("id").asLong();
    }

    private MvcResult performPost(String body) throws Exception {
        return mockMvc.perform(post("/api/v1/providers")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
    }

    // ==================== Scenario 1: 创建 ====================

    @Nested
    @DisplayName("POST /api/v1/providers — 创建供应商")
    class CreateProvider {

        @Test
        @DisplayName("合法请求创建成功，返回 code=200 且 data.id 有值")
        void should_createProvider_when_requestIsValid() throws Exception {
            // Given
            String body = """
                    {
                        "name": "test-create-%d",
                        "type": "OPENAI",
                        "baseUrl": "https://api.test.com",
                        "authConfig": {"api_key": "sk-test-create"},
                        "enabled": 1
                    }
                    """.formatted(System.currentTimeMillis());

            // When
            MvcResult result = performPost(body);

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            JsonNode data = node.get("data");
            assertThat(data.get("id").asLong()).isPositive();
            assertThat(data.get("name").asText()).startsWith("test-create-");
            assertThat(data.get("type").asText()).isEqualTo("OPENAI");
            assertThat(data.get("baseUrl").asText()).isEqualTo("https://api.test.com");
            assertThat(data.get("enabled").asInt()).isEqualTo(1);

            createdProviderIds.add(data.get("id").asLong());

            // 验证数据库落库
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM provider WHERE id = ? AND deleted = 0",
                    Integer.class, data.get("id").asLong());
            assertThat(count).isEqualTo(1);
        }
    }

    // ==================== Scenario 2: 重复名称 ====================

    @Nested
    @DisplayName("POST /api/v1/providers — 重复名称")
    class DuplicateName {

        @Test
        @DisplayName("同名创建失败，返回 code=1001 (PARAM_ERROR)")
        void should_rejectDuplicate_when_nameAlreadyExists() throws Exception {
            // Given — 先创建一个
            String name = "test-dup-" + System.currentTimeMillis();
            String body = """
                    {
                        "name": "%s",
                        "type": "OPENAI",
                        "baseUrl": "https://api.test.com",
                        "authConfig": {"api_key": "sk-test"},
                        "enabled": 1
                    }
                    """.formatted(name);

            MvcResult first = performPost(body);
            Long id = extractId(first);
            createdProviderIds.add(id);

            // When — 同名再创建
            MvcResult result = performPost(body);

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt())
                    .isEqualTo(ErrorCode.PARAM_ERROR.getCode());
            assertThat(node.get("message").asText()).contains("已存在");
        }
    }

    // ==================== Scenario 3: 查询存在的记录 ====================

    @Nested
    @DisplayName("GET /api/v1/providers/{id} — 查询供应商")
    class GetProvider {

        @Test
        @DisplayName("查询存在的记录返回完整字段")
        void should_returnProvider_when_idExists() throws Exception {
            // Given — 创建测试数据
            String name = "test-query-" + System.currentTimeMillis();
            String createBody = """
                    {
                        "name": "%s",
                        "type": "OPENAI",
                        "baseUrl": "https://api.query-test.com",
                        "authConfig": {"api_key": "sk-query"},
                        "enabled": 1
                    }
                    """.formatted(name);

            MvcResult createResult = performPost(createBody);
            Long id = extractId(createResult);
            createdProviderIds.add(id);

            // When
            MvcResult result = mockMvc.perform(get("/api/v1/providers/{id}", id)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            JsonNode data = node.get("data");
            assertThat(data.get("id").asLong()).isEqualTo(id);
            assertThat(data.get("name").asText()).isEqualTo(name);
            assertThat(data.get("type").asText()).isEqualTo("OPENAI");
            assertThat(data.get("baseUrl").asText()).isEqualTo("https://api.query-test.com");
            assertThat(data.get("enabled").asInt()).isEqualTo(1);
            assertThat(data.has("createdAt")).isTrue();
            assertThat(data.has("updatedAt")).isTrue();
        }
    }

    // ==================== Scenario 4: 查询不存在的记录 ====================

    @Nested
    @DisplayName("GET /api/v1/providers/{id} — 查询不存在")
    class GetMissing {

        @Test
        @DisplayName("查询不存在的记录返回 code=1004 (NOT_FOUND)")
        void should_returnNotFound_when_idNotExists() throws Exception {
            // When
            MvcResult result = mockMvc.perform(get("/api/v1/providers/{id}", 99999L)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt())
                    .isEqualTo(ErrorCode.NOT_FOUND.getCode());
            assertThat(node.get("data").isNull()).isTrue();
        }
    }

    // ==================== Scenario 5: 更新 ====================

    @Nested
    @DisplayName("PUT /api/v1/providers/{id} — 更新供应商")
    class UpdateProvider {

        @Test
        @DisplayName("更新成功后数据库 name 确实变了")
        void should_updateName_when_requestIsValid() throws Exception {
            // Given — 创建测试数据
            String originalName = "test-update-orig-" + System.currentTimeMillis();
            String createBody = """
                    {
                        "name": "%s",
                        "type": "OPENAI",
                        "baseUrl": "https://api.update-test.com",
                        "authConfig": {"api_key": "sk-update"},
                        "enabled": 1
                    }
                    """.formatted(originalName);

            MvcResult createResult = performPost(createBody);
            Long id = extractId(createResult);
            createdProviderIds.add(id);

            // When
            String updateBody = """
                    {
                        "name": "test-update-changed-%d"
                    }
                    """.formatted(System.currentTimeMillis());

            MvcResult result = mockMvc.perform(put("/api/v1/providers/{id}", id)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(updateBody))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then — 验证响应
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            assertThat(node.get("data").get("name").asText()).startsWith("test-update-changed-");

            // Then — 验证数据库确实变了
            String dbName = jdbcTemplate.queryForObject(
                    "SELECT name FROM provider WHERE id = ? AND deleted = 0",
                    String.class, id);
            assertThat(dbName).startsWith("test-update-changed-");
            assertThat(dbName).isNotEqualTo(originalName);
        }
    }

    // ==================== Scenario 6: 删除 ====================

    @Nested
    @DisplayName("DELETE /api/v1/providers/{id} — 删除供应商")
    class DeleteProvider {

        @Test
        @DisplayName("删除后数据库 deleted=1（软删除）")
        void should_softDelete_when_idExists() throws Exception {
            // Given — 创建测试数据
            String name = "test-delete-" + System.currentTimeMillis();
            String createBody = """
                    {
                        "name": "%s",
                        "type": "OPENAI",
                        "baseUrl": "https://api.delete-test.com",
                        "authConfig": {"api_key": "sk-delete"},
                        "enabled": 1
                    }
                    """.formatted(name);

            MvcResult createResult = performPost(createBody);
            Long id = extractId(createResult);
            createdProviderIds.add(id);

            // When
            MvcResult result = mockMvc.perform(delete("/api/v1/providers/{id}", id)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then — 响应成功
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            // Then — 数据库里 deleted=1（软删除）
            Integer deleted = jdbcTemplate.queryForObject(
                    "SELECT deleted FROM provider WHERE id = ?",
                    Integer.class, id);
            assertThat(deleted).isEqualTo(1);

            // 从 cleanup 列表中移除（已删除，无需再删）
            createdProviderIds.remove(id);
        }
    }

    // ==================== IT-1: 分页查询带筛选 ====================

    @Nested
    @DisplayName("GET /api/v1/providers — 分页查询")
    class ListProviders {

        @Test
        @DisplayName("默认分页返回列表，支持 ?name= 模糊筛选")
        void should_filterByName_when_nameProvided() throws Exception {
            // Given — 创建两个不同名称的供应商
            String suffix = String.valueOf(System.currentTimeMillis());
            String nameA = "test-list-alpha-" + suffix;
            String nameB = "test-list-beta-" + suffix;

            String bodyA = """
                    {"name":"%s","type":"OPENAI","baseUrl":"https://a.test.com","authConfig":{"api_key":"sk-a"},"enabled":1}
                    """.formatted(nameA);
            String bodyB = """
                    {"name":"%s","type":"ANTHROPIC","baseUrl":"https://b.test.com","authConfig":{"api_key":"sk-b"},"enabled":1}
                    """.formatted(nameB);

            Long idA = extractId(performPost(bodyA));
            Long idB = extractId(performPost(bodyB));
            createdProviderIds.add(idA);
            createdProviderIds.add(idB);

            // When — 按名称模糊搜索
            MvcResult result = mockMvc.perform(get("/api/v1/providers")
                            .header("Authorization", "Bearer " + authToken)
                            .param("name", "test-list-alpha")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            JsonNode data = node.get("data");
            assertThat(data.get("total").asInt()).isGreaterThanOrEqualTo(1);
            assertThat(data.get("list").toString()).contains(nameA);
            assertThat(data.get("list").toString()).doesNotContain(nameB);
        }

        @Test
        @DisplayName("按 type 筛选只返回匹配类型")
        void should_filterByType_when_typeProvided() throws Exception {
            // When
            MvcResult result = mockMvc.perform(get("/api/v1/providers")
                            .header("Authorization", "Bearer " + authToken)
                            .param("type", "OLLAMA")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            JsonNode list = node.get("data").get("list");
            for (JsonNode item : list) {
                assertThat(item.get("type").asText()).isEqualTo("OLLAMA");
            }
        }

        @Test
        @DisplayName("按 enabled 筛选只返回匹配状态")
        void should_filterByEnabled_when_enabledProvided() throws Exception {
            // When
            MvcResult result = mockMvc.perform(get("/api/v1/providers")
                            .header("Authorization", "Bearer " + authToken)
                            .param("enabled", "1")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            JsonNode list = node.get("data").get("list");
            for (JsonNode item : list) {
                assertThat(item.get("enabled").asInt()).isEqualTo(1);
            }
        }
    }

    // ==================== IT-2: 连通性测试 ====================

    @Nested
    @DisplayName("POST /api/v1/providers/{id}/test-connection — 连通性测试")
    class TestConnection {

        @Test
        @DisplayName("连通性测试返回结果（包含 success、latencyMs、errorMessage 字段）")
        void should_returnConnectionResult_when_providerExists() throws Exception {
            // Given — 创建测试供应商
            String suffix = String.valueOf(System.currentTimeMillis());
            String body = """
                    {"name":"test-conn-%s","type":"OPENAI","baseUrl":"https://api.conn-test.com","authConfig":{"api_key":"sk-conn"},"enabled":1}
                    """.formatted(suffix);
            Long id = extractId(performPost(body));
            createdProviderIds.add(id);

            // When
            MvcResult result = mockMvc.perform(post("/api/v1/providers/{id}/test-connection", id)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then — 无论连通成功还是失败，结构必须完整
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            JsonNode data = node.get("data");
            assertThat(data.has("success")).isTrue();
            assertThat(data.has("latencyMs")).isTrue();
            // errorMessage 可能为 null，但字段应存在
            assertThat(data.has("errorMessage")).isTrue();
        }
    }

    // ==================== IT-3: 获取模型列表 ====================

    @Nested
    @DisplayName("GET /api/v1/providers/{id}/models — 获取模型列表")
    class GetModels {

        @Test
        @DisplayName("查询模型列表返回数组，支持 ?category=1 过滤")
        void should_returnModelList_when_providerExists() throws Exception {
            // Given — 使用 seed 数据中的默认 provider (id=1)
            // When — 不过滤 category
            MvcResult result = mockMvc.perform(get("/api/v1/providers/{id}/models", 1L)
                            .header("Authorization", "Bearer " + authToken))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            assertThat(node.get("data").isArray()).isTrue();
        }

        @Test
        @DisplayName("按 category=1 过滤只返回 EMBEDDING 模型")
        void should_filterByCategory_when_categoryProvided() throws Exception {
            // When
            MvcResult result = mockMvc.perform(get("/api/v1/providers/{id}/models", 1L)
                            .header("Authorization", "Bearer " + authToken)
                            .param("category", "1"))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());
            assertThat(node.get("data").isArray()).isTrue();
            for (JsonNode model : node.get("data")) {
                assertThat(model.get("category").asInt()).isEqualTo(1);
            }
        }
    }

    // ==================== IT-4: 手动添加模型 ====================

    @Nested
    @DisplayName("POST /api/v1/providers/{id}/models — 手动添加模型")
    class CreateModel {

        @Test
        @DisplayName("合法请求创建模型成功，返回完整字段")
        void should_createModel_when_requestIsValid() throws Exception {
            // Given — 创建测试供应商
            String suffix = String.valueOf(System.currentTimeMillis());
            String createBody = """
                    {"name":"test-model-prov-%s","type":"OPENAI","baseUrl":"https://api.model-test.com","authConfig":{"api_key":"sk-model"},"enabled":1}
                    """.formatted(suffix);
            Long providerId = extractId(performPost(createBody));
            createdProviderIds.add(providerId);

            // When — manual model creation without extraParams (CLOB compat in H2)
            String modelBody = """
                    {
                        "modelId": "test-embedding-model-%s",
                        "displayName": "Test Embedding Model %s",
                        "category": 1
                    }
                    """.formatted(suffix, suffix);

            MvcResult result = mockMvc.perform(post("/api/v1/providers/{id}/models", providerId)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(modelBody))
                    .andExpect(status().isOk())
                    .andReturn();

            // Then
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            assertThat(node.get("code").asInt()).isEqualTo(ErrorCode.SUCCESS.getCode());

            JsonNode data = node.get("data");
            assertThat(data.get("id").asLong()).isPositive();
            assertThat(data.get("modelName").asText()).startsWith("test-embedding-model-");
            assertThat(data.get("displayName").asText()).startsWith("Test Embedding Model ");
            assertThat(data.get("category").asInt()).isEqualTo(1);
        }

        @Test
        @DisplayName("缺少必填字段返回校验错误")
        void should_reject_when_requiredFieldMissing() throws Exception {
            // Given — 使用 seed 默认 provider (id=1)
            String body = """
                    {"category": 1}
                    """;

            // When
            MvcResult result = mockMvc.perform(post("/api/v1/providers/{id}/models", 1L)
                            .header("Authorization", "Bearer " + authToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andReturn();

            // Then — 返回 400 或业务错误
            JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
            // 参数校验失败，code != SUCCESS
            assertThat(node.get("code").asInt()).isNotEqualTo(ErrorCode.SUCCESS.getCode());
        }
    }
}
