package com.eify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.agent.domain.entity.Agent;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * Agent Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<Agent> {

    @Select("SELECT COUNT(*) FROM ai_chat_session WHERE agent_id = #{agentId} AND deleted = 0 AND status = 1")
    int countConversationReferences(@Param("agentId") Long agentId);

    @Select("<script>SELECT id, name FROM knowledge_base WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> AND deleted = 0</script>")
    @MapKey("id")
    Map<Long, Map<String, Object>> selectKnowledgeBaseNames(@Param("ids") List<Long> ids);
}
