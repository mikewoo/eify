package com.eify.workflow.engine;

import com.eify.common.error.ErrorCode;
import com.eify.common.exception.BusinessException;
import com.eify.workflow.domain.entity.Workflow;
import com.eify.workflow.domain.entity.WorkflowEdge;
import com.eify.workflow.domain.entity.WorkflowNode;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 一次工作流执行的完整状态快照。构造时从 DB 数据加载不可变部分，运行时修改可变状态。
 */
public class ExecutionContext {

    // ---- 不可变快照 ----
    private final Long executionId;
    private final Long workflowId;
    private final Integer workflowVersion;
    private final Map<Long, WorkflowNode> nodeMap;
    private final Map<Long, List<WorkflowEdge>> outgoingEdges;
    private final WorkflowNode startNode;
    private final VariableResolver variableResolver;

    // ---- 运行时可变状态 ----
    private Long currentNodeId;
    private NodeResult lastResult;
    private String status;
    private String errorMessage;
    private LocalDateTime startedAt;

    public ExecutionContext(Long executionId, Workflow workflow,
                            List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        this.executionId = executionId;
        this.workflowId = workflow.getId();
        this.workflowVersion = workflow.getVersion();
        this.status = "running";
        this.startedAt = LocalDateTime.now();

        this.nodeMap = nodes.stream().collect(Collectors.toMap(
                WorkflowNode::getId, Function.identity()));

        this.outgoingEdges = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            this.outgoingEdges
                    .computeIfAbsent(edge.getSourceNodeId(), k -> new java.util.ArrayList<>())
                    .add(edge);
        }

        this.startNode = nodes.stream()
                .filter(n -> "start".equals(n.getType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.WORKFLOW_CONFIG_INVALID, "工作流缺少 start 节点"));
        this.currentNodeId = startNode.getId();

        this.variableResolver = new VariableResolver();
        if (workflow.getVariables() != null) {
            this.variableResolver.loadDefaults(workflow.getVariables());
        }
    }

    // ---- 导航 ----

    public boolean hasNextNode() {
        List<WorkflowEdge> edges = outgoingEdges.get(currentNodeId);
        return edges != null && !edges.isEmpty();
    }

    public Long resolveNextNode(String handle) {
        List<WorkflowEdge> edges = outgoingEdges.get(currentNodeId);
        if (edges == null || edges.isEmpty()) {
            return null;
        }
        // 精确匹配
        for (WorkflowEdge edge : edges) {
            if (handle.equals(edge.getSourceHandle())) {
                return edge.getTargetNodeId();
            }
        }
        // 回退到 default
        for (WorkflowEdge edge : edges) {
            if ("default".equals(edge.getSourceHandle())) {
                return edge.getTargetNodeId();
            }
        }
        return null;
    }

    public void advanceTo(Long nodeId) {
        this.currentNodeId = nodeId;
    }

    // ---- 变量 ----

    public void setVariable(String key, Object value) {
        variableResolver.set(key, value);
    }

    public Object getVariable(String key) {
        return variableResolver.get(key);
    }

    public String resolveTemplate(String template) {
        return variableResolver.resolve(template);
    }

    public Map<String, Object> getVariableSnapshot() {
        return variableResolver.snapshot();
    }

    // ---- getters / setters ----

    public Long getExecutionId() { return executionId; }
    public Long getWorkflowId() { return workflowId; }
    public Integer getWorkflowVersion() { return workflowVersion; }
    public Long getCurrentNodeId() { return currentNodeId; }
    public WorkflowNode getCurrentNode() { return nodeMap.get(currentNodeId); }
    public WorkflowNode getNode(Long nodeId) { return nodeMap.get(nodeId); }
    public String getStatus() { return status; }
    public void markCompleted() { this.status = "completed"; }
    public void markFailed(String msg) { this.status = "failed"; this.errorMessage = msg; }
    public String getErrorMessage() { return errorMessage; }
    public NodeResult getLastResult() { return lastResult; }
    public void setLastResult(NodeResult r) { this.lastResult = r; }
    public LocalDateTime getStartedAt() { return startedAt; }
}
