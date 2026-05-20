package com.eify.workflow.domain.config;

import java.util.List;

public record StartNodeConfig(List<VariableDef> inputVariables) implements NodeConfig {

    @Override
    public String type() {
        return "start";
    }

    public record VariableDef(String key, String varType, boolean required, String defaultVal) {}
}
