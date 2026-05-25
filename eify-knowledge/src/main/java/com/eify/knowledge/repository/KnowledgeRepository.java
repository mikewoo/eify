package com.eify.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.knowledge.domain.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 知识库数据访问层（MySQL）
 */
@Mapper
public interface KnowledgeRepository extends BaseMapper<KnowledgeBase> {

    @Update("UPDATE knowledge_base SET document_count = #{documentCount}, chunk_count = #{chunkCount} WHERE id = #{id}")
    void updateStats(@Param("id") Long id,
                    @Param("documentCount") Integer documentCount,
                    @Param("chunkCount") Integer chunkCount);

    @Update("UPDATE knowledge_base SET retrieval_count = retrieval_count + 1 WHERE id = #{id}")
    void incrementRetrievalCount(@Param("id") Long id);

    @Update("UPDATE knowledge_base SET updated_at = NOW() WHERE id = #{id}")
    void updateLastUpdateTime(@Param("id") Long id);

    @Update("UPDATE knowledge_base SET document_count = document_count + #{delta} WHERE id = #{id}")
    void incrementDocumentCount(@Param("id") Long id, @Param("delta") int delta);

    @Update("UPDATE knowledge_base SET chunk_count = chunk_count + #{delta} WHERE id = #{id}")
    void incrementChunkCount(@Param("id") Long id, @Param("delta") int delta);

    @org.apache.ibatis.annotations.Select("SELECT COUNT(*) FROM agent_knowledge WHERE knowledge_id = #{knowledgeId} AND deleted = 0")
    int countAgentReferences(@Param("knowledgeId") Long knowledgeId);

    @Update("UPDATE agent_knowledge SET deleted = 1, updated_at = NOW() WHERE knowledge_id = #{knowledgeId} AND deleted = 0")
    int softDeleteAgentKnowledgeByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
