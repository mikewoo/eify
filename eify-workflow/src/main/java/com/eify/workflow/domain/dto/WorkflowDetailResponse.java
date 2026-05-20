package com.eify.workflow.domain.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import tools.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class WorkflowDetailResponse {

    private Long id;
    private String name;
    private String description;
    private Integer status;
    private Integer version;
    private JsonNode variables;

    private List<NodeDetail> nodes;
    private List<EdgeDetail> edges;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class NodeDetail {
        private Long id;
        private Long workflowId;
        private String nodeKey;
        private String type;
        private String name;
        private Double positionX;
        private Double positionY;
        private JsonNode config;
    }

    @Data
    @Builder
    public static class EdgeDetail {
        private Long id;
        private Long workflowId;
        private Long sourceNodeId;
        private Long targetNodeId;
        private String condition;
        private String label;
    }
}
