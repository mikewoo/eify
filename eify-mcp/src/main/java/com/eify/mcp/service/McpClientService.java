package com.eify.mcp.service;

import java.util.List;
import java.util.Map;

public interface McpClientService {

    String callTool(Long serverId, String toolName, Map<String, Object> arguments);

    List<String> listTools(Long serverId);

    /**
     * 检查指定 serverId 的 MCP 客户端是否在连接缓存中（最近 5 分钟内连接成功过）。
     */
    boolean isClientCached(Long serverId);
}
