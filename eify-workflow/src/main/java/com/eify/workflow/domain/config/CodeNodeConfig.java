package com.eify.workflow.domain.config;

public record CodeNodeConfig(String language, String code, String outputKey) implements NodeConfig {

    @Override
    public String type() {
        return "code";
    }
}
