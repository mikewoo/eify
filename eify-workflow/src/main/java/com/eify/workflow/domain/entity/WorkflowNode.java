package com.eify.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.entity.BaseEntity;
import com.eify.common.handler.JsonNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_node")
public class WorkflowNode extends BaseEntity {

    private Long workflowId;

    /** 节点标识（工作流内唯一），用于边的引用 */
    private String nodeKey;

    /** 节点类型：start / end / llm / api_call / condition / code */
    private String type;

    /** 节点显示名称 */
    private String label;

    /** 画布 X 坐标 */
    private Double positionX;

    /** 画布 Y 坐标 */
    private Double positionY;

    /**
     * 节点配置（JSON，不同 type 结构不同）
     * <ul>
     *   <li>llm: {"providerId": 1, "model": "gpt-4", "systemPrompt": "...", "temperature": 0.7}</li>
     *   <li>api_call: {"url": "https://...", "method": "POST", "headers": {}, "body": {}}</li>
     *   <li>condition: {"expression": "input.score > 80", "trueBranch": "nodeKey1", "falseBranch": "nodeKey2"}</li>
     *   <li>code: {"language": "javascript", "script": "return input.value * 2"}</li>
     * </ul>
     */
    @TableField(value = "config", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode config;
}
