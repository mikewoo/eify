package com.eify.workflow.domain.config;

public record ConditionNodeConfig(String expression) implements NodeConfig {

    @Override
    public String type() {
        return "condition";
    }
}
