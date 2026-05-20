package com.eify.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_session")
public class Conversation extends BaseEntity implements WorkspaceAware {

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    @TableField("workspace_id")
    private Long workspaceId;

    /**
     * Agent ID（与 workflowId 互斥但至少选一）
     */
    private Long agentId;

    /**
     * 工作流 ID（直接绑定工作流，与 agentId 互斥但至少选一）
     */
    private Long workflowId;

    /**
     * 对话标题
     */
    private String title;

    /**
     * 状态：0=已归档，1=进行中
     */
    private Integer status;

    /**
     * 创建人 ID
     */
    private Long creatorId;
}
