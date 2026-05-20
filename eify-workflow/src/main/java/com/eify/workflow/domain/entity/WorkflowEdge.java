package com.eify.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow_edge")
public class WorkflowEdge extends BaseEntity {

    private Long workflowId;

    private Long sourceNodeId;

    private Long targetNodeId;

    /** 源节点出口：default / true / false / 自定义分支名 */
    private String sourceHandle;

    /** 连线显示文字 */
    private String label;
}
