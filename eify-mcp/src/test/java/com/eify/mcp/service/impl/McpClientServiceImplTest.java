package com.eify.mcp.service.impl;

import com.eify.common.context.CurrentContext;
import com.eify.common.exception.BusinessException;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.mapper.McpServerMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("McpClientServiceImpl")
class McpClientServiceImplTest {

    @Mock McpServerMapper mcpServerMapper;
    @Mock McpSyncClient mockClient;
    @Mock McpClient.SyncSpec syncSpec;

    MockedStatic<McpClient> mcClientStatic;
    Executor syncExecutor = Runnable::run;
    McpClientServiceImpl service;

    @BeforeEach
    void setUp() {
        mcClientStatic = mockStatic(McpClient.class);
        // Use lenient to avoid unnecessary stubbing complaints when
        // tests fail before reaching client creation
        mcClientStatic.when(() -> McpClient.sync(any())).thenReturn(syncSpec);
        lenient().when(syncSpec.capabilities(any())).thenReturn(syncSpec);
        lenient().when(syncSpec.build()).thenReturn(mockClient);

        service = new McpClientServiceImpl(mcpServerMapper);
        try {
            var field = McpClientServiceImpl.class.getDeclaredField("mcpExecutor");
            field.setAccessible(true);
            field.set(service, syncExecutor);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setUpContext() {
        CurrentContext.set(1L, 1L);
    }

    @AfterEach
    void tearDownContext() {
        CurrentContext.clear();
    }

    @AfterEach
    void tearDown() {
        mcClientStatic.close();
    }

    private McpServer buildServer(Long id, String endpoint) {
        McpServer server = new McpServer();
        server.setId(id);
        server.setName("test-server");
        server.setEndpoint(endpoint);
        server.setWorkspaceId(1L);
        server.setEnabled(1);
        return server;
    }

    // ==================== callTool ====================

    @Nested
    @DisplayName("callTool")
    class CallTool {

        @Test
        @DisplayName("成功调用工具并返回文本内容")
        void shouldCallToolAndReturnContent() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search", "summarize"));
            stubCallToolReturns("result text");

            String result = service.callTool(1L, "search", Map.of("query", "test"));

            assertThat(result).isEqualTo("result text");
        }

        @Test
        @DisplayName("工具返回空内容时返回空字符串")
        void shouldReturnEmptyStringWhenNoContent() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search"));
            var emptyResult = mock(McpSchema.CallToolResult.class);
            lenient().when(emptyResult.content()).thenReturn(List.of());
            when(mockClient.callTool(any())).thenReturn(emptyResult);

            String result = service.callTool(1L, "search", Map.of());

            assertThat(result).isEqualTo("");
        }

        @Test
        @DisplayName("Server 不存在时抛出 MCP_SERVER_NOT_FOUND")
        void shouldThrowWhenServerNotFound() {
            when(mcpServerMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> service.callTool(999L, "search", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("MCP 服务器不存在");
        }

        @Test
        @DisplayName("请求的工具不在可用工具列表中时抛出异常")
        void shouldThrowWhenToolNotAvailable() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search", "summarize"));

            assertThatThrownBy(() -> service.callTool(1L, "nonexistent", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("工具 [nonexistent] 在 MCP Server 上不存在");
        }

        @Test
        @DisplayName("IO 异常时重试并最终成功")
        void shouldRetryOnIOException() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search"));
            var retryResult = mock(McpSchema.CallToolResult.class);
            lenient().when(retryResult.content()).thenReturn(List.of(new McpSchema.TextContent("retry success")));
            // MCP SDK 抛出 RuntimeException，其 cause 为 IOException
            RuntimeException ioError = new RuntimeException(new java.io.IOException("network error"));
            doThrow(ioError)
                    .doThrow(ioError)
                    .doReturn(retryResult)
                    .when(mockClient).callTool(any());

            String result = service.callTool(1L, "search", Map.of());

            assertThat(result).isEqualTo("retry success");
        }

        @Test
        @DisplayName("重试耗尽后抛出 BusinessException")
        void shouldThrowAfterRetriesExhausted() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search"));
            RuntimeException ioError = new RuntimeException(new java.io.IOException("persistent error"));
            when(mockClient.callTool(any())).thenThrow(ioError);

            assertThatThrownBy(() -> service.callTool(1L, "search", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("工具 [search] 调用失败");
        }

        @Test
        @DisplayName("BusinessException 不重试，直接抛出")
        void shouldNotRetryBusinessException() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search"));
            when(mockClient.callTool(any()))
                    .thenThrow(new BusinessException(
                            com.eify.common.error.ErrorCode.MCP_CALL_FAILED, "业务错误"));

            assertThatThrownBy(() -> service.callTool(1L, "search", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("业务错误");
        }

        @Test
        @DisplayName("非 IO 异常（如 NPE）不重试，直接抛出")
        void shouldNotRetryNonIoException() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search"));
            when(mockClient.callTool(any())).thenThrow(new NullPointerException("unexpected null"));

            assertThatThrownBy(() -> service.callTool(1L, "search", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("工具 [search] 调用失败");

            // NPE 不重试，所以只调用 1 次（不是 3 次）
            verify(mockClient, times(1)).callTool(any());
        }

        @Test
        @DisplayName("跨工作空间 serverId 调用 callTool 时抛出 MCP_SERVER_NOT_FOUND")
        void shouldRejectCrossWorkspaceServerOnCallTool() {
            McpServer otherWsServer = buildServer(1L, "http://localhost:8080");
            otherWsServer.setWorkspaceId(2L);
            when(mcpServerMapper.selectById(1L)).thenReturn(otherWsServer);

            assertThatThrownBy(() -> service.callTool(1L, "search", Map.of()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("MCP 服务器不存在");
        }
    }

    // ==================== listTools ====================

    @Nested
    @DisplayName("listTools")
    class ListTools {

        @Test
        @DisplayName("成功获取工具列表")
        void shouldListTools() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search", "summarize", "calculate"));

            List<String> tools = service.listTools(1L);

            assertThat(tools).containsExactly("search", "summarize", "calculate");
        }

        @Test
        @DisplayName("无工具时返回空列表")
        void shouldReturnEmptyListWhenNoTools() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of());

            List<String> tools = service.listTools(1L);

            assertThat(tools).isEmpty();
        }

        @Test
        @DisplayName("Server 不存在时抛出异常")
        void shouldThrowWhenServerNotFound() {
            when(mcpServerMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> service.listTools(999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("MCP 服务器不存在");
        }

        @Test
        @DisplayName("IO 异常时重试并成功")
        void shouldRetryOnIOException() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            var tool = mock(McpSchema.Tool.class);
            lenient().when(tool.name()).thenReturn("recovered-tool");
            var listResult = mock(McpSchema.ListToolsResult.class);
            lenient().when(listResult.tools()).thenReturn(List.of(tool));
            RuntimeException ioError = new RuntimeException(new java.io.IOException("network error"));
            doThrow(ioError)
                    .doThrow(ioError)
                    .doReturn(listResult)
                    .when(mockClient).listTools(null);

            List<String> tools = service.listTools(1L);

            assertThat(tools).containsExactly("recovered-tool");
        }

        @Test
        @DisplayName("跨工作空间 serverId 调用 listTools 时抛出 MCP_SERVER_NOT_FOUND")
        void shouldRejectCrossWorkspaceServerOnListTools() {
            McpServer otherWsServer = buildServer(1L, "http://localhost:8080");
            otherWsServer.setWorkspaceId(2L);
            when(mcpServerMapper.selectById(1L)).thenReturn(otherWsServer);

            assertThatThrownBy(() -> service.listTools(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("MCP 服务器不存在");
        }
    }

    // ==================== tool cache ====================

    @Nested
    @DisplayName("tool cache")
    class ToolCache {

        @Test
        @DisplayName("工具列表缓存后第二次不重新获取")
        void shouldCacheToolList() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("t1", "t2"));
            stubCallToolReturns("ok");

            // 第一次调用：获取并缓存工具列表
            service.callTool(1L, "t1", Map.of());
            // 第二次调用：使用缓存的工具列表
            service.callTool(1L, "t2", Map.of());

            // listTools 只调用了 1 次（缓存生效）
            verify(mockClient, times(1)).listTools(null);
            // callTool 调用了 2 次
            verify(mockClient, times(2)).callTool(any());
        }

        @Test
        @DisplayName("evictToolCache 后重新获取工具列表")
        void shouldRefetchToolsAfterEviction() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("t1", "t2"));
            stubCallToolReturns("ok");

            // 第一次：缓存工具列表
            service.callTool(1L, "t1", Map.of());
            verify(mockClient, times(1)).listTools(null);

            // 清除工具缓存
            service.evictToolCache(1L);

            // 第二次：重新获取工具列表
            service.callTool(1L, "t2", Map.of());
            verify(mockClient, times(2)).listTools(null);
        }
    }

    // ==================== client eviction ====================

    @Nested
    @DisplayName("client eviction")
    class ClientEviction {

        @Test
        @DisplayName("重试失败后驱逐客户端并调用 closeGracefully")
        void shouldEvictClientOnRetryFailure() {
            when(mcpServerMapper.selectById(1L)).thenReturn(buildServer(1L, "http://localhost:8080"));
            stubListTools(List.of("search"));
            RuntimeException ioError = new RuntimeException(new java.io.IOException("network error"));
            when(mockClient.callTool(any())).thenThrow(ioError);

            assertThatThrownBy(() -> service.callTool(1L, "search", Map.of()))
                    .isInstanceOf(BusinessException.class);

            verify(mockClient, atLeastOnce()).closeGracefully();
        }
    }

    // ---- helper stubs (inline, not in @BeforeEach) ----

    private void stubListTools(List<String> toolNames) {
        var tools = toolNames.stream()
                .map(name -> {
                    var t = mock(McpSchema.Tool.class);
                    lenient().when(t.name()).thenReturn(name);
                    return t;
                })
                .toList();
        var result = mock(McpSchema.ListToolsResult.class);
        lenient().when(result.tools()).thenReturn(tools);
        lenient().when(mockClient.listTools(null)).thenReturn(result);
    }

    private void stubCallToolReturns(String text) {
        var content = new McpSchema.TextContent(text);
        var result = mock(McpSchema.CallToolResult.class);
        lenient().when(result.content()).thenReturn(List.of(content));
        lenient().when(mockClient.callTool(any())).thenReturn(result);
    }
}
