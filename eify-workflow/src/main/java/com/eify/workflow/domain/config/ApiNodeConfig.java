package com.eify.workflow.domain.config;

import java.util.Map;

public record ApiNodeConfig(
        String url,
        String method,
        Map<String, String> headers,
        Object body,
        String outputKey,
        Integer timeoutSeconds
) implements NodeConfig {

    @Override
    public String type() {
        return "api_call";
    }
}
