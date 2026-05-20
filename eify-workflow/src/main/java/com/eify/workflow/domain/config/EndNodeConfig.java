package com.eify.workflow.domain.config;

public record EndNodeConfig(String outputKey) implements NodeConfig {

    @Override
    public String type() {
        return "end";
    }
}
