package com.eify.workflow.engine;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.eify.workflow.domain.entity.WorkflowNode;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 执行事件，通过 SSE 推送给前端。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExecutionEvent {

    public enum Type {
        EXECUTION_STARTED,
        NODE_STARTED,
        NODE_COMPLETED,
        NODE_FAILED,
        EXECUTION_COMPLETED,
        EXECUTION_FAILED
    }

    private String event;
    private Long executionId;
    private Long nodeId;
    private String nodeType;
    private String nodeName;
    private Map<String, Object> outputs;
    private String error;
    private long timestamp;

    public static ExecutionEvent executionStarted(Long executionId) {
        return ExecutionEvent.builder()
                .event(Type.EXECUTION_STARTED.name().toLowerCase())
                .executionId(executionId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExecutionEvent executionCompleted(Long executionId) {
        return ExecutionEvent.builder()
                .event(Type.EXECUTION_COMPLETED.name().toLowerCase())
                .executionId(executionId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExecutionEvent executionFailed(String error) {
        return ExecutionEvent.builder()
                .event(Type.EXECUTION_FAILED.name().toLowerCase())
                .error(error)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExecutionEvent nodeStarted(WorkflowNode node) {
        return ExecutionEvent.builder()
                .event(Type.NODE_STARTED.name().toLowerCase())
                .nodeId(node.getId())
                .nodeType(node.getType())
                .nodeName(node.getLabel())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExecutionEvent nodeCompleted(WorkflowNode node, NodeResult result) {
        return ExecutionEvent.builder()
                .event(Type.NODE_COMPLETED.name().toLowerCase())
                .nodeId(node.getId())
                .nodeType(node.getType())
                .nodeName(node.getLabel())
                .outputs(result.getOutputs())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static ExecutionEvent nodeFailed(WorkflowNode node, NodeResult result) {
        return ExecutionEvent.builder()
                .event(Type.NODE_FAILED.name().toLowerCase())
                .nodeId(node.getId())
                .nodeType(node.getType())
                .nodeName(node.getLabel())
                .error(result.getErrorMessage())
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
