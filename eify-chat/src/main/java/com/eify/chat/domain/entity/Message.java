package com.eify.chat.domain.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 消息实体
 * <p>
 * 对应表：ai_chat_message
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_chat_message")
public class Message extends BaseEntity implements WorkspaceAware {

    /**
     * 会话ID
     */
    @TableField("session_id")
    private Long sessionId;

    /**
     * 所属工作空间ID（从会话继承，用于多租户数据隔离）
     */
    @TableField("workspace_id")
    private Long workspaceId;

    /**
     * 角色：user/assistant/system
     */
    private String role;

    /**
     * 消息内容
     */
    private String content;

    /**
     * token 数（对应数据库字段 token_count）
     */
    @TableField("token_count")
    private Integer tokensUsed;
}
