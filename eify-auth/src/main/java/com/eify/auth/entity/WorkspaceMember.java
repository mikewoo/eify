package com.eify.auth.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_workspace_member")
public class WorkspaceMember extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("user_id")
    private Long userId;

    /** owner / admin / member */
    @TableField("role")
    private String role;

    @TableField("joined_at")
    private LocalDateTime joinedAt;
}
