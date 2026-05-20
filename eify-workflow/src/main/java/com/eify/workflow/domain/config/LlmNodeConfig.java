package com.eify.workflow.domain.config;

public record LlmNodeConfig(
        String model,
        Double temperature,
        Integer maxTokens,
        String systemPrompt,
        String userPrompt,
        String outputKey,
        Long providerId
) implements NodeConfig {

    @Override
    public String type() {
        return "llm";
    }
}
