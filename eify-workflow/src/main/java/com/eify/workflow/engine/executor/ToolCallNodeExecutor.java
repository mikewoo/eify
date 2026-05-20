package com.eify.workflow.engine.executor;

import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.service.McpClientService;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.config.ToolCallNodeConfig;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolCallNodeExecutor implements NodeExecutor {

    private final McpServerMapper mcpServerMapper;
    private final McpClientService mcpClientService;

    @Override
    public String getType() {
        return "tool_call";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        ToolCallNodeConfig config = (ToolCallNodeConfig) NodeConfigParser.parse("tool_call", node.getConfig());
        if (config == null) {
            return NodeResult.fail("MCP 工具节点配置为空");
        }

        McpServer server = mcpServerMapper.selectById(config.serverId());
        if (server == null || server.getEnabled() == 0) {
            return NodeResult.fail("MCP Server 不存在或已禁用: id=" + config.serverId());
        }

        Map<String, Object> resolvedArgs = new HashMap<>();
        if (config.argumentsTemplate() != null) {
            for (var entry : config.argumentsTemplate().entrySet()) {
                String resolved = ctx.resolveTemplate(String.valueOf(entry.getValue()));
                resolvedArgs.put(entry.getKey(), resolved);
            }
        }

        try {
            log.info("[ToolCall] 调用 MCP 工具 - nodeId={}, serverId={}, toolName={}, args={}",
                    node.getId(), config.serverId(), config.toolName(), resolvedArgs);

            String result = mcpClientService.callTool(config.serverId(), config.toolName(), resolvedArgs);

            log.info("[ToolCall] MCP 工具调用成功 - nodeId={}, toolName={}, resultLength={}",
                    node.getId(), config.toolName(), result != null ? result.length() : 0);

            Map<String, Object> outputs = new HashMap<>();
            outputs.put(config.outputKey(), result);
            return NodeResult.ok(outputs);
        } catch (Exception e) {
            String errMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.error("[ToolCall] MCP 工具调用失败 - nodeId={}, toolName={}, error={}",
                    node.getId(), config.toolName(), errMsg, e);
            return NodeResult.fail("工具 [" + config.toolName() + "] 调用失败: " + errMsg);
        }
    }
}
