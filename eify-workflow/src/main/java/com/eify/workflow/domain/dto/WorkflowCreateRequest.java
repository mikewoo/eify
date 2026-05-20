package com.eify.workflow.domain.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class WorkflowCreateRequest {

    @NotBlank(message = "工作流名称不能为空")
    private String name;

    private String description;

    private Integer status;

    /** 全局变量定义：[{key, type, required, defaultVal}] */
    private JsonNode variables;

    @NotNull(message = "节点列表不能为空")
    @Valid
    private List<NodeItem> nodes;

    @NotNull(message = "连线列表不能为空")
    @Valid
    private List<EdgeItem> edges;

    @Data
    public static class NodeItem {
        /** 节点标识（工作流内唯一），用于 edges 中的引用 */
        @NotBlank(message = "节点 nodeKey 不能为空")
        private String nodeKey;

        @NotBlank(message = "节点类型不能为空")
        private String type;

        /** 节点显示名称 */
        private String name;

        private Double positionX;
        private Double positionY;
        private JsonNode config;
    }

    @Data
    public static class EdgeItem {
        /** 引用 node.nodeKey */
        @NotBlank(message = "源节点 nodeKey 不能为空")
        private String sourceNodeKey;

        /** 引用 node.nodeKey */
        @NotBlank(message = "目标节点 nodeKey 不能为空")
        private String targetNodeKey;

        /** 条件值（条件节点用于路由，普通连线为 null） */
        private String condition;

        /** 连线显示文字 */
        private String label;
    }
}
