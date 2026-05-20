package com.eify.workflow.engine.executor;

import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.config.StartNodeConfig;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class StartNodeExecutor implements NodeExecutor {

    @Override
    public String getType() {
        return "start";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        StartNodeConfig config = (StartNodeConfig) NodeConfigParser.parse("start", node.getConfig());
        Map<String, Object> outputs = new HashMap<>();

        if (config != null && config.inputVariables() != null) {
            for (var def : config.inputVariables()) {
                Object value = ctx.getVariable(def.key());
                if (value == null) {
                    value = def.defaultVal();
                }
                outputs.put(def.key(), value);
            }
        }

        return NodeResult.ok(outputs);
    }
}
