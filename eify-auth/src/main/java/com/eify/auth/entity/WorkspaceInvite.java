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
@TableName("ai_workspace_invite")
public class WorkspaceInvite extends BaseEntity {

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("code")
    private String code;

    @TableField("expires_at")
    private LocalDateTime expiresAt;

    @TableField("max_uses")
    private Integer maxUses;

    @TableField("use_count")
    private Integer useCount;

    @TableField("enabled")
    private Integer enabled;
}
