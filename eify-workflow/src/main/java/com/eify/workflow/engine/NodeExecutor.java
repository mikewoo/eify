package com.eify.workflow.engine;

import com.eify.workflow.domain.entity.WorkflowNode;

public interface NodeExecutor {

    /** 支持的节点类型 */
    String getType();

    /** 执行节点 */
    NodeResult execute(WorkflowNode node, ExecutionContext ctx);
}
