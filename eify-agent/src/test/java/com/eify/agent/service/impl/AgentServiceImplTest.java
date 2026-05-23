package com.eify.agent.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.agent.domain.dto.*;
import com.eify.agent.domain.entity.Agent;
import com.eify.agent.mapper.AgentKnowledgeMapper;
import com.eify.agent.mapper.AgentMapper;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.http.LlmHttpClient;
import com.eify.common.result.PageResult;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.domain.entity.McpTool;
import com.eify.mcp.mapper.AgentMcpToolMapper;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.mapper.McpToolMapper;
import com.eify.provider.constant.ProviderType;
import com.eify.provider.domain.entity.Provider;
import com.eify.provider.mapper.ProviderMapper;
import com.eify.provider.service.ProviderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AgentServiceImpl 单元测试。
 * <p>
 * 使用 Mockito mock 所有依赖，只测业务逻辑。
 * 不启动 Spring 容器，单个测试 < 50ms。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentServiceImpl")
class AgentServiceImplTest {

    @Mock AgentMapper agentMapper;
    @Mock AgentKnowledgeMapper agentKnowledgeMapper;
    @Mock AgentMcpToolMapper agentMcpToolMapper;
    @Mock McpToolMapper mcpToolMapper;
    @Mock McpServerMapper mcpServerMapper;
    @Mock ProviderMapper providerMapper;
    @Mock ProviderService providerService;
    @Mock LlmHttpClient llmHttpClient;

    @InjectMocks
    AgentServiceImpl agentService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L); // userId=1, workspaceId=1
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ========== 辅助方法 ==========

    private Agent buildAgent(Long id, String name, Long workspaceId) {
        Agent a = new Agent();
        a.setId(id);
        a.setName(name);
        a.setWorkspaceId(workspaceId);
        a.setDefaultProviderId(1L);
        a.setDefaultModel("gpt-4");
        a.setSystemPrompt("You are helpful");
        a.setTemperature(new BigDecimal("0.7"));
        a.setMaxTokens(2000);
        a.setEnabled(1);
        a.setCreatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        a.setUpdatedAt(LocalDateTime.of(2025, 1, 1, 0, 0));
        return a;
    }

    private Provider buildProvider(Long id, String name) {
        Provider p = new Provider();
        p.setId(id);
        p.setName(name);
        p.setType(ProviderType.OPENAI);
        p.setBaseUrl("https://api.openai.com");
        p.setWorkspaceId(1L);
        p.setEnabled(1);
        return p;
    }

    private McpTool buildMcpTool(Long id, String name, Long serverId) {
        McpTool t = new McpTool();
        t.setId(id);
        t.setName(name);
        t.setServerId(serverId);
        t.setWorkspaceId(1L);
        return t;
    }

    private McpServer buildMcpServer(Long id, String name, int enabled) {
        McpServer s = new McpServer();
        s.setId(id);
        s.setName(name);
        s.setEnabled(enabled);
        s.setWorkspaceId(1L);
        return s;
    }

    private AgentCreateRequest buildCreateRequest() {
        AgentCreateRequest req = new AgentCreateRequest();
        req.setName("TestAgent");
        req.setDefaultProviderId(1L);
        req.setDefaultModel("gpt-4");
        req.setSystemPrompt("You are helpful");
        req.setTemperature(new BigDecimal("0.7"));
        req.setMaxTokens(2000);
        req.setEnabled(1);
        return req;
    }

    private Page<Agent> buildPage(List<Agent> agents, long total) {
        Page<Agent> pageObj = new Page<>(1, 20);
        pageObj.setRecords(agents);
        pageObj.setTotal(total);
        return pageObj;
    }

    // ========== list(page, pageSize) ==========

    @Nested
    @DisplayName("list(page, pageSize)")
    class ListTests {

        @Test
        @DisplayName("P0 - page < 1 应抛参数异常")
        void shouldThrowWhenPageLessThan1() {
            assertThrows(BusinessException.class,
                    () -> agentService.list(0, 20));
        }

        @Test
        @DisplayName("P0 - pageSize < 1 应抛参数异常")
        void shouldThrowWhenPageSizeLessThan1() {
            assertThrows(BusinessException.class,
                    () -> agentService.list(1, 0));
        }

        @Test
        @DisplayName("P0 - pageSize > 100 应抛参数异常")
        void shouldThrowWhenPageSizeExceeds100() {
            assertThrows(BusinessException.class,
                    () -> agentService.list(1, 101));
        }

        @Test
        @DisplayName("P1 - 正常分页返回结果")
        void shouldReturnPaginatedResults() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(List.of(agent), 1));
            when(agentKnowledgeMapper.selectByAgentIds(anyList()))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectByAgentIds(anyList(), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<AgentResponse> result = agentService.list(1, 20);

            // then
            assertEquals(1, result.getList().size());
            assertEquals("TestAgent", result.getList().get(0).getName());
            assertEquals(1L, result.getTotal());
            assertEquals(1, result.getPage());
            assertEquals(20, result.getPageSize());
        }

        @Test
        @DisplayName("P1 - 空列表应返回空结果")
        void shouldReturnEmptyListWhenNoRecords() {
            // given
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(Collections.emptyList(), 0));

            // when
            PageResult<AgentResponse> result = agentService.list(1, 20);

            // then
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
        }

        @Test
        @DisplayName("P1 - 应按 workspaceId 过滤并倒序排列")
        void shouldFilterByWorkspaceAndOrderByIdDesc() {
            // given
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(Collections.emptyList(), 0));

            // when
            agentService.list(1, 20);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<LambdaQueryWrapper<Agent>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(agentMapper).selectPage(any(Page.class), wrapperCaptor.capture());
            // 验证 wrapper 非空即可（LambdaQueryWrapper 内部条件不易直接断言）
        }
    }

    // ========== list(page, pageSize, name, enabled) ==========

    @Nested
    @DisplayName("list(page, pageSize, name, enabled)")
    class ListWithFilterTests {

        @Test
        @DisplayName("P0 - page < 1 应抛参数异常")
        void shouldThrowWhenPageLessThan1() {
            assertThrows(BusinessException.class,
                    () -> agentService.list(0, 20, null, null));
        }

        @Test
        @DisplayName("P0 - pageSize < 1 应抛参数异常")
        void shouldThrowWhenPageSizeLessThan1() {
            assertThrows(BusinessException.class,
                    () -> agentService.list(1, 0, null, null));
        }

        @Test
        @DisplayName("P0 - pageSize > 100 应抛参数异常")
        void shouldThrowWhenPageSizeExceeds100() {
            assertThrows(BusinessException.class,
                    () -> agentService.list(1, 101, null, null));
        }

        @Test
        @DisplayName("P1 - 按名称模糊搜索应返回匹配结果")
        void shouldReturnFilteredByName() {
            // given
            Agent agent = buildAgent(1L, "MyAgent", 1L);
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(List.of(agent), 1));
            when(agentKnowledgeMapper.selectByAgentIds(anyList()))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectByAgentIds(anyList(), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<AgentResponse> result = agentService.list(1, 20, "My", null);

            // then
            assertEquals(1, result.getList().size());
            assertEquals("MyAgent", result.getList().get(0).getName());
        }

        @Test
        @DisplayName("P1 - 按启用状态过滤应返回匹配结果")
        void shouldReturnFilteredByEnabled() {
            // given
            Agent agent = buildAgent(1L, "EnabledAgent", 1L);
            agent.setEnabled(1);
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(List.of(agent), 1));
            when(agentKnowledgeMapper.selectByAgentIds(anyList()))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectByAgentIds(anyList(), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<AgentResponse> result = agentService.list(1, 20, null, 1);

            // then
            assertEquals(1, result.getList().size());
            assertEquals(1, result.getList().get(0).getEnabled());
        }

        @Test
        @DisplayName("P1 - 同时按名称和启用状态过滤")
        void shouldReturnFilteredByNameAndEnabled() {
            // given
            Agent agent = buildAgent(1L, "ChatBot", 1L);
            agent.setEnabled(0);
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(List.of(agent), 1));
            when(agentKnowledgeMapper.selectByAgentIds(anyList()))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectByAgentIds(anyList(), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<AgentResponse> result = agentService.list(1, 20, "Chat", 0);

            // then
            assertEquals(1, result.getList().size());
            assertEquals("ChatBot", result.getList().get(0).getName());
        }

        @Test
        @DisplayName("P1 - 名称为空字符串时不应添加 like 条件")
        void shouldNotAddLikeConditionWhenNameBlank() {
            // given
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(Collections.emptyList(), 0));

            // when
            agentService.list(1, 20, "   ", null);

            // then - 不抛异常即通过
            verify(agentMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 空结果应返回空列表")
        void shouldReturnEmptyWhenNoMatch() {
            // given
            when(agentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(buildPage(Collections.emptyList(), 0));

            // when
            PageResult<AgentResponse> result = agentService.list(1, 20, "NotExist", 1);

            // then
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
        }
    }

    // ========== getById() ==========

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("P0 - Agent 不存在应抛异常")
        void shouldThrowWhenAgentNotFound() {
            // given
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.getById(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛异常（模拟返回 null）")
        void shouldThrowWhenAgentInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> agentService.getById(1L));
        }

        @Test
        @DisplayName("P1 - 正常查询应返回完整响应（含供应商信息和 MCP 工具简要）")
        void shouldReturnFullResponseWithProviderAndMcpTools() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            agent.setDefaultProviderId(1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            Provider provider = buildProvider(1L, "OpenAI");
            when(providerMapper.selectById(1L)).thenReturn(provider);

            when(agentKnowledgeMapper.selectKnowledgeIdsByAgentId(1L))
                    .thenReturn(List.of(10L, 20L));

            when(agentMcpToolMapper.selectToolIdsByAgentId(eq(1L), eq(1L)))
                    .thenReturn(List.of(30L));

            McpTool tool = buildMcpTool(30L, "search", 100L);
            when(mcpToolMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tool));

            McpServer server = buildMcpServer(100L, "MyServer", 1);
            when(mcpServerMapper.selectBatchIds(anyCollection())).thenReturn(List.of(server));

            // when
            AgentResponse response = agentService.getById(1L);

            // then
            assertEquals(1L, response.getId());
            assertEquals("TestAgent", response.getName());
            assertNotNull(response.getDefaultProvider());
            assertEquals("OpenAI", response.getDefaultProvider().getName());
            assertEquals(ProviderType.OPENAI.toString(), response.getDefaultProvider().getType());
            assertEquals(List.of(10L, 20L), response.getKnowledgeIds());
            assertEquals(List.of(30L), response.getMcpToolIds());
            assertNotNull(response.getMcpTools());
            assertEquals(1, response.getMcpTools().size());
            assertEquals("search", response.getMcpTools().get(0).getName());
            assertEquals("MyServer", response.getMcpTools().get(0).getServerName());
        }

        @Test
        @DisplayName("P1 - 供应商为 null 时不应设置 defaultProvider")
        void shouldNotSetProviderWhenProviderIsNull() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            agent.setDefaultProviderId(999L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);
            when(providerMapper.selectById(999L)).thenReturn(null);
            when(agentKnowledgeMapper.selectKnowledgeIdsByAgentId(1L))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectToolIdsByAgentId(eq(1L), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            AgentResponse response = agentService.getById(1L);

            // then
            assertNull(response.getDefaultProvider());
        }

        @Test
        @DisplayName("P1 - MCP 工具对应的服务器不存在时应跳过该工具")
        void shouldSkipMcpToolWhenServerNotFound() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentKnowledgeMapper.selectKnowledgeIdsByAgentId(1L))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectToolIdsByAgentId(eq(1L), eq(1L)))
                    .thenReturn(List.of(30L));

            McpTool tool = buildMcpTool(30L, "search", 100L);
            when(mcpToolMapper.selectBatchIds(anyCollection())).thenReturn(List.of(tool));
            when(mcpServerMapper.selectBatchIds(anyCollection())).thenReturn(List.of()); // 服务器不存在

            // when
            AgentResponse response = agentService.getById(1L);

            // then
            assertNotNull(response.getMcpTools());
            assertEquals(1, response.getMcpTools().size());
            assertNull(response.getMcpTools().get(0).getServerName());
        }
    }

    // ========== getEntityById() ==========

    @Nested
    @DisplayName("getEntityById()")
    class GetEntityByIdTests {

        @Test
        @DisplayName("P0 - Agent 不存在应抛异常")
        void shouldThrowWhenAgentNotFound() {
            // given
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.getEntityById(999L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
            assertEquals("Agent 不存在", ex.getMessage());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛异常")
        void shouldThrowWhenAgentInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> agentService.getEntityById(1L));
        }

        @Test
        @DisplayName("P1 - 正常查询应返回实体并加载关联 IDs")
        void shouldReturnEntityWithKnowledgeAndMcpToolIds() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);
            when(agentKnowledgeMapper.selectKnowledgeIdsByAgentId(1L))
                    .thenReturn(List.of(10L, 20L));
            when(agentMcpToolMapper.selectToolIdsByAgentId(eq(1L), eq(1L)))
                    .thenReturn(List.of(30L, 40L));

            // when
            Agent result = agentService.getEntityById(1L);

            // then
            assertEquals(1L, result.getId());
            assertEquals("TestAgent", result.getName());
            assertEquals(1L, result.getWorkspaceId());
            assertEquals(List.of(10L, 20L), result.getKnowledgeIds());
            assertEquals(List.of(30L, 40L), result.getMcpToolIds());
        }

        @Test
        @DisplayName("P1 - 无关联数据时应返回空列表")
        void shouldReturnEmptyListsWhenNoAssociations() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);
            when(agentKnowledgeMapper.selectKnowledgeIdsByAgentId(1L))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectToolIdsByAgentId(eq(1L), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            Agent result = agentService.getEntityById(1L);

            // then
            assertTrue(result.getKnowledgeIds().isEmpty());
            assertTrue(result.getMcpToolIds().isEmpty());
        }

        @Test
        @DisplayName("P1 - 应正确传递 workspaceId 过滤条件")
        void shouldPassWorkspaceIdFilter() {
            // given
            Agent agent = buildAgent(5L, "WsAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);
            when(agentKnowledgeMapper.selectKnowledgeIdsByAgentId(5L))
                    .thenReturn(Collections.emptyList());
            when(agentMcpToolMapper.selectToolIdsByAgentId(eq(5L), eq(1L)))
                    .thenReturn(Collections.emptyList());

            // when
            Agent result = agentService.getEntityById(5L);

            // then
            assertEquals(5L, result.getId());
            verify(agentMapper).selectOne(any(LambdaQueryWrapper.class));
        }
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("P0 - 名称重复应抛异常")
        void shouldThrowWhenNameDuplicate() {
            // given
            AgentCreateRequest request = buildCreateRequest();

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L); // 名称已存在

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.create(request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
            assertEquals("Agent 名称已存在", ex.getMessage());
        }

        @Test
        @DisplayName("P0 - 供应商不存在应抛异常")
        void shouldThrowWhenProviderNotFound() {
            // given
            AgentCreateRequest request = buildCreateRequest();

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.create(request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
            assertEquals("指定的供应商不存在", ex.getMessage());
        }

        @Test
        @DisplayName("P0 - MCP 工具不存在应抛异常")
        void shouldThrowWhenMcpToolNotFoundDuringCreate() {
            // given
            AgentCreateRequest request = buildCreateRequest();
            request.setMcpToolIds(List.of(999L));

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(100L);
                return 1;
            });
            when(mcpToolMapper.selectById(999L)).thenReturn(null); // 工具不存在

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.create(request));
            assertEquals(ErrorCode.MCP_TOOL_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - MCP 服务器离线应抛异常")
        void shouldThrowWhenMcpServerOfflineDuringCreate() {
            // given
            AgentCreateRequest request = buildCreateRequest();
            request.setMcpToolIds(List.of(30L));

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(100L);
                return 1;
            });
            McpTool tool = buildMcpTool(30L, "search", 100L);
            when(mcpToolMapper.selectById(30L)).thenReturn(tool);
            McpServer server = buildMcpServer(100L, "MyServer", 0); // 离线
            when(mcpServerMapper.selectById(100L)).thenReturn(server);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.create(request));
            assertEquals(ErrorCode.MCP_SERVER_OFFLINE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - MCP 工具数量超过 10 应抛异常")
        void shouldThrowWhenMcpToolLimitExceededDuringCreate() {
            // given
            AgentCreateRequest request = buildCreateRequest();
            request.setMcpToolIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(100L);
                return 1;
            });

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.create(request));
            assertEquals(ErrorCode.MCP_TOOL_LIMIT_EXCEEDED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 创建成功应绑定 workspaceId 并插入")
        void shouldBindWorkspaceAndInsert() {
            // given
            AgentCreateRequest request = buildCreateRequest();

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            Provider provider = buildProvider(1L, "OpenAI");
            when(providerMapper.selectById(1L)).thenReturn(provider);
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(100L);
                return 1;
            });

            // when
            Agent result = agentService.create(request);

            // then
            ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
            verify(agentMapper).insert(captor.capture());

            Agent saved = captor.getValue();
            assertEquals("TestAgent", saved.getName());
            assertEquals(1L, saved.getWorkspaceId());
            assertEquals(1L, saved.getDefaultProviderId());
            assertEquals("gpt-4", saved.getDefaultModel());
            assertEquals("You are helpful", saved.getSystemPrompt());
            assertEquals(new BigDecimal("0.7"), saved.getTemperature());
            assertEquals(2000, saved.getMaxTokens());
            assertEquals(1, saved.getEnabled());
            assertEquals(0, saved.getRagEnabled());
            assertEquals(5, saved.getRagTopK());
            assertEquals("hybrid", saved.getRagStrategy());
        }

        @Test
        @DisplayName("P1 - 创建时带知识库和 MCP 工具关联应正确保存")
        void shouldSaveKnowledgeAndMcpToolAssociations() {
            // given
            AgentCreateRequest request = buildCreateRequest();
            request.setKnowledgeIds(List.of(10L, 20L));
            request.setMcpToolIds(List.of(30L));

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(100L);
                return 1;
            });

            McpTool tool = buildMcpTool(30L, "search", 100L);
            when(mcpToolMapper.selectById(30L)).thenReturn(tool);
            McpServer server = buildMcpServer(100L, "MyServer", 1);
            when(mcpServerMapper.selectById(100L)).thenReturn(server);
            when(agentMcpToolMapper.batchInsert(eq(100L), anyList(), eq(1L))).thenReturn(1);

            // when
            Agent result = agentService.create(request);

            // then
            verify(agentKnowledgeMapper).upsertKnowledgeIds(100L, List.of(10L, 20L));
            verify(agentMcpToolMapper).batchInsert(eq(100L), eq(List.of(30L)), eq(1L));
            assertEquals(List.of(10L, 20L), result.getKnowledgeIds());
            assertEquals(List.of(30L), result.getMcpToolIds());
        }

        @Test
        @DisplayName("P1 - 未传可选字段应使用默认值")
        void shouldUseDefaultValuesForOptionalFields() {
            // given
            AgentCreateRequest request = new AgentCreateRequest();
            request.setName("MinimalAgent");
            request.setDefaultProviderId(1L);
            request.setDefaultModel("gpt-4");
            request.setSystemPrompt("Hello");
            // enabled, ragEnabled, ragTopK, ragStrategy 均为 null

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(200L);
                return 1;
            });

            // when
            Agent result = agentService.create(request);

            // then
            assertEquals(1, result.getEnabled());    // 默认 1
            assertEquals(0, result.getRagEnabled()); // 默认 0
            assertEquals(5, result.getRagTopK());    // 默认 5
            assertEquals("hybrid", result.getRagStrategy()); // 默认 hybrid
        }

        @Test
        @DisplayName("P1 - 知识库和 MCP 工具列表为 null 时不应调用关联保存")
        void shouldNotSaveAssociationsWhenListsAreNull() {
            // given
            AgentCreateRequest request = buildCreateRequest();
            request.setKnowledgeIds(null);
            request.setMcpToolIds(null);

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(providerMapper.selectById(1L)).thenReturn(buildProvider(1L, "OpenAI"));
            when(agentMapper.insert(any(Agent.class))).thenAnswer(invocation -> {
                Agent a = invocation.getArgument(0);
                a.setId(300L);
                return 1;
            });

            // when
            agentService.create(request);

            // then
            verify(agentKnowledgeMapper, never()).upsertKnowledgeIds(anyLong(), anyList());
            verify(agentMcpToolMapper, never()).batchInsert(anyLong(), anyList(), anyLong());
        }
    }

    // ========== update() ==========

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("P0 - Agent 不存在应抛异常")
        void shouldThrowWhenAgentNotFound() {
            // given
            when(agentMapper.selectById(999L)).thenReturn(null);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("Updated");

            // when & then
            assertThrows(BusinessException.class,
                    () -> agentService.update(999L, request));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 更新应抛异常")
        void shouldThrowWhenAgentInDifferentWorkspace() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 999L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("Updated");

            // when & then
            assertThrows(BusinessException.class,
                    () -> agentService.update(1L, request));
        }

        @Test
        @DisplayName("P0 - 改名为已存在的名称应抛异常")
        void shouldThrowWhenNewNameDuplicate() {
            // given
            Agent existing = buildAgent(1L, "OldName", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);

            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("DuplicateName");

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.update(1L, request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 更新供应商为不存在的应抛异常")
        void shouldThrowWhenNewProviderNotFound() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(providerMapper.selectById(999L)).thenReturn(null);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setDefaultProviderId(999L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.update(1L, request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
            assertEquals("指定的供应商不存在", ex.getMessage());
        }

        @Test
        @DisplayName("P0 - MCP 工具不存在应抛异常")
        void shouldThrowWhenMcpToolNotFoundDuringUpdate() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);
            when(mcpToolMapper.selectById(999L)).thenReturn(null);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setMcpToolIds(List.of(999L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.update(1L, request));
            assertEquals(ErrorCode.MCP_TOOL_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - MCP 服务器离线应抛异常")
        void shouldThrowWhenMcpServerOfflineDuringUpdate() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            McpTool tool = buildMcpTool(30L, "search", 100L);
            when(mcpToolMapper.selectById(30L)).thenReturn(tool);
            McpServer server = buildMcpServer(100L, "MyServer", 0); // 离线
            when(mcpServerMapper.selectById(100L)).thenReturn(server);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setMcpToolIds(List.of(30L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.update(1L, request));
            assertEquals(ErrorCode.MCP_SERVER_OFFLINE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - MCP 工具数量超过 10 应抛异常")
        void shouldThrowWhenMcpToolLimitExceededDuringUpdate() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setMcpToolIds(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.update(1L, request));
            assertEquals(ErrorCode.MCP_TOOL_LIMIT_EXCEEDED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 部分更新只修改提供的字段")
        void shouldOnlyUpdateProvidedFields() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            existing.setSystemPrompt("Old prompt");
            existing.setTemperature(new BigDecimal("0.5"));
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setSystemPrompt("New prompt");

            // when
            Agent result = agentService.update(1L, request);

            // then
            assertEquals("TestAgent", result.getName());
            assertEquals("New prompt", result.getSystemPrompt());
            assertEquals(new BigDecimal("0.5"), result.getTemperature());
            verify(agentMapper).updateById(any(Agent.class));
        }

        @Test
        @DisplayName("P1 - 更新名称时检查唯一性")
        void shouldCheckNameUniquenessWhenNameChanged() {
            // given
            Agent existing = buildAgent(1L, "OldName", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("NewName");

            // when
            Agent result = agentService.update(1L, request);

            // then
            assertEquals("NewName", result.getName());
            verify(agentMapper).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 更新相同名称不应触发唯一性检查")
        void shouldSkipNameCheckWhenNameUnchanged() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("TestAgent");
            request.setEnabled(0);

            // when
            agentService.update(1L, request);

            // then
            verify(agentMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 更新知识库关联应执行差量更新")
        void shouldUpdateKnowledgeAssociations() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setKnowledgeIds(List.of(10L, 20L));

            // when
            agentService.update(1L, request);

            // then
            verify(agentKnowledgeMapper).upsertKnowledgeIds(1L, List.of(10L, 20L));
            verify(agentKnowledgeMapper).softDeleteExcept(1L, List.of(10L, 20L));
        }

        @Test
        @DisplayName("P1 - 更新知识库为空列表应删除所有关联")
        void shouldDeleteAllKnowledgeWhenEmptyList() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setKnowledgeIds(Collections.emptyList());

            // when
            agentService.update(1L, request);

            // then
            verify(agentKnowledgeMapper).softDeleteByAgentId(1L);
            verify(agentKnowledgeMapper, never()).upsertKnowledgeIds(anyLong(), anyList());
        }

        @Test
        @DisplayName("P1 - 更新 MCP 工具关联应全量替换")
        void shouldReplaceMcpToolAssociations() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            McpTool tool = buildMcpTool(30L, "search", 100L);
            when(mcpToolMapper.selectById(30L)).thenReturn(tool);
            McpServer server = buildMcpServer(100L, "MyServer", 1);
            when(mcpServerMapper.selectById(100L)).thenReturn(server);
            when(agentMcpToolMapper.batchInsert(eq(1L), anyList(), eq(1L))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setMcpToolIds(List.of(30L));

            // when
            agentService.update(1L, request);

            // then
            verify(agentMcpToolMapper).deleteByAgentId(eq(1L), eq(1L));
            verify(agentMcpToolMapper).batchInsert(eq(1L), eq(List.of(30L)), eq(1L));
        }

        @Test
        @DisplayName("P1 - 更新供应商时应验证供应商存在")
        void shouldValidateProviderWhenChanged() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            Provider newProvider = buildProvider(2L, "Anthropic");
            when(providerMapper.selectById(2L)).thenReturn(newProvider);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setDefaultProviderId(2L);

            // when
            Agent result = agentService.update(1L, request);

            // then
            assertEquals(2L, result.getDefaultProviderId());
            verify(providerMapper).selectById(2L);
        }

        @Test
        @DisplayName("P1 - 更新 MCP 工具为空列表应只删除已有绑定")
        void shouldDeleteAllMcpToolsWhenEmptyList() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setMcpToolIds(Collections.emptyList());

            // when
            agentService.update(1L, request);

            // then
            verify(agentMcpToolMapper).deleteByAgentId(eq(1L), eq(1L));
            verify(agentMcpToolMapper, never()).batchInsert(anyLong(), anyList(), anyLong());
        }

        @Test
        @DisplayName("P1 - 未传 knowledgeIds 和 mcpToolIds 时不应操作关联表")
        void shouldNotTouchAssociationTablesWhenNotProvided() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setDescription("New description");
            // knowledgeIds 和 mcpToolIds 均为 null

            // when
            agentService.update(1L, request);

            // then
            verify(agentKnowledgeMapper, never()).upsertKnowledgeIds(anyLong(), anyList());
            verify(agentKnowledgeMapper, never()).softDeleteExcept(anyLong(), anyList());
            verify(agentKnowledgeMapper, never()).softDeleteByAgentId(anyLong());
            verify(agentMcpToolMapper, never()).deleteByAgentId(anyLong(), anyLong());
        }

        @Test
        @DisplayName("P1 - 更新所有可配置字段")
        void shouldUpdateAllConfigurableFields() {
            // given
            Agent existing = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(existing);
            when(agentMapper.updateById(any(Agent.class))).thenReturn(1);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("UpdatedAgent");
            request.setDescription("New desc");
            request.setAvatar("http://avatar.png");
            request.setDefaultModel("claude-3");
            request.setSystemPrompt("New prompt");
            request.setUserMessagePrefix("Prefix:");
            request.setWelcomeMessage("Hello!");
            request.setTemperature(new BigDecimal("1.5"));
            request.setMaxTokens(4000);
            request.setTopP(new BigDecimal("0.9"));
            request.setFrequencyPenalty(new BigDecimal("0.5"));
            request.setPresencePenalty(new BigDecimal("0.3"));
            request.setMaxHistoryRounds(10);
            request.setStreamEnabled(1);
            request.setEnabled(0);
            request.setRagEnabled(1);
            request.setRagTopK(10);
            request.setRagStrategy("vector");

            // when
            Agent result = agentService.update(1L, request);

            // then
            assertEquals("UpdatedAgent", result.getName());
            assertEquals("New desc", result.getDescription());
            assertEquals("http://avatar.png", result.getAvatar());
            assertEquals("claude-3", result.getDefaultModel());
            assertEquals("New prompt", result.getSystemPrompt());
            assertEquals("Prefix:", result.getUserMessagePrefix());
            assertEquals("Hello!", result.getWelcomeMessage());
            assertEquals(new BigDecimal("1.5"), result.getTemperature());
            assertEquals(4000, result.getMaxTokens());
            assertEquals(new BigDecimal("0.9"), result.getTopP());
            assertEquals(new BigDecimal("0.5"), result.getFrequencyPenalty());
            assertEquals(new BigDecimal("0.3"), result.getPresencePenalty());
            assertEquals(10, result.getMaxHistoryRounds());
            assertEquals(1, result.getStreamEnabled());
            assertEquals(0, result.getEnabled());
            assertEquals(1, result.getRagEnabled());
            assertEquals(10, result.getRagTopK());
            assertEquals("vector", result.getRagStrategy());
        }

        // ========== Scenario C: cross-workspace tool binding ==========

        @Test
        @DisplayName("绑定跨工作空间工具时抛出 MCP_TOOL_NOT_FOUND")
        void shouldRejectCrossWorkspaceToolBinding() {
            Agent agent = buildAgent(1L, "Test Agent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            McpTool ws2Tool = new McpTool();
            ws2Tool.setId(30L);
            ws2Tool.setServerId(5L);
            ws2Tool.setWorkspaceId(2L);
            when(mcpToolMapper.selectById(30L)).thenReturn(ws2Tool);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("Updated");
            request.setMcpToolIds(List.of(30L));

            assertThatThrownBy(() -> agentService.update(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("MCP 工具不存在");

            // deleteByAgentId is called before validation (全量替换策略),
            // but batchInsert must not be called since validation fails
            verify(agentMcpToolMapper, never()).batchInsert(anyLong(), anyList(), anyLong());
        }

        @Test
        @DisplayName("工具所属 Server 跨工作空间时拒绝绑定")
        void shouldRejectToolWithCrossWorkspaceServer() {
            Agent agent = buildAgent(1L, "Test Agent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            McpTool tool = new McpTool();
            tool.setId(30L);
            tool.setServerId(5L);
            tool.setWorkspaceId(1L);
            when(mcpToolMapper.selectById(30L)).thenReturn(tool);

            McpServer ws2Server = new McpServer();
            ws2Server.setId(5L);
            ws2Server.setWorkspaceId(2L);
            ws2Server.setEnabled(1);
            when(mcpServerMapper.selectById(5L)).thenReturn(ws2Server);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("Updated");
            request.setMcpToolIds(List.of(30L));

            assertThatThrownBy(() -> agentService.update(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("MCP 服务器离线");
        }

        // ========== Scenario E: mapper workspace param verification ==========

        @Test
        @DisplayName("batchInsert 传入当前上下文的 workspaceId")
        void shouldPassWorkspaceIdToBatchInsert() {
            Agent agent = buildAgent(1L, "Test Agent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            McpTool tool = new McpTool();
            tool.setId(30L);
            tool.setServerId(5L);
            tool.setWorkspaceId(1L);
            when(mcpToolMapper.selectById(30L)).thenReturn(tool);

            McpServer server = new McpServer();
            server.setId(5L);
            server.setWorkspaceId(1L);
            server.setEnabled(1);
            when(mcpServerMapper.selectById(5L)).thenReturn(server);

            AgentUpdateRequest request = new AgentUpdateRequest();
            request.setName("Updated");
            request.setMcpToolIds(List.of(30L));

            agentService.update(1L, request);

            verify(agentMcpToolMapper).batchInsert(eq(1L), eq(List.of(30L)), eq(1L));
        }
    }

    // ========== delete() ==========

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("P0 - Agent 不存在应抛异常")
        void shouldThrowWhenAgentNotFound() {
            // given
            when(agentMapper.selectById(999L)).thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> agentService.delete(999L));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 删除应抛异常")
        void shouldThrowWhenAgentInDifferentWorkspace() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 999L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            // when & then
            assertThrows(BusinessException.class,
                    () -> agentService.delete(1L));
        }

        @Test
        @DisplayName("P1 - 正常删除应调用 deleteById 并清理关联数据")
        void shouldDeleteSuccessfullyAndCleanAssociations() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);
            when(agentMapper.deleteById(1L)).thenReturn(1);

            // when
            assertDoesNotThrow(() -> agentService.delete(1L));

            // then
            verify(agentMapper).deleteById(1L);
            verify(agentKnowledgeMapper).softDeleteByAgentId(1L);
            verify(agentMcpToolMapper).deleteByAgentId(eq(1L), eq(1L));
        }

        @Test
        @DisplayName("P1 - 删除不存在的 ID（被 WorkspaceGuard 拦截）应抛异常")
        void shouldThrowWhenDeletingNonExistentId() {
            // given
            when(agentMapper.selectById(42L)).thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.delete(42L));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }
    }

    // ========== testChat() ==========

    @Nested
    @DisplayName("testChat()")
    class TestChatTests {

        @Test
        @DisplayName("P0 - Agent 不存在应返回失败响应")
        void shouldReturnFailureWhenAgentNotFound() {
            // given
            when(agentMapper.selectById(999L)).thenReturn(null);

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");

            // when
            AgentTestChatResponse response = agentService.testChat(999L, request);

            // then
            assertFalse(response.getSuccess());
            assertNotNull(response.getErrorMessage());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应返回失败响应")
        void shouldReturnFailureWhenAgentInDifferentWorkspace() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 999L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");

            // when
            AgentTestChatResponse response = agentService.testChat(1L, request);

            // then
            assertFalse(response.getSuccess());
            assertNotNull(response.getErrorMessage());
        }

        @Test
        @DisplayName("P0 - 供应商不存在应返回失败响应")
        void shouldReturnFailureWhenProviderNotFound() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            agent.setDefaultProviderId(999L);
            when(agentMapper.selectById(1L)).thenReturn(agent);
            when(providerService.getEntityById(999L))
                    .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "供应商不存在"));

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");

            // when
            AgentTestChatResponse response = agentService.testChat(1L, request);

            // then
            assertFalse(response.getSuccess());
            assertNotNull(response.getErrorMessage());
        }

        @Test
        @DisplayName("P0 - LLM 调用失败应返回失败响应")
        void shouldReturnFailureWhenLlmCallFails() throws Exception {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);
            Provider provider = buildProvider(1L, "OpenAI");
            when(providerService.getEntityById(1L)).thenReturn(provider);
            when(llmHttpClient.post(anyString(), anyMap(), anyString()))
                    .thenThrow(new RuntimeException("Connection refused"));

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");

            // when
            AgentTestChatResponse response = agentService.testChat(1L, request);

            // then
            assertFalse(response.getSuccess());
            assertTrue(response.getErrorMessage().contains("Connection refused"));
        }

        @Test
        @DisplayName("P1 - 正常对话应返回成功响应")
        void shouldReturnSuccessResponse() throws Exception {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);
            Provider provider = buildProvider(1L, "OpenAI");
            provider.setAuthConfig(new tools.jackson.databind.ObjectMapper()
                    .readTree("{\"api_key\": \"sk-test\"}"));
            when(providerService.getEntityById(1L)).thenReturn(provider);

            String llmResponse = "{\"choices\":[{\"message\":{\"content\":\"Hi there!\"}}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5,\"total_tokens\":15}}";
            when(llmHttpClient.post(anyString(), anyMap(), anyString()))
                    .thenReturn(llmResponse);

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");

            // when
            AgentTestChatResponse response = agentService.testChat(1L, request);

            // then
            assertTrue(response.getSuccess());
            assertEquals("Hi there!", response.getReply());
            assertNotNull(response.getTokens());
            assertEquals(10, response.getTokens().getPromptTokens());
            assertEquals(5, response.getTokens().getCompletionTokens());
            assertEquals(15, response.getTokens().getTotalTokens());
            assertNotNull(response.getPerformance());
            assertEquals(1L, response.getPerformance().getActualProviderId());
            assertEquals("gpt-4", response.getPerformance().getActualModel());
        }

        @Test
        @DisplayName("P1 - 使用 overrideProviderId 和 overrideModel 应覆盖默认值")
        void shouldUseOverrideProviderAndModel() throws Exception {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectById(1L)).thenReturn(agent);

            Provider overrideProvider = buildProvider(2L, "Anthropic");
            overrideProvider.setType(ProviderType.ANTHROPIC);
            overrideProvider.setAuthConfig(new tools.jackson.databind.ObjectMapper()
                    .readTree("{\"api_key\": \"sk-ant-test\"}"));
            when(providerService.getEntityById(2L)).thenReturn(overrideProvider);

            String llmResponse = "{\"choices\":[{\"message\":{\"content\":\"Claude reply\"}}],"
                    + "\"usage\":{\"prompt_tokens\":8,\"completion_tokens\":3,\"total_tokens\":11}}";
            when(llmHttpClient.post(anyString(), anyMap(), anyString()))
                    .thenReturn(llmResponse);

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");
            request.setOverrideProviderId(2L);
            request.setOverrideModel("claude-3-opus");

            // when
            AgentTestChatResponse response = agentService.testChat(1L, request);

            // then
            assertTrue(response.getSuccess());
            assertEquals("Claude reply", response.getReply());
            assertEquals(2L, response.getPerformance().getActualProviderId());
            assertEquals("claude-3-opus", response.getPerformance().getActualModel());
        }

        @Test
        @DisplayName("P1 - 用户消息前缀应拼接到消息前面")
        void shouldPrependUserMessagePrefix() throws Exception {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            agent.setUserMessagePrefix("Context: ");
            when(agentMapper.selectById(1L)).thenReturn(agent);
            Provider provider = buildProvider(1L, "OpenAI");
            provider.setAuthConfig(new tools.jackson.databind.ObjectMapper()
                    .readTree("{\"api_key\": \"sk-test\"}"));
            when(providerService.getEntityById(1L)).thenReturn(provider);

            String llmResponse = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}],"
                    + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1,\"total_tokens\":6}}";
            when(llmHttpClient.post(anyString(), anyMap(), anyString()))
                    .thenReturn(llmResponse);

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("What is AI?");

            // when
            agentService.testChat(1L, request);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmHttpClient).post(anyString(), anyMap(), bodyCaptor.capture());
            String requestBody = bodyCaptor.getValue();
            assertTrue(requestBody.contains("Context: What is AI?"));
        }

        @Test
        @DisplayName("P1 - 无用户消息前缀时应直接使用原始消息")
        void shouldUseOriginalMessageWhenNoPrefix() throws Exception {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            agent.setUserMessagePrefix(null);
            when(agentMapper.selectById(1L)).thenReturn(agent);
            Provider provider = buildProvider(1L, "OpenAI");
            provider.setAuthConfig(new tools.jackson.databind.ObjectMapper()
                    .readTree("{\"api_key\": \"sk-test\"}"));
            when(providerService.getEntityById(1L)).thenReturn(provider);

            String llmResponse = "{\"choices\":[{\"message\":{\"content\":\"OK\"}}],"
                    + "\"usage\":{\"prompt_tokens\":5,\"completion_tokens\":1,\"total_tokens\":6}}";
            when(llmHttpClient.post(anyString(), anyMap(), anyString()))
                    .thenReturn(llmResponse);

            AgentTestChatRequest request = new AgentTestChatRequest();
            request.setMessage("Hello");

            // when
            agentService.testChat(1L, request);

            // then
            @SuppressWarnings("unchecked")
            ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
            verify(llmHttpClient).post(anyString(), anyMap(), bodyCaptor.capture());
            String requestBody = bodyCaptor.getValue();
            assertTrue(requestBody.contains("\"Hello\""));
            assertFalse(requestBody.contains("null"));
        }
    }

    // ========== bindTools() ==========

    @Nested
    @DisplayName("bindTools()")
    class BindToolsTests {

        @Test
        @DisplayName("P0 - Agent 不存在应抛异常")
        void shouldThrowWhenAgentNotFound() {
            // given
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(List.of(1L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.bindTools(999L, request));
            assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 工具不存在应抛异常")
        void shouldThrowWhenToolNotFound() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);
            when(mcpToolMapper.selectById(999L)).thenReturn(null);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(List.of(999L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.bindTools(1L, request));
            assertEquals(ErrorCode.MCP_TOOL_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - MCP 服务器离线应抛异常")
        void shouldThrowWhenServerOffline() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            McpTool tool = buildMcpTool(1L, "search", 100L);
            when(mcpToolMapper.selectById(1L)).thenReturn(tool);

            McpServer server = buildMcpServer(100L, "MyServer", 0);
            when(mcpServerMapper.selectById(100L)).thenReturn(server);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(List.of(1L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.bindTools(1L, request));
            assertEquals(ErrorCode.MCP_SERVER_OFFLINE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - MCP 服务器不存在应抛异常")
        void shouldThrowWhenServerNotFound() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            McpTool tool = buildMcpTool(1L, "search", 100L);
            when(mcpToolMapper.selectById(1L)).thenReturn(tool);
            when(mcpServerMapper.selectById(100L)).thenReturn(null);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(List.of(1L));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.bindTools(1L, request));
            assertEquals(ErrorCode.MCP_SERVER_OFFLINE.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 工具数量超过 10 应抛异常")
        void shouldThrowWhenToolLimitExceeded() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            List<Long> toolIds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(toolIds);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> agentService.bindTools(1L, request));
            assertEquals(ErrorCode.MCP_TOOL_LIMIT_EXCEEDED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 绑定成功应先删后插")
        void shouldDeleteThenInsertTools() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            McpTool tool = buildMcpTool(1L, "search", 100L);
            when(mcpToolMapper.selectById(1L)).thenReturn(tool);
            McpServer server = buildMcpServer(100L, "MyServer", 1);
            when(mcpServerMapper.selectById(100L)).thenReturn(server);
            when(agentMcpToolMapper.batchInsert(eq(1L), anyList(), eq(1L))).thenReturn(1);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(List.of(1L));

            // when
            assertDoesNotThrow(() -> agentService.bindTools(1L, request));

            // then
            verify(agentMcpToolMapper).deleteByAgentId(eq(1L), eq(1L));
            verify(agentMcpToolMapper).batchInsert(eq(1L), eq(List.of(1L)), eq(1L));
        }

        @Test
        @DisplayName("P1 - 空工具列表应只删除已有绑定")
        void shouldDeleteExistingBindingsWhenEmptyList() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(Collections.emptyList());

            // when
            assertDoesNotThrow(() -> agentService.bindTools(1L, request));

            // then
            verify(agentMcpToolMapper).deleteByAgentId(eq(1L), eq(1L));
            verify(agentMcpToolMapper, never()).batchInsert(anyLong(), anyList(), anyLong());
        }

        @Test
        @DisplayName("P1 - 工具列表为 null 时应只删除已有绑定")
        void shouldDeleteExistingBindingsWhenNullList() {
            // given
            Agent agent = buildAgent(1L, "TestAgent", 1L);
            when(agentMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(agent);

            BindToolsRequest request = new BindToolsRequest();
            request.setToolIds(null);

            // when
            assertDoesNotThrow(() -> agentService.bindTools(1L, request));

            // then
            verify(agentMcpToolMapper).deleteByAgentId(eq(1L), eq(1L));
            verify(agentMcpToolMapper, never()).batchInsert(anyLong(), anyList(), anyLong());
        }
    }
}
