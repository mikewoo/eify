package com.eify.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.agent.domain.entity.AgentKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * Agent 与知识库关联 Mapper
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledge> {

    @Select("SELECT knowledge_id FROM agent_knowledge WHERE agent_id = #{agentId} AND deleted = 0")
    List<Long> selectKnowledgeIdsByAgentId(@Param("agentId") Long agentId);

    @Select("<script>SELECT agent_id, knowledge_id FROM agent_knowledge WHERE agent_id IN <foreach collection='agentIds' item='id' open='(' separator=',' close=')'>#{id}</foreach> AND deleted = 0</script>")
    List<AgentKnowledge> selectByAgentIds(@Param("agentIds") List<Long> agentIds);

    @Insert("<script>INSERT INTO agent_knowledge (agent_id, knowledge_id, creator_id, created_at, updated_at) VALUES " +
            "<foreach collection='knowledgeIds' item='kbId' separator=','>(#{agentId}, #{kbId}, NULL, NOW(), NOW())</foreach> " +
            "ON DUPLICATE KEY UPDATE deleted = 0, updated_at = NOW()</script>")
    int upsertKnowledgeIds(@Param("agentId") Long agentId, @Param("knowledgeIds") List<Long> knowledgeIds);

    @Update("<script>UPDATE agent_knowledge SET deleted = 1, updated_at = NOW() WHERE agent_id = #{agentId} AND deleted = 0 " +
            "<if test='keepIds != null and keepIds.size() > 0'>AND knowledge_id NOT IN <foreach collection='keepIds' item='id' open='(' separator=',' close=')'>#{id}</foreach></if></script>")
    int softDeleteExcept(@Param("agentId") Long agentId, @Param("keepIds") List<Long> keepIds);

    @Update("UPDATE agent_knowledge SET deleted = 1, updated_at = NOW() WHERE agent_id = #{agentId} AND deleted = 0")
    int softDeleteByAgentId(@Param("agentId") Long agentId);

    @Select("SELECT COUNT(*) FROM agent_knowledge WHERE knowledge_id = #{knowledgeId} AND deleted = 0")
    int countByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Update("UPDATE agent_knowledge SET deleted = 1, updated_at = NOW() WHERE knowledge_id = #{knowledgeId} AND deleted = 0")
    int softDeleteByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
