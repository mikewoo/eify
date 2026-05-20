package com.eify.workflow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import com.eify.common.handler.JsonNodeTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workflow")
public class Workflow extends BaseEntity implements WorkspaceAware {

    private String name;

    @TableField("workspace_id")
    private Long workspaceId;

    private String description;

    /** 状态：0=草稿，1=已发布，2=已禁用 */
    private Integer status;

    /** 版本号，每次发布 +1 */
    private Integer version;

    /**
     * 全局变量定义（JSON 数组）
     * <pre>
     * [
     *   {"key": "userInput", "type": "string", "required": true, "defaultVal": null},
     *   {"key": "maxScore", "type": "number", "required": false, "defaultVal": 100}
     * ]
     * </pre>
     */
    @TableField(value = "variables", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode variables;
}
