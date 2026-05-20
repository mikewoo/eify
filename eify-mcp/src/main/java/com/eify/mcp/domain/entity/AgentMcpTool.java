package com.eify.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.eify.common.entity.BaseEntity;
import lombok.Data;

@Data
@TableName("agent_mcp_tool")
public class AgentMcpTool extends BaseEntity {

    @TableField("agent_id")
    private Long agentId;

    @TableField("tool_id")
    private Long toolId;
}
