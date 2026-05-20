package com.eify.knowledge.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eify.knowledge.domain.entity.Document;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文档数据访问层（MySQL）
 */
@Mapper
public interface DocumentRepository extends BaseMapper<Document> {

    @Update("UPDATE document SET process_status = #{status}, " +
            "error_message = #{errorMessage} " +
            "WHERE id = #{id}")
    void updateProcessStatus(
        @Param("id") Long id,
        @Param("status") Integer status,
        @Param("errorMessage") String errorMessage
    );

    @Update("UPDATE document SET chunk_count = #{chunkCount} WHERE id = #{id}")
    void updateChunkCount(@Param("id") Long id, @Param("chunkCount") Integer chunkCount);

    @Update("UPDATE document SET char_count = #{charCount} WHERE id = #{id}")
    void updateCharCount(@Param("id") Long id, @Param("charCount") Integer charCount);

    @Select("SELECT * FROM document WHERE knowledge_id = #{knowledgeId} AND deleted = 0 ORDER BY created_at DESC")
    List<Document> findByKnowledgeId(@Param("knowledgeId") Long knowledgeId);

    @Select("SELECT COUNT(*) FROM document WHERE knowledge_id = #{knowledgeId} AND deleted = 0")
    Integer countByKnowledgeId(@Param("knowledgeId") Long knowledgeId);
}
