package com.eify.knowledge.service;

import com.eify.knowledge.domain.entity.DocumentChunk;
import com.eify.knowledge.repository.ChunkRepository;

import java.util.List;

/**
 * 向量分块服务接口
 * 封装 ChunkRepository，供 ChatService 等外部模块调用
 */
public interface ChunkService {

    /**
     * 向量相似度检索
     */
    List<ChunkRepository.ChunkSearchResult> search(Long knowledgeId, float[] query, int topK);

    /**
     * 按文档ID查询所有分块（按 chunk_index 排序）
     */
    List<DocumentChunk> findByDocumentId(Long documentId);

    /**
     * 按文档删除分块
     */
    int deleteByDocumentId(Long documentId);

    /**
     * 按知识库删除分块
     */
    int deleteByKnowledgeId(Long knowledgeId);
}
