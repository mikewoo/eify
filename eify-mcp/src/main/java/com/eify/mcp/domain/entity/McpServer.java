package com.eify.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_server")
public class McpServer extends BaseEntity implements WorkspaceAware {

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    @TableField("workspace_id")
    private Long workspaceId;

    @TableField("endpoint")
    private String endpoint;

    @TableField("enabled")
    private Integer enabled;
}
