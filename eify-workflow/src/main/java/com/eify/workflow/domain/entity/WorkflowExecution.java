package com.eify.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.entity.BaseEntity;
import com.eify.common.handler.JsonNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_execution")
public class WorkflowExecution extends BaseEntity {

    private Long workflowId;

    /** 执行时工作流版本快照 */
    private Integer workflowVersion;

    /** 执行状态：running / completed / failed / cancelled */
    private String status;

    /**
     * 运行时变量快照（JSON）
     * <pre>
     * {"userInput": "...", "score": 85, "result": {"label": "pass"}}
     * </pre>
     */
    @TableField(value = "variables", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode variables;

    /** 当前执行到的节点 ID */
    private Long currentNodeId;

    /** 失败原因 */
    private String errorMessage;

    /** 开始执行时间 */
    private LocalDateTime startedAt;

    /** 执行结束时间 */
    private LocalDateTime completedAt;
}
