package com.eify.workflow.domain.config;

import java.util.Map;

public record ToolCallNodeConfig(
        Long serverId,
        String toolName,
        Map<String, Object> argumentsTemplate,
        String outputKey
) implements NodeConfig {

    @Override
    public String type() {
        return "tool_call";
    }
}
