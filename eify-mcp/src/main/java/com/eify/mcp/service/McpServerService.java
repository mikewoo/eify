package com.eify.mcp.service;

import com.eify.common.result.PageResult;
import com.eify.mcp.domain.dto.ConnectionTestResult;
import com.eify.mcp.domain.dto.DebugToolRequest;
import com.eify.mcp.domain.dto.DebugToolResponse;
import com.eify.mcp.domain.dto.McpServerCreateRequest;
import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.domain.dto.McpServerUpdateRequest;
import com.eify.mcp.domain.entity.McpServer;

import java.util.List;

public interface McpServerService {

    PageResult<McpServerResponse> list(Integer page, Integer pageSize);

    /**
     * 批量查询当前工作空间下所有 Server 及其工具列表（含 online 状态）。
     *
     * @param enabled 筛选 enabled 状态（null=全部，1=仅启用）
     * @return Server + 工具完整信息列表
     */
    List<McpServerResponse> listToolsByWorkspace(Integer enabled);

    McpServerResponse getById(Long id);

    McpServer getEntityById(Long id);

    McpServer create(McpServerCreateRequest request);

    McpServer update(Long id, McpServerUpdateRequest request);

    void delete(Long id);

    ConnectionTestResult testConnection(Long id);

    DebugToolResponse debugTool(Long id, DebugToolRequest request);
}
