package com.eify.knowledge.strategy;

import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import lombok.Data;

import java.util.List;

/**
 * 检索策略接口
 */
public interface RetrievalStrategy {

    /**
     * 向量检索（pgvector <=> 算子）
     */
    List<ChunkRepository.ChunkSearchResult> vectorRetrieval(String query, KnowledgeBase knowledge, int topK);

    /**
     * 关键词检索（ILIKE 文本匹配）
     */
    List<ChunkRepository.ChunkSearchResult> keywordRetrieval(String keyword, KnowledgeBase knowledge, int topK);

    /**
     * 混合检索（向量 + 关键词加权合并）
     */
    List<ChunkRepository.ChunkSearchResult> hybridRetrieval(
        String query,
        KnowledgeBase knowledge,
        int topK,
        float keywordWeight,
        float vectorWeight
    );

    /**
     * 带过滤条件的检索
     */
    List<ChunkRepository.ChunkSearchResult> filteredRetrieval(
        String query,
        KnowledgeBase knowledge,
        int topK,
        RetrievalFilters filters
    );

    /**
     * 检索结果增强（添加置信度等元数据）
     */
    List<EnhancedChunk> enhanceResults(List<ChunkRepository.ChunkSearchResult> chunks, String query);
}

@Data
class RetrievalFilters {
    private List<String> tags;
    private Long documentId;
    private Long afterTime;
    private Long beforeTime;
    private Float minScore;
}

@Data
class EnhancedChunk {
    private Long id;
    private Long knowledgeId;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private Float relevanceScore;
    private Float similarity;
    private String matchType;
    private Float confidenceScore;
}
