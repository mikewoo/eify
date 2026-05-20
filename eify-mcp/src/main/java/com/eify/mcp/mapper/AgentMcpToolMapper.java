package com.eify.mcp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.mcp.domain.entity.AgentMcpTool;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentMcpToolMapper extends BaseMapper<AgentMcpTool> {

    @Select("SELECT tool_id FROM agent_mcp_tool WHERE agent_id = #{agentId}")
    List<Long> selectToolIdsByAgentId(@Param("agentId") Long agentId);

    @Select("<script>SELECT agent_id, tool_id FROM agent_mcp_tool WHERE agent_id IN <foreach collection='agentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<AgentMcpTool> selectByAgentIds(@Param("agentIds") List<Long> agentIds);

    @Delete("DELETE FROM agent_mcp_tool WHERE agent_id = #{agentId}")
    int deleteByAgentId(@Param("agentId") Long agentId);

    @Insert("<script>INSERT INTO agent_mcp_tool (agent_id, tool_id, created_at) VALUES " +
            "<foreach collection='toolIds' item='toolId' separator=','>(#{agentId}, #{toolId}, NOW())</foreach></script>")
    int batchInsert(@Param("agentId") Long agentId, @Param("toolIds") List<Long> toolIds);
}
