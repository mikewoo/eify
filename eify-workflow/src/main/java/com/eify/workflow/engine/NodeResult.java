package com.eify.workflow.engine;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.Map;

@Data
@Builder
public class NodeResult {

    private boolean success;
    private String nextHandle;
    private Map<String, Object> outputs;
    private String errorMessage;

    public static NodeResult ok(String nextHandle, Map<String, Object> outputs) {
        return NodeResult.builder()
                .success(true)
                .nextHandle(nextHandle != null ? nextHandle : "default")
                .outputs(outputs != null ? outputs : Collections.emptyMap())
                .build();
    }

    public static NodeResult ok(Map<String, Object> outputs) {
        return ok("default", outputs);
    }

    public static NodeResult fail(String errorMessage) {
        return NodeResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
