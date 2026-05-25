package com.eify.mcp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import tools.jackson.databind.JsonNode;
import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.common.result.PageResult;
import com.eify.common.util.PageHelper;
import com.eify.common.workspace.WorkspaceGuard;
import com.eify.mcp.domain.dto.ConnectionTestResult;
import com.eify.mcp.domain.dto.McpServerCreateRequest;
import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.domain.dto.McpServerUpdateRequest;
import com.eify.mcp.domain.entity.AgentMcpTool;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.domain.entity.McpTool;
import com.eify.mcp.mapper.AgentMcpToolMapper;
import com.eify.mcp.mapper.McpServerMapper;
import com.eify.mcp.mapper.McpToolMapper;
import com.eify.mcp.domain.dto.DebugToolRequest;
import com.eify.mcp.domain.dto.DebugToolResponse;
import com.eify.mcp.service.McpClientService;
import com.eify.mcp.service.McpServerService;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.ClientCapabilities;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerServiceImpl implements McpServerService {

    private final McpServerMapper mcpServerMapper;
    private final McpToolMapper mcpToolMapper;
    private final AgentMcpToolMapper agentMcpToolMapper;
    private final McpClientService mcpClientService;

    @Override
    public PageResult<McpServerResponse> list(Integer page, Integer pageSize) {
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId())
                .orderByDesc(McpServer::getId);

        IPage<McpServer> iPage = mcpServerMapper.selectPage(
                PageHelper.toPage(page, pageSize), wrapper);

        List<McpServer> servers = iPage.getRecords();

        // 批量查询工具数量，避免 N+1
        Map<Long, Long> toolCountMap = Collections.emptyMap();
        if (!servers.isEmpty()) {
            List<Long> serverIds = servers.stream().map(McpServer::getId).collect(Collectors.toList());
            List<McpTool> allTools = mcpToolMapper.selectList(
                    new LambdaQueryWrapper<McpTool>()
                            .in(McpTool::getServerId, serverIds)
                            .eq(McpTool::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
            toolCountMap = allTools.stream()
                    .collect(Collectors.groupingBy(McpTool::getServerId, Collectors.counting()));
        }
        Map<Long, Long> finalToolCountMap = toolCountMap;

        List<McpServerResponse> list = servers.stream()
                .map(s -> toBasicResponse(s, finalToolCountMap.getOrDefault(s.getId(), 0L).intValue()))
                .collect(Collectors.toList());

        return PageResult.of(list, iPage.getTotal(), (int) iPage.getCurrent(), (int) iPage.getSize());
    }

    @Override
    public List<McpServerResponse> listToolsByWorkspace(Integer enabled) {
        Long workspaceId = com.eify.common.context.CurrentContext.getWorkspaceId();
        LambdaQueryWrapper<McpServer> wrapper = new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getWorkspaceId, workspaceId)
                .orderByAsc(McpServer::getName);
        if (enabled != null) {
            wrapper.eq(McpServer::getEnabled, enabled);
        }
        List<McpServer> servers = mcpServerMapper.selectList(wrapper);
        if (servers.isEmpty()) {
            return List.of();
        }

        // 批量查询所有工具
        List<Long> serverIds = servers.stream().map(McpServer::getId).collect(Collectors.toList());
        List<McpTool> allTools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .in(McpTool::getServerId, serverIds)
                        .eq(McpTool::getWorkspaceId, workspaceId));

        // 按 serverId 分组
        Map<Long, List<McpTool>> toolsByServer = allTools.stream()
                .collect(Collectors.groupingBy(McpTool::getServerId));

        return servers.stream()
                .map(server -> toToolsListResponse(server, toolsByServer.getOrDefault(server.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Override
    public McpServerResponse getById(Long id) {
        McpServer server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getId, id)
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (server == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        return toFullResponse(server);
    }

    @Override
    public McpServer getEntityById(Long id) {
        McpServer server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getId, id)
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (server == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }
        return server;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServer create(McpServerCreateRequest request) {
        WorkspaceGuard.checkNameUnique(mcpServerMapper,
                McpServer::getName, McpServer::getWorkspaceId, McpServer::getId,
                request.getName(), null, ErrorCode.PARAM_ERROR, "MCP Server 名称已存在");

        McpServer server = new McpServer();
        server.setName(request.getName());
        server.setEndpoint(request.getEndpoint());
        server.setEnabled(request.getEnabled() != null ? request.getEnabled() : 1);
        WorkspaceGuard.bind(server);
        mcpServerMapper.insert(server);
        return server;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public McpServer update(Long id, McpServerUpdateRequest request) {
        McpServer server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getId, id)
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (server == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        if (request.getName() != null) {
            WorkspaceGuard.checkNameUnique(mcpServerMapper,
                    McpServer::getName, McpServer::getWorkspaceId, McpServer::getId,
                    request.getName(), id, ErrorCode.PARAM_ERROR, "MCP Server 名称已存在");
            server.setName(request.getName());
        }
        if (request.getEndpoint() != null) {
            server.setEndpoint(request.getEndpoint());
        }
        if (request.getEnabled() != null) {
            server.setEnabled(request.getEnabled());
        }
        mcpServerMapper.updateById(server);
        return server;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        McpServer server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getId, id)
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (server == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        // 检查是否有 Agent 绑定该 Server 的工具
        List<McpTool> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, id)
                        .eq(McpTool::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (!tools.isEmpty()) {
            Set<Long> toolIds = tools.stream().map(McpTool::getId).collect(Collectors.toSet());
            Long bindCount = agentMcpToolMapper.selectCount(
                    new LambdaQueryWrapper<AgentMcpTool>()
                            .in(AgentMcpTool::getToolId, toolIds)
                            .eq(AgentMcpTool::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
            if (bindCount > 0) {
                throw new BusinessException(ErrorCode.MCP_SERVER_HAS_BINDINGS);
            }
        }

        // 检查是否有 Workflow ToolCall 节点引用此 Server
        int workflowRefs = mcpServerMapper.countWorkflowToolCallReferences(id);
        if (workflowRefs > 0) {
            throw new BusinessException(ErrorCode.MCP_SERVER_IN_USE_BY_WORKFLOW);
        }

        mcpServerMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConnectionTestResult testConnection(Long id) {
        McpServer server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getId, id)
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (server == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        long startTime = System.currentTimeMillis();
        try {
            var transport = HttpClientStreamableHttpTransport.builder(server.getEndpoint()).build();

            var client = McpClient.sync(transport)
                    .capabilities(ClientCapabilities.builder().build())
                    .build();

            try {
                client.initialize();
                var result = client.listTools(null);
                var tools = result != null ? result.tools() : null;
                long latencyMs = System.currentTimeMillis() - startTime;

                // 删除该 Server 的旧工具记录
                mcpToolMapper.delete(new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, id));

                // 保存新工具列表
                List<String> toolNames = new ArrayList<>();
                if (tools != null) {
                    for (var t : tools) {
                        McpTool tool = new McpTool();
                        tool.setServerId(id);
                        tool.setName(t.name());
                        tool.setDescription(t.description());
                        tool.setInputSchema(convertToJsonNode(t.inputSchema()));
                        WorkspaceGuard.bind(tool);
                        mcpToolMapper.insert(tool);
                        toolNames.add(t.name());
                    }
                }

                log.info("MCP Server 连通性测试成功: name={}, tools={}", server.getName(), toolNames.size());
                return ConnectionTestResult.success(latencyMs, toolNames.size(), toolNames);
            } finally {
                client.close();
            }
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - startTime;
            log.error("MCP Server 连通性测试失败: name={}, error={}", server.getName(), e.getMessage());
            return ConnectionTestResult.failure(latencyMs, e.getMessage());
        }
    }

    @Override
    public DebugToolResponse debugTool(Long id, DebugToolRequest request) {
        McpServer server = mcpServerMapper.selectOne(new LambdaQueryWrapper<McpServer>()
                .eq(McpServer::getId, id)
                .eq(McpServer::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));
        if (server == null) {
            throw new BusinessException(ErrorCode.MCP_SERVER_NOT_FOUND);
        }

        long startTime = System.currentTimeMillis();
        String result = mcpClientService.callTool(id, request.getToolName(), request.getArguments());
        int elapsedMs = (int) (System.currentTimeMillis() - startTime);

        return DebugToolResponse.builder()
                .result(result)
                .elapsedMs(elapsedMs)
                .build();
    }

    private McpServerResponse toBasicResponse(McpServer server, Integer toolCount) {
        return McpServerResponse.builder()
                .id(server.getId())
                .name(server.getName())
                .endpoint(server.getEndpoint())
                .enabled(server.getEnabled())
                .toolCount(toolCount)
                .createdAt(server.getCreatedAt())
                .updatedAt(server.getUpdatedAt())
                .build();
    }

    private McpServerResponse toFullResponse(McpServer server) {
        List<McpTool> tools = mcpToolMapper.selectList(
                new LambdaQueryWrapper<McpTool>()
                        .eq(McpTool::getServerId, server.getId())
                        .eq(McpTool::getWorkspaceId, com.eify.common.context.CurrentContext.getWorkspaceId()));

        List<McpServerResponse.McpToolResponse> toolResponses = tools.stream()
                .map(t -> McpServerResponse.McpToolResponse.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .description(t.getDescription())
                        .inputSchema(t.getInputSchema())
                        .build())
                .collect(Collectors.toList());

        return McpServerResponse.builder()
                .id(server.getId())
                .name(server.getName())
                .endpoint(server.getEndpoint())
                .enabled(server.getEnabled())
                .toolCount(toolResponses.size())
                .tools(toolResponses)
                .createdAt(server.getCreatedAt())
                .updatedAt(server.getUpdatedAt())
                .build();
    }

    private McpServerResponse toToolsListResponse(McpServer server, List<McpTool> tools) {
        boolean online = mcpClientService.isClientCached(server.getId());
        if (!online && server.getEnabled() != null && server.getEnabled() == 1) {
            online = true; // enabled 但未缓存 → 假定在线
        }

        List<McpServerResponse.McpToolResponse> toolResponses = tools.stream()
                .map(t -> McpServerResponse.McpToolResponse.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .description(t.getDescription())
                        .inputSchema(t.getInputSchema())
                        .build())
                .collect(Collectors.toList());

        return McpServerResponse.builder()
                .id(server.getId())
                .name(server.getName())
                .endpoint(server.getEndpoint())
                .enabled(server.getEnabled())
                .online(online)
                .toolCount(toolResponses.size())
                .tools(toolResponses)
                .createdAt(server.getCreatedAt())
                .updatedAt(server.getUpdatedAt())
                .build();
    }

    @SuppressWarnings("unchecked")
    private JsonNode convertToJsonNode(Object schema) {
        if (schema == null) return null;
        if (schema instanceof JsonNode node) return node;
        // MCP SDK returns Map, need to convert
        try {
            tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
            return mapper.valueToTree(schema);
        } catch (Exception e) {
            log.warn("转换 inputSchema 失败: {}", e.getMessage());
            return null;
        }
    }
}
