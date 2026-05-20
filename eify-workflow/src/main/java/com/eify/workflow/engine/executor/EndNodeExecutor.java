package com.eify.workflow.engine.executor;

import com.eify.workflow.domain.config.EndNodeConfig;
import com.eify.workflow.domain.config.NodeConfigParser;
import com.eify.workflow.domain.entity.WorkflowNode;
import com.eify.workflow.engine.ExecutionContext;
import com.eify.workflow.engine.NodeExecutor;
import com.eify.workflow.engine.NodeResult;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

@Component
public class EndNodeExecutor implements NodeExecutor {

    @Override
    public String getType() {
        return "end";
    }

    @Override
    public NodeResult execute(WorkflowNode node, ExecutionContext ctx) {
        EndNodeConfig config = (EndNodeConfig) NodeConfigParser.parse("end", node.getConfig());

        Map<String, Object> outputs = Collections.emptyMap();
        if (config != null && config.outputKey() != null && !config.outputKey().isEmpty()) {
            // 以指定 key 输出整个变量快照
            outputs = Map.of(config.outputKey(), ctx.getVariableSnapshot());
        }

        return NodeResult.ok(outputs);
    }
}
