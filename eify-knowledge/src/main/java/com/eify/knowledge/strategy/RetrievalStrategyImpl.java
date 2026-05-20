package com.eify.knowledge.strategy;

import com.eify.knowledge.domain.entity.KnowledgeBase;
import com.eify.knowledge.repository.ChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 检索策略实现 — 基于 pgvector 向量检索 + ILIKE 关键词检索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RetrievalStrategyImpl implements RetrievalStrategy {

    private final ChunkRepository chunkRepository;
    private final EmbeddingStrategy embeddingStrategy;

    @Override
    public List<ChunkRepository.ChunkSearchResult> vectorRetrieval(
            String query, KnowledgeBase knowledge, int topK) {
        try {
            float[] queryVector = embeddingStrategy.embed(query);
            return chunkRepository.search(knowledge.getId(), queryVector, topK);
        } catch (Exception e) {
            log.error("Vector retrieval failed for knowledge={}", knowledge.getId(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<ChunkRepository.ChunkSearchResult> keywordRetrieval(
            String keyword, KnowledgeBase knowledge, int topK) {
        try {
            return chunkRepository.searchByKeyword(knowledge.getId(), keyword, topK);
        } catch (Exception e) {
            log.error("Keyword retrieval failed for knowledge={}", knowledge.getId(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<ChunkRepository.ChunkSearchResult> hybridRetrieval(
            String query, KnowledgeBase knowledge, int topK,
            float keywordWeight, float vectorWeight) {
        try {
            List<ChunkRepository.ChunkSearchResult> vectorResults =
                    vectorRetrieval(query, knowledge, topK * 2);
            List<ChunkRepository.ChunkSearchResult> keywordResults =
                    keywordRetrieval(query, knowledge, topK * 2);

            float totalWeight = keywordWeight + vectorWeight;
            if (totalWeight == 0) totalWeight = 1;
            float normKeywordW = keywordWeight / totalWeight;
            float normVectorW = vectorWeight / totalWeight;

            float maxVectorSim = vectorResults.stream()
                    .map(ChunkRepository.ChunkSearchResult::similarity)
                    .max(Float::compare).orElse(1f);

            Map<Long, MergedResult> merged = new LinkedHashMap<>();

            for (var r : vectorResults) {
                float normScore = maxVectorSim > 0 ? r.similarity() / maxVectorSim : 0;
                MergedResult mr = new MergedResult();
                mr.id = r.id();
                mr.knowledgeId = r.knowledgeId();
                mr.documentId = r.documentId();
                mr.chunkIndex = r.chunkIndex();
                mr.content = r.content();
                mr.score = normScore * normVectorW;
                mr.matchType = "vector";
                merged.put(r.id(), mr);
            }

            for (var r : keywordResults) {
                float kwScore = computeKeywordDensity(query, r.content());
                MergedResult existing = merged.get(r.id());
                if (existing != null) {
                    existing.score += kwScore * normKeywordW;
                    existing.matchType = "hybrid";
                } else {
                    MergedResult mr = new MergedResult();
                    mr.id = r.id();
                    mr.knowledgeId = r.knowledgeId();
                    mr.documentId = r.documentId();
                    mr.chunkIndex = r.chunkIndex();
                    mr.content = r.content();
                    mr.score = kwScore * normKeywordW;
                    mr.matchType = "keyword";
                    merged.put(r.id(), mr);
                }
            }

            return merged.values().stream()
                    .sorted((a, b) -> Double.compare(b.score, a.score))
                    .limit(topK)
                    .map(mr -> new ChunkRepository.ChunkSearchResult(
                            mr.id, mr.knowledgeId, mr.documentId,
                            mr.chunkIndex, mr.content, (float) mr.score))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Hybrid retrieval failed for knowledge={}", knowledge.getId(), e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<ChunkRepository.ChunkSearchResult> filteredRetrieval(
            String query, KnowledgeBase knowledge, int topK,
            RetrievalFilters filters) {
        List<ChunkRepository.ChunkSearchResult> results =
                vectorRetrieval(query, knowledge, topK * 2);

        return results.stream()
                .filter(r -> {
                    if (filters.getDocumentId() != null
                            && !r.documentId().equals(filters.getDocumentId())) {
                        return false;
                    }
                    if (filters.getMinScore() != null
                            && r.similarity() < filters.getMinScore()) {
                        return false;
                    }
                    return true;
                })
                .limit(topK)
                .collect(Collectors.toList());
    }

    @Override
    public List<EnhancedChunk> enhanceResults(
            List<ChunkRepository.ChunkSearchResult> chunks, String query) {
        return chunks.stream()
                .map(r -> {
                    EnhancedChunk enhanced = new EnhancedChunk();
                    enhanced.setId(r.id());
                    enhanced.setKnowledgeId(r.knowledgeId());
                    enhanced.setDocumentId(r.documentId());
                    enhanced.setChunkIndex(r.chunkIndex());
                    enhanced.setContent(r.content());
                    enhanced.setRelevanceScore(r.similarity());
                    enhanced.setSimilarity(r.similarity());
                    enhanced.setMatchType("vector");
                    enhanced.setConfidenceScore(computeConfidence(query, r.content()));
                    return enhanced;
                })
                .collect(Collectors.toList());
    }

    private float computeKeywordDensity(String query, String content) {
        if (query == null || query.isEmpty()) return 0;
        String lowerContent = content.toLowerCase();
        int count = 0;
        int idx = 0;
        String lowerQ = query.toLowerCase();
        while ((idx = lowerContent.indexOf(lowerQ, idx)) != -1) {
            count++;
            idx += lowerQ.length();
        }
        return Math.min(1f, (float) count / Math.max(1, content.length() / 100));
    }

    private float computeConfidence(String query, String content) {
        float density = computeKeywordDensity(query, content);
        float lengthRatio = query.length() / (float) Math.max(1, content.length());
        return density * (1 - Math.abs(lengthRatio - 0.1f));
    }

    private static class MergedResult {
        Long id;
        Long knowledgeId;
        Long documentId;
        int chunkIndex;
        String content;
        double score;
        String matchType;
    }
}
