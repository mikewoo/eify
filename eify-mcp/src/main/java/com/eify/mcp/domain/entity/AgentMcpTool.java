package com.eify.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;

@Data
@TableName("agent_mcp_tool")
public class AgentMcpTool extends BaseEntity implements WorkspaceAware {

    @TableField("agent_id")
    private Long agentId;

    @TableField("tool_id")
    private Long toolId;

    @TableField("workspace_id")
    private Long workspaceId;
}
