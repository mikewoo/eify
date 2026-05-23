package com.eify.mcp.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import tools.jackson.databind.JsonNode;
import com.eify.common.entity.BaseEntity;
import com.eify.common.handler.JsonNodeTypeHandler;
import com.eify.common.workspace.WorkspaceAware;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mcp_tool")
public class McpTool extends BaseEntity implements WorkspaceAware {

    @TableField("server_id")
    private Long serverId;

    @TableField("name")
    private String name;

    @TableField("description")
    private String description;

    /**
     * 工具输入参数 Schema（JSON）
     * <pre>
     * {
     *   "type": "object",
     *   "properties": {
     *     "query": {"type": "string", "description": "搜索关键词"}
     *   },
     *   "required": ["query"]
     * }
     * </pre>
     */
    @TableField(value = "input_schema", typeHandler = JsonNodeTypeHandler.class)
    private JsonNode inputSchema;

    @TableField("workspace_id")
    private Long workspaceId;
}
