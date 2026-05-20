package com.eify.mcp.service;

import com.eify.common.result.PageResult;
import com.eify.mcp.domain.dto.ConnectionTestResult;
import com.eify.mcp.domain.dto.DebugToolRequest;
import com.eify.mcp.domain.dto.DebugToolResponse;
import com.eify.mcp.domain.dto.McpServerCreateRequest;
import com.eify.mcp.domain.dto.McpServerResponse;
import com.eify.mcp.domain.dto.McpServerUpdateRequest;
import com.eify.mcp.domain.entity.McpServer;

public interface McpServerService {

    PageResult<McpServerResponse> list(Integer page, Integer pageSize);

    McpServerResponse getById(Long id);

    McpServer getEntityById(Long id);

    McpServer create(McpServerCreateRequest request);

    McpServer update(Long id, McpServerUpdateRequest request);

    void delete(Long id);

    ConnectionTestResult testConnection(Long id);

    DebugToolResponse debugTool(Long id, DebugToolRequest request);
}
