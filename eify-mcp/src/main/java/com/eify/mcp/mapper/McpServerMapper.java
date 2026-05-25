package com.eify.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.mcp.domain.entity.McpServer;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface McpServerMapper extends BaseMapper<McpServer> {

    @Select("SELECT COUNT(*) FROM ai_workflow_node n " +
            "INNER JOIN ai_workflow w ON n.workflow_id = w.id " +
            "WHERE n.type = 'tool_call' " +
            "AND JSON_EXTRACT(n.config, '$.serverId') = #{serverId} " +
            "AND n.deleted = 0 " +
            "AND w.deleted = 0")
    int countWorkflowToolCallReferences(@Param("serverId") Long serverId);
}
