package com.eify.workflow.domain.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowUpdateRequest {

    private String name;
    private String description;
    private Integer status;
    private JsonNode variables;

    @Valid
    private List<NodeItem> nodes;

    @Valid
    private List<EdgeItem> edges;

    @Data
    public static class NodeItem {
        private String nodeKey;
        private String type;
        private String name;
        private Double positionX;
        private Double positionY;
        private JsonNode config;
    }

    @Data
    public static class EdgeItem {
        private String sourceNodeKey;
        private String targetNodeKey;
        private String condition;
        private String label;
    }
}
