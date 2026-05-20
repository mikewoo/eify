package com.eify.mcp.controller;

import com.eify.common.result.PageResult;
import com.eify.common.result.Result;
import com.eify.mcp.domain.dto.ConnectionTestResult;
import com.eify.mcp.domain.dto.DebugToolRequest;
import com.eify.mcp.domain.dto.DebugToolResponse;
import com.eify.mcp.domain.dto.McpServerCreateRequest;
import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.domain.dto.McpServerUpdateRequest;
import com.eify.mcp.domain.entity.McpServer;
import com.eify.mcp.service.McpServerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "MCP Server 管理", description = "MCP Server CRUD 与连通性测试")
@RestController
@RequestMapping("/api/v1/mcp-servers")
public class McpServerController {

    private final McpServerService mcpServerService;

    public McpServerController(McpServerService mcpServerService) {
        this.mcpServerService = mcpServerService;
    }

    @Operation(summary = "分页查询 MCP Server", description = "分页查询 MCP Server 列表")
    @GetMapping
    public Result<PageResult<McpServerResponse>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        PageResult<McpServerResponse> result = mcpServerService.list(page, pageSize);
        return Result.success(result);
    }

    @Operation(summary = "查询 MCP Server 详情", description = "根据 ID 查询 MCP Server，含工具列表")
    @GetMapping("/{id}")
    public Result<McpServerResponse> getById(@PathVariable Long id) {
        McpServerResponse response = mcpServerService.getById(id);
        return Result.success(response);
    }

    @Operation(summary = "创建 MCP Server")
    @PostMapping
    public Result<McpServerResponse> create(@Valid @RequestBody McpServerCreateRequest request) {
        McpServer server = mcpServerService.create(request);
        return Result.success(toResponse(server));
    }

    @Operation(summary = "更新 MCP Server")
    @PutMapping("/{id}")
    public Result<McpServerResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody McpServerUpdateRequest request) {
        McpServer server = mcpServerService.update(id, request);
        return Result.success(toResponse(server));
    }

    @Operation(summary = "删除 MCP Server", description = "逻辑删除，若有 Agent 绑定则拒绝")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mcpServerService.delete(id);
        return Result.success();
    }

    @Operation(summary = "测试连通性", description = "连接 MCP Server，调用 tools/list 并保存工具列表")
    @PostMapping("/{id}/test")
    public Result<ConnectionTestResult> testConnection(@PathVariable Long id) {
        ConnectionTestResult result = mcpServerService.testConnection(id);
        return Result.success(result);
    }

    @Operation(summary = "调试工具", description = "调用指定工具并返回结果与耗时")
    @PostMapping("/{id}/debug")
    public Result<DebugToolResponse> debugTool(@PathVariable Long id,
                                               @Valid @RequestBody DebugToolRequest request) {
        DebugToolResponse response = mcpServerService.debugTool(id, request);
        return Result.success(response);
    }

    private McpServerResponse toResponse(McpServer entity) {
        return McpServerResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .endpoint(entity.getEndpoint())
                .enabled(entity.getEnabled())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
