package com.eify.knowledge.service;

import com.eify.knowledge.repository.ChunkRepository;
import com.eify.knowledge.strategy.RetrievalStrategy;
import lombok.Data;

import java.util.List;

/**
 * 检索服务接口
 */
public interface RetrievalService {

    List<ChunkRepository.ChunkSearchResult> retrieve(RetrievalRequest request);

    List<ChunkRepository.ChunkSearchResult> retrieve(RetrievalRequest request, RetrievalStrategy strategy);

    String ragChat(RagRequest ragRequest);

    List<List<ChunkRepository.ChunkSearchResult>> batchRetrieve(List<RetrievalRequest> requests);

    List<String> getRetrievalSuggestions(String query, Long knowledgeId);

    RetrievalAnalysis analyzeRetrieval(List<RetrievalHistory> retrievalHistory);

    @Data
    class RetrievalRequest {
        private Long knowledgeId;
        private String query;
        private Integer topK = 5;
        private String strategy = "hybrid";
        private Float keywordWeight = 0.3f;
        private Float vectorWeight = 0.7f;
        private Long documentId;
        private List<String> tags;
    }

    @Data
    class RagRequest {
        private Long knowledgeId;
        private Long providerId;
        private String query;
        private String conversationHistory;
        private Integer maxContextLength = 2000;
        private Float temperature = 0.7f;
        private String systemPrompt;
    }

    @Data
    class RetrievalHistory {
        private Long knowledgeId;
        private String query;
        private List<String> retrievedContent;
        private String answer;
        private Long responseTime;
        private Float relevanceScore;
    }

    @Data
    class RetrievalAnalysis {
        private Float averageRelevance;
        private Long averageResponseTime;
        private Float retrievalEfficiency;
        private List<String> optimizationSuggestions;
        private List<String> commonIssues;
    }
}
