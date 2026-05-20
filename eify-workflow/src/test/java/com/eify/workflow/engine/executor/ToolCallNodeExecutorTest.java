package com.eify.workflow.engine.executor;

import tools.jackson.databind.ObjectMapper;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.service.McpClientService;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ToolCallNodeExecutor")
class ToolCallNodeExecutorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    McpServerMapper mcpServerMapper;

    @Mock
    McpClientService mcpClientService;

    ToolCallNodeExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new ToolCallNodeExecutor(mcpServerMapper, mcpClientService);
    }

    private ExecutionContext buildContext() {
        Workflow workflow = new Workflow();
        workflow.setId(1L);
        workflow.setVersion(1);
        WorkflowNode startNode = new WorkflowNode();
        startNode.setId(1L);
        startNode.setNodeKey("start");
        startNode.setType("start");
        startNode.setLabel("开始");
        return new ExecutionContext(100L, workflow,
                List.of(startNode), Collections.emptyList());
    }

    private WorkflowNode buildToolCallNode(Map<String, Object> configMap) throws Exception {
        WorkflowNode node = new WorkflowNode();
        node.setId(3L);
        node.setNodeKey("tool_1");
        node.setType("tool_call");
        node.setLabel("工具调用");
        node.setConfig(MAPPER.readTree(MAPPER.writeValueAsString(configMap)));
        return node;
    }

    @Nested
    @DisplayName("getType")
    class GetTypeTests {

        @Test
        @DisplayName("应返回 tool_call")
        void shouldReturnToolCall() {
            assertThat(executor.getType()).isEqualTo("tool_call");
        }
    }

    @Nested
    @DisplayName("execute")
    class ExecuteTests {

        @Test
        @DisplayName("config 为 null 时应返回失败")
        void shouldReturnFailWhenConfigNull() {
            WorkflowNode node = new WorkflowNode();
            node.setId(3L);
            node.setType("tool_call");
            ExecutionContext ctx = buildContext();

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("配置为空");
        }

        @Test
        @DisplayName("MCP Server 不存在时应返回失败")
        void shouldReturnFailWhenServerNotFound() throws Exception {
            WorkflowNode node = buildToolCallNode(Map.of(
                    "serverId", 999L,
                    "toolName", "getOrder",
                    "outputKey", "orderInfo"
            ));
            ExecutionContext ctx = buildContext();
            when(mcpServerMapper.selectById(999L)).thenReturn(null);

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("MCP Server 不存在或已禁用");
        }

        @Test
        @DisplayName("MCP Server 已禁用时应返回失败")
        void shouldReturnFailWhenServerDisabled() throws Exception {
            WorkflowNode node = buildToolCallNode(Map.of(
                    "serverId", 1L,
                    "toolName", "getOrder",
                    "outputKey", "orderInfo"
            ));
            ExecutionContext ctx = buildContext();
            McpServer server = new McpServer();
            server.setId(1L);
            server.setEnabled(0);
            when(mcpServerMapper.selectById(1L)).thenReturn(server);

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("已禁用");
        }

        @Test
        @DisplayName("工具调用成功应返回结果")
        void shouldReturnToolResultOnSuccess() throws Exception {
            WorkflowNode node = buildToolCallNode(Map.of(
                    "serverId", 1L,
                    "toolName", "getOrder",
                    "outputKey", "orderInfo",
                    "argumentsTemplate", Map.of("orderId", "ORD-001")
            ));
            ExecutionContext ctx = buildContext();
            McpServer server = new McpServer();
            server.setId(1L);
            server.setEnabled(1);
            when(mcpServerMapper.selectById(1L)).thenReturn(server);
            when(mcpClientService.callTool(eq(1L), eq("getOrder"), anyMap()))
                    .thenReturn("{\"status\": \"shipped\"}");

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getOutputs()).containsEntry("orderInfo", "{\"status\": \"shipped\"}");
        }

        @Test
        @DisplayName("工具调用异常应返回失败")
        void shouldReturnFailOnToolException() throws Exception {
            WorkflowNode node = buildToolCallNode(Map.of(
                    "serverId", 1L,
                    "toolName", "getOrder",
                    "outputKey", "orderInfo"
            ));
            ExecutionContext ctx = buildContext();
            McpServer server = new McpServer();
            server.setId(1L);
            server.setEnabled(1);
            when(mcpServerMapper.selectById(1L)).thenReturn(server);
            when(mcpClientService.callTool(anyLong(), anyString(), anyMap()))
                    .thenThrow(new RuntimeException("MCP connection timeout"));

            NodeResult result = executor.execute(node, ctx);

            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorMessage()).contains("getOrder");
            assertThat(result.getErrorMessage()).contains("MCP connection timeout");
        }
    }
}
