package com.eify.mcp.service;

import java.util.List;
import java.util.Map;

public interface McpClientService {

    String callTool(Long serverId, String toolName, Map<String, Object> arguments);

    List<String> listTools(Long serverId);
}
