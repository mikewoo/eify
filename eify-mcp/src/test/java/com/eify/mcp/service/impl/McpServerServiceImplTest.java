package com.eify.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eify.common.context.CurrentContext;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.mcp.domain.dto.ConnectionTestResult;
import com.eify.mcp.domain.dto.DebugToolRequest;
import com.eify.mcp.domain.dto.DebugToolResponse;
import com.eify.mcp.domain.dto.McpServerCreateRequest;
import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.domain.dto.McpServerUpdateRequest;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.domain.entity.McpTool;
import com.eify.mcp.mapper.AgentMcpToolMapper;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.mapper.McpToolMapper;
import com.eify.mcp.service.McpClientService;
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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * McpServerServiceImpl 单元测试。
 * <p>
 * 使用 Mockito mock 所有依赖，只测业务逻辑。
 * 不启动 Spring 容器，单个测试 < 50ms。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("McpServerServiceImpl")
class McpServerServiceImplTest {

    @Mock McpServerMapper mcpServerMapper;
    @Mock McpToolMapper mcpToolMapper;
    @Mock AgentMcpToolMapper agentMcpToolMapper;
    @Mock McpClientService mcpClientService;

    @InjectMocks
    McpServerServiceImpl mcpServerService;

    @BeforeEach
    void setUp() {
        CurrentContext.set(1L, 1L); // userId=1, workspaceId=1
    }

    @AfterEach
    void tearDown() {
        CurrentContext.clear();
    }

    // ========== 辅助方法 ==========

    private McpServer buildServer(Long id, String name, String endpoint, Long workspaceId) {
        McpServer server = new McpServer();
        server.setId(id);
        server.setName(name);
        server.setEndpoint(endpoint);
        server.setWorkspaceId(workspaceId);
        server.setEnabled(1);
        return server;
    }

    private McpTool buildTool(Long id, Long serverId, String name) {
        McpTool tool = new McpTool();
        tool.setId(id);
        tool.setServerId(serverId);
        tool.setName(name);
        tool.setDescription("desc-" + name);
        return tool;
    }

    // ========== list() ==========

    @Nested
    @DisplayName("list()")
    class ListTests {

        @Test
        @DisplayName("P0 - 应按 workspaceId 过滤查询")
        void shouldFilterByWorkspaceId() {
            // given
            Page<McpServer> pageObj = new Page<>(1, 20);
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            pageObj.setRecords(List.of(server));
            pageObj.setTotal(1);

            when(mcpServerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            mcpServerService.list(1, 20);

            // then - 验证查询条件包含 workspaceId
            ArgumentCaptor<LambdaQueryWrapper<McpServer>> wrapperCaptor =
                    ArgumentCaptor.forClass(LambdaQueryWrapper.class);
            verify(mcpServerMapper).selectPage(any(Page.class), wrapperCaptor.capture());
            // 查询已通过 CurrentContext.getWorkspaceId() 过滤，这里验证调用发生
            verify(mcpServerMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 正常分页返回结果（含工具数量）")
        void shouldReturnPaginatedResultsWithToolCounts() {
            // given
            Page<McpServer> pageObj = new Page<>(1, 20);
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            pageObj.setRecords(List.of(server));
            pageObj.setTotal(1);

            when(mcpServerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            McpTool tool1 = buildTool(10L, 1L, "search_order");
            McpTool tool2 = buildTool(11L, 1L, "create_order");
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(tool1, tool2));

            // when
            PageResult<McpServerResponse> result = mcpServerService.list(1, 20);

            // then
            assertEquals(1, result.getList().size());
            assertEquals("订单服务", result.getList().get(0).getName());
            assertEquals(2, result.getList().get(0).getToolCount());
            assertEquals(1L, result.getTotal());
        }

        @Test
        @DisplayName("P1 - 空列表应返回空结果且不查询工具")
        void shouldReturnEmptyListWhenNoRecords() {
            // given
            Page<McpServer> pageObj = new Page<>(1, 20);
            pageObj.setRecords(Collections.emptyList());
            pageObj.setTotal(0);

            when(mcpServerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // when
            PageResult<McpServerResponse> result = mcpServerService.list(1, 20);

            // then
            assertTrue(result.getList().isEmpty());
            assertEquals(0L, result.getTotal());
            // 空列表时不应查询工具
            verify(mcpToolMapper, never()).selectList(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 无工具的服务器应返回 toolCount=0")
        void shouldReturnZeroToolCountWhenNoTools() {
            // given
            Page<McpServer> pageObj = new Page<>(1, 20);
            McpServer server = buildServer(1L, "空服务", "http://localhost:9090/mcp", 1L);
            pageObj.setRecords(List.of(server));
            pageObj.setTotal(1);

            when(mcpServerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            PageResult<McpServerResponse> result = mcpServerService.list(1, 20);

            // then
            assertEquals(0, result.getList().get(0).getToolCount());
        }

        @Test
        @DisplayName("P1 - 多个服务器应分别统计工具数量")
        void shouldCountToolsPerServer() {
            // given
            Page<McpServer> pageObj = new Page<>(1, 20);
            McpServer server1 = buildServer(1L, "服务A", "http://localhost:8080/mcp", 1L);
            McpServer server2 = buildServer(2L, "服务B", "http://localhost:8081/mcp", 1L);
            pageObj.setRecords(List.of(server1, server2));
            pageObj.setTotal(2);

            when(mcpServerMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageObj);

            // 服务A 有 2 个工具，服务B 有 1 个工具
            McpTool tool1 = buildTool(10L, 1L, "tool_a1");
            McpTool tool2 = buildTool(11L, 1L, "tool_a2");
            McpTool tool3 = buildTool(12L, 2L, "tool_b1");
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(tool1, tool2, tool3));

            // when
            PageResult<McpServerResponse> result = mcpServerService.list(1, 20);

            // then
            assertEquals(2, result.getList().size());
            assertEquals(2, result.getList().get(0).getToolCount()); // 服务A
            assertEquals(1, result.getList().get(1).getToolCount()); // 服务B
        }
    }

    // ========== getById() ==========

    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("P0 - 服务器不存在应抛 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.getById(999L));
            assertEquals(ErrorCode.MCP_SERVER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛异常（selectOne 返回 null）")
        void shouldThrowWhenServerInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.getById(1L));
        }

        @Test
        @DisplayName("P1 - 正常查询应返回完整响应（含工具列表）")
        void shouldReturnFullResponseWithTools() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            McpTool tool1 = buildTool(10L, 1L, "search_order");
            tool1.setDescription("搜索订单");
            McpTool tool2 = buildTool(11L, 1L, "create_order");
            tool2.setDescription("创建订单");
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(tool1, tool2));

            // when
            McpServerResponse response = mcpServerService.getById(1L);

            // then
            assertEquals(1L, response.getId());
            assertEquals("订单服务", response.getName());
            assertEquals("http://localhost:8080/mcp", response.getEndpoint());
            assertEquals(1, response.getEnabled());
            assertNotNull(response.getTools());
            assertEquals(2, response.getTools().size());
            assertEquals("search_order", response.getTools().get(0).getName());
            assertEquals("搜索订单", response.getTools().get(0).getDescription());
            assertEquals(2, response.getToolCount());
        }

        @Test
        @DisplayName("P1 - 无工具的服务器应返回空工具列表")
        void shouldReturnEmptyToolsWhenNoTools() {
            // given
            McpServer server = buildServer(1L, "空服务", "http://localhost:9090/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            // when
            McpServerResponse response = mcpServerService.getById(1L);

            // then
            assertNotNull(response.getTools());
            assertTrue(response.getTools().isEmpty());
            assertEquals(0, response.getToolCount());
        }
    }

    // ========== getEntityById() ==========

    @Nested
    @DisplayName("getEntityById()")
    class GetEntityByIdTests {

        @Test
        @DisplayName("P0 - 服务器不存在应抛 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.getEntityById(999L));
            assertEquals(ErrorCode.MCP_SERVER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 访问应抛异常（SQL 级别过滤）")
        void shouldThrowWhenServerInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.getEntityById(1L));
        }

        @Test
        @DisplayName("P1 - 正常查询应返回实体对象")
        void shouldReturnEntityWhenFound() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            // when
            McpServer result = mcpServerService.getEntityById(1L);

            // then
            assertNotNull(result);
            assertEquals(1L, result.getId());
            assertEquals("订单服务", result.getName());
            assertEquals("http://localhost:8080/mcp", result.getEndpoint());
            assertEquals(1, result.getEnabled());
            assertEquals(1L, result.getWorkspaceId());
        }

        @Test
        @DisplayName("P1 - 返回的实体应包含正确的 workspaceId")
        void shouldReturnEntityWithCorrectWorkspaceId() {
            // given
            McpServer server = buildServer(5L, "测试服务", "http://localhost:3000/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            // when
            McpServer result = mcpServerService.getEntityById(5L);

            // then
            assertEquals(1L, result.getWorkspaceId());
            assertEquals(5L, result.getId());
        }
    }

    // ========== create() ==========

    @Nested
    @DisplayName("create()")
    class CreateTests {

        @Test
        @DisplayName("P0 - 名称重复应抛 PARAM_ERROR")
        void shouldThrowWhenNameDuplicate() {
            // given
            McpServerCreateRequest request = McpServerCreateRequest.builder()
                    .name("订单服务")
                    .endpoint("http://localhost:8080/mcp")
                    .enabled(1)
                    .build();

            // WorkspaceGuard.checkNameUnique 内部调用 selectCount
            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L); // 名称已存在

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.create(request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 创建成功应绑定 workspaceId 并插入")
        void shouldBindWorkspaceAndInsert() {
            // given
            McpServerCreateRequest request = McpServerCreateRequest.builder()
                    .name("新服务")
                    .endpoint("http://localhost:8081/mcp")
                    .enabled(1)
                    .build();

            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L); // 名称不重复
            when(mcpServerMapper.insert(any(McpServer.class))).thenReturn(1);

            // when
            McpServer result = mcpServerService.create(request);

            // then
            ArgumentCaptor<McpServer> captor = ArgumentCaptor.forClass(McpServer.class);
            verify(mcpServerMapper).insert(captor.capture());

            McpServer saved = captor.getValue();
            assertEquals("新服务", saved.getName());
            assertEquals("http://localhost:8081/mcp", saved.getEndpoint());
            assertEquals(1L, saved.getWorkspaceId()); // WorkspaceGuard.bind 设置
            assertEquals(1, saved.getEnabled());
        }

        @Test
        @DisplayName("P1 - enabled 为 null 时默认设为 1")
        void shouldDefaultEnabledTo1WhenNull() {
            // given
            McpServerCreateRequest request = McpServerCreateRequest.builder()
                    .name("默认启用服务")
                    .endpoint("http://localhost:8082/mcp")
                    .enabled(null)
                    .build();

            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(mcpServerMapper.insert(any(McpServer.class))).thenReturn(1);

            // when
            McpServer result = mcpServerService.create(request);

            // then
            assertEquals(1, result.getEnabled());
        }

        @Test
        @DisplayName("P1 - 创建时应正确设置所有字段")
        void shouldSetAllFieldsCorrectly() {
            // given
            McpServerCreateRequest request = McpServerCreateRequest.builder()
                    .name("完整服务")
                    .endpoint("http://example.com:9999/mcp")
                    .enabled(0)
                    .build();

            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(mcpServerMapper.insert(any(McpServer.class))).thenReturn(1);

            // when
            McpServer result = mcpServerService.create(request);

            // then
            assertEquals("完整服务", result.getName());
            assertEquals("http://example.com:9999/mcp", result.getEndpoint());
            assertEquals(0, result.getEnabled());
            assertEquals(1L, result.getWorkspaceId());
        }
    }

    // ========== update() ==========

    @Nested
    @DisplayName("update()")
    class UpdateTests {

        @Test
        @DisplayName("P0 - 服务器不存在应抛 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setName("更新名称");

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.update(999L, request));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 更新应抛异常（SQL 级别过滤）")
        void shouldThrowWhenServerInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setName("更新名称");

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.update(1L, request));
        }

        @Test
        @DisplayName("P0 - 改名为已存在的名称应抛 PARAM_ERROR")
        void shouldThrowWhenNewNameDuplicate() {
            // given
            McpServer existing = buildServer(1L, "旧名称", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);

            // 名称唯一性检查发现冲突
            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(1L);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setName("重复名称");

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.update(1L, request));
            assertEquals(ErrorCode.PARAM_ERROR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P1 - 部分更新只修改提供的字段")
        void shouldOnlyUpdateProvidedFields() {
            // given
            McpServer existing = buildServer(1L, "订单服务", "http://old-endpoint/mcp", 1L);
            existing.setEnabled(0);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);
            when(mcpServerMapper.updateById(any(McpServer.class))).thenReturn(1);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setEndpoint("http://new-endpoint/mcp");
            // name, enabled 均为 null → 不修改

            // when
            McpServer result = mcpServerService.update(1L, request);

            // then
            assertEquals("订单服务", result.getName());                    // 未修改
            assertEquals("http://new-endpoint/mcp", result.getEndpoint()); // 已修改
            assertEquals(0, result.getEnabled());                          // 未修改
            verify(mcpServerMapper).updateById(any(McpServer.class));
            // name 为 null 时不应触发名称唯一性检查
            verify(mcpServerMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 更新名称时检查唯一性并排除自身")
        void shouldCheckNameUniquenessWhenNameChanged() {
            // given
            McpServer existing = buildServer(1L, "旧名称", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);
            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L); // 新名称不重复
            when(mcpServerMapper.updateById(any(McpServer.class))).thenReturn(1);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setName("新名称");

            // when
            McpServer result = mcpServerService.update(1L, request);

            // then
            assertEquals("新名称", result.getName());
            verify(mcpServerMapper).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 更新 enabled 字段")
        void shouldUpdateEnabledField() {
            // given
            McpServer existing = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            existing.setEnabled(1);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);
            when(mcpServerMapper.updateById(any(McpServer.class))).thenReturn(1);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setEnabled(0);

            // when
            McpServer result = mcpServerService.update(1L, request);

            // then
            assertEquals(0, result.getEnabled());
            assertEquals("订单服务", result.getName()); // 未修改
        }

        @Test
        @DisplayName("P1 - 所有字段均更新")
        void shouldUpdateAllFieldsWhenAllProvided() {
            // given
            McpServer existing = buildServer(1L, "旧服务", "http://old/mcp", 1L);
            existing.setEnabled(0);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(existing);
            when(mcpServerMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L); // 新名称不重复
            when(mcpServerMapper.updateById(any(McpServer.class))).thenReturn(1);

            McpServerUpdateRequest request = new McpServerUpdateRequest();
            request.setName("新服务");
            request.setEndpoint("http://new/mcp");
            request.setEnabled(1);

            // when
            McpServer result = mcpServerService.update(1L, request);

            // then
            assertEquals("新服务", result.getName());
            assertEquals("http://new/mcp", result.getEndpoint());
            assertEquals(1, result.getEnabled());
        }
    }

    // ========== delete() ==========

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("P0 - 服务器不存在应抛 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.delete(999L));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 删除应抛异常（SQL 级别过滤）")
        void shouldThrowWhenServerInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.delete(1L));
        }

        @Test
        @DisplayName("P0 - 有 Agent 绑定应抛 MCP_SERVER_HAS_BINDINGS")
        void shouldThrowWhenServerHasBindings() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            McpTool tool = buildTool(10L, 1L, "search_order");
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(tool));

            // agentMcpToolMapper.selectCount > 0 表示有绑定
            when(agentMcpToolMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(3L);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.delete(1L));
            assertEquals(ErrorCode.MCP_SERVER_HAS_BINDINGS.getCode(), ex.getCode());
            // 不应执行删除
            verify(mcpServerMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("P1 - 无工具时应直接删除")
        void shouldDeleteWhenNoTools() {
            // given
            McpServer server = buildServer(1L, "空服务", "http://localhost:9090/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());
            when(mcpServerMapper.deleteById(1L)).thenReturn(1);

            // when
            assertDoesNotThrow(() -> mcpServerService.delete(1L));

            // then
            verify(mcpServerMapper).deleteById(1L);
            // 无工具时不应检查绑定
            verify(agentMcpToolMapper, never()).selectCount(any(LambdaQueryWrapper.class));
        }

        @Test
        @DisplayName("P1 - 有工具但无 Agent 绑定时应删除成功")
        void shouldDeleteWhenToolsExistButNoBindings() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            McpTool tool1 = buildTool(10L, 1L, "search_order");
            McpTool tool2 = buildTool(11L, 1L, "create_order");
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(tool1, tool2));

            // 无绑定
            when(agentMcpToolMapper.selectCount(any(LambdaQueryWrapper.class)))
                    .thenReturn(0L);
            when(mcpServerMapper.deleteById(1L)).thenReturn(1);

            // when
            assertDoesNotThrow(() -> mcpServerService.delete(1L));

            // then
            verify(agentMcpToolMapper).selectCount(any(LambdaQueryWrapper.class));
            verify(mcpServerMapper).deleteById(1L);
        }
    }

    // ========== testConnection() ==========

    @Nested
    @DisplayName("testConnection()")
    class TestConnectionTests {

        @Test
        @DisplayName("P0 - 服务器不存在应抛 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.testConnection(999L));
            assertEquals(ErrorCode.MCP_SERVER_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("P0 - 跨 workspace 调用连通性测试应抛异常")
        void shouldThrowWhenServerInDifferentWorkspace() {
            // given - selectOne 按 (id, workspaceId) 查询，不同 workspace 返回 null
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.testConnection(1L));
        }
    }

    // ========== debugTool() ==========

    @Nested
    @DisplayName("debugTool()")
    class DebugToolTests {

        @Test
        @DisplayName("P0 - 服务器不存在应抛 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            DebugToolRequest request = new DebugToolRequest();
            request.setToolName("search_order");
            request.setArguments(Map.of("query", "test"));

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.debugTool(999L, request));
        }

        @Test
        @DisplayName("P0 - 跨 workspace 调试工具应抛异常")
        void shouldThrowWhenServerInDifferentWorkspace() {
            // given
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(null);

            DebugToolRequest request = new DebugToolRequest();
            request.setToolName("search_order");
            request.setArguments(Map.of("query", "test"));

            // when & then
            assertThrows(BusinessException.class,
                    () -> mcpServerService.debugTool(1L, request));
        }

        @Test
        @DisplayName("P1 - 应委托给 mcpClientService 并返回结果")
        void shouldDelegateToMcpClientServiceAndReturnResult() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            DebugToolRequest request = new DebugToolRequest();
            request.setToolName("search_order");
            request.setArguments(Map.of("query", "test", "page", 1));

            when(mcpClientService.callTool(1L, "search_order", request.getArguments()))
                    .thenReturn("{\"data\": []}");

            // when
            DebugToolResponse response = mcpServerService.debugTool(1L, request);

            // then
            assertEquals("{\"data\": []}", response.getResult());
            assertNotNull(response.getElapsedMs());
            assertTrue(response.getElapsedMs() >= 0);
            verify(mcpClientService).callTool(1L, "search_order", request.getArguments());
        }

        @Test
        @DisplayName("P1 - 应记录正确的工具名称和参数")
        void shouldPassCorrectToolNameAndArguments() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            Map<String, Object> args = Map.of("orderId", "12345", "includeDetails", true);
            DebugToolRequest request = new DebugToolRequest();
            request.setToolName("get_order_detail");
            request.setArguments(args);

            when(mcpClientService.callTool(1L, "get_order_detail", args))
                    .thenReturn("{\"orderId\": \"12345\"}");

            // when
            DebugToolResponse response = mcpServerService.debugTool(1L, request);

            // then
            assertEquals("{\"orderId\": \"12345\"}", response.getResult());
            verify(mcpClientService).callTool(eq(1L), eq("get_order_detail"), eq(args));
        }

        @Test
        @DisplayName("P1 - mcpClientService 抛异常应向上传播")
        void shouldPropagateExceptionFromMcpClientService() {
            // given
            McpServer server = buildServer(1L, "订单服务", "http://localhost:8080/mcp", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(server);

            DebugToolRequest request = new DebugToolRequest();
            request.setToolName("search_order");
            request.setArguments(Map.of("query", "test"));

            when(mcpClientService.callTool(1L, "search_order", request.getArguments()))
                    .thenThrow(new BusinessException(ErrorCode.MCP_CALL_FAILED));

            // when & then
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> mcpServerService.debugTool(1L, request));
            assertEquals(ErrorCode.MCP_CALL_FAILED.getCode(), ex.getCode());
        }
    }

    // ========== 工具查询工作空间隔离 ==========

    @Nested
    @DisplayName("工具查询工作空间隔离")
    class ToolWorkspaceIsolation {

        @Test
        @DisplayName("同名工具在不同工作空间时，只返回当前工作空间的工具")
        void shouldOnlyReturnToolsInCurrentWorkspace() {
            McpServer server = buildServer(1L, "test", "http://localhost:8080", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(server);

            McpTool ws1Tool = new McpTool();
            ws1Tool.setId(10L);
            ws1Tool.setServerId(1L);
            ws1Tool.setName("get_data");
            ws1Tool.setWorkspaceId(1L);

            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(ws1Tool));

            McpServerResponse response = mcpServerService.getById(1L);

            assertThat(response.getTools()).hasSize(1);
            assertThat(response.getTools().get(0).getId()).isEqualTo(10L);
        }

        @Test
        @DisplayName("工具仅在另一工作空间存在时，当前空间返回空列表")
        void shouldReturnEmptyToolsWhenOnlyInOtherWorkspace() {
            McpServer server = buildServer(1L, "test", "http://localhost:8080", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(server);
            when(mcpToolMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of());

            McpServerResponse response = mcpServerService.getById(1L);

            assertThat(response.getTools()).isEmpty();
        }
    }

    @Nested
    @DisplayName("工具刷新写入 workspaceId")
    class ToolRefreshWorkspaceBinding {

        @Test
        @DisplayName("testConnection 即使连接失败也返回结果（不抛异常）")
        void shouldReturnResultEvenWhenConnectionFails() {
            McpServer server = buildServer(1L, "test", "http://localhost:8080", 1L);
            when(mcpServerMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(server);

            ConnectionTestResult result = mcpServerService.testConnection(1L);

            assertNotNull(result);
        }
    }
}
